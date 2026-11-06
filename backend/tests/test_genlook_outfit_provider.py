"""Offline HTTP transport tests, never Genlook requests or real AI inference."""
import asyncio
from dataclasses import replace
from email.parser import BytesParser
from email.policy import default
import hashlib
import io
import json
import logging
from uuid import uuid4

import httpx
from PIL import Image
import pytest

import genlook_outfit_provider as mod
from outfit_vton_provider import ProviderFailure


def run(awaitable):
    return asyncio.run(awaitable)


def png(color, size=(64, 80), format="PNG"):
    out = io.BytesIO()
    Image.new("RGB", size, color).save(out, format=format)
    return out.getvalue()


def inputs():
    return {"person": png("white"), "tops": png("red"), "bottoms": png("blue"), "shoes": png("black")}


def placements():
    return {role: {"center_x": .5, "center_y": .5, "width": .2, "scale": 1, "rotation": 0} for role in mod.ROLES}


def settings(tmp_path, **changes):
    value = mod.GenlookSettings(True, True, True, True, True, True, "gk_synthetic_test_only",
        "synthetic-terms", "synthetic-privacy", "synthetic-cost", str(tmp_path / "owned-ledger"),
        6, 40000, 240000, 30)
    return replace(value, **changes)


def multipart(request, raw):
    message = BytesParser(policy=default).parsebytes(
        b"Content-Type: " + request.headers["content-type"].encode() + b"\r\nMIME-Version: 1.0\r\n\r\n" + raw)
    return {part.get_param("name", header="content-disposition"): part.get_payload(decode=True)
            for part in message.iter_parts()}


class SyntheticService:
    def __init__(self, *, balance=100, case=None):
        self.balance = balance
        self.case = case
        self.calls = []
        self.uploads = []
        self.posts = []
        self.deletes = []
        self.artifact_headers = []
        self.poll_started = asyncio.Event()
        self.release_poll = asyncio.Event()
        self.credit_calls = 0
        self.outputs = [png((50 + n * 60, 20, 180)) for n in range(3)]

    async def __call__(self, request):
        self.calls.append((request.method, request.url.host, request.url.path))
        if self.case == "logging":
            # Like httpcore DEBUG trace, deliberately attempts to log headers.
            logging.getLogger("httpcore.synthetic.child").debug("raw headers %s", dict(request.headers))
        if request.url.host == "storage.googleapis.com":
            self.artifact_headers.append(dict(request.headers))
            assert "x-api-key" not in request.headers
            assert "authorization" not in request.headers
            if self.case == "redirect":
                return httpx.Response(302, headers={"location": "https://evil.invalid/result"})
            if self.case == "bad_mime":
                return httpx.Response(200, content=self.outputs[0], headers={"content-type": "image/jpeg"})
            if self.case == "corrupt":
                return httpx.Response(200, content=b"not-image", headers={"content-type": "image/png"})
            if self.case == "oversize":
                return httpx.Response(200, content=b"small", headers={"content-type": "image/png", "content-length": str(mod.MAX_IMAGE + 1)})
            if self.case == "unchanged":
                return httpx.Response(200, content=self.uploads[-1]["file"], headers={"content-type": "image/png"})
            index = int(request.url.path.rsplit("/", 1)[-1]) - 1
            return httpx.Response(200, content=self.outputs[index], headers={"content-type": "image/png"})
        assert request.url.host == "api.genlook.app"
        assert request.headers["x-api-key"] == "gk_synthetic_test_only"
        if request.method == "GET" and request.url.path.endswith("/account/credits"):
            self.credit_calls += 1
            if self.case == "pause_after_stage" and self.credit_calls == 3:
                self.poll_started.set()
                await self.release_poll.wait()
            return httpx.Response(200, json={"creditBalance": self.balance})
        if request.method == "DELETE":
            self.deletes.append(request.url.path)
            if self.case == "delete_failure":
                return httpx.Response(503, json={"message": "private signed url or account error must not escape"})
            return httpx.Response(204)
        if request.method == "POST" and request.url.path.endswith("/images/upload"):
            parts = multipart(request, await request.aread())
            self.uploads.append(parts)
            assert parts["crop"] == b"false" and parts["keepForDays"] == b"1"
            if self.case == "upload_lost":
                raise httpx.ReadTimeout("SENSITIVE UPLOAD URL", request=request)
            return httpx.Response(201, json={"imageId": "opaque/upload/" + str(len(self.uploads)),
                                             "imageUrl": "https://storage.googleapis.com/not-used"})
        if request.method == "POST" and request.url.path.endswith("/try-on"):
            parts = multipart(request, await request.aread())
            body = json.loads(parts["data"])
            self.posts.append((body, parts["product"]))
            if self.case == "post_lost":
                raise httpx.ReadTimeout("SENSITIVE API TOKEN MUST NOT ESCAPE", request=request)
            return httpx.Response(201, json={"generationId": "generation_" + str(len(self.posts)), "status": "PENDING",
                                             "productExternalIds": [body["products"][0]["externalId"]]})
        if request.method == "GET" and "/generations/" in request.url.path:
            generation = request.url.path.rsplit("/", 1)[-1]
            if self.case == "wait":
                self.poll_started.set()
                await self.release_poll.wait()
            if self.case == "polling":
                return httpx.Response(200, json={"generationId": generation, "status": "PROCESSING"})
            if self.case == "mismatched_id":
                return httpx.Response(200, json={"generationId": "unrelated_generation", "status": "COMPLETED"})
            if self.case == "remote_failed":
                return httpx.Response(200, json={"generationId": generation, "status": "FAILED",
                    "errorCode": "REMOTE_INTERNAL", "errorMessage": "SENSITIVE URL AND PERSON PATH"})
            return httpx.Response(200, json={"generationId": generation, "status": "COMPLETED",
                "resultImageUrl": ("https://evil.invalid/result" if self.case == "bad_host" else
                                    "https://storage.googleapis.com/synthetic/" + str(len(self.posts)) +
                                    ("?X-Goog-Signature=SENSITIVE_SIGNED_URL" if self.case == "logging" else ""))})
        raise AssertionError("Unexpected test HTTP contract")


