"""Offline request/ledger regressions; not evidence of improved generated pixels."""
import asyncio
import hashlib
import json
from uuid import uuid4

import httpx
import pytest

import genlook_outfit_provider as mod
from outfit_vton_api import verified_provider_info, verified_provider_source
from genlook_request_contract import RequestStage, build_stage_body
from tests.test_genlook_p51_pair import PairService, images, placements, settings


def body(roles, **options):
    return build_stage_body(RequestStage(roles), image_id="input", customer_id="test-person",
                            product_ids={role: "test-" + role for role in roles}, **options)


def test_baseline_remains_exactly_the_default_and_has_no_new_detail_instructions():
    for roles in (("tops",), ("bottoms",), ("shoes",), ("tops", "bottoms")):
        assert body(roles) == body(roles, detail_policy="p54")
        assert all("Do not add" not in item["description"] for item in body(roles)["products"])


def test_detail_policy_only_changes_documented_product_descriptions():
    for roles in (("tops",), ("bottoms",), ("shoes",), ("tops", "bottoms")):
        original = body(roles)
        detailed = body(roles, detail_policy="reference-details-v1")
        for before, after in zip(original["products"], detailed["products"]):
            description = after["description"]
            assert "face, hair, hands, body pose and background" in description
            assert "Do not add" in description
            assert "visible in the supplied product image" in description
            after["description"] = before["description"]
        assert detailed == original  # Same IDs, images, retention, resolution and label.


def test_roles_preserve_visible_details_without_asserting_unknown_product_attributes():
    pair = body(("tops", "bottoms"), detail_policy="reference-details-v1")["products"]
    assert "button count" in pair[0]["description"] and "pocket" in pair[0]["description"]
    assert "waistband" in pair[1]["description"] and "pleats" in pair[1]["description"]
    assert all("existing lower-body clothing" not in item["description"] for item in pair)
    shoe = body(("shoes",), detail_policy="reference-details-v1")["products"][0]["description"]
    assert "toe" in shoe and "heel" in shoe and "pattern" in shoe
    assert "fasteners, pockets" in shoe  # Preserve earlier generated garment details too.
    for item in pair + [{"description": shoe}]:
        assert "one button" not in item["description"] and "two buttons" not in item["description"]
        assert "jacket" not in item["description"] and "striped" not in item["description"]


@pytest.mark.parametrize("policy", ["", "unknown", None, {"prompt": "unreviewed"}])
def test_unknown_policy_rejected_before_provider_io(tmp_path, policy):
    with pytest.raises(ValueError, match="invalid_detail_policy"):
        body(("tops",), detail_policy=policy)
    with pytest.raises(ValueError, match="invalid_detail_policy"):
        mod.GenlookProvider(settings(tmp_path), detail_policy=policy)


def test_pair_default_contract_digest_and_revision_are_unchanged(tmp_path):
    config = settings(tmp_path)
    identity = {name: value for name, value in vars(config).items() if name not in {"api_key", "ledger_dir"}}
    identity.update(api=mod.API, contract="genlook-paired-v1-20261106",
        account_fingerprint=hashlib.sha256(config.api_key.encode()).hexdigest(),
        mode="clothing-pair-then-shoes", roles=("tops", "bottoms", "shoes"), keep_for_days=1,
        crop=False, resolution="default", remote_cancel_supported=False)
    digest = hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    provider = mod.GenlookProvider(config)
    assert provider.contract_sha == digest
    assert provider.revision == "genlook-paired-v1-20261106-" + digest[:16]


def test_actual_adapter_uses_detail_contract_without_quality_success_or_extra_calls(tmp_path):
    async def check():
        fake = PairService()
        config = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        baseline = mod.GenlookProvider(config)
        provider = mod.GenlookProvider(config, detail_policy="reference-details-v1", transport=httpx.MockTransport(fake))
        try:
            assert provider.contract_sha != baseline.contract_sha
            result = await provider.generate(str(uuid4()), "a" * 64, images(), placements())
            available = await provider.check_availability()
            # Existing API/Android paired-v1 wire contract is retained; its digest
            # distinguishes the new prompt policy without changing receipt shape.
            assert verified_provider_info(available.provider_info, provider.revision)["mode"] == mod.PAIRED_MODE
            assert verified_provider_source(result.source, mod.MODEL, provider.revision) == result.source
            assert len(fake.posts) == 2 and fake.balance == 6
            assert all("Do not add" in p["description"] for b, _ in fake.posts for p in b["products"])
            assert result.source["quality"] == "NOT_EVALUATED"
            assert result.model_revision == provider.revision
            assert result.source["credits_reserved"] == 3
            assert (await provider.check_availability()).reason == "genlook_budget_exhausted"
        finally:
            await provider.close()
        before = (tmp_path / "pair-ledger" / "ledger.json").read_bytes()
        calls = list(fake.calls)
        incompatible = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert (await incompatible.check_availability()).reason == "genlook_journal_unavailable"
            assert (tmp_path / "pair-ledger" / "ledger.json").read_bytes() == before
            assert fake.calls == calls
        finally:
            await incompatible.close()
    asyncio.run(check())


def test_detail_policy_never_grants_generation_permission(tmp_path):
    async def check():
        fake = PairService()
        provider = mod.GenlookProvider(mod.GenlookSettings(), detail_policy="reference-details-v1",
                                      transport=httpx.MockTransport(fake))
        try:
            assert (await provider.check_availability()).reason == "genlook_disabled"
            assert fake.calls == []
        finally:
            await provider.close()
    asyncio.run(check())
