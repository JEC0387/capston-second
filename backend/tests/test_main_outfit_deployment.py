"""Exercise the deployed main ASGI app; isolated SQLite, no model or paid HTTP."""
import asyncio
import json
import re
from pathlib import Path

import pytest

from tests.test_auth_api import main, client_and_db, register, bearer


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main, "initialize_database", lambda: None)
    monkeypatch.setattr(main, "database_status", lambda: {"ready": False, "status": "not_configured"})
    test_client, _, engine = client_and_db(monkeypatch)
    try:
        with test_client:
            yield test_client
    finally:
        test_client.close()
        main.app.dependency_overrides.clear()
        engine.dispose()


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer invalid"}, {"X-Device-Id": "synthetic-device"}])
def test_deployed_route_requires_real_bearer(client, headers):
    response = client.get("https://testserver/api/outfit-vton/config", headers=headers)
    assert response.status_code == 401
    assert response.headers["cache-control"] == "no-store"


def test_existing_auth_reaches_disabled_outfit_and_never_uploads(client, monkeypatch):
    access = bearer(register(client)["accessToken"])
    reply = client.get("https://testserver/api/outfit-vton/config", headers=access)
    assert reply.status_code == 200
    assert reply.json()["ready"] is False and reply.json()["reason"] == "disabled"
    assert reply.json()["max_input_side"] == 2048
    assert set(reply.json()["roles"]) == {"tops", "bottoms", "shoes"}
    from starlette.requests import Request
    async def forbidden_stream(_):
        raise AssertionError("Disabled endpoint consumed image body")
        yield b""
    monkeypatch.setattr(Request, "stream", forbidden_stream)
    blocked = client.post("https://testserver/api/outfit-vton/jobs", headers=access, content=b"private-unread-fixture")
    assert blocked.status_code == 503
    assert blocked.json() == {"detail": "disabled"}
    assert blocked.headers["cache-control"] == "no-store"


def test_client_forwarded_header_cannot_assert_https(client):
    access = bearer(register(client)["accessToken"])
    reply = client.get("http://testserver/api/outfit-vton/config",
                       headers={**access, "X-Forwarded-Proto": "https"})
    assert reply.status_code == 403 and reply.json()["detail"] == "https_required"


def test_outfit_fault_is_sanitized_without_affecting_home(client, monkeypatch):
    access = bearer(register(client)["accessToken"])
    async def broken():
        raise RuntimeError("synthetic-secret-provider-url")
    monkeypatch.setattr(main.outfit_service, "availability", broken)
    reply = client.get("https://testserver/api/outfit-vton/config", headers=access)
    assert reply.status_code == 503
    assert reply.json() == {"detail": "service_unavailable"}
    assert "synthetic-secret" not in reply.text
    assert reply.headers["cache-control"] == "no-store"
    assert client.get("/").status_code == 200


def test_liveness_does_not_claim_database_or_generation_readiness(client):
    reply = client.get("/health")
    assert reply.status_code == 200 and reply.json() == {"status": "alive"}
    assert "no-store" in reply.headers["cache-control"]
    assert client.get("/api/auth/providers").json() == dict(google=False, kakao=False, facebook=False, apple=False)


def test_shared_lifecycle_starts_cleans_and_closes_even_after_error():
    from outfit_vton_api import service_lifespan
    class SyntheticService:
        def __init__(self): self.events = []
        async def start(self): self.events.append("start")
        def purge(self): self.events.append("purge")
        async def close(self): self.events.append("close")
    service = SyntheticService()
    async def run():
        with pytest.raises(ValueError, match="synthetic body failure"):
            async with service_lifespan(service):
                await asyncio.sleep(1.05)
                raise ValueError("synthetic body failure")
        count = len(service.events)
        await asyncio.sleep(.05)
        assert len(service.events) == count
    asyncio.run(run())
    assert service.events == ["start", "purge", "close"]


def test_deployed_container_satisfies_genlook_runtime_and_transport():
    root = Path(__file__).resolve().parents[1]
    docker = (root / "Dockerfile").read_text(encoding="utf8")
    version = re.search(r"FROM python:(\d+)\.(\d+)", docker)
    assert version and tuple(map(int, version.groups())) >= (3, 11), "asyncio.timeout requires Python 3.11+"
    assert "requirements-genlook.txt" in docker
    assert re.search(r"pip install[^\n]*-r requirements-genlook\.txt", docker)
    assert 'uvicorn main:app' in docker and '--workers 1' in docker
    assert '--no-access-log' in docker
    config = json.loads((root / "railway.json").read_text(encoding="utf8"))
    assert config["deploy"]["healthcheckPath"] == "/health"
