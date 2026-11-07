"""One-shot public research trial ledger; no network or provider credentials.

Commit SUBMITTING before any generation POST. A lost response stays UNKNOWN:
neither a restart nor a failed/cancelled trial restores its one-shot allowance.
RECEIVED means file receipt, never a claim about visual quality or app E2E.
"""
from __future__ import annotations

from dataclasses import dataclass
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sqlite3
from uuid import uuid4


ENGINE = "qwen-official-public-research"
MODEL = "Qwen/Qwen-Image-2.1"
MAX_ATTEMPTS = 1
TERMINAL_STATES = frozenset({"RECEIVED", "UNKNOWN", "FAILED", "BLOCKED"})
_SHA = re.compile(r"[0-9a-f]{64}\Z")
_REASON = re.compile(r"[a-z][a-z0-9_]{0,63}\Z")
_REQUEST_ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z")


class TrialJournalError(RuntimeError):
    """Safe reason codes only: never include request/image contents."""


class TrialAlreadyReserved(TrialJournalError):
    pass


class InvalidTransition(TrialJournalError):
    pass


def _sha(value: str) -> str:
    if not isinstance(value, str) or not _SHA.fullmatch(value):
        raise ValueError("sha256_must_be_64_lowercase_hex")
    return value


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class TrialRequest:
    person_sha256: str
    top_sha256: str
    bottom_sha256: str
    shoes_sha256: str
    prompt_sha256: str
    seed: int = 42
    width: int = 768
    height: int = 1024
    num_inference_steps: int = 40
    prompt_extend: bool = False

    def __post_init__(self) -> None:
        for value in (self.person_sha256, self.top_sha256,
                      self.bottom_sha256, self.shoes_sha256, self.prompt_sha256):
            _sha(value)
        if type(self.seed) is not int or not 0 <= self.seed <= 2147483647:
            raise ValueError("invalid_seed")
        for value in (self.width, self.height):
            if type(value) is not int or not 256 <= value <= 2048 or value % 32:
                raise ValueError("invalid_output_dimension")
        if self.num_inference_steps != 40 or type(self.num_inference_steps) is not int:
            raise ValueError("official_trial_requires_40_steps")
        if self.prompt_extend is not False:
            raise ValueError("companion_upload_disabled")

    def canonical_json(self) -> str:
        # Order is semantic: changing garments or slots changes the fingerprint.
        value = {
            "schema_version": 1, "engine": ENGINE, "model": MODEL, "n": 1,
            "references": [
                {"role": "person", "sha256": self.person_sha256},
                {"role": "top", "sha256": self.top_sha256},
                {"role": "bottom", "sha256": self.bottom_sha256},
                {"role": "shoes", "sha256": self.shoes_sha256},
            ],
            "prompt_sha256": self.prompt_sha256,
            "parameters": {"seed": self.seed, "width": self.width,
                           "height": self.height, "num_inference_steps": 40,
                           "prompt_extend": False, "negative_prompt": ""},
        }
        return json.dumps(value, sort_keys=True, separators=(",", ":"))

    @property
    def fingerprint(self) -> str:
        return hashlib.sha256(self.canonical_json().encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class Reservation:
    reservation_id: str
    fingerprint: str
    state: str
    created_at: str
    updated_at: str
    output_sha256: str | None = None
    provider_request_id: str | None = None
    reason_code: str | None = None
    quality_status: str = "NOT_EVALUATED"


class TrialJournal:
    """Use one stable private DB path for the entire trial, never one per retry.

    This ledger limits callers sharing that file. It does not verify provider
    quota, input permission, model license, or protect against file deletion.
    """

    def __init__(self, db_path: str | Path):
        self.path = Path(db_path).resolve()
        if str(db_path) == ":memory:":
            raise ValueError("durable_file_required")
        # Parent must already be a selected private experiment directory.
        if not self.path.parent.is_dir():
            raise ValueError("journal_parent_missing")
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS trial (
                slot INTEGER PRIMARY KEY CHECK(slot=1),
                reservation_id TEXT NOT NULL UNIQUE,
                engine TEXT NOT NULL CHECK(engine='qwen-official-public-research'),
                fingerprint TEXT NOT NULL CHECK(length(fingerprint)=64),
                canonical_request TEXT NOT NULL,
                state TEXT NOT NULL CHECK(state IN
                    ('RESERVED','SUBMITTING','RECEIVED','UNKNOWN','FAILED','BLOCKED')),
                created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                output_sha256 TEXT, provider_request_id TEXT, reason_code TEXT,
                quality_status TEXT NOT NULL DEFAULT 'NOT_EVALUATED'
                    CHECK(quality_status='NOT_EVALUATED')
            )""")

    @contextmanager
    def _connect(self):
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            db.execute("PRAGMA synchronous=FULL")
            with db:
                yield db
        finally:
            db.close()

    def reserve(self, request: TrialRequest) -> Reservation:
        if not isinstance(request, TrialRequest):
            raise ValueError("typed_trial_request_required")
        # Revalidate even when a caller used low-level mutation on the dataclass.
        request.__post_init__()
        canonical = request.canonical_json()
        fingerprint = request.fingerprint
        reservation_id = uuid4().hex
        timestamp = _now()
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            if db.execute("SELECT 1 FROM trial LIMIT 1").fetchone():
                raise TrialAlreadyReserved("one_trial_already_reserved")
            db.execute("""INSERT INTO trial
                (slot,reservation_id,engine,fingerprint,canonical_request,state,
                 created_at,updated_at) VALUES (1,?,?,?,?, 'RESERVED',?,?)""",
                (reservation_id, ENGINE, fingerprint, canonical, timestamp, timestamp))
            db.commit()
        return self.get(reservation_id)

    def get(self, reservation_id: str) -> Reservation:
        with self._connect() as db:
            row = db.execute("SELECT * FROM trial WHERE reservation_id=?",
                             (reservation_id,)).fetchone()
        if row is None:
            raise TrialJournalError("reservation_not_found")
        if row["engine"] != ENGINE or hashlib.sha256(
                row["canonical_request"].encode("utf-8")).hexdigest() != row["fingerprint"]:
            raise TrialJournalError("journal_integrity_failed")
        return Reservation(**{field: row[field] for field in Reservation.__dataclass_fields__})

    def _transition(self, reservation_id: str, target: str, *,
                    output_sha256: str | None = None,
                    provider_request_id: str | None = None,
                    reason_code: str | None = None) -> Reservation:
        if target not in {"SUBMITTING", *TERMINAL_STATES}:
            raise ValueError("invalid_target_state")
        if target in {"UNKNOWN", "FAILED", "BLOCKED"} and reason_code is None:
            raise ValueError("reason_code_required")
        if provider_request_id is not None and (
                not isinstance(provider_request_id, str) or
                not _REQUEST_ID.fullmatch(provider_request_id)):
            raise ValueError("invalid_provider_request_id")
        if reason_code is not None and (
                not isinstance(reason_code, str) or not _REASON.fullmatch(reason_code)):
            raise ValueError("invalid_reason_code")
        expected = "RESERVED" if target == "SUBMITTING" else "SUBMITTING"
        # BLOCKED can mean a failed preflight before submission.
        allowed = ("RESERVED", "SUBMITTING") if target == "BLOCKED" else (expected,)
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT state,engine,canonical_request,fingerprint FROM trial "
                             "WHERE reservation_id=?", (reservation_id,)).fetchone()
            if row is None:
                raise TrialJournalError("reservation_not_found")
            if row["engine"] != ENGINE or hashlib.sha256(
                    row["canonical_request"].encode("utf-8")).hexdigest() != row["fingerprint"]:
                raise TrialJournalError("journal_integrity_failed")
            if row["state"] not in allowed:
                raise InvalidTransition("transition_not_allowed")
            db.execute("""UPDATE trial SET state=?,updated_at=?,output_sha256=?,
                       provider_request_id=?,reason_code=? WHERE reservation_id=?""",
                       (target, _now(), output_sha256, provider_request_id,
                        reason_code, reservation_id))
            db.commit()
        return self.get(reservation_id)

    def mark_submitting(self, reservation_id: str) -> Reservation:
        return self._transition(reservation_id, "SUBMITTING")

    def mark_received(self, reservation_id: str, output_sha256: str,
                      provider_request_id: str | None = None) -> Reservation:
        return self._transition(reservation_id, "RECEIVED",
                                output_sha256=_sha(output_sha256),
                                provider_request_id=provider_request_id)

    def mark_unknown(self, reservation_id: str, reason_code: str) -> Reservation:
        return self._transition(reservation_id, "UNKNOWN", reason_code=reason_code)

    def mark_failed(self, reservation_id: str, reason_code: str) -> Reservation:
        return self._transition(reservation_id, "FAILED", reason_code=reason_code)

    def mark_blocked(self, reservation_id: str, reason_code: str) -> Reservation:
        return self._transition(reservation_id, "BLOCKED", reason_code=reason_code)
