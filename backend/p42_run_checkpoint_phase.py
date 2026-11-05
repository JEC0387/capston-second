"""Run ONE bounded P42 experiment phase; no automatic retry or model download.

Reuses the P41 monitor and sampler verbatim. The continuous and split paths all
use the first two steps of the SAME 20-step schedule, never a two-step schedule.
Outputs and snapshots must be new directories. This driver is PC research only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time

from p41_monitor import system_memory


HELPERS = ("p40_flatlay_probe.py", "p41_preprocess.py", "p41_streaming.py",
           "p41_attention.py", "p41_sampling_state.py")

# P42 split1 hit the runtime RAM guard despite passing P41's 512MiB startup
# floor. This conservative new start policy is NOT a measured minimum or a
# guarantee against pressure from other applications. P41 runtime guards stay.
START_RAM_BYTES = 2 * 1024**3
START_COMMIT_BYTES = 3 * 1024**3


def preflight_block_reason(memory):
    if memory["free_ram"] < START_RAM_BYTES:
        return "p42_requires_stable_2gib_start_ram_reserve"
    if memory["free_commit"] < START_COMMIT_BYTES:
        return "p42_requires_3gib_start_commit_reserve"
    return None


def build_arguments(reference, output, phase, resume=None):
    arguments = list(reference["arguments"])
    if len(arguments) % 2 or any(not arguments[i].startswith("--") for i in range(0, len(arguments), 2)):
        raise ValueError("reference_must_use_explicit_option_value_pairs")
    options = dict(zip(arguments[::2], arguments[1::2]))
    if len(options) != len(arguments) // 2:
        raise ValueError("duplicate_reference_option")
    allowed = {"--source-dir", "--source-manifest", "--weights-dir", "--prepared-dir",
               "--output-dir", "--steps", "--threads", "--query-chunk", "--seed", "--stop-after-step"}
    if set(options) - allowed or options.get("--steps") != "20":
        raise ValueError("only_original_standard20_reference_allowed")
    if phase not in {"continuous2", "split1", "resume2"}:
        raise ValueError("unknown_phase")
    if (phase == "resume2") != (resume is not None):
        raise ValueError("resume_required_only_for_resume2")
    options.update({"--output-dir": str(output), "--steps": "20", "--threads": "2",
                    "--query-chunk": "128", "--seed": "42",
                    "--stop-after-step": "1" if phase == "split1" else "2"})
    if resume is not None:
        resume = Path(resume).resolve(strict=True)
        record = json.loads((resume / "sampling-state.json").read_text(encoding="utf-8"))
        if record.get("completed_steps") != 1 or record.get("contract", {}).get("steps") != 20:
            raise ValueError("resume_requires_actual_step1_of_standard20")
        # p41_infer additionally verifies all hashes/schema/current contract.
        options["--resume"] = str(resume)
    return [part for pair in options.items() for part in pair]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--phase", choices=("continuous2", "split1", "resume2"), required=True)
    parser.add_argument("--resume", type=Path)
    args = parser.parse_args(argv)
    root = args.root.resolve(strict=True)
    output, supervisor = root / args.phase, root / (args.phase + "-supervisor")
    record_path = root / (args.phase + "-driver.json")
    if any(path.exists() for path in (output, supervisor, record_path)):
        raise ValueError("refusing_to_overwrite_existing_phase")
    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    extra = build_arguments(reference, output, args.phase, args.resume)
    source = Path(__file__).resolve().parent
    command = [sys.executable, str(source / "p41_monitor.py"),
               "--python", reference["python"], "--site-packages", reference["site_packages"],
               "--probe", str(source / "p41_infer.py"), "--output", str(supervisor),
               "--max-seconds", "1800", "--max-rss-mib", "1536"]
    for name in HELPERS:
        command.extend(("--helper", str(source / name)))
    command.extend(("--", *extra))
    baseline = system_memory()
    blocked = preflight_block_reason(baseline)
    record = {"phase": args.phase, "schedule_steps": 20, "completed_target": 1 if args.phase == "split1" else 2,
              "command": command, "memory_before": baseline, "automatic_retry": False,
              "driver_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "start_ram_reserve_bytes": START_RAM_BYTES, "start_commit_reserve_bytes": START_COMMIT_BYTES,
              "preflight_block_reason": blocked}
    # Exclusive creation also reserves this phase if interrupted before launch.
    with record_path.open("x", encoding="utf-8") as handle:
        json.dump(record, handle, indent=2)
    if blocked:
        print(json.dumps({"phase": args.phase, "started": False, "reason": blocked,
                          "memory_before": baseline}))
        return 2
    started = time.monotonic()
    outcome = subprocess.run(command, check=False)
    record.update(exit_code=outcome.returncode, elapsed_seconds=time.monotonic() - started,
                  memory_after_process_exit=system_memory())
    # This is only our newly created driver record, never an earlier run's file.
    record_path.write_text(json.dumps(record, indent=2), encoding="utf-8")
    print(json.dumps({key: record[key] for key in ("phase", "exit_code", "elapsed_seconds",
                                                 "memory_before", "memory_after_process_exit")}))
    return outcome.returncode


if __name__ == "__main__":
    raise SystemExit(main())
