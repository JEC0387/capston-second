"""Offline pixel and wire regressions; no claim of generated product fidelity."""
import asyncio
import hashlib
import io
from uuid import uuid4

import httpx
from PIL import Image
import pytest

import genlook_outfit_provider as mod
from genlook_product_references import ProductReference, ProductReferences
from outfit_vton_provider import ProviderFailure
from tests.test_genlook_p51_pair import PairService, images, placements, settings


def pack(inputs, box=(0, 0, 32, 40)):
    return ProductReferences({role: ProductReference(
        hashlib.sha256(mod.verified_png(inputs[role])).hexdigest(),
        "Synthetic " + role, "Reviewed synthetic fixture.", (box,)) for role in mod.ROLES})


def test_crops_are_exact_original_pixels_without_resampling_or_reconstruction():
    inputs = images()
    im = Image.new("RGB", (64, 80))
    im.putdata([(x, y, (x + y) % 256) for y in range(80) for x in range(64)])
    stream = io.BytesIO(); im.save(stream, format="PNG"); inputs["tops"] = stream.getvalue()
    normalized = {role: mod.verified_png(raw) for role, raw in inputs.items()}
    cropped = pack(inputs, (10, 20, 50, 70)).prepare(normalized)
    with Image.open(io.BytesIO(cropped["tops"][0])) as actual:
        assert actual.size == (40, 50)
        assert list(actual.getdata()) == [(x, y, x + y) for y in range(20, 70) for x in range(10, 50)]
    assert inputs["tops"] == stream.getvalue()


@pytest.mark.parametrize("fault", ["different_garment", "out_of_bounds"])
def test_wrong_input_or_box_blocks_before_any_http_or_reservation(tmp_path, fault):
    async def check():
        original = images(); changed = images()
        references = pack(original, (0, 0, 65, 80) if fault == "out_of_bounds" else (0, 0, 32, 40))
        if fault == "different_garment":
            changed["tops"] = changed["bottoms"]
        fake = PairService()
        provider = mod.GenlookProvider(settings(tmp_path), product_references=references, transport=httpx.MockTransport(fake))
        try:
            with pytest.raises(ProviderFailure, match="invalid_provider_input"):
                await provider.generate(str(uuid4()), "a" * 64, changed, placements())
            assert fake.calls == [] and provider._ledger["jobs"] == {}
        finally:
            await provider.close()
    asyncio.run(check())


def test_references_sent_as_same_product_full_image_first_with_no_extra_paid_posts(tmp_path):
    async def check():
        inputs = images(); references = pack(inputs); fake = PairService()
        config = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        baseline = mod.GenlookProvider(config)
        provider = mod.GenlookProvider(config, product_references=references, transport=httpx.MockTransport(fake))
        try:
            result = await provider.generate(str(uuid4()), "b" * 64, inputs, placements())
            assert provider.contract_sha != baseline.contract_sha
            assert len(fake.posts) == 2 and fake.balance == 6
            for (body, parts), roles in zip(fake.posts, (("tops", "bottoms"), ("shoes",))):
                assert len(body["products"]) == len(roles)
                for role, product in zip(roles, body["products"]):
                    primary = "product" if len(roles) == 1 else role
                    detail = role + "_detail_1"
                    assert product["images"] == [{"source": {"fileKey": primary}}, {"source": {"fileKey": detail}}]
                    assert parts[primary] == mod.verified_png(inputs[role])
                    assert parts[detail] == references.prepare({r: mod.verified_png(v) for r, v in inputs.items()})[role][0]
                    assert product["title"] == "Synthetic " + role
                    assert product["validForDays"] == 1
                    assert "Do not add" not in product["description"]
            assert result.source["quality"] == "NOT_EVALUATED"
            assert (await provider.check_availability()).reason == "genlook_budget_exhausted"
        finally:
            await provider.close()
        before = (tmp_path / "pair-ledger/ledger.json").read_bytes()
        calls = list(fake.calls)
        reopened = mod.GenlookProvider(config, transport=httpx.MockTransport(fake))
        try:
            assert (await reopened.check_availability()).reason == "genlook_journal_unavailable"
            assert fake.calls == calls
            assert (tmp_path / "pair-ledger/ledger.json").read_bytes() == before
        finally:
            await reopened.close()
    asyncio.run(check())


def test_reference_pack_does_not_grant_permission_or_allow_p55_policy_stacking(tmp_path):
    async def check():
        fake = PairService()
        provider = mod.GenlookProvider(mod.GenlookSettings(), product_references=pack(images()), transport=httpx.MockTransport(fake))
        try:
            assert (await provider.check_availability()).reason == "genlook_disabled"
            assert fake.calls == []
        finally:
            await provider.close()
    asyncio.run(check())
    with pytest.raises(ValueError, match="conflicting"):
        mod.GenlookProvider(settings(tmp_path), product_references=pack(images()), detail_policy="reference-details-v1")


@pytest.mark.parametrize("box", [(0, 0, 0, 80), (-1, 0, 64, 80), (False, 0, 64, 80), [0, 0, 64, 80]])
def test_invalid_boxes_rejected(box):
    with pytest.raises(ValueError, match="reference_box"):
        ProductReference("a" * 64, "Title", "Description", (box,))
