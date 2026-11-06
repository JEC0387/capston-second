"""Offline, explicitly reviewed region compositing; never generative inference.

Only the supplied edit mask may receive supplied result pixels. All pixels outside
it and all protected pixels are copied from the orientation-normalized original.
There is no automatic person segmentation, image registration or quality approval.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import io
import re

from PIL import Image, ImageChops, ImageFilter, ImageOps


MAX_IMAGE_BYTES = 8 * 1024 * 1024
MAX_PIXELS = 12_000_000
MAX_SIDE = 6000
MAX_FEATHER = 32
_SHA = re.compile(r"[a-f0-9]{64}\Z")
_REVIEW_ID = re.compile(r"[A-Za-z0-9._-]{1,80}\Z")


class RegionPreservationError(ValueError):
    """Fixed public error codes; no original image metadata or exception messages."""


@dataclass(frozen=True)
class RegionReview:
    source_sha256: str
    result_sha256: str
    edit_mask_sha256: str
    protected_mask_sha256: str
    alignment_reviewed: bool = False
    alignment_method: str = "unverified"
    review_id: str = ""
    transform_note: str = ""


@dataclass(frozen=True)
class RegionComposite:
    png: bytes
    provenance: dict


def _digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _validate_review(review, originals):
    if (not isinstance(review, RegionReview) or review.alignment_reviewed is not True or
            not isinstance(review.review_id, str) or not _REVIEW_ID.fullmatch(review.review_id)):
        raise RegionPreservationError("review_required")
    if review.alignment_method not in {"same_pixel_frame", "reviewed_external_transform"}:
        raise RegionPreservationError("alignment_unknown")
    if (not isinstance(review.transform_note, str) or len(review.transform_note) > 300 or
            any(ord(c) < 32 for c in review.transform_note) or
            review.alignment_method == "reviewed_external_transform" and not review.transform_note.strip()):
        raise RegionPreservationError("alignment_unknown")
    expected = (review.source_sha256, review.result_sha256, review.edit_mask_sha256, review.protected_mask_sha256)
    for raw, digest in zip(originals, expected):
        if not isinstance(raw, bytes) or not 0 < len(raw) <= MAX_IMAGE_BYTES:
            raise RegionPreservationError("image_invalid")
        if not isinstance(digest, str) or not _SHA.fullmatch(digest) or _digest(raw) != digest:
            raise RegionPreservationError("hash_mismatch")


def _check_raster(image):
    if (min(image.size) < 2 or max(image.size) > MAX_SIDE or
            image.width * image.height > MAX_PIXELS or getattr(image, "n_frames", 1) != 1):
        raise RegionPreservationError("image_invalid")


def _photo(raw):
    try:
        with Image.open(io.BytesIO(raw)) as probe:
            _check_raster(probe)
            if probe.format not in {"PNG", "JPEG"} or probe.mode not in {"RGB", "RGBA", "L", "LA"}:
                raise RegionPreservationError("image_invalid")
            probe.verify()
        with Image.open(io.BytesIO(raw)) as image:
            image.load()
            orientation = image.getexif().get(274, 1)
            if type(orientation) is not int or orientation not in range(1, 9):
                raise RegionPreservationError("image_invalid")
            # Review masks refer to this display-oriented source frame, not raw JPEG storage coordinates.
            return ImageOps.exif_transpose(image).convert("RGBA")
    except RegionPreservationError:
        raise
    except Exception:
        raise RegionPreservationError("image_invalid") from None


def _mask(raw, size):
    try:
        with Image.open(io.BytesIO(raw)) as probe:
            _check_raster(probe)
            if probe.format != "PNG" or probe.mode != "L":
                raise RegionPreservationError("mask_invalid")
            if probe.size != size:
                raise RegionPreservationError("dimensions_mismatch")
            probe.verify()
        with Image.open(io.BytesIO(raw)) as image:
            image.load()
            if image.getexif().get(274, 1) != 1:
                raise RegionPreservationError("mask_invalid")
            colors = image.getcolors(maxcolors=3)
            if colors is None or any(value not in (0, 255) for _, value in colors):
                raise RegionPreservationError("mask_invalid")
            return image.copy()
    except RegionPreservationError:
        raise
    except Exception:
        raise RegionPreservationError("mask_invalid") from None


def _inward_alpha(editable: Image.Image, radius: int) -> Image.Image:
    if radius == 0:
        return editable.copy()
    alpha = Image.new("L", editable.size, 0)
    remaining = editable.copy()
    for depth in range(1, radius + 1):
        level = (255 * depth) // (radius + 1)
        alpha = ImageChops.lighter(alpha, remaining.point([0] + [level] * 255))
        # Explicit zero padding makes image edges noneditable too. Erosion cannot
        # spread generated pixels into background or across protected-mask edges.
        padded = ImageOps.expand(remaining, border=1, fill=0)
        eroded = padded.filter(ImageFilter.MinFilter(3))
        remaining = eroded.crop((1, 1, editable.width + 1, editable.height + 1))
    return ImageChops.lighter(alpha, remaining)


def compose_reviewed_regions(original: bytes, generated: bytes, edit_mask: bytes,
                             protected_mask: bytes, review: RegionReview,
                             inward_feather_px: int = 0) -> RegionComposite:
    """Combine already aligned inputs, keeping original RGBA values outside edits.

    An external transform must be applied and reviewed by the caller first; the
    transformed result bytes are hash-bound here. Same dimensions alone do not
    establish alignment. Acknowledgement is a caller attestation, not proof of
    human inspection or pixel correspondence. This function makes no network call.
    """
    _validate_review(review, (original, generated, edit_mask, protected_mask))
    if type(inward_feather_px) is not int or not 0 <= inward_feather_px <= MAX_FEATHER:
        raise RegionPreservationError("invalid_feather")
    source = _photo(original)
    result = _photo(generated)
    if result.size != source.size:
        raise RegionPreservationError("dimensions_mismatch")
    edits = _mask(edit_mask, source.size)
    protected = _mask(protected_mask, source.size)
    if edits.getbbox() is None:
        raise RegionPreservationError("edit_mask_empty")
    if edits.getextrema() == (255, 255):
        raise RegionPreservationError("edit_mask_all")
    if protected.getbbox() is None:
        raise RegionPreservationError("protected_mask_empty")
    effective = ImageChops.subtract(edits, protected)
    if effective.getbbox() is None:
        raise RegionPreservationError("no_editable_pixels")
    alpha = _inward_alpha(effective, inward_feather_px)
    output = Image.composite(result, source, alpha)
    # Force exact source bytes on the preserved region, including alpha. This is
    # independent of smoothing precision and does not modify either input object.
    output = Image.composite(output, source, effective)
    encoded = io.BytesIO()
    # Do not copy EXIF, ICC, comments or input-side metadata to the output.
    output.info.clear()
    output.save(encoded, format="PNG")
    png = encoded.getvalue()
    if len(png) > MAX_IMAGE_BYTES:
        raise RegionPreservationError("output_too_large")
    provenance = {
        "method": "region_composite",
        "generative_inference_performed": False,
        "new_inference": False,
        "not_raw_model_output": True,
        "requires_external_ai_label": True,
        "label_notice": "Locally region-composited from caller-supplied AI result; not a new inference or raw model output. Preserve this notice when displaying or distributing.",
        "generated_input_provenance": "caller_supplied_not_verified_here",
        "quality": "NEEDS_VISUAL_REVIEW",
        "review_id": review.review_id,
        "alignment_method": review.alignment_method,
        "transform_note_sha256": _digest(review.transform_note.encode()) if review.transform_note else None,
        "source_sha256": review.source_sha256,
        "result_sha256": review.result_sha256,
        "edit_mask_sha256": review.edit_mask_sha256,
        "protected_mask_sha256": review.protected_mask_sha256,
        "output_sha256": _digest(png),
        "width": output.width,
        "height": output.height,
        "pixel_frame": "exif_oriented_rgba",
        "color_profile": "metadata_stripped_no_colorimetric_equivalence_claim",
        "inward_feather_px": inward_feather_px,
        "editable_pixels": effective.histogram()[255],
        "preserved_pixels": output.width * output.height - effective.histogram()[255],
    }
    return RegionComposite(png, provenance)
