"""Synthetic mock integration only. These tests do NOT generate a real AI photo."""
import asyncio
import base64
from dataclasses import replace
import hashlib
import io
import json
from uuid import uuid4
import httpx
import pytest
from PIL import Image, PngImagePlugin
from local_vton_server import create_app, LocalSettings, SubprocessEngine, EngineFailure

TOKEN='local-model-test-token-never-a-real-secret-01'
OTHER='local-model-test-token-never-a-real-secret-02'
AUTH={'Authorization':'Bearer '+TOKEN}

def picture(size=(256,384)):
    out=io.BytesIO();Image.new('RGB',size,'green').save(out,'PNG')
    return 'data:image/png;base64,'+base64.b64encode(out.getvalue()).decode()

def body(category='tops'):
    return {'request_id':str(uuid4()),'person':picture(),
        'garments':[{'category':category,'image':picture()}],'consent':True}

class FakeEngine:
    reason=None
    def __init__(self):self.calls=[];self.gate=None;self.failure=False;self.cancelled=False
    async def generate(self,person,garment,options):
        self.calls.append(options)
        try:
            if self.gate:await self.gate.wait()
            if self.failure=='oom':raise EngineFailure('cuda_out_of_memory')
            if self.failure:raise RuntimeError('private data must not escape')
            out=io.BytesIO();meta=PngImagePlugin.PngInfo();meta.add_text('private','remove me')
            Image.new('RGB',(576,864),'blue').save(out,'PNG',pnginfo=meta)
            return out.getvalue(),{'width':576,'height':864,'vram_peak_bytes':None}
        except asyncio.CancelledError:self.cancelled=True;raise

@pytest.fixture
def anyio_backend():return 'asyncio'

@pytest.fixture
def settings(tmp_path):
    return LocalSettings(enabled=True,research_license=True,
        token_hashes=tuple(hashlib.sha256(t.encode()).hexdigest() for t in (TOKEN,OTHER)),
        journal=str(tmp_path/'private'/'journal.json'),result_ttl=.08)

@pytest.fixture
async def setup(settings,monkeypatch):
    def forbid(*a,**k):raise AssertionError('External requests/paid API are forbidden')
    monkeypatch.setattr('urllib.request.OpenerDirector.open',forbid)
    engine=FakeEngine();app=create_app(settings,engine)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver',headers=AUTH) as client:
            yield client,app.state.vton,engine

async def wait(client,id):
    for _ in range(100):
        result=(await client.get('/api/local-vton/jobs/'+id)).json()
        if result['status'] in ('completed','failed','cancelled','timeout','interrupted'):return result
        await asyncio.sleep(.005)
    raise AssertionError('Job did not terminate')

@pytest.mark.anyio
@pytest.mark.parametrize('category',['tops','bottoms','one-pieces'])
async def test_success_single_real_engine_boundary_and_private_png(setup,category):
    client,service,engine=setup;data=body(category)
    assert (await client.post('/api/local-vton/jobs',json=data)).status_code==202
    state=await wait(client,data['request_id']);assert state['status']=='completed'
    assert engine.calls[0]['category']==category and len(engine.calls)==1
    result=await client.get('/api/local-vton/jobs/'+data['request_id']+'/result')
    assert result.status_code==200 and result.headers['content-type']=='image/png'
    assert result.headers['cache-control']=='no-store'
    with Image.open(io.BytesIO(result.content)) as image:
        assert image.size==(576,864) and not image.info
    journal=open(service.settings.journal).read()
    assert TOKEN not in journal and 'data:image' not in journal and 'person' not in journal

@pytest.mark.anyio
async def test_auth_and_https_precede_body_and_cross_user_read(setup):
    client,service,engine=setup
    assert (await client.post('/api/local-vton/jobs',content=b'not json',headers={'Authorization':''})).status_code==401
    assert (await client.get('http://testserver/api/local-vton/config')).status_code==403
    data=body();await client.post('/api/local-vton/jobs',json=data)
    for suffix in ('','/result'):
        result=await client.get('/api/local-vton/jobs/'+data['request_id']+suffix,headers={'Authorization':'Bearer '+OTHER})
        assert result.status_code==404
    assert (await client.delete('/api/local-vton/jobs/'+data['request_id'],headers={'Authorization':'Bearer '+OTHER})).status_code==404

