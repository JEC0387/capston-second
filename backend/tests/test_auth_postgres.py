"""Real row-lock/atomic-upsert tests, only on an explicitly configured LOCAL test DB."""
import os
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi import HTTPException, Request
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker

import main
from auth_api import issue_legacy_ticket
from auth_limits import budget
from database import Base, get_db
from db_models import AuthCredential, AuthRateBucket, LegacyClaimTicket, User
from tests.test_auth_api import bearer, register


@pytest.fixture
def postgres(monkeypatch):
    raw = os.getenv("TEST_POSTGRES_URL")
    if not raw:
        pytest.skip("TEST_POSTGRES_URL local PostgreSQL test service is not configured")
    url = make_url(raw)
    if url.host not in ("localhost", "127.0.0.1") or url.database != "fashionai_test":
        pytest.fail("Refusing non-local or non-test PostgreSQL database")
    schema = "auth_test_" + uuid4().hex
    control = create_engine(url)
    with control.begin() as conn:
        conn.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"})
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        # Verify team-lineage auth follow-ups, including idempotent hardening DDL.
        tables = [t for t in Base.metadata.sorted_tables if t.name not in
                  {"auth_credentials", "legacy_claim_tickets", "auth_rate_buckets"}]
        Base.metadata.create_all(engine, tables=tables)
        with engine.connect().execution_options(isolation_level="AUTOCOMMIT") as conn:
            for file in ("006_auth_credentials.sql", "007_auth_hardening.sql", "007_auth_hardening.sql"):
                conn.exec_driver_sql((Path(__file__).parents[1] / "migrations" / file).read_text())
        def get_test_db():
            with factory() as session:
                yield session
        main.app.dependency_overrides[get_db] = get_test_db
        monkeypatch.setenv("AUTH_REGISTRATION_ENABLED", "true")
        yield factory
    finally:
        main.app.dependency_overrides.clear()
        engine.dispose()
        with control.begin() as conn:
            conn.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        control.dispose()


def test_postgres_parallel_attempts_share_exact_budget(postgres):
    limiter = budget("parallel-test", 10)
    request = Request({"type": "http", "client": ("127.0.0.1", 1234), "headers": []})
    def attempt(_):
        with postgres() as db:
            try:
                limiter(request, db)
                return 200
            except HTTPException as error:
                return error.status_code
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(attempt, range(25)))
    assert results.count(200) == 10
    assert results.count(429) == 15
    with postgres() as db:
        assert db.scalar(select(AuthRateBucket)).attempts == 25


def test_postgres_competing_claims_consume_ticket_once(postgres):
    with postgres() as db:
        user = User(device_id="legacy-race", nickname="legacy", preferred_styles=[])
        db.add(user); db.commit()
        target = user.id
        ticket = issue_legacy_ticket(db, "legacy-race", approved_by="operator-test",
            approval_reference="approval/race", verification_method="verified-account-record")
    with TestClient(main.app) as client:
        accounts = [register(client, legacy=True) for _ in range(2)]
    def claim(account):
        with TestClient(main.app) as client:
            return client.post("/api/auth/claim-legacy", headers=bearer(account["accessToken"]),
                               json={"ticket": ticket}).status_code
    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(claim, accounts))
    assert sorted(outcomes) == [200, 404]
    with postgres() as db:
        assert len(list(db.scalars(select(AuthCredential).where(AuthCredential.user_id == target)))) == 1
        assert db.scalar(select(LegacyClaimTicket)).consumed_at is not None


def test_postgres_personal_data_roundtrip_and_recovery(postgres):
    import base64
    import hashlib
    with TestClient(main.app) as client:
        a, b = register(client), register(client)
        headers = bearer(a["accessToken"])
        item = {"id": "pg-shirt", "name": "원본 옷", "category": "상의", "colorName": "검정",
                "colorValue": 0xFF000000, "imageUri": "", "price": 30000, "brand": "test"}
        assert client.post("/api/wardrobe/sync", headers=headers, json={"items": [item]}).status_code == 200
        photo = b"\xff\xd8\xfftest-only-photo"
        assert client.post("/api/wardrobe/photos", headers=headers, json={"clientId": "pg-shirt",
            "mime": "image/jpeg", "data": base64.b64encode(photo).decode()}).status_code == 200
        assert client.post("/api/wear-logs/sync", headers=headers, json={"logs": [
            {"date": "2026-10-25", "itemIds": ["pg-shirt"], "memo": "original"}]}).status_code == 200
        assert client.post("/api/profile/styles", headers=headers, json={"styles": ["미니멀"]}).status_code == 200
        recovered = client.post("/api/auth/recover", json={"recoveryCode": a["recoveryCode"]})
        assert recovered.status_code == 200
        assert client.get("/api/backup/snapshot", headers=headers).status_code == 401
        headers = bearer(recovered.json()["accessToken"])
        snapshot = client.get("/api/backup/snapshot", headers=headers).json()
        assert snapshot["wardrobe"]["items"][0]["name"] == "원본 옷"
        assert snapshot["wardrobe"]["items"][0]["price"] == 30000
        assert snapshot["wearLogs"]["logs"][0]["memo"] == "original"
        assert snapshot["photos"]["pg-shirt"] == hashlib.sha256(photo).hexdigest()
        assert client.get("/api/wardrobe/photos/pg-shirt", headers=headers).content == photo
        assert client.get("/api/backup/snapshot", headers=bearer(b["accessToken"])).json()["wardrobe"]["items"] == []
