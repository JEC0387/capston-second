"""Bounded Windows experiment monitor; controls only its own direct child.

This is an experiment utility, not an app runtime or proof of model quality.
Source snapshots, process counters and system reserves are recorded separately.
"""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time


MIB = 1024 ** 2
GIB = 1024 ** 3


def system_memory():
    if os.name != "nt":
        raise RuntimeError("windows_memory_monitor_required")

    class Memory(ctypes.Structure):
        _fields_ = [("length", ctypes.c_uint32), ("load", ctypes.c_uint32)] + [
            (name, ctypes.c_uint64) for name in (
                "total_ram", "free_ram", "total_commit", "free_commit",
                "total_virtual", "free_virtual", "extended")]

    item = Memory()
    item.length = ctypes.sizeof(item)
    api = ctypes.WinDLL("kernel32", use_last_error=True).GlobalMemoryStatusEx
    api.argtypes = [ctypes.POINTER(Memory)]
    api.restype = ctypes.c_int
    if not api(ctypes.byref(item)):
        raise ctypes.WinError(ctypes.get_last_error())
    return {name: int(getattr(item, name)) for name in (
        "total_ram", "free_ram", "total_commit", "free_commit")}


class OwnedProcessMeter:
    """Use the live Popen handle, never a PID lookup that could target reuse."""

    def __init__(self, process):
        if os.name != "nt":
            raise RuntimeError("windows_process_monitor_required")
        self.handle = ctypes.c_void_p(int(process._handle))

        class Counters(ctypes.Structure):
            _fields_ = [("cb", ctypes.c_uint32), ("PageFaultCount", ctypes.c_uint32)] + [
                (name, ctypes.c_size_t) for name in (
                    "PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage",
                    "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
                    "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage",
                    "PrivateUsage")]

        class FileTime(ctypes.Structure):
            _fields_ = [("low", ctypes.c_uint32), ("high", ctypes.c_uint32)]

        self.Counters, self.FileTime = Counters, FileTime
        self.memory_api = ctypes.WinDLL("psapi", use_last_error=True).GetProcessMemoryInfo
        self.memory_api.argtypes = [ctypes.c_void_p, ctypes.POINTER(Counters), ctypes.c_uint32]
        self.memory_api.restype = ctypes.c_int
        self.times_api = ctypes.WinDLL("kernel32", use_last_error=True).GetProcessTimes
        self.times_api.argtypes = [ctypes.c_void_p] + [ctypes.POINTER(FileTime)] * 4
        self.times_api.restype = ctypes.c_int

    def sample(self):
        item = self.Counters()
        item.cb = ctypes.sizeof(item)
        if not self.memory_api(self.handle, ctypes.byref(item), item.cb):
            raise ctypes.WinError(ctypes.get_last_error())
        creation, exit_time, kernel, user = [self.FileTime() for _ in range(4)]
        if not self.times_api(self.handle, *map(ctypes.byref, (creation, exit_time, kernel, user))):
            raise ctypes.WinError(ctypes.get_last_error())
        seconds = lambda value: ((value.high << 32) | value.low) / 10_000_000
        return {
            "rss_bytes": int(item.WorkingSetSize),
            "peak_rss_bytes": int(item.PeakWorkingSetSize),
            "private_commit_bytes": int(item.PrivateUsage),
            "peak_commit_bytes": int(item.PeakPagefileUsage),
            "cpu_user_seconds": seconds(user), "cpu_kernel_seconds": seconds(kernel),
            "cpu_total_seconds": seconds(user) + seconds(kernel),
        }


def snapshot_sources(probe, helpers, folder):
    sources = [Path(probe), *map(Path, helpers)]
    names = [source.name.casefold() for source in sources]
    if len(names) != len(set(names)):
        raise ValueError("snapshot_source_basename_collision")
    captured = folder / "sources"
    captured.mkdir(exist_ok=False)
    inventory = []
    for source in sources:
        data = source.read_bytes()
        destination = captured / source.name
        with destination.open("xb") as output:
            output.write(data)
        inventory.append({"name": source.name, "bytes": len(data),
                          "sha256": hashlib.sha256(data).hexdigest()})
    return captured / sources[0].name, inventory


