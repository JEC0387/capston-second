"""Opt-in, real worker/CUDA smoke test. Default CI skips; no mock output or downloads.

Only an operator with approved GPU, installed/pinned model, licenses and nonpersonal
inputs may enable this. Passing this is NOT Android E2E or image-quality acceptance.
"""
import asyncio
import hashlib
import importlib.metadata
import io
import json
import os
from pathlib import Path
import tempfile
import time

from PIL import Image
import pytest


PINNED_MODEL_COMMIT = '7c0f10af3f91ad4048fe9729c470a13ef905d25a'


@pytest.mark.skipif(os.getenv('LOCAL_VTON_REAL_TEST_APPROVED') != 'approved-nonpersonal-inputs',
                    reason='Real CUDA/model/input approval not provided; not inference success')
def test_real_pinned_worker_generates_new_png_offline(monkeypatch):
    # Importing the module also creates its default FastAPI app. Do not acquire
    # an operator's existing service journal/credentials during this isolated test.
    monkeypatch.setenv('LOCAL_VTON_ENABLED', 'false')
    monkeypatch.delenv('LOCAL_VTON_JOURNAL', raising=False)
    monkeypatch.delenv('LOCAL_VTON_TOKEN_HASHES', raising=False)
    from local_vton import read_input, require_offline_model_loading, verify_weight_manifest
    from local_vton_server import LocalSettings, SubprocessEngine

    assert os.getenv('LOCAL_VTON_RESEARCH_LICENSE_ACCEPTED') == 'true', 'Explicit dependency-license review required'
    keys = ('LOCAL_VTON_TEST_PERSON', 'LOCAL_VTON_TEST_GARMENT', 'LOCAL_VTON_WEIGHTS', 'LOCAL_VTON_TEST_OUTPUT_DIR')
    assert all(os.getenv(key) for key in keys), 'Approved input/weights/private-output paths are required'
    require_offline_model_loading()
    # A name/version alone is insufficient: require the installed VCS revision.
    distributions = importlib.metadata.packages_distributions().get('fashn_vton', [])
    assert len(distributions) == 1, 'Exactly one pinned model distribution required'
    direct = importlib.metadata.distribution(distributions[0]).read_text('direct_url.json')
    assert direct and json.loads(direct).get('vcs_info', {}).get('commit_id') == PINNED_MODEL_COMMIT, 'Install the reviewed immutable model commit'

    weights = Path(os.environ['LOCAL_VTON_WEIGHTS']).resolve()
    verify_weight_manifest(weights)
    output_root = Path(os.environ['LOCAL_VTON_TEST_OUTPUT_DIR']).resolve()
    repository = Path(__file__).resolve().parents[2]
    assert output_root.is_dir() and not output_root.is_relative_to(repository), 'Use an existing private directory outside Git'
    # New directory for each explicit invocation; never overwrite a prior result.
    private = Path(tempfile.mkdtemp(prefix='real-vton-check-', dir=output_root))
    settings = LocalSettings(enabled=True, weights=str(weights), journal=str(private / 'jobs.json'), device='cuda')
    engine = SubprocessEngine(settings)
    assert engine.reason is None, 'Actual model runtime is not ready: ' + str(engine.reason)
    import torch
    gpu_name = torch.cuda.get_device_name(0)
    person = read_input(Path(os.environ['LOCAL_VTON_TEST_PERSON']))
    garment = read_input(Path(os.environ['LOCAL_VTON_TEST_GARMENT']))
    started = time.perf_counter()
    # Uses the production subprocess, which imports TryOnPipeline itself. No factory injection.
    result, metrics = asyncio.run(asyncio.wait_for(engine.generate(person, garment,
        {'category': 'tops', 'garment_photo_type': 'flat-lay', 'num_timesteps': 30, 'seed': 42}), timeout=300))
    elapsed = time.perf_counter() - started
    with Image.open(io.BytesIO(result)) as image:
        assert image.format == 'PNG' and min(image.size) >= 32
        image.load()
        width, height = image.size
    assert metrics['width'] == width and metrics['height'] == height
    assert metrics['model_load_seconds'] >= 0 and metrics['inference_seconds'] > 0
    assert metrics['vram_peak_bytes'] > 0
    output = private / 'new-model-result.png'
    with output.open('xb') as stream:
        stream.write(result)
    receipt = {'engine': 'fashn-vton-1.5-local', 'model_revision': PINNED_MODEL_COMMIT,
        'model_inference_completed': True, 'android_e2e_verified': False, 'quality_review': 'pending',
        'gpu': gpu_name, 'wall_seconds': elapsed, 'metrics': metrics, 'seed': 42,
        'sha256': hashlib.sha256(result).hexdigest(), 'cost': 'not supplied by operator',
        'manifest_sha256': hashlib.sha256((weights / 'vton.manifest.json').read_bytes()).hexdigest()}
    with (private / 'evidence.json').open('x', encoding='utf-8') as stream:
        json.dump(receipt, stream, indent=2)
