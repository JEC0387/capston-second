"""Entirely synthetic files; never a real license review or model-ready record."""
import hashlib
import json
from local_vton_parser import LICENSE_SOURCES, MODEL_FILES, LICENSE_FILES, PARSER_REPO


def parser_fixture(weights):
    config = {'model_type': 'segformer', 'architectures': ['SegformerForSemanticSegmentation']}
    for name in MODEL_FILES | LICENSE_FILES:
        path = weights / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(json.dumps(config).encode() if name.endswith('config.json') else
                         b'synthetic test fixture, not weights or licensed terms')
    manifest = {'version': 1, 'model_repo': PARSER_REPO, 'model_revision': 'a' * 40,
                'files': {name: hashlib.sha256((weights/name).read_bytes()).hexdigest()
                          for name in MODEL_FILES | LICENSE_FILES},
                'license_review': {'reviewed': True, 'purpose': 'noncommercial-research-evaluation',
                                   'sources': LICENSE_SOURCES}}
    (weights/'parser.manifest.json').write_text(json.dumps(manifest))
    return manifest
