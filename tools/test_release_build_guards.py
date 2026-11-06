"""Local build-safety regressions. Never load signing material or use a remote.

The identity checks execute the Gradle-declared Git status arguments in a temporary
repository containing synthetic Android source. They do not approximate dirty
state with a second implementation, and do not mutate this checkout.
"""
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "fashion_app/app/build.gradle.kts"


def block_after(source, marker):
    start = source.index("{", source.index(marker))
    # Ignore braces inside comments and strings, while preserving source offsets.
    masked = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/',
                    lambda match: " " * len(match.group()), source, flags=re.S)
    depth = 0
    for index in range(start, len(masked)):
        if masked[index] == "{":
            depth += 1
        elif masked[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start + 1:index]
    raise AssertionError("Expected a complete Kotlin block")


class ReleaseBuildGuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = GRADLE.read_text(encoding="utf-8")
        cls.git = shutil.which("git")
        if not cls.git:
            raise RuntimeError("Git is required for the local source-identity regression")

    def status_arguments(self):
        match = re.search(r'val\s+sourceDirty\s*=\s*gitValue\(([^)]*)\)\s*\.isNotEmpty\(\)', self.source)
        self.assertIsNotNone(match, "Review a changed sourceDirty contract before distributing an APK")
        arguments = [json.loads(value) for value in re.findall(r'"(?:\\.|[^"\\])*"', match[1])]
        self.assertTrue(arguments and arguments[0] == "status", "Identity must inspect the real Git worktree")
        return arguments

    def git_run(self, folder, *args):
        result = subprocess.run([self.git, "-c", "core.hooksPath=" + str(folder / "no-hooks"),
                               "-c", "commit.gpgsign=false", "-c", "user.name=Build safety test",
                               "-c", "user.email=build-safety@example.invalid", *args],
                              cwd=folder, check=False, text=True, encoding="utf-8", capture_output=True)
        if result.returncode:
            raise RuntimeError(f"Synthetic Git {args[0]} failed ({result.returncode}): {result.stderr.strip()}")
        return result.stdout

    def prepare_repository(self, folder):
        self.git_run(folder, "init", "--quiet")
        (folder / ".gitignore").write_text("fashion_app/build/\nbackend/.env\n", encoding="utf-8")
        existing = folder / "fashion_app/app/src/main/java/SyntheticExisting.kt"
        existing.parent.mkdir(parents=True)
        existing.write_text("class SyntheticExisting\n", encoding="utf-8")
        self.git_run(folder, "add", ".gitignore", "fashion_app/app/src/main/java/SyntheticExisting.kt")
        self.git_run(folder, "commit", "--quiet", "-m", "Synthetic fixture")
        self.assertEqual("", self.git_run(folder, *self.status_arguments()).strip())

    def test_preview_distribution_key_is_rejected_before_primary_package_signing(self):
        block = block_after(self.source, 'System.getenv("ANDROID_DISTRIBUTION_KEYSTORE")')
        signing = block.find("signingConfig")
        self.assertGreaterEqual(signing, 0)
        before_signing = block[:signing]
        self.assertRegex(before_signing, r'\b(?:require|check)\s*\(\s*previewBuild\s*\)',
                         "The explicit Preview-only keystore must be gated before assigning a signer; "
                         "publication checks cannot undo an incorrectly signed primary-package APK")

    def test_untracked_android_code_and_resources_make_source_identity_dirty(self):
        with tempfile.TemporaryDirectory(prefix="fashionai-identity-test-") as directory:
            folder = Path(directory)
            self.prepare_repository(folder)
            (folder / "fashion_app/app/src/main/java/SyntheticNew.kt").write_text("class SyntheticNew\n", encoding="utf-8")
            resource = folder / "fashion_app/app/src/main/res/raw/synthetic_new.txt"
            resource.parent.mkdir(parents=True)
            resource.write_text("synthetic APK resource\n", encoding="utf-8")
            self.assertNotEqual("", self.git_run(folder, *self.status_arguments()).strip(),
                                "Uncommitted Android source/resources would enter the APK without belonging to its recorded SHA")

    def test_ignored_build_outputs_and_private_environment_are_not_source_changes(self):
        with tempfile.TemporaryDirectory(prefix="fashionai-identity-test-") as directory:
            folder = Path(directory)
            self.prepare_repository(folder)
            output = folder / "fashion_app/build/synthetic-report.txt"
            output.parent.mkdir(parents=True)
            output.write_text("synthetic result\n", encoding="utf-8")
            env = folder / "backend/.env"
            env.parent.mkdir()
            env.write_text("SYNTHETIC_EMPTY_SETTING=\n", encoding="utf-8")
            self.assertEqual("", self.git_run(folder, *self.status_arguments()).strip(),
                             "Ignored local artifacts must remain excluded; do not enumerate secret contents")


if __name__ == "__main__":
    unittest.main()
