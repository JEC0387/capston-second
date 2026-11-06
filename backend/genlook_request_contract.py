"""Pure request planning: no keys, networking, accounts or permission to spend.

Official /multi-product permits clothing combinations, not clothing plus shoes.
The adapter defaults to sequential-3; paired mode requires explicit selection.
Descriptions express requested preservation, not an API guarantee or a mask.
"""
from dataclasses import dataclass
import re

ROLES = ("tops", "bottoms", "shoes")
TITLES = {"tops": "Upper-body clothing", "bottoms": "Lower-body clothing", "shoes": "Footwear pair"}
DESCRIPTIONS = {
    "tops": "The supplied upper-body garment only. Preserve its visible colour, pattern and cut. "
            "Keep the person's existing lower-body clothing, its pattern and colour, and footwear unchanged.",
    "bottoms": "The supplied lower-body garment only. Preserve its visible colour, pattern and cut. "
               "Keep the person's upper-body garment and footwear unchanged.",
    "shoes": "The supplied footwear only. Apply it to both visible feet while preserving the product design. "
             "Keep the person's upper-body and lower-body clothing unchanged.",
}
IDENTITY = " Preserve the person's face, hair, hands, body pose and background."
SAFE_ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z")


@dataclass(frozen=True)
class RequestStage:
    roles: tuple[str, ...]
    resolution: str = "default"

    def __post_init__(self):
        if (not isinstance(self.roles, tuple) or not self.roles or
                any(not isinstance(role, str) or role not in ROLES for role in self.roles) or
                len(set(self.roles)) != len(self.roles)):
            raise ValueError("invalid_or_duplicate_roles")
        if "shoes" in self.roles and len(self.roles) > 1:
            raise ValueError("MULTI_PRODUCT_NOT_SUPPORTED")
        if self.resolution not in {"default", "2k"}:
            raise ValueError("invalid_resolution")

    @property
    def expected_credits(self):
        return (1 if len(self.roles) == 1 else 2) + (1 if self.resolution == "2k" else 0)


def review_plan(mode="sequential-3", resolution="default"):
    """An estimate only. It neither verifies balance nor schedules any requests."""
    if mode == "sequential-3":
        return tuple(RequestStage((role,), resolution) for role in ROLES)
    if mode == "clothing-pair-then-shoes":
        return (RequestStage(("tops", "bottoms"), resolution), RequestStage(("shoes",), resolution))
    raise ValueError("unsupported_plan")


def build_stage_body(stage, *, image_id, customer_id, product_ids):
    """Build only the documented multipart data body; caller attaches file bytes.

    Single-product file key stays `product` for the existing wire contract;
    multi-product keys are role names. Private metadata and unsupported prompt
    fields cannot accidentally be added to the vendor API by this builder.
    """
    if not isinstance(stage, RequestStage):
        raise ValueError("invalid_stage")
    if (not isinstance(image_id, str) or not 1 <= len(image_id) <= 1024 or
            any(ord(c) < 32 for c in image_id)):
        raise ValueError("invalid_image_id")
    if not isinstance(customer_id, str) or not SAFE_ID.fullmatch(customer_id):
        raise ValueError("invalid_customer_id")
    if not isinstance(product_ids, dict) or set(product_ids) != set(stage.roles):
        raise ValueError("product_role_mismatch")
    ids = list(product_ids.values())
    if (any(not isinstance(value, str) or not SAFE_ID.fullmatch(value) or value.startswith("_anon_") for value in ids)
            or len(set(ids)) != len(ids)):
        raise ValueError("invalid_product_id")
    def description(role):
        if len(stage.roles) == 1:
            return DESCRIPTIONS[role] + IDENTITY
        # Do not tell the model to preserve a different garment that is also
        # explicitly selected for replacement in the same paired request.
        return ("The supplied " + TITLES[role].lower() + " only. Preserve its visible colour, pattern and cut. "
                "Apply both supplied upper-body and lower-body garments together. Keep existing footwear unchanged." + IDENTITY)

    return {
        "products": [{"externalId": product_ids[role], "title": TITLES[role],
                      "description": description(role),
                      "validForDays": 1, "images": [{"source": {"fileKey": "product" if len(stage.roles) == 1 else role}}]}
                     for role in stage.roles],
        "person": {"image": {"source": {"id": image_id}}},
        "externalUserId": customer_id,
        "output": {"resolution": stage.resolution, "aiLabel": True, "keepForDays": 1},
    }
