"""Offline child-process boundary tests. No Gradio requests/GPU/model/network execution."""
import os
from pathlib import Path
import subprocess
import sys
import threading

import pytest

import p46_research_supervisor as supervisor


def child(code):
    return subprocess.Popen([sys.executable, "-c", code], stdin=subprocess.DEVNULL,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                            env=supervisor.child_environment(os.environ, Path.cwd()),
                            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))


def test_future_timeout_and_gradio_close_leave_local_worker_running():
    from concurrent.futures import ThreadPoolExecutor, TimeoutError

    # Use the installed SDK's actual close method without initialization or a
    # network request. This proves local worker behavior, not a provider error
    # or remote cancellation. Optional SDK absence is explicitly a skip.
    sdk = pytest.importorskip("gradio_client")
    client = sdk.Client.__new__(sdk.Client)
    client._kill_heartbeat = threading.Event()
    client.heartbeat = threading.Thread(target=client._kill_heartbeat.wait,
                                        kwargs={"timeout": 5})
    client.executor = ThreadPoolExecutor(max_workers=1)
    gate, started = threading.Event(), threading.Event()

    def blocked_work():
        started.set()
        return gate.wait(timeout=5)

    client.heartbeat.start()
    future = client.executor.submit(blocked_work)
    try:
        assert started.wait(timeout=2)
        with pytest.raises(TimeoutError):
            future.result(timeout=.08)
        client.close()
        assert client._kill_heartbeat.is_set()
        assert not client.heartbeat.is_alive()
        assert future.running() and not future.done()
        assert not gate.is_set()
    finally:
        gate.set()
        client._kill_heartbeat.set()
        client.heartbeat.join(timeout=2)
        client.executor.shutdown(wait=True)


def test_deadline_stops_and_reaps_only_owned_child_without_remote_success():
    processes = []
    def spawn():
        p = child("import time; time.sleep(30)")
        processes.append(p)
        return p
    outcome = supervisor._supervise(spawn, deadline_seconds=.12)
    assert len(processes) == 1 and processes[0].poll() is not None
    assert outcome.local_state == "terminated" and outcome.reason == "deadline"
    assert outcome.remote_outcome == "UNKNOWN" and not outcome.remote_cancel_confirmed
    assert outcome.generation_retries == 0 and not outcome.allowance_refunded


def test_cancel_stops_owned_child_but_not_another_process():
    cancel = threading.Event()
    unrelated = child("import time; time.sleep(30)")
    owned = []
    def spawn():
        p = child("import time; time.sleep(30)")
        owned.append(p)
        cancel.set()
        return p
    try:
        outcome = supervisor._supervise(spawn, deadline_seconds=2, cancel=cancel)
        assert owned[0].poll() is not None and unrelated.poll() is None
        assert outcome.local_state == "terminated" and outcome.reason == "cancel_requested"
        assert outcome.remote_outcome == "UNKNOWN"
    finally:
        unrelated.kill()
        unrelated.wait(timeout=5)


def test_cancel_before_start_never_spawns():
    cancel = threading.Event(); cancel.set()
    def no_spawn():
        pytest.fail("No process should start")
    outcome = supervisor._supervise(no_spawn, deadline_seconds=1, cancel=cancel)
    assert outcome.local_state == "not_started" and outcome.remote_outcome == "NOT_SUBMITTED"


def test_exit_zero_does_not_claim_ai_generation_success():
    outcome = supervisor._supervise(lambda: child("pass"), deadline_seconds=10)
    assert outcome.exit_code == 0 and outcome.local_state == "exited"
    assert outcome.remote_outcome == "UNVERIFIED"


def test_launch_error_cannot_claim_submission():
    def missing(): raise OSError("synthetic launch error")
    result = supervisor._supervise(missing, deadline_seconds=1)
    assert result.reason == "launch_failed" and result.remote_outcome == "NOT_SUBMITTED"


def test_unconfirmed_termination_does_not_claim_worker_or_remote_cancelled():
    class CannotConfirm:
        returncode = None
        def poll(self): return None
        def terminate(self): pass
        def kill(self): pass
        def wait(self, timeout): raise subprocess.TimeoutExpired("synthetic", timeout)
    result = supervisor._supervise(lambda: CannotConfirm(), deadline_seconds=.01,
                                   terminate_grace=.01, kill_grace=.01)
    assert result.local_state == "termination_unconfirmed" and result.exit_code is None
    assert result.remote_outcome == "UNKNOWN" and not result.remote_cancel_confirmed


@pytest.mark.parametrize("limit", [0, -1, float("nan"), float("inf"), 901, True])
def test_invalid_deadline_never_spawns(limit):
    def no_spawn(): pytest.fail("invalid limit spawned a child")
    with pytest.raises(ValueError): supervisor._supervise(no_spawn, deadline_seconds=limit)


def test_child_environment_drops_secrets_and_does_not_mutate_parent(tmp_path):
    original = {"SYSTEMROOT": "C:/Windows", "PATH": "private_path", "HF_TOKEN": "synthetic",
                "AWS_SECRET_ACCESS_KEY": "synthetic", "HTTPS_PROXY": "synthetic",
                "LOCAL_VTON_TOKEN": "synthetic", "PYTHONPATH": "private_modules",
                "APPDATA": "private_profile", "HUGGING_FACE_HUB_TOKEN": "synthetic"}
    before = dict(original)
    env = supervisor.child_environment(original, tmp_path)
    assert original == before
    assert env["SYSTEMROOT"] == "C:/Windows"
    assert not set(original).difference({"SYSTEMROOT"}).intersection(env)
    assert env["HF_HUB_DISABLE_IMPLICIT_TOKEN"] == "1"
    assert env["HF_HOME"] == str(tmp_path / "empty-hf-home")


def test_production_command_is_fixed_without_shell_or_user_payload():
    argv = supervisor.fixed_trial_command()
    assert len(argv) == 7
    assert Path(argv[0]).name == "python.exe" and Path(argv[1]).name == "p45_public_demo_trial.py"
    assert argv[2] == "--inputs" and argv[4] == "--evidence"
    assert Path(argv[5]).name == "official-research-trial-01"


def test_explicit_approval_and_existing_journal_both_block_before_spawn(tmp_path, monkeypatch):
    assert supervisor.run_public_research_trial().reason == "explicit_approval_required"
    evidence = tmp_path / "p45-evidence"; evidence.mkdir()
    journal = evidence / "one-shot.sqlite3"; journal.write_bytes(b"existing evidence sentinel")
    before = journal.read_bytes()
    monkeypatch.setattr(supervisor, "_EVIDENCE", evidence)
    monkeypatch.setattr(subprocess, "Popen", lambda *a, **k: pytest.fail("spent trial spawned"))
    result = supervisor.run_public_research_trial(explicitly_approved=True)
    assert result.reason == "trial_already_reserved" and result.remote_outcome == "NOT_SUBMITTED"
    assert journal.read_bytes() == before
