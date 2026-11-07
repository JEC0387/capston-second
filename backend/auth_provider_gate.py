"""Google · Kakao login bridged to the Bearer account model (Facebook · Apple stay disabled).

Rules
- Off unless SOCIAL_LOGIN_ENABLED=true AND the provider is configured (GOOGLE_CLIENT_ID / KAKAO_REST_API_KEY).
  While off, every route answers 503 social_auth_bridge_unavailable and contacts no provider.
- Every login request must carry this installation's Bearer token (POST /api/auth/register first).
  A device ID is never used: the identity is linked to the *authenticated* account, or the device
  switches to the account that identity was linked to before.
- First login of an identity: it is linked to the current Bearer account. No data moves.
- Identity already linked to another account: that account's access token is rotated and returned
  (one active device per account, same as recovery). The current account and its rows are untouched.
- Identity linked to a pre-Bearer (legacy) user without credentials: 409, operator review path only.
- This account already linked to a *different* identity of the same provider: 409. Local records on the
  phone belong to the current account, so they are never moved into another account automatically.
- Kakao: state and the one-time code are bound to the starting account; the exchange must present
  the same Bearer account, so an intercepted redirect code is useless on another device.
"""
import os
import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Form, Header, HTTPException, Request, Response
from fastapi.responses import HTMLResponse
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

import auth_google
import auth_social
from auth_api import _hash, _new_secret, current_credential
from auth_limits import budget
from database import get_db
from db_models import AuthCredential, User, UserLogin
from schemas import ExchangeLoginRequest, GoogleLoginRequest, LoginProvidersResponse, StartLoginRequest

router = APIRouter(prefix="/api/auth", tags=["인증"])
_UNAVAILABLE = "소셜 로그인 연동이 준비되지 않았습니다. 기기의 기존 기록을 유지하고 게스트로 계속 이용해주세요."
_NO_STORE = {"Cache-Control": "no-store"}
BRIDGED = ("google", "kakao")


def enabled() -> bool:
    return os.getenv("SOCIAL_LOGIN_ENABLED", "false").lower() == "true"


def available(provider: str) -> bool:
    if not enabled() or provider not in BRIDGED:
        return False
    return auth_google.is_configured() if provider == "google" else auth_social.is_configured(provider)


def unavailable():
    raise HTTPException(503, detail={"code": "social_auth_bridge_unavailable", "message": _UNAVAILABLE},
                        headers=_NO_STORE)


def social_credential(request: Request, db: Session = Depends(get_db),
                      authorization: str | None = Header(None)) -> AuthCredential:
    """While social login is off, answer 503 before looking at any credential."""
    if not enabled():
        unavailable()
    return current_credential(request, db, authorization)


def _fail(status: int, code: str, message: str):
    raise HTTPException(status, detail={"code": code, "message": message}, headers=_NO_STORE)


def _cut(value, n):
    return (value or "")[:n] or None


def link_identity(db: Session, credential: AuthCredential, provider: str, claims: dict) -> dict:
    """Link a verified provider identity to the Bearer account, or switch the device to its account."""
    if not credential.data_enabled:
        _fail(409, "legacy_decision_required", "기존 기록 선택을 먼저 마쳐 주세요.")
    now = datetime.now(timezone.utc)
    login = db.scalar(select(UserLogin).where(UserLogin.provider == provider,
                                              UserLogin.subject == claims["sub"]).with_for_update())
    result = {"provider": provider, "switched": False, "isNewAccount": False, "dataEnabled": True}

    if login is not None and login.user_id == credential.user_id:
        login.last_login_at = now
        user = db.get(User, credential.user_id)
    elif login is not None:
        # Same identity, other account → this device becomes that account (token rotation).
        target = db.scalar(select(AuthCredential).where(AuthCredential.user_id == login.user_id).with_for_update())
        if target is None or not target.data_enabled:
            _fail(409, "social_account_requires_review",
                  "이 계정은 예전 방식으로 연결된 기록이라 운영자 확인이 필요해요. 기존 기록은 그대로 있어요.")
        access = _new_secret()
        target.token_hash = _hash(access)
        login.last_login_at = now
        user = db.get(User, login.user_id)
        result.update(switched=True, accessToken=access)
    else:
        if db.scalar(select(func.count()).select_from(UserLogin).where(
                UserLogin.user_id == credential.user_id, UserLogin.provider == provider)):
            _fail(409, "provider_already_linked",
                  "이 기기의 계정은 이미 다른 계정으로 연결돼 있어요. 기존 기록은 그대로 있어요.")
        user = db.get(User, credential.user_id)
        db.add(UserLogin(user_id=user.id, provider=provider, subject=claims["sub"],
                         email=_cut(claims.get("email"), 255), name=_cut(claims.get("name"), 100),
                         last_login_at=now))
        result["isNewAccount"] = True

    if user is None:
        _fail(401, "authentication_required", "다시 로그인해 주세요.")
    email = claims.get("email") or ""
    if email and user.email != email and db.scalar(
            select(User.id).where(User.email == email, User.id != user.id)) is None:
        user.email = _cut(email, 255)
    if claims.get("name") and user.nickname in (None, "", "나의 스타일"):
        user.nickname = _cut(claims["name"], 50)
    try:
        db.commit()
    except IntegrityError:          # concurrent first login of the same identity
        db.rollback()
        _fail(409, "social_login_conflict", "동시에 로그인이 진행됐어요. 다시 시도해 주세요.")
    except SQLAlchemyError:
        db.rollback()
        _fail(503, "auth_schema_unavailable", "서버 DB가 준비되지 않았어요.")
    result.update(name=user.nickname or "", email=user.email or "")
    return result


