"""Aggregate-only inspection. Never return raw preferences, identifiers or image data.

SQLite format: https://sqlite.org/fileformat2.html#wal_file_format and walformat.html.
Callers must obtain a stable read-only snapshot; this module never opens a device DB.
"""
import datetime as dt
from contextlib import closing
import json
import math
import sqlite3
import struct
import xml.etree.ElementTree as ET


class StructureError(ValueError):
    pass


def require(value):
    if not value:
        raise StructureError('structure_invalid')


def preferences(raw):
    require(len(raw) <= 4 * 1024 * 1024)
    require(b'<!DOCTYPE' not in raw.upper() and b'<!ENTITY' not in raw.upper())
    try:
        root = ET.fromstring(raw)
        require(root.tag == 'map')
        result = {}
        for child in root:
            name = child.get('name')
            require(name is not None and name not in result)
            value = child.text or ''
            if child.tag in ('int', 'long'):
                value = int(child.attrib['value'])
            elif child.tag == 'float':
                value = float(child.attrib['value'])
                require(math.isfinite(value))
            elif child.tag == 'boolean':
                require(child.get('value') in ('true', 'false'))
                value = child.get('value') == 'true'
            else:
                require(child.tag == 'string')
            result[name] = (child.tag, value)
        return result
    except Exception:
        raise StructureError('preferences_invalid') from None


def json_list(prefs, key):
    try:
        if key not in prefs:
            return []
        require(prefs[key][0] == 'string')
        value = json.loads(prefs[key][1])
        require(isinstance(value, list) and all(isinstance(v, dict) for v in value))
        return value
    except Exception:
        raise StructureError('record_structure_invalid') from None


def weather(raw):
    p = preferences(raw)
    bad = unknown = days = 0
    for key, (kind, value) in p.items():
        if key == 'enabled':
            bad += kind != 'boolean'
        elif key in ('lat', 'lon'):
            try:
                number = float(value)
                bad += kind != 'string' or not math.isfinite(number) or abs(number) > (90 if key == 'lat' else 180)
            except (ValueError, TypeError):
                bad += 1
        elif key.startswith('daytemp_'):
            days += 1
            try:
                dt.date.fromisoformat(key[8:])
                bad += kind != 'int'
            except ValueError:
                bad += 1
        else:
            unknown += 1
    return dict(entryCount=len(p), rememberedDayCount=days, invalidFields=bad, unknownFields=unknown)


def wardrobe(raw):
    rows = json_list(preferences(raw), 'items')
    ids = [r.get('id') for r in rows]
    valid = all(isinstance(i, str) and bool(i) for i in ids)
    duplicates = len(ids) - len(set(ids)) if valid else None
    refs = [r[k] for r in rows for k in ('imageUri', 'originalUri', 'cutoutUri', 'garmentUri') if r.get(k)]
    return dict(recordCount=len(rows), invalidIds=not valid, duplicateIds=duplicates,
                nonStringReferences=sum(not isinstance(v, str) for v in refs))


def recommendations(raw, wardrobe_raw):
    rows = json_list(preferences(raw), 'entries')
    ids = {r.get('id') for r in json_list(preferences(wardrobe_raw), 'items') if isinstance(r.get('id'), str)}
    invalid = historical_refs = 0
    keys = []
    for row in rows:
        try:
            dt.date.fromisoformat(row['date'])
            require(isinstance(row['context'], str) and (row['context'] in ('home', 'today', 'forecast') or row['context'].startswith('situation:')))
            require(row.get('scope', 'OWNED') in ('OWNED', 'ALL', 'INTERESTED'))
            require(isinstance(row.get('rank', 0), int) and 0 <= row.get('rank', 0) < 5)
            require(isinstance(row['itemIds'], list) and 0 < len(row['itemIds']) <= 8)
            require(all(isinstance(i, str) and i for i in row['itemIds']))
            require(isinstance(row.get('shownAt', 0), int) and row.get('shownAt', 0) >= 0)
            historical_refs += sum(i not in ids for i in row['itemIds'])
            keys.append((row['date'], row['context'], tuple(sorted(row['itemIds']))))
        except Exception:
            invalid += 1
    return dict(recordCount=len(rows), invalidRecords=invalid, withinRetentionLimit=len(rows) <= 1000,
                duplicateDateContextItems=len(keys)-len(set(keys)), historicalReferencesOutsideCurrentWardrobe=historical_refs)


