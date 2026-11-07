"""Offline synthetic API/auth/storage tests. Not GPU or private-photo evidence."""
import asyncio
import base64
from dataclasses import replace
import hashlib
import io
import json
import time
from types import SimpleNamespace
from uuid import uuid4

from fastapi import HTTPException, Request
from fastapi.testclient import TestClient
from PIL import Image
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from outfit_vton_api import ENGINE, OutfitSettings, create_app
from outfit_vton_provider import ProviderAvailability, ProviderFailure, ProviderResult


def png(color="red", size=(256, 384), fmt="PNG"):
    output = io.BytesIO()
    Image.new("RGB", size, color).save(output, fmt)
    return output.getvalue()


def data_uri(color="red", mime="png", fmt="PNG"):
    return "data:image/" + mime + ";base64," + base64.b64encode(png(color, fmt=fmt)).decode()


class SyntheticProvider:
    def __init__(self, mode="normal", delay=0):
        self.mode, self.delay, self.calls, self.cancels = mode, delay, 0, 0
        self.stopped = False
        self.available = ProviderAvailability(True, "", "synthetic-test-only", "test-r1", "a"*64,
                                               "test-fixture-not-a-model-license", "test-only")

    async def initialize(self):
        pass

    async def check_availability(self):
        return self.available

    async def generate(self, job_id, input_sha256, images, placements):
        self.calls += 1
        assert set(images) == {"person", "tops", "bottoms", "shoes"}
        assert set(placements) == {"tops", "bottoms", "shoes"}
        try:
            await asyncio.sleep(self.delay)
        except asyncio.CancelledError:
            if self.mode == "slow_cancel":
                if hasattr(self, "during_cancel"):
                    self.during_cancel(job_id)
                await asyncio.sleep(.08)
            self.stopped = True
            raise
        if self.mode == "failure":
            raise RuntimeError("synthetic-secret https://private.invalid?token=never-log")
        result = images["person"] if self.mode == "echo" else b"not-png" if self.mode == "corrupt" else png("purple")
        return ProviderResult(result, str(uuid4()) if self.mode == "wrong_job" else job_id,
                              input_sha256, self.available.model_revision, self.available.model_sha256)

    async def cancel(self, job_id):
        self.cancels += 1


def test_user(request: Request):
    token = request.headers.get("authorization", "")
    if token not in ("Bearer synthetic-a", "Bearer synthetic-b"):
        raise HTTPException(401, "authentication_required")
    return SimpleNamespace(id=1 if token.endswith("-a") else 2)
test_user.__test__ = False


def settings(tmp_path, **kw):
    return replace(OutfitSettings(enabled=True, deployment_approved=True,
        policy_version="synthetic-policy-v1", retention_seconds=60, storage=str(tmp_path / "owned")), **kw)


def payload(identity=None):
    identity = identity or str(uuid4())
    return {"request_id": identity, "model_revision": "test-r1",
        "consent": {"accepted": True, "policy_version": "synthetic-policy-v1"},
        "person": data_uri("red"), "garments": [
            {"id": role + "-synthetic", "category": role, "image": data_uri(color),
             "placement": {"center_x": .5, "center_y": y, "width": .4, "scale": 1, "rotation": 0}}
            for role, color, y in (("tops", "green", .3), ("bottoms", "blue", .65), ("shoes", "yellow", .95))]}


def post(client, value, token="synthetic-a", raw=None):
    raw = raw or json.dumps(value, separators=(",", ":")).encode()
    return client.post("/api/outfit-vton/jobs", content=raw, headers={
        "Authorization": "Bearer " + token, "Content-Type": "application/json",
        "Idempotency-Key": value["request_id"]})


def status(client, identity, token="synthetic-a"):
    return client.get("/api/outfit-vton/jobs/" + identity, headers={"Authorization": "Bearer " + token})


def finish(client, identity):
    for _ in range(100):
        response = status(client, identity)
        value = response.json()
        assert response.status_code == 200, (response.status_code, value.get("detail"))
        if value["status"] in ("completed", "failed", "cancelled", "timeout", "interrupted", "expired"):
            return value
        time.sleep(.01)
    raise AssertionError("synthetic job did not terminate")


