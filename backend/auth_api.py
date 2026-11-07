"""Private data authentication. Legacy device IDs are never accepted as credentials."""
import hashlib
import hmac
import os
import re
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, Response, Request
from pydantic import BaseModel, Field
from sqlalchemy import select, update
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from database import get_db
from db_models import AuthCredential, LegacyClaimTicket, User
from auth_limits import budget

router = APIRouter(prefix="/api/auth", tags=["사용자 인증"])
_SECRET_PATTERN = re.compile(r"[A-Za-z0-9_-]{40,100}\Z")


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode("ascii")).hexdigest()


def _new_secret() -> str:
    return secrets.token_urlsafe(32)


def _credential(db: Session, authorization: str | None, lock: bool = False) -> AuthCredential:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "authentication_required")
    secret = authorization[7:]
    if not _SECRET_PATTERN.fullmatch(secret):
        raise HTTPException(401, "authentication_required")
    try:
        query = select(AuthCredential).where(AuthCredential.token_hash == _hash(secret))
        credential = db.scalar(query.with_for_update() if lock else query)
    except SQLAlchemyError:
        raise HTTPException(503, "auth_schema_unavailable") from None
    if credential is None:
        raise HTTPException(401, "authentication_required")
    return credential


def current_credential(request: Request, db: Session = Depends(get_db), authorization: str | None = Header(None)) -> AuthCredential:
    return _credential(db, authorization, lock=(request.method not in ("GET", "HEAD") or
                                               request.url.path == "/api/backup/snapshot"))


def current_user(db: Session = Depends(get_db), credential: AuthCredential = Depends(current_credential)) -> User:
    if not credential.data_enabled:
        raise HTTPException(409, "legacy_decision_required")
    user = db.get(User, credential.user_id)
    if user is None:
        raise HTTPException(401, "authentication_required")
    return user


def analytics_admin(authorization: str | None = Header(None)):
    """Cross-user analytics include arbitrary labels/dates, so require operator access."""
    expected = os.getenv("ANALYTICS_ADMIN_TOKEN_SHA256", "")
    if (not authorization or not authorization.startswith("Bearer ") or
            not _SECRET_PATTERN.fullmatch(authorization[7:]) or
            not re.fullmatch(r"[a-f0-9]{64}", expected) or
            not hmac.compare_digest(_hash(authorization[7:]), expected)):
        raise HTTPException(401, "operator_authentication_required")


class RegisterRequest(BaseModel):
    legacyPending: bool = False


class RecoveryRequest(BaseModel):
    recoveryCode: str = Field(min_length=40, max_length=100)


class ClaimRequest(BaseModel):
    ticket: str = Field(min_length=40, max_length=100)


@router.post("/register", status_code=201, dependencies=[Depends(budget("register", 10))])
def register(request: RegisterRequest, response: Response, db: Session = Depends(get_db)):
    """Creates a new empty account, never attaches a pre-existing device ID."""
    if os.getenv("AUTH_REGISTRATION_ENABLED", "false").lower() != "true":
        raise HTTPException(503, "registration_disabled")
    access, recovery = _new_secret(), _new_secret()
    try:
        user = User(device_id=None, nickname="나의 스타일", preferred_styles=[], sample_records_added=True)
        db.add(user)
        db.flush()
        db.add(AuthCredential(user_id=user.id, token_hash=_hash(access),
                              recovery_hash=_hash(recovery), data_enabled=not request.legacyPending))
        db.commit()
    except SQLAlchemyError:
        db.rollback()
        raise HTTPException(503, "auth_schema_unavailable") from None
    response.headers["Cache-Control"] = "no-store"
    return {"accessToken": access, "recoveryCode": recovery,
            "dataEnabled": not request.legacyPending}


