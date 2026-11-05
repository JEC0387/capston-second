"""Strict, local P41 sampler checkpoints; never a completed generation result.

Only intermediate states of the original 20/30/50-step schedules are accepted.
No pickle, path supplied by metadata, implicit RNG restore, or existing-file
overwrite. The caller supplies the independently verified current contract.
Hashes detect corruption/mismatches, not a malicious replacement of both files
and manifest. Keep these local experiment artifacts out of app/APK/Git assets.
"""
from pathlib import Path
import hashlib
import json
import re
import sys


SCHEMA = "fashionai-p41-sampling-state-v1"
SHAPE = (1, 3, 864, 576)
IMAGE_BYTES = 1 * 3 * 864 * 576 * 4
MAX_MANIFEST_BYTES = 64 * 1024
MAX_RNG_BYTES = 64 * 1024
MANIFEST_FILE = "sampling-state.json"
IMAGE_FILE = "images.f32"
RNG_FILE = "rng.u8"
SHA_KEYS = {"model_sha256", "source_manifest_sha256", "prepared_manifest_sha256",
            "person_sha256", "garment_sha256", "adapter_sha256"}
CONTRACT_KEYS = SHA_KEYS | {
    "source_revision", "seed", "steps", "category", "precision", "sampler",
    "time_shift_mu", "guidance_scale", "skip_cfg_last_n_steps", "query_chunk",
    "cfg_execution", "torch_version", "algorithm",
}


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate_checkpoint_key")
        result[key] = value
    return result


def _valid_sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def validate_contract(contract):
    if not isinstance(contract, dict) or set(contract) != CONTRACT_KEYS:
        raise ValueError("checkpoint_contract_keys")
    if any(not _valid_sha(contract[name]) for name in SHA_KEYS):
        raise ValueError("checkpoint_contract_sha")
    revision = contract["source_revision"]
    if not isinstance(revision, str) or re.fullmatch(r"[0-9a-f]{40}", revision) is None:
        raise ValueError("checkpoint_source_revision")
    if type(contract["seed"]) is not int or not 0 <= contract["seed"] < 2**63:
        raise ValueError("checkpoint_seed")
    if type(contract["steps"]) is not int or contract["steps"] not in {20, 30, 50}:
        raise ValueError("checkpoint_requires_standard_schedule")
    if contract["category"] not in {"tops", "bottoms"}:
        raise ValueError("checkpoint_category")
    fixed = {
        "precision": "float32", "sampler": "euler_rf",
        "cfg_execution": "sequential_conditional_then_unconditional",
        "algorithm": "p41-streamed-fp32-v1",
    }
    if any(contract[name] != value for name, value in fixed.items()):
        raise ValueError("checkpoint_algorithm_contract")
    if (type(contract["time_shift_mu"]) not in {int, float} or contract["time_shift_mu"] != 1.5 or
            type(contract["guidance_scale"]) not in {int, float} or contract["guidance_scale"] != 1.5 or
            type(contract["skip_cfg_last_n_steps"]) is not int or contract["skip_cfg_last_n_steps"] != 1):
        raise ValueError("checkpoint_sampling_parameters")
    if type(contract["query_chunk"]) is not int or not 1 <= contract["query_chunk"] <= 256:
        raise ValueError("checkpoint_query_chunk")
    version = contract["torch_version"]
    if not isinstance(version, str) or re.fullmatch(r"[A-Za-z0-9.+_-]{1,64}", version) is None:
        raise ValueError("checkpoint_torch_version")


def _validate_step(completed_steps, contract):
    if type(completed_steps) is not int or not 0 < completed_steps < contract["steps"]:
        raise ValueError("checkpoint_must_be_nonfinal_standard_intermediate")


def _validate_runtime(contract):
    import torch
    if sys.byteorder != "little":
        raise ValueError("checkpoint_requires_little_endian_runtime")
    if str(torch.__version__) != contract["torch_version"]:
        raise ValueError("checkpoint_runtime_version_mismatch")


def _fixed_file(folder, name):
    path = folder / name
    if path.is_symlink() or path.resolve().parent != folder or not path.is_file():
        raise ValueError("checkpoint_file_missing_or_external")
    return path


def _read_fixed(folder, name, byte_count):
    path = _fixed_file(folder, name)
    if path.stat().st_size != byte_count:
        raise ValueError("checkpoint_file_size:" + name)
    with path.open("rb") as stream:
        raw = stream.read(byte_count + 1)
    if len(raw) != byte_count:
        raise ValueError("checkpoint_file_changed:" + name)
    return raw


