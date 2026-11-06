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


def test_reference_binding_survives_api_metadata_removal_without_changing_pixels():
    from outfit_vton_api import _canonical_image

    image = Image.new("RGB", (160, 200), (73, 117, 201))
    stream = io.BytesIO()
    image.save(stream, "PNG", icc_profile=b"synthetic-profile-metadata")
    inputs = {role: stream.getvalue() for role in mod.ROLES}
    references = pack(inputs)
    through_api = {role: mod.verified_png(_canonical_image(raw))
                   for role, raw in inputs.items()}
    crops = references.prepare(through_api)
    for role in mod.ROLES:
        assert mod.verified_png(inputs[role]) == through_api[role]
        with Image.open(io.BytesIO(through_api[role])) as actual:
            assert actual.size == (160, 200)
            assert actual.tobytes() == image.tobytes()
            assert "icc_profile" not in actual.info
        with Image.open(io.BytesIO(crops[role][0])) as actual:
            assert actual.size == (32, 40)
            assert actual.getpixel((10, 20)) == (73, 117, 201)


def test_reviewed_mobile_derivative_keeps_full_resolution_detail_pixels():
    source = Image.new("RGB", (160, 200))
    source.putdata([(x, y, (x + y) % 256) for y in range(200) for x in range(160)])
    out = io.BytesIO(); source.save(out, "PNG"); original = out.getvalue()
    out = io.BytesIO(); source.resize((80, 100)).save(out, "PNG"); derivative = out.getvalue()
    refs = {r: ProductReference(hashlib.sha256(derivative).hexdigest(), "Test", "Reviewed test",
                               ((40, 60, 120, 160),), hashlib.sha256(original).hexdigest())
            for r in mod.ROLES}
    references = ProductReferences(refs, detail_sources={r: original for r in mod.ROLES})
    result = references.prepare({r: derivative for r in mod.ROLES})
    for role in mod.ROLES:
        with Image.open(io.BytesIO(result[role][0])) as crop:
            assert crop.size == (80, 100)
            assert crop.getpixel((10, 20)) == (50, 80, 130)
        assert references.manifest()["products"][role]["detail_source_sha256"] == hashlib.sha256(original).hexdigest()
    with pytest.raises(ValueError, match="reference_input_mismatch"):
        references.prepare({r: original for r in mod.ROLES})
    with pytest.raises(ValueError, match="detail_source_hash_mismatch"):
        ProductReferences(refs, detail_sources={r: derivative for r in mod.ROLES})
    with pytest.raises(ValueError, match="detail_source_roles_mismatch"):
        ProductReferences(refs)


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
