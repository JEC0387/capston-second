"""Synthetic HTTP only: paired requests are not evidence of actual AI quality."""
import asyncio
from dataclasses import replace
from email.parser import BytesParser
from email.policy import default
import hashlib
import io
import json
from uuid import uuid4

import httpx
from PIL import Image
import pytest

import genlook_outfit_provider as mod
from outfit_vton_provider import ProviderFailure


def png(color):
    out = io.BytesIO()
    Image.new("RGB", (64, 80), color).save(out, format="PNG")
    return out.getvalue()


def images():
    return {"person": png("white"), "tops": png("red"), "bottoms": png("blue"), "shoes": png("black")}


def placements():
    return {role: {"center_x": .5, "center_y": .5, "width": .2, "scale": 1, "rotation": 0} for role in mod.ROLES}


def settings(tmp_path, **changes):
    value = mod.GenlookSettings(
        enabled=True, deployment_approved=True, paid_calls_approved=True, privacy_approved=True,
        prepaid_confirmed=True, auto_topup_disabled_confirmed=True, api_key="gk_synthetic_pair_only",
        terms_review_id="test-terms", privacy_review_id="test-privacy", cost_review_id="test-cost",
        ledger_dir=str(tmp_path / "pair-ledger"), max_total_credits=6,
        unit_price_microusd=40000, max_total_microusd=240000, timeout_seconds=30,
        mode=mod.PAIRED_MODE)
    return replace(value, **changes)


def multipart(request, raw):
    value = BytesParser(policy=default).parsebytes(
        b"Content-Type: " + request.headers["content-type"].encode() + b"\r\nMIME-Version: 1.0\r\n\r\n" + raw)
    return {part.get_param("name", header="content-disposition"): part.get_payload(decode=True)
            for part in value.iter_parts()}


class PairService:
    def __init__(self, *, lost_post=None, mismatch_products=False, fail_first=False, depleted_after_pair=False):
        self.balance = 9
        self.lost_post = lost_post
        self.mismatch_products = mismatch_products
        self.fail_first = fail_first
        self.depleted_after_pair = depleted_after_pair
        self.calls = []
        self.uploads = []
        self.posts = []
        self.deletes = []
        self.credit_balances = []
        self.outputs = [png((40 + index * 60, 20, 100)) for index in range(3)]

    async def __call__(self, request):
        self.calls.append((request.method, request.url.host, request.url.path))
        if request.url.host == "storage.googleapis.com":
            assert "x-api-key" not in request.headers and "authorization" not in request.headers
            index = int(request.url.path.rsplit("/", 1)[-1]) - 1
            return httpx.Response(200, content=self.outputs[index], headers={"content-type": "image/png"})
        assert request.url.host == "api.genlook.app"
        assert request.headers["x-api-key"] == "gk_synthetic_pair_only"
        if request.method == "GET" and request.url.path.endswith("/account/credits"):
            self.credit_balances.append(self.balance)
            return httpx.Response(200, json={"creditBalance": self.balance})
        if request.method == "POST" and request.url.path.endswith("/images/upload"):
            parts = multipart(request, await request.aread())
            self.uploads.append(parts)
            assert parts["crop"] == b"false" and parts["keepForDays"] == b"1"
            return httpx.Response(201, json={"imageId": "opaque/person/" + str(len(self.uploads))})
        if request.method == "POST" and request.url.path.endswith("/try-on"):
            parts = multipart(request, await request.aread())
            body = json.loads(parts["data"])
            self.posts.append((body, parts))
            self.balance -= 2 if len(body["products"]) > 1 else 1
            if self.depleted_after_pair:
                self.balance = 0
            if len(self.posts) == self.lost_post:
                raise httpx.ReadTimeout("synthetic lost response", request=request)
            product_ids = [product["externalId"] for product in body["products"]]
            if self.mismatch_products:
                product_ids = product_ids[:1]
            return httpx.Response(201, json={"generationId": "pair_generation_" + str(len(self.posts)),
                                           "status": "PENDING", "productExternalIds": product_ids})
        if request.method == "GET" and "/generations/" in request.url.path:
            identity = request.url.path.rsplit("/", 1)[-1]
            index = int(identity.rsplit("_", 1)[-1])
            if self.fail_first:
                return httpx.Response(200, json={"generationId": identity, "status": "FAILED"})
            return httpx.Response(200, json={"generationId": identity, "status": "COMPLETED",
                "resultImageUrl": "https://storage.googleapis.com/synthetic-pair/" + str(index)})
        if request.method == "DELETE":
            self.deletes.append(request.url.path)
            return httpx.Response(204)
        raise AssertionError("Unexpected synthetic paired HTTP contract")


