"""Resource/evidence tests; no model, GPU, network, device or subprocess launched."""
import hashlib
import io
import json
import sys
from types import SimpleNamespace
from unittest.mock import Mock

import pytest

import p40_download as download
import p40_supervise as supervise


def test_existing_weight_cannot_be_overwritten_or_silently_reused(tmp_path, monkeypatch):
    monkeypatch.setattr(download.urllib.request, 'build_opener', Mock(side_effect=AssertionError('no network')))
    target = tmp_path / 'weight'
    target.write_bytes(b'old')
    asset = ('weight', 'https://huggingface.co/official', hashlib.sha256(b'new').hexdigest(), 3, 'test')
    with pytest.raises(ValueError, match='not overwritten'):
        download.fetch(tmp_path, asset)
    assert target.read_bytes() == b'old'


def test_insufficient_disk_stops_before_network(tmp_path, monkeypatch):
    monkeypatch.setattr(download.shutil, 'disk_usage', lambda _: SimpleNamespace(free=1))
    opener = Mock()
    monkeypatch.setattr(download.urllib.request, 'build_opener', opener)
    with pytest.raises(OSError, match='reserve'):
        download.fetch(tmp_path, ('weight', 'https://huggingface.co/x', '0'*64, 3, 'test'))
    opener.assert_not_called()


@pytest.mark.parametrize('url', ['http://huggingface.co/x', 'https://huggingface.co.evil.invalid/x', 'https://example.invalid/x'])
def test_unapproved_redirect_refused(url):
    with pytest.raises(ValueError, match='Non-official'):
        download.OfficialRedirects().redirect_request(None, None, 302, '', {}, url)


def test_slow_download_times_out_without_publishing_partial(tmp_path, monkeypatch):
    monkeypatch.setattr(download.shutil, 'disk_usage', lambda _: SimpleNamespace(free=download.RESERVE+100))
    ticks = iter([0.0, 901.0])
    monkeypatch.setattr(download.time, 'monotonic', lambda: next(ticks))
    monkeypatch.setattr(download.urllib.request, 'build_opener', lambda *_: SimpleNamespace(open=lambda *a, **k: io.BytesIO(b'abc')))
    with pytest.raises(TimeoutError, match='wall limit'):
        download.fetch(tmp_path, ('weight', 'https://huggingface.co/x', '0'*64, 3, 'test'))
    assert not (tmp_path/'weight').exists()
    assert (tmp_path/'weight.part').exists()


def supervisor_args(tmp_path, monkeypatch):
    source = tmp_path/'probe.py'
    source.write_text('raise AssertionError("must not execute in these tests")')
    output = tmp_path/'evidence'
    monkeypatch.setattr(sys, 'argv', ['supervise', '--python', sys.executable, '--probe', str(source), '--log-dir', str(output)])
    memory = {'total_ram':8*1024**3, 'free_ram':1024**3, 'total_commit':24*1024**3, 'free_commit':8*1024**3}
    monkeypatch.setattr(supervise, 'system_memory', lambda: memory)
    return source, output


def test_failed_launch_is_recorded_without_fake_model_success(tmp_path, monkeypatch):
    _, output = supervisor_args(tmp_path, monkeypatch)
    monkeypatch.setattr(supervise.subprocess, 'Popen', Mock(side_effect=OSError('private system error')))
    assert supervise.main() == 1
    record = json.loads((output/'supervisor.json').read_text())
    assert record['started'] is False
    assert record['exit_code'] is None
    assert record['exception_type'] == 'OSError'
    assert 'private system error' not in (output/'supervisor.json').read_text()


def test_supervisor_executes_snapshot_and_cleans_up_only_owned_child(tmp_path, monkeypatch):
    source, output = supervisor_args(tmp_path, monkeypatch)
    original = source.read_bytes()
    child = Mock()
    child.poll.return_value = None
    child.wait.return_value = -1
    calls = []
    def launch(argv, **kwargs):
        calls.append(argv)
        source.write_text('changed after snapshot')
        return child
    monkeypatch.setattr(supervise.subprocess, 'Popen', launch)
    monkeypatch.setattr(supervise.time, 'sleep', Mock(side_effect=RuntimeError('monitor failed')))
    assert supervise.main() == 1
    assert calls[0][2] == str(output/'probe-source.py')
    assert (output/'probe-source.py').read_bytes() == original
    assert json.loads((output/'invocation.json').read_text())['probe_sha256'] == hashlib.sha256(original).hexdigest()
    child.kill.assert_called_once_with()
    child.terminate.assert_not_called()
    record = json.loads((output/'supervisor.json').read_text())
    assert record['stop_reason'] == 'supervisor_or_launch_error'
    assert record['exit_code'] == -1
    # A zero process exit would still not be image/inference evidence.
    assert 'not model inference' in record['note']
