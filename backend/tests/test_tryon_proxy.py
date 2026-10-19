"""Local fakes only: no paid FASHN calls or operational database."""
import asyncio, base64, hashlib, io, json
from uuid import uuid4
import httpx
import pytest
from fastapi import FastAPI
from PIL import Image
import tryon_proxy as p
TOKEN = 'mock-client-token-for-tests-only-00000001'
OTHER = 'mock-client-token-for-tests-only-00000002'
AUTH = {'Authorization': 'Bearer ' + TOKEN}

def image(color='red'):
    out = io.BytesIO(); Image.new('RGB', (64, 96), color).save(out, 'JPEG')
    return 'data:image/jpeg;base64,' + base64.b64encode(out.getvalue()).decode()

class Fake:
    def __init__(self): self.calls=[]; self.state='completed'; self.failure=None; self.gate=None
    async def run(self, person, garment, category):
        self.calls.append((person, garment, category))
        if self.gate: await self.gate.wait()
        if self.failure: raise p.ProviderFailure(self.failure)
        return 'mock-' + str(len(self.calls))
    async def status(self, job_id):
        return {'status': self.state, 'output': [image('blue')], 'error': 'PoseError' if self.state == 'failed' else None}

@pytest.fixture
def anyio_backend(): return 'asyncio'

@pytest.fixture
async def setup(tmp_path, monkeypatch):
    def forbid(*args, **kwargs): raise AssertionError('External networking forbidden in Mock tests')
    monkeypatch.setattr(p.urlrequest.OpenerDirector, 'open', forbid)
    settings = p.Settings('MOCK_NOT_A_KEY', tuple(hashlib.sha256(t.encode()).hexdigest() for t in (TOKEN, OTHER)), str(tmp_path/'private'/'journal.json'), 4, 6, True)
    fake=Fake(); service=p.TryOnService(settings, fake, poll_seconds=0, timeout_seconds=.05)
    app=FastAPI(); app.include_router(p.create_router(lambda: service))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='https://testserver', headers=AUTH) as client:
        yield client, service, fake
    await service.close()

def body(categories=('tops',), id=None):
    return {'request_id': id or str(uuid4()), 'consent': True, 'person': image(), 'garments': [{'category': c, 'image': image('green')} for c in categories]}

async def finish(client, id, headers=None):
    for _ in range(200):
        response=await client.get('/api/tryon/jobs/'+id, headers=headers)
        assert response.status_code == 200, response.text
        value=response.json()
        if value['status'] in p.TERMINAL: return value
        await asyncio.sleep(.002)
    pytest.fail('Mock job did not finish')

@pytest.mark.anyio
@pytest.mark.parametrize('token,expected', [('',401), ('bad',401), (TOKEN,200)])
async def test_authentication(setup, token, expected):
    c,_,f=setup
    assert (await c.get('/api/tryon/config', headers={'Authorization':'Bearer '+token})).status_code == expected
    assert not f.calls

@pytest.mark.anyio
async def test_http_refused_before_photo_read(setup):
    c,_,f=setup
    assert (await c.post('http://testserver/api/tryon/jobs', content=b'bad')).status_code == 403
    assert not f.calls

@pytest.mark.anyio
async def test_disabled_by_default():
    service=p.TryOnService(p.Settings()); app=FastAPI(); app.include_router(p.create_router(lambda:service))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://testserver') as c:
        assert (await c.post('/api/tryon/jobs',content=b'bad')).status_code==503
    await service.close()

@pytest.mark.anyio
@pytest.mark.parametrize('change', ['no_person','no_clothes','declined','shoes','duplicate','dress_plus','url','broken','bad_id','extra'])
async def test_invalid_input_never_generates(setup,change):
    c,_,f=setup; b=body()
    if change=='no_person': b['person']=''
    elif change=='no_clothes': b['garments']=[]
    elif change=='declined': b['consent']=False
    elif change=='shoes': b['garments'][0]['category']='shoes'
    elif change=='duplicate': b=body(('tops','tops'))
    elif change=='dress_plus': b=body(('one-pieces','tops'))
    elif change=='url': b['person']='http://169.254.169.254/metadata'
    elif change=='broken': b['person']='data:image/jpeg;base64,Zm9v'
    elif change=='bad_id': b['request_id']='not-uuid'
    else: b['model_name']='expensive-model'
    assert (await c.post('/api/tryon/jobs',json=b)).status_code==422
    assert not f.calls

@pytest.mark.anyio
@pytest.mark.parametrize('category',['tops','bottoms','one-pieces'])
async def test_categories_success_and_jpeg(setup,category):
    c,s,f=setup; b=body((category,))
    assert (await c.post('/api/tryon/jobs',json=b)).status_code==202
    assert (await finish(c,b['request_id']))['status']=='completed'
    r=await c.get('/api/tryon/jobs/'+b['request_id']+'/result')
    assert r.status_code==200 and r.content.startswith(b'\xff\xd8')
    assert r.headers['cache-control']=='no-store' and Image.open(io.BytesIO(r.content)).format=='JPEG'
    assert len(f.calls)==1 and f.calls[0][2]==category
    raw=s.path.read_text(); assert 'data:image' not in raw and TOKEN not in raw and 'MOCK_NOT_A_KEY' not in raw
    assert s.path.stat().st_mode & 0o777 == 0o600