def test_pair_sends_two_generations_reserves_three_and_feeds_first_result_to_shoes(tmp_path):
    async def check():
        fake = PairService()
        provider = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        identity = str(uuid4())
        inputs = images()
        try:
            output = await provider.generate(identity, "a" * 64, inputs, placements())
            assert len(fake.posts) == 2 and fake.credit_balances == [9, 9, 7, 6]
            assert set(fake.posts[0][1]) == {"data", "tops", "bottoms"}
            assert set(fake.posts[1][1]) == {"data", "product"}
            assert fake.posts[0][1]["tops"] == mod.verified_png(inputs["tops"])
            assert fake.posts[0][1]["bottoms"] == mod.verified_png(inputs["bottoms"])
            assert fake.posts[1][1]["product"] == mod.verified_png(inputs["shoes"])
            assert fake.uploads[0]["file"] == mod.verified_png(inputs["person"])
            assert fake.uploads[1]["file"] == mod.verified_png(fake.outputs[0])
            assert output.png == mod.verified_png(fake.outputs[1])
            for index, (body, _) in enumerate(fake.posts):
                assert body["person"]["image"]["source"] == {"id": "opaque/person/" + str(index + 1)}
                assert body["output"] == {"resolution": "default", "aiLabel": True, "keepForDays": 1}
            assert output.source["mode"] == "clothing-pair-then-shoes"
            assert output.source["generation_ids"] == ["pair_generation_1", "pair_generation_2"]
            assert output.source["credits_reserved"] == 3 and output.source["quality"] == "NOT_EVALUATED"
            assert output.source["credits_balance_before"] == 9 and output.source["credits_balance_after"] == 6
            assert output.model_revision.startswith("genlook-paired-v1-20261106-")
            job = provider._ledger["jobs"][identity]
            assert provider._ledger["version"] == 2 and job["status"] == "completed"
            assert [stage["roles"] for stage in job["stages"]] == [["tops", "bottoms"], ["shoes"]]
            all_products = {value for stage in job["stages"] for value in stage["product_ids"].values()}
            assert len(all_products) == 3
            assert set(fake.deletes) == {"/tryon/v1/customers/" + job["customer_id"],
                                         *("/tryon/v1/products/" + value for value in all_products)}
            assert len(fake.deletes) == 4
            journal = (tmp_path / "pair-ledger" / "ledger.json").read_text()
            assert "gk_" not in journal and "https://" not in journal and "opaque/person" not in journal
        finally:
            await provider.close()
    asyncio.run(check())


