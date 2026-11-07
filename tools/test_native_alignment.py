import io
import unittest
import warnings
import zipfile
from native_alignment import load_segments, native_members


class NativeAlignmentTest(unittest.TestCase):
    def test_llvm_wide_flags_do_not_hide_unaligned_load(self):
        rows = """  LOAD 0x000000 0x0000000000000000 0x0000000000000000 0x001000 0x001000 R E 0x4000
  LOAD 0x001000 0x0000000000005000 0x0000000000005000 0x001000 0x002000 RW 0x1000"""
        self.assertEqual([x["aligned16Kb"] for x in load_segments(rows)], [True, False])

    def test_larger_power_of_two_alignment_and_congruence(self):
        self.assertTrue(load_segments("LOAD 0x1000 0x11000 0x11000 0x1000 0x1000 R 0x10000")[0]["aligned16Kb"])
        self.assertFalse(load_segments("LOAD 0x1000 0x12000 0x12000 0x1000 0x1000 R 0x4000")[0]["aligned16Kb"])
        self.assertFalse(load_segments("LOAD 0x1000 0x11000 0x11000 0x1000 0x1000 R 0x5000")[0]["aligned16Kb"])

    def test_empty_or_malformed_output_cannot_pass(self):
        for text in ("", "readelf: invalid file", "LOAD 0x0", "LOAD nope 0 0 0 0 R 0x4000"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                load_segments(text)

    def test_both_64_bit_abis_are_checked_and_32_bit_inventory_retained(self):
        data = io.BytesIO()
        names = ["lib/arm64-v8a/a.so", "lib/x86_64/b.so", "lib/armeabi-v7a/c.so", "assets/not-native.txt"]
        with zipfile.ZipFile(data, "w") as archive:
            for name in names:
                archive.writestr(name, b"example")
        with zipfile.ZipFile(data) as archive:
            inventory, selected = native_members(archive)
        self.assertEqual(selected, names[:2])
        self.assertIn(names[2], inventory)

    def test_missing_64_bit_or_duplicate_entry_is_not_a_pass(self):
        for names in (["lib/armeabi-v7a/a.so"], ["lib/arm64-v8a/a.so"] * 2):
            data = io.BytesIO()
            with warnings.catch_warnings(), zipfile.ZipFile(data, "w") as archive:
                warnings.simplefilter("ignore", UserWarning)
                for name in names:
                    archive.writestr(name, b"example")
            with zipfile.ZipFile(data) as archive, self.assertRaises(ValueError):
                native_members(archive)


if __name__ == "__main__":
    unittest.main()