@pytest.mark.anyio
async def test_duplicate_clicks_reentry_and_races(setup):
    c,_,f=setup; b=body()
    results=await asyncio.gather(*(c.post('/api/tryon/jobs',json=b) for _ in range(5)))
    assert all(r.status_code in (202,429) for r in results)
    assert any(r.status_code==202 for r in results)
    await finish(c,b['request_id'])
    assert (await c.post('/api/tryon/jobs',json=dict(reversed(list(b.items()))))).status_code==202
    assert len(f.calls)==1
    b['person']=image('yellow')
    assert (await c.post('/api/tryon/jobs',json=b)).status_code==409

@pytest.mark.anyio
async def test_batch_order_and_intermediate_result(setup):
    c,_,f=setup; b=body(('bottoms','tops'))
    await c.post('/api/tryon/jobs',json=b); assert (await finish(c,b['request_id']))['status']=='completed'
    assert [x[2] for x in f.calls]==['tops','bottoms'] and f.calls[1][0]!=f.calls[0][0]

@pytest.mark.anyio
@pytest.mark.parametrize('failure',['credit','provider_auth','provider_connection_unknown'])
async def test_provider_error_does_not_retry(setup,failure):
    c,_,f=setup; f.failure=failure; b=body()
    await c.post('/api/tryon/jobs',json=b); value=await finish(c,b['request_id'])
    assert value['status']=='failed' and value['error']==failure
    for _ in range(3): await c.get('/api/tryon/jobs/'+b['request_id'])
    assert len(f.calls)==1

@pytest.mark.anyio
@pytest.mark.parametrize('state,expected',[('processing','timeout'),('failed','failed'),('unexpected','failed')])
async def test_timeout_and_failure(setup,state,expected):
    c,_,f=setup; f.state=state; b=body()
    await c.post('/api/tryon/jobs',json=b)
    assert (await finish(c,b['request_id']))['status']==expected and len(f.calls)==1

@pytest.mark.anyio
async def test_cancel_during_run_stops_next_garment(setup):
    c,_,f=setup; f.gate=asyncio.Event(); b=body(('tops','bottoms'))
    await c.post('/api/tryon/jobs',json=b); await asyncio.sleep(.005)
    assert (await c.delete('/api/tryon/jobs/'+b['request_id'])).status_code==200
    f.gate.set(); assert (await finish(c,b['request_id']))['status']=='cancelled'
    assert len(f.calls)==1

@pytest.mark.anyio
async def test_other_user_cannot_read_or_cancel(setup):
    c,_,_=setup; b=body(); await c.post('/api/tryon/jobs',json=b); await finish(c,b['request_id'])
    h={'Authorization':'Bearer '+OTHER}; path='/api/tryon/jobs/'+b['request_id']
    assert (await c.get(path,headers=h)).status_code==404
    assert (await c.get(path+'/result',headers=h)).status_code==404
    assert (await c.delete(path,headers=h)).status_code==404

@pytest.mark.anyio
async def test_daily_quota_persisted_even_when_outcome_unknown(setup):
    c,s,f=setup; f.failure='provider_connection_unknown'
    for _ in range(4):
        b=body(); assert (await c.post('/api/tryon/jobs',json=b)).status_code==202
        await finish(c,b['request_id'])
    assert (await c.post('/api/tryon/jobs',json=body())).status_code==429
    await s.close(); restored=p.TryOnService(s.settings,Fake())
    assert sum(x['total'] for x in restored.data['days'].values())==4
    await restored.close()

@pytest.mark.anyio
async def test_restart_never_replays_paid_intent(setup):
    c,s,f=setup; f.gate=asyncio.Event(); b=body(); await c.post('/api/tryon/jobs',json=b); await asyncio.sleep(.005)
    await s.close(); replacement=Fake(); restored=p.TryOnService(s.settings,replacement)
    j=restored.data['jobs'][b['request_id']]; assert j['status']=='interrupted'
    assert restored.submit(j['owner'],j['id'],'',b['garments'],j['fingerprint'])['status']=='interrupted'
    assert not replacement.calls
    await restored.close()

@pytest.mark.anyio
async def test_second_worker_and_corrupt_journal_fail_closed(setup):
    _,s,_=setup; second=p.TryOnService(s.settings,Fake()); assert second.problem=='journal_unavailable'; await second.close()
    await s.close(); s.path.write_text('corrupt'); broken=p.TryOnService(s.settings,Fake())
    assert broken.problem=='journal_unavailable'; await broken.close()

