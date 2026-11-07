"""Offline, reviewed Parser bundle and narrow adapters for the pinned upstream APIs.

No cache discovery, downloads, license acceptance, or model import at module import.
A reviewed manifest is operator evidence, not independent legal authorization.
"""
import hashlib
import importlib.metadata
import inspect
import json
from pathlib import Path
import re


MODEL_COMMIT = '7c0f10af3f91ad4048fe9729c470a13ef905d25a'
PARSER_COMMIT = 'f2771f2fb8655349e87e2869bbde7ace0bd06f2c'
SEGFORMER_LICENSE_COMMIT = '65fa8cfa9b52b6ee7e8897a98705abf8570f9e32'
PARSER_REPO = 'fashn-ai/fashn-human-parser'
LICENSE_SOURCES = {
    'human-parser': 'https://github.com/fashn-AI/fashn-human-parser/blob/' + PARSER_COMMIT + '/LICENSE',
    'segformer': 'https://github.com/NVlabs/SegFormer/blob/' + SEGFORMER_LICENSE_COMMIT + '/LICENSE',
}
MODEL_FILES = {'human-parser/config.json', 'human-parser/model.safetensors'}
LICENSE_FILES = {'licenses/human-parser.txt', 'licenses/segformer.txt'}
SHA256 = re.compile(r'[a-f0-9]{64}\Z')
REVISION = re.compile(r'[a-f0-9]{40}\Z')


class ParserIntegrityError(ValueError):
    """Only stable public reason codes; no paths, tokens or raw provider content."""


def _json(path, limit):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > limit:
        raise ValueError()
    return json.loads(path.read_text(encoding='utf-8'))


def verify_parser_assets(weights_dir: Path) -> Path:
    """Verify a deliberately staged local bundle, never an ambient HF cache/ref."""
    try:
        root = weights_dir.resolve(strict=True)
        manifest_path = root / 'parser.manifest.json'
        manifest = _json(manifest_path, 64 * 1024)
        if (type(manifest.get('version')) is not int or manifest['version'] != 1 or manifest.get('model_repo') != PARSER_REPO or
                not isinstance(manifest.get('model_revision'), str) or
                not REVISION.fullmatch(manifest['model_revision'])):
            raise ValueError()
        files = manifest.get('files')
        if not isinstance(files, dict) or set(files) != MODEL_FILES | LICENSE_FILES:
            raise ValueError()
        review = manifest.get('license_review', {})
        if (review.get('reviewed') is not True or review.get('purpose') != 'noncommercial-research-evaluation' or
                review.get('sources') != LICENSE_SOURCES):
            raise ParserIntegrityError('parser_license_review_missing')
        # Do not allow an unlisted pickle, remote-code module, index, alternate weights,
        # or symlink to influence Transformers' choice of local loading inputs.
        bundle = root / 'human-parser'
        if not bundle.is_dir() or bundle.resolve() != bundle:
            raise ValueError()
        if {p.name for p in bundle.iterdir()} != {'config.json', 'model.safetensors'}:
            raise ValueError()
        for name, expected in files.items():
            path = root / name
            if (not isinstance(expected, str) or not SHA256.fullmatch(expected) or
                    not path.is_file() or path.resolve() != path or path.stat().st_size == 0):
                raise ValueError()
            if name in LICENSE_FILES and path.stat().st_size > 1024 * 1024:
                raise ValueError()
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                header = stream.read(80)
                if header.startswith((b'version https://git-lfs', b'<!DOCTYPE', b'<html')):
                    raise ValueError()
                digest.update(header)
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise ValueError()
        config = _json(bundle / 'config.json', 1024 * 1024)
        if (config.get('model_type') != 'segformer' or
                config.get('architectures') != ['SegformerForSemanticSegmentation'] or
                'auto_map' in config or 'quantization_config' in config):
            raise ValueError()
        return bundle
    except ParserIntegrityError:
        raise
    except (OSError, ValueError, KeyError, TypeError, AttributeError, RecursionError):
        raise ParserIntegrityError('parser_manifest_missing_or_mismatched') from None


def verify_runtime_revisions():
    """The adapter hook is private upstream API: accept only reviewed immutable code."""
    try:
        for package, repo, commit in (('fashn-vton', 'fashn-vton-1.5', MODEL_COMMIT),
                                      ('fashn-human-parser', 'fashn-human-parser', PARSER_COMMIT)):
            direct = importlib.metadata.distribution(package).read_text('direct_url.json')
            data = json.loads(direct) if direct else {}
            origin = data.get('url', '').lower().removesuffix('.git')
            if (data.get('vcs_info', {}).get('commit_id') != commit or
                    origin != 'https://github.com/fashn-ai/' + repo):
                raise ValueError()
    except (importlib.metadata.PackageNotFoundError, ValueError, TypeError, AttributeError, RecursionError):
        raise ParserIntegrityError('parser_runtime_revision_unverified') from None


def _pipeline_class(pipeline_type, parser_type, segformer_type, parser_dir):
    if (list(inspect.signature(pipeline_type._setup_hp_model).parameters) != ['self'] or
            list(inspect.signature(parser_type.__init__).parameters) != ['self', 'model_id', 'device']):
        raise ParserIntegrityError('parser_adapter_contract_mismatch')

    class VerifiedHumanParser(parser_type):
        def __init__(self, device):
            self.device = device
            self.model = segformer_type.from_pretrained(str(parser_dir), local_files_only=True,
                                                       use_safetensors=True, trust_remote_code=False)
            self.model.to(device)
            self.model.eval()

    class VerifiedPipeline(pipeline_type):
        def _setup_hp_model(self):
            device = 'cuda' if self.device.type == 'cuda' else 'cpu'
            self.hp_model = VerifiedHumanParser(device)

    return VerifiedPipeline


def load_verified_pipeline(*, weights_dir, device):
    from local_vton import require_offline_model_loading
    require_offline_model_loading()
    parser_dir = verify_parser_assets(Path(weights_dir))
    verify_runtime_revisions()
    from fashn_vton import TryOnPipeline
    from fashn_human_parser import FashnHumanParser
    from transformers import SegformerForSemanticSegmentation
    try:
        adapted = _pipeline_class(TryOnPipeline, FashnHumanParser,
                                  SegformerForSemanticSegmentation, parser_dir)
    except (AttributeError, TypeError, ValueError):
        raise ParserIntegrityError('parser_adapter_contract_mismatch') from None
    return adapted(weights_dir=str(weights_dir), device=device)


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Offline Parser asset and installed-code preflight; no generation')
    parser.add_argument('--weights-dir', type=Path, required=True)
    options = parser.parse_args()
    try:
        verify_parser_assets(options.weights_dir)
        verify_runtime_revisions()
    except ParserIntegrityError as error:
        print(json.dumps({'ready': False, 'reason': str(error), 'generated': False}))
        return 2
    print(json.dumps({'assets_and_revisions_verified': True, 'model_loaded': False,
                      'license_legally_authorized': 'operator_review_required', 'generated': False}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