def test_disabled_and_unapproved_routes_reject_before_body(tmp_path):
    class MustNotInspect(SyntheticProvider):
        inspected = 0
        initialized = 0
        async def initialize(self):
            self.initialized += 1
            raise AssertionError("Unapproved deployment initialized a model")
        async def check_availability(self):
            self.inspected += 1
            raise AssertionError("Unapproved deployment inspected a model")
    for config in (OutfitSettings(), settings(tmp_path, deployment_approved=False)):
        provider = MustNotInspect()
        with TestClient(create_app(config, provider, test_user), base_url="https://testserver") as client:
            def forbidden():
                raise AssertionError("Blocked server read the image body")
                yield b"unused"
            response = client.post("/api/outfit-vton/jobs", content=forbidden(),
                                   headers={"Authorization": "Bearer synthetic-a"})
            assert response.status_code == 503
            assert provider.calls == 0
            assert provider.initialized == provider.inspected == 0
    assert not (tmp_path / "owned").exists()


def test_invalid_upload_attempts_are_bounded_without_consuming_inference(tmp_path):
    provider = SyntheticProvider()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        for _ in range(12):
            value = payload()
            assert post(client, value, raw=b"invalid-json").status_code == 422
        limited = post(client, payload(), raw=b"invalid-json")
        assert limited.status_code == 429 and limited.json()["detail"] == "upload_rate_limited"
        assert provider.calls == 0


def test_body_size_and_duplicate_json_keys_reject_without_generation(tmp_path, monkeypatch):
    import outfit_vton_api as api
    provider = SyntheticProvider()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        value = payload()
        raw = json.dumps(value).encode()
        duplicate = b'{"request_id":"' + value["request_id"].encode() + b'",' + raw[1:]
        assert post(client, value, raw=duplicate).status_code == 422
        monkeypatch.setattr(api, "MAX_BODY", 32)
        assert post(client, value, raw=raw).status_code == 413
        assert provider.calls == 0


def test_completion_journal_failure_immediately_removes_private_png(tmp_path, monkeypatch):
    provider = SyntheticProvider()
    app = create_app(settings(tmp_path), provider, test_user)
    with TestClient(app, base_url="https://testserver") as client:
        service = app.state.outfit_vton
        original = service.persist
        def fail_completion():
            if any(job["status"] == "completed" for job in service.jobs.values()):
                raise OSError("synthetic completion write failure")
            original()
        monkeypatch.setattr(service, "persist", fail_completion)
        value = payload(); post(client, value)
        for _ in range(100):
            if provider.calls and service.active is None:
                break
            time.sleep(.01)
        assert provider.calls == 1
        assert service.jobs[value["request_id"]]["status"] == "failed"
        assert not service.journal_ready
        assert not (tmp_path / "owned" / (value["request_id"] + ".png")).exists()
        assert status(client, value["request_id"]).status_code == 503


def test_unconfirmed_worker_stop_is_not_cancel_success_and_blocks_new_work(tmp_path):
    class StopFailure(SyntheticProvider):
        async def cancel(self, job_id):
            self.cancels += 1
            raise OSError("synthetic stop not confirmed")
    provider = StopFailure(delay=60)
    app = create_app(settings(tmp_path), provider, test_user)
    with TestClient(app, base_url="https://testserver") as client:
        value = payload(); post(client, value)
        for _ in range(100):
            if provider.calls:
                break
            time.sleep(.005)
        cancelled = client.delete(f"/api/outfit-vton/jobs/{value['request_id']}", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert cancelled["status"] == "interrupted"
        assert cancelled["error"] == "worker_stop_unconfirmed"
        config = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert config["ready"] is False and config["reason"] == "worker_stop_unconfirmed"
        assert post(client, payload()).status_code == 503
        assert provider.calls == 1
    replacement = SyntheticProvider()
    with TestClient(create_app(settings(tmp_path), replacement, test_user), base_url="https://testserver") as client:
        restored = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert restored["ready"] is False and restored["reason"] == "worker_stop_unconfirmed"
        assert replacement.calls == 0


def test_completed_failure_still_requires_owned_native_stop_confirmation(tmp_path):
    class ReturnedFailure(SyntheticProvider):
        async def generate(self, *args):
            self.calls += 1
            raise RuntimeError("synthetic native teardown failed before await returned")
        async def cancel(self, job_id):
            self.cancels += 1
            raise PermissionError("synthetic child still running")
    provider = ReturnedFailure()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        value = payload(); post(client, value)
        failed = finish(client, value["request_id"])
        assert failed["status"] == "interrupted" and failed["error"] == "worker_stop_unconfirmed"
        assert provider.calls == provider.cancels == 1
        assert post(client, payload()).status_code == 503


def test_auth_and_https_are_required_before_image_processing(tmp_path):
    provider = SyntheticProvider()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        assert client.post("/api/outfit-vton/jobs", content=b"not-json", headers={"X-Device-Id": "a"}).status_code == 401
        response = client.get("http://testserver/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a", "X-Forwarded-Proto": "https"})
        assert response.status_code == 403
        assert provider.calls == 0


