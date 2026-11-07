"""Monitor contract checks use fake owned processes; never launch a model."""
import argparse
import ast
from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from p41_monitor import GIB, MIB, parse_args, run, snapshot_sources, snapshot_bootstrap, validate_runtime


class FakeProcess:
    def __init__(self, exit_after=None):
        self.pid = 12345
        self.exit_after = exit_after
        self.ticks = 0
        self.code = None
        self.terminated = self.killed = False

    def poll(self):
        if self.exit_after is not None and self.ticks >= self.exit_after and self.code is None:
            self.code = 0
        return self.code

    def terminate(self):
        self.terminated = True
        self.code = 1

    def kill(self):
        self.killed = True
        self.code = 1

    def wait(self, timeout):
        if self.poll() is None:
            raise RuntimeError("fake process still running")
        return self.code


class MonitorTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.probe = self.root / "probe.py"
        self.probe.write_text("# immutable experiment\n", encoding="utf-8")
        self.helper = self.root / "helper.py"
        self.helper.write_text("VALUE = 3\n", encoding="utf-8")
        self.python = self.root / "base" / "python.exe"
        self.python.parent.mkdir()
        self.python.write_bytes(b"fake executable is never run")
        self.site_packages = self.root / "approved-venv" / "Lib" / "site-packages"
        self.site_packages.mkdir(parents=True)
        (self.site_packages.parent.parent / "pyvenv.cfg").write_text("home = test\n")
        self.args = argparse.Namespace(python=self.python, site_packages=self.site_packages, probe=self.probe,
                                      helper=[self.helper], output=self.root / "output",
                                      max_seconds=10, max_rss_mib=1536, probe_args=["--", "test"])
        self.process = FakeProcess()
        self.now = 0
        self.launches = []

    @staticmethod
    def memory(ram=700 * MIB, commit=3 * GIB):
        return {"free_ram": ram, "free_commit": commit,
                "total_ram": 8 * GIB, "total_commit": 24 * GIB}

    @staticmethod
    def counters(rss=100 * MIB):
        return {"rss_bytes": rss, "peak_rss_bytes": rss,
                "private_commit_bytes": 200 * MIB, "peak_commit_bytes": 250 * MIB,
                "cpu_user_seconds": 1.25, "cpu_kernel_seconds": 0.5,
                "cpu_total_seconds": 1.75}

    def execute(self, memories=None, sample=None, launch_error=None):
        values = list(memories or [self.memory()])
        def memory():
            return values.pop(0) if len(values) > 1 else values[0]
        def popen(command, **kwargs):
            self.launches.append((command, kwargs))
            if launch_error:
                raise launch_error
            return self.process
        def sleep(seconds):
            self.now += seconds
            self.process.ticks += 1
        class Meter:
            def sample(inner):
                return sample() if sample else self.counters()
        with redirect_stdout(io.StringIO()):
            code = run(self.args, memory=memory, meter_factory=lambda _: Meter(),
                       popen=popen, clock=lambda: self.now, sleep=sleep)
        record = json.loads((self.args.output / "supervisor.json").read_text())
        return code, record

    def test_snapshots_probe_and_helpers_by_hash_without_overwrite(self):
        folder = self.root / "snap"
        folder.mkdir()
        captured, inventory = snapshot_sources(self.probe, [self.helper], folder)
        self.assertEqual(captured.read_bytes(), self.probe.read_bytes())
        self.assertEqual(inventory[1]["sha256"], hashlib.sha256(self.helper.read_bytes()).hexdigest())
        with self.assertRaises(FileExistsError):
            snapshot_sources(self.probe, [self.helper], folder)

    def test_duplicate_basename_is_rejected(self):
        with self.assertRaises(ValueError):
            snapshot_sources(self.probe, [self.probe], self.root)

    def test_initial_ram_reserve_does_not_launch(self):
        code, result = self.execute([self.memory(ram=511 * MIB)])
        self.assertEqual(code, 1)
        self.assertEqual(result["stop_reason"], "insufficient_initial_ram_reserve")
        self.assertFalse(result["started"])
        self.assertEqual(self.launches, [])

    def test_initial_commit_reserve_does_not_launch(self):
        _, result = self.execute([self.memory(commit=2 * GIB - 1)])
        self.assertEqual(result["stop_reason"], "insufficient_initial_commit_reserve")
        self.assertEqual(self.launches, [])

    def test_three_consecutive_low_ram_samples_stop_only_owned_child(self):
        _, result = self.execute([self.memory(), self.memory(ram=95 * MIB)])
        self.assertEqual(result["stop_reason"], "system_memory_reserve_reached")
        self.assertEqual(result["elapsed_seconds"], 2)
        self.assertTrue(self.process.terminated)
        self.assertFalse(self.process.killed)

    def test_recovered_reserve_resets_low_sample_counter(self):
        self.process.exit_after = 4
        code, result = self.execute([self.memory(), self.memory(ram=95 * MIB),
                                    self.memory(), self.memory(ram=95 * MIB), self.memory()])
        self.assertEqual(code, 0)
        self.assertIsNone(result["stop_reason"])

    def test_peak_rss_ceiling_is_independent_of_global_commit(self):
        _, result = self.execute(sample=lambda: self.counters(1537 * MIB))
        self.assertEqual(result["stop_reason"], "owned_process_rss_limit_reached")
        self.assertTrue(self.process.terminated)

    def test_success_records_process_cpu_and_isolates_helper_import_path(self):
        self.process.exit_after = 2
        code, result = self.execute()
        self.assertEqual(code, 0)
        samples = [json.loads(line) for line in (self.args.output / "resources.jsonl").read_text().splitlines()]
        self.assertEqual(len(samples), 2)
        self.assertEqual(samples[0]["process"]["cpu_total_seconds"], 1.75)
        self.assertIn("free_commit", samples[0]["system"])
        command, kwargs = self.launches[0]
        self.assertEqual(command[1:4], ["-I", "-S", "-u"])
        self.assertEqual(command[4], str(self.args.output / "bootstrap.py"))
        self.assertNotIn("PYTHONPATH", kwargs["env"])
        self.assertNotIn("PYTHONHOME", kwargs["env"])
        self.assertEqual(kwargs["env"]["HF_HUB_OFFLINE"], "1")
        self.assertEqual(result["owned_child_pid"], 12345)
        self.assertEqual(result["final_process_sample"]["private_commit_bytes"], 200 * MIB)

    def test_measurement_failure_stops_owned_process_and_records_error(self):
        def failed():
            raise OSError("synthetic measurement failure")
        _, result = self.execute(sample=failed)
        self.assertEqual(result["stop_reason"], "monitor_or_launch_error")
        self.assertEqual(result["exception_type"], "OSError")
        self.assertTrue(self.process.killed)

    def test_launch_failure_is_not_reported_as_started(self):
        _, result = self.execute(launch_error=OSError("synthetic launch failure"))
        self.assertFalse(result["started"])
        self.assertEqual(result["exception_type"], "OSError")
        self.assertIsNone(result["exit_code"])

    def test_timeout_is_bounded(self):
        self.args.max_seconds = 2
        _, result = self.execute()
        self.assertEqual(result["stop_reason"], "bounded_wall_timeout")
        self.assertEqual(result["elapsed_seconds"], 2)

    def test_cli_rejects_unbounded_duration_and_negative_rss(self):
        base = ["--python", "python", "--site-packages", "approved", "--probe", "probe", "--output", "out"]
        for extra in (["--max-seconds", "1801"], ["--max-rss-mib", "-1"]):
            with self.subTest(extra=extra), redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit):
                    parse_args(base + extra)

    def test_venv_launcher_is_rejected_before_any_process(self):
        launcher = self.site_packages.parent.parent / "Scripts" / "python.exe"
        launcher.parent.mkdir()
        launcher.write_bytes(b"fake launcher")
        self.args.python = launcher
        _, result = self.execute()
        self.assertFalse(result["started"])
        self.assertEqual(self.launches, [])
        self.assertEqual(result["exception_type"], "ValueError")

    def test_explicit_site_packages_must_exist_and_belong_to_venv(self):
        with self.assertRaisesRegex(ValueError, "directory_missing"):
            validate_runtime(self.python, self.root / "missing")
        global_site = self.root / "Lib" / "site-packages"
        global_site.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, "explicit_venv"):
            validate_runtime(self.python, global_site)

    def test_bootstrap_preserves_arguments_and_hash_without_interpolation(self):
        folder = self.root / "bootstrap-test"
        folder.mkdir()
        arguments = ["literal quote'\\\"\n", "$(not-a-command)", "한글"]
        path, metadata = snapshot_bootstrap(self.probe, self.site_packages, arguments, folder)
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source)
        assignment = next(node for node in tree.body if isinstance(node, ast.Assign)
                          and isinstance(node.targets[0], ast.Name) and node.targets[0].id == "PAYLOAD")
        payload = json.loads(ast.literal_eval(assignment.value.args[0]))
        self.assertEqual(payload["arguments"], arguments)
        self.assertEqual(payload["site_packages"], str(self.site_packages))
        self.assertEqual(metadata["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
        self.assertIn('runpy.run_path(PAYLOAD["probe"], run_name="__main__")', source)
        self.assertNotIn("subprocess", source)
        self.assertIn("sys.flags.isolated", source)
        self.assertIn("sys.flags.no_site", source)


if __name__ == "__main__":
    unittest.main()
