"""Bounded inference helpers for the isolated P41 experiment, not the app API.

The reviewed FASHN source uses noncausal, mask-free scaled dot product attention.
Chunking only queries retains every key/value and the original attention scale.
Sequential CFG avoids duplicating the model batch. It is mathematically equivalent
for the reviewed floating-point eval model, subject to numerical rounding. Dynamic
INT8 activation ranges can change when batches are split; that combination is an
explicitly approximate experiment requiring output quality review.

These helpers do not load models, download assets, weaken input gates, or emit PNGs.
The runner must retain process-level memory/time limits and the complete schedule.
"""
from contextlib import contextmanager


def validate_query_chunk_size(value):
    if isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= 256:
        raise ValueError("query_chunk_size_must_be_1_to_256")


def query_chunked_attention(q, k, v, *, query_chunk_size=128):
    """Execute actual SDPA with full keys/values, and one query chunk at a time."""
    import torch
    from torch.nn.functional import scaled_dot_product_attention

    validate_query_chunk_size(query_chunk_size)
    if any(t.ndim != 4 for t in (q, k, v)):
        raise ValueError("attention_requires_bhld_tensors")
    if q.shape[:2] != k.shape[:2] or k.shape[:2] != v.shape[:2]:
        raise ValueError("attention_batch_or_head_mismatch")
    if q.shape[-1] != k.shape[-1] or k.shape[-2] != v.shape[-2]:
        raise ValueError("attention_key_value_dimension_mismatch")
    if min(*q.shape, *k.shape, *v.shape) <= 0:
        raise ValueError("attention_empty_dimension")
    if any(t.device != q.device or t.dtype != q.dtype for t in (k, v)):
        raise ValueError("attention_dtype_or_device_mismatch")
    if not q.is_floating_point():
        raise ValueError("attention_requires_floating_point")
    with torch.inference_mode():
        output = torch.empty((*q.shape[:-1], v.shape[-1]), dtype=q.dtype, device=q.device)
        for start in range(0, q.shape[-2], query_chunk_size):
            stop = min(start + query_chunk_size, q.shape[-2])
            # Every query chunk sees ALL original keys/values. No local attention,
            # spatial downsampling, causal mask, or altered scale is introduced.
            chunk = scaled_dot_product_attention(
                q[..., start:stop, :], k, v, dropout_p=0.0, is_causal=False
            )
            output[..., start:stop, :].copy_(chunk)
            del chunk
    return output


@contextmanager
def patched_query_attention(upstream_module, *, query_chunk_size=128):
    """Temporarily replace only the reviewed module's three-argument processor."""
    validate_query_chunk_size(query_chunk_size)
    original = getattr(upstream_module, "_attn_processor", None)
    if not callable(original):
        raise ValueError("reviewed_attention_processor_missing")
    def processor(q, k, v):
        return query_chunked_attention(q, k, v, query_chunk_size=query_chunk_size)
    upstream_module._attn_processor = processor
    try:
        yield {
            "query_chunk_size": query_chunk_size,
            "keys_values": "full_length",
            "causal": False,
            "dropout_probability": 0.0,
            "quality_verified": False,
        }
    finally:
        upstream_module._attn_processor = original


def cfg_execution_metadata(model, *, allow_dynamic_quantization=False):
    from torch.ao.nn.quantized.dynamic import Linear as DynamicLinear

    count = sum(isinstance(module, DynamicLinear) for module in model.modules())
    if count and not allow_dynamic_quantization:
        raise ValueError("dynamic_int8_cfg_requires_explicit_experimental_mode")
    return {
        "cfg_execution": "sequential_conditional_then_unconditional",
        "model_batch_size": 1,
        "dynamic_int8_linear_modules": count,
        "batch_quantization_ranges_may_differ": count > 0,
        "quality_verified": False,
    }


def sequential_cfg(model, noisy_images, times, *, ca_images, garment_images,
                   person_poses, garment_poses, garment_categories,
                   allow_dynamic_quantization=False):
    """Call the genuine model twice with the upstream true/false condition masks.

    This helper deliberately accepts one sample and no caller-supplied mask.
    It does not approximate unconditional output by reusing the conditional image.
    The original caller tensors are never replaced or modified.
    """
    import torch

    if model.training:
        raise ValueError("sequential_cfg_requires_eval_model")
    cfg_execution_metadata(model, allow_dynamic_quantization=allow_dynamic_quantization)
    if noisy_images.ndim != 4 or noisy_images.shape[:2] != (1, 3):
        raise ValueError("sequential_cfg_requires_one_rgb_sample")
    if times.shape != (1,) or times.device != noisy_images.device or times.dtype != noisy_images.dtype:
        raise ValueError("sequential_cfg_time_contract")
    images = {"ca_images": ca_images, "garment_images": garment_images,
              "person_poses": person_poses, "garment_poses": garment_poses}
    for name, tensor in images.items():
        channels = 1 if name.endswith("poses") else 3
        if (tensor.shape != (1, channels, *noisy_images.shape[-2:]) or
                tensor.dtype != noisy_images.dtype or tensor.device != noisy_images.device):
            raise ValueError("sequential_cfg_image_contract:" + name)
    if (garment_categories.shape != (1,) or garment_categories.dtype != torch.int64 or
            garment_categories.device != noisy_images.device):
        raise ValueError("sequential_cfg_category_contract")
    inputs = {**images, "garment_categories": garment_categories}
    with torch.inference_mode():
        conditional = model.forward(
            noisy_images, times, mask=torch.ones(1, dtype=torch.bool, device=noisy_images.device), **inputs
        )["x"]
        if conditional.shape != noisy_images.shape or conditional.dtype != noisy_images.dtype:
            raise ValueError("sequential_cfg_conditional_output_contract")
        unconditional = model.forward(
            noisy_images, times, mask=torch.zeros(1, dtype=torch.bool, device=noisy_images.device), **inputs
        )["x"]
        if unconditional.shape != noisy_images.shape or unconditional.dtype != noisy_images.dtype:
            raise ValueError("sequential_cfg_unconditional_output_contract")
    return {"v_c": conditional, "v_u": unconditional}
