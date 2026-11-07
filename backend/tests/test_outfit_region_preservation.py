"""Synthetic offline region tests; not model quality or actual body segmentation."""
from dataclasses import replace
import hashlib
import io

from PIL import Image, ImageDraw, ImageOps
import pytest

from outfit_region_preservation import RegionPreservationError, RegionReview, compose_reviewed_regions


def encode(image, format="PNG", **options):
    output = io.BytesIO()
    image.save(output, format=format, **options)
    return output.getvalue()


def load(raw):
    with Image.open(io.BytesIO(raw)) as image:
        image.load()
        return image.convert("RGBA")


def fixture():
    original = Image.new("RGBA", (32, 32), (10, 30, 50, 140))
    generated = Image.new("RGBA", (32, 32), (240, 210, 180, 250))
    edits = Image.new("L", (32, 32), 0)
    ImageDraw.Draw(edits).rectangle((6, 6, 25, 25), fill=255)
    protected = Image.new("L", (32, 32), 0)
    # Protected face/hand surrogate overlaps editable clothing surrogate.
    ImageDraw.Draw(protected).rectangle((8, 8, 11, 11), fill=255)
    return tuple(encode(image) for image in (original, generated, edits, protected))


def reviewed(values, **changes):
    hashes = [hashlib.sha256(raw).hexdigest() for raw in values]
    return RegionReview(*hashes, alignment_reviewed=True, alignment_method="same_pixel_frame",
                        review_id="synthetic-review", **changes)


def test_outside_and_protected_pixels_are_exact_including_alpha():
    values = fixture()
    before = tuple(hashlib.sha256(raw).hexdigest() for raw in values)
    product = compose_reviewed_regions(*values, reviewed(values))
    source, generated, edits, protected = [load(raw) for raw in values]
    output = load(product.png)
    for y in range(32):
        for x in range(32):
            target = generated if edits.getpixel((x, y))[0] == 255 and protected.getpixel((x, y))[0] == 0 else source
            assert output.getpixel((x, y)) == target.getpixel((x, y))
    assert before == tuple(hashlib.sha256(raw).hexdigest() for raw in values)
    assert product.provenance["method"] == "region_composite"
    assert product.provenance["generative_inference_performed"] is False
    assert product.provenance["new_inference"] is False
    assert product.provenance["not_raw_model_output"] is True
    assert product.provenance["requires_external_ai_label"] is True
    assert product.provenance["quality"] == "NEEDS_VISUAL_REVIEW"
    assert product.provenance["output_sha256"] == hashlib.sha256(product.png).hexdigest()


def test_feather_is_inward_only_and_never_bleeds_through_protection():
    values = fixture()
    product = compose_reviewed_regions(*values, reviewed(values), inward_feather_px=2)
    output = load(product.png)
    source, result = load(values[0]), load(values[1])
    assert output.getpixel((5, 16)) == source.getpixel((5, 16))
    assert output.getpixel((8, 8)) == source.getpixel((8, 8))
    boundary = output.getpixel((6, 16))
    for channel, (start, end) in enumerate(zip(source.getpixel((6, 16)), result.getpixel((6, 16)))):
        assert start < boundary[channel] < end
    assert output.getpixel((16, 16)) == result.getpixel((16, 16))


@pytest.mark.parametrize("index", range(4))
def test_stale_source_result_or_mask_binding_is_rejected(index):
    values = fixture()
    review = reviewed(values)
    changed = list(values)
    image = Image.open(io.BytesIO(changed[index])).copy()
    image.putpixel((0, 0), 255 if image.mode == "L" else (20, 60, 90, 120))
    changed[index] = encode(image)
    with pytest.raises(RegionPreservationError, match="hash_mismatch"):
        compose_reviewed_regions(*changed, review)


def test_missing_review_unknown_alignment_and_unexplained_transform_are_rejected():
    values = fixture()
    good = reviewed(values)
    for review in (replace(good, alignment_reviewed=False),
                   replace(good, alignment_method="automatic_guess"),
                   replace(good, alignment_method="reviewed_external_transform", transform_note="")):
        with pytest.raises(RegionPreservationError):
            compose_reviewed_regions(*values, review)


def test_dimension_mismatch_is_not_silently_scaled_even_with_acknowledgement():
    values = list(fixture())
    values[1] = encode(Image.new("RGB", (31, 32), "blue"))
    review = replace(reviewed(values), alignment_method="reviewed_external_transform", transform_note="caller-reviewed affine result")
    with pytest.raises(RegionPreservationError, match="dimensions_mismatch"):
        compose_reviewed_regions(*values, review)


def test_empty_full_or_nonbinary_masks_are_rejected_without_output():
    baseline = fixture()
    for index, image, reason in (
        (2, Image.new("L", (32, 32), 0), "edit_mask_empty"),
        (2, Image.new("L", (32, 32), 255), "edit_mask_all"),
        (2, Image.new("L", (32, 32), 128), "mask_invalid"),
        (2, Image.new("RGB", (32, 32), "white"), "mask_invalid"),
        (2, Image.new("L", (31, 32), 255), "dimensions_mismatch"),
        (3, Image.new("L", (32, 32), 0), "protected_mask_empty"),
        (3, Image.new("L", (32, 32), 255), "no_editable_pixels"),
    ):
        values = list(baseline)
        values[index] = encode(image)
        with pytest.raises(RegionPreservationError, match=reason):
            compose_reviewed_regions(*values, reviewed(values))


def test_output_is_decodable_png_and_metadata_is_not_copied():
    values = list(fixture())
    source = Image.open(io.BytesIO(values[0])).copy()
    exif = Image.Exif()
    exif[270] = "SYNTHETIC PRIVATE COMMENT"
    values[0] = encode(source, exif=exif)
    product = compose_reviewed_regions(*values, reviewed(values), inward_feather_px=1)
    with Image.open(io.BytesIO(product.png)) as result:
        assert result.format == "PNG" and result.size == (32, 32)
        result.verify()
    assert b"SYNTHETIC PRIVATE COMMENT" not in product.png


def test_exif_oriented_original_requires_masks_in_oriented_frame():
    exif = Image.Exif()
    exif[274] = 6
    source = encode(Image.new("RGB", (40, 24), (20, 40, 60)), format="JPEG", exif=exif)
    generated = encode(Image.new("RGB", (24, 40), (200, 100, 50)))
    edits = Image.new("L", (24, 40), 0)
    ImageDraw.Draw(edits).rectangle((7, 7, 17, 30), fill=255)
    protected = Image.new("L", (24, 40), 0)
    ImageDraw.Draw(protected).rectangle((0, 0, 23, 4), fill=255)
    values = (source, generated, encode(edits), encode(protected))
    product = compose_reviewed_regions(*values, reviewed(values))
    output = load(product.png)
    with Image.open(io.BytesIO(source)) as image:
        oriented = ImageOps.exif_transpose(image).convert("RGBA")
    assert output.size == (24, 40) and output.getpixel((0, 0)) == oriented.getpixel((0, 0))
    assert product.provenance["pixel_frame"] == "exif_oriented_rgba"


def test_feather_limits_reject_noninteger_or_unbounded_work():
    values = fixture()
    for radius in (-1, 33, True, 1.5):
        with pytest.raises(RegionPreservationError, match="invalid_feather"):
            compose_reviewed_regions(*values, reviewed(values), inward_feather_px=radius)
