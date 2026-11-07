"""Strict P42 comparison of two steps in ONE original 20-step schedule.

This checks local evidence consistency and bit-exact tensors/RNG, not model
authenticity, image quality, Android operation, or completion of all 20 steps.
Invocations and source snapshots are required because the P41 checkpoint schema
does not record CPU thread count. No numeric tolerance is used to declare PASS.
"""
import argparse
import hashlib
import json
from pathlib import Path

from p41_sampling_state import MANIFEST_FILE, load_state, validate_contract


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _json(path):
    path = Path(path)
    if not 0 < path.stat().st_size <= 128 * 1024:
        raise ValueError("comparison_metadata_size")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("comparison_duplicate_key")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique)


def _arguments(arguments, stop_after):
    paths = {"source-dir", "source-manifest", "weights-dir", "prepared-dir", "output-dir", "resume"}
    integers = {"steps", "seed", "threads", "query-chunk", "stop-after-step"}
    if not isinstance(arguments, list) or any(not isinstance(v, str) for v in arguments):
        raise ValueError("comparison_invocation_arguments")
    result = {"steps": 20, "seed": 42, "threads": 2, "query-chunk": 128}
    seen = set()
    if len(arguments) % 2:
        raise ValueError("comparison_requires_standard_non_diagnostic_arguments")
    for flag, value in zip(arguments[::2], arguments[1::2]):
        if not flag.startswith("--") or flag[2:] not in paths | integers or flag in seen:
            raise ValueError("comparison_unknown_or_duplicate_argument")
        seen.add(flag)
        name = flag[2:]
        result[name] = str(Path(value).resolve()) if name in paths else int(value)
    if not {"source-dir", "source-manifest", "weights-dir", "prepared-dir", "output-dir"} <= set(result):
        raise ValueError("comparison_missing_input_paths")
    if result["steps"] != 20 or result.get("stop-after-step") != stop_after:
        raise ValueError("comparison_requires_fixed_stop_of_twenty:" + str(stop_after))
    return result


def _snapshot_inventory(invocation_path, record):
    root = Path(invocation_path).resolve().parent / "sources"
    entries = record.get("sources")
    if not isinstance(entries, list) or not entries:
        raise ValueError("comparison_source_inventory_missing")
    inventory = {}
    for item in entries:
        if not isinstance(item, dict) or set(item) != {"name", "bytes", "sha256"}:
            raise ValueError("comparison_source_descriptor")
        name = item["name"]
        if (not isinstance(name, str) or name != Path(name).name or name in {".", ".."}
                or name.casefold() in inventory):
            raise ValueError("comparison_source_name")
        path = root / name
        if path.is_symlink() or path.resolve().parent != root.resolve():
            raise ValueError("comparison_source_external")
        data = path.read_bytes()
        if len(data) != item["bytes"] or _sha(data) != item["sha256"]:
            raise ValueError("comparison_source_hash")
        inventory[name.casefold()] = {"bytes": len(data), "sha256": _sha(data)}
    return inventory


def _completed_supervisor(invocation):
    record = _json(Path(invocation).parent / "supervisor.json")
    if (record.get("started") is not True or type(record.get("exit_code")) is not int
            or record["exit_code"] != 0 or "stop_reason" not in record
            or record["stop_reason"] is not None or record.get("owned_process_only") is not True):
        raise ValueError("comparison_supervisor_did_not_complete_owned_process")
    return {"started": True, "exit_code": 0, "stop_reason": None, "owned_process_only": True}


def validate_invocations(continuous_path, split_path, resumed_path):
    paths = [Path(item).resolve() for item in (continuous_path, split_path, resumed_path)]
    if len(set(paths)) != 3:
        raise ValueError("comparison_requires_distinct_invocations")
    records = [_json(path) for path in paths]
    left, first, right = records
    arguments = [_arguments(record["arguments"], stop) for record, stop in zip(records, (2, 1, 2))]
    left_args, first_args, right_args = arguments
    if "resume" in left_args or "resume" in first_args or "resume" not in right_args:
        raise ValueError("comparison_requires_fresh_continuous_and_resumed_execution")
    outputs = [Path(item["output-dir"]) for item in arguments]
    if len(set(outputs)) != 3:
        raise ValueError("comparison_execution_output_collision")
    if Path(right_args["resume"]) != outputs[1] / "checkpoint-001":
        raise ValueError("comparison_resume_not_from_split_first_step")
    excluded = {"output-dir", "resume", "stop-after-step"}
    shared_left = {k: v for k, v in left_args.items() if k not in excluded}
    for item in arguments[1:]:
        if shared_left != {k: v for k, v in item.items() if k not in excluded}:
            raise ValueError("comparison_execution_settings_mismatch")
    for name in ("python", "site_packages", "interpreter_flags", "max_rss_mib",
                 "initial_free_ram_min_bytes", "initial_free_commit_min_bytes",
                 "runtime_free_ram_min_bytes", "runtime_free_commit_min_bytes",
                 "runtime_consecutive_low_samples"):
        if any(name not in item for item in records) or any(item[name] != left[name] for item in records[1:]):
            raise ValueError("comparison_runtime_or_guard_mismatch:" + name)
    inventories = [_snapshot_inventory(path, record) for path, record in zip(paths, records)]
    if any(item != inventories[0] for item in inventories[1:]):
        raise ValueError("comparison_execution_source_mismatch")
    supervisors = [_completed_supervisor(path) for path in paths]
    return left_args, first_args, right_args, inventories[0], supervisors


