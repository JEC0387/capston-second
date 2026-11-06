"""Opt-in three-garment server contract. No research Space, paid fallback or mock engine.

Uses the existing account dependency; deploy behind an approved same-origin HTTPS
proxy. Stored job tombstones are never silently removed/refunded or resubmitted.
"""
from __future__ import annotations

import asyncio
import base64
from collections import deque
from contextlib import asynccontextmanager
from dataclasses import dataclass
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import stat
import time
from uuid import UUID

from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request, Response
from PIL import Image, ImageOps

from auth_api import current_user
from outfit_vton_provider import ProviderFailure

ENGINE = "fashionai-outfit-vton-v1"
ROLES = ("tops", "bottoms", "shoes")
MAX_IMAGE = 5 * 1024 * 1024
MAX_BODY = 29 * 1024 * 1024
MAX_RESULT = 8 * 1024 * 1024
SHA = re.compile(r"[a-f0-9]{64}\Z")
CODE = re.compile(r"[a-z][a-z0-9_]{0,63}\Z")
TERMINAL = {"completed", "failed", "cancelled", "timeout", "interrupted", "expired"}


def safe_code(value, fallback="engine_unavailable"):
    return value if isinstance(value, str) and CODE.fullmatch(value) else fallback


def verified_provider_info(value):
    """Public contract disclosure only, not provider credentials or raw errors."""
    expected = {"name", "execution_location", "upstream_model", "mode", "max_credits_per_job",
                "remote_cancellation_supported", "remote_retention_days", "notice"}
    if (not isinstance(value, dict) or set(value) != expected or
            value["name"] != "genlook" or value["execution_location"] != "third_party" or
            value["upstream_model"] != "not_disclosed" or value["mode"] != "sequential-3" or
            type(value["max_credits_per_job"]) is not int or value["max_credits_per_job"] != 3 or
            value["remote_cancellation_supported"] is not False or
            type(value["remote_retention_days"]) is not int or value["remote_retention_days"] != 1 or
            not isinstance(value["notice"], str) or not 1 <= len(value["notice"]) <= 1000):
        raise ValueError("invalid_provider_disclosure")
    return dict(value)


