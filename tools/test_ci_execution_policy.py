"""Offline checks of CI cost controls; never start Actions or alter release artifacts."""
from pathlib import Path
import re
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/android-debug-apk.yml'
FULL = "github.event_name == 'pull_request' || (github.event_name == 'workflow_dispatch' && inputs.check_level == 'full')"
LIGHT = "github.event_name == 'workflow_dispatch' && inputs.check_level == 'light'"


class CiExecutionPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding='utf-8')
        cls.trigger = cls.text.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
        job_text = cls.text.split('\njobs:\n', 1)[1]
        matches = list(re.finditer(r'^  ([\w-]+):\s*$', job_text, re.M))
        cls.jobs = {m[1]: job_text[m.end():matches[i + 1].start() if i + 1 < len(matches) else len(job_text)]
                    for i, m in enumerate(matches)}

    def test_push_and_implicit_recurring_execution_are_disabled(self):
        events = re.findall(r'^  ([\w-]+):\s*$', self.trigger, re.M)
        self.assertEqual(events, ['pull_request', 'workflow_dispatch'])
        self.assertRegex(self.trigger, r'(?m)^        default: light$')
        self.assertRegex(self.trigger, r'(?m)^        type: choice$')
        self.assertIn('          - full\n', self.trigger)

    def test_pr_full_checks_and_job_ids_remain_intact(self):
        self.assertEqual(set(self.jobs), {'build', 'backend', 'server-start', 'development'})
        for name in ('build', 'backend', 'server-start'):
            self.assertIn('    if: ' + FULL + '\n', self.jobs[name])
        self.assertIn('needs: [backend, server-start]', self.jobs['build'])
        self.assertIn("python: ['3.11', '3.12', '3.13', '3.14']", self.jobs['server-start'])
        self.assertIn('os: [windows-latest]', self.jobs['server-start'])
        self.assertIn("- os: ubuntu-24.04\n            python: '3.12'", self.jobs['server-start'])

    def test_light_mode_cannot_publish_or_access_signing_secrets(self):
        job = self.jobs['development']
        self.assertIn('    if: ' + LIGHT + '\n', job)
        self.assertIn('timeout-minutes: 5', job)
        self.assertIn('test_apk_release test_native_alignment test_artifact_retention test_ci_execution_policy', job)
        for forbidden in ('secrets.', 'upload-artifact', 'gradlew', 'strategy:', 'pip install', 'apk_release.py prepare'):
            self.assertNotIn(forbidden, job)

    def test_light_check_cannot_cancel_pr_or_full_release(self):
        group = re.search(r'(?m)^  group: (.+)$', self.text)[1]
        self.assertIn('github.ref', group)
        self.assertIn("github.event_name == 'pull_request' && 'pr' || inputs.check_level", group)
        self.assertIn("  cancel-in-progress: ${{ github.event_name == 'pull_request' }}", self.text)
        self.assertIn('      group: fashionai-apk-distribution\n      cancel-in-progress: false', self.jobs['build'])


if __name__ == '__main__':
    unittest.main()
