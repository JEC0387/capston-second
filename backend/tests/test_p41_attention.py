"""Real small-tensor numerics; these tests do not claim model or PNG success."""
import math
from types import SimpleNamespace

import pytest

torch = pytest.importorskip("torch")

from p41_attention import cfg_execution_metadata, patched_query_attention, query_chunked_attention, sequential_cfg


def test_query_chunks_match_independent_explicit_softmax_and_use_all_keys():
    q = torch.tensor([[[[1.0, 0.0], [0.0, 1.0]]]], dtype=torch.float64)
    k = torch.tensor([[[[1.0, 0.0], [0.0, 1.0], [1.0, 1.0]]]], dtype=torch.float64)
    v = torch.tensor([[[[10.0, 0.0], [0.0, 20.0], [30.0, 40.0]]]], dtype=torch.float64)
    factor = math.exp(1.0 / math.sqrt(2.0))
    denominator = 2.0 * factor + 1.0
    # Independently derived rows include the third key, beyond each query chunk.
    expected = torch.tensor([[[[(40.0 * factor) / denominator, (20.0 + 40.0 * factor) / denominator],
                               [(10.0 + 30.0 * factor) / denominator, 60.0 * factor / denominator]]]],
                            dtype=torch.float64)
    actual = query_chunked_attention(q, k, v, query_chunk_size=1)
    torch.testing.assert_close(actual, expected, rtol=1e-12, atol=1e-12)


def test_nondivisible_query_chunks_match_original_full_sdpa():
    generator = torch.Generator().manual_seed(41)
    q = torch.randn((1, 2, 7, 4), generator=generator)
    k = torch.randn((1, 2, 11, 4), generator=generator)
    v = torch.randn((1, 2, 11, 3), generator=generator)
    original = torch.nn.functional.scaled_dot_product_attention(q, k, v)
    actual = query_chunked_attention(q, k, v, query_chunk_size=3)
    assert actual.shape == (1, 2, 7, 3)
    torch.testing.assert_close(actual, original, rtol=1e-5, atol=1e-6)


def test_attention_patch_restores_original_after_failure():
    original = torch.nn.functional.scaled_dot_product_attention
    module = SimpleNamespace(_attn_processor=original)
    with pytest.raises(RuntimeError, match="interrupted"):
        with patched_query_attention(module, query_chunk_size=2) as metadata:
            assert module._attn_processor is not original
            assert metadata["keys_values"] == "full_length"
            assert not metadata["quality_verified"]
            raise RuntimeError("interrupted")
    assert module._attn_processor is original


@pytest.mark.parametrize("chunk", [0, -1, 257, True, 1.5])
def test_attention_refuses_unbounded_or_invalid_chunk_sizes(chunk):
    q = torch.zeros(1, 1, 2, 2)
    with pytest.raises(ValueError, match="query_chunk_size"):
        query_chunked_attention(q, q, q, query_chunk_size=chunk)


class ConditioningModel(torch.nn.Module):
    """Small deterministic tensor contract oracle, not a generation substitute."""
    def __init__(self):
        super().__init__()
        self.calls = []

    def forward(self, x, times, *, ca_images, garment_images, person_poses,
                garment_poses, garment_categories, mask):
        self.calls.append((x.shape[0], mask.tolist()))
        active = mask.reshape(-1, 1, 1, 1)
        condition = ca_images + garment_images + person_poses + garment_poses
        condition = condition + garment_categories.reshape(-1, 1, 1, 1)
        return {"x": x + times.reshape(-1, 1, 1, 1) + torch.where(active, condition, 0.0)}


def cfg_inputs():
    return {
        "ca_images": torch.full((1, 3, 2, 2), 3.0),
        "garment_images": torch.full((1, 3, 2, 2), 5.0),
        "person_poses": torch.full((1, 1, 2, 2), 7.0),
        "garment_poses": torch.full((1, 1, 2, 2), 11.0),
        "garment_categories": torch.tensor([1], dtype=torch.int64),
    }


def test_sequential_cfg_preserves_conditions_masks_inputs_and_independent_outputs():
    model = ConditioningModel().eval()
    x = torch.full((1, 3, 2, 2), 2.0)
    times = torch.tensor([0.25])
    inputs = cfg_inputs()
    before = {name: value.clone() for name, value in inputs.items()}
    result = sequential_cfg(model, x, times, **inputs)
    assert model.calls == [(1, [True]), (1, [False])]
    torch.testing.assert_close(result["v_c"], torch.full_like(x, 29.25), rtol=0, atol=0)
    torch.testing.assert_close(result["v_u"], torch.full_like(x, 2.25), rtol=0, atol=0)
    for name, value in inputs.items():
        assert torch.equal(value, before[name])
    assert torch.equal(x, torch.full_like(x, 2.0))


def test_sequential_cfg_matches_independent_batched_reference_for_float_model():
    model = ConditioningModel().eval()
    x = torch.full((1, 3, 2, 2), 2.0)
    times = torch.tensor([0.25])
    inputs = cfg_inputs()
    sequential = sequential_cfg(model, x, times, **inputs)
    batched = model.forward(torch.cat((x, x)), torch.cat((times, times)),
                            mask=torch.tensor([True, False]),
                            **{name: torch.cat((value, value)) for name, value in inputs.items()})["x"]
    torch.testing.assert_close(sequential["v_c"], batched[:1], rtol=0, atol=0)
    torch.testing.assert_close(sequential["v_u"], batched[1:], rtol=0, atol=0)


def test_cfg_requires_eval_and_valid_grayscale_pose_contract():
    model = ConditioningModel()
    x = torch.zeros((1, 3, 2, 2))
    times = torch.zeros(1)
    inputs = cfg_inputs()
    with pytest.raises(ValueError, match="eval_model"):
        sequential_cfg(model, x, times, **inputs)
    model.eval()
    inputs["person_poses"] = inputs["person_poses"].squeeze(1)
    with pytest.raises(ValueError, match="image_contract"):
        sequential_cfg(model, x, times, **inputs)
    assert model.calls == []


def test_dynamic_int8_cfg_is_explicitly_approximate_not_silently_equivalent():
    holder = torch.nn.Sequential(torch.nn.Linear(2, 2)).eval()
    torch.ao.quantization.quantize_dynamic(holder, {torch.nn.Linear}, dtype=torch.qint8, inplace=True)
    with pytest.raises(ValueError, match="explicit_experimental_mode"):
        cfg_execution_metadata(holder)
    metadata = cfg_execution_metadata(holder, allow_dynamic_quantization=True)
    assert metadata["dynamic_int8_linear_modules"] == 1
    assert metadata["batch_quantization_ranges_may_differ"]
    assert not metadata["quality_verified"]
