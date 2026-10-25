"""Offline SQLite authentication tests. No production DB, API key, or paid provider."""
import os
os.environ["DATABASE_URL"] = ""
os.environ["DB_AUTO_CREATE"] = "false"

from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import main
from auth_api import issue_legacy_ticket
from database import Base, get_db
from db_models import User, Clothing


def client_and_db(monkeypatch):
    monkeypatch.setenv("AUTH_REGISTRATION_ENABLED", "true")
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    def test_db():
        with factory() as session:
            yield session
    main.app.dependency_overrides[get_db] = test_db
    return TestClient(main.app), factory, engine


def register(client, legacy=False):
    response = client.post("/api/auth/register", json={"legacyPending": legacy})
    assert response.status_code == 201, response.text
    assert response.headers["cache-control"] == "no-store"
    return response.json()


def bearer(token):
    return {"Authorization": "Bearer " + token}


def test_device_id_alone_cannot_read_or_change_legacy_data(monkeypatch):
    client, factory, engine = client_and_db(monkeypatch)
    try:
        with factory() as db:
            legacy = User(device_id="old-backup-uuid", nickname="old", preferred_styles=[])
            db.add(legacy); db.flush()
            db.add(Clothing(user_id=legacy.id, client_id="private-shirt", name="private", category="상의",
                            color="#000000", image_url=""))
            db.commit()
        old = {"X-Device-Id": "old-backup-uuid"}
        assert client.get("/api/wardrobe", headers=old).status_code == 401
        assert client.post("/api/wardrobe/sync", headers=old, json={"items": []}).status_code == 401
        assert client.get("/api/wardrobe/photos/private-shirt", headers=old).status_code == 401
        assert client.delete("/api/me", headers=old).status_code == 401
        fresh = register(client)
        assert client.get("/api/wardrobe", headers=bearer(fresh["accessToken"])).json()["totalCount"] == 0
        with factory() as db:
            assert db.scalar(select(Clothing).where(Clothing.client_id == "private-shirt")) is not None
    finally:
        main.app.dependency_overrides.clear(); client.close(); engine.dispose()


def test_new_accounts_are_isolated_and_recovery_rotates_access(monkeypatch):
    client, factory, engine = client_and_db(monkeypatch)
    try:
        a, b = register(client), register(client)
        item = {"id": "one", "name": "내 옷", "category": "상의", "colorName": "검정",
                "colorValue": 0xFF000000, "imageUri": "file:///private"}
        assert client.post("/api/wardrobe/sync", json={"items": [item]},
                           headers=bearer(a["accessToken"])).status_code == 200
        assert client.get("/api/wardrobe", headers=bearer(b["accessToken"])).json()["totalCount"] == 0
        assert client.get("/api/wardrobe/photos/one", headers=bearer(b["accessToken"])).status_code == 404
        rotated = client.post("/api/auth/recover", json={"recoveryCode": a["recoveryCode"]})
        assert rotated.status_code == 200
        assert client.get("/api/wardrobe", headers=bearer(a["accessToken"])).status_code == 401
        assert client.get("/api/wardrobe", headers=bearer(rotated.json()["accessToken"])).json()["totalCount"] == 1
        assert client.post("/api/auth/recover", json={"recoveryCode": b["accessToken"]}).status_code == 401
    finally:
        main.app.dependency_overrides.clear(); client.close(); engine.dispose()


def test_legacy_claim_requires_one_use_operator_ticket(monkeypatch):
    client, factory, engine = client_and_db(monkeypatch)
    try:
        with factory() as db:
            legacy = User(device_id="old-device", nickname="old", preferred_styles=[])
            db.add(legacy); db.flush()
            db.add(Clothing(user_id=legacy.id, client_id="old-shirt", name="old", category="상의",
                            color="#000000", image_url=""))
            db.commit()
            legacy_id = legacy.id
        pending = register(client, legacy=True)
        headers = bearer(pending["accessToken"])
        assert client.get("/api/wardrobe", headers=headers).status_code == 409
        assert client.post("/api/wardrobe/sync", headers=headers, json={"items": []}).status_code == 409
        assert client.post("/api/auth/claim-legacy", headers=headers,
                           json={"ticket": pending["recoveryCode"]}).status_code == 404
        with factory() as db:
            ticket = issue_legacy_ticket(db, "old-device", approved_by="operator-test",
                approval_reference="approval/123", verification_method="verified-account-record")
        assert client.post("/api/auth/claim-legacy", headers=headers, json={"ticket": ticket}).status_code == 200
        assert client.get("/api/wardrobe", headers=headers).json()["items"][0]["clientId"] == "old-shirt"
        second = register(client, legacy=True)
        assert client.post("/api/auth/claim-legacy", headers=bearer(second["accessToken"]),
                           json={"ticket": ticket}).status_code == 404
        with factory() as db:
            assert db.get(User, legacy_id) is not None
            assert db.scalar(select(Clothing).where(Clothing.client_id == "old-shirt")) is not None
    finally:
        main.app.dependency_overrides.clear(); client.close(); engine.dispose()


def test_registration_is_disabled_by_default(monkeypatch):
    monkeypatch.delenv("AUTH_REGISTRATION_ENABLED", raising=False)
    client = TestClient(main.app)
    assert client.post("/api/auth/register", json={}).status_code == 503
