"""Bound the local P45 research client, never claim remote cancellation.

This is not a FastAPI route, Android engine, or free production service. There
is no shell command/endpoint supplied by a caller. P45's existing one-shot
evidence directory is fixed and its spent allowance is never reset here.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
import json
import math
import os
from pathlib import Path
import subprocess
import threading
import time
from typing import Callable, Mapping
from uuid import uuid4


_WORKSPACE = Path(__file__).resolve().parents[2]
_P45 = _WORKSPACE / "experiments" / "P45-20261106"
_SCRIPT = Path(__file__).with_name("p45_public_demo_trial.py")
_INPUTS = _P45 / "public-inputs-20261106-060727"
_EVIDENCE = _P45 / "official-research-trial-01"
_PYTHON = _P45 / ".venv-demo" / "Scripts" / "python.exe"


@dataclass(frozen=True)
class SupervisionResult:
    local_state: str
    reason: str
    exit_code: int | None
    elapsed_seconds: float
    remote_outcome: str
    # A terminated local client does not prove the remote GPU stopped.
    remote_cancel_confirmed: bool = False
    generation_retries: int = 0
    allowance_refunded: bool = False


def child_environment(parent: Mapping[str, str], scratch: Path) -> dict[str, str]:
    """Create a fresh child environment; do not mutate or log the parent's.

    Deliberately excludes credentials, proxy URLs, app tokens, HOME/APPDATA,
    PYTHONPATH, and inherited HF configuration. The fixed venv executable is
    absolute, so the caller's PATH is not required.
    """
    allowed = {"SYSTEMROOT", "WINDIR", "SYSTEMDRIVE", "NUMBER_OF_PROCESSORS"}
    result = {key: value for key, value in parent.items() if key.upper() in allowed}
    result.update({
        "TEMP": str(scratch / "temp"), "TMP": str(scratch / "temp"),
        "HF_HOME": str(scratch / "empty-hf-home"),
        "HF_HUB_DISABLE_IMPLICIT_TOKEN": "1", "HF_HUB_DISABLE_TELEMETRY": "1",
        "GRADIO_ANALYTICS_ENABLED": "False", "PYTHONIOENCODING": "utf-8",
        "PYTHONUTF8": "1", "PYTHONUNBUFFERED": "1",
    })
    return result


def fixed_trial_command() -> tuple[str, ...]:
    return (str(_PYTHON), str(_SCRIPT), "--inputs", str(_INPUTS),
            "--evidence", str(_EVIDENCE), "--execute-public-research-once")


def _stop_owned_child(process: subprocess.Popen, terminate_grace: float,
                      kill_grace: float) -> tuple[bool, int | None]:
    """Use only the Popen handle we created; never a caller-supplied PID/tree."""
    if process.poll() is not None:
        return True, process.returncode
    try:
        process.terminate()
    except OSError:
        pass  # Poll/wait below still determines whether termination happened.
    try:
        return True, process.wait(timeout=terminate_grace)
    except subprocess.TimeoutExpired:
        pass
    except OSError:
        if process.poll() is not None:
            return True, process.returncode
    try:
        process.kill()
    except OSError:
        pass
    try:
        return True, process.wait(timeout=kill_grace)
    except (subprocess.TimeoutExpired, OSError):
        return False, process.poll()


def _supervise(spawn: Callable[[], subprocess.Popen], *, deadline_seconds: float,
               cancel: threading.Event | None = None, poll_seconds: float = .025,
               terminate_grace: float = 1, kill_grace: float = 2) -> SupervisionResult:
    """Internal process seam for bounded synthetic tests; not a command API."""
    for value, upper in ((deadline_seconds, 900), (poll_seconds, 1),
                         (terminate_grace, 5), (kill_grace, 5)):
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 < value <= upper:
            raise ValueError("invalid_supervision_limit")
    start = time.monotonic()
    def result(local, reason, code, remote):
        return SupervisionResult(local, reason, code, round(time.monotonic()-start, 6), remote)
    if cancel is not None and cancel.is_set():
        return result("not_started", "cancel_before_start", None, "NOT_SUBMITTED")
    try:
        process = spawn()
    except OSError:
        return result("not_started", "launch_failed", None, "NOT_SUBMITTED")
    try:
        while True:
            code = process.poll()
            if code is not None:
                # Even exit 0 needs independent journal/PNG/request/quality
                # verification; process exit is not an AI success receipt.
                return result("exited", "worker_exited", code, "UNVERIFIED" if code == 0 else "UNKNOWN")
            reason = "cancel_requested" if cancel is not None and cancel.is_set() else (
                "deadline" if time.monotonic()-start >= deadline_seconds else None)
            if reason:
                stopped, code = _stop_owned_child(process, terminate_grace, kill_grace)
                return result("terminated" if stopped else "termination_unconfirmed", reason, code, "UNKNOWN")
            time.sleep(min(poll_seconds, max(.001, deadline_seconds-(time.monotonic()-start))))
    except BaseException:
        # An interrupted parent must not abandon its own network client.
        _stop_owned_child(process, terminate_grace, kill_grace)
        raise


def run_public_research_trial(*, explicitly_approved: bool = False,
                             deadline_seconds: float = 810,
                             cancel: threading.Event | None = None) -> SupervisionResult:
    """Explicit local entry point; intentionally no HTTP route or import side effect.

    The boolean is a caller's authorization decision, not proof of rights,
    provider quota, or service readiness. An existing P45 journal always blocks
    this entry point, even if an earlier attempt failed or was interrupted.
    """
    def blocked(reason):
        return SupervisionResult("not_started", reason, None, 0, "NOT_SUBMITTED")
    if explicitly_approved is not True:
        return blocked("explicit_approval_required")
    if (_EVIDENCE / "one-shot.sqlite3").exists():
        return blocked("trial_already_reserved")
    if cancel is not None and cancel.is_set():
        return blocked("cancel_before_start")
    if not _PYTHON.is_file() or not _SCRIPT.is_file() or not _INPUTS.is_dir():
        return blocked("fixed_trial_assets_missing")
    scratch = _WORKSPACE / "experiments" / "P46-20261106" / "supervisor" / uuid4().hex
    (scratch / "temp").mkdir(parents=True)
    (scratch / "empty-hf-home").mkdir()
    env = child_environment(os.environ, scratch)
    def spawn():
        return subprocess.Popen(fixed_trial_command(), cwd=_SCRIPT.parent,
                                env=env, stdin=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                shell=False, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    outcome = _supervise(spawn, deadline_seconds=deadline_seconds, cancel=cancel)
    # New, local-only record. Never overwrite P45's journal, PNG, or execution log.
    with (scratch / "supervision.json").open("x", encoding="utf-8") as stream:
        json.dump(asdict(outcome), stream, indent=2)
    return outcome
