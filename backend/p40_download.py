"""Explicit P40 official, pinned research assets. No parser, credentials, uploads or inference.
Run only under the user's P40 download authorization. Existing files are never overwritten.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import time
import urllib.parse
import urllib.request
import urllib.error

CODE_REV = '7c0f10af3f91ad4048fe9729c470a13ef905d25a'
ASSETS = [
    ('code/fashn-vton-1.5.zip', f'https://codeload.github.com/fashn-AI/fashn-vton-1.5/zip/{CODE_REV}', None, 5_000_000, 'Apache-2.0; upstream archive pinned by commit'),
    ('weights/model.safetensors', 'https://huggingface.co/fashn-ai/fashn-vton-1.5/resolve/7720683168567eb5a2a4c67f15116c6e29c83ded/model.safetensors', 'd6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a', 1_943_668_048, 'Apache-2.0'),
    ('weights/dwpose/yolox_l.onnx', 'https://huggingface.co/fashn-ai/DWPose/resolve/548b5df25b84d9f4aac0611dfa1c2a7a12f15571/yolox_l.onnx', '7860ae79de6c89a3c1eb72ae9a2756c0ccfbe04b7791bb5880afabd97855a411', 216_746_733, 'Apache-2.0; upstream IDEA-Research attribution required'),
    ('weights/dwpose/dw-ll_ucoco_384.onnx', 'https://huggingface.co/fashn-ai/DWPose/resolve/548b5df25b84d9f4aac0611dfa1c2a7a12f15571/dw-ll_ucoco_384.onnx', '724f4ff2439ed61afb86fb8a1951ec39c6220682803b4a8bd4f598cd913b1843', 134_399_116, 'Apache-2.0; upstream IDEA-Research attribution required'),
    ('licenses/fashn-LICENSE.txt', f'https://raw.githubusercontent.com/fashn-AI/fashn-vton-1.5/{CODE_REV}/LICENSE', None, 100_000, 'Apache-2.0'),
    ('licenses/fashn-model-card.md', 'https://huggingface.co/fashn-ai/fashn-vton-1.5/raw/7720683168567eb5a2a4c67f15116c6e29c83ded/README.md', None, 100_000, 'Model card; license metadata'),
    ('licenses/dwpose-model-card.md', 'https://huggingface.co/fashn-ai/DWPose/raw/548b5df25b84d9f4aac0611dfa1c2a7a12f15571/README.md', None, 100_000, 'Model card; attribution and license metadata'),
]
RESERVE = 4 * 1024 ** 3
MAX_DOWNLOAD_SECONDS = 900  # Checked between reads; each socket operation also has a 60s timeout.

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

class OfficialRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlparse(newurl)
        host = parsed.hostname or ''
        if parsed.scheme != 'https' or not any(host == h or host.endswith('.'+h) for h in
                ('huggingface.co', 'hf.co', 'xethub.hf.co', 'github.com', 'githubusercontent.com')):
            raise ValueError('Non-official redirect refused')
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def fetch(root, asset):
    name, url, expected, maximum, license_name = asset
    dest = root / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        actual = digest(dest)
        if expected is None or actual != expected or dest.stat().st_size != maximum:
            raise ValueError('Existing file needs explicit review; not overwritten: ' + name)
        return dict(file=name, source=url, bytes=dest.stat().st_size, sha256=actual,
                    expected_sha256=expected, license=license_name, reused=True)
    part = dest.with_name(dest.name + '.part')
    offset = part.stat().st_size if part.exists() else 0
    if offset > maximum:
        raise ValueError('Partial file exceeds declared limit')
    if shutil.disk_usage(root).free < maximum - offset + RESERVE:
        raise OSError('Insufficient disk reserve; no download started')
    headers = {'User-Agent':'FashionAI-P40-local-research/1.0'}
    if offset:
        headers['Range'] = f'bytes={offset}-'
    opener = urllib.request.build_opener(OfficialRedirects())
    start = time.monotonic()
    last = start
    with opener.open(urllib.request.Request(url, headers=headers), timeout=60) as response:
        if offset and (response.status != 206 or not response.headers.get('Content-Range','').startswith(f'bytes {offset}-')):
            raise ValueError('Server did not honor resume; partial preserved')
        with part.open('ab' if offset else 'xb') as output:
            count = offset
            while chunk := response.read(1024 * 1024):
                if time.monotonic() - start > MAX_DOWNLOAD_SECONDS:
                    raise TimeoutError('Download wall limit reached; partial preserved')
                count += len(chunk)
                if count > maximum:
                    raise ValueError('Download exceeded declared size')
                if shutil.disk_usage(root).free < RESERVE:
                    raise OSError('Disk reserve reached; partial preserved')
                output.write(chunk)
                if time.monotonic() - last > 20:
                    print(json.dumps({'file':name,'bytes':count,'seconds':round(time.monotonic()-start,1)}),flush=True)
                    last = time.monotonic()
    actual = digest(part)
    if expected and (actual != expected or part.stat().st_size != maximum):
        raise ValueError('Pinned size/checksum mismatch; partial preserved')
    # Windows rename fails if destination appeared meanwhile; never replace another file.
    part.rename(dest)
    return dict(file=name, source=url, bytes=dest.stat().st_size, sha256=actual,
                expected_sha256=expected, license=license_name, seconds=round(time.monotonic()-start,3))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--only', choices=['small','weights','all'], default='all')
    args = parser.parse_args()
    root = args.root.resolve(strict=True)
    if (root / '.git').exists():
        raise ValueError('Use a separate experiment directory, not a Git root')
    log = root / ('downloads-' + time.strftime('%Y%m%d-%H%M%S') + '.jsonl')
    selected = [a for a in ASSETS if args.only == 'all' or (a[2] is not None) == (args.only == 'weights')]
    with log.open('x', encoding='utf-8') as evidence:
        for asset in selected:
            try:
                record = fetch(root, asset)
                record['status'] = 'verified' if asset[2] else 'downloaded-hash-recorded'
            except Exception as error:
                # Do not disclose signed redirect URLs, proxy credentials, or response content.
                record = {'file':asset[0], 'source':asset[1], 'status':'failed',
                          'error_type':type(error).__name__, 'message':str(error) if isinstance(error,(ValueError,OSError)) and not isinstance(error,urllib.error.URLError) else 'network_or_http_error'}
                evidence.write(json.dumps(record)+'\n'); evidence.flush()
                print(json.dumps(record),flush=True)
                return 2
            evidence.write(json.dumps(record)+'\n'); evidence.flush()
            print(json.dumps(record),flush=True)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