def checksum(data, endian, seed=(0, 0)):
    require(len(data) % 8 == 0)
    a, b = seed
    for x, y in struct.iter_unpack(endian+'II', data):
        a = (a + x + b) & 0xffffffff
        b = (b + y + a) & 0xffffffff
    return a, b


def workmanager(db, wal, shm):
    """Validate committed WAL prefix and deserialize a reconstructed copy in RAM only."""
    require(len(db) <= 32*1024*1024 and len(wal) <= 32*1024*1024)
    require(db[:16] == b'SQLite format 3\0' and len(wal) >= 32 and len(shm) >= 136)
    require(len(shm) % 32768 == 0 and shm[:48] == shm[48:96])
    magic, version, page = struct.unpack('>III', wal[:12])
    require(magic in (0x377f0682, 0x377f0683) and version == 3007000)
    require(512 <= page <= 65536 and page & (page-1) == 0)
    db_page = struct.unpack('>H', db[16:18])[0]
    require((65536 if db_page == 1 else db_page) == page and len(db) % page == 0)
    endian = '<' if magic == 0x377f0682 else '>'
    native = '<' if struct.unpack('<I', shm[:4])[0] == 3007000 else '>'
    require(struct.unpack(native+'I', shm[:4])[0] == 3007000 and shm[12] == 1)
    require(shm[13] == (magic & 1) and shm[32:40] == wal[16:24])
    require(checksum(shm[:40], native) == struct.unpack(native+'II', shm[40:48]))
    shm_page = struct.unpack(native+'H', shm[14:16])[0]
    require((65536 if shm_page == 1 else shm_page) == page)
    frames, pages = struct.unpack(native+'II', shm[16:24])
    require(0 < frames <= (len(wal)-32)//(page+24) and 0 < pages*page <= 32*1024*1024)
    require(struct.unpack(native+'I', shm[96:100])[0] <= frames)
    sums = checksum(wal[:24], endian)
    require(sums == struct.unpack('>II', wal[24:32]))
    image = bytearray(db[:pages*page])
    image.extend(bytes(max(0, pages*page-len(image))))
    commit_pages = 0
    for i in range(frames):
        offset = 32+i*(page+24)
        header = wal[offset:offset+24]
        content = wal[offset+24:offset+24+page]
        pg, commit_pages = struct.unpack('>II', header[:8])
        require(pg > 0 and header[8:16] == wal[16:24])
        sums = checksum(header[:8]+content, endian, sums)
        require(sums == struct.unpack('>II', header[16:24]))
        if pg <= pages:
            image[(pg-1)*page:pg*page] = content
    require(commit_pages == pages and sums == struct.unpack(native+'II', shm[24:32]))
    require(image[:16] == b'SQLite format 3\0')
    # Convert only the disposable RAM copy to rollback format; never touch device files.
    image[18:20] = b'\1\1'
    try:
        with closing(sqlite3.connect(':memory:')) as connection:
            connection.deserialize(bytes(image))
            connection.execute('PRAGMA query_only=ON')
            connection.execute('PRAGMA trusted_schema=OFF')
            okay = connection.execute('PRAGMA quick_check').fetchall() == [('ok',)]
            tables = {r[0] for r in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            require(okay)
            return dict(committedFrames=frames, pageSize=page, pageCount=pages, walChecksumsValid=True,
                        shmHeadersValid=True, memoryCopyQuickCheckPassed=True, workSpecTablePresent='WorkSpec' in tables,
                        trailingBytesNotInterpreted=len(wal)-(32+frames*(page+24)), deviceDatabaseOpened=False)
    except Exception:
        raise StructureError('database_structure_invalid') from None
