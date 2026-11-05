"""Synthetic evidence tests; these do not execute or certify a VTON model."""
import hashlib
import json
from pathlib import Path

import pytest

torch = pytest.importorskip("torch")

from p41_sampling_state import SHAPE, save_state
from p42_compare_states import compare_states, main, validate_invocations


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


def invocation(root, name, output, resume=None, threads=2, stop=2):
    folder = root / name
    folder.mkdir()
    sources = folder / "sources"
    sources.mkdir()
    source = b"# Test evidence only: no model executed\n"
    (sources / "p41_infer.py").write_bytes(source)
    arguments = []
    for flag in ("source-dir", "source-manifest", "weights-dir", "prepared-dir"):
        arguments.extend(["--" + flag, str(root / flag)])
    arguments.extend(["--output-dir", str(output), "--steps", "20", "--seed", "42",
                      "--query-chunk", "128", "--threads", str(threads), "--stop-after-step", str(stop)])
    if resume is not None:
        arguments.extend(["--resume", str(resume)])
    record = {
        "python": "fixed-test-python", "site_packages": "fixed-test-environment",
        "interpreter_flags": ["-I", "-S", "-u"], "max_rss_mib": 1536,
        "initial_free_ram_min_bytes": 512 * 1024**2,
        "initial_free_commit_min_bytes": 2 * 1024**3,
        "runtime_free_ram_min_bytes": 96 * 1024**2,
        "runtime_free_commit_min_bytes": 768 * 1024**2,
        "runtime_consecutive_low_samples": 3, "arguments": arguments,
        "sources": [{"name": "p41_infer.py", "bytes": len(source),
                     "sha256": hashlib.sha256(source).hexdigest()}],
    }
    path = folder / "invocation.json"
    path.write_text(json.dumps(record))
    (folder / "supervisor.json").write_text(json.dumps({
        "started": True, "exit_code": 0, "stop_reason": None, "owned_process_only": True,
    }))
    return path


def final(folder, step):
    (folder / "final.json").write_text(json.dumps({
        "success": True, "checkpoint_only": True, "completed_steps": step, "requested_steps": 20,
    }))


@pytest.fixture
def evidence(tmp_path):
    left = tmp_path / "continuous"
    first = tmp_path / "split-first"
    right = tmp_path / "resumed"
    image1 = torch.full(SHAPE, 0.125)
    image2 = torch.full(SHAPE, 0.25)
    image2[0, 0, 0, 0] = -0.0
    rng = torch.Generator().manual_seed(42).get_state()
    for folder, image, step in ((left, image1, 1), (left, image2, 2),
                                (first, image1, 1), (right, image2, 2)):
        save_state(folder / f"checkpoint-{step:03d}", image, step, contract(), rng)
    final(left, 2)
    final(first, 1)
    final(right, 2)
    return {"left": left, "first": first, "right": right,
            "a": invocation(tmp_path, "continuous-monitor", left),
            "c": invocation(tmp_path, "split-first-monitor", first, stop=1),
            "b": invocation(tmp_path, "resumed-monitor", right, first / "checkpoint-001")}


def rewrite_image(folder, index, value):
    import struct
    path = folder / "images.f32"
    raw = bytearray(path.read_bytes())
    raw[index * 4:index * 4 + 4] = struct.pack("<f", value)
    path.write_bytes(raw)
    manifest = folder / "sampling-state.json"
    record = json.loads(manifest.read_text())
    record["images"]["sha256"] = hashlib.sha256(raw).hexdigest()
    manifest.write_text(json.dumps(record))


def test_exact_comparison_preserves_partial_and_non_model_claims(evidence):
    result = compare_states(evidence["a"], evidence["c"], evidence["b"], contract())
    assert result["result"] == "PASS"
    assert result["completed_steps"] == 2 and result["requested_steps"] == 20
    assert not result["generation_complete"] and not result["quality_verified"]
    assert not result["model_authenticity_verified_by_this_comparator"]
    assert result["threads"] == 2 and not result["numeric_tolerance_used"]
    assert set(result["completed_processes"]) == {"continuous", "split_first", "resumed"}
    for item in result["comparisons"]:
        assert item["images"]["max_abs_error"] == 0
        assert item["images"]["rmse"] == 0
        assert item["images"]["bitexact"] and item["rng_bitexact"]


def test_nonzero_difference_is_not_hidden_by_tolerance(evidence):
    rewrite_image(evidence["right"] / "checkpoint-002", 1, 0.5)
    result = compare_states(evidence["a"], evidence["c"], evidence["b"], contract())
    assert result["result"] == "FAIL"
    item = result["comparisons"][1]["images"]
    assert item["numeric_different_elements"] == 1
    assert item["max_abs_error"] == 0.25
    assert item["rmse"] == pytest.approx(0.25 / (1 * 3 * 864 * 576) ** 0.5)