def test_disabled_configuration_does_not_create_clients_or_storage(tmp_path):
    async def check():
        engine = mod.GenlookProvider(mod.GenlookSettings(ledger_dir=str(tmp_path / "unused")))
        available = await engine.check_availability()
        assert not available.ready and available.reason == "genlook_disabled"
        assert engine._api is None and not (tmp_path / "unused").exists()
        with pytest.raises(ProviderFailure, match="genlook_disabled"):
            await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
    run(check())


@pytest.mark.parametrize("flag", ["deployment_approved", "paid_calls_approved", "privacy_approved", "prepaid_confirmed", "auto_topup_disabled_confirmed"])
def test_each_approval_is_required_before_network_or_storage(tmp_path, flag):
    async def check():
        fake = SyntheticService()
        engine = mod.GenlookProvider(settings(tmp_path, **{flag: False}), transport=httpx.MockTransport(fake))
        assert (await engine.check_availability()).reason == "genlook_approval_required"
        assert fake.calls == [] and not (tmp_path / "owned-ledger").exists()
        await engine.close()
    run(check())


def test_key_redaction_and_contract_binding(tmp_path):
    configured = settings(tmp_path)
    assert configured.api_key not in repr(configured)
    original = mod.GenlookProvider(configured)
    changed = mod.GenlookProvider(replace(configured, privacy_review_id="new-policy"))
    assert original.contract_sha != changed.contract_sha and original.revision != changed.revision
    assert configured.api_key not in json.dumps(original.public_policy())
    assert original.public_policy()["remote_cancellation_supported"] is False


def test_three_stages_feed_real_returned_bytes_and_record_source_then_cleanup(tmp_path):
    async def check():
        fake = SyntheticService()
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            identity = str(uuid4())
            result = await engine.generate(identity, "a" * 64, inputs(), placements())
            assert result.job_id == identity and result.png == fake.outputs[2]
            assert result.source["generation_ids"] == ["generation_1", "generation_2", "generation_3"]
            assert result.source["credits_reserved"] == 3 and result.source["upstream_model"] == "not_disclosed"
            assert len(fake.posts) == 3 and len(fake.uploads) == 3 and len(fake.deletes) == 4
            assert fake.uploads[0]["file"] == inputs()["person"]
            assert fake.uploads[1]["file"] == fake.outputs[0]
            assert fake.uploads[2]["file"] == fake.outputs[1]
            for index, (body, garment) in enumerate(fake.posts):
                role = mod.ROLES[index]
                assert "mock" not in body and len(body["products"]) == 1
                assert body["person"]["image"]["source"] == {"id": "opaque/upload/" + str(index + 1)}
                assert body["products"][0]["images"] == [{"source": {"fileKey": "product"}}]
                assert body["products"][0]["validForDays"] == 1 and garment == inputs()[role]
                assert body["output"] == {"resolution": "default", "aiLabel": True, "keepForDays": 1}
                assert body["products"][0]["externalId"].endswith("-" + role)
            ledger = (tmp_path / "owned-ledger" / "ledger.json").read_text()
            assert "gk_" not in ledger and "https://" not in ledger and "imageId" not in ledger
            assert engine._ledger["jobs"][identity]["status"] == "completed"
        finally:
            await engine.close()
    run(check())