def test_default_mode_preserves_pre_p51_digest_revision_and_version_one_ledger(tmp_path):
    async def check():
        config = settings(tmp_path, mode=mod.SEQUENTIAL_MODE)
        old_identity = {key: value for key, value in vars(config).items()
                        if key not in {"api_key", "ledger_dir", "mode"}}
        old_identity.update(api=mod.API, contract="genlook-sequential-v2-preservation-20261106",
            account_fingerprint=hashlib.sha256(config.api_key.encode()).hexdigest(), mode="sequential-3",
            roles=mod.ROLES, keep_for_days=1, crop=False, resolution="default", remote_cancel_supported=False)
        expected = hashlib.sha256(json.dumps(old_identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        fake = PairService()
        provider = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert mod.GenlookSettings().mode == "sequential-3"
            assert provider.contract_sha == expected
            assert provider.revision == "genlook-sequential-v2-preservation-20261106-" + expected[:16]
            output = await provider.generate(str(uuid4()), "a" * 64, images(), placements())
            assert len(fake.posts) == 3 and output.source["mode"] == "sequential-3"
            assert provider._ledger["version"] == 1
            assert [stage["role"] for stage in next(iter(provider._ledger["jobs"].values()))["stages"]] == list(mod.ROLES)
        finally:
            await provider.close()
        before = (tmp_path / "pair-ledger" / "ledger.json").read_bytes()
        calls_before = len(fake.calls)
        restored = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert (await restored.check_availability()).ready
            assert (tmp_path / "pair-ledger" / "ledger.json").read_bytes() == before
            assert len(fake.calls) == calls_before
        finally:
            await restored.close()
    asyncio.run(check())


@pytest.mark.parametrize("first_mode,next_mode", [(mod.SEQUENTIAL_MODE, mod.PAIRED_MODE), (mod.PAIRED_MODE, mod.SEQUENTIAL_MODE)])
def test_mode_switch_refuses_existing_ledger_without_reset_or_network(tmp_path, first_mode, next_mode):
    async def check():
        fake = PairService()
        original = mod.GenlookProvider(settings(tmp_path, mode=first_mode), transport=httpx.MockTransport(fake))
        try:
            await original.generate(str(uuid4()), "a" * 64, images(), placements())
        finally:
            await original.close()
        before = (tmp_path / "pair-ledger" / "ledger.json").read_bytes()
        calls_before = list(fake.calls)
        switched = mod.GenlookProvider(settings(tmp_path, mode=next_mode), transport=httpx.MockTransport(fake))
        try:
            assert (await switched.check_availability()).reason == "genlook_journal_unavailable"
            assert (tmp_path / "pair-ledger" / "ledger.json").read_bytes() == before
            assert fake.calls == calls_before
        finally:
            await switched.close()
    asyncio.run(check())


@pytest.mark.parametrize("lost_post", [1, 2])
def test_lost_paid_response_blocks_replay_and_keeps_full_reservation(tmp_path, lost_post):
    async def check():
        fake = PairService(lost_post=lost_post)
        config = settings(tmp_path)
        provider = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        identity = str(uuid4())
        try:
            with pytest.raises(ProviderFailure, match="genlook_unavailable"):
                await provider.generate(identity, "a" * 64, images(), placements())
            job = provider._ledger["jobs"][identity]
            assert job["status"] == "uncertain" and job["credits_reserved"] == 3
            assert len(fake.posts) == lost_post and fake.deletes == []
        finally:
            await provider.close()
        calls_before = list(fake.calls)
        restarted = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert (await restarted.check_availability()).reason == "genlook_reconciliation_required"
            with pytest.raises(ProviderFailure, match="genlook_reconciliation_required"):
                await restarted.generate(str(uuid4()), "b" * 64, images(), placements())
            assert fake.calls == calls_before
        finally:
            await restarted.close()
    asyncio.run(check())


def test_pair_product_identity_mismatch_never_starts_shoes_or_claims_success(tmp_path):
    async def check():
        fake = PairService(mismatch_products=True)
        provider = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match="genlook_invalid_response"):
                await provider.generate(str(uuid4()), "a" * 64, images(), placements())
            assert len(fake.posts) == 1 and len(fake.uploads) == 1 and fake.deletes == []
            assert (await provider.check_availability()).reason == "genlook_reconciliation_required"
        finally:
            await provider.close()
    asyncio.run(check())


@pytest.mark.parametrize("case,code", [("fail_first", "genlook_remote_failed"), ("depleted_after_pair", "genlook_insufficient_credits")])
def test_pair_failure_or_insufficient_remaining_credit_stops_shoes_and_cleans_both_products(tmp_path, case, code):
    async def check():
        fake = PairService(**{case: True})
        provider = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match=code):
                await provider.generate(str(uuid4()), "a" * 64, images(), placements())
            assert len(fake.posts) == 1 and len(fake.uploads) == 1
            assert len(fake.deletes) == 3
            assert any(path.endswith("-tops") for path in fake.deletes)
            assert any(path.endswith("-bottoms") for path in fake.deletes)
            assert not any(path.endswith("-shoes") for path in fake.deletes)
            job = next(iter(provider._ledger["jobs"].values()))
            assert job["status"] == "failed" and job["credits_reserved"] == 3
        finally:
            await provider.close()
    asyncio.run(check())


def test_paired_restart_retains_spend_limit_and_never_replays_completed_job(tmp_path):
    async def check():
        fake = PairService()
        config = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        first = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            await first.generate(str(uuid4()), "a" * 64, images(), placements())
        finally:
            await first.close()
        before = (tmp_path / "pair-ledger" / "ledger.json").read_bytes()
        calls_before = list(fake.calls)
        restarted = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert (await restarted.check_availability()).reason == "genlook_budget_exhausted"
            with pytest.raises(ProviderFailure, match="genlook_budget_exhausted"):
                await restarted.generate(str(uuid4()), "b" * 64, images(), placements())
            assert fake.calls == calls_before
            assert (tmp_path / "pair-ledger" / "ledger.json").read_bytes() == before
        finally:
            await restarted.close()
    asyncio.run(check())


def test_mode_environment_selection_is_explicit_and_unknown_mode_is_blocked(tmp_path, monkeypatch):
    monkeypatch.setattr(mod.os, "getenv", lambda name, fallback=None:
                        mod.PAIRED_MODE if name == "GENLOOK_MODE" else fallback)
    assert mod.GenlookSettings.environment().mode == mod.PAIRED_MODE
    fake = PairService()
    provider = mod.GenlookProvider(settings(tmp_path, mode="any-three-products"), transport=httpx.MockTransport(fake))
    async def check():
        try:
            assert (await provider.check_availability()).reason == "genlook_policy_unreviewed"
            assert fake.calls == [] and not (tmp_path / "pair-ledger").exists()
        finally:
            await provider.close()
    asyncio.run(check())