def _numeric_comparison(left, right):
    import numpy as np
    a, b = left.numpy().reshape(-1), right.numpy().reshape(-1)
    max_abs, sum_squares, differences = 0.0, 0.0, 0
    # Bounded float64 chunks avoid large temporary copies and cancellation.
    for start in range(0, a.size, 65536):
        stop = start + 65536
        delta = a[start:stop].astype(np.float64) - b[start:stop].astype(np.float64)
        max_abs = max(max_abs, float(np.abs(delta).max()))
        sum_squares += float(np.dot(delta, delta))
        differences += int(np.count_nonzero(delta))
    a_raw, b_raw = a.tobytes(), b.tobytes()
    return {"bitexact": a_raw == b_raw, "numeric_different_elements": differences,
            "max_abs_error": max_abs, "rmse": (sum_squares / a.size) ** 0.5,
            "continuous_sha256": _sha(a_raw), "resumed_sha256": _sha(b_raw),
            "elements": int(a.size)}


def compare_states(continuous_invocation, split_invocation, resumed_invocation, expected_contract):
    """Validate live invocation evidence, then compare step1 and step2 states."""
    validate_contract(expected_contract)
    if expected_contract["steps"] != 20:
        raise ValueError("comparison_requires_twenty_step_contract")
    left_args, first_args, right_args, sources, supervisors = validate_invocations(
        continuous_invocation, split_invocation, resumed_invocation)
    for key, argument in (("seed", "seed"), ("steps", "steps"), ("query_chunk", "query-chunk")):
        if expected_contract[key] != left_args[argument]:
            raise ValueError("comparison_contract_invocation_mismatch:" + key)
    left_folder, right_folder = Path(left_args["output-dir"]), Path(right_args["output-dir"])
    for folder in (left_folder, right_folder):
        final = _json(folder / "final.json")
        if (final.get("success") is not True or final.get("checkpoint_only") is not True
                or final.get("completed_steps") != 2 or final.get("requested_steps") != 20):
            raise ValueError("comparison_process_did_not_finish_two_steps")
    resume_folder = Path(right_args["resume"])
    if resume_folder.parent in {left_folder, right_folder}:
        raise ValueError("comparison_requires_separate_stopped_first_step_run")
    first_final = _json(resume_folder.parent / "final.json")
    if (first_final.get("success") is not True or first_final.get("checkpoint_only") is not True
            or first_final.get("completed_steps") != 1 or first_final.get("requested_steps") != 20):
        raise ValueError("comparison_first_process_did_not_stop_after_one_step")
    records = []
    for step in (1, 2):
        left_state = left_folder / f"checkpoint-{step:03d}"
        right_state = resume_folder if step == 1 else right_folder / "checkpoint-002"
        left_images, left_step, left_rng = load_state(left_state, expected_contract)
        right_images, right_step, right_rng = load_state(right_state, expected_contract)
        if left_step != step or right_step != step:
            raise ValueError("comparison_completed_step_mismatch")
        pixels = _numeric_comparison(left_images, right_images)
        left_rng_raw, right_rng_raw = left_rng.numpy().tobytes(), right_rng.numpy().tobytes()
        records.append({"completed_steps": step, "images": pixels,
                        "rng_bitexact": left_rng_raw == right_rng_raw,
                        "continuous_rng_sha256": _sha(left_rng_raw),
                        "resumed_rng_sha256": _sha(right_rng_raw),
                        "continuous_manifest_sha256": _sha((left_state / MANIFEST_FILE).read_bytes()),
                        "resumed_manifest_sha256": _sha((right_state / MANIFEST_FILE).read_bytes())})
        del left_images, right_images, left_rng, right_rng
    exact = all(item["images"]["bitexact"] and item["rng_bitexact"] for item in records)
    return {"schema": "fashionai-p42-two-step-comparison-v1", "result": "PASS" if exact else "FAIL",
            "bitexact_required": True, "numeric_tolerance_used": False,
            "completed_steps": 2, "requested_steps": 20, "generation_complete": False,
            "quality_verified": False, "model_authenticity_verified_by_this_comparator": False,
            "invocation_settings_equal": True, "source_snapshots_equal": True,
            "completed_processes": dict(zip(("continuous", "split_first", "resumed"), supervisors)),
            "threads": left_args["threads"], "contract": dict(expected_contract),
            "source_inventory": sources, "comparisons": records}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--continuous-invocation", type=Path, required=True)
    parser.add_argument("--split-invocation", type=Path, required=True)
    parser.add_argument("--resumed-invocation", type=Path, required=True)
    parser.add_argument("--expected-contract", type=Path, required=True,
                        help="Caller-reviewed JSON contract, independently matched to fixed runner inputs")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    # Exclusive creation protects earlier evidence even when verification fails.
    with args.output.open("x", encoding="utf-8") as stream:
        try:
            result = compare_states(args.continuous_invocation, args.split_invocation, args.resumed_invocation,
                                    _json(args.expected_contract))
            exit_code = 0 if result["result"] == "PASS" else 1
        except (ValueError, OSError, KeyError, TypeError) as error:
            result = {"result": "ERROR", "reason": str(error), "generation_complete": False}
            exit_code = 2
        json.dump(result, stream, indent=2, allow_nan=False)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