def test_insufficient_balance_prevents_all_uploads_and_paid_posts(tmp_path):
    async def check():
        fake = SyntheticService(balance=2)
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match="genlook_insufficient_credits"):
                await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            assert fake.posts == [] and fake.uploads == [] and engine._ledger["jobs"] == {}
        finally:
            await engine.close()
    run(check())


def test_budget_is_durable_and_not_refunded_by_restart(tmp_path):
    async def check():
        fake = SyntheticService()
        configured = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        engine = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
        await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
        await engine.close()
        restarted = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
        try:
            assert (await restarted.check_availability()).reason == "genlook_budget_exhausted"
            with pytest.raises(ProviderFailure, match="genlook_budget_exhausted"):
                await restarted.generate(str(uuid4()), "b" * 64, inputs(), placements())
            assert len(fake.posts) == 3
        finally:
            await restarted.close()
    run(check())


def test_request_contract_upgrade_preserves_old_spend_ledger_and_blocks_replay(tmp_path, monkeypatch):
    async def check():
        fake = SyntheticService()
        configured = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        new_contract = mod.CONTRACT
        with monkeypatch.context() as old:
            old.setattr(mod, "CONTRACT", "genlook-sequential-v1-20261106")
            prior = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
            await prior.generate(str(uuid4()), "a" * 64, inputs(), placements())
            await prior.close()
        assert mod.CONTRACT == new_contract
        ledger = tmp_path / "owned-ledger" / "ledger.json"
        before = ledger.read_bytes()
        calls_before = list(fake.calls)
        upgraded = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
        try:
            assert (await upgraded.check_availability()).reason == "genlook_journal_unavailable"
            with pytest.raises(ProviderFailure, match="genlook_journal_unavailable"):
                await upgraded.generate(str(uuid4()), "b" * 64, inputs(), placements())
            assert ledger.read_bytes() == before
            assert fake.calls == calls_before
            assert len(fake.posts) == 3
        finally:
            await upgraded.close()
    run(check())


@pytest.mark.parametrize("case,paid", [("post_lost", 1), ("upload_lost", 0), ("mismatched_id", 1)])
def test_ambiguous_remote_work_is_never_reposted_or_declared_cancelled(tmp_path, case, paid):
    async def check():
        fake = SyntheticService(case=case)
        configured = settings(tmp_path)
        engine = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
        identity = str(uuid4())
        with pytest.raises(ProviderFailure) as failure:
            await engine.generate(identity, "a" * 64, inputs(), placements())
        assert "SENSITIVE" not in str(failure.value)
        assert engine._ledger["jobs"][identity]["status"] == "uncertain"
        with pytest.raises(ProviderFailure, match="remote_execution_unconfirmed"):
            await engine.cancel(identity)
        assert len(fake.posts) == paid and fake.deletes == []
        await engine.close()
        restarted = mod.GenlookProvider(configured, transport=httpx.MockTransport(fake))
        try:
            assert (await restarted.check_availability()).reason == "genlook_reconciliation_required"
            with pytest.raises(ProviderFailure, match="genlook_reconciliation_required"):
                await restarted.generate(identity, "a" * 64, inputs(), placements())
            assert len(fake.posts) == paid
        finally:
            await restarted.close()
    run(check())


@pytest.mark.parametrize("case", ["redirect", "bad_host", "bad_mime", "corrupt", "oversize", "unchanged"])
def test_result_url_and_actual_image_validation_block_bad_outputs(tmp_path, case):
    async def check():
        fake = SyntheticService(case=case)
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure):
                await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            assert len(fake.posts) == 1 and len(fake.deletes) == 2
            assert fake.deletes[-1].endswith("-tops")
            assert all(host != "evil.invalid" for _, host, _ in fake.calls)
        finally:
            await engine.close()
    run(check())


def test_remote_failure_does_not_expose_error_text_or_execute_next_stage(tmp_path):
    async def check():
        fake = SyntheticService(case="remote_failed")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match="genlook_remote_failed") as failure:
                await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            assert "SENSITIVE" not in str(failure.value)
            assert len(fake.posts) == 1 and len(fake.deletes) == 2
            assert fake.deletes[-1].endswith("-tops")
        finally:
            await engine.close()
    run(check())


def test_cleanup_failure_keeps_readiness_blocked_and_no_result_success(tmp_path):
    async def check():
        fake = SyntheticService(case="delete_failure")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure):
                await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            assert (await engine.check_availability()).reason == "genlook_cleanup_required"
            assert len(fake.posts) == 3
        finally:
            await engine.close()
    run(check())


