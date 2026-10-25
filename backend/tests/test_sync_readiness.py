"""Connection readiness reports capabilities without creating or migrating a database."""
from sqlalchemy import create_engine, text
from sqlalchemy.pool import StaticPool

import database
from database import Base


def test_unconfigured_database_cannot_advertise_sync(monkeypatch):
    monkeypatch.setattr(database, "engine", None)
    status = database.database_status()
    assert status["authProtocol"] == 2
    assert status["syncReady"] is False
    assert status["authReady"] is False


def test_incomplete_auth_schema_blocks_automatic_sync_without_touching_data(monkeypatch):
    engine = create_engine("sqlite://", poolclass=StaticPool)
    Base.metadata.create_all(engine)
    monkeypatch.setattr(database, "engine", engine)
    try:
        assert database.database_status()["syncReady"] is True
        with engine.begin() as connection:
            connection.execute(text("DROP TABLE auth_rate_buckets"))
        status = database.database_status()
        assert status["ready"] is True
        assert status["syncReady"] is False
        assert status["authReady"] is False
        assert "006_auth_hardening.sql" in status["hint"]
        with engine.connect() as connection:
            assert connection.execute(text("SELECT count(*) FROM users")).scalar_one() == 0
    finally:
        engine.dispose()
