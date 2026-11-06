"""Integrated team contracts using synthetic local records, no OAuth/network/DB service."""
import hashlib
import json
from pathlib import Path
from unittest import mock

import pytest
from sqlalchemy import select

import auth_google
import auth_social
import main
from db_models import Clothing, User, UserLogin, StyleRating
from migration_plan import inspect_plan, MigrationPlanError
from tests.test_auth_api import client_and_db, register, bearer


@pytest.fixture
def isolated(monkeypatch):
    client, factory, engine = client_and_db(monkeypatch)
    try:
        yield client, factory
    finally:
        client.close()
        main.app.dependency_overrides.clear()
        engine.dispose()


def test_disabled_social_routes_never_claim_device_or_delete_local_account(isolated, monkeypatch):
    client, factory = isolated
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "synthetic-config-does-not-enable-unsafe-bridge")
    monkeypatch.setenv("KAKAO_REST_API_KEY", "synthetic-config")
    with factory() as db:
        user = User(device_id="legacy-team-device", nickname="synthetic", preferred_styles=["클래식"])
        db.add(user); db.flush()
        db.add(UserLogin(user_id=user.id, provider="google", subject="synthetic-sub"))
        db.add(Clothing(user_id=user.id, client_id="shirt", name="preserved", category="상의", color="#000000", image_url=""))
        db.commit()
        user_id = user.id
    assert client.get("/api/auth/providers").json() == dict(google=False, kakao=False, facebook=False, apple=False)
    with mock.patch.object(auth_google, "verify") as verify, mock.patch.object(auth_social, "fetch_user") as fetch:
        for path, payload in [("google", {"idToken": "synthetic"}), ("start", {"provider": "kakao"}),
                              ("exchange", {"code": "synthetic"})]:
            response = client.post("/api/auth/" + path, json=payload, headers={"X-Device-Id": "legacy-team-device"})
            assert response.status_code == 503
            assert response.json()["detail"]["code"] == "social_auth_bridge_unavailable"
            assert response.headers["cache-control"] == "no-store"
        assert client.get("/api/auth/kakao/callback?code=private-code&state=private-state").status_code == 503
        assert client.post("/api/auth/apple/callback", data={"code": "private-code"}).status_code == 503
        verify.assert_not_called(); fetch.assert_not_called()
    with factory() as db:
        assert db.get(User, user_id).device_id == "legacy-team-device"
        assert db.get(User, user_id).preferred_styles == ["클래식"]
        assert db.scalar(select(Clothing).where(Clothing.user_id == user_id)).name == "preserved"
        assert db.scalar(select(UserLogin).where(UserLogin.user_id == user_id)).subject == "synthetic-sub"
    assert client.get("/api/wardrobe", headers={"X-Device-Id": "legacy-team-device"}).status_code == 401


def test_team_eight_styles_and_legacy_values_roundtrip_without_rewriting_records(isolated):
    client, factory = isolated
    access = bearer(register(client)["accessToken"])
    options = ["캐주얼", "스트릿", "미니멀", "댄디", "아메카지", "유니크", "스포티", "포멀"]
    assert client.get("/api/styles").json() == options
    saved = options + ["페미닌", "빈티지", "클래식"]
    assert client.post("/api/profile/styles", json={"styles": saved}, headers=access).status_code == 200
    assert client.get("/api/profile", headers=access).json()["preferredStyles"] == saved
    for style in ("클래식", "빈티지", "댄디", "유니크"):
        result = client.post("/api/style-preference/rate", json={"style": style, "like": True}, headers=access)
        assert result.status_code == 200
    scores = {item["style"]: item["score"] for item in result.json()["scores"]}
    assert scores["포멀"] == scores["아메카지"] == scores["댄디"] == scores["유니크"] == 2
    with factory() as db:
        assert list(db.scalars(select(StyleRating.style).order_by(StyleRating.id))) == ["클래식", "빈티지", "댄디", "유니크"]
    assert client.post("/api/profile/styles", json={"styles": ["unsupported"]}, headers=access).status_code == 400
    assert client.get("/api/profile", headers=access).json()["preferredStyles"] == saved


def test_team_migration_identity_and_personal_lineage_are_distinct(tmp_path):
    migrations = Path(__file__).parents[1] / "migrations"
    manifest = json.loads((migrations / "lineage_manifest.json").read_text())
    expected = {
        "005_user_logins.sql": "28e40d700777b79949c511b18af0ae14d1eedb000668ad1c2646de09361c090b",
        "legacy_personal/005_auth_credentials.sql": "185de551b87529d6ade8560b2750b0b9a17770b04c699f4faaf5ff7150fc1a96",
        "legacy_personal/006_auth_hardening.sql": "892ab7cbbc6834cfaba537fd45ed4b722b793ecc5e82a40b762e52176d6dc59e",
    }
    assert manifest["preserved"] == expected
    for name, digest in expected.items():
        assert hashlib.sha256((migrations / name).read_bytes()).hexdigest() == digest
    for new, old in manifest["followups"].items():
        assert (migrations / new).read_bytes() == (migrations / old).read_bytes()
    for new, old in manifest["personalFollowups"].items():
        assert (migrations / new).read_bytes() == (migrations / old).read_bytes()
    personal = migrations / "legacy_personal"
    assert inspect_plan(personal)["files"] == manifest["personalLineage"]
    plan = inspect_plan(migrations)
    assert len(plan["files"]) == 7
    assert plan["databaseAccessed"] is False and plan["pendingFiles"] is None
    history = tmp_path / "team-history.json"
    history.write_text(json.dumps({"version": 1, "applied": plan["files"][:5]}))
    assert inspect_plan(migrations, history)["pendingFiles"] == ["006_auth_credentials.sql", "007_auth_hardening.sql"]
    history.write_text(json.dumps({"version": 1, "applied": [{"file": "005_auth_credentials.sql", "sha256": expected["legacy_personal/005_auth_credentials.sql"]}]}))
    with pytest.raises(MigrationPlanError, match="history_version_conflict:005"):
        inspect_plan(migrations, history)
    history.write_text(json.dumps({"version": 1, "applied": manifest["personalLineage"][:6]}))
    assert inspect_plan(personal, history)["pendingFiles"] == ["007_user_logins.sql"]
