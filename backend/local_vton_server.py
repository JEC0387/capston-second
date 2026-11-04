"""Independent development-only model server. Never imports main.py or the paid FASHN provider."""
import asyncio
import base64
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from dataclasses import dataclass
import hashlib
import hmac
import importlib.util
import io
import json
import math
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import time
from uuid import UUID

from fastapi import FastAPI, HTTPException, Request, Response
from PIL import Image, ImageOps
from local_vton import CATEGORIES, LocalVtonError, verify_weight_manifest
from local_vton_parser import ParserIntegrityError, verify_parser_assets, verify_runtime_revisions

MAX_IMAGE = 5 * 1024 * 1024
MAX_BODY = 15 * 1024 * 1024
TERMINAL = {'completed', 'failed', 'timeout', 'cancelled', 'interrupted', 'expired'}
ENGINE = 'fashn-vton-1.5-local'


class EngineFailure(LocalVtonError):
    def __init__(self,code):
        self.code=code if code=='cuda_out_of_memory' else 'model_failed'
        super().__init__(self.code)


def private_work_directory(journal,clean=False):
    root=Path(journal).resolve().parent/'local-vton-work'
    marker=root/'.fashionai-owner'
    if root.is_symlink():raise OSError('Unsafe private work directory')
    if root.exists():
        if not marker.is_file() or marker.read_text()!=ENGINE:raise OSError('Unowned work directory')
    else:
        root.mkdir(parents=True,mode=0o700)
        marker.write_text(ENGINE);os.chmod(marker,0o600)
    if clean:
        for child in root.iterdir():
            # Confine recursive cleanup to this service's marked, private job directories.
            if child.name.startswith('job-') and child.is_dir() and not child.is_symlink() and child.resolve().parent==root:
                shutil.rmtree(child)
    return root


@dataclass(frozen=True)
class LocalSettings:
    enabled: bool = False
    token_hashes: tuple = ()
    weights: str = ''
    journal: str = ''
    allow_loopback_http: bool = False
    device: str = 'cuda'
    allow_cpu: bool = False
    research_license: bool = False
    timeout: float = 240
    result_ttl: float = 300
    max_jobs: int = 1000
    memory_fraction: float = .8

    @classmethod
    def environment(cls):
        return cls(enabled=os.getenv('LOCAL_VTON_ENABLED') == 'true',
            token_hashes=tuple(part.strip().lower() for part in os.getenv('LOCAL_VTON_TOKEN_HASHES', '').split(',') if part.strip()),
            weights=os.getenv('LOCAL_VTON_WEIGHTS', ''), journal=os.getenv('LOCAL_VTON_JOURNAL', ''),
            allow_loopback_http=os.getenv('LOCAL_VTON_LOOPBACK_HTTP') == 'true',
            device=os.getenv('LOCAL_VTON_DEVICE', 'cuda'), allow_cpu=os.getenv('LOCAL_VTON_ALLOW_CPU') == 'true',
            research_license=os.getenv('LOCAL_VTON_RESEARCH_LICENSE_ACCEPTED') == 'true')


def decode_image(value, role):
    """Inline data only; no URL fetches, filesystem paths, metadata or animated images."""
    if not isinstance(value, str) or not value.startswith(('data:image/jpeg;base64,', 'data:image/png;base64,', 'data:image/webp;base64,')):
        raise ValueError('image_format')
    encoded = value.split(',', 1)[1]
    if len(encoded) > MAX_IMAGE * 4 // 3 + 8:
        raise ValueError('image_size')
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > MAX_IMAGE:
        raise ValueError('image_size')
    with Image.open(io.BytesIO(raw)) as image:
        if image.format not in ('JPEG', 'PNG', 'WEBP') or getattr(image, 'n_frames', 1) != 1:
            raise ValueError('image_format')
        if image.width * image.height > 20_000_000 or min(image.size) < (256 if role == 'person' else 128):
            raise ValueError('image_dimensions')
        image = ImageOps.exif_transpose(image)
        if role == 'person' and not .25 <= image.width / image.height <= 1.2:
            raise ValueError('person_framing')
        image.thumbnail((1296, 1296))
        rgba = image.convert('RGBA')
        result = Image.new('RGB', image.size, 'white')
        result.paste(rgba, mask=rgba.getchannel('A'))
        return result