@router.post("/recover", dependencies=[Depends(budget("recover", 20))])
def recover(request: RecoveryRequest, response: Response, db: Session = Depends(get_db)):
    """Recovery rotates the active access token. The high-entropy recovery code remains reusable."""
    if not _SECRET_PATTERN.fullmatch(request.recoveryCode):
        raise HTTPException(401, "invalid_recovery_code")
    try:
        credential = db.scalar(select(AuthCredential).where(
            AuthCredential.recovery_hash == _hash(request.recoveryCode)).with_for_update())
        if credential is None:
            raise HTTPException(401, "invalid_recovery_code")
        access = _new_secret()
        credential.token_hash = _hash(access)
        db.commit()
    except SQLAlchemyError:
        db.rollback()
        raise HTTPException(503, "auth_schema_unavailable") from None
    response.headers["Cache-Control"] = "no-store"
    return {"accessToken": access, "dataEnabled": credential.data_enabled}


@router.get("/status")
def status(credential: AuthCredential = Depends(current_credential)):
    return {"authenticated": True, "dataEnabled": credential.data_enabled}


@router.post("/start-fresh")
def start_fresh(db: Session = Depends(get_db), credential: AuthCredential = Depends(current_credential)):
    """Explicit opt-in: local data may now sync to this new account; old server data stays untouched."""
    credential.data_enabled = True
    db.commit()
    return {"dataEnabled": True}


@router.post("/claim-legacy", dependencies=[Depends(budget("claim", 20))])
def claim_legacy(request: ClaimRequest, db: Session = Depends(get_db),
                 credential: AuthCredential = Depends(current_credential)):
    """Only an operator-issued one-use ticket can link an empty new account to legacy records."""
    if credential.data_enabled or not _SECRET_PATTERN.fullmatch(request.ticket):
        raise HTTPException(409, "legacy_claim_unavailable")
    try:
        ticket = db.scalar(select(LegacyClaimTicket).where(
            LegacyClaimTicket.ticket_hash == _hash(request.ticket)).with_for_update())
        now = datetime.now(timezone.utc)
        expiry = ticket.expires_at if ticket else None
        if expiry is not None and expiry.tzinfo is None:
            expiry = expiry.replace(tzinfo=timezone.utc)
        if (ticket is None or ticket.consumed_at is not None or expiry <= now or
                not ticket.approved_by or not ticket.approval_reference or not ticket.verification_method):
            raise HTTPException(404, "legacy_claim_unavailable")
        target = db.scalar(select(User).where(User.id == ticket.user_id).with_for_update())
        if target is None or target.device_id is None or db.scalar(select(AuthCredential).where(
                AuthCredential.user_id == target.id)) is not None:
            raise HTTPException(409, "legacy_claim_unavailable")
        credential.user_id = target.id
        credential.data_enabled = True
        ticket.consumed_at = now
        # The newly created, empty account remains orphaned. No legacy or local rows are deleted.
        db.commit()
    except SQLAlchemyError:
        db.rollback()
        raise HTTPException(503, "auth_schema_unavailable") from None
    return {"dataEnabled": True}


def issue_legacy_ticket(db: Session, device_id: str, hours: int = 24, *,
                        approved_by: str, approval_reference: str, verification_method: str) -> str:
    """Admin-only helper; caller must verify ownership independently before invoking."""
    if not 1 <= hours <= 72:
        raise ValueError("hours must be 1..72")
    if (not re.fullmatch(r"[A-Za-z0-9_.@-]{3,100}", approved_by) or
            not re.fullmatch(r"[A-Za-z0-9_./-]{3,100}", approval_reference) or
            verification_method not in {"signed-installation-challenge", "verified-account-record"}):
        raise ValueError("independent approval metadata is required; use references, never raw evidence")
    user = db.scalar(select(User).where(User.device_id == device_id).with_for_update())
    if user is None or db.scalar(select(AuthCredential).where(AuthCredential.user_id == user.id)):
        raise ValueError("legacy account is unavailable")
    secret = _new_secret()
    db.execute(update(LegacyClaimTicket).where(LegacyClaimTicket.user_id == user.id,
        LegacyClaimTicket.consumed_at.is_(None)).values(consumed_at=datetime.now(timezone.utc)))
    db.add(LegacyClaimTicket(user_id=user.id, ticket_hash=_hash(secret),
        expires_at=datetime.now(timezone.utc) + timedelta(hours=hours), approved_by=approved_by,
        approval_reference=approval_reference, verification_method=verification_method))
    db.commit()
    return secret
