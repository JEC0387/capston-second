"""Release workflow invariants; no network, artifacts, credentials, or device access."""
from pathlib import Path
import re
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/android-debug-apk.yml"


class ArtifactRetentionSafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.steps = {}
        matches = list(re.finditer(r"^      - name: (.+)$", cls.workflow, re.M))
        for index, match in enumerate(matches):
            end = matches[index + 1].start() if index + 1 < len(matches) else len(cls.workflow)
            cls.steps[match.group(1).strip()] = cls.workflow[match.start():end]

    def retention(self, name):
        match = re.search(r"^          retention-days: (\d+)\s*$", self.steps[name], re.M)
        self.assertIsNotNone(match, f"{name}: retention must be explicit, not the account default")
        return int(match.group(1))

    def test_preview_survives_presentation_while_ephemeral_outputs_expire_earlier(self):
        preview = self.retention("Upload APK")
        # Nov 4/5 builds must still exist on Nov 16; keep a full two-week minimum.
        self.assertGreaterEqual(preview, 14)
        self.assertLessEqual(preview, 90)
        for name in ("Upload opt-in Preview instrumentation harness", "Upload separate device QA app and tests"):
            days = self.retention(name)
            self.assertGreaterEqual(days, 1)
            self.assertLessEqual(days, 3)
            self.assertLess(days, preview)
        reports = self.retention("Upload test and lint reports")
        self.assertGreaterEqual(reports, 1)
        self.assertLessEqual(reports, 7)
        self.assertLess(reports, preview)
        self.assertGreaterEqual(self.retention("Upload validation metadata (contains no APK or signing key)"), preview)

    def test_installable_artifacts_require_success_and_original_distribution_gate(self):
        verification = "Verify Preview trusted signer and monotonic version before publishing"
        self.assertIn("--distribution --preview", self.steps[verification])
        self.assertIn("APK_SIGNING_CERT_SHA256", self.steps[verification])
        alignment = "Verify every packaged 64-bit ELF and APK ZIP alignment"
        self.assertIn("tools/native_alignment.py", self.steps[alignment])
        self.assertIn("needs: [backend, server-start]", self.workflow)
        for name in ("Upload APK", "Upload opt-in Preview instrumentation harness", "Upload separate device QA app and tests"):
            step = self.steps[name]
            self.assertIn("if: success() && steps.distribution.outputs.ready == 'true'", step)
            self.assertIn("if-no-files-found: error", step)
            self.assertNotIn("continue-on-error:", step)
            self.assertLess(self.workflow.index("- name: " + verification), self.workflow.index("- name: " + name))
            self.assertLess(self.workflow.index("- name: " + alignment), self.workflow.index("- name: " + name))
        self.assertIn("--preview --device-qa", self.steps["Verify QA package and trusted signer (never a Preview update)"])
        self.assertIn("if: always()", self.steps["Remove temporary signing key"])

    def test_policy_does_not_delete_existing_artifacts_or_hide_failed_reports(self):
        self.assertNotRegex(self.workflow, r"(?i)gh\s+(?:run\s+delete|api[^\n]*--method\s+DELETE)")
        self.assertNotRegex(self.workflow, r"(?i)delete-artifact|delete-workflow-runs|actions/artifacts/\S+[^\n]*DELETE")
        self.assertIn("if: always()", self.steps["Upload test and lint reports"])
        preview = self.steps["Build isolated Preview and test isolation, sample photos and body entry"]
        for preserved in ("P16", "P19", "P22", "P23"):
            self.assertIn(f"--tests '*{preserved}*'", preview)


if __name__ == "__main__":
    unittest.main()
