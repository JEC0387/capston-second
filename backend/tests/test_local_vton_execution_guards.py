"""Synthetic adapter/worker contracts, not actual AI inference evidence."""
import contextlib
import hashlib
import json
import os
from pathlib import Path
import sys
from types import SimpleNamespace

from PIL import Image
import pytest

from local_vton import LocalVtonError, run_inference


@pytest.fixture
def inputs(tmp_path):
    weights = tmp_path / 'weights'
    files = {}
    for name in ('model.safetensors', 'dwpose/yolox_l.onnx', 'dwpose/dw-ll_ucoco_384.onnx'):
        path = weights / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'synthetic contract fixture, not model weights')
        files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    (weights / 'vton.manifest.json').write_text(json.dumps({'files': files}))
    person, garment = tmp_path / 'person.png', tmp_path / 'garment.png'
    Image.new('RGB', (256, 384), 'white').save(person)
    Image.new('RGB', (256, 256), 'blue').save(garment)
    return dict(person=person, garment=garment, weights_dir=weights, output=tmp_path / 'result.png',
                category='tops', device='cpu', allow_cpu=True)


def pipeline(image=None):
    return lambda **_: SimpleNamespace(images=[image if image is not None else Image.new('RGB', (256, 384), 'red')])


def test_concurrent_output_is_not_overwritten_without_permission(inputs):
    original = b'concurrently created operator file'
    def factory(**_):
        inputs['output'].write_bytes(original)
        return pipeline()
    with pytest.raises(LocalVtonError, match='Output'):
        run_inference(**inputs, pipeline_factory=factory)
    assert inputs['output'].read_bytes() == original
    assert not list(inputs['output'].parent.glob('.vton-*'))


def test_no_clobber_filesystem_failure_does_not_fall_back_to_replacement(inputs, monkeypatch):
    def unsupported(*_):
        raise OSError('hard-link unavailable')
    monkeypatch.setattr('local_vton.os.link', unsupported)
    with pytest.raises(LocalVtonError, match='published safely'):
        run_inference(**inputs, pipeline_factory=lambda **_: pipeline())
    assert not inputs['output'].exists()
    assert not list(inputs['output'].parent.glob('.vton-*'))


def test_missing_human_parser_is_not_reported_as_ready(inputs, monkeypatch):
    from local_vton_server import LocalSettings, SubprocessEngine
    monkeypatch.setattr('local_vton_server.importlib.util.find_spec',
                        lambda name: None if name == 'fashn_human_parser' else object())
    engine = SubprocessEngine(LocalSettings(enabled=True, weights=str(inputs['weights_dir']), device='cpu', allow_cpu=True))
    assert engine.reason == 'dependencies_missing'


def test_cli_is_offline_before_pipeline_construction(inputs, monkeypatch):
    names = ('HF_HUB_OFFLINE', 'TRANSFORMERS_OFFLINE', 'HF_HUB_DISABLE_TELEMETRY')
    for name in names:
        monkeypatch.delenv(name, raising=False)
    seen = {}
    def factory(**_):
        seen.update({name: os.environ.get(name) for name in names})
        return pipeline()
    run_inference(**inputs, pipeline_factory=factory)
    assert seen == dict.fromkeys(names, '1')


def test_cli_requires_manifest_even_for_non_generating_preflight(inputs):
    (inputs['weights_dir'] / 'vton.manifest.json').unlink()
    with pytest.raises(LocalVtonError, match='manifest'):
        run_inference(**inputs, dry_run=True)
    assert not inputs['output'].exists()


def test_cli_generated_png_does_not_retain_model_metadata(inputs):
    generated = Image.new('RGB', (256, 384), 'red')
    exif = generated.getexif()
    exif[270] = 'synthetic private metadata must not be published'
    generated.info['exif'] = exif.tobytes()
    run_inference(**inputs, pipeline_factory=lambda **_: pipeline(generated))
    with Image.open(inputs['output']) as saved:
        assert not saved.getexif()
        assert 'exif' not in saved.info


def test_worker_rechecks_manifest_before_loading_model(inputs, monkeypatch):
    import local_vton_worker as worker
    folder = inputs['output'].parent
    (folder / 'request.json').write_text(json.dumps(dict(parent_pid=os.getpid(), device='cpu',
        weights_dir=str(inputs['weights_dir']), category='tops', garment_photo_type='flat-lay',
        num_timesteps=20, seed=42, memory_fraction=.8)))
    # The weights existed at server startup, but were altered before this worker.
    (inputs['weights_dir'] / 'model.safetensors').write_bytes(b'changed model data')
    loaded = []
    def factory(**_):
        loaded.append(True)
        return pipeline()
    monkeypatch.setattr(sys, 'argv', ['local_vton_worker.py', str(folder)])
    monkeypatch.setattr(worker.threading, 'Thread', lambda **_: SimpleNamespace(start=lambda: None))
    monkeypatch.setitem(sys.modules, 'torch', SimpleNamespace(inference_mode=contextlib.nullcontext))
    monkeypatch.setitem(sys.modules, 'fashn_vton', SimpleNamespace(TryOnPipeline=factory))
    with pytest.raises(LocalVtonError, match='mismatched'):
        worker.main()
    assert not loaded
    assert not inputs['output'].exists()
