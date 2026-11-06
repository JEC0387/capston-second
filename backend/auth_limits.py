"""Atomic, database-backed attempt budgets. Never trust caller-supplied forwarded IPs."""
import hashlib
import time

from fastapi import Depends, HTTPException, Request
from sqlalchemy import case, delete
from sqlalchemy.exc import SQLAlchemyError

from database import get_db
from db_models import AuthRateBucket


def budget(scope: str, limit: int, window: int = 900):
    def check(request: Request, db=Depends(get_db)):
        # A separate transaction keeps failed/rejected attempts durable. The test
        # override supplies the same isolated engine, never an operational DB.
        from sqlalchemy.orm import Session
        now = int(time.time())
        peer = request.client.host if request.client else "unknown"
        key = hashlib.sha256(f"{scope}:{peer}".encode()).hexdigest()
        dialect = db.get_bind().dialect.name
        if dialect == "postgresql":
            from sqlalchemy.dialects.postgresql import insert
        elif dialect == "sqlite":
            from sqlalchemy.dialects.sqlite import insert
        else:
            raise HTTPException(503, "auth_limiter_unavailable")
        table = AuthRateBucket
        expired = table.started_at <= now - window
        statement = insert(table).values(key=key, started_at=now, attempts=1)
        statement = statement.on_conflict_do_update(index_elements=[table.key], set_={
            "started_at": case((expired, now), else_=table.started_at),
            "attempts": case((expired, 1), else_=table.attempts + 1),
        }).returning(table.attempts, table.started_at)
        try:
            with Session(db.get_bind()) as session:
                attempts, started = session.execute(statement).one()
                session.execute(delete(table).where(table.started_at < now - 86400))
                session.commit()
        except SQLAlchemyError:
            raise HTTPException(503, "auth_limiter_unavailable") from None
        if attempts > limit:
            raise HTTPException(429, "auth_rate_limited", headers={
                "Retry-After": str(max(1, started + window - now))})
    return check
