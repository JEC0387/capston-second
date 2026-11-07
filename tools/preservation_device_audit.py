"""Explicit, read-only Preview audit; raw contents and device IDs stay in memory.

No UI, install, DB connection, photo reads, writes, or network requests on the device.
The output directory must be ignored by Git. Baselines are hashed-path manifests.
"""
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import shlex
import struct
import subprocess
from urllib.parse import unquote, urlparse

import preservation_structure as structure

PACKAGE = 'com.jeiu.fashionproject.preview'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def compare(before, after):
    common = before.keys() & after.keys()
    return dict(same=sum(before[k] == after[k] for k in common),
                changed=sum(before[k] != after[k] for k in common),
                missing=len(before.keys()-after.keys()), new=len(after.keys()-before.keys()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--baseline', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise RuntimeError('new_output_directory_required')
    if subprocess.run(['git', 'check-ignore', '-q', str(args.output)], capture_output=True).returncode:
        raise RuntimeError('output_must_be_git_ignored')

    def run(*command):
        result = subprocess.run(command, capture_output=True, timeout=40)
        if result.returncode:
            raise RuntimeError('read_failed_no_raw_output')
        return result.stdout

    devices = []
    for line in run(args.adb, 'devices').decode().splitlines()[1:]:
        fields = line.split()
        if len(fields) == 2 and fields[1] == 'device' and not fields[0].startswith('emulator-'):
            if run(args.adb, '-s', fields[0], 'shell', 'getprop', 'ro.product.model').strip() == b'SM-S936N':
                devices.append(fields[0])
    if len(devices) != 1:
        raise RuntimeError('authorized_single_S25_unavailable')

    def adb(*command):
        return run(args.adb, '-s', devices[0], *command)

    def app(command):
        return adb('exec-out', 'run-as', PACKAGE, *shlex.split(command))

    def read(path):
        # Only callers with explicit static metadata paths invoke this. Never read photo bytes.
        return app('cat '+shlex.quote(path))

    def hashes():
        paths = app('find shared_prefs files no_backup databases -type f').decode().splitlines()
        result = {}
        for path in paths:
            if not path.startswith(('shared_prefs/', 'files/', 'no_backup/', 'databases/')) or '\n' in path:
                raise RuntimeError('unexpected_inventory_path')
            value = app('sha256sum '+shlex.quote(path)).decode().split()[0]
            if not re.fullmatch('[a-f0-9]{64}', value):
                raise RuntimeError('hash_invalid')
            result[digest(path.encode())] = value
        return result

    args.output.mkdir(parents=True)
    before = hashes()
    report = dict(atUtc=dt.datetime.now(dt.timezone.utc).isoformat(), readOnly=True,
                  rawDataPersisted=False, deviceIdentifiersPersisted=False, atomicSnapshot=False)
    package = adb('shell', 'dumpsys', 'package', PACKAGE).decode(errors='replace')
    version = re.search(r'versionCode=(\d+)', package)
    report['previewVersionCode'] = int(version[1]) if version else None
    qa = adb('shell', 'dumpsys', 'package', PACKAGE+'.qa').decode(errors='replace')
    version = re.search(r'versionCode=(\d+)', qa)
    report['qaVersionCode'] = int(version[1]) if version else None
    body = {}
    for name, operation in (
        ('weather', lambda: structure.weather(read('shared_prefs/weather_alert.xml'))),
        ('wardrobe', lambda: structure.wardrobe(read('shared_prefs/wardrobe_store.xml'))),
        ('recommendations', lambda: structure.recommendations(read('shared_prefs/recommendation_log.xml'), read('shared_prefs/wardrobe_store.xml'))),
        ('workmanager', lambda: structure.workmanager(read('no_backup/androidx.work.workdb'), read('no_backup/androidx.work.workdb-wal'), read('no_backup/androidx.work.workdb-shm'))),
    ):
        try:
            body[name] = dict(status='INSPECTED', aggregate=operation())
        except (RuntimeError, structure.StructureError):
            body[name] = dict(status='BLOCKED', reason='unavailable_or_inconsistent_structure')
    profile = read('files/profileInstalled')
    body['profileInstalled'] = dict(sizeBytes=len(profile), expected24ByteFormat=len(profile) == 24)
    if len(profile) == 24:
        schema, result, update, size = struct.unpack('>iiqq', profile)
        body['profileInstalled'].update(schemaVersion=schema, resultCode=result,
                                        nonnegativeTimestampAndSize=update >= 0 and size >= 0)
    # Only existence of references inside this app's files directory is inspected.
    rows = structure.json_list(structure.preferences(read('shared_prefs/wardrobe_store.xml')), 'items')
    refs = {r[k] for r in rows for k in ('imageUri', 'originalUri', 'cutoutUri', 'garmentUri') if isinstance(r.get(k), str) and r[k]}
    ref_result = dict(internalPresent=0, internalMissing=0, bundledResourceUnverified=0, otherUnverified=0)
    roots = ['/data/user/0/'+PACKAGE+'/files/', '/data/data/'+PACKAGE+'/files/']
    for ref in refs:
        uri = urlparse(ref)
        path = unquote(uri.path)
        if uri.scheme == 'file' and any(path.startswith(root) for root in roots) and '..' not in path.split('/'):
            relative = 'files/'+next(path[len(root):] for root in roots if path.startswith(root))
            exists = subprocess.run([args.adb, '-s', devices[0], 'exec-out', 'run-as', PACKAGE,
                                     'test', '-f', relative], capture_output=True, timeout=20).returncode == 0
            ref_result['internalPresent' if exists else 'internalMissing'] += 1
        elif uri.scheme == 'android.resource':
            ref_result['bundledResourceUnverified'] += 1
        else:
            ref_result['otherUnverified'] += 1
    body['wardrobeReferences'] = ref_result
    after = hashes()
    report['structure'] = body
    report['duringAudit'] = compare(before, after)
    report['stableReadWindow'] = before == after
    baselines = {}
    for phase in ('preinstall-immediate', 'postinstall-before-ui', 'post-ui-final'):
        old = json.loads((args.baseline/(phase+'-hashes.json')).read_text(encoding='utf-8'))['files']
        baselines[phase] = compare(old, after)
    report['P34Comparisons'] = baselines
    report['knownStores'] = {}
    old = json.loads((args.baseline/'preinstall-immediate-hashes.json').read_text(encoding='utf-8'))['files']
    for name in ('shared_prefs/wardrobe_store.xml', 'shared_prefs/manual_body_measurements.xml',
                 'shared_prefs/avatar_visual_profile_v1.xml', 'no_backup/official-demo-job.json'):
        key = digest(name.encode())
        report['knownStores'][name] = dict(present=key in after, baselinePresent=key in old,
                                         unchangedSincePreinstall=key in old and old.get(key) == after.get(key))
    report['limits'] = ['No pre-UI raw snapshot: exact historical field differences unknown.',
                        'Cache, external media/SAF, Keystore and server data are outside comparison.',
                        'In-memory local VTON result and external saved images not verified.',
                        'Repeated matching hashes reduce races; this is not a transactional snapshot.']
    for filename, value in (('AUDIT.json', report), ('before-hashes.json', {'files': before}), ('after-hashes.json', {'files': after})):
        with (args.output/filename).open('x', encoding='utf-8') as target:
            json.dump(value, target, ensure_ascii=False, indent=2)
    print(json.dumps(report, ensure_ascii=True))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # subprocess/parse exceptions may carry raw device output; never expose them.
        safe = {'new_output_directory_required', 'output_must_be_git_ignored', 'read_failed_no_raw_output',
                'authorized_single_S25_unavailable', 'unexpected_inventory_path', 'hash_invalid'}
        print(json.dumps({'status': 'BLOCKED', 'reason': str(error) if type(error) is RuntimeError and str(error) in safe else type(error).__name__}))
        raise SystemExit(1)
