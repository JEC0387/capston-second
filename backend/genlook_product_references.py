"""Operator-reviewed, input-bound detail crops. No networking or spending permission.

These are additional views of the SAME source pixels, not new viewing angles or
reconstructed details. The full product stays first. Disabled unless supplied by
the server operator; an Android wardrobe item cannot select another item's pack.
"""
from dataclasses import asdict, dataclass
import hashlib
import io
import re
from types import MappingProxyType

from PIL import Image

ROLES = ("tops", "bottoms", "shoes")


@dataclass(frozen=True)
class ProductReference:
    normalized_sha256: str
    title: str
    description: str
    boxes: tuple[tuple[int, int, int, int], ...]

    def __post_init__(self):
        if (not isinstance(self.normalized_sha256, str) or
                not re.fullmatch(r"[a-f0-9]{64}", self.normalized_sha256)):
            raise ValueError("invalid_reference_hash")
        for text, limit in ((self.title, 120), (self.description, 600)):
            if not isinstance(text, str) or not 1 <= len(text.strip()) <= limit or any(ord(c) < 32 for c in text):
                raise ValueError("invalid_reference_description")
        if not isinstance(self.boxes, tuple) or not 1 <= len(self.boxes) <= 3:
            raise ValueError("invalid_reference_boxes")
        for box in self.boxes:
            if (not isinstance(box, tuple) or len(box) != 4 or any(type(x) is not int for x in box) or
                    min(box) < 0 or box[2] - box[0] < 32 or box[3] - box[1] < 32):
                raise ValueError("invalid_reference_box")


class ProductReferences:
    def __init__(self, references):
        if (not isinstance(references, dict) or set(references) != set(ROLES) or
                any(not isinstance(value, ProductReference) for value in references.values())):
            raise ValueError("invalid_reference_roles")
        self._references = MappingProxyType(dict(references))

    def manifest(self):
        return {"version": "source-pixel-details-v1",
                "products": {role: asdict(self._references[role]) for role in ROLES}}

    def prepare(self, normalized):
        """All roles validated before balance lookup, upload or paid POST."""
        result = {}
        for role in ROLES:
            spec = self._references[role]
            raw = normalized[role]
            if hashlib.sha256(raw).hexdigest() != spec.normalized_sha256:
                raise ValueError("reference_input_mismatch")
            with Image.open(io.BytesIO(raw)) as image:
                image.load()
                if image.format != "PNG" or image.mode != "RGB":
                    raise ValueError("reference_requires_normalized_png")
                crops = []
                for left, top, right, bottom in spec.boxes:
                    if right > image.width or bottom > image.height:
                        raise ValueError("reference_box_outside_image")
                    output = io.BytesIO()
                    image.crop((left, top, right, bottom)).save(output, format="PNG")
                    crops.append(output.getvalue())
            result[role] = tuple(crops)
        return result

    def attach(self, body, files, roles, prepared):
        for role, product in zip(roles, body["products"]):
            spec = self._references[role]
            product["title"] = spec.title
            # Keep the baseline non-target/identity constraints; replace the
            # ineffective P55 generic detail policy with factual source metadata.
            product["description"] = spec.description + " " + product["description"]
            for index, raw in enumerate(prepared[role]):
                key = role + "_detail_" + str(index + 1)
                files[key] = (key + ".png", raw, "image/png")
                product["images"].append({"source": {"fileKey": key}})