def test_signed_zero_difference_is_not_bitexact_pass(evidence):
    rewrite_image(evidence["right"] / "checkpoint-002", 0, 0.0)
    result = compare_states(evidence["a"], evidence["c"], evidence["b"], contract())
    assert result["result"] == "FAIL"
    assert result["comparisons"][1]["images"]["max_abs_error"] == 0
    assert not result["comparisons"][1]["images"]["bitexact"]


def test_first_step_replay_mismatch_also_fails(evidence):
    rewrite_image(evidence["first"] / "checkpoint-001", 7, 0.5)
    result = compare_states(evidence["a"], evidence["c"], evidence["b"], contract())
    assert result["result"] == "FAIL"
    assert not result["comparisons"][0]["images"]["bitexact"]
    assert result["comparisons"][1]["images"]["bitexact"]


def test_rng_mismatch_fails_even_when_pixels_match(evidence):
    folder = evidence["right"] / "checkpoint-002"
    raw = torch.Generator().manual_seed(43).get_state().numpy().tobytes()
    (folder / "rng.u8").write_bytes(raw)
    manifest = folder / "sampling-state.json"
    record = json.loads(manifest.read_text())
    record["rng"]["sha256"] = hashlib.sha256(raw).hexdigest()
    manifest.write_text(json.dumps(record))
    result = compare_states(evidence["a"], evidence["c"], evidence["b"], contract())
    assert result["result"] == "FAIL"
    assert result["comparisons"][1]["images"]["bitexact"]
    assert not result["comparisons"][1]["rng_bitexact"]


@pytest.mark.parametrize("flag,new", [("--threads", "1"), ("--query-chunk", "64"), ("--seed", "43")])
def test_invocation_numeric_execution_settings_must_match(evidence, flag, new):
    record = json.loads(evidence["b"].read_text())
    record["arguments"][record["arguments"].index(flag) + 1] = new
    evidence["b"].write_text(json.dumps(record))
    with pytest.raises(ValueError, match="execution_settings_mismatch"):
        compare_states(evidence["a"], evidence["c"], evidence["b"], contract())


def test_source_snapshot_corruption_is_rejected(evidence):
    (evidence["b"].parent / "sources" / "p41_infer.py").write_bytes(b"changed")
    with pytest.raises(ValueError, match="source_hash"):
        validate_invocations(evidence["a"], evidence["c"], evidence["b"])


def test_same_invocation_cannot_certify_independent_run(evidence):
    with pytest.raises(ValueError, match="distinct_invocations"):
        validate_invocations(evidence["a"], evidence["c"], evidence["a"])


def test_partial_or_wrong_final_record_is_rejected(evidence):
    final(evidence["right"], 1)
    with pytest.raises(ValueError, match="did_not_finish_two_steps"):
        compare_states(evidence["a"], evidence["c"], evidence["b"], contract())


@pytest.mark.parametrize("field,value", [
    ("started", False), ("exit_code", 1), ("exit_code", None),
    ("stop_reason", "system_memory_reserve_reached"), ("owned_process_only", False),
])
def test_split_worker_must_have_proven_successful_exit(evidence, field, value):
    path = evidence["c"].parent / "supervisor.json"
    record = json.loads(path.read_text())
    record[field] = value
    path.write_text(json.dumps(record))
    with pytest.raises(ValueError, match="supervisor_did_not_complete"):
        compare_states(evidence["a"], evidence["c"], evidence["b"], contract())


def test_split_first_thread_count_is_also_required(evidence):
    record = json.loads(evidence["c"].read_text())
    record["arguments"][record["arguments"].index("--threads") + 1] = "1"
    evidence["c"].write_text(json.dumps(record))
    with pytest.raises(ValueError, match="execution_settings_mismatch"):
        validate_invocations(evidence["a"], evidence["c"], evidence["b"])


def test_split_snapshot_is_checked_against_its_bytes(evidence):
    (evidence["c"].parent / "sources" / "p41_infer.py").write_bytes(b"changed")
    with pytest.raises(ValueError, match="source_hash"):
        validate_invocations(evidence["a"], evidence["c"], evidence["b"])


def test_resume_must_target_verified_split_output(evidence):
    record = json.loads(evidence["b"].read_text())
    record["arguments"][record["arguments"].index("--resume") + 1] = str(evidence["left"] / "checkpoint-001")
    evidence["b"].write_text(json.dumps(record))
    with pytest.raises(ValueError, match="resume_not_from_split"):
        validate_invocations(evidence["a"], evidence["c"], evidence["b"])


def test_existing_comparison_evidence_is_never_overwritten(tmp_path):
    output = tmp_path / "comparison.json"
    output.write_text("preserved")
    with pytest.raises(FileExistsError):
        main(["--continuous-invocation", "missing", "--split-invocation", "missing1", "--resumed-invocation", "missing2",
              "--expected-contract", "missing3", "--output", str(output)])
    assert output.read_text() == "preserved"