@pytest.mark.anyio
async def test_result_expiry_never_regenerates(setup):
    c,s,f=setup; s.result_ttl=.015; b=body(); await c.post('/api/tryon/jobs',json=b); await finish(c,b['request_id'])
    await asyncio.sleep(.025)
    assert (await c.get('/api/tryon/jobs/'+b['request_id']+'/result')).status_code==410 and len(f.calls)==1

@pytest.mark.anyio
async def test_body_limit(setup,monkeypatch):
    c,_,f=setup; monkeypatch.setattr(p,'MAX_BODY',16)
    assert (await c.post('/api/tryon/jobs',content=b'x'*17)).status_code==413 and not f.calls

def test_exif_rotation_alpha_and_size():
    out=io.BytesIO(); im=Image.new('RGB',(40,80),'red'); exif=im.getexif(); exif[274]=6; im.save(out,'JPEG',exif=exif)
    value=p.normalize_image('data:image/jpeg;base64,'+base64.b64encode(out.getvalue()).decode())
    result=Image.open(io.BytesIO(base64.b64decode(value.split(',')[1])))
    assert result.size==(80,40) and not result.getexif()
    out=io.BytesIO(); Image.new('RGBA',(1400,1600),(0,0,0,0)).save(out,'PNG')
    value=p.normalize_image('data:image/png;base64,'+base64.b64encode(out.getvalue()).decode())
    result=Image.open(io.BytesIO(base64.b64decode(value.split(',')[1])))
    assert max(result.size)==1296 and min(result.getpixel((0,0)))>250

@pytest.mark.anyio
async def test_official_contract_without_network():
    class Response:
        def __enter__(self): return self
        def __exit__(self,*args): pass
        def read(self,limit): return b'{"id":"mock-id","error":null}'
    class Opener:
        def __init__(self): self.requests=[]
        def open(self,request,timeout): self.requests.append(request); return Response()
    provider=p.FashnProvider('MOCK_NOT_A_KEY'); provider.opener=Opener()
    assert await provider.run(image(),image(),'tops')=='mock-id'
    await provider.status('mock-id'); post,get=provider.opener.requests; data=json.loads(post.data)
    assert post.full_url=='https://api.fashn.ai/v1/run' and post.get_method()=='POST'
    assert data['model_name']=='tryon-v1.6' and data['inputs']['num_samples']==1 and data['inputs']['return_base64'] is True
    assert data['inputs']['category']=='tops' and data['inputs']['output_format']=='jpeg'
    assert get.full_url=='https://api.fashn.ai/v1/status/mock-id' and get.get_method()=='GET'

@pytest.mark.anyio
async def test_active_job_and_global_limit_protect_usage(setup):
    c,s,f=setup; f.gate=asyncio.Event(); b=body()
    await c.post('/api/tryon/jobs',json=b); await asyncio.sleep(.005)
    assert (await c.post('/api/tryon/jobs',json=body())).status_code==409
    f.gate.set(); await finish(c,b['request_id'])
    # Each two-garment request reserves two units. Cross-user total also has a cap.
    for token in (TOKEN, OTHER):
        b=body(('tops','bottoms'))
        assert (await c.post('/api/tryon/jobs',json=b,headers={'Authorization':'Bearer '+token})).status_code==202
        await finish(c,b['request_id'],headers={'Authorization':'Bearer '+token})
    b=body(('tops','bottoms'))
    assert (await c.post('/api/tryon/jobs',json=b,headers={'Authorization':'Bearer '+OTHER})).status_code==429
    assert len(f.calls)==5

@pytest.mark.anyio
async def test_rate_limit_upload_concurrency_and_capacity_fail_closed(setup):
    c,s,f=setup
    s.uploads=2
    assert (await c.post('/api/tryon/jobs',json=body())).status_code==429
    s.uploads=0
    owner=hashlib.sha256(TOKEN.encode()).hexdigest()
    import time
    s.rates[owner].extend([time.monotonic()]*90)
    assert (await c.get('/api/tryon/config')).status_code==429
    assert not f.calls

@pytest.mark.anyio
async def test_disk_write_failure_cannot_send_a_paid_request(setup,monkeypatch):
    c,s,f=setup
    def disk_full(): raise OSError('mock disk full')
    monkeypatch.setattr(s,'_persist',disk_full)
    assert (await c.post('/api/tryon/jobs',json=body())).status_code==503
    assert not f.calls and s.problem=='journal_unavailable'

@pytest.mark.anyio
async def test_untrusted_provider_url_is_not_downloaded(setup):
    c,_,f=setup
    async def output(_): return {'status':'completed','output':['https://untrusted.example/image.jpg']}
    f.status=output; b=body(); await c.post('/api/tryon/jobs',json=b)
    result=await finish(c,b['request_id'])
    assert result['status']=='failed' and result['error']=='invalid_result'
    assert len(f.calls)==1