@router.get("/providers", response_model=LoginProvidersResponse)
def providers():
    return {"google": available("google"), "kakao": available("kakao"), "facebook": False, "apple": False}


@router.post("/google", dependencies=[Depends(budget("social", 20))])
def google_login(request: GoogleLoginRequest, response: Response, db: Session = Depends(get_db),
                 credential: AuthCredential = Depends(social_credential)):
    if not available("google"):
        unavailable()
    try:
        claims = auth_google.verify(request.idToken)
    except auth_google.GoogleNotConfigured:
        unavailable()
    except auth_google.InvalidGoogleToken:
        _fail(401, "invalid_identity", "구글 로그인 정보를 확인하지 못했어요.")
    except auth_google.GoogleUnavailable:
        _fail(502, "identity_provider_unreachable", "구글 확인 서버에 연결하지 못했어요.")
    response.headers.update(_NO_STORE)
    return link_identity(db, credential, "google", claims)


@router.post("/start", dependencies=[Depends(budget("social", 20))])
def start_login(request: StartLoginRequest, http: Request, response: Response,
                credential: AuthCredential = Depends(social_credential)):
    if not available(request.provider):
        unavailable()
    if not credential.data_enabled:
        _fail(409, "legacy_decision_required", "기존 기록 선택을 먼저 마쳐 주세요.")
    proto = http.headers.get("x-forwarded-proto", http.url.scheme)
    base = f"{proto}://{http.headers.get('host', http.url.netloc)}"
    response.headers.update(_NO_STORE)
    # The state remembers the starting *account* (never a device ID).
    return {"url": auth_social.start(request.provider, f"user:{credential.user_id}", base)}


@router.get("/{provider}/callback", response_class=HTMLResponse)
def provider_callback(provider: str, http: Request, code: str | None = None, state: str | None = None,
                      error: str | None = None):
    if provider not in ("kakao", "facebook", "apple"):
        raise HTTPException(404, detail="Unknown login provider")
    if not available(provider):
        unavailable()
    proto = http.headers.get("x-forwarded-proto", http.url.scheme)
    base = f"{proto}://{http.headers.get('host', http.url.netloc)}"
    try:
        owner = auth_social.take_state(provider, state)
        if error or not code:
            raise auth_social.ProviderFailed(error or "no code")
        user = auth_social.fetch_user(provider, code, auth_social.redirect_uri(provider, base))
        query = {"code": auth_social.issue_code(provider, user, owner), "provider": provider}
    except auth_social.ProviderFailed:
        query = {"error": "cancelled" if error else "failed", "provider": provider}
    return HTMLResponse(auth_social.back_to_app_html(query), headers=_NO_STORE)


@router.post("/exchange", dependencies=[Depends(budget("social", 20))])
def exchange_login(request: ExchangeLoginRequest, response: Response, db: Session = Depends(get_db),
                   credential: AuthCredential = Depends(social_credential)):
    entry = auth_social.take_code(request.code)
    # The code is only valid for the account that started the login (state binding).
    if entry is None or not secrets.compare_digest(str(entry[4]), f"user:{credential.user_id}"):
        _fail(401, "login_expired", "로그인 시간이 지났어요. 다시 로그인해 주세요.")
    provider, sub, email, name = entry[:4]
    if not available(provider):
        unavailable()
    response.headers.update(_NO_STORE)
    return link_identity(db, credential, provider, {"sub": sub, "email": email, "name": name})


@router.post("/apple/callback")
def apple_callback(code: str | None = Form(None)):
    unavailable()