class SubprocessEngine:
    """A killed/timed-out job cannot leave a Python inference thread occupying the GPU slot."""
    def __init__(self, settings):
        self.settings = settings
        self.reason = self._preflight() if settings.enabled else 'disabled'

    def _preflight(self):
        try:
            if not self.settings.weights:
                return 'weights_missing'
            verify_weight_manifest(Path(self.settings.weights))
            if any(importlib.util.find_spec(name) is None for name in ('torch', 'fashn_vton', 'fashn_human_parser', 'transformers', 'onnxruntime')):
                return 'dependencies_missing'
            try:
                verify_parser_assets(Path(self.settings.weights))
                verify_runtime_revisions()
            except ParserIntegrityError:
                return 'parser_integrity_unavailable'
            import torch
            if self.settings.device == 'cpu':
                return None if self.settings.allow_cpu else 'cpu_not_approved'
            if self.settings.device != 'cuda' or not torch.cuda.is_available():
                return 'cuda_unavailable'
            if torch.cuda.get_device_properties(0).total_memory < 8 * 1024**3:
                return 'vram_below_model_guidance'
            return None
        except (LocalVtonError, OSError, ImportError, RuntimeError):
            return 'weights_or_runtime_unavailable'

    async def generate(self, person, garment, options):
        process = None
        # Private per-job files exist only during the worker; user supplies no path.
        with tempfile.TemporaryDirectory(prefix='job-',dir=private_work_directory(self.settings.journal)) as private:
            folder = Path(private)
            person.save(folder/'person.png', 'PNG')
            garment.save(folder/'garment.png', 'PNG')
            request = dict(options, parent_pid=os.getpid(), weights_dir=str(Path(self.settings.weights).resolve()),
                device=self.settings.device, memory_fraction=self.settings.memory_fraction)
            (folder/'request.json').write_text(json.dumps(request))
            try:
                process = await asyncio.create_subprocess_exec(sys.executable,
                    str(Path(__file__).with_name('local_vton_worker.py')), private,
                    stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
                if await process.wait() != 0:
                    error_file=folder/'error.json'
                    code=json.loads(error_file.read_text()).get('error') if error_file.is_file() else 'model_failed'
                    raise EngineFailure(code)
                output = folder/'result.png'
                if not output.is_file() or output.stat().st_size > 8 * 1024 * 1024:
                    raise LocalVtonError('invalid_result')
                result = output.read_bytes()
                metrics = json.loads((folder/'metrics.json').read_text())
                return result, metrics
            finally:
                if process and process.returncode is None:
                    process.kill()
                    await process.wait()


class LocalVtonService:
    def __init__(self, settings, engine=None):
        self.settings = settings
        self.engine = engine or SubprocessEngine(settings)
        self.jobs, self.results, self.tasks = {}, {}, {}
        self.active = None
        self.uploads = 0
        self.budgets = defaultdict(deque)
        self.lease = None
        self.journal_ready = False
        if settings.journal:
            self._open_journal()

    def _open_journal(self):
        path = Path(self.settings.journal)
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.lease = open(str(path)+'.lock', 'a+b')
        os.chmod(str(path)+'.lock',0o600)
        try:
            self.lease.seek(0);self.lease.write(b'0');self.lease.flush();self.lease.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(self.lease.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            private_work_directory(self.settings.journal,clean=True)
            if path.exists():
                if path.stat().st_size > 2 * 1024 * 1024:
                    raise ValueError('journal_size')
                self.jobs = json.loads(path.read_text())
                if not isinstance(self.jobs, dict) or len(self.jobs) > self.settings.max_jobs:
                    raise ValueError('journal_size')
                for id, job in self.jobs.items():
                    UUID(id)
                    if set(job) != {'id','owner','fingerprint','status','error','created','metrics'}:
                        raise ValueError('journal_schema')
                    if not all(re.fullmatch('[a-f0-9]{64}', job[key]) for key in ('owner','fingerprint')):
                        raise ValueError('journal_schema')
                    if job['status'] not in TERMINAL:
                        job.update(status='interrupted', error='server_restarted')
            self.journal_ready = True
            self.persist()
        except (OSError, ValueError, KeyError, TypeError):
            self.journal_ready = False
            self.lease.close();self.lease = None

    def persist(self):
        path = Path(self.settings.journal)
        temporary = path.with_name(path.name+'.tmp')
        with temporary.open('w', encoding='utf-8') as stream:
            os.chmod(temporary,0o600)
            json.dump(self.jobs, stream, separators=(',', ':'))
            stream.flush();os.fsync(stream.fileno())
        os.replace(temporary, path)

    def authenticate(self, request):
        local = (self.settings.allow_loopback_http and request.url.scheme == 'http' and request.client
            and request.client.host in ('127.0.0.1','::1') and request.url.hostname in ('127.0.0.1','localhost','::1','10.0.2.2'))
        if request.url.scheme != 'https' and not local:
            raise HTTPException(403, 'https_required')
        token = request.headers.get('authorization', '')
        if not token.startswith('Bearer ') or not 32 <= len(token[7:]) <= 256:
            raise HTTPException(401, 'authentication_required')
        owner = hashlib.sha256(token[7:].encode()).hexdigest()
        if not any(hmac.compare_digest(owner, known) for known in self.settings.token_hashes):
            raise HTTPException(401, 'authentication_required')
        return owner

    def ready_reason(self):
        if not self.settings.enabled:
            return 'disabled'
        if not self.settings.research_license:
            return 'research_license_required'
        if not self.settings.token_hashes or any(not re.fullmatch('[a-f0-9]{64}',value) for value in self.settings.token_hashes):
            return 'authentication_not_configured'
        if not self.journal_ready:
            return 'journal_unavailable'
        return self.engine.reason

    def purge(self):
        now = time.time()
        changed = False
        for id, (_, expiry) in list(self.results.items()):
            if expiry <= now:
                self.results.pop(id, None)
                self.jobs[id].update(status='expired', error='result_expired')
                changed = True
        stale = [id for id,j in self.jobs.items() if j['status'] in TERMINAL and j['created'] < now-86400]
        for id in stale:
            self.jobs.pop(id, None);self.results.pop(id, None)
            changed = True
        # Expiry is a durable terminal state. Without this write, a restart can
        # resurrect "completed" in the journal even though the PNG is gone.
        if changed and self.journal_ready:
            try:
                self.persist()
            except OSError:
                self.journal_ready = False

    def owned(self, id, owner):
        self.purge()
        job = self.jobs.get(id)
        if not job or not hmac.compare_digest(job['owner'], owner):
            raise HTTPException(404, 'job_not_found')
        return job

    def view(self, job):
        return {key:job[key] for key in ('id','status','error','metrics')} | {
            'engine':ENGINE,'step':1,'count':1,'result_available':job['id'] in self.results}

    async def run(self, job, person, garment, options):
        try:
            job['status']='running';self.persist()
            result, metrics = await asyncio.wait_for(self.engine.generate(person,garment,options),self.settings.timeout)
            # Validate and strip all output metadata before publishing a real worker result.
            if len(result) > 8 * 1024 * 1024:
                raise ValueError('invalid_result')
            with Image.open(io.BytesIO(result)) as opened:
                if opened.format != 'PNG' or min(opened.size)<32 or opened.width*opened.height>20_000_000:
                    raise ValueError('invalid_result')
                image=Image.new('RGB',opened.size);image.paste(opened.convert('RGB'))
                output=io.BytesIO();image.save(output,'PNG');result=output.getvalue()
            allowed=('model_load_seconds','inference_seconds','vram_peak_bytes','width','height')
            job['metrics']={k:metrics[k] for k in allowed if k in metrics and
                (metrics[k] is None or isinstance(metrics[k],(float,int)) and math.isfinite(metrics[k]) and metrics[k]>=0)}
            self.results[job['id']]=(result,time.time()+self.settings.result_ttl)
            job.update(status='completed',error=None)
        except asyncio.TimeoutError:
            job.update(status='timeout',error='timeout')
        except asyncio.CancelledError:
            job.update(status='cancelled',error='cancelled')
        except EngineFailure as error:
            job.update(status='failed',error=error.code)
        except Exception:
            job.update(status='failed',error='model_failed')
        finally:
            self.active=None
            self.tasks.pop(job['id'],None)
            self.persist()

    async def close(self):
        for task in list(self.tasks.values()):task.cancel()
        if self.tasks:await asyncio.gather(*list(self.tasks.values()),return_exceptions=True)
        self.results.clear()
        if self.lease:self.lease.close();self.lease=None


def create_app(settings=None, engine=None):
    settings=settings or LocalSettings.environment()
    if not (0<settings.timeout<=1800 and 0<settings.result_ttl<=300 and .1<=settings.memory_fraction<=1 and 1<=settings.max_jobs<=1000):
        raise ValueError('Unsafe local VTON resource limits')
    service=LocalVtonService(settings,engine)
    @asynccontextmanager
    async def lifespan(app):
        async def expire():
            while True:
                await asyncio.sleep(1);service.purge()
        cleanup=asyncio.create_task(expire())
        try:yield
        finally:
            cleanup.cancel();await asyncio.gather(cleanup,return_exceptions=True);await service.close()
    app=FastAPI(title='FashionAI independent local VTON development server',lifespan=lifespan)
    app.state.vton=service

    @app.get('/health')
    async def health():return {'service':'FashionAI local VTON','ready':service.ready_reason() is None}

    @app.get('/api/local-vton/config')
    async def config(request:Request):
        service.authenticate(request)
        reason=service.ready_reason()
        return {'engine':ENGINE,'configured':reason is None,'ready':reason is None,'reason':reason,
            'categories':list(CATEGORIES),'max_garments':1,'model_loaded':False,
            'paid_api':False,'research_only':True,'result_ttl_seconds':settings.result_ttl}

    @app.post('/api/local-vton/jobs',status_code=202)
    async def submit(request:Request):
        owner=service.authenticate(request) # Before any body/image parsing.
        if service.uploads>=2:raise HTTPException(429,'busy')
        if request.headers.get('content-type','').split(';')[0]!='application/json':raise HTTPException(415,'json_required')
        service.uploads+=1
        try:
            raw=bytearray()
            async for chunk in request.stream():
                raw.extend(chunk)
                if len(raw)>MAX_BODY:raise HTTPException(413,'request_too_large')
            try:
                body=json.loads(raw)
                if not isinstance(body,dict) or set(body)-{'request_id','person','garments','consent','garment_photo_type','num_timesteps','seed'}:
                    raise ValueError()
                id=body['request_id']
                if not isinstance(id,str) or str(UUID(id))!=id:raise ValueError()
                if body.get('consent') is not True:raise HTTPException(400,'consent_required')
                garments=body['garments']
                if not isinstance(garments,list) or len(garments)!=1:raise ValueError()
                if set(garments[0])!={'category','image'} or garments[0]['category'] not in CATEGORIES:raise ValueError()
                photo=body.get('garment_photo_type','flat-lay');steps=body.get('num_timesteps',30);seed=body.get('seed',42)
                if photo not in ('flat-lay','model') or type(steps)!=int or not 20<=steps<=50 or type(seed)!=int or not 0<=seed<=2**31-1:raise ValueError()
                fingerprint=hashlib.sha256(json.dumps(body,sort_keys=True,separators=(',',':')).encode()).hexdigest()
                if id in service.jobs:
                    job=service.owned(id,owner)
                    if job['fingerprint']!=fingerprint:raise HTTPException(409,'idempotency_conflict')
                    return service.view(job)
                reason=service.ready_reason()
                if reason:raise HTTPException(503,reason)
                service.purge()
                if service.active or len(service.jobs)>=settings.max_jobs:raise HTTPException(429,'busy')
                budget=service.budgets[owner];now=time.time()
                while budget and budget[0]<now-3600:budget.popleft()
                if len(budget)>=30:raise HTTPException(429,'rate_limit')
                person=decode_image(body['person'],'person')
                garment=decode_image(garments[0]['image'],'garment')
            except HTTPException:raise
            except Exception:raise HTTPException(400,'invalid_images_or_request') from None
            job={'id':id,'owner':owner,'fingerprint':fingerprint,'status':'queued','error':None,'created':now,'metrics':{}}
            service.jobs[id]=job
            try:service.persist() # Intent is durable before computation.
            except OSError:
                service.journal_ready=False
                job.update(status='failed',error='journal_unavailable')
                raise HTTPException(503,'journal_unavailable') from None
            service.active=id;budget.append(now)
            options={'category':garments[0]['category'],'garment_photo_type':photo,'num_timesteps':steps,'seed':seed}
            service.tasks[id]=asyncio.create_task(service.run(job,person,garment,options))
            return service.view(job)
        finally:service.uploads-=1

    @app.get('/api/local-vton/jobs/{id}')
    async def status(id:str,request:Request):return service.view(service.owned(id,service.authenticate(request)))

    @app.get('/api/local-vton/jobs/{id}/result')
    async def result(id:str,request:Request):
        service.owned(id,service.authenticate(request))
        if id not in service.results:raise HTTPException(410,'result_unavailable')
        return Response(service.results[id][0],media_type='image/png',headers={'Cache-Control':'no-store'})

    @app.delete('/api/local-vton/jobs/{id}')
    async def cancel(id:str,request:Request):
        job=service.owned(id,service.authenticate(request));service.results.pop(id,None)
        job.update(status='cancelled',error='cancelled')
        if id in service.tasks:
            task=service.tasks[id];task.cancel()
            await asyncio.gather(task,return_exceptions=True)
            service.tasks.pop(id,None)
            if service.active==id:service.active=None
        service.persist()
        return service.view(job)
    return app


app=create_app()
