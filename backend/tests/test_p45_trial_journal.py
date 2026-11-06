"""Synthetic ledger tests only: no image generation, network, or credentials."""
from concurrent.futures import ThreadPoolExecutor
from dataclasses import FrozenInstanceError, replace
import json
import sqlite3
from threading import Barrier

import pytest

from p45_trial_journal import (
    ENGINE, InvalidTransition, TrialAlreadyReserved, TrialJournal,
    TrialJournalError, TrialRequest,
)


def request():
    return TrialRequest("1" * 64, "2" * 64, "3" * 64, "4" * 64, "5" * 64)


def test_digest_binds_slots_and_generation_settings():
    original = request()
    assert len(original.fingerprint) == 64
    assert original.fingerprint == request().fingerprint
    assert replace(original, top_sha256=original.bottom_sha256,
                   bottom_sha256=original.top_sha256).fingerprint != original.fingerprint
    assert replace(original, seed=43).fingerprint != original.fingerprint
    assert replace(original, width=1024).fingerprint != original.fingerprint
    assert replace(original, prompt_sha256="6" * 64).fingerprint != original.fingerprint
    data = json.loads(original.canonical_json())
    assert data["n"] == 1 and data["engine"] == ENGINE
    assert [x["role"] for x in data["references"]] == ["person", "top", "bottom", "shoes"]
    with pytest.raises(FrozenInstanceError):
        original.seed = 99


@pytest.mark.parametrize("changes", [
    {"person_sha256": "not-a-hash"}, {"prompt_sha256": "A" * 64},
    {"seed": True}, {"seed": -1}, {"width": 0}, {"height": 1001},
    {"num_inference_steps": 1}, {"prompt_extend": True},
])
def test_invalid_or_unapproved_contract_rejected(changes):
    with pytest.raises(ValueError):
        replace(request(), **changes)


def test_restart_and_changed_clothing_cannot_reserve_again(tmp_path):
    path = tmp_path / "trial.sqlite3"
    journal = TrialJournal(path)
    reserved = journal.reserve(request())
    assert reserved.state == "RESERVED"
    reloaded = TrialJournal(path)
    assert reloaded.get(reserved.reservation_id) == reserved
    for candidate in (request(), replace(request(), top_sha256="9" * 64)):
        with pytest.raises(TrialAlreadyReserved):
            reloaded.reserve(candidate)


def test_parallel_connections_claim_only_once(tmp_path):
    path = tmp_path / "trial.sqlite3"
    TrialJournal(path)
    barrier = Barrier(4)

    def claim(_):
        journal = TrialJournal(path)
        barrier.wait(timeout=10)
        try:
            return journal.reserve(request()).state
        except TrialAlreadyReserved:
            return "DENIED"

    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(claim, range(4)))
    assert results.count("RESERVED") == 1
    assert results.count("DENIED") == 3


@pytest.mark.parametrize("outcome,reason", [
    ("unknown", "response_lost"), ("unknown", "waiting_cancelled"),
    ("unknown", "timeout"), ("failed", "provider_error"),
    ("blocked", "provider_quota_unavailable"),
])
def test_terminal_failure_never_refunds_or_allows_late_success(tmp_path, outcome, reason):
    journal = TrialJournal(tmp_path / "trial.sqlite3")
    reserved = journal.reserve(request())
    journal.mark_submitting(reserved.reservation_id)
    final = getattr(journal, "mark_" + outcome)(reserved.reservation_id, reason)
    assert final.state == outcome.upper()
    assert final.reason_code == reason
    with pytest.raises(TrialAlreadyReserved):
        journal.reserve(request())
    with pytest.raises(InvalidTransition):
        journal.mark_received(reserved.reservation_id, "a" * 64)
    with pytest.raises(InvalidTransition):
        journal.mark_submitting(reserved.reservation_id)
    assert journal.get(reserved.reservation_id) == final


def test_received_is_not_quality_pass_and_cannot_be_overwritten(tmp_path):
    journal = TrialJournal(tmp_path / "trial.sqlite3")
    job = journal.reserve(request())
    with pytest.raises(InvalidTransition):
        journal.mark_received(job.reservation_id, "a" * 64)
    journal.mark_submitting(job.reservation_id)
    result = journal.mark_received(job.reservation_id, "a" * 64, "event-123")
    assert result.state == "RECEIVED"
    assert result.output_sha256 == "a" * 64
    assert result.quality_status == "NOT_EVALUATED"
    with pytest.raises(InvalidTransition):
        journal.mark_received(job.reservation_id, "b" * 64)
    with pytest.raises(InvalidTransition):
        journal.mark_failed(job.reservation_id, "late_error")


def test_preflight_block_still_consumes_reservation(tmp_path):
    journal = TrialJournal(tmp_path / "trial.sqlite3")
    job = journal.reserve(request())
    assert journal.mark_blocked(job.reservation_id, "not_ready").state == "BLOCKED"
    with pytest.raises(TrialAlreadyReserved):
        journal.reserve(request())


def test_bad_reason_or_provider_id_never_persist_raw_errors(tmp_path):
    journal = TrialJournal(tmp_path / "trial.sqlite3")
    job = journal.reserve(request())
    journal.mark_submitting(job.reservation_id)
    with pytest.raises(ValueError):
        journal.mark_unknown(job.reservation_id, "https://private.example/error?key=secret")
    with pytest.raises(ValueError):
        journal.mark_received(job.reservation_id, "a" * 64, "token with spaces")
    assert journal.get(job.reservation_id).state == "SUBMITTING"


def test_corrupt_request_blocks_transition_without_reset(tmp_path):
    path = tmp_path / "trial.sqlite3"
    journal = TrialJournal(path)
    job = journal.reserve(request())
    with sqlite3.connect(path) as db:
        db.execute("UPDATE trial SET canonical_request='{}'")
    with pytest.raises(TrialJournalError, match="integrity"):
        journal.mark_submitting(job.reservation_id)
    with pytest.raises(TrialAlreadyReserved):
        journal.reserve(request())


def test_crash_after_submitting_remains_spent(tmp_path):
    path = tmp_path / "trial.sqlite3"
    first = TrialJournal(path)
    job = first.reserve(request())
    first.mark_submitting(job.reservation_id)
    restarted = TrialJournal(path)
    assert restarted.get(job.reservation_id).state == "SUBMITTING"
    with pytest.raises(TrialAlreadyReserved):
        restarted.reserve(request())
    restarted.mark_unknown(job.reservation_id, "process_interrupted")


def test_memory_db_not_allowed():
    with pytest.raises(ValueError, match="durable"):
        TrialJournal(":memory:")
