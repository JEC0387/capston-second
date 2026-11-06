"""Privacy boundaries, durable abuse budgets, and independently approved migration."""
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.routing import APIRoute
from sqlalchemy import select

import main
from auth_api import current_user, issue_legacy_ticket, analytics_admin
from db_models import AuthCredential, AuthRateBucket, LegacyClaimTicket, User
from tests.test_auth_api import bearer, client_and_db, register


@pytest.fixture
def auth(monkeypatch):
    client, factory, engine = client_and_db(monkeypatch)
    yield client, factory
    main.app.dependency_overrides.clear()
    client.close()
    engine.dispose()


def depends_on(dependency, target):
    return dependency.call is target or any(depends_on(child, target) for child in dependency.dependencies)


def test_every_personal_route_rejects_device_ids_and_invalid_bearers(auth):
    client, _ = auth
    personal = ("/api/wardrobe", "/api/wear-logs", "/api/body-measurements", "/api/profile",
                "/api/style-preference", "/api/product-interests", "/api/taste-events",
                "/api/recommendations", "/api/saved-outfits", "/api/outfit-plans", "/api/me", "/api/backup")
    public_aggregates = {"/api/recommendations/stats/all", "/api/product-interests/popular"}
    checked = []
    for route in main.app.routes:
        if not isinstance(route, APIRoute):
            continue
        if route.path in public_aggregates or route.path == "/api/stats/overview":
            assert depends_on(route.dependant, analytics_admin), route.path
            assert client.get(route.path).status_code == 401
            continue
        if not any(route.path == p or route.path.startswith(p + "/") for p in personal):
            continue
        assert depends_on(route.dependant, current_user), route.path
        path = route.path.replace("{item_id}", "123").replace("{client_id}", "private-photo")
        for method in route.methods:
            for header in ({"X-Device-Id": "old-device"}, bearer("X" * 43), {"Authorization": "Basic abc"}):
                response = client.request(method, path, headers=header, json={})
                assert response.status_code == 401, (method, path, response.text)
                assert response.headers["cache-control"] == "no-store"
            checked.append((method, route.path))
    assert len(checked) >= 25


def test_recovery_budget_persists_after_failed_requests_and_ignores_spoofed_forwarding(auth):
    client, factory = auth
    for i in range(20):
        assert client.post("/api/auth/recover", json={"recoveryCode": "x" * 43},
                           headers={"X-Forwarded-For": f"10.0.0.{i}"}).status_code == 401
    with factory() as db:
        bucket = db.scalar(select(AuthRateBucket))
        assert bucket.attempts == 20
        assert "testclient" not in bucket.key
    response = client.post("/api/auth/recover", json={"recoveryCode": "y" * 43})
    assert response.status_code == 429
    assert 1 <= int(response.headers["retry-after"]) <= 900
    with factory() as db:
        bucket = db.scalar(select(AuthRateBucket))
        bucket.started_at -= 901
        db.commit()
    assert client.post("/api/auth/recover", json={"recoveryCode": "z" * 43}).status_code == 401


def test_registration_budget_and_credential_hashes(auth):
    client, factory = auth
    accounts = [register(client) for _ in range(10)]
    assert client.post("/api/auth/register", json={}).status_code == 429
    with factory() as db:
        assert len(list(db.scalars(select(User)))) == 10
        hashes = {c.token_hash for c in db.scalars(select(AuthCredential))}
        assert not hashes.intersection(a["accessToken"] for a in accounts)


def test_ticket_requires_audit_metadata_and_reissue_revokes_previous_ticket(auth):
    client, factory = auth
    with factory() as db:
        user = User(device_id="legacy", nickname="legacy", preferred_styles=[])
        db.add(user); db.commit()
        metadata = dict(approved_by="operator-test", approval_reference="approval/456",
                        verification_method="verified-account-record")
        with pytest.raises(ValueError):
            issue_legacy_ticket(db, "legacy", **{**metadata, "verification_method": "device-id-only"})
        first = issue_legacy_ticket(db, "legacy", **metadata)
        second = issue_legacy_ticket(db, "legacy", **metadata)
        rows = list(db.scalars(select(LegacyClaimTicket).order_by(LegacyClaimTicket.id)))
        assert rows[0].consumed_at is not None
        assert rows[1].approval_reference == "approval/456"
        assert rows[1].ticket_hash != second
    account = register(client, legacy=True)
    headers = bearer(account["accessToken"])
    assert client.post("/api/auth/claim-legacy", headers=headers, json={"ticket": first}).status_code == 404
    with factory() as db:
        row = db.scalar(select(LegacyClaimTicket).order_by(LegacyClaimTicket.id.desc()))
        row.expires_at = datetime.now(timezone.utc) - timedelta(seconds=1)
        db.commit()
    assert client.post("/api/auth/claim-legacy", headers=headers, json={"ticket": second}).status_code == 404


def test_cross_user_numeric_ids_and_deletion(auth):
    client, _ = auth
    a, b = register(client), register(client)
    ah, bh = bearer(a["accessToken"]), bearer(b["accessToken"])
    item = {"id": "same-client-id", "name": "private", "category": "상의", "colorName": "검정",
            "colorValue": 0xFF000000, "imageUri": ""}
    for h in (ah, bh):
        assert client.post("/api/wardrobe/sync", headers=h, json={"items": [item]}).status_code == 200
    own = client.get("/api/wardrobe", headers=ah).json()["items"][0]
    assert client.delete(f'/api/wardrobe/{own["id"]}', headers=bh).status_code == 404
    assert client.post(f'/api/wardrobe/{own["id"]}/favorite', headers=bh).status_code == 404
    assert client.delete("/api/me", headers=ah).status_code == 200
    assert client.get("/api/wardrobe", headers=bh).json()["totalCount"] == 1


def test_account_token_cannot_read_cross_user_analytics(auth):
    client, _ = auth
    headers = bearer(register(client)["accessToken"])
    for path in ("/api/product-interests/popular", "/api/recommendations/stats/all", "/api/stats/overview"):
        assert client.get(path, headers=headers).status_code == 401


def test_snapshot_is_authenticated_account_scoped_and_has_photo_manifest(auth):
    client, _ = auth
    a, b = register(client), register(client)
    item = {"id": "only-a", "name": "private", "category": "상의", "colorName": "검정",
            "colorValue": 0xFF000000, "imageUri": ""}
    assert client.post("/api/wardrobe/sync", headers=bearer(a["accessToken"]), json={"items": [item]}).status_code == 200
    response = client.get("/api/backup/snapshot", headers=bearer(a["accessToken"]))
    assert response.status_code == 200, response.text
    assert response.headers["cache-control"] == "no-store"
    snapshot = response.json()
    assert snapshot["version"] == 1
    assert snapshot["wardrobe"]["items"][0]["clientId"] == "only-a"
    assert snapshot["photos"] == {}
    assert client.get("/api/backup/snapshot", headers=bearer(b["accessToken"])).json()["wardrobe"]["items"] == []