@pytest.mark.anyio
@pytest.mark.parametrize('mutation',[lambda d:d.update(consent=False),lambda d:d.update(person='https://example.test/person.png'),
    lambda d:d.update(person='../../private.png'),lambda d:d['garments'].append(d['garments'][0]),
    lambda d:d['garments'][0].update(category='shoes'),lambda d:d.update(seed=-1),
    lambda d:d.update(person=picture((32,32))),lambda d:d.update(request_id='../bad'),lambda d:d.update(num_timesteps=200)])
async def test_invalid_images_or_consent_never_start_model(setup,mutation):
    client,_,engine=setup;data=body();mutation(data)
    assert (await client.post('/api/local-vton/jobs',json=data)).status_code==400
    assert not engine.calls

@pytest.mark.anyio
async def test_idempotency_conflict_global_limit_and_cancellation(setup):
    client,service,engine=setup;engine.gate=asyncio.Event();data=body()
    assert (await client.post('/api/local-vton/jobs',json=data)).status_code==202
    await asyncio.sleep(.01)
    assert (await client.post('/api/local-vton/jobs',json=data)).status_code==202
    changed=dict(data,seed=5)
    assert (await client.post('/api/local-vton/jobs',json=changed)).status_code==409
    assert (await client.post('/api/local-vton/jobs',json=body())).status_code==429
    assert len(engine.calls)==1
    assert (await client.delete('/api/local-vton/jobs/'+data['request_id'])).status_code==200
    assert engine.cancelled and service.active is None
    assert (await client.get('/api/local-vton/jobs/'+data['request_id']+'/result')).status_code==410

@pytest.mark.anyio
async def test_failed_inference_is_not_fake_success(setup):
    client,_,engine=setup;engine.failure=True;data=body()
    await client.post('/api/local-vton/jobs',json=data)
    result=await wait(client,data['request_id'])
    assert result['status']=='failed' and result['error']=='model_failed'
    assert not result['result_available'] and 'private data' not in json.dumps(result)


@pytest.mark.anyio
async def test_oom_is_reported_without_an_image(setup):
    client,service,engine=setup;engine.failure='oom';data=body()
    await client.post('/api/local-vton/jobs',json=data)
    result=await wait(client,data['request_id'])
    assert result['error']=='cuda_out_of_memory' and not result['result_available']
    assert service.active is None

@pytest.mark.anyio
async def test_ttl_and_explicit_delete_remove_result(setup):
    client,_,_=setup;data=body();await client.post('/api/local-vton/jobs',json=data);await wait(client,data['request_id'])
    await asyncio.sleep(.1)
    assert (await client.get('/api/local-vton/jobs/'+data['request_id']+'/result')).status_code==410
    assert (await client.get('/api/local-vton/jobs/'+data['request_id'])).json()['status']=='expired'

@pytest.mark.anyio
async def test_unavailable_model_is_reported_without_accepting_images(settings):
    engine=FakeEngine();engine.reason='cuda_unavailable';app=create_app(settings,engine)
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver',headers=AUTH) as client:
            config=(await client.get('/api/local-vton/config')).json()
            assert config['reason']=='cuda_unavailable' and not config['configured'] and not config['paid_api']
            assert (await client.post('/api/local-vton/jobs',json=body())).status_code==503
            assert not engine.calls
    finally:await app.state.vton.close()

@pytest.mark.anyio
async def test_restart_never_recomputes_same_request(settings):
    engine=FakeEngine();app=create_app(settings,engine);data=body()
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver',headers=AUTH) as client:
        await client.post('/api/local-vton/jobs',json=data);await wait(client,data['request_id'])
    await app.state.vton.close()
    saved=json.loads(open(settings.journal).read());saved[data['request_id']]['status']='running'
    open(settings.journal,'w').write(json.dumps(saved))
    fresh=FakeEngine();app=create_app(settings,fresh)
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver',headers=AUTH) as client:
            reply=await client.post('/api/local-vton/jobs',json=data)
            assert reply.json()['status']=='interrupted' and not fresh.calls
    finally:await app.state.vton.close()

