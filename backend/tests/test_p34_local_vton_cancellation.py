"""Synthetic ASGI cancellation races, not model inference or GPU execution."""
import asyncio
from dataclasses import replace

import httpx
import pytest

from local_vton_server import create_app
from .test_local_vton_server import AUTH, body, settings, anyio_backend


class GatedCleanupEngine:
    reason = None

    def __init__(self):
        self.started = asyncio.Event()
        self.cleanup_started = asyncio.Event()
        self.allow_stop = asyncio.Event()
        self.stopped = False
        self.cleanup_interrupted = False

    async def generate(self, *args):
        self.started.set()
        try:
            await asyncio.Event().wait()
        finally:
            self.cleanup_started.set()
            try:
                # Represents waiting for a killed worker to exit before private-file cleanup.
                await self.allow_stop.wait()
                self.stopped = True
            except asyncio.CancelledError:
                self.cleanup_interrupted = True
                raise


@pytest.mark.anyio
@pytest.mark.parametrize('second_action', ['duplicate_delete', 'http_disconnect', 'shutdown', 'timeout_then_delete'])
async def test_overlapping_cancel_requests_keep_slot_until_worker_cleanup_finishes(settings, second_action):
    engine = GatedCleanupEngine()
    timeout = .02 if second_action == 'timeout_then_delete' else settings.timeout
    app = create_app(replace(settings, result_ttl=300, timeout=timeout), engine)
    service = app.state.vton
    request = body()
    pending = []
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                    base_url='https://testserver', headers=AUTH) as client:
            assert (await client.post('/api/local-vton/jobs', json=request)).status_code == 202
            await asyncio.wait_for(engine.started.wait(), 2)
            if second_action != 'timeout_then_delete':
                pending.append(asyncio.create_task(client.delete('/api/local-vton/jobs/'+request['request_id'])))
            await asyncio.wait_for(engine.cleanup_started.wait(), 2)
            if second_action in ('duplicate_delete', 'timeout_then_delete'):
                pending.append(asyncio.create_task(client.delete('/api/local-vton/jobs/'+request['request_id'])))
            elif second_action == 'http_disconnect':
                pending[0].cancel()
            else:
                pending.append(asyncio.create_task(service.close()))
            await asyncio.sleep(.05)
            assert not engine.cleanup_interrupted
            assert service.active == request['request_id'] and not engine.stopped
            assert (await client.post('/api/local-vton/jobs', json=body())).status_code == 429
            engine.allow_stop.set()
            replies = await asyncio.wait_for(asyncio.gather(*pending, return_exceptions=True), 2)
            for reply in replies:
                if isinstance(reply, httpx.Response):
                    assert reply.status_code == 200 and reply.json()['status'] == 'cancelled'
                else:
                    assert reply is None or second_action == 'http_disconnect' and isinstance(reply, asyncio.CancelledError)
            # The disconnected HTTP waiter must not prevent the underlying cancellation finishing.
            for _ in range(20):
                if service.active is None:
                    break
                await asyncio.sleep(.01)
            assert engine.stopped and service.active is None and not service.results
    finally:
        engine.allow_stop.set()
        await asyncio.gather(*pending, return_exceptions=True)
        await service.close()
