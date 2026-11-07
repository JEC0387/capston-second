"""Authenticated, bounded, opt-in FASHN proxy. No application DB or photo files.

The private journal records intent BEFORE a paid POST. Unknown outcomes are never
resubmitted, including after restart. One Unix worker owns the journal lease.
"""
import asyncio
import base64
import hashlib
import hmac
import io
import json
import os
import re
import time
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib import request as urlrequest, error as urlerror
from uuid import UUID

from fastapi import APIRouter, HTTPException, Request, Response
from PIL import Image, ImageOps

MAX_IMAGE = 5 * 1024 * 1024
MAX_BODY = 22 * 1024 * 1024
TERMINAL = {"completed", "failed", "timeout", "cancelled", "interrupted"}


class ProviderFailure(Exception):
    def __init__(self, code):
        self.code = code


def normalize_image(value, *, max_side=1296, quality=90):
    """Only inline images, not client URLs (no SSRF); strip EXIF and white-fill alpha."""
    if not isinstance(value, str) or not value.startswith(("data:image/jpeg;base64,", "data:image/png;base64,", "data:image/webp;base64,")):
        raise ValueError("image_format")
    encoded = value.split(",", 1)[1]
    if len(encoded) > (MAX_IMAGE * 4 // 3 + 8):
        raise ValueError("image_size")
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > MAX_IMAGE:
        raise ValueError("image_size")
    with Image.open(io.BytesIO(raw)) as opened:
        if getattr(opened, "n_frames", 1) != 1:
            raise ValueError("animated_image")
        if opened.width * opened.height > 20_000_000 or min(opened.size) < 32:
            raise ValueError("image_dimensions")
        im = ImageOps.exif_transpose(opened)
        im.thumbnail((max_side, max_side))
        rgba = im.convert("RGBA")
        rgb = Image.new("RGB", rgba.size, "white")
        rgb.paste(rgba, mask=rgba.getchannel("A"))
        out = io.BytesIO()
        rgb.save(out, "JPEG", quality=quality)
    return "data:image/jpeg;base64," + base64.b64encode(out.getvalue()).decode("ascii")


class NoRedirect(urlrequest.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class FashnProvider:
    def __init__(self, key):
        self.key = key
        self.opener = urlrequest.build_opener(NoRedirect())

    def _request(self, path, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        req = urlrequest.Request("https://api.fashn.ai/v1/" + path, data=data,
                                 headers={"Authorization": "Bearer " + self.key, "Content-Type": "application/json"})
        try:
            with self.opener.open(req, timeout=25) as response:
                raw = response.read(20 * 1024 * 1024 + 1)
            if len(raw) > 20 * 1024 * 1024:
                raise ProviderFailure("invalid_result")
            parsed = json.loads(raw)
            if parsed.get("error"):
                error = str(parsed["error"])
                raise ProviderFailure("pose" if "PoseError" in error else "provider_failed")
            return parsed
        except urlerror.HTTPError as exc:
            raise ProviderFailure({401: "provider_auth", 402: "credit", 429: "provider_busy"}.get(exc.code, "provider_failed")) from None
        except (OSError, ValueError):
            # A timed-out POST might have reached FASHN. It must NEVER be retried.
            raise ProviderFailure("provider_connection_unknown") from None

    async def run(self, person, garment, category):
        body = {"model_name": "tryon-v1.6", "inputs": {
            "model_image": person, "garment_image": garment, "category": category,
            "mode": "quality", "num_samples": 1, "output_format": "jpeg",
            "garment_photo_type": "auto", "return_base64": True}}
        value = await asyncio.to_thread(self._request, "run", body)
        job_id = value.get("id", "")
        if not isinstance(job_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", job_id):
            raise ProviderFailure("provider_connection_unknown")
        return job_id

    async def status(self, job_id):
        return await asyncio.to_thread(self._request, "status/" + job_id)


@dataclass(frozen=True)
class Settings:
    key: str = ""
    hashes: tuple = ()
    journal: str = ""
    per_user: int = 0
    global_daily: int = 0
    enabled: bool = False

    @classmethod
    def environment(cls):
        try:
            return cls(os.getenv("FASHN_API_KEY", ""), tuple(filter(None, os.getenv("TRYON_CLIENT_TOKEN_HASHES", "").split(","))),
                       os.getenv("TRYON_JOURNAL_PATH", ""), int(os.getenv("TRYON_USER_DAILY_LIMIT", "0")),
                       int(os.getenv("TRYON_GLOBAL_DAILY_LIMIT", "0")), os.getenv("TRYON_ENABLED") == "true")
        except ValueError:
            return cls()


class TryOnService:
    def __init__(self, settings, provider=None, poll_seconds=3, timeout_seconds=180, result_ttl=1800):
        self.settings, self.provider = settings, provider or FashnProvider(settings.key)
        self.poll_seconds, self.timeout_seconds, self.result_ttl = poll_seconds, timeout_seconds, result_ttl
        self.tasks, self.results, self.rates = {}, {}, defaultdict(deque)
        self.uploads = 0
        self.lease, self.problem = None, "not_configured"
        self.data = {"jobs": {}, "days": {}}
        if not (settings.enabled and settings.key and settings.journal and settings.per_user > 0 and settings.global_daily > 0
                and settings.hashes and all(re.fullmatch(r"[a-f0-9]{64}", v) for v in settings.hashes)):
            return
        try:
            import fcntl  # Unix deployment; unsupported systems stay disabled.
            if not Path(settings.journal).is_absolute():
                raise ValueError()
            path = Path(settings.journal).resolve()
            if path.name in ("", ".") or not path.is_absolute():
                raise ValueError()
            path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            self.path = path
            self.lease = open(str(path) + ".lock", "a+b")
            os.chmod(self.lease.name, 0o600)
            fcntl.flock(self.lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if path.exists():
                self.data = json.loads(path.read_text())
            # Persisted requests are never replayed. Photos/results are RAM-only.
            for job in self.data["jobs"].values():
                if job["status"] not in TERMINAL:
                    job.update(status="interrupted", error="server_restarted")
            self._persist()
            self.problem = None
        except Exception:
            self.problem = "journal_unavailable"
            if self.lease:
                self.lease.close()
                self.lease = None

    def _persist(self):
        temporary = str(self.path) + ".tmp"
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "w") as output:
            json.dump(self.data, output, separators=(",", ":"))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, self.path)
        directory = os.open(self.path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)

    def commit(self):
        try:
            self._persist()
        except Exception:
            self.problem = "journal_unavailable"
            raise ProviderFailure("journal_unavailable") from None

    def authenticate(self, request):
        if self.problem:
            raise HTTPException(503, self.problem)
        # Trust only the ASGI scheme set by a correctly configured TLS proxy.
        if request.url.scheme != "https":
            raise HTTPException(403, "https_required")
        auth = request.headers.get("authorization", "")
        token = auth[7:] if auth.startswith("Bearer ") else ""
        digest = hashlib.sha256(token.encode()).hexdigest()
        if len(token) < 32 or not any(hmac.compare_digest(digest, allowed) for allowed in self.settings.hashes):
            raise HTTPException(401, "unauthorized", headers={"WWW-Authenticate": "Bearer"})
        # Polling has its own generous bound. Paid usage has persistent daily caps.
        now = time.monotonic()
        queue = self.rates[digest]
        while queue and queue[0] < now - 60:
            queue.popleft()
        if len(queue) >= 90:
            raise HTTPException(429, "rate_limit")
        queue.append(now)
        self.purge_results()
        return digest

    def purge_results(self):
        now = time.time()
        for key, (_, expiry) in list(self.results.items()):
            if expiry <= now:
                self.results.pop(key, None)

    def owned(self, job_id, owner):
        job = self.data["jobs"].get(job_id)
        if not job or not hmac.compare_digest(job["owner"], owner):
            raise HTTPException(404, "not_found")
        return job

    def view(self, job):
        return {k: job[k] for k in ("id", "status", "error", "step", "count") } | {"result_available": job["id"] in self.results}

    def submit(self, owner, job_id, person, garments, fingerprint):
        try:
            if str(UUID(job_id)) != job_id:
                raise ValueError()
        except (ValueError, TypeError, AttributeError):
            raise HTTPException(422, "invalid_request_id") from None
        existing = self.data["jobs"].get(job_id)
        if existing:
            self.owned(job_id, owner)
            if not hmac.compare_digest(existing["fingerprint"], fingerprint):
                raise HTTPException(409, "idempotency_conflict")
            return self.view(existing)
        if len(self.data["jobs"]) >= 10000:
            raise HTTPException(503, "journal_capacity")  # never silently forget paid intents
        if sum(j["status"] not in TERMINAL for j in self.data["jobs"].values()) >= 2:
            raise HTTPException(429, "busy")
        if any(j["owner"] == owner and j["status"] not in TERMINAL for j in self.data["jobs"].values()):
            raise HTTPException(409, "active_job")
        day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        usage = self.data["days"].setdefault(day, {"total": 0, "users": {}})
        count = len(garments)
        if usage["total"] + count > self.settings.global_daily or usage["users"].get(owner, 0) + count > self.settings.per_user:
            raise HTTPException(429, "daily_limit")
        usage["total"] += count
        usage["users"][owner] = usage["users"].get(owner, 0) + count
        job = {"id": job_id, "owner": owner, "fingerprint": fingerprint, "status": "queued",
               "error": None, "step": 0, "count": count, "cancel": False, "created": time.time()}
        self.data["jobs"][job_id] = job
        self.commit()  # reservation is durable before scheduling any paid operation
        self.tasks[job_id] = asyncio.create_task(self.generate(job, person, garments))
        self.tasks[job_id].add_done_callback(lambda _: self.tasks.pop(job_id, None))
        return self.view(job)

    async def generate(self, job, person, garments):
        try:
            current = person
            for index, garment in enumerate(garments):
                if job["cancel"]:
                    job.update(status="cancelled", error="cancelled")
                    return
                job.update(status="submitting", step=index + 1)
                self.commit()  # records intent before POST; no automatic retry even if ID is lost
                provider_id = await self.provider.run(current, garment["image"], garment["category"])
                job.update(status="processing", provider_id=provider_id)
                self.commit()
                deadline = time.monotonic() + self.timeout_seconds
                while True:
                    if job["cancel"]:
                        job.update(status="cancelled", error="cancelled")
                        return
                    if time.monotonic() >= deadline:
                        raise ProviderFailure("timeout")
                    await asyncio.sleep(self.poll_seconds)
                    status = await self.provider.status(provider_id)
                    state = status.get("status")
                    if state == "completed":
                        output = status.get("output")
                        if not isinstance(output, list) or not output:
                            raise ProviderFailure("invalid_result")
                        # API return_base64 avoids exposing a public CDN URL. Never fetch arbitrary URLs.
                        try:
                            # Preserve more output detail for comparison and a second garment.
                            # This removes unnecessary recompression loss; it cannot repair model anatomy.
                            current = await asyncio.to_thread(normalize_image, output[0], max_side=2048, quality=95)
                        except Exception:
                            raise ProviderFailure("invalid_result") from None
                        break
                    if state not in ("starting", "in_queue", "processing"):
                        reason = str(status.get("error", ""))
                        raise ProviderFailure("pose" if "PoseError" in reason else "provider_failed")
            if job["cancel"]:
                job.update(status="cancelled", error="cancelled")
            else:
                self.results[job["id"]] = (base64.b64decode(current.split(",", 1)[1]), time.time() + self.result_ttl)
                asyncio.get_running_loop().call_later(self.result_ttl, self.results.pop, job["id"], None)
                job.update(status="completed", error=None)
        except asyncio.CancelledError:
            job.update(status="interrupted", error="server_restarted")
            raise
        except ProviderFailure as exc:
            job.update(status="timeout" if exc.code == "timeout" else "failed", error=exc.code)
        except Exception:
            job.update(status="failed", error="internal")
        finally:
            try:
                self.commit()
            except ProviderFailure:
                pass  # fail closed for all future requests

    async def close(self):
        tasks = list(self.tasks.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        self.results.clear()
        if self.lease:
            self.lease.close()
            self.lease = None


_service = None


def service():
    global _service
    if _service is None:
        _service = TryOnService(Settings.environment())
    return _service


async def close_service():
    global _service
    if _service:
        await _service.close()
        _service = None


def create_router(get_service=service):
    router = APIRouter(prefix="/api/tryon", tags=["AI 가상피팅"])

    @router.get("/config")
    async def config(request: Request):
        current = get_service()
        current.authenticate(request)
        return {"configured": True, "model": "tryon-v1.6", "generation_mode": "quality",
                "per_user_daily_limit": current.settings.per_user,
                "visual_quality_validated": False, "max_input_side": 1296, "max_result_side": 2048}

    @router.post("/jobs", status_code=202)
    async def submit(request: Request):
        current = get_service()
        owner = current.authenticate(request)  # authenticate BEFORE reading photos
        if current.uploads >= 2:
            raise HTTPException(429, "busy")
        current.uploads += 1
        try:
            raw = bytearray()
            async for chunk in request.stream():
                if len(raw) + len(chunk) > MAX_BODY:
                    raise HTTPException(413, "image_size")
                raw.extend(chunk)
            try:
                body = json.loads(raw)
                if set(body) != {"request_id", "person", "garments", "consent"} or body["consent"] is not True:
                    raise ValueError()
                garments = body["garments"]
                if not isinstance(garments, list) or not 1 <= len(garments) <= 2:
                    raise ValueError()
                cats = [g["category"] for g in garments]
                if any(c not in ("tops", "bottoms", "one-pieces") for c in cats) or len(set(cats)) != len(cats) or ("one-pieces" in cats and len(cats) != 1):
                    raise ValueError()
                if any(set(g) != {"image", "category"} for g in garments):
                    raise ValueError()
                canonical = json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
                fingerprint = hashlib.sha256(canonical).hexdigest()
                # Duplicates can be answered without decoding images again.
                if body["request_id"] in current.data["jobs"]:
                    return current.submit(owner, body["request_id"], "", garments, fingerprint)
                person = await asyncio.to_thread(normalize_image, body["person"])
                normalized = [{"category": g["category"], "image": await asyncio.to_thread(normalize_image, g["image"])}
                              for g in sorted(garments, key=lambda g: g["category"] == "bottoms")]
                return current.submit(owner, body["request_id"], person, normalized, fingerprint)
            except (ValueError, KeyError, TypeError, AttributeError, OSError, Image.DecompressionBombError):
                raise HTTPException(422, "invalid_input") from None
            except ProviderFailure:
                raise HTTPException(503, "journal_unavailable") from None
        finally:
            current.uploads -= 1

    @router.get("/jobs/{job_id}")
    async def status(job_id: str, request: Request):
        current = get_service()
        owner = current.authenticate(request)
        return current.view(current.owned(job_id, owner))

    @router.delete("/jobs/{job_id}")
    async def cancel(job_id: str, request: Request):
        current = get_service()
        job = current.owned(job_id, current.authenticate(request))
        if job["status"] not in TERMINAL:
            job["cancel"] = True
            try:
                current.commit()
            except ProviderFailure:
                raise HTTPException(503, "journal_unavailable") from None
        return current.view(job)

    @router.get("/jobs/{job_id}/result")
    async def result(job_id: str, request: Request):
        current = get_service()
        current.owned(job_id, current.authenticate(request))
        value = current.results.get(job_id)
        if not value:
            raise HTTPException(410, "result_expired")
        return Response(value[0], media_type="image/jpeg", headers={"Cache-Control": "no-store"})

    return router
