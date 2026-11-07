"""Sampler state integrity tests, not model generation or image-quality evidence."""
import hashlib
import json
import struct

import pytest

torch = pytest.importorskip("torch")

from p41_sampling_state import IMAGE_FILE, MANIFEST_FILE, SHAPE, load_state, save_state, validate_contract


def contract():
    return {
        "model_sha256": "1" * 64, "source_manifest_sha256": "2" * 64,
        "prepared_manifest_sha256": "3" * 64, "person_sha256": "4" * 64,
        "garment_sha256": "5" * 64, "adapter_sha256": "6" * 64,
        "source_revision": "7" * 40, "seed": 42, "steps": 20, "category": "tops",
        "precision": "float32", "sampler": "euler_rf", "time_shift_mu": 1.5,
        "guidance_scale": 1.5, "skip_cfg_last_n_steps": 1, "query_chunk": 128,
        "cfg_execution": "sequential_conditional_then_unconditional",
        "torch_version": str(torch.__version__), "algorithm": "p41-streamed-fp32-v1",
    }


@pytest.fixture
def checkpoint(tmp_path):
    images = torch.full(SHAPE, 0.25, dtype=torch.float32)
    images[0, 0, 0, 0] = -0.0
    images[0, 2, 1, 1] = 7.125  # Sampler states must not be clipped to image range.
    rng = torch.get_rng_state()
    folder = tmp_path / "checkpoint-003"
    save_state(folder, images, 3, contract(), rng)
    return folder, images, rng


def test_exact_roundtrip_preserves_pixels_signed_zero_rng_and_global_rng(checkpoint):
    folder, expected, rng = checkpoint
    before = torch.get_rng_state().clone()
    actual, step, actual_rng = load_state(folder, contract())
    assert step == 3
    assert actual.numpy().tobytes() == expected.numpy().tobytes()
    assert (folder / IMAGE_FILE).read_bytes()[:4] == struct.pack("<f", -0.0)
    assert actual[0, 2, 1, 1].item() == 7.125
    assert torch.equal(actual_rng, rng)
    assert torch.equal(torch.get_rng_state(), before)
    verification = json.loads((folder / "roundtrip.json").read_text())
    assert verification["roundtrip_bitexact"]
    assert not verification["generation_complete"]


def test_existing_checkpoint_is_never_overwritten(checkpoint):
    folder, images, rng = checkpoint
    original = (folder / MANIFEST_FILE).read_bytes()
    with pytest.raises(FileExistsError):
        save_state(folder, images, 4, contract(), rng)
    assert (folder / MANIFEST_FILE).read_bytes() == original


@pytest.mark.parametrize("field,value", [
    ("steps", 30), ("seed", 43), ("category", "bottoms"), ("query_chunk", 64),
    ("model_sha256", "8" * 64), ("person_sha256", "8" * 64),
    ("garment_sha256", "8" * 64), ("source_manifest_sha256", "8" * 64),
    ("prepared_manifest_sha256", "8" * 64), ("adapter_sha256", "8" * 64),
])
def test_changed_execution_or_input_contract_cannot_resume(checkpoint, field, value):
    folder, _, _ = checkpoint
    changed = contract()
    changed[field] = value
    with pytest.raises(ValueError, match="contract_mismatch"):
        load_state(folder, changed)


@pytest.mark.parametrize("steps", [1, 2, 4])
def test_diagnostic_schedule_cannot_be_promoted_to_standard_resume(steps):
    value = contract()
    value["steps"] = steps
    with pytest.raises(ValueError, match="standard_schedule"):
        validate_contract(value)


def test_corrupt_payload_fails_hash_before_tensor_use(checkpoint):
    folder, _, _ = checkpoint
    path = folder / IMAGE_FILE
    with path.open("r+b") as stream:
        stream.seek(12)
        stream.write(b"\x01")
    with pytest.raises(ValueError, match="payload_hash"):
        load_state(folder, contract())


def test_manifest_filename_cannot_select_external_file(checkpoint):
    folder, _, _ = checkpoint
    path = folder / MANIFEST_FILE
    record = json.loads(path.read_text())
    record["images"]["file"] = "../external.f32"
    path.write_text(json.dumps(record))
    with pytest.raises(ValueError, match="image_descriptor"):
        load_state(folder, contract())


def test_nonfinite_even_with_updated_hash_is_rejected(checkpoint):
    folder, _, _ = checkpoint
    path = folder / IMAGE_FILE
    raw = bytearray(path.read_bytes())
    raw[:4] = struct.pack("<f", float("nan"))
    path.write_bytes(raw)
    manifest_path = folder / MANIFEST_FILE
    record = json.loads(manifest_path.read_text())
    record["images"]["sha256"] = hashlib.sha256(raw).hexdigest()
    manifest_path.write_text(json.dumps(record))
    with pytest.raises(ValueError, match="nonfinite_images"):
        load_state(folder, contract())


@pytest.mark.parametrize("step", [0, 20, 21, True])
def test_initial_final_or_invalid_step_cannot_be_saved(tmp_path, step):
    folder = tmp_path / "must-not-exist"
    with pytest.raises(ValueError, match="nonfinal_standard_intermediate"):
        save_state(folder, torch.zeros(SHAPE), step, contract(), torch.get_rng_state())
    assert not folder.exists()


def test_missing_or_unknown_contract_fields_are_rejected():
    value = contract()
    value.pop("adapter_sha256")
    with pytest.raises(ValueError, match="contract_keys"):
        validate_contract(value)
    value = contract()
    value["input_path"] = "untrusted"
    with pytest.raises(ValueError, match="contract_keys"):
        validate_contract(value)
