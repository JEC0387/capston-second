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


def _container_command_args(env):
    """Run the real Dockerfile CMD shell with uvicorn replaced by a printer (no server is started)."""
    import os, shutil, subprocess
    root = Path(__file__).resolve().parents[1]
    line = [l for l in (root / "Dockerfile").read_text(encoding="utf8").splitlines() if l.startswith("CMD ")][-1]
    shell, flag, script = json.loads(line[4:])
    assert (shell, flag) == ("sh", "-c")
    sh = shutil.which("sh")
    if not sh:
        pytest.skip("POSIX sh is not available")
    script = script.replace("exec uvicorn", "printf '%s\\n'")
    clean = {k: v for k, v in os.environ.items() if not k.startswith(("RAILWAY_", "FASHION_TRUSTED"))}
    out = subprocess.run([sh, "-c", script], env={**clean, **env}, capture_output=True, text=True, check=True)
    args = out.stdout.splitlines()
    return args[args.index("--forwarded-allow-ips") + 1]


def test_railway_edge_proxy_is_trusted_so_https_is_seen():
    # Railway edge terminates TLS; '*' additionally requires operator-verified ingress isolation.
    assert _container_command_args({"RAILWAY_PROJECT_ID": "synthetic"}) == "*"


def test_outside_railway_only_loopback_or_explicit_proxies_are_trusted():
    assert _container_command_args({}) == "127.0.0.1"
    assert _container_command_args({"FASHION_TRUSTED_PROXY_IPS": "10.0.0.5"}) == "10.0.0.5"
    assert _container_command_args({"RAILWAY_PROJECT_ID": "synthetic", "FASHION_TRUSTED_PROXY_IPS": "10.0.0.5"}) == "10.0.0.5"


def test_trusted_edge_header_turns_into_https_for_photo_routes(client):
    """The same uvicorn proxy middleware the container uses, with the Railway trust setting."""
    proxy = pytest.importorskip("uvicorn.middleware.proxy_headers")
    from starlette.testclient import TestClient
    access = bearer(register(client)["accessToken"])
    edge = TestClient(proxy.ProxyHeadersMiddleware(main.app, trusted_hosts="*"))
    reply = edge.get("http://testserver/api/outfit-vton/config", headers={**access, "X-Forwarded-Proto": "https"})
    assert reply.status_code == 200, reply.text
    # Untrusted peers still cannot assert https (unchanged behaviour outside Railway).
    plain = TestClient(proxy.ProxyHeadersMiddleware(main.app, trusted_hosts="127.0.0.1"))
    blocked = plain.get("http://testserver/api/outfit-vton/config", headers={**access, "X-Forwarded-Proto": "https"})
    assert blocked.status_code == 403 and blocked.json()["detail"] == "https_required"
