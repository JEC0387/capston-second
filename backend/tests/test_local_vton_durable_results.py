"""MOCK ASGI lifecycle tests: synthetic inputs/output, never actual AI inference."""
import json
from dataclasses import replace

import httpx
import pytest

from local_vton_server import create_app
from .test_local_vton_server import AUTH, FakeEngine, body, wait, settings, setup, anyio_backend


@pytest.mark.anyio
async def test_terminal_journal_failure_never_publishes_success_or_accepts_more_work(setup, monkeypatch):
    client, service, engine = setup
    original = service.persist

    def fail_completed_write():
        if any(job['status'] == 'completed' for job in service.jobs.values()):
            raise OSError('synthetic disk failure with private detail')
        original()

    monkeypatch.setattr(service, 'persist', fail_completed_write)
    request = body()
    assert (await client.post('/api/local-vton/jobs', json=request)).status_code == 202
    state = await wait(client, request['request_id'])
    assert state['status'] == 'failed'
    assert state['error'] == 'journal_unavailable'
    assert not state['result_available']
    assert (await client.get('/api/local-vton/jobs/'+request['request_id']+'/result')).status_code == 410
    config = (await client.get('/api/local-vton/config')).json()
    assert not config['ready'] and config['reason'] == 'journal_unavailable'
    assert (await client.post('/api/local-vton/jobs', json=body())).status_code == 503
    assert len(engine.calls) == 1 and service.active is None
    assert 'private detail' not in json.dumps(state)


@pytest.mark.anyio
async def test_restart_marks_ram_only_completed_result_expired_without_regeneration(settings):
    settings = replace(settings, result_ttl=300)
    first = create_app(settings, FakeEngine())
    request = body()
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=first),
                                     base_url='https://testserver', headers=AUTH) as client:
            assert (await client.post('/api/local-vton/jobs', json=request)).status_code == 202
            assert (await wait(client, request['request_id']))['status'] == 'completed'
    finally:
        await first.state.vton.close()
    fresh_engine = FakeEngine()
    restarted = create_app(settings, fresh_engine)
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=restarted),
                                     base_url='https://testserver', headers=AUTH) as client:
            state = (await client.get('/api/local-vton/jobs/'+request['request_id'])).json()
            assert state['status'] == 'expired' and state['error'] == 'result_expired'
            assert not state['result_available']
            assert (await client.get('/api/local-vton/jobs/'+request['request_id']+'/result')).status_code == 410
            duplicate = await client.post('/api/local-vton/jobs', json=request)
            assert duplicate.status_code == 202 and duplicate.json()['status'] == 'expired'
            assert not fresh_engine.calls
            durable = json.loads(open(settings.journal).read())
            assert durable[request['request_id']]['status'] == 'expired'
    finally:
        await restarted.state.vton.close()


@pytest.mark.anyio
async def test_cancel_disk_failure_removes_result_but_does_not_claim_durable_success(setup, monkeypatch):
    client, service, _ = setup
    request = body()
    await client.post('/api/local-vton/jobs', json=request)
    assert (await wait(client, request['request_id']))['status'] == 'completed'
    original = service.persist
    def fail_cancelled_write():
        if service.jobs[request['request_id']]['status'] == 'cancelled':
            raise OSError('synthetic journal disk failure')
        original()
    monkeypatch.setattr(service, 'persist', fail_cancelled_write)
    response = await client.delete('/api/local-vton/jobs/'+request['request_id'])
    assert response.status_code == 503 and response.json()['detail'] == 'journal_unavailable'
    assert not service.journal_ready and request['request_id'] not in service.results
    assert (await client.get('/api/local-vton/jobs/'+request['request_id']+'/result')).status_code == 410