def load_state(folder, expected_contract):
    """Return (CPU FP32 images, completed_steps, CPU uint8 RNG) without RNG mutation."""
    import torch

    validate_contract(expected_contract)
    _validate_runtime(expected_contract)
    folder = Path(folder).resolve(strict=True)
    manifest_path = _fixed_file(folder, MANIFEST_FILE)
    if not 0 < manifest_path.stat().st_size <= MAX_MANIFEST_BYTES:
        raise ValueError("checkpoint_manifest_size")
    record = json.loads(manifest_path.read_text(encoding="utf-8"), object_pairs_hook=_unique_object)
    if not isinstance(record, dict) or set(record) != {"schema", "contract", "completed_steps", "images", "rng"}:
        raise ValueError("checkpoint_manifest_keys")
    if record["schema"] != SCHEMA:
        raise ValueError("checkpoint_schema")
    validate_contract(record["contract"])
    if record["contract"] != expected_contract:
        raise ValueError("checkpoint_contract_mismatch")
    _validate_step(record["completed_steps"], record["contract"])
    image_meta, rng_meta = record["images"], record["rng"]
    if (not isinstance(image_meta, dict) or set(image_meta) != {"file", "dtype", "shape", "bytes", "sha256"} or
            image_meta["file"] != IMAGE_FILE or image_meta["dtype"] != "float32-le" or
            image_meta["shape"] != list(SHAPE) or type(image_meta["bytes"]) is not int or
            image_meta["bytes"] != IMAGE_BYTES or not _valid_sha(image_meta["sha256"])):
        raise ValueError("checkpoint_image_descriptor")
    if (not isinstance(rng_meta, dict) or set(rng_meta) != {"file", "dtype", "bytes", "sha256"} or
            rng_meta["file"] != RNG_FILE or rng_meta["dtype"] != "uint8" or
            type(rng_meta["bytes"]) is not int or not 64 <= rng_meta["bytes"] <= MAX_RNG_BYTES or
            not _valid_sha(rng_meta["sha256"])):
        raise ValueError("checkpoint_rng_descriptor")
    image_raw = _read_fixed(folder, IMAGE_FILE, IMAGE_BYTES)
    rng_raw = _read_fixed(folder, RNG_FILE, rng_meta["bytes"])
    if _sha(image_raw) != image_meta["sha256"] or _sha(rng_raw) != rng_meta["sha256"]:
        raise ValueError("checkpoint_payload_hash")
    images = torch.frombuffer(bytearray(image_raw), dtype=torch.float32).reshape(SHAPE).clone()
    rng = torch.frombuffer(bytearray(rng_raw), dtype=torch.uint8).clone()
    if not bool(torch.isfinite(images).all()):
        raise ValueError("checkpoint_nonfinite_images")
    # Validate RNG format in an isolated generator, never the process-global one.
    try:
        torch.Generator(device="cpu").set_state(rng)
    except RuntimeError as error:
        raise ValueError("checkpoint_invalid_rng_state") from error
    return images, record["completed_steps"], rng


def save_state(folder, images, completed_steps, contract, rng):
    """Write a NEW checkpoint, then verify exact roundtrip; never replace old files."""
    import torch

    validate_contract(contract)
    _validate_runtime(contract)
    _validate_step(completed_steps, contract)
    if (not isinstance(images, torch.Tensor) or images.device.type != "cpu" or
            images.dtype != torch.float32 or tuple(images.shape) != SHAPE):
        raise ValueError("checkpoint_image_tensor_contract")
    if not bool(torch.isfinite(images).all()):
        raise ValueError("checkpoint_nonfinite_images")
    if (not isinstance(rng, torch.Tensor) or rng.device.type != "cpu" or rng.dtype != torch.uint8 or
            rng.ndim != 1 or not 64 <= rng.numel() <= MAX_RNG_BYTES):
        raise ValueError("checkpoint_rng_tensor_contract")
    try:
        torch.Generator(device="cpu").set_state(rng)
    except RuntimeError as error:
        raise ValueError("checkpoint_invalid_rng_state") from error
    image_raw = images.detach().contiguous().numpy().tobytes(order="C")
    rng_raw = rng.detach().contiguous().numpy().tobytes(order="C")
    record = {
        "schema": SCHEMA, "contract": dict(contract), "completed_steps": completed_steps,
        "images": {"file": IMAGE_FILE, "dtype": "float32-le", "shape": list(SHAPE),
                   "bytes": IMAGE_BYTES, "sha256": _sha(image_raw)},
        "rng": {"file": RNG_FILE, "dtype": "uint8", "bytes": len(rng_raw), "sha256": _sha(rng_raw)},
    }
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=False)
    folder = folder.resolve(strict=True)
    for name, payload in ((IMAGE_FILE, image_raw), (RNG_FILE, rng_raw)):
        with (folder / name).open("xb") as stream:
            stream.write(payload)
    # Manifest is the last data file. An interruption leaves incomplete evidence,
    # which load_state rejects; no existing checkpoint is ever overwritten.
    with (folder / MANIFEST_FILE).open("x", encoding="utf-8") as stream:
        json.dump(record, stream, indent=2, allow_nan=False)
    restored, actual_steps, restored_rng = load_state(folder, contract)
    if (actual_steps != completed_steps or restored.numpy().tobytes(order="C") != image_raw or
            restored_rng.numpy().tobytes(order="C") != rng_raw):
        raise ValueError("checkpoint_roundtrip_mismatch")
    verification = {"roundtrip_bitexact": True, "completed_steps": completed_steps,
                    "requested_steps": contract["steps"], "generation_complete": False,
                    "images_sha256": record["images"]["sha256"], "rng_sha256": record["rng"]["sha256"]}
    with (folder / "roundtrip.json").open("x", encoding="utf-8") as stream:
        json.dump(verification, stream, indent=2)
    return verification
