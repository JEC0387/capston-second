import unittest
from apk_release import reject_downgrade, version_code, verify
from check_apk_update import compatibility


class ReleaseSafetyTest(unittest.TestCase):
    def test_device_qa_cannot_be_published_as_a_preview_update(self):
        # Fail before accessing any APK/key/network when the mode combination is unsafe.
        with self.assertRaisesRegex(ValueError, "separate isolated test app"):
            verify(None, None, distribution=True, preview=True, device_qa=True)
        with self.assertRaisesRegex(ValueError, "separate isolated test app"):
            verify(None, None, preview=False, device_qa=True)

    def test_versions_increase_and_old_reruns_are_rejected(self):
        self.assertGreater(version_code(1, 1), 3)
        self.assertGreater(version_code(2, 1), version_code(1, 99))
        self.assertGreater(version_code(2, 2), version_code(2, 1))
        with self.assertRaises(ValueError):
            reject_downgrade(version_code(1, 2), version_code(2, 1))
        with self.assertRaises(ValueError):
            reject_downgrade(3, 3)

    def test_updates_never_bypass_signer_or_version(self):
        old = dict(packageName="com.jeiu.fashionproject", certificateSha256="old", versionCode=3)
        new = dict(old, versionCode=4)
        self.assertTrue(compatibility(old, new).startswith("COMPATIBLE"))
        for key, value in [("certificateSha256", "new"), ("packageName", "another.app"), ("versionCode", 2), ("versionCode", 3)]:
            self.assertTrue(compatibility(old, dict(new, **{key: value})).startswith("BLOCKED"))

    def test_attempt_ranges_are_bounded(self):
        for run, attempt in [(0, 1), (1, 0), (1, 100), (21_000_000, 1)]:
            with self.assertRaises(ValueError):
                version_code(run, attempt)


if __name__ == "__main__":
    unittest.main()