def verified_provider_source(value, model_id):
    """Persist a bounded receipt; never signed URLs, raw API responses or guessed weights."""
    if not model_id.startswith("genlook/"):
        if value is not None:
            raise ValueError("unexpected_provider_source")
        return None
    expected = {"provider", "service", "upstream_model", "generation_ids", "mode", "credits_reserved",
                "credits_balance_before", "credits_balance_after", "quality"}
    if (not isinstance(value, dict) or set(value) != expected or value["provider"] != "genlook" or
            value["service"] != "tryon/v1" or value["upstream_model"] != "not_disclosed" or
            value["mode"] != "sequential-3" or value["quality"] != "NOT_EVALUATED" or
            type(value["credits_reserved"]) is not int or value["credits_reserved"] != 3):
        raise ValueError("invalid_provider_source")
    ids = value["generation_ids"]
    if (not isinstance(ids, list) or len(ids) != 3 or
            any(not isinstance(identity, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", identity) for identity in ids) or
            len(set(ids)) != 3 or any(value[key] is not None and
                (type(value[key]) is not int or not 0 <= value[key] <= 10**9)
                for key in ("credits_balance_before", "credits_balance_after"))):
        raise ValueError("invalid_provider_source")
    return value | {"generation_ids": list(ids)}


@dataclass(frozen=True)
class OutfitSettings:
    enabled: bool = False
    deployment_approved: bool = False
    policy_version: str = ""
    retention_seconds: int = 0  # Operator's agreed policy, not a legal default.
    storage: str = ""
    timeout_seconds: float = 240
    max_jobs_per_day: int = 2
    max_records: int = 10000
    allow_loopback_http: bool = False

    @classmethod
    def environment(cls):
        def number(name, default, parser=int):
            try:
                return parser(os.getenv(name, str(default)))
            except ValueError:
                return 0
        return cls(enabled=os.getenv("OUTFIT_VTON_ENABLED") == "true",
                   deployment_approved=os.getenv("OUTFIT_VTON_DEPLOYMENT_APPROVED") == "true",
                   policy_version=os.getenv("OUTFIT_VTON_POLICY_VERSION", ""),
                   retention_seconds=number("OUTFIT_VTON_RETENTION_SECONDS", 0),
                   storage=os.getenv("OUTFIT_VTON_STORAGE", ""),
                   timeout_seconds=number("OUTFIT_VTON_TIMEOUT_SECONDS", 240, float),
                   max_jobs_per_day=number("OUTFIT_VTON_MAX_JOBS_PER_DAY", 2),
                   allow_loopback_http=os.getenv("OUTFIT_VTON_LOOPBACK_HTTP") == "true")

    def issue(self):
        if not self.enabled:
            return "disabled"
        if not self.deployment_approved:
            return "deployment_not_approved"
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", self.policy_version):
            return "policy_not_configured"
        if type(self.retention_seconds) is not int or not 1 <= self.retention_seconds <= 86400:
            return "retention_not_configured"
        if (not math.isfinite(self.timeout_seconds) or not 0 < self.timeout_seconds <= 1800 or
                type(self.max_jobs_per_day) is not int or not 1 <= self.max_jobs_per_day <= 20 or
                type(self.max_records) is not int or not 1 <= self.max_records <= 10000):
            return "unsafe_resource_limits"
        if not self.storage:
            return "storage_not_configured"
        return None


def require_id(value):
    try:
        if not isinstance(value, str) or str(UUID(value)) != value:
            raise ValueError()
        return value
    except (ValueError, AttributeError):
        raise HTTPException(422, "invalid_request_id") from None


def _keys(value, expected):
    if not isinstance(value, dict) or set(value) != set(expected):
        raise ValueError("invalid_request_schema")


def _bounded_number(value, low, high, exclusive=False):
    if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high or exclusive and value == low:
        raise ValueError("invalid_placement")
    return float(value)


def _canonical_image(raw: bytes, expected_format=None, minimum=128, result=False):
    if not raw or len(raw) > (MAX_RESULT if result else MAX_IMAGE):
        raise ValueError("image_size")
    with Image.open(io.BytesIO(raw)) as check:
        if check.format not in ("PNG", "JPEG", "WEBP") or expected_format and check.format != expected_format:
            raise ValueError("image_mime_mismatch")
        if getattr(check, "n_frames", 1) != 1 or min(check.size) < minimum or check.width * check.height > 20_000_000:
            raise ValueError("image_dimensions")
        check.verify()
    with Image.open(io.BytesIO(raw)) as opened:
        opened.load()
        image = ImageOps.exif_transpose(opened).convert("RGBA")
        if not result:
            image.thumbnail((2048, 2048))
        clean = Image.new("RGBA", image.size)
        clean.paste(image)
        out = io.BytesIO()
        clean.save(out, "PNG")
        value = out.getvalue()
        if len(value) > (MAX_RESULT if result else MAX_IMAGE):
            raise ValueError("image_size")
        return value


def decode_image(value, role):
    if not isinstance(value, str) or len(value) > MAX_IMAGE * 4 // 3 + 100:
        raise ValueError("image_size")
    match = re.fullmatch(r"data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", value)
    if not match:
        raise ValueError("image_format")
    raw = base64.b64decode(match[2], validate=True)
    return _canonical_image(raw, {"png": "PNG", "jpeg": "JPEG", "webp": "WEBP"}[match[1]],
                            minimum=256 if role == "person" else 128)


def parse_submission(raw, settings, availability):
    def reject_constant(_):
        raise ValueError("invalid_json_number")
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate_json_key")
            result[key] = value
        return result
    data = json.loads(raw.decode("utf-8"), parse_constant=reject_constant, object_pairs_hook=unique_pairs)
    _keys(data, ("request_id", "model_revision", "consent", "person", "garments"))
    require_id(data["request_id"])
    if data["model_revision"] != availability.model_revision:
        raise ValueError("model_revision_changed")
    _keys(data["consent"], ("accepted", "policy_version"))
    if data["consent"]["accepted"] is not True or data["consent"]["policy_version"] != settings.policy_version:
        raise ValueError("consent_required")
    garments = data["garments"]
    if not isinstance(garments, list) or len(garments) != 3:
        raise ValueError("all_three_garments_required")
    placements, sources, ids = {}, {}, set()
    for garment in garments:
        _keys(garment, ("id", "category", "image", "placement"))
        role = garment["category"]
        identity = garment["id"]
        if role not in ROLES or role in placements:
            raise ValueError("all_three_garments_required")
        if not isinstance(identity, str) or not 1 <= len(identity) <= 512 or identity in ids:
            raise ValueError("invalid_garment_id")
        ids.add(identity)
        placement = garment["placement"]
        _keys(placement, ("center_x", "center_y", "width", "scale", "rotation"))
        placements[role] = {
            "center_x": _bounded_number(placement["center_x"], -2, 3),
            "center_y": _bounded_number(placement["center_y"], -2, 3),
            "width": _bounded_number(placement["width"], 0, 4, True),
            "scale": _bounded_number(placement["scale"], .05, 20),
            "rotation": _bounded_number(placement["rotation"], -36000, 36000),
        }
        sources[role] = garment["image"]
    # All schema/consent/category checks precede any image decoding.
    images = {"person": decode_image(data["person"], "person")}
    images.update({role: decode_image(sources[role], role) for role in ROLES})
    return data["request_id"], images, placements


class OutfitService:
    def __init__(self, settings=None, provider=None):
        from outfit_vton_provider import DisabledProvider
        self.settings = settings or OutfitSettings.environment()
        self.provider = provider or DisabledProvider()
        self.jobs = {}
        self.tasks = {}
        self.cancel_tasks = {}
        self.active = None
        self.lease = None
        self.root = None
        self.journal_ready = False
        self.uploads = 0
        self.upload_budgets = {}
        self.runtime_issue = None
        self.lock = asyncio.Lock()

    async def start(self):
        if self.settings.issue() is not None:
            return
        try:
            selected = Path(self.settings.storage).absolute()
            for part in (selected, *selected.parents):
                if part.exists():
                    info = part.lstat()
                    if part.is_symlink() or getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400):
                        raise ValueError()
            root = selected.resolve()
            # Never claim or clean an existing unowned folder.
            if Path(self.settings.storage).is_symlink():
                raise ValueError()
            marker = root / ".fashionai-outfit-owner"
            if root.exists():
                if marker.is_symlink() or not marker.is_file() or marker.read_text() != ENGINE:
                    raise ValueError()
            else:
                root.mkdir(parents=True, mode=0o700)
                marker.write_text(ENGINE)
                os.chmod(marker, 0o600)
            self.root = root
            if (root / "service.lock").is_symlink():
                raise ValueError()
            self.lease = (root / "service.lock").open("a+b")
            self.lease.seek(0); self.lease.write(b"0"); self.lease.flush(); self.lease.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self.lease.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            journal = root / "jobs.json"
            if journal.exists():
                if journal.is_symlink() or journal.stat().st_size > 12 * 1024 * 1024:
                    raise ValueError()
                self.jobs = json.loads(journal.read_text())
                if not isinstance(self.jobs, dict) or len(self.jobs) > self.settings.max_records:
                    raise ValueError()
                for identity, job in self.jobs.items():
                    require_id(identity)
                    expected = {"request_id", "owner", "input_sha256", "model_id", "model_revision", "model_sha256",
                                "policy_version", "quality", "status", "error", "progress", "result_sha256",
                                "result_available", "expires_at", "created_at"}
                    if not isinstance(job, dict) or set(job) not in (expected, expected | {"source"}):
                        raise ValueError()
                    if (job["request_id"] != identity or not SHA.fullmatch(job["owner"]) or
                            not SHA.fullmatch(job["input_sha256"]) or not SHA.fullmatch(job["model_sha256"]) or
                            job["status"] not in TERMINAL | {"queued", "running", "cancelling"} or
                            job["quality"] != "NOT_EVALUATED" or type(job["result_available"]) is not bool or
                            any(type(job[key]) not in (int, float) or not math.isfinite(job[key]) or job[key] < 0
                                for key in ("expires_at", "created_at", "progress"))):
                        raise ValueError()
                    if job["status"] not in TERMINAL:
                        job.update(status="interrupted", error="server_restarted", result_available=False)
                    if job["error"] in ("worker_stop_unconfirmed", "remote_execution_unconfirmed"):
                        self.runtime_issue = job["error"]
                    if job["status"] == "completed":
                        verified_provider_source(job.get("source"), job["model_id"])
                        self._read_result(job)  # Validate before exposing a restored completion.
                    elif job.get("source") is not None:
                        verified_provider_source(job["source"], job["model_id"])
                        self._erase_result(job)
                    else:
                        self._erase_result(job)  # Interrupted workers cannot leave an orphan PNG.
            elif any(root.glob("*.png")):
                # Missing journal cannot turn prior outputs into a fresh allowance.
                raise ValueError()
            self.journal_ready = True
            self.purge()
            self.persist()
            if self.runtime_issue is None:
                await self.provider.initialize()
        except Exception:
            self.journal_ready = False
            if self.lease:
                self.lease.close(); self.lease = None

    def persist(self):
        if self.root is None:
            raise OSError("storage_unavailable")
        target = self.root / "jobs.json"
        temporary = self.root / "jobs.next"
        if target.is_symlink() or temporary.is_symlink():
            raise OSError("storage_unavailable")
        with temporary.open("w", encoding="utf-8") as out:
            os.chmod(temporary, 0o600)
            json.dump(self.jobs, out, separators=(",", ":"), allow_nan=False)
            out.flush(); os.fsync(out.fileno())
        os.replace(temporary, target)

    def store_state(self):
        try:
            if not self.journal_ready:
                raise OSError()
            self.persist()
        except OSError:
            self.journal_ready = False
            raise HTTPException(503, "journal_unavailable") from None

    def transport(self, request):
        loopback = (self.settings.allow_loopback_http and request.url.scheme == "http" and
                    request.client and request.client.host in ("127.0.0.1", "::1") and
                    request.url.hostname in ("127.0.0.1", "localhost", "::1"))
        if request.url.scheme != "https" and not loopback:
            raise HTTPException(403, "https_required")

    async def availability(self):
        issue = self.settings.issue()
        if issue or self.runtime_issue:
            # Deployment approval gates even model inspection/loading, not only POST.
            from outfit_vton_provider import ProviderAvailability
            return ProviderAvailability(), issue or self.runtime_issue
        try:
            available = await self.provider.check_availability()
        except Exception:
            from outfit_vton_provider import DisabledProvider
            available = await DisabledProvider().check_availability()
            issue = issue or "engine_unavailable"
        issue = issue or (None if self.journal_ready else "journal_unavailable")
        if not available.ready:
            issue = issue or safe_code(available.reason)
        elif (not SHA.fullmatch(available.model_sha256) or not available.model_id or
              not available.model_revision or set(available.roles) != set(ROLES)):
            issue = issue or "invalid_model_contract"
        if available.model_id.startswith("genlook/"):
            try:
                verified_provider_info(available.provider_info)
            except (ValueError, AttributeError):
                issue = issue or "invalid_model_contract"
        return available, issue

    def upload_attempt(self, owner):
        """Bound invalid authenticated requests before decoding. No photo/body retention.

        This short abuse window is process-local; durable daily generation limits
        remain in the job journal and are not refunded by cancellation/restart.
        """
        now = time.monotonic()
        for identity, attempts in list(self.upload_budgets.items()):
            while attempts and attempts[0] <= now-60:
                attempts.popleft()
            if not attempts:
                self.upload_budgets.pop(identity, None)
        if owner not in self.upload_budgets and len(self.upload_budgets) >= 1000:
            raise HTTPException(429, "upload_budget_busy")
        attempts = self.upload_budgets.setdefault(owner, deque())
        if len(attempts) >= 12:
            raise HTTPException(429, "upload_rate_limited", headers={"Retry-After": "60"})
        attempts.append(now)

    def path(self, identity):
        require_id(identity)
        target = self.root / (identity + ".png")
        if target.is_symlink() or target.resolve().parent != self.root:
            raise ValueError("unsafe_result")
        return target

    def _erase_result(self, job):
        try:
            self.path(job["request_id"]).unlink(missing_ok=True)
        except OSError:
            self.journal_ready = False
            raise
        job.update(result_available=False, result_sha256=None)

    def _read_result(self, job):
        try:
            target = self.path(job["request_id"])
            if target.stat().st_size > MAX_RESULT:
                raise ValueError()
            raw = target.read_bytes()
            _canonical_image(raw, "PNG", minimum=32, result=True)
            if hashlib.sha256(raw).hexdigest() != job["result_sha256"]:
                raise ValueError()
            return raw
        except (OSError, ValueError, SyntaxError):
            job.update(status="expired", error="result_unavailable", result_available=False, result_sha256=None)
            self._erase_result(job)
            return None

    def purge(self):
        now = time.time()
        changed = False
        for job in self.jobs.values():
            if job["status"] == "completed" and job["expires_at"] <= now:
                self._erase_result(job)
                job.update(status="expired", error="result_expired")
                changed = True
        if changed:
            self.store_state()

    def owned(self, identity, owner):
        if not self.journal_ready:
            raise HTTPException(503, "journal_unavailable")
        require_id(identity)
        self.purge()
        job = self.jobs.get(identity)
        if job is None or job["owner"] != owner:
            raise HTTPException(404, "job_not_found")
        return job

    def view(self, job):
        return {k: v for k, v in job.items() if k != "owner"} | {"engine": ENGINE}

    async def execute(self, job, images, placements, available):
        worker = None
        output = None
        timed_out = False
        cancelled = False
        failed = False
        failure_code = "generation_failed"
        cleanup_failed = False
        cleanup_code = "worker_stop_unconfirmed"
        try:
            job.update(status="running", progress=10)
            self.store_state()
            worker = asyncio.create_task(self.provider.generate(job["request_id"], job["input_sha256"], images, placements))
            output = await asyncio.wait_for(asyncio.shield(worker), self.settings.timeout_seconds)
            if (output.job_id != job["request_id"] or output.input_sha256 != job["input_sha256"] or
                    output.model_revision != available.model_revision or output.model_sha256 != available.model_sha256):
                raise ValueError("result_identity_mismatch")
            source = verified_provider_source(output.source, available.model_id)
            result = _canonical_image(output.png, "PNG", minimum=32, result=True)
            if any(result == image for image in images.values()):
                raise ValueError("input_is_not_generated_result")
            target = self.path(job["request_id"])
            # UUID reservation prevents overwriting an earlier result.
            with target.open("xb") as stream:
                os.chmod(target, 0o600)
                stream.write(result); stream.flush(); os.fsync(stream.fileno())
            job.update(status="completed", error=None, progress=100, result_available=True,
                       result_sha256=hashlib.sha256(result).hexdigest(), expires_at=time.time()+self.settings.retention_seconds)
            if source is not None:
                job["source"] = source
        except asyncio.TimeoutError:
            timed_out = True
            job.update(status="cancelling", error="generation_timeout", result_available=False)
        except asyncio.CancelledError:
            cancelled = True
            job.update(status="cancelling", error="cancel_requested", result_available=False)
        except Exception as failure:
            failed = True
            if isinstance(failure, ProviderFailure):
                # Re-validate the provider's registered code; never expose an
                # arbitrary exception message or another type's code attribute.
                failure_code = ProviderFailure(failure.code).code
            job.update(status="cancelling", error=failure_code, result_available=False)
        finally:
            failed_before_cleanup = (worker is not None and worker.done() and not worker.cancelled()
                                     and worker.exception() is not None)
            if worker is not None and (not worker.done() or failed_before_cleanup or worker.cancelled()):
                if not worker.done():
                    worker.cancel()
                async def join():
                    nonlocal cleanup_code
                    failed = False
                    try:
                        await self.provider.cancel(job["request_id"])
                    except ProviderFailure as failure:
                        failed = True
                        if failure.code == "remote_execution_unconfirmed":
                            cleanup_code = failure.code
                    except Exception:
                        failed = True
                    outcomes = await asyncio.gather(worker, return_exceptions=True)
                    # A model error already returned by a stopped worker is not
                    # itself an unconfirmed cancellation. The provider still
                    # must confirm no owned native child remains after failure.
                    return failed or (not failed_before_cleanup and any(isinstance(value, BaseException) and
                        not isinstance(value, asyncio.CancelledError) for value in outcomes)
                    )
                cleanup = asyncio.create_task(join())
                while not cleanup.done():
                    try:
                        await asyncio.shield(cleanup)
                    except asyncio.CancelledError:
                        pass
                outcomes = await asyncio.gather(cleanup, return_exceptions=True)
                cleanup_failed = outcomes != [False]
            images.clear()
            if cleanup_failed:
                job.update(status="interrupted", error=cleanup_code, result_available=False)
                self.runtime_issue = cleanup_code
            elif timed_out:
                job.update(status="timeout", error="generation_timeout")
            elif cancelled:
                job.update(status="cancelled", error="cancelled")
            elif failed:
                job.update(status="failed", error=failure_code)
            if job["status"] != "completed":
                try:
                    self._erase_result(job)
                except (OSError, ValueError):
                    self.journal_ready = False
            try:
                self.store_state()
            except HTTPException:
                job.update(status="failed", error="journal_unavailable", result_available=False)
                try:
                    self._erase_result(job)
                except (OSError, ValueError):
                    job.update(error="result_cleanup_failed")
            self.active = job["request_id"] if cleanup_failed else None
            self.tasks.pop(job["request_id"], None)

    async def cancel(self, identity):
        existing = self.cancel_tasks.get(identity)
        if existing is None:
            task = self.tasks.get(identity)
            if task is None:
                return
            task.cancel()
            async def finish():
                await asyncio.gather(task, return_exceptions=True)
                job = self.jobs[identity]
                if job["status"] in ("queued", "cancelling"):
                    job.update(status="cancelled", error="cancelled", result_available=False)
                    self.store_state()
                self.tasks.pop(identity, None)
                if self.active == identity and self.runtime_issue is None:
                    self.active = None
                self.cancel_tasks.pop(identity, None)
            existing = asyncio.create_task(finish())
            self.cancel_tasks[identity] = existing
        await asyncio.shield(existing)

    async def close(self):
        await asyncio.gather(*(self.cancel(identity) for identity in list(self.tasks)), return_exceptions=True)
        try:
            close_provider = getattr(self.provider, "close", None)
            if close_provider is not None:
                await close_provider()
        finally:
            if self.lease:
                self.lease.close(); self.lease = None


def create_router(service: OutfitService, auth_dependency=current_user):
    router = APIRouter(prefix="/api/outfit-vton", tags=["Approved three-garment generation"])
    def owner(user):
        return hashlib.sha256(("account:" + str(user.id)).encode()).hexdigest()

    @router.get("/config")
    async def config(request: Request, user=Depends(auth_dependency)):
        service.transport(request)
        available, issue = await service.availability()
        result = {"engine": ENGINE, "ready": issue is None, "reason": issue,
                "model_id": available.model_id, "model_revision": available.model_revision,
                "model_sha256": available.model_sha256, "policy_version": service.settings.policy_version,
                "retention_seconds": service.settings.retention_seconds, "max_image_bytes": MAX_IMAGE,
                "max_body_bytes": MAX_BODY, "roles": list(ROLES)}
        if available.model_id.startswith("genlook/"):
            try:
                result["provider_info"] = verified_provider_info(available.provider_info)
            except (ValueError, AttributeError):
                result.update(ready=False, reason="invalid_model_contract")
        return result

    @router.post("/jobs", status_code=202)
    async def submit(request: Request, user=Depends(auth_dependency)):
        service.transport(request)
        available, issue = await service.availability()
        if issue:
            raise HTTPException(503, issue)  # Authenticate/readiness before image bytes.
        if service.uploads >= 2:
            raise HTTPException(429, "upload_busy")
        if request.headers.get("content-type", "").split(";", 1)[0].lower() != "application/json":
            raise HTTPException(415, "json_required")
        key = require_id(request.headers.get("idempotency-key"))
        account = owner(user)
        service.upload_attempt(account)
        service.uploads += 1
        try:
            async def bounded_body():
                data = bytearray()
                async for chunk in request.stream():
                    if len(chunk) > MAX_BODY - len(data):
                        raise HTTPException(413, "request_too_large")
                    data.extend(chunk)
                return bytes(data)
            raw = await asyncio.wait_for(bounded_body(), 30)
            fingerprint = hashlib.sha256(raw).hexdigest()
            async with service.lock:
                previous = service.jobs.get(key)
                if previous is not None:
                    if previous["owner"] != account:
                        raise HTTPException(404, "job_not_found")
                    if previous["input_sha256"] != fingerprint:
                        raise HTTPException(409, "idempotency_conflict")
                    service.purge()
                    return service.view(previous)
                if service.active is not None:
                    raise HTTPException(429, "engine_busy")
                if len(service.jobs) >= service.settings.max_records:
                    raise HTTPException(503, "journal_capacity")
                now = time.time()
                if sum(j["owner"] == account and j["created_at"] > now-86400 for j in service.jobs.values()) >= service.settings.max_jobs_per_day:
                    raise HTTPException(429, "usage_limit")
                try:
                    identity, images, placements = parse_submission(raw, service.settings, available)
                except HTTPException:
                    raise
                except Exception:
                    raise HTTPException(422, "invalid_images_or_request") from None
                if identity != key:
                    raise HTTPException(409, "idempotency_conflict")
                job = dict(request_id=identity, owner=account, input_sha256=fingerprint,
                           model_id=available.model_id, model_revision=available.model_revision,
                           model_sha256=available.model_sha256, policy_version=service.settings.policy_version,
                           quality="NOT_EVALUATED", status="queued", error=None,
                           progress=0, result_sha256=None, result_available=False,
                           expires_at=now+service.settings.timeout_seconds+service.settings.retention_seconds,
                           created_at=now)
                service.jobs[identity] = job
                service.store_state()  # Durable reservation before scheduling real inference.
                service.active = identity
                service.tasks[identity] = asyncio.create_task(service.execute(job, images, placements, available))
                return service.view(job)
        except asyncio.TimeoutError:
            raise HTTPException(408, "upload_timeout") from None
        finally:
            service.uploads -= 1

    @router.get("/jobs/{identity}")
    async def status(identity: str, request: Request, user=Depends(auth_dependency)):
        service.transport(request)
        return service.view(service.owned(identity, owner(user)))

    @router.get("/jobs/{identity}/result")
    async def result(identity: str, request: Request, user=Depends(auth_dependency)):
        service.transport(request)
        job = service.owned(identity, owner(user))
        if job["status"] != "completed" or not job["result_available"]:
            raise HTTPException(409, "result_not_available")
        raw = service._read_result(job)
        if raw is None:
            service.store_state()
            raise HTTPException(410, "result_unavailable")
        return Response(raw, media_type="image/png", headers={
            "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
            "X-Request-Id": identity, "X-Input-SHA256": job["input_sha256"],
            "X-Model-Revision": job["model_revision"], "X-Result-SHA256": job["result_sha256"]})

    @router.delete("/jobs/{identity}")
    async def cancel(identity: str, request: Request, user=Depends(auth_dependency)):
        service.transport(request)
        job = service.owned(identity, owner(user))
        if job["status"] == "completed":
            service._erase_result(job)
            job.update(status="cancelled", error="cancelled")
            service.store_state()
        elif identity in service.tasks:
            job.update(status="cancelling", error="cancel_requested", result_available=False)
            service.store_state()
            await service.cancel(identity)
        return service.view(job)
    return router


def create_app(settings=None, provider=None, auth_dependency=current_user):
    service = OutfitService(settings, provider)
    @asynccontextmanager
    async def lifespan(app):
        await service.start()
        async def cleaner():
            while True:
                await asyncio.sleep(1)
                try:
                    service.purge()
                except Exception:
                    service.journal_ready = False
        cleanup = asyncio.create_task(cleaner())
        try:
            yield
        finally:
            cleanup.cancel()
            await asyncio.gather(cleanup, return_exceptions=True)
            await service.close()
    app = FastAPI(title="FashionAI approved three-garment server", lifespan=lifespan)
    app.state.outfit_vton = service
    app.include_router(create_router(service, auth_dependency))
    @app.middleware("http")
    async def safe_responses(request, call_next):
        try:
            response = await call_next(request)
        except Exception:
            from fastapi.responses import JSONResponse
            response = JSONResponse(status_code=503, content={"detail": "service_unavailable"})
        response.headers["Cache-Control"] = "no-store"
        return response
    return app
