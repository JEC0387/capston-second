"""Synthetic HTTP boundary only; no Genlook calls, invoices or generated fashion evidence."""
from dataclasses import replace
import json
import pytest
from fastapi.testclient import TestClient

from outfit_vton_api import create_app
from outfit_vton_provider import ProviderAvailability, ProviderFailure, ProviderResult, configured_provider
from tests.test_outfit_vton_api import (
    SyntheticProvider, settings, test_user, payload, post, finish, status,
)


def disclosure():
    return dict(name="genlook", execution_location="third_party", upstream_model="not_disclosed",
        mode="sequential-3", max_credits_per_job=3, remote_cancellation_supported=False,
        remote_retention_days=1, notice="Synthetic operator-reviewed disclosure; not a real service approval.")


def receipt():
    return dict(provider="genlook", service="tryon/v1", upstream_model="not_disclosed",
        generation_ids=["synthetic-top", "synthetic-bottom", "synthetic-shoe"], mode="sequential-3",
        credits_reserved=3, credits_balance_before=8, credits_balance_after=5, quality="NOT_EVALUATED")


class RemoteFixture(SyntheticProvider):
    def __init__(self, *, delay=0, unconfirmed=False):
        super().__init__(delay=delay)
        self.available = ProviderAvailability(True, "", "genlook/tryon-api-v1",
            "genlook-sequential-fixture", "b" * 64, "synthetic-service-review", "third-party-server",
            provider_info=disclosure())
        self.receipt = receipt()
        self.unconfirmed = unconfirmed

    async def generate(self, *args):
        result = await super().generate(*args)
        return replace(result, source=self.receipt)

    async def cancel(self, identity):
        self.cancels += 1
        if self.unconfirmed:
            raise ProviderFailure("remote_execution_unconfirmed")


def submission(provider):
    result = payload()
    result["model_revision"] = provider.available.model_revision
    return result


def test_optional_provider_selection_does_not_enable_or_fallback(monkeypatch):
    monkeypatch.delenv("OUTFIT_VTON_PROVIDER", raising=False)
    monkeypatch.delenv("OUTFIT_VTON_GPU_ENABLED", raising=False)
    assert type(configured_provider()).__name__ == "DisabledProvider"
    monkeypatch.setenv("OUTFIT_VTON_PROVIDER", "unknown-vendor")
    assert configured_provider().reason == "provider_selection_invalid"
    monkeypatch.setenv("OUTFIT_VTON_PROVIDER", "genlook")
    monkeypatch.delenv("GENLOOK_ENABLED", raising=False)
    assert type(configured_provider()).__name__ == "GenlookProvider"


def test_source_receipt_owner_binding_duplicate_and_restart(tmp_path):
    provider = RemoteFixture()
    config = settings(tmp_path)
    request = submission(provider)
    with TestClient(create_app(config, provider, test_user), base_url="https://testserver") as client:
        public = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert public["provider_info"] == disclosure()
        assert public["ready"] is True
        assert post(client, request).status_code == 202
        result = finish(client, request["request_id"])
        assert result["status"] == "completed" and result["source"] == receipt()
        assert result["quality"] == "NOT_EVALUATED"
        assert post(client, request).status_code == 202 and provider.calls == 1
        assert status(client, request["request_id"], "synthetic-b").status_code == 404
        binary = client.get("/api/outfit-vton/jobs/" + request["request_id"] + "/result",
                            headers={"Authorization": "Bearer synthetic-a"})
        assert binary.status_code == 200 and binary.content.startswith(b"\x89PNG\r\n\x1a\n")
        assert binary.headers["x-result-sha256"] == result["result_sha256"]
    fresh = RemoteFixture()
    with TestClient(create_app(config, fresh, test_user), base_url="https://testserver") as client:
        restored = status(client, request["request_id"]).json()
        assert restored["status"] == "completed" and restored["source"] == receipt()
        assert fresh.calls == 0


@pytest.mark.parametrize("bad", [None, {"provider": "genlook"}, receipt() | {"model": "invented"},
    receipt() | {"generation_ids": ["same", "same", "same"]}, receipt() | {"credits_reserved": True}])
def test_missing_or_forged_provenance_cannot_be_completed(tmp_path, bad):
    provider = RemoteFixture()
    provider.receipt = bad
    request = submission(provider)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        assert post(client, request).status_code == 202
        result = finish(client, request["request_id"])
        assert result["status"] == "failed"
        assert result["result_available"] is False
        assert "source" not in result
    assert not list((tmp_path / "owned").glob("*.png"))


@pytest.mark.parametrize("bad", [None, disclosure() | {"remote_cancellation_supported": True},
                                disclosure() | {"max_credits_per_job": 0}])
def test_bad_disclosure_blocks_before_upload(tmp_path, bad):
    provider = RemoteFixture()
    provider.available = replace(provider.available, provider_info=bad)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url="https://testserver") as client:
        public = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert public["ready"] is False
        assert post(client, submission(provider)).status_code == 503
        assert provider.calls == 0


def test_remote_timeout_is_uncertain_and_survives_restart(tmp_path):
    provider = RemoteFixture(delay=.3, unconfirmed=True)
    config = settings(tmp_path, timeout_seconds=.02)
    request = submission(provider)
    with TestClient(create_app(config, provider, test_user), base_url="https://testserver") as client:
        assert post(client, request).status_code == 202
        result = finish(client, request["request_id"])
        assert result["status"] == "interrupted"
        assert result["error"] == "remote_execution_unconfirmed"
        assert result["result_available"] is False
        assert post(client, submission(provider)).status_code == 503
    with TestClient(create_app(config, RemoteFixture(), test_user), base_url="https://testserver") as client:
        public = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert not public["ready"] and public["reason"] == "remote_execution_unconfirmed"
        assert status(client, request["request_id"]).json()["error"] == "remote_execution_unconfirmed"


def test_p48_journal_without_source_remains_readable(tmp_path):
    config = settings(tmp_path)
    request = payload()
    with TestClient(create_app(config, SyntheticProvider(), test_user), base_url="https://testserver") as client:
        assert post(client, request).status_code == 202
        assert finish(client, request["request_id"])["status"] == "completed"
    before = json.loads((tmp_path / "owned" / "jobs.json").read_text())
    assert "source" not in before[request["request_id"]]
    with TestClient(create_app(config, SyntheticProvider(), test_user), base_url="https://testserver") as client:
        assert status(client, request["request_id"]).json()["status"] == "completed"
