"""Official request rules only; no live vendor call or generated output."""
import pytest
from genlook_request_contract import RequestStage, build_stage_body, review_plan


def test_pair_then_shoes_is_two_requests_three_standard_credits_not_one_three_product_call():
    stages = review_plan("clothing-pair-then-shoes")
    assert [s.roles for s in stages] == [("tops", "bottoms"), ("shoes",)]
    assert [s.expected_credits for s in stages] == [2, 1]
    assert sum(s.expected_credits for s in review_plan()) == 3
    assert [s.expected_credits for s in review_plan("clothing-pair-then-shoes", "2k")] == [3, 2]


@pytest.mark.parametrize("roles", [("tops", "shoes"), ("bottoms", "shoes"), ("tops", "bottoms", "shoes")])
def test_shoes_mixed_with_clothing_rejected_before_network(roles):
    with pytest.raises(ValueError, match="MULTI_PRODUCT_NOT_SUPPORTED"):
        RequestStage(roles)


@pytest.mark.parametrize("roles", [(), ("tops", "tops"), ("bottoms", "bottoms"), ("unknown",)])
def test_missing_duplicate_or_unknown_roles_rejected(roles):
    with pytest.raises(ValueError): RequestStage(roles)


def test_pair_schema_uses_two_inline_products_original_uploaded_id_and_minimum_retention():
    body = build_stage_body(RequestStage(("tops", "bottoms")), image_id="opaque/image/ref",
                            customer_id="test-customer", product_ids={"tops": "test-top", "bottoms": "test-bottom"})
    assert list(body) == ["products", "person", "externalUserId", "output"]
    assert [p["externalId"] for p in body["products"]] == ["test-top", "test-bottom"]
    assert [p["images"][0]["source"]["fileKey"] for p in body["products"]] == ["tops", "bottoms"]
    assert all(p["validForDays"] == 1 for p in body["products"])
    assert body["person"] == {"image": {"source": {"id": "opaque/image/ref"}}}
    assert body["output"] == {"resolution": "default", "aiLabel": True, "keepForDays": 1}
    assert "prompt" not in body and "mask" not in body and "mock" not in body
    assert all("both supplied" in p["description"] and "footwear unchanged" in p["description"] for p in body["products"])
    assert all("existing lower-body clothing" not in p["description"] for p in body["products"])


def test_single_stage_requests_preservation_of_other_garments_without_inventing_garment_attributes():
    body = build_stage_body(RequestStage(("tops",)), image_id="image", customer_id="customer",
                            product_ids={"tops": "product"})
    text = body["products"][0]["description"]
    assert "lower-body clothing, its pattern and colour, and footwear unchanged" in text
    assert "face, hair, hands, body pose and background" in text
    assert "plaid" not in text and "jacket" not in text


def test_product_ids_and_file_roles_cannot_be_swapped_or_reused():
    for ids in ({"tops": "top"}, {"tops": "same", "bottoms": "same"},
                {"tops": "top", "bottoms": "_anon_reserved"}):
        with pytest.raises(ValueError):
            build_stage_body(RequestStage(("tops", "bottoms")), image_id="image", customer_id="customer", product_ids=ids)