@pytest.mark.parametrize("change", [
    lambda p: p["consent"].update(accepted=False),
    lambda p: p["consent"].update(policy_version="old"),
    lambda p: p.update(model_revision="old"),
    lambda p: p.update(extra="must-not-accept"),
    lambda p: p["garments"][2].update(category="tops"),
    lambda p: p["garments"][2].update(id=p["garments"][0]["id"]),
    lambda p: p["garments"][0]["placement"].update(width=float("nan")),
    lambda p: p.update(person="https://private.invalid/person.jpg"),
    lambda p: p.update(person=data_uri("red", mime="jpeg", fmt="PNG")),
    lambda p: p.update(person="data:image/png;base64," + base64.b64encode(b"invalid").decode()),
])
def test_schema_consent_roles_and_real_image_format_reject(tmp_path, change):
    provider = SyntheticProvider()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        value = payload(); change(value)
        response = post(client, value)
        assert response.status_code == 422
        assert response.json() == {"detail": "invalid_images_or_request"}
        assert provider.calls == 0


def test_three_images_frozen_result_bound_idempotent_and_isolated(tmp_path):
    provider = SyntheticProvider()
    app = create_app(settings(tmp_path), provider, test_user)
    value = payload(); identity = value["request_id"]
    raw = json.dumps(value, separators=(",", ":")).encode()
    with TestClient(app, base_url="https://testserver") as client:
        config = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert config["engine"] == ENGINE and config["ready"] is True
        submitted = post(client, value, raw=raw)
        assert submitted.status_code == 202
        complete = finish(client, identity)
        assert complete["status"] == "completed" and complete["quality"] == "NOT_EVALUATED"
        assert complete["input_sha256"] == hashlib.sha256(raw).hexdigest()
        assert post(client, value, raw=raw).status_code == 202
        assert provider.calls == 1
        changed = payload(identity)
        changed["garments"][0]["placement"]["rotation"] = 20
        assert post(client, changed).status_code == 409
        assert status(client, identity, "synthetic-b").status_code == 404
        assert client.get(f"/api/outfit-vton/jobs/{identity}/result", headers={"Authorization": "Bearer synthetic-b"}).status_code == 404
        assert client.delete(f"/api/outfit-vton/jobs/{identity}", headers={"Authorization": "Bearer synthetic-b"}).status_code == 404
        result = client.get(f"/api/outfit-vton/jobs/{identity}/result", headers={"Authorization": "Bearer synthetic-a"})
        assert result.status_code == 200 and result.headers["content-type"] == "image/png"
        assert result.headers["x-request-id"] == identity
        assert result.headers["x-input-sha256"] == hashlib.sha256(raw).hexdigest()
        assert result.headers["x-result-sha256"] == hashlib.sha256(result.content).hexdigest()
        assert result.headers["cache-control"] == "no-store"
        with Image.open(io.BytesIO(result.content)) as image:
            image.load(); assert image.getpixel((20, 20))[:3] == (128, 0, 128)
        journal = (tmp_path / "owned/jobs.json").read_text()
        assert "data:image" not in journal and "synthetic-a" not in journal


@pytest.mark.parametrize("mode", ["wrong_job", "echo", "corrupt", "failure"])
def test_invalid_model_output_is_never_success_or_raw_error(tmp_path, mode):
    provider = SyntheticProvider(mode)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        value = payload(); post(client, value)
        finished = finish(client, value["request_id"])
        assert finished["status"] == "failed" and finished["result_available"] is False
        assert "synthetic-secret" not in json.dumps(finished) and "private.invalid" not in json.dumps(finished)
        assert client.get(f"/api/outfit-vton/jobs/{value['request_id']}/result", headers={"Authorization": "Bearer synthetic-a"}).status_code == 409


@pytest.mark.parametrize("kind,expected", [
    ("cuda_out_of_memory", "cuda_out_of_memory"),
    ("model_load_failed", "model_load_failed"),
    ("unknown_provider_code", "provider_failed"),
    ("foreign_exception", "generation_failed"),
])
def test_only_registered_provider_failure_codes_survive_job_cleanup(tmp_path, kind, expected):
    class FailingProvider(SyntheticProvider):
        async def generate(self, job_id, input_sha256, images, placements):
            failure = (RuntimeError("synthetic-secret https://private.invalid?token=never-log")
                       if kind == "foreign_exception" else ProviderFailure(kind))
            if kind == "foreign_exception":
                failure.code = "cuda_out_of_memory"
            raise failure

    provider = FailingProvider()
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        value = payload()
        response = post(client, value)
        assert response.status_code == 202, (response.status_code, response.json().get("detail"))
        finished = finish(client, value["request_id"])
        assert finished["status"] == "failed" and finished["error"] == expected
        assert finished["result_available"] is False and provider.cancels == 1
        assert "synthetic-secret" not in json.dumps(finished) and "private.invalid" not in json.dumps(finished)
        journal = json.loads((tmp_path / "owned/jobs.json").read_text())
        assert expected in json.dumps(journal)


