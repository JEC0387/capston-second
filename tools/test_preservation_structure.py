import json
from contextlib import closing
import pathlib
import sqlite3
import tempfile
import unittest
from xml.sax.saxutils import escape

import preservation_structure as audit


def pref(key, value):
    return ('<map><string name="' + key + '">' + escape(json.dumps(value)) + '</string></map>').encode()


class AggregateInspectionTest(unittest.TestCase):
    def test_private_values_never_returned(self):
        wardrobe = pref('items', [dict(id='PRIVATE-ID', imageUri='file:///PRIVATE-PHOTO')])
        rows = [dict(date='2026-11-05', context='home', scope='OWNED', rank=0,
                     itemIds=['PRIVATE-ID'], shownAt=123)]
        report = audit.recommendations(pref('entries', rows), wardrobe)
        self.assertEqual(report['invalidRecords'], 0)
        self.assertEqual(report['historicalReferencesOutsideCurrentWardrobe'], 0)
        self.assertNotIn('PRIVATE', json.dumps([report, audit.wardrobe(wardrobe)]))

    def test_corrupt_preferences_have_redacted_error(self):
        for raw in (b'<map>PRIVATE-TOKEN', b'<!DOCTYPE map [<!ENTITY secret "PRIVATE">]><map/>',
                    b'<map><float name="secret" value="nan"/></map>'):
            with self.assertRaisesRegex(audit.StructureError, '^preferences_invalid$|^structure_invalid$'):
                audit.weather(raw)

    def test_weather_structure_without_coordinates(self):
        report = audit.weather(b'<map><string name="lat">37.12345</string><string name="lon">127.12345</string><int name="daytemp_2026-11-05" value="17"/><boolean name="enabled" value="false"/></map>')
        self.assertEqual(report, dict(entryCount=4, rememberedDayCount=1, invalidFields=0, unknownFields=0))
        self.assertNotIn('37.12345', json.dumps(report))

    def test_recommendation_bad_records_and_deleted_item_not_conflated(self):
        rows = [dict(date='2026-11-05', context='today', scope='OWNED', rank=0, itemIds=['old'], shownAt=1),
                dict(date='bad', context='home', rank=9, itemIds=[])]
        result = audit.recommendations(pref('entries', rows), pref('items', []))
        self.assertEqual(result['invalidRecords'], 1)
        self.assertEqual(result['historicalReferencesOutsideCurrentWardrobe'], 1)

    def test_real_sqlite_wal_copy_and_corruption(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'synthetic.db'
            with closing(sqlite3.connect(path)) as connection:
                connection.execute('PRAGMA journal_mode=WAL')
                connection.execute('PRAGMA wal_autocheckpoint=0')
                connection.execute('CREATE TABLE WorkSpec (id TEXT PRIMARY KEY, state INTEGER)')
                connection.commit()
                connection.execute("INSERT INTO WorkSpec VALUES ('SYNTHETIC', 0)")
                connection.commit()
                db, wal, shm = [p.read_bytes() for p in (path, pathlib.Path(str(path)+'-wal'), pathlib.Path(str(path)+'-shm'))]
                before = [db, wal, shm]
                result = audit.workmanager(db, wal, shm)
                self.assertTrue(result['memoryCopyQuickCheckPassed'])
                self.assertTrue(result['workSpecTablePresent'])
                self.assertFalse(result['deviceDatabaseOpened'])
                self.assertEqual(before, [p.read_bytes() for p in (path, pathlib.Path(str(path)+'-wal'), pathlib.Path(str(path)+'-shm'))])
                for broken in (wal[:40], wal[:100] + bytes([wal[100] ^ 1]) + wal[101:]):
                    with self.assertRaises(audit.StructureError):
                        audit.workmanager(db, broken, shm)
                with self.assertRaises(audit.StructureError):
                    audit.workmanager(db, wal, shm[:48] + bytes(48) + shm[96:])
                # Bytes beyond the authoritative committed prefix are not current records.
                self.assertEqual(audit.workmanager(db, wal+b'old tail', shm)['trailingBytesNotInterpreted'], 8)


if __name__ == '__main__':
    unittest.main()