def validate_runtime(python, site_packages):
    """Reject Windows venv launchers: their handle is not the Python worker."""
    python, site_packages = Path(python).resolve(), Path(site_packages).resolve()
    if not python.is_file():
        raise ValueError("base_python_executable_missing")
    if (python.parent.name.casefold() == "scripts"
            or (python.parent / "pyvenv.cfg").exists()
            or (python.parent.parent / "pyvenv.cfg").exists()):
        raise ValueError("venv_launcher_not_direct_worker_use_base_python")
    if not site_packages.is_dir():
        raise ValueError("approved_site_packages_directory_missing")
    if (site_packages.name.casefold() != "site-packages"
            or site_packages.parent.name.casefold() != "lib"
            or not (site_packages.parent.parent / "pyvenv.cfg").is_file()):
        raise ValueError("approved_site_packages_must_belong_to_explicit_venv")
    return python, site_packages


def snapshot_bootstrap(probe, site_packages, arguments, folder):
    """Base Python executes the probe in the same process, without a launcher."""
    payload = {"probe": str(probe), "site_packages": str(site_packages),
               "arguments": arguments, "evidence": str(folder / "bootstrap.json")}
    source = '''import json, os, runpy, site, sys
from pathlib import Path
PAYLOAD = json.loads(%s)
if not sys.flags.isolated or not sys.flags.no_site or sys.prefix != sys.base_prefix:
    raise RuntimeError("direct_isolated_base_python_required")
sys.dont_write_bytecode = True
approved = Path(PAYLOAD["site_packages"]).resolve()
original_paths = tuple(sys.path)
site.addsitedir(str(approved))
# Preserve base stdlib paths, plus only the expressly approved environment.
sys.path[:] = [entry for entry in sys.path if entry in original_paths
               or Path(entry).resolve().is_relative_to(approved)]
sys.path.insert(0, str(Path(PAYLOAD["probe"]).parent))
sys.argv = [PAYLOAD["probe"], *PAYLOAD["arguments"]]
Path(PAYLOAD["evidence"]).write_text(json.dumps({
    "pid": os.getpid(), "parent_pid": os.getppid(), "executable": sys.executable,
    "isolated": bool(sys.flags.isolated), "no_site": bool(sys.flags.no_site),
    "base_prefix_matches": sys.prefix == sys.base_prefix,
    "site_packages": str(approved), "source_root": str(Path(PAYLOAD["probe"]).parent)
}, indent=2), encoding="utf-8")
runpy.run_path(PAYLOAD["probe"], run_name="__main__")
''' % repr(json.dumps(payload, ensure_ascii=True))
    data = source.encode("utf-8")
    destination = folder / "bootstrap.py"
    with destination.open("xb") as output:
        output.write(data)
    return destination, {"name": destination.name, "bytes": len(data),
                         "sha256": hashlib.sha256(data).hexdigest()}


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python", type=Path, required=True)
    parser.add_argument("--site-packages", type=Path, required=True,
                        help="Explicit approved venv Lib/site-packages, used with base Python -I -S")
    parser.add_argument("--probe", type=Path, required=True)
    parser.add_argument("--helper", type=Path, action="append", default=[])
    parser.add_argument("--output", "--log-dir", dest="output", type=Path, required=True)
    parser.add_argument("--max-seconds", "--maxseconds", dest="max_seconds", type=int, default=900)
    parser.add_argument("--max-rss-mib", type=int, default=1536,
                        help="Owned child peak RSS ceiling; 0 explicitly disables this optional ceiling")
    parser.add_argument("probe_args", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    if not 1 <= args.max_seconds <= 1800:
        parser.error("Maximum bounded run is 1800 seconds")
    if args.max_rss_mib < 0:
        parser.error("RSS ceiling cannot be negative")
    return args


def run(args, *, memory=system_memory, meter_factory=OwnedProcessMeter,
        popen=subprocess.Popen, clock=time.monotonic, sleep=time.sleep):
    folder = args.output.resolve()
    folder.mkdir(parents=True, exist_ok=False)
    start = clock()
    record = {"started": False, "exit_code": None, "stop_reason": None,
              "exception_type": None, "baseline_memory": None, "final_memory": None,
              "owned_process_only": True, "process_sample_count": 0,
              "process_observed_maxima": {}, "final_process_sample": None,
              "sample_interval_seconds": 1,
              "note": "Recorded OS peaks are lifetime peaks only through the last successful sample. "
                      "System counters include all applications. Exit success does not prove inference quality."}
    process = meter = None
    extra = args.probe_args[1:] if args.probe_args[:1] == ["--"] else args.probe_args

    def observe(value):
        record["process_sample_count"] += 1
        for name, amount in value.items():
            record["process_observed_maxima"][name] = max(
                record["process_observed_maxima"].get(name, 0), amount)

    try:
        python, site_packages = validate_runtime(args.python, args.site_packages)
        probe, inventory = snapshot_sources(args.probe, args.helper, folder)
        bootstrap, bootstrap_inventory = snapshot_bootstrap(probe, site_packages, extra, folder)
        invocation = {"python": str(python), "arguments": extra, "sources": inventory,
                      "site_packages": str(site_packages), "bootstrap": bootstrap_inventory,
                      "interpreter_flags": ["-I", "-S", "-u"],
                      "max_seconds": args.max_seconds, "max_rss_mib": args.max_rss_mib,
                      "initial_free_ram_min_bytes": 512 * MIB,
                      "initial_free_commit_min_bytes": 2 * GIB,
                      "runtime_free_ram_min_bytes": 96 * MIB,
                      "runtime_free_commit_min_bytes": 768 * MIB,
                      "runtime_consecutive_low_samples": 3}
        (folder / "invocation.json").write_text(json.dumps(invocation, indent=2), encoding="utf-8")
        baseline = memory()
        record["baseline_memory"] = baseline
        if baseline["free_ram"] < 512 * MIB:
            record["stop_reason"] = "insufficient_initial_ram_reserve"
        elif baseline["free_commit"] < 2 * GIB:
            record["stop_reason"] = "insufficient_initial_commit_reserve"
        else:
            environment = os.environ.copy()
            environment.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1",
                               HF_HUB_DISABLE_TELEMETRY="1", MPLBACKEND="Agg",
                               MPLCONFIGDIR=str(folder / "matplotlib-cache"),
                               PYTHONDONTWRITEBYTECODE="1")
            # -I ignores inherited PYTHON* options; remove these too for clarity.
            environment.pop("PYTHONPATH", None)
            environment.pop("PYTHONHOME", None)
            low_samples = 0
            with (folder / "process.log").open("x", encoding="utf-8") as output, \
                    (folder / "resources.jsonl").open("x", encoding="utf-8") as samples:
                process = popen([str(python), "-I", "-S", "-u", str(bootstrap)],
                                stdout=output, stderr=subprocess.STDOUT, env=environment,
                                cwd=str(probe.parent))
                record["started"] = True
                record["owned_child_pid"] = process.pid
                meter = meter_factory(process)
                while process.poll() is None:
                    elapsed = clock() - start
                    reserve = memory()
                    try:
                        counters = meter.sample()
                    except Exception:
                        if process.poll() is not None:
                            record["final_process_sample_unavailable"] = "exited_during_sampling"
                            break
                        raise
                    observe(counters)
                    samples.write(json.dumps({"elapsed_seconds": elapsed,
                                              "system": reserve, "process": counters}) + "\n")
                    samples.flush()
                    low_samples = low_samples + 1 if (
                        reserve["free_ram"] < 96 * MIB or reserve["free_commit"] < 768 * MIB) else 0
                    if elapsed >= args.max_seconds:
                        record["stop_reason"] = "bounded_wall_timeout"
                    elif low_samples >= 3:
                        record["stop_reason"] = "system_memory_reserve_reached"
                    elif args.max_rss_mib and counters["peak_rss_bytes"] > args.max_rss_mib * MIB:
                        record["stop_reason"] = "owned_process_rss_limit_reached"
                    if record["stop_reason"]:
                        process.terminate()
                        break
                    sleep(1)
                record["exit_code"] = process.wait(timeout=15)
    except Exception as error:
        record["stop_reason"] = "monitor_or_launch_error"
        record["exception_type"] = type(error).__name__
    finally:
        if process is not None:
            try:
                if process.poll() is None:
                    process.kill()
                record["exit_code"] = process.wait(timeout=15)
            except Exception as error:
                record["cleanup_exception_type"] = type(error).__name__
                record["stop_reason"] = record["stop_reason"] or "owned_child_cleanup_error"
            if meter is not None:
                try:
                    final = meter.sample()
                    observe(final)
                    record["final_process_sample"] = final
                except Exception as error:
                    record["final_process_sample_unavailable"] = type(error).__name__
        try:
            record["final_memory"] = memory()
        except Exception as error:
            record["final_memory_exception_type"] = type(error).__name__
            record["stop_reason"] = record["stop_reason"] or "final_resource_measurement_failed"
        record["elapsed_seconds"] = clock() - start
        (folder / "supervisor.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
    print(json.dumps(record), flush=True)
    return 0 if record["started"] and record["exit_code"] == 0 and not record["stop_reason"] else 1


if __name__ == "__main__":
    raise SystemExit(run(parse_args()))
