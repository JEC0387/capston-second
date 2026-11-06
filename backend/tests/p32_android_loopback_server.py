"""TEST HARNESS ONLY: real loopback HTTP with a synthetic engine, never AI inference.

No production route or readiness check is changed. The fixture-only license flag
does not accept any model license: no model, parser, weights or torch are loaded.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys

from PIL import Image, ImageDraw


def main():
    if os.environ.get('P32_SYNTHETIC_TEST_ONLY') != 'approved-local-test-fixture':
        raise RuntimeError('Synthetic test harness opt-in required')
    port, root = int(sys.argv[1]), Path(sys.argv[2]).resolve()
    if not 1024 <= port <= 65535 or not root.is_dir() or not (root / 'SYNTHETIC_TEST_ONLY').is_file():
        raise RuntimeError('Test-owned directory and loopback port required')
    digest = os.environ.get('P32_TEST_TOKEN_SHA256', '')
    if not re.fullmatch('[a-f0-9]{64}', digest):
        raise RuntimeError('Test-only credential required')
    # Existing production service remains untouched; injection is confined to this tests/ module.
    from local_vton_server import create_app, LocalSettings
    import uvicorn

    class SyntheticEngine:
        reason = None
        calls = 0

        async def generate(self, person, garment, options):
            self.calls += 1
            # Persist ONLY a fixture counter, no tokens/images/job fingerprints.
            (root / 'synthetic-calls.json').write_text(json.dumps({'calls': self.calls}))
            color = tuple(hashlib.sha256(garment.tobytes()).digest()[:3])
            image = Image.new('RGB', (256, 384), color)
            draw = ImageDraw.Draw(image)
            draw.line((0, 0, 255, 383), fill='white', width=12)
            draw.line((255, 0, 0, 383), fill='black', width=12)
            draw.rectangle((8, 170, 247, 206), fill='white')
            draw.text((14, 180), 'SYNTHETIC TEST ONLY - NOT AI', fill='black')
            output = io.BytesIO()
            image.save(output, 'PNG')
            return output.getvalue(), {'width': 256, 'height': 384}

    settings = LocalSettings(enabled=True, research_license=True, allow_loopback_http=True,
        token_hashes=(digest,), journal=str(root / 'jobs.json'), result_ttl=300, timeout=10)
    app = create_app(settings, SyntheticEngine())
    uvicorn.run(app, host='127.0.0.1', port=port, access_log=False, log_level='critical')


if __name__ == '__main__':
    main()