def test_cancel_waits_for_worker_and_never_refunds_usage(tmp_path):
    provider = SyntheticProvider("slow_cancel", delay=60)
    app = create_app(settings(tmp_path, max_jobs_per_day=1), provider, test_user)
    observed = []
    provider.during_cancel = lambda identity: observed.append(app.state.outfit_vton.jobs[identity]["status"])
    with TestClient(app, base_url="https://testserver") as client:
        value = payload(); post(client, value)
        for _ in range(100):
            if provider.calls:
                break
            time.sleep(.005)
        response = client.delete(f"/api/outfit-vton/jobs/{value['request_id']}", headers={"Authorization": "Bearer synthetic-a"})
        assert response.json()["status"] == "cancelled"
        assert observed == ["cancelling"]
        assert provider.stopped and app.state.outfit_vton.active is None
        assert post(client, value).json()["status"] == "cancelled"
        assert post(client, payload()).status_code == 429
        assert provider.calls == 1


def test_timeout_restart_and_expiration_do_not_regenerate(tmp_path):
    value = payload(); identity = value["request_id"]
    config = settings(tmp_path, timeout_seconds=.02)
    provider = SyntheticProvider(delay=60)
    with TestClient(create_app(config, provider, test_user), base_url="https://testserver") as client:
        post(client, value)
        assert finish(client, identity)["status"] == "timeout"
    replacement = SyntheticProvider()
    with TestClient(create_app(config, replacement, test_user), base_url="https://testserver") as client:
        assert status(client, identity).json()["status"] == "timeout"
        assert post(client, value).json()["status"] == "timeout"
        assert replacement.calls == 0


def test_result_survives_restart_then_policy_expiry_deletes_only_result(tmp_path):
    config = settings(tmp_path)
    value = payload(); identity = value["request_id"]
    with TestClient(create_app(config, SyntheticProvider(), test_user), base_url="https://testserver") as client:
        post(client, value); assert finish(client, identity)["status"] == "completed"
    replacement = SyntheticProvider()
    app = create_app(config, replacement, test_user)
    with TestClient(app, base_url="https://testserver") as client:
        assert status(client, identity).json()["result_available"] is True
        assert replacement.calls == 0
        app.state.outfit_vton.jobs[identity]["expires_at"] = time.time()-1
        assert status(client, identity).json()["status"] == "expired"
        assert not (tmp_path / "owned" / (identity + ".png")).exists()
        assert identity in json.loads((tmp_path / "owned/jobs.json").read_text())
        assert post(client, value).json()["status"] == "expired"


def test_storage_lease_prevents_second_worker(tmp_path):
    config = settings(tmp_path)
    with TestClient(create_app(config, SyntheticProvider(), test_user), base_url="https://testserver"):
        with TestClient(create_app(config, SyntheticProvider(), test_user), base_url="https://testserver") as second:
            response = second.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"})
            assert response.json()["ready"] is False
            assert response.json()["reason"] == "journal_unavailable"


def test_existing_account_auth_and_revocation_not_device_id(tmp_path):
    # The production dependency itself runs against isolated SQLite, no auth shim.
    from database import Base, get_db
    from db_models import User, AuthCredential
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    token = "p48_synthetic_account_credential_" + "x"*20
    with factory() as db:
        user = User(nickname="synthetic", preferred_styles=[], sample_records_added=True)
        db.add(user); db.flush()
        credential = AuthCredential(user_id=user.id, token_hash=hashlib.sha256(token.encode()).hexdigest(),
                                    recovery_hash="b"*64, data_enabled=True)
        db.add(credential); db.commit(); credential_id = credential.id
    def isolated_db():
        with factory() as db:
            yield db
    app = create_app(settings(tmp_path), SyntheticProvider())
    app.dependency_overrides[get_db] = isolated_db
    try:
        with TestClient(app, base_url="https://testserver") as client:
            assert client.get("/api/outfit-vton/config", headers={"X-Device-Id": "old"}).status_code == 401
            assert client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer " + token}).status_code == 200
            with factory() as db:
                db.get(AuthCredential, credential_id).data_enabled = False
                db.commit()
            assert client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer " + token}).status_code == 409
    finally:
        app.dependency_overrides.clear(); engine.dispose()
