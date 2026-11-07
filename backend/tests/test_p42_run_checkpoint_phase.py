import json
from pathlib import Path

import pytest

from p42_run_checkpoint_phase import build_arguments, preflight_block_reason, START_RAM_BYTES, START_COMMIT_BYTES


def reference():
    return {"arguments": ["--source-dir", "source", "--source-manifest", "manifest",
                          "--weights-dir", "weights", "--prepared-dir", "inputs",
                          "--output-dir", "old", "--steps", "20", "--stop-after-step", "1"]}


@pytest.mark.parametrize("phase,target", [("continuous2", "2"), ("split1", "1")])
def test_preserves_twenty_step_schedule(phase, target):
    result = build_arguments(reference(), Path("new"), phase)
    options = dict(zip(result[::2], result[1::2]))
    assert options["--steps"] == "20"
    assert options["--stop-after-step"] == target
    assert options["--threads"] == "2"
    assert options["--seed"] == "42"
    assert options["--prepared-dir"] == "inputs"
    assert "--diagnostic" not in options


def test_rejects_diagnostic_schedule():
    value = reference()
    value["arguments"][value["arguments"].index("--steps") + 1] = "2"
    with pytest.raises(ValueError, match="standard20"):
        build_arguments(value, Path("new"), "continuous2")


def test_resume_requires_intermediate_one(tmp_path):
    (tmp_path / "sampling-state.json").write_text(json.dumps({"completed_steps": 2, "contract": {"steps": 20}}))
    with pytest.raises(ValueError, match="actual_step1"):
        build_arguments(reference(), Path("new"), "resume2", tmp_path)


def test_resume_is_passed_to_strict_sampler_validation(tmp_path):
    (tmp_path / "sampling-state.json").write_text(json.dumps({"completed_steps": 1, "contract": {"steps": 20}}))
    result = build_arguments(reference(), Path("new"), "resume2", tmp_path)
    options = dict(zip(result[::2], result[1::2]))
    assert options["--resume"] == str(tmp_path.resolve())
    assert options["--stop-after-step"] == "2"


def test_forbids_ambiguous_duplicate_options():
    value = reference()
    value["arguments"] += ["--steps", "20"]
    with pytest.raises(ValueError, match="duplicate"):
        build_arguments(value, Path("new"), "continuous2")


@pytest.mark.parametrize("phase,resume", [("resume2", None), ("split1", Path("fake"))])
def test_resume_cannot_restart_or_change_path(phase, resume):
    with pytest.raises(ValueError, match="only_for_resume2"):
        build_arguments(reference(), Path("new"), phase, resume)


@pytest.mark.parametrize("ram,commit,reason", [
    (786612224, 7151472640, "p42_requires_stable_2gib_start_ram_reserve"),
    (START_RAM_BYTES, START_COMMIT_BYTES-1, "p42_requires_3gib_start_commit_reserve"),
    (START_RAM_BYTES, START_COMMIT_BYTES, None),
])
def test_observed_failed_start_cannot_repeat_under_new_safety_floor(ram, commit, reason):
    assert preflight_block_reason({"free_ram": ram, "free_commit": commit}) == reason
