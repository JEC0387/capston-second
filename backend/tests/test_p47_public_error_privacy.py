"""Offline failure privacy tests; synthetic inputs, SDK jobs, and temporary ledger.

These do not run a provider request, use private photos, or verify AI generation.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import sys
from types import SimpleNamespace

import pytest

import p45_public_demo_trial as trial
from p45_public_inputs import PublicInput
from p45_trial_journal import TrialAlreadyReserved, TrialJournal


MARKERS = (
    "P47_SIGNED_SENTINEL", "P47_TOKEN_SENTINEL", "P47_FAKE_PRIVATE",
    "P47_BODY_SENTINEL",
)
PRIVATE_ERROR = (
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=sample.png"
    "?signature=P47_SIGNED_SENTINEL token=P47_TOKEN_SENTINEL "
    "path=C:/P47_FAKE_PRIVATE/image.png body=P47_BODY_SENTINEL"
)


def _failure(monkeypatch, tmp_path, capsys, phase):
    actual_environment = dict(os.environ)
    input_root = tmp_path / "synthetic-inputs"
    input_root.mkdir()
    evidence = tmp_path / "synthetic-evidence"
    inputs = tuple(
        PublicInput(role, input_root / (role + ".png"),
                    hashlib.sha256(role.encode()).hexdigest(), len(role), role.encode())
        for role in ("person", "top", "bottom", "shoes")
    )
    counts = {"metadata": 0, "prepare": 0, "generation": 0, "closed": 0}

    def reject_network(*args, **kwargs):
        pytest.fail("Offline failure test attempted network access")

    monkeypatch.setattr(socket.socket, "connect", reject_network)
    monkeypatch.setattr(socket, "create_connection", reject_network)
    monkeypatch.setattr(trial, "os", SimpleNamespace(environ={"HF_TOKEN": "synthetic"}))

    def public_inputs(selected):
        assert Path(selected) == input_root
        return inputs

    def metadata(url, limit):
        assert url == "https://huggingface.co/api/spaces/Qwen/Qwen-Image-2.1"
        counts["metadata"] += 1
        if phase == "preflight":
            raise RuntimeError(PRIVATE_ERROR)
        return json.dumps({"sha": trial.REVISION, "runtime": {"stage": "RUNNING"}}).encode()

    def failed_result(timeout):
        assert timeout == 480
        raise RuntimeError(PRIVATE_ERROR)

    class FakeClient:
        def __init__(self, host, **kwargs):
            assert host == trial.HOST and kwargs["token"] is False
            self.headers = {}

        def view_api(self, **kwargs):
            parameters = {
                "/prepare_request": ["input_images", "original_prompt", "enable_extend",
                                     "custom_size", "quality", "seed", "randomize_seed"],
                "/generate_request": ["original_prompt", "enable_extend", "custom_size",
                                      "log_dir", "seed", "height", "width", "negative_prompt"],
            }
            return {"named_endpoints": {
                name: {"parameters": [{"parameter_name": field} for field in fields]}
                for name, fields in parameters.items()
            }}

        def submit(self, *args, api_name):
            if api_name == "/prepare_request":
                counts["prepare"] += 1
                return SimpleNamespace(result=lambda timeout: None)
            assert api_name == "/generate_request"
            counts["generation"] += 1
            return SimpleNamespace(result=failed_result)

        def close(self):
            counts["closed"] += 1

    monkeypatch.setitem(sys.modules, "httpx", SimpleNamespace(Timeout=lambda *a, **k: None))
    monkeypatch.setitem(sys.modules, "gradio_client", SimpleNamespace(
        Client=FakeClient, handle_file=lambda path: {"synthetic_file": path}))
    monkeypatch.setattr(trial, "load_public_inputs", public_inputs)
    monkeypatch.setattr(trial, "read_https", metadata)
    monkeypatch.setattr(sys, "argv", ["offline-test", "--inputs", str(input_root),
                                    "--evidence", str(evidence), "--execute-public-research-once"])
    with pytest.raises(SystemExit) as exit_info:
        trial.main()
    assert exit_info.value.code == 2
    assert dict(os.environ) == actual_environment
    captured = capsys.readouterr()
    raw = (evidence / "execution.json").read_text(encoding="utf-8")
    return evidence, json.loads(raw), raw + captured.out + captured.err + str(exit_info.value), counts


@pytest.mark.parametrize("phase", ["preflight", "generation"])
def test_external_failure_does_not_persist_signed_url_token_path_or_body(
        monkeypatch, tmp_path, capsys, phase):
    _, record, emitted, _ = _failure(monkeypatch, tmp_path, capsys, phase)
    for marker in MARKERS:
        assert marker not in emitted
    assert "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=" not in emitted
    assert re.fullmatch(r"[a-z][a-z0-9_]{0,63}", record["error"])
    assert record["failed_phase"] == phase


@pytest.mark.parametrize("phase, expected_state, expected_submits", [
    ("preflight", "BLOCKED", 0), ("generation", "UNKNOWN", 1),
])
def test_external_failure_preserves_terminal_reservation_and_never_reissues(
        monkeypatch, tmp_path, capsys, phase, expected_state, expected_submits):
    evidence, record, _, counts = _failure(monkeypatch, tmp_path, capsys, phase)
    assert record["phase"] == "blocked"
    assert record["journal"]["state"] == expected_state
    assert record["generation_submit_count"] == expected_submits
    assert counts == {"metadata": 1, "prepare": expected_submits,
                      "generation": expected_submits, "closed": expected_submits}
    assert not (evidence / "new-generated-outfit.png").exists()
    ledger = TrialJournal(evidence / "one-shot.sqlite3")
    before = ledger.get(record["journal"]["reservation_id"])
    prior_record = (evidence / "execution.json").read_bytes()
    calls_before = dict(counts)
    with pytest.raises(TrialAlreadyReserved, match="one_trial_already_reserved"):
        trial.main()
    assert counts == calls_before
    assert ledger.get(before.reservation_id) == before
    assert (evidence / "execution.json").read_bytes() == prior_record


def test_only_allowlisted_local_reasons_are_preserved_without_stringification():
    class MustNotStringify(RuntimeError):
        def __str__(self):
            raise AssertionError("Exception body must not be inspected")

    assert trial.safe_error_fields(MustNotStringify(PRIVATE_ERROR)) == {
        "error_type": "external_request", "error": "external_request_failed"}
    assert trial.safe_error_fields(ValueError(PRIVATE_ERROR)) == {
        "error_type": "validation", "error": "validation_failed"}
    assert trial.safe_error_fields(ValueError("unexpected_auth_header")) == {
        "error_type": "validation", "error": "unexpected_auth_header"}
    assert trial.safe_error_fields(TimeoutError(PRIVATE_ERROR)) == {
        "error_type": "timeout", "error": "request_timeout"}
