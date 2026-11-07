"""Google · Kakao login on the Bearer account model. Provider calls are faked; no network, no real DB."""
import re
from unittest import mock
from urllib.parse import parse_qs, urlparse

import pytest
from sqlalchemy import select

import auth_google
import auth_social
import main
from db_models import AuthCredential, Clothing, User, UserLogin
from tests.test_auth_api import bearer, client_and_db, register

GOOGLE = {"tok-a": {"sub": "g-a", "email": "a@example.com", "name": "구글A", "picture": ""},
          "tok-b": {"sub": "g-b", "email": "b@example.com", "name": "구글B", "picture": ""}}
KAKAO = {"k-code": {"sub": "1001", "email": "", "name": "카카오유저"}}
ITEM = {"id": "shirt", "name": "내 셔츠", "category": "상의", "colorName": "검정", "colorValue": 0xFF000000,
        "imageUri": "file:///private"}


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setenv("SOCIAL_LOGIN_ENABLED", "true")
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "web-client")
    monkeypatch.setenv("KAKAO_REST_API_KEY", "kakao-key")
    monkeypatch.setenv("PUBLIC_BASE_URL", "https://server.example")
    client, factory, engine = client_and_db(monkeypatch)

    def verify(token):
        if token not in GOOGLE:
            raise auth_google.InvalidGoogleToken()
        return GOOGLE[token]

    def fetch(provider, code, uri, user_json=None):
        assert uri == f"https://server.example/api/auth/{provider}/callback"
        if code not in KAKAO:
            raise auth_social.ProviderFailed("bad")
        return KAKAO[code]
    with mock.patch.object(auth_google, "verify", side_effect=verify), \
            mock.patch.object(auth_social, "fetch_user", side_effect=fetch):
        try:
            yield client, factory
        finally:
            client.close(); main.app.dependency_overrides.clear(); engine.dispose()


def google(client, token, access):
    return client.post("/api/auth/google", json={"idToken": token}, headers=bearer(access))


def kakao(client, access, code="k-code"):
    r = client.post("/api/auth/start", json={"provider": "kakao"}, headers=bearer(access))
    assert r.status_code == 200, r.text
    state = parse_qs(urlparse(r.json()["url"]).query)["state"][0]
    html = client.get("/api/auth/kakao/callback", params={"code": code, "state": state}).text
    m = re.search(r"fashionai://login\?code=([A-Za-z0-9_-]+)", html)
    return m.group(1) if m else html


def test_providers_only_google_and_kakao(env):
    client, _ = env
    assert client.get("/api/auth/providers").json() == {"google": True, "kakao": True, "facebook": False, "apple": False}
    a = register(client)["accessToken"]
    for provider in ("facebook", "apple"):
        assert client.post("/api/auth/start", json={"provider": provider}, headers=bearer(a)).status_code == 503


def test_login_requires_bearer_never_device_id(env):
    client, _ = env
    assert client.post("/api/auth/google", json={"idToken": "tok-a"}, headers={"X-Device-Id": "old"}).status_code == 401
    assert client.post("/api/auth/start", json={"provider": "kakao"}).status_code == 401


def test_first_google_login_links_current_account_and_keeps_data(env):
    client, factory = env
    a = register(client)["accessToken"]
    assert client.post("/api/wardrobe/sync", json={"items": [ITEM]}, headers=bearer(a)).status_code == 200
    r = google(client, "tok-a", a)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["isNewAccount"] and not body["switched"] and "accessToken" not in body
    assert body["name"] == "구글A" and body["email"] == "a@example.com"
    assert r.headers["cache-control"] == "no-store"
    assert client.get("/api/wardrobe", headers=bearer(a)).json()["totalCount"] == 1
    again = google(client, "tok-a", a).json()
    assert not again["isNewAccount"] and not again["switched"]


def test_same_google_on_new_phone_switches_and_rotates_token(env):
    client, factory = env
    a = register(client)["accessToken"]
    client.post("/api/wardrobe/sync", json={"items": [ITEM]}, headers=bearer(a))
    google(client, "tok-a", a)
    phone2 = register(client)["accessToken"]
    r = google(client, "tok-a", phone2).json()
    assert r["switched"] and not r["isNewAccount"]
    new = r["accessToken"]
    assert client.get("/api/wardrobe", headers=bearer(new)).json()["totalCount"] == 1
    assert client.get("/api/wardrobe", headers=bearer(a)).status_code == 401      # one active device per account
    assert client.get("/api/wardrobe", headers=bearer(phone2)).json()["totalCount"] == 0  # phone2's own account untouched


def test_second_identity_on_same_account_is_refused(env):
    client, _ = env
    a = register(client)["accessToken"]
    google(client, "tok-a", a)
    r = google(client, "tok-b", a)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "provider_already_linked"


def test_kakao_flow_is_bound_to_the_starting_account(env):
    client, _ = env
    a = register(client)["accessToken"]
    other = register(client)["accessToken"]
    code = kakao(client, a)
    # an intercepted code cannot be used by another account
    assert client.post("/api/auth/exchange", json={"code": code}, headers=bearer(other)).status_code == 401
    code = kakao(client, a)
    r = client.post("/api/auth/exchange", json={"code": code}, headers=bearer(a))
    assert r.status_code == 200, r.text
    assert r.json()["provider"] == "kakao" and r.json()["isNewAccount"] and r.json()["name"] == "카카오유저"
    assert client.post("/api/auth/exchange", json={"code": code}, headers=bearer(a)).status_code == 401  # one use


def test_kakao_forged_state_and_cancel(env):
    client, _ = env
    html = client.get("/api/auth/kakao/callback", params={"code": "k-code", "state": "forged"}).text
    assert "error=failed" in html and "fashionai://login?code=" not in html
    a = register(client)["accessToken"]
    r = client.post("/api/auth/start", json={"provider": "kakao"}, headers=bearer(a))
    state = parse_qs(urlparse(r.json()["url"]).query)["state"][0]
    assert "error=cancelled" in client.get("/api/auth/kakao/callback", params={"error": "access_denied", "state": state}).text


def test_legacy_linked_identity_needs_operator_review(env):
    client, factory = env
    with factory() as db:
        legacy = User(device_id="legacy-device", nickname="old", preferred_styles=[])
        db.add(legacy); db.flush()
        db.add(UserLogin(user_id=legacy.id, provider="google", subject="g-a"))
        db.add(Clothing(user_id=legacy.id, client_id="old", name="old", category="상의", color="#000000", image_url=""))
        db.commit()
        legacy_id = legacy.id
    a = register(client)["accessToken"]
    r = google(client, "tok-a", a)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "social_account_requires_review"
    with factory() as db:
        assert db.scalar(select(AuthCredential).where(AuthCredential.user_id == legacy_id)) is None
        assert db.scalar(select(Clothing).where(Clothing.user_id == legacy_id)) is not None


def test_pending_legacy_decision_cannot_link(env):
    client, _ = env
    pending = register(client, legacy=True)["accessToken"]
    assert google(client, "tok-a", pending).status_code == 409


def test_disabled_flag_keeps_everything_off(env, monkeypatch):
    client, _ = env
    monkeypatch.setenv("SOCIAL_LOGIN_ENABLED", "false")
    assert client.get("/api/auth/providers").json() == {"google": False, "kakao": False, "facebook": False, "apple": False}
    a = register(client)["accessToken"]
    r = google(client, "tok-a", a)
    assert r.status_code == 503 and r.json()["detail"]["code"] == "social_auth_bridge_unavailable"
