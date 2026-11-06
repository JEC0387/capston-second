"""Offline P57 Android decoder -> FastAPI -> strict product-reference proof.

Consumes only the synthetic artifacts produced by P57ServerImageContractTest.
No sockets, credentials, model, provider execution, billing or personal images.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
from types import SimpleNamespace

from PIL import Image
from genlook_product_references import ProductReference, ProductReferences
from genlook_outfit_provider import verified_png
from outfit_vton_api import parse_submission, MAX_INPUT_SIDE


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def rgba(raw):
    with Image.open(io.BytesIO(raw)) as image:
        image.load()
        return image.size, image.convert("RGBA").tobytes()


def verify(folder):
    record = json.loads((folder / "receipt.json").read_text(encoding="utf-8"))
    assert record["synthetic_only"] is True
    assert record["max_input_side"] == MAX_INPUT_SIDE == 2048
    assert sha((folder / "receipt.json").read_bytes()) == (folder / "receipt.sha256").read_text().split()[0]
    raw = (folder / "payload.json").read_bytes()
    assert sha(raw) == record["body_sha256"]
    identity, images, placements = parse_submission(raw,
        SimpleNamespace(policy_version="synthetic-policy"),
        SimpleNamespace(model_revision="synthetic-p57"))
    assert identity == record["request_id"]
    expected_sizes = {"person": (1024, 1353), "tops": (930, 1200),
                      "bottoms": (850, 1200), "shoes": (1200, 704)}
    report, refs, normalized = {}, {}, {}
    for role, expected_size in expected_sizes.items():
        original = (folder / (role + "-original.png")).read_bytes()
        wire = (folder / (role + "-wire.png")).read_bytes()
        receipt = record["images"][role]
        assert sha(original) == receipt["original_sha256"]
        assert sha(wire) == receipt["wire_sha256"]
        assert rgba(wire)[0] == rgba(images[role])[0] == expected_size
        assert rgba(original)[1] == rgba(wire)[1] == rgba(images[role])[1]
        assert sha(rgba(wire)[1]) == receipt["rgba_sha256"]
        # Metadata/PNG encoders may change file SHA. Provider reference hashes bind
        # the exact RGB-normalized input, never the camera/source file's bytes.
        provider_png = verified_png(images[role])
        source_png = verified_png(original)
        assert provider_png == source_png
        report[role] = dict(size=expected_size, original_sha256=sha(original),
            wire_sha256=sha(wire), server_sha256=sha(images[role]),
            provider_sha256=sha(provider_png), rgba_sha256=sha(rgba(wire)[1]), pixel_equal=True)
        if role != "person":
            normalized[role] = provider_png
            refs[role] = ProductReference(sha(source_png), "Synthetic " + role,
                "Synthetic pixel markers; not generated clothing", ((10, 10, 74, 74),))
    pack = ProductReferences(refs)
    crops = pack.prepare(normalized)
    for role, values in crops.items():
        with Image.open(io.BytesIO(normalized[role])) as image:
            assert rgba(values[0])[1] == image.crop((10, 10, 74, 74)).convert("RGBA").tobytes()
    # A different actual input must still fail, regardless of metadata handling.
    altered = dict(normalized)
    with Image.open(io.BytesIO(altered["bottoms"])) as image:
        image.putpixel((10, 10), (1, 2, 3)); output = io.BytesIO(); image.save(output, "PNG")
        altered["bottoms"] = output.getvalue()
    try:
        pack.prepare(altered)
    except ValueError as error:
        assert str(error) == "reference_input_mismatch"
    else:
        raise AssertionError("Changed product pixels bypassed strict reference validation")
    return dict(synthetic_only=True, actual_android_wire=True, real_ai=False,
        body_sha256=sha(raw), request_id=identity, normalized_roles=list(placements),
        wrong_product_rejected=True, images=report)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()
    result = verify(args.folder)
    with (args.folder / "SERVER_VERIFIED.json").open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)
    print("PASS: synthetic Android wire/FastAPI/reference pixels and strict mismatch rejection")
