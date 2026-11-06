"""Pure stage projection tests: no client, account, API request or inference."""
from copy import deepcopy
from uuid import uuid4

import pytest

import genlook_outfit_provider as mod


def active(mode=mod.PAIRED_MODE):
    provider = mod.GenlookProvider(mod.GenlookSettings(mode=mode))
    identity = str(uuid4())
    input_sha = "a" * 64
    job = {"input_sha": input_sha, "status": "reserved", "stages": provider._new_stages("synthetic-private")}
    provider._ledger = {"version": 2 if mode == mod.PAIRED_MODE else 1,
                        "contract_sha": provider.contract_sha, "jobs": {identity: job}}
    provider._busy = True
    provider._current_job = identity
    return provider, identity, input_sha, job


def test_paired_snapshot_follows_confirmed_stage_transition_without_percentage_or_identifiers():
    provider, identity, input_sha, job = active()
    first, shoes = job["stages"]
    for state, phase in (("unsubmitted", "preparing"), ("uploading", "uploading"),
                         ("uploaded", "submitting"), ("submitting", "submitting"),
                         ("polling", "waiting_for_generation"), ("downloaded", "validating_result")):
        first["state"] = state
        before = deepcopy(provider._ledger)
        actual = provider.public_progress(identity, input_sha)
        assert actual == {"mode": "clothing-pair-then-shoes", "stage_index": 1,
                          "stage_count": 2, "phase": phase}
        assert provider._ledger == before
        assert provider._api is None and provider._downloads is None
    first["generation_id"] = "synthetic-first-private-id"
    first["result_sha"] = "b" * 64
    assert provider.public_progress(identity, input_sha) == {
        "mode": "clothing-pair-then-shoes", "stage_index": 2, "stage_count": 2, "phase": "preparing"}
    shoes["state"] = "polling"
    assert provider.public_progress(identity, input_sha)["phase"] == "waiting_for_generation"
    shoes["state"] = "downloaded"
    assert provider.public_progress(identity, input_sha)["phase"] == "validating_result"
    shoes["result_sha"] = "c" * 64
    finalizing = provider.public_progress(identity, input_sha)
    assert finalizing == {"mode": "clothing-pair-then-shoes", "stage_index": 2,
                          "stage_count": 2, "phase": "finalizing"}
    assert "private" not in str(finalizing) and identity not in str(finalizing)
    assert input_sha not in str(finalizing) and "result_sha" not in finalizing


def test_first_stage_failure_does_not_report_shoes_even_when_other_state_is_stale():
    provider, identity, input_sha, job = active()
    job["stages"][0]["state"] = "failed"
    assert provider.public_progress(identity, input_sha) is None
    job["stages"][1]["state"] = "polling"
    assert provider.public_progress(identity, input_sha) is None


def test_finished_upload_does_not_regress_current_stage_phase():
    provider, identity, input_sha, job = active()
    # The Android stage contract rejects a same-stage phase moving backwards.
    # Completing the upload prepares the generation submission, not a new stage.
    ordering = {"preparing": 0, "uploading": 1, "submitting": 2,
                "waiting_for_generation": 3, "validating_result": 4, "finalizing": 5}
    observed = []
    for state in ("uploading", "uploaded", "submitting"):
        job["stages"][0]["state"] = state
        snapshot = provider.public_progress(identity, input_sha)
        assert snapshot["stage_index"] == 1
        observed.append(ordering[snapshot["phase"]])
    assert observed == sorted(observed), "Finishing upload must not move back to preparing"


def test_snapshot_requires_current_job_and_exact_input_and_contract():
    provider, identity, input_sha, job = active()
    assert provider.public_progress(str(uuid4()), input_sha) is None
    assert provider.public_progress(identity, "b" * 64) is None
    assert provider.public_progress(identity, "invalid") is None
    assert provider.public_progress([], input_sha) is None
    provider._current_job = str(uuid4())
    assert provider.public_progress(identity, input_sha) is None
    provider._current_job = identity
    provider._ledger["contract_sha"] = "c" * 64
    assert provider.public_progress(identity, input_sha) is None
    provider._ledger["contract_sha"] = provider.contract_sha
    provider._ledger["version"] = 1
    assert provider.public_progress(identity, input_sha) is None


@pytest.mark.parametrize("status", ["completed", "failed", "cancelled", "uncertain", "cleanup_failed"])
def test_terminal_or_uncertain_provider_states_hide_progress(status):
    provider, identity, input_sha, job = active()
    job["status"] = status
    assert provider.public_progress(identity, input_sha) is None


def test_cancelled_and_restarted_not_owned_jobs_never_show_stale_progress():
    provider, identity, input_sha, job = active()
    job["stages"][0]["state"] = "polling"
    provider._cancelled.add(identity)
    assert provider.public_progress(identity, input_sha) is None
    provider._cancelled.clear()
    provider._busy = False
    assert provider.public_progress(identity, input_sha) is None
    provider._busy = True
    provider._current_job = None
    assert provider.public_progress(identity, input_sha) is None


def test_sequential_mode_retains_three_distinct_workflow_stages():
    provider, identity, input_sha, job = active(mod.SEQUENTIAL_MODE)
    for index in range(3):
        snapshot = provider.public_progress(identity, input_sha)
        assert snapshot == {"mode": "sequential-3", "stage_index": index + 1,
                            "stage_count": 3, "phase": "preparing"}
        job["stages"][index].update(state="downloaded", result_sha=str(index + 1) * 64)
    assert provider.public_progress(identity, input_sha) == {
        "mode": "sequential-3", "stage_index": 3, "stage_count": 3, "phase": "finalizing"}


def test_invalid_ledger_stage_or_result_hash_has_no_public_snapshot():
    provider, identity, input_sha, job = active()
    baseline = deepcopy(job["stages"])
    for bad in (None, [], [{"state": "polling"}], ["private", "private"]):
        job["stages"] = bad
        assert provider.public_progress(identity, input_sha) is None
    job["stages"] = deepcopy(baseline)
    job["stages"][0]["roles"] = ["shoes"]
    assert provider.public_progress(identity, input_sha) is None
    job["stages"] = deepcopy(baseline)
    job["stages"][0]["state"] = "downloaded"
    job["stages"][0]["result_sha"] = "not-a-verified-png-hash"
    assert provider.public_progress(identity, input_sha) is None
    job["stages"][0]["state"] = "https://private.invalid/should-not-be-returned"
    assert provider.public_progress(identity, input_sha) is None
