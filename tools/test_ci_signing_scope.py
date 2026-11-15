"""Offline signing-scope checks; do not load keys, invoke Gradle or start Actions.

The workflow uses regular two-space jobs and six-space steps. Inspect those
scopes directly so this safety check needs no YAML dependency or network setup.
"""
from pathlib import Path
import re
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/android-debug-apk.yml"
SIGNING_ENV = {
    "ANDROID_DISTRIBUTION_KEYSTORE", "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_PASSWORD", "ANDROID_KEY_ALIAS",
}


def scopes(text, pattern):
    matches = list(re.finditer(pattern, text, re.M))
    return [(match[1], text[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)])
            for i, match in enumerate(matches)]


def gradle_commands(step):
    # A continued shell command is one invocation; comments do not count.
    joined = re.sub(r"\\\r?\n\s*", " ", step)
    return [line.strip() for line in joined.splitlines()
            if not line.lstrip().startswith("#") and re.search(r"\b(?:bash\s+)?(?:\./)?gradlew\b", line)]


class CiSigningScopeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = WORKFLOW.read_text(encoding="utf-8")
        cls.jobs = dict(scopes(source.split("\njobs:\n", 1)[1], r"^  ([\w-]+):\s*$"))
        cls.steps = scopes(cls.jobs["build"], r"^      - (?:name|uses): ([^\n]+)$")

    def test_primary_gradle_commands_do_not_inherit_preview_signing_material(self):
        active = set()
        primary_count = 0
        preview_count = 0
        for name, step in self.steps:
            exported = {variable for variable in SIGNING_ENV
                        if re.search(r'echo\s+["\']?' + variable + r'=.*\$GITHUB_ENV', step)}
            for command in gradle_commands(step):
                if "-PfashionPreview=true" in command:
                    preview_count += 1
                else:
                    primary_count += 1
                    self.assertFalse(active | exported,
                                     f"Primary Gradle step {name!r} inherits Preview signing variables: "
                                     f"{sorted(active | exported)}")
            active.update(exported)
        self.assertGreater(primary_count, 0, "The primary regression/build path must remain")
        self.assertGreater(preview_count, 0, "The isolated Preview/QA path must remain")

    def test_signer_is_loaded_only_after_primary_checks_and_before_preview_build(self):
        restore = [i for i, (_, step) in enumerate(self.steps)
                   if "KEYSTORE_BASE64:" in step and "base64 --decode" in step]
        passwords = [i for i, (_, step) in enumerate(self.steps)
                     if re.search(r'echo\s+["\']?ANDROID_KEYSTORE_PASSWORD=.*\$GITHUB_ENV', step)]
        self.assertEqual(len(restore), 1, "Restore exactly one explicitly managed Preview signer")
        self.assertEqual(len(passwords), 1, "Export its passwords exactly once")
        primary = [i for i, (_, step) in enumerate(self.steps)
                   if any("-PfashionPreview=true" not in command for command in gradle_commands(step))]
        previews = [i for i, (_, step) in enumerate(self.steps)
                    if any("-PfashionPreview=true" in command for command in gradle_commands(step))]
        self.assertTrue(primary and previews)
        self.assertGreater(restore[0], max(primary), "Primary compile/tests/lint must precede key access")
        self.assertLess(restore[0], passwords[0])
        self.assertLess(passwords[0], min(previews), "Preview must still receive the trusted signer")
        for index in (*restore, *passwords):
            self.assertIn("        if: github.event_name != 'pull_request' && !inputs.validation_only\n", self.steps[index][1])

    def test_other_jobs_are_key_free_and_key_cleanup_remains_unconditional(self):
        for name, job in self.jobs.items():
            if name != "build":
                for variable in SIGNING_ENV | {"ANDROID_DEBUG_KEYSTORE_BASE64"}:
                    self.assertNotIn(variable, job, f"{name} must not receive APK signing material")
        cleanup = [(name, step) for name, step in self.steps
                   if 'rm -f -- "$RUNNER_TEMP/fashionai-signing/debug.keystore"' in step]
        self.assertEqual(len(cleanup), 1)
        self.assertIn("        if: always()\n", cleanup[0][1])


if __name__ == "__main__":
    unittest.main()
