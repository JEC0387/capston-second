"""Diagnostic-only access/concurrency tests; no Android or provider success claim."""
import asyncio
import hashlib
import importlib.util
import json
from pathlib import Path

import httpx
import pytest


SPEC = importlib.util.spec_from_file_location("wire_probe", Path(__file__).parent /
    "android_outfit_contract_probe/loopback_validator.py")
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


@pytest.fixture
def fixture(tmp_path, monkeypatch):
    raw = b'{"synthetic_transport_fixture":true}'
    digest = hashlib.sha256(raw).hexdigest()
    (tmp_path / "payload.json").write_bytes(raw)
    (tmp_path / "receipt.json").write_text(json.dumps(dict(synthetic_only=True,
        actual_device=True, body_sha256=digest)))
    # Pixel/reference parsing is separately proven with real S25+ artifacts.
    monkeypatch.setattr(MOD, "verify", lambda folder: {"synthetic_unit_fixture": True})
    token = "a" * 64
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json",
               "X-P57-Body-SHA256": digest}
    return MOD.create_validator(tmp_path, token), raw, headers


def test_authentication_and_single_success(fixture):
    async def run():
        app, raw, headers = fixture
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://test") as client:
            for credential in ("", "Bearer incorrect"):
                reply = await client.post("/p57/validate", content=raw, headers={"Authorization": credential})
                assert reply.status_code == 401
            reply = await client.post("/p57/validate", content=raw, headers=headers)
            assert reply.status_code == 200
            assert reply.json() == dict(status="PASS", received_body_sha256=hashlib.sha256(raw).hexdigest(),
                                        external_provider_calls=0)
            assert (await client.post("/p57/validate", content=raw, headers=headers)).status_code == 409
    asyncio.run(run())


def test_changed_wire_rejected_and_no_retry(fixture):
    async def run():
        app, raw, headers = fixture
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://test") as client:
            assert (await client.post("/p57/validate", content=raw+b" ", headers=headers)).status_code == 422
            assert (await client.post("/p57/validate", content=raw, headers=headers)).status_code == 409
    asyncio.run(run())


def test_concurrent_request_rejected_while_first_stream_is_incomplete(fixture):
    async def run():
        app, raw, headers = fixture
        first_chunk, release = asyncio.Event(), asyncio.Event()
        async def slow_body():
            yield raw[:5]
            first_chunk.set()
            await release.wait()
            yield raw[5:]
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://test") as client:
            first = asyncio.create_task(client.post("/p57/validate", content=slow_body(), headers=headers))
            try:
                await asyncio.wait_for(first_chunk.wait(), timeout=2)
                assert (await client.post("/p57/validate", content=raw, headers=headers)).status_code == 409
            finally:
                release.set()
            assert (await first).status_code == 200
    asyncio.run(run())