@pytest.mark.anyio
async def test_timeout_releases_slot_only_after_worker_stops(settings):
    engine=FakeEngine();engine.gate=asyncio.Event();app=create_app(replace(settings,timeout=.025),engine)
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver',headers=AUTH) as client:
            data=body();await client.post('/api/local-vton/jobs',json=data)
            assert (await wait(client,data['request_id']))['status']=='timeout'
            assert engine.cancelled and app.state.vton.active is None
    finally:await app.state.vton.close()

@pytest.mark.anyio
async def test_loopback_http_is_explicit_and_remote_headers_do_not_bypass(settings):
    app=create_app(replace(settings,allow_loopback_http=True),FakeEngine())
    try:
        for peer,status in [('127.0.0.1',200),('203.0.113.1',403)]:
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app,client=(peer,12)),base_url='http://127.0.0.1',headers=AUTH) as client:
                assert (await client.get('/api/local-vton/config',headers={'X-Forwarded-Proto':'https'})).status_code==status
    finally:await app.state.vton.close()

@pytest.mark.anyio
async def test_body_size_is_bounded_before_parsing(setup,monkeypatch):
    client,_,engine=setup;monkeypatch.setattr('local_vton_server.MAX_BODY',100)
    assert (await client.post('/api/local-vton/jobs',content=b'x'*101,headers={'Content-Type':'application/json'})).status_code==413
    assert not engine.calls

def test_no_model_or_license_is_not_ready(settings):
    engine=SubprocessEngine(replace(settings,weights=''))
    assert engine.reason=='weights_missing'
    app=create_app(replace(settings,research_license=False),FakeEngine())
    assert app.state.vton.ready_reason()=='research_license_required'
    app.state.vton.lease.close()


def test_cpu_gpu_and_memory_preflight_branches_are_explicit(settings,monkeypatch):
    import sys
    from types import SimpleNamespace
    monkeypatch.setattr('local_vton_server.verify_weight_manifest',lambda _:None)
    monkeypatch.setattr('local_vton_server.importlib.util.find_spec',lambda _:object())
    cuda=SimpleNamespace(is_available=lambda:False,get_device_properties=lambda _:SimpleNamespace(total_memory=4*1024**3))
    monkeypatch.setitem(sys.modules,'torch',SimpleNamespace(cuda=cuda))
    configured=replace(settings,weights='synthetic-test-path')
    assert SubprocessEngine(configured).reason=='cuda_unavailable'
    assert SubprocessEngine(replace(configured,device='cpu')).reason=='cpu_not_approved'
    assert SubprocessEngine(replace(configured,device='cpu',allow_cpu=True)).reason is None
    cuda.is_available=lambda:True
    assert SubprocessEngine(configured).reason=='vram_below_model_guidance'
    cuda.get_device_properties=lambda _:SimpleNamespace(total_memory=16*1024**3)
    assert SubprocessEngine(configured).reason is None


@pytest.mark.anyio
async def test_real_child_process_is_killed_and_private_files_removed(settings,monkeypatch):
    import sys
    from pathlib import Path
    real=asyncio.create_subprocess_exec
    started=asyncio.Event();observed={}
    async def launch(*args,**kwargs):
        observed['private']=Path(args[-1])
        process=await real(sys.executable,'-c','import time;time.sleep(60)',**kwargs)
        observed['process']=process;started.set()
        return process
    monkeypatch.setattr('local_vton_server.asyncio.create_subprocess_exec',launch)
    engine=SubprocessEngine(settings)
    task=asyncio.create_task(engine.generate(Image.new('RGB',(256,384)),Image.new('RGB',(256,384)),
        {'category':'tops','garment_photo_type':'flat-lay','num_timesteps':20,'seed':42}))
    await asyncio.wait_for(started.wait(),15)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):await asyncio.wait_for(task,15)
    assert observed['process'].returncode is not None
    assert not observed['private'].exists()
