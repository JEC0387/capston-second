"""MOCK worker stages; synthetic images and fake torch, never actual model inference."""
import contextlib
import json
import os
import sys
from types import SimpleNamespace

import pytest

from .test_local_vton_execution_guards import inputs


@pytest.mark.parametrize('stage', ['load', 'inference'])
@pytest.mark.parametrize('oom', [False, True])
def test_worker_stage_failures_are_distinct_and_sanitized(inputs, monkeypatch, stage, oom):
    import local_vton_worker as worker
    folder = inputs['output'].parent
    (folder / 'request.json').write_text(json.dumps(dict(parent_pid=os.getpid(), device='cpu',
        weights_dir=str(inputs['weights_dir']), category='tops', garment_photo_type='flat-lay',
        num_timesteps=20, seed=42, memory_fraction=.8)))
    error_type = type('OutOfMemoryError', (RuntimeError,), {}) if oom else RuntimeError
    def fail(**_):
        raise error_type('synthetic private path / token / provider detail')
    monkeypatch.setattr(sys, 'argv', ['local_vton_worker.py', str(folder)])
    monkeypatch.setattr(worker.threading, 'Thread', lambda **_: SimpleNamespace(start=lambda: None))
    monkeypatch.setitem(sys.modules, 'torch', SimpleNamespace(inference_mode=contextlib.nullcontext))
    monkeypatch.setattr(worker, 'verify_runtime_revisions', lambda: None)
    monkeypatch.setattr(worker, 'load_verified_pipeline', fail if stage == 'load' else lambda **_: fail)
    with pytest.raises(Exception) as caught:
        worker.main()
    expected = 'cuda_out_of_memory' if oom else 'model_' + stage + '_failed'
    assert worker.failure_code(caught.value) == expected
    assert 'private' not in worker.failure_code(caught.value)
    assert not inputs['output'].exists() and not (folder / 'metrics.json').exists()


@pytest.mark.parametrize('code', ['model_load_failed', 'model_inference_failed', 'cuda_out_of_memory'])
def test_server_preserves_only_sanitized_worker_stage_codes(code):
    from local_vton_server import EngineFailure
    assert EngineFailure(code).code == code
    assert EngineFailure('synthetic private error').code == 'model_failed'