def test_partial_cleanup_does_not_delete_never_submitted_products(tmp_path):
    async def check():
        fake = SyntheticService(case="remote_failed")

        async def real_missing_product_contract(request):
            if request.method == "DELETE" and "/products/" in request.url.path:
                created = {body["products"][0]["externalId"] for body, _ in fake.posts}
                if request.url.path.rsplit("/", 1)[-1] not in created:
                    fake.deletes.append(request.url.path)
                    return httpx.Response(404, json={"code": "PRODUCT_NOT_FOUND"})
            return await fake(request)

        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(real_missing_product_contract))
        identity = str(uuid4())
        try:
            with pytest.raises(ProviderFailure, match="genlook_remote_failed"):
                await engine.generate(identity, "a" * 64, inputs(), placements())
            job = engine._ledger["jobs"][identity]
            assert job["status"] == "failed"
            assert fake.deletes == ["/tryon/v1/customers/" + job["customer_id"],
                                    "/tryon/v1/products/" + job["stages"][0]["product_id"]]
            assert [s["state"] for s in job["stages"]] == ["failed", "unsubmitted", "unsubmitted"]
            assert len(fake.posts) == 1
            assert (await engine.check_availability()).reason != "genlook_cleanup_required"
        finally:
            await engine.close()
    run(check())


def test_cancel_during_polling_retains_uncertainty_and_spend_reservation(tmp_path):
    async def check():
        fake = SyntheticService(case="wait")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        identity = str(uuid4())
        task = asyncio.create_task(engine.generate(identity, "a" * 64, inputs(), placements()))
        await asyncio.wait_for(fake.poll_started.wait(), 2)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        with pytest.raises(ProviderFailure, match="remote_execution_unconfirmed"):
            await engine.cancel(identity)
        assert engine._ledger["jobs"][identity]["credits_reserved"] == 3
        assert len(fake.posts) == 1 and fake.deletes == []
        await engine.close()
    run(check())


def test_api_cancel_before_join_has_one_cleanup_owner_and_no_next_upload(tmp_path):
    async def check():
        fake = SyntheticService(case="pause_after_stage")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        identity = str(uuid4())
        task = asyncio.create_task(engine.generate(identity, "a" * 64, inputs(), placements()))
        await asyncio.wait_for(fake.poll_started.wait(), 2)
        # Exact API ordering: task.cancel -> provider.cancel -> join.
        task.cancel()
        await engine.cancel(identity)
        with pytest.raises(asyncio.CancelledError):
            await task
        assert len(fake.posts) == 1 and len(fake.uploads) == 1
        assert len(fake.deletes) == 2 and len(set(fake.deletes)) == 2
        assert fake.deletes[-1].endswith("-tops")
        assert engine._ledger["jobs"][identity]["status"] == "cancelled"
        await engine.close()
    run(check())


def test_poll_timeout_does_not_start_a_second_generation(tmp_path, monkeypatch):
    async def expired(_):
        raise TimeoutError()
    monkeypatch.setattr(mod.asyncio, "sleep", expired)
    async def check():
        fake = SyntheticService(case="polling")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match="genlook_timeout"):
                await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            assert len(fake.posts) == 1 and (await engine.check_availability()).reason == "genlook_reconciliation_required"
        finally:
            await engine.close()
    run(check())


def test_unowned_directory_and_missing_journal_are_not_adopted(tmp_path):
    root = tmp_path / "owned-ledger"
    root.mkdir()
    private = root / "do-not-delete.txt"
    private.write_text("synthetic existing record")
    engine = mod.GenlookProvider(settings(tmp_path))
    assert run(engine.check_availability()).reason == "genlook_journal_unavailable"
    assert private.read_text() == "synthetic existing record"


def test_webp_is_decoded_and_encoded_as_png():
    raw = png((20, 50, 70), format="WEBP")
    converted = mod.verified_png(raw, "image/webp")
    assert converted.startswith(b"\x89PNG\r\n\x1a\n")
    with Image.open(io.BytesIO(converted)) as image:
        assert image.format == "PNG" and image.size == (64, 80)


def test_httpx_and_nested_httpcore_logs_redact_only_this_transport_context(tmp_path, caplog):
    caplog.set_level(logging.DEBUG)
    async def check():
        fake = SyntheticService(case="logging")
        engine = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            await engine.generate(str(uuid4()), "a" * 64, inputs(), placements())
            logging.getLogger("httpx").info("unrelated HTTP trace remains visible")
        finally:
            await engine.close()
    run(check())
    assert "Genlook HTTP transport details redacted" in caplog.text
    assert "unrelated HTTP trace remains visible" in caplog.text
    assert "SENSITIVE_SIGNED_URL" not in caplog.text
    assert "gk_synthetic_test_only" not in caplog.text
    assert "storage.googleapis.com/synthetic" not in caplog.text
