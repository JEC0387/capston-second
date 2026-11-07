"""Opt-in Genlook adapter. No signup, top-up, POST retry or mock fallback.

The external service does not document generation cancellation or idempotency.
Intent is therefore journaled before each paid POST. Ambiguous submissions block
the service for operator reconciliation; stopping polling is not remote cancel.
"""
from __future__ import annotations

import asyncio
from contextvars import ContextVar
from dataclasses import dataclass, field
from functools import wraps
import hashlib
import io
import json
import logging
import math
import os
from pathlib import Path
import re
import time
from urllib.parse import quote, urlsplit
from uuid import UUID, uuid4

from PIL import Image, ImageOps

from genlook_request_contract import (RequestStage, build_stage_body, review_plan,
                                      BASELINE_DETAIL_POLICY, detail_policy_manifest)
from outfit_vton_provider import ProviderAvailability, ProviderFailure, ProviderResult, _regular_local

API = "https://api.genlook.app/tryon/v1"
MODEL = "genlook/tryon-api-v1"
CONTRACT = "genlook-sequential-v2-preservation-20261106"
PAIRED_CONTRACT = "genlook-paired-v1-20261106"
SEQUENTIAL_MODE = "sequential-3"
PAIRED_MODE = "clothing-pair-then-shoes"
ROLES = ("tops", "bottoms", "shoes")
SAFE_ID = re.compile(r"[A-Za-z0-9_-]{1,128}\Z")
SHA = re.compile(r"[a-f0-9]{64}\Z")
MAX_IMAGE = 8 * 1024 * 1024
MAX_JSON = 256 * 1024
MARKER = ".fashionai-genlook-owned"
REVIEW_IMAGE_OWNER = ".intermediate-owner-v1.json"
STATES = {"reserved", "uploading", "submitting", "polling", "downloaded", "completed",
          "failed", "cancelled", "uncertain", "cleanup_failed"}
_PRIVATE_TRANSPORT = ContextVar("fashionai_genlook_private_transport", default=False)


def _install_transport_redaction():
    """Redact this task's HTTP-library records, including dynamic child loggers.

    A filter on the httpcore ancestor alone does not filter child logger records.
    The chained record factory leaves unrelated tasks and logging unchanged.
    """
    previous = logging.getLogRecordFactory()
    if getattr(previous, "_fashionai_genlook_redactor", False):
        return

    def factory(*args, **kwargs):
        record = previous(*args, **kwargs)
        if _PRIVATE_TRANSPORT.get() and (record.name == "httpx" or record.name.startswith("httpx.") or
                                         record.name == "httpcore" or record.name.startswith("httpcore.")):
            record.msg = "Genlook HTTP transport details redacted"
            record.args = ()
            record.exc_info = None
            record.exc_text = None
            record.stack_info = None
        return record

    factory._fashionai_genlook_redactor = True
    logging.setLogRecordFactory(factory)


def _private_transport(method):
    @wraps(method)
    async def guarded(*args, **kwargs):
        _install_transport_redaction()
        token = _PRIVATE_TRANSPORT.set(True)
        try:
            return await method(*args, **kwargs)
        finally:
            _PRIVATE_TRANSPORT.reset(token)
    return guarded


@dataclass(frozen=True)
class GenlookSettings:
    enabled: bool = False
    deployment_approved: bool = False
    paid_calls_approved: bool = False
    privacy_approved: bool = False
    prepaid_confirmed: bool = False
    auto_topup_disabled_confirmed: bool = False
    api_key: str = field(default="", repr=False)
    terms_review_id: str = ""
    privacy_review_id: str = ""
    cost_review_id: str = ""
    ledger_dir: str = ""
    max_total_credits: int = 0
    unit_price_microusd: int = 0
    max_total_microusd: int = 0
    timeout_seconds: int = 240
    mode: str = SEQUENTIAL_MODE

    @classmethod
    def environment(cls):
        def number(name, default=0):
            value = os.getenv(name, str(default))
            return int(value) if value.isdigit() and len(value) <= 12 else 0
        return cls(
            enabled=os.getenv("GENLOOK_ENABLED") == "true",
            deployment_approved=os.getenv("GENLOOK_DEPLOYMENT_APPROVED") == "true",
            paid_calls_approved=os.getenv("GENLOOK_PAID_CALLS_APPROVED") == "true",
            privacy_approved=os.getenv("GENLOOK_PRIVACY_APPROVED") == "true",
            prepaid_confirmed=os.getenv("GENLOOK_PREPAID_CONFIRMED") == "true",
            auto_topup_disabled_confirmed=os.getenv("GENLOOK_AUTO_TOPUP_DISABLED_CONFIRMED") == "true",
            api_key=os.getenv("GENLOOK_API_KEY", ""),
            terms_review_id=os.getenv("GENLOOK_TERMS_REVIEW_ID", ""),
            privacy_review_id=os.getenv("GENLOOK_PRIVACY_REVIEW_ID", ""),
            cost_review_id=os.getenv("GENLOOK_COST_REVIEW_ID", ""),
            ledger_dir=os.getenv("GENLOOK_LEDGER_DIR", ""),
            max_total_credits=number("GENLOOK_MAX_TOTAL_CREDITS"),
            unit_price_microusd=number("GENLOOK_UNIT_PRICE_MICROUSD"),
            max_total_microusd=number("GENLOOK_MAX_TOTAL_MICROUSD"),
            timeout_seconds=number("GENLOOK_TIMEOUT_SECONDS", 240),
            mode=os.getenv("GENLOOK_MODE", SEQUENTIAL_MODE),
        )

    def issue(self):
        if not self.enabled:
            return "genlook_disabled"
        if self.mode not in (SEQUENTIAL_MODE, PAIRED_MODE):
            return "genlook_policy_unreviewed"
        if not all((self.deployment_approved, self.paid_calls_approved, self.privacy_approved,
                    self.prepaid_confirmed, self.auto_topup_disabled_confirmed)):
            return "genlook_approval_required"
        if not re.fullmatch(r"[A-Za-z0-9_-]{8,512}", self.api_key):
            return "genlook_key_missing"
        if any(not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", value)
               for value in (self.terms_review_id, self.privacy_review_id, self.cost_review_id)):
            return "genlook_policy_unreviewed"
        if (type(self.max_total_credits) is not int or not 3 <= self.max_total_credits <= 3000 or
                type(self.unit_price_microusd) is not int or not 1 <= self.unit_price_microusd <= 10000000 or
                type(self.max_total_microusd) is not int or not 3 * self.unit_price_microusd <= self.max_total_microusd <= 1000000000 or
                type(self.timeout_seconds) is not int or not 10 <= self.timeout_seconds <= 1800):
            return "genlook_budget_exhausted"
        if not self.ledger_dir or not Path(self.ledger_dir).is_absolute():
            return "genlook_journal_unavailable"
        return None


def verified_png(raw: bytes, content_type: str | None = None) -> bytes:
    """Bound, decode and re-encode actual PNG/JPEG/WebP bytes, never rename WebP."""
    try:
        if not isinstance(raw, bytes) or not 0 < len(raw) <= MAX_IMAGE:
            raise ValueError()
        mime = content_type.split(";", 1)[0].strip().lower() if content_type else None
        expected = {"image/png": "PNG", "image/jpeg": "JPEG", "image/webp": "WEBP"}
        if mime is not None and mime not in expected:
            raise ValueError()
        with Image.open(io.BytesIO(raw)) as image:
            if (image.format not in expected.values() or mime and image.format != expected[mime] or
                    getattr(image, "n_frames", 1) != 1 or min(image.size) < 32 or
                    image.width * image.height > 20000000):
                raise ValueError()
            image.verify()
        with Image.open(io.BytesIO(raw)) as image:
            image.load()
            normalized = ImageOps.exif_transpose(image).convert("RGB")
            # Match the API boundary's metadata-free PNG. Pillow convert()
            # retains ICC/EXIF info, otherwise identical source pixels can have
            # different hashes when a reviewed reference passes through the API.
            clean = Image.new("RGB", normalized.size)
            clean.paste(normalized)
            out = io.BytesIO()
            clean.save(out, format="PNG")
            value = out.getvalue()
        if len(value) > MAX_IMAGE:
            raise ValueError()
        return value
    except Exception:
        raise ProviderFailure("invalid_provider_result") from None


class GenlookProvider:
    """One serial owner of a private durable spend ledger; HTTP injection is test-only."""
    def __init__(self, settings: GenlookSettings | None = None, *, transport=None, review_timeout_seconds=0,
                 detail_policy=BASELINE_DETAIL_POLICY, product_references=None):
        details = detail_policy_manifest(detail_policy)
        from genlook_product_references import ProductReferences
        if product_references is not None and (not isinstance(product_references, ProductReferences) or details):
            raise ValueError("invalid_or_conflicting_product_references")
        self._product_references = product_references
        self._detail_policy = detail_policy
        self.settings = settings or GenlookSettings.environment()
        self._transport = transport
        self._initialized = False
        self._issue = None
        self._busy = False
        self._current_job = None
        self._cancelled = set()
        self._api = None
        self._downloads = None
        self._lease = None
        self._root = None
        self._ledger = None
        # Explicit operator-only opt-in. No new unauthenticated HTTP endpoint and
        # no implicit approval based on a decodable image or a completed job.
        self._review_timeout = review_timeout_seconds
        self._reviewing_job = None
        self._paired = self.settings.mode == PAIRED_MODE
        self._plan = review_plan(self.settings.mode) if self.settings.mode in (SEQUENTIAL_MODE, PAIRED_MODE) else ()
        self._ledger_version = 2 if self._paired else 1
        contract = PAIRED_CONTRACT if self._paired else CONTRACT
        # Credential changes must not silently rebind an existing spend ledger.
        # The new mode field is overwritten below with the same pre-P51 value in
        # default mode, retaining existing sequential contract digests exactly.
        identity = {name: value for name, value in vars(self.settings).items() if name not in ("api_key", "ledger_dir")}
        identity.update(api=API, contract=contract, account_fingerprint=hashlib.sha256(self.settings.api_key.encode()).hexdigest(),
                        mode=self.settings.mode, roles=ROLES, keep_for_days=1, crop=False,
                        resolution="default", remote_cancel_supported=False)
        if review_timeout_seconds:
            identity["operator_review"] = {"version": 1, "timeout_seconds": review_timeout_seconds}
        if details:
            # Opt-in cannot silently mutate/reuse a P54 ledger or its approval.
            # Default identity remains byte-for-byte compatible with P54.
            identity["detail_policy"] = details
        if product_references is not None:
            identity["product_references"] = product_references.manifest()
        self.contract_sha = hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        self.revision = contract + "-" + self.contract_sha[:16]

    def public_policy(self):
        requests_notice = ("상의·하의를 함께 생성한 뒤 신발을 적용하는 2회 요청으로 최대 3크레딧을 예약합니다. "
                           if self._paired else "3회 순차 생성합니다. ")
        return {
            "name": "genlook", "execution_location": "third_party", "upstream_model": "not_disclosed",
            "mode": self.settings.mode, "max_credits_per_job": 3, "remote_cancellation_supported": False,
            "remote_retention_days": 1,
            "notice": "Genlook 외부 서버에 사진과 의류를 전송해 " + requests_notice + "EU 저장·글로벌 처리 정책 검토가 필요합니다. "
                      "사진·결과는 1일 보관 설정 및 완료 후 삭제 요청, 생성·보안 기록 2년·동의 기록 5년·백업 최대 30일 정책이 별도로 적용됩니다. "
                      "삭제 요청은 진행 중 생성 취소를 보장하지 않으며 대기 종료 후에도 과금될 수 있습니다. 모델 이름·버전은 공급자가 공개하지 않습니다.",
        }

    def _availability(self):
        issue = self.settings.issue() or self._issue
        if issue is None and self._ledger is not None and not self._budget_fits():
            issue = "genlook_budget_exhausted"
        return ProviderAvailability(not bool(issue), issue or "", MODEL, self.revision, self.contract_sha,
                                    "operator-reviewed Genlook service terms", "third-party-server", ROLES,
                                    provider_info=self.public_policy())

    def _budget_fits(self):
        reserved = sum(job["credits_reserved"] for job in self._ledger["jobs"].values())
        return (reserved + 3 <= self.settings.max_total_credits and
                (reserved + 3) * self.settings.unit_price_microusd <= self.settings.max_total_microusd)

    async def initialize(self):
        if self._initialized:
            return
        self._initialized = True
        self._issue = self.settings.issue()
        if (type(self._review_timeout) is not int or self._review_timeout < 0 or
                self._review_timeout and (not self._paired or self._review_timeout > self.settings.timeout_seconds)):
            self._issue = "genlook_policy_unreviewed"
        if self._issue:
            return
        try:
            import httpx
            root = Path(self.settings.ledger_dir)
            existed = root.exists()
            if existed:
                _regular_local(root)
                marker = root / MARKER
                _regular_local(marker)
                if not marker.is_file() or marker.read_bytes() != b"fashionai-genlook-v1\n":
                    raise ValueError()
                if os.name != "nt" and root.stat().st_mode & 0o077:
                    raise ValueError()
            else:
                root.mkdir(parents=True, mode=0o700)
                _regular_local(root)
                with (root / MARKER).open("xb") as marker:
                    marker.write(b"fashionai-genlook-v1\n")
                os.chmod(root / MARKER, 0o600)
            allowed = {MARKER, "provider.lock", "ledger.json"}
            if self._review_timeout:
                allowed.add("reviews")
            if any(path.name not in allowed for path in root.iterdir()):
                raise ValueError()
            for path in root.iterdir():
                _regular_local(path)
                if path.name == "reviews" and self._review_timeout:
                    if not path.is_dir():
                        raise ValueError()
                    for child in path.rglob("*"):
                        _regular_local(child)
                    continue
                if not path.is_file():
                    raise ValueError()
            lease = root / "provider.lock"
            self._lease = lease.open("a+b")
            os.chmod(lease, 0o600)
            self._lease.seek(0); self._lease.write(b"0"); self._lease.flush(); self._lease.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self._lease.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self._lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self._root = root
            journal = root / "ledger.json"
            if journal.exists():
                if journal.stat().st_size > 4 * 1024 * 1024:
                    raise ValueError()
                self._ledger = json.loads(journal.read_bytes())
                self._validate_ledger()
            elif existed:
                # A deleted journal is not a reset of approved spending.
                raise ValueError()
            else:
                self._ledger = {"version": self._ledger_version, "contract_sha": self.contract_sha,
                                "namespace": uuid4().hex, "jobs": {}}
                self._persist()
            try:
                self._retire_marked_review_images()
            except Exception:
                # Local cleanup must be reconciled before another paid request.
                # Legacy, unmarked P54 evidence is never claimed or removed.
                self._issue = "genlook_cleanup_required"
                return
            if any(job["status"] not in {"completed", "failed", "cancelled"}
                   for job in self._ledger["jobs"].values()):
                self._issue = "genlook_reconciliation_required"
                return
            timeout = httpx.Timeout(20.0, connect=10.0)
            # No ambient proxy, cookies from another service, redirect or transport retry.
            self._api = httpx.AsyncClient(timeout=timeout, trust_env=False, follow_redirects=False, transport=self._transport)
            self._downloads = httpx.AsyncClient(timeout=timeout, trust_env=False, follow_redirects=False, transport=self._transport)
        except Exception:
            self._issue = "genlook_journal_unavailable"
            if self._lease:
                self._lease.close(); self._lease = None

    def _validate_ledger(self):
        value = self._ledger
        if (not isinstance(value, dict) or set(value) != {"version", "contract_sha", "namespace", "jobs"} or
                value["version"] != self._ledger_version or value["contract_sha"] != self.contract_sha or
                not re.fullmatch(r"[a-f0-9]{32}", value["namespace"]) or
                not isinstance(value["jobs"], dict) or len(value["jobs"]) > 1000):
            raise ValueError()
        for identity, job in value["jobs"].items():
            if str(UUID(identity)) != identity or not isinstance(job, dict) or set(job) != {
                    "input_sha", "status", "credits_reserved", "balance_before", "balance_after", "customer_id", "created_at", "stages"}:
                raise ValueError()
            if (not SHA.fullmatch(job["input_sha"]) or job["status"] not in STATES or
                    job["credits_reserved"] != 3 or not SAFE_ID.fullmatch(job["customer_id"]) or
                    type(job["created_at"]) not in (int, float) or not math.isfinite(job["created_at"]) or
                    not isinstance(job["stages"], list) or len(job["stages"]) != len(self._plan)):
                raise ValueError()
            prefix = "fa-" + value["namespace"][:16] + "-" + UUID(identity).hex
            if job["customer_id"] != prefix:
                raise ValueError()
            for balance in (job["balance_before"], job["balance_after"]):
                if balance is not None and (type(balance) is not int or not 0 <= balance <= 1000000000):
                    raise ValueError()
            for plan, stage in zip(self._plan, job["stages"]):
                expected = ({"roles", "product_ids", "generation_id", "state", "result_sha"} if self._paired
                            else {"role", "product_id", "generation_id", "state", "result_sha"})
                if not isinstance(stage, dict) or set(stage) != expected:
                    raise ValueError()
                if self._paired:
                    if (stage["roles"] != list(plan.roles) or
                            stage["product_ids"] != {role: prefix + "-" + role for role in plan.roles}):
                        raise ValueError()
                elif stage["role"] != plan.roles[0] or stage["product_id"] != prefix + "-" + plan.roles[0]:
                    raise ValueError()
                if (stage["state"] not in {"unsubmitted", "uploading", "uploaded", "submitting", "polling", "downloaded", "failed"} or
                        stage["generation_id"] is not None and not SAFE_ID.fullmatch(stage["generation_id"]) or
                        stage["result_sha"] is not None and not SHA.fullmatch(stage["result_sha"])):
                    raise ValueError()
            ids = [stage["generation_id"] for stage in job["stages"] if stage["generation_id"]]
            if len(set(ids)) != len(ids) or job["status"] in {"completed", "failed", "cancelled"} and self._pending(job):
                raise ValueError()
            if job["status"] == "completed" and any(stage["state"] != "downloaded" or not stage["generation_id"] or not stage["result_sha"] for stage in job["stages"]):
                raise ValueError()

    def _persist(self):
        try:
            target = self._root / "ledger.json"
            temporary = self._root / "ledger.next"
            if target.is_symlink() or temporary.exists():
                raise OSError()
            with temporary.open("x", encoding="utf-8") as output:
                os.chmod(temporary, 0o600)
                json.dump(self._ledger, output, sort_keys=True, separators=(",", ":"), allow_nan=False)
                output.flush(); os.fsync(output.fileno())
            os.replace(temporary, target)
        except Exception:
            self._issue = "genlook_journal_unavailable"
            raise ProviderFailure("genlook_journal_unavailable") from None

    async def check_availability(self):
        await self.initialize()
        return self._availability()

    def public_progress(self, job_id, input_sha256):
        """Return a bounded, current-job stage snapshot without HTTP or persistence.

        Stage position describes workflow state, not percentage, visual quality or
        remote execution speed. The service must expose this only for its owned
        running job; cancelled/restored/other-input work cannot supply a snapshot.
        """
        if (not isinstance(job_id, str) or not isinstance(input_sha256, str) or
                not SHA.fullmatch(input_sha256) or not self._busy or self._current_job != job_id or
                job_id in self._cancelled or self.settings.mode not in (SEQUENTIAL_MODE, PAIRED_MODE)):
            return None
        try:
            if (not isinstance(self._ledger, dict) or self._ledger.get("contract_sha") != self.contract_sha or
                    self._ledger.get("version") != self._ledger_version):
                return None
            job = self._ledger["jobs"].get(job_id)
            if (not isinstance(job, dict) or job.get("input_sha") != input_sha256 or
                    job.get("status") not in {"reserved", "uploading", "submitting", "polling", "downloaded"}):
                return None
            if self._reviewing_job == job_id:
                return {"mode": self.settings.mode, "stage_index": 1,
                        "stage_count": len(self._plan), "phase": "validating_result"}
            stages = job["stages"]
            if (not isinstance(stages, list) or len(stages) != len(self._plan) or
                    any(not isinstance(stage, dict) or stage.get("state") == "failed" for stage in stages)):
                return None
            phases = {"unsubmitted": "preparing", "uploading": "uploading", "uploaded": "submitting",
                      "submitting": "submitting", "polling": "waiting_for_generation"}
            for index, (plan, stage) in enumerate(zip(self._plan, stages), start=1):
                if self._stage_plan(stage).roles != plan.roles:
                    return None
                state = stage["state"]
                if state == "downloaded":
                    result_sha = stage.get("result_sha")
                    if result_sha is None:
                        # Remote COMPLETED alone is not a validated local PNG.
                        phase = "validating_result"
                        break
                    if not isinstance(result_sha, str) or not SHA.fullmatch(result_sha):
                        return None
                    continue
                if state not in phases:
                    return None
                phase = phases[state]
                break
            else:
                index, phase = len(stages), "finalizing"
            return {"mode": self.settings.mode, "stage_index": index,
                    "stage_count": len(stages), "phase": phase}
        except (AttributeError, KeyError, TypeError, ValueError):
            # Optional progress must not expose or destabilize private journal state.
            return None

    @_private_transport
    async def _request(self, method, path, *, accepted=(200,), data=None, files=None):
        import httpx
        try:
            async with self._api.stream(method, API + path, headers={"x-api-key": self.settings.api_key},
                                        data=data, files=files) as response:
                if response.status_code not in accepted:
                    code = {401: "genlook_auth_failed", 403: "genlook_auth_failed", 402: "genlook_insufficient_credits",
                            429: "genlook_rate_limited"}.get(response.status_code, "genlook_unavailable")
                    raise ProviderFailure(code)
                body = bytearray()
                async for part in response.aiter_bytes():
                    if len(body) + len(part) > MAX_JSON:
                        raise ProviderFailure("genlook_invalid_response")
                    body.extend(part)
                if method == "DELETE":
                    return None
                value = json.loads(body)
                if not isinstance(value, dict):
                    raise ValueError()
                return value
        except ProviderFailure:
            raise
        except (httpx.HTTPError, ValueError, TypeError):
            raise ProviderFailure("genlook_unavailable") from None

    async def _credits(self):
        value = await self._request("GET", "/account/credits")
        balance = value.get("creditBalance")
        if type(balance) is not int or not 0 <= balance <= 1000000000:
            raise ProviderFailure("genlook_invalid_response")
        return balance

    @staticmethod
    def _pending(job):
        # An ambiguous upload can finish after an attempted DELETE too. It needs
        # operator reconciliation even though no paid generation is yet known.
        return any(stage["state"] in {"uploading", "submitting", "polling"} for stage in job["stages"])

    def _stage_plan(self, stage):
        return RequestStage(tuple(stage["roles"]) if self._paired else (stage["role"],))

    def _stage_products(self, stage):
        return stage["product_ids"] if self._paired else {stage["role"]: stage["product_id"]}

    def _new_stages(self, prefix):
        stages = []
        for plan in self._plan:
            fields = ({"roles": list(plan.roles), "product_ids": {role: prefix + "-" + role for role in plan.roles}}
                      if self._paired else {"role": plan.roles[0], "product_id": prefix + "-" + plan.roles[0]})
            stages.append(fields | {"generation_id": None, "state": "unsubmitted", "result_sha": None})
        return stages

    async def _cleanup(self, job):
        try:
            await self._request("DELETE", "/customers/" + quote(job["customer_id"], safe=""), accepted=(204,))
            for stage in job["stages"]:
                # Unsubmitted stages never created a product. Genlook returns
                # PRODUCT_NOT_FOUND for these IDs (unlike customer deletion).
                # Ambiguous submissions stay pending and never reach cleanup.
                if not stage["generation_id"]:
                    continue
                for product_id in self._stage_products(stage).values():
                    await self._request("DELETE", "/products/" + quote(product_id, safe=""), accepted=(200, 204))
        except BaseException:
            job["status"] = "cleanup_failed"
            self._issue = "genlook_cleanup_required"
            self._persist()
            raise

    @_private_transport
    async def _download(self, url):
        import httpx
        try:
            parsed = urlsplit(url)
            if (not isinstance(url, str) or len(url) > 8192 or parsed.scheme != "https" or
                    parsed.hostname != "storage.googleapis.com" or parsed.port not in (None, 443) or
                    parsed.username is not None or parsed.password is not None or parsed.fragment or not parsed.path):
                raise ValueError()
            async with self._downloads.stream("GET", url, headers={"Accept": "image/png,image/jpeg,image/webp"}) as response:
                if response.status_code != 200:
                    raise ValueError()
                declared = response.headers.get("content-length")
                if declared and (not declared.isdigit() or int(declared) > MAX_IMAGE):
                    raise ValueError()
                content = bytearray()
                async for part in response.aiter_bytes():
                    if len(content) + len(part) > MAX_IMAGE:
                        raise ValueError()
                    content.extend(part)
                return verified_png(bytes(content), response.headers.get("content-type", ""))
        except ProviderFailure:
            raise
        except (httpx.HTTPError, ValueError, TypeError, AttributeError):
            raise ProviderFailure("genlook_result_untrusted") from None

    def _check_cancel(self, identity):
        if identity in self._cancelled:
            raise asyncio.CancelledError()

    def _retire_owned_review_image(self, directory, *, required=False):
        """Unlink only a newly marked, ledger-bound local image; retain audit files."""
        if directory.parent != self._root / "reviews":
            raise ValueError("review_path_invalid")
        _regular_local(directory)
        marker = directory / REVIEW_IMAGE_OWNER
        if not marker.exists() and not marker.is_symlink():
            if required:
                raise ValueError("review_owner_missing")
            return  # Existing QA evidence has no marker and is out of scope.
        _regular_local(marker)
        if not marker.is_file() or marker.stat().st_size > 4096:
            raise ValueError("review_owner_invalid")
        owner = json.loads(marker.read_bytes())
        keys = {"version", "namespace", "job_id", "input_sha256", "result_sha256",
                "contract_sha256", "created_at", "expires_at"}
        if not isinstance(owner, dict) or set(owner) != keys:
            raise ValueError("review_owner_invalid")
        identity = directory.name
        job = self._ledger["jobs"].get(identity)
        if (str(UUID(identity)) != identity or job is None or type(owner["version"]) is not int or owner["version"] != 1 or
                owner["namespace"] != self._ledger["namespace"] or owner["job_id"] != identity or
                owner["contract_sha256"] != self.contract_sha or
                owner["input_sha256"] != job["input_sha"] or
                owner["result_sha256"] != job["stages"][0]["result_sha"] or
                not isinstance(owner["result_sha256"], str) or not SHA.fullmatch(owner["result_sha256"])):
            raise ValueError("review_owner_invalid")
        created, expires = owner["created_at"], owner["expires_at"]
        if (type(created) not in (int, float) or type(expires) not in (int, float) or
                not math.isfinite(created) or not math.isfinite(expires) or
                not 0 < expires - created <= 1800):
            raise ValueError("review_retention_invalid")
        image = directory / "intermediate.png"
        if not image.exists() and not image.is_symlink():
            return
        _regular_local(image)
        if (not image.is_file() or image.stat().st_size > MAX_IMAGE or
                hashlib.sha256(image.read_bytes()).hexdigest() != owner["result_sha256"]):
            raise ValueError("review_image_changed")
        image.unlink()

    def _retire_marked_review_images(self):
        # An abrupt process exit cannot run finally. On restart clean only these
        # explicit owners, then retain the normal unfinished-job spend block.
        root = self._root / "reviews"
        if not root.exists():
            return
        _regular_local(root)
        for count, directory in enumerate(root.iterdir(), start=1):
            if count > 1000:
                raise ValueError("review_count_exceeded")
            _regular_local(directory)
            if directory.is_dir():
                self._retire_owned_review_image(directory)

    async def _review_pair(self, job_id, input_sha256, png):
        """Wait for a private, exact-binding operator decision; never repeat a POST.

        The operator reviews intermediate.png and atomically creates decision.json
        from request.json's binding with decision='approve' or 'reject'. A restart
        remains blocked by the existing unfinished-ledger reconciliation rule.
        Files inherit the reviewed ledger ACL; caller must enforce Windows ACLs.
        """
        directory = None
        accepted = False
        self._reviewing_job = job_id
        try:
            review_root = self._root / "reviews"
            review_root.mkdir(mode=0o700, exist_ok=True)
            _regular_local(review_root)
            directory = review_root / job_id
            directory.mkdir(mode=0o700, exist_ok=False)
            _regular_local(directory)
            now = time.time()
            owner = {"version": 1, "namespace": self._ledger["namespace"], "job_id": job_id,
                     "input_sha256": input_sha256, "result_sha256": hashlib.sha256(png).hexdigest(),
                     "contract_sha256": self.contract_sha, "created_at": now,
                     "expires_at": now + self._review_timeout}
            # Ownership precedes the image write; no old evidence is adopted.
            with (directory / REVIEW_IMAGE_OWNER).open("x", encoding="utf-8") as stream:
                os.chmod(directory / REVIEW_IMAGE_OWNER, 0o600)
                json.dump(owner, stream, sort_keys=True, allow_nan=False)
                stream.flush(); os.fsync(stream.fileno())
            image = directory / "intermediate.png"
            with image.open("xb") as stream:
                os.chmod(image, 0o600)
                stream.write(png); stream.flush(); os.fsync(stream.fileno())
            binding = {"job_id": job_id, "input_sha256": input_sha256,
                       "result_sha256": hashlib.sha256(png).hexdigest(),
                       "model_revision": self.revision, "nonce": uuid4().hex}
            request = directory / "request.next"
            with request.open("x", encoding="utf-8") as stream:
                os.chmod(request, 0o600)
                json.dump(binding, stream, sort_keys=True)
                stream.flush(); os.fsync(stream.fileno())
            request.replace(directory / "request.json")
            decision = directory / "decision.json"
            until = time.monotonic() + self._review_timeout
            while True:
                self._check_cancel(job_id)
                if time.monotonic() >= until:
                    raise ProviderFailure("genlook_review_timeout")
                if decision.exists():
                    _regular_local(decision)
                    _regular_local(image)
                    if not decision.is_file() or decision.stat().st_size > 4096 or image.stat().st_size > MAX_IMAGE:
                        raise ProviderFailure("genlook_review_invalid")
                    value = json.loads(decision.read_bytes())
                    if (not isinstance(value, dict) or set(value) != {*binding, "decision"} or
                            any(value.get(key) != expected for key, expected in binding.items()) or
                            value["decision"] not in ("approve", "reject") or
                            hashlib.sha256(image.read_bytes()).hexdigest() != binding["result_sha256"]):
                        raise ProviderFailure("genlook_review_invalid")
                    self._check_cancel(job_id)
                    if value["decision"] == "reject":
                        raise ProviderFailure("genlook_review_rejected")
                    accepted = True
                    return
                await asyncio.sleep(.2)
        except (OSError, ValueError, TypeError):
            raise ProviderFailure("genlook_review_invalid") from None
        finally:
            self._reviewing_job = None
            if directory is not None:
                try:
                    self._retire_owned_review_image(directory, required=True)
                except Exception:
                    self._issue = "genlook_cleanup_required"
                    # Preserve rejection/cancellation diagnostics, but a nominal
                    # approval must not proceed to shoes with failed cleanup.
                    if accepted:
                        raise ProviderFailure("genlook_cleanup_required") from None

    async def _stage(self, identity, job, stage, person, garments, remaining, prepared_references=None):
        self._check_cancel(identity)
        if await self._credits() < remaining:
            raise ProviderFailure("genlook_insufficient_credits")
        self._check_cancel(identity)
        stage["state"] = "uploading"; job["status"] = "uploading"; self._persist()
        uploaded = await self._request("POST", "/images/upload", accepted=(201,),
            data={"crop": "false", "externalUserId": job["customer_id"], "keepForDays": "1"},
            files={"file": ("person.png", person, "image/png")})
        image_id = uploaded.get("imageId")
        if not isinstance(image_id, str) or not 1 <= len(image_id) <= 1024 or any(ord(c) < 32 for c in image_id):
            raise ProviderFailure("genlook_invalid_response")
        stage["state"] = "uploaded"; job["status"] = "reserved"; self._persist()
        self._check_cancel(identity)
        plan = self._stage_plan(stage)
        products = self._stage_products(stage)
        body = build_stage_body(plan, image_id=image_id,
                                customer_id=job["customer_id"],
                                product_ids=products, detail_policy=self._detail_policy)
        files = {("product" if len(plan.roles) == 1 else role): (role + ".png", garments[role], "image/png")
                 for role in plan.roles}
        if self._product_references is not None:
            self._product_references.attach(body, files, plan.roles, prepared_references)
        # Persist intent BEFORE the paid POST. No retries, even if the response is lost.
        stage["state"] = "submitting"; job["status"] = "submitting"; self._persist()
        created = await self._request("POST", "/try-on", accepted=(201,),
            data={"data": json.dumps(body, separators=(",", ":"))},
            files=files)
        generation = created.get("generationId")
        prior = [item["generation_id"] for item in job["stages"] if item["generation_id"]]
        if (not isinstance(generation, str) or not SAFE_ID.fullmatch(generation) or generation in prior or
                created.get("status") != "PENDING" or created.get("productExternalIds") != [products[role] for role in plan.roles]):
            raise ProviderFailure("genlook_invalid_response")
        stage["generation_id"] = generation; stage["state"] = "polling"; job["status"] = "polling"; self._persist()
        while True:
            self._check_cancel(identity)
            status = await self._request("GET", "/generations/" + quote(generation, safe=""))
            if status.get("generationId") != generation or status.get("status") not in {"PENDING", "PROCESSING", "COMPLETED", "FAILED"}:
                raise ProviderFailure("genlook_invalid_response")
            if status["status"] == "FAILED":
                stage["state"] = "failed"; self._persist()
                raise ProviderFailure("genlook_remote_failed")
            if status["status"] == "COMPLETED":
                # A terminal status proves generation stopped, not that the image is safe or correct.
                stage["state"] = "downloaded"; self._persist()
                result = await self._download(status.get("resultImageUrl"))
                if hashlib.sha256(result).digest() == hashlib.sha256(person).digest():
                    raise ProviderFailure("invalid_provider_result")
                stage["result_sha"] = hashlib.sha256(result).hexdigest(); self._persist()
                return result
            await asyncio.sleep(1)

    async def generate(self, job_id, input_sha256, images, placements):
        available = await self.check_availability()
        if not available.ready:
            raise ProviderFailure(available.reason)
        if self._busy:
            raise ProviderFailure("provider_busy")
        try:
            if (str(UUID(job_id)) != job_id or not SHA.fullmatch(input_sha256) or
                    set(images) != {"person", *ROLES} or set(placements) != set(ROLES)):
                raise ValueError()
            normalized = {role: verified_png(value) for role, value in images.items()}
            prepared_references = (self._product_references.prepare(normalized)
                                   if self._product_references is not None else None)
        except Exception:
            raise ProviderFailure("invalid_provider_input") from None
        if job_id in self._ledger["jobs"]:
            raise ProviderFailure("genlook_request_already_recorded")
        if len(self._ledger["jobs"]) >= 1000 or not self._budget_fits():
            raise ProviderFailure("genlook_budget_exhausted")
        self._busy = True
        self._current_job = job_id
        job = None
        try:
            async with asyncio.timeout(self.settings.timeout_seconds):
                balance = await self._credits()
                if balance < 3:
                    raise ProviderFailure("genlook_insufficient_credits")
                self._check_cancel(job_id)
                prefix = "fa-" + self._ledger["namespace"][:16] + "-" + UUID(job_id).hex
                job = {"input_sha": input_sha256, "status": "reserved", "credits_reserved": 3,
                       "balance_before": balance, "balance_after": None, "customer_id": prefix,
                       "created_at": time.time(), "stages": self._new_stages(prefix)}
                self._ledger["jobs"][job_id] = job; self._persist()
                current = normalized["person"]
                remaining = 3
                for stage_index, stage in enumerate(job["stages"]):
                    current = await self._stage(job_id, job, stage, current, normalized, remaining, prepared_references)
                    remaining -= self._stage_plan(stage).expected_credits
                    if self._review_timeout and stage_index == 0:
                        await self._review_pair(job_id, input_sha256, current)
                self._check_cancel(job_id)
                try:
                    job["balance_after"] = await self._credits()
                except ProviderFailure:
                    pass  # Unknown post-run balance is not invented billing proof.
                await self._cleanup(job)
                job["status"] = "completed"; self._persist()
                source = {"provider": "genlook", "service": "tryon/v1", "upstream_model": "not_disclosed",
                          "generation_ids": [stage["generation_id"] for stage in job["stages"]],
                          "mode": self.settings.mode, "credits_reserved": 3,
                          "credits_balance_before": job["balance_before"], "credits_balance_after": job["balance_after"],
                          "quality": "NOT_EVALUATED"}
                return ProviderResult(current, job_id, input_sha256, self.revision, self.contract_sha, source=source)
        except BaseException as failure:
            if job is not None and job["status"] != "completed":
                if self._pending(job):
                    job["status"] = "uncertain"
                    self._issue = "genlook_reconciliation_required"
                    self._persist()
                elif job["status"] != "cleanup_failed":
                    try:
                        await self._cleanup(job)
                        # A successful remote DELETE does not resolve a refused
                        # local image cleanup. Preserve that block across restart
                        # even if its ownership marker was removed or damaged.
                        job["status"] = ("cleanup_failed" if self._issue == "genlook_cleanup_required" else
                                         "cancelled" if isinstance(failure, asyncio.CancelledError) else "failed")
                        self._persist()
                    except BaseException:
                        self._issue = "genlook_cleanup_required"
            if isinstance(failure, asyncio.CancelledError):
                raise
            if isinstance(failure, ProviderFailure):
                raise
            if isinstance(failure, TimeoutError):
                raise ProviderFailure("genlook_timeout") from None
            raise ProviderFailure("genlook_unavailable") from None
        finally:
            normalized.clear()
            self._busy = False
            self._current_job = None

    async def cancel(self, job_id):
        self._cancelled.add(job_id)
        job = self._ledger["jobs"].get(job_id) if self._ledger else None
        if job and job["status"] == "cleanup_failed":
            # Startup deliberately has no HTTP clients until reconciliation.
            # Cancellation cannot certify unresolved local/remote cleanup or
            # replace its durable state with a terminal success-like state.
            self._issue = "genlook_cleanup_required"
            raise ProviderFailure("genlook_cleanup_required")
        if job and self._pending(job):
            job["status"] = "uncertain"
            self._issue = "genlook_reconciliation_required"
            self._persist()
            raise ProviderFailure("remote_execution_unconfirmed")
        # API requests cancel before joining its coroutine. That owned coroutine
        # is responsible for cleanup; do not race it with a second DELETE stream.
        if self._busy and self._current_job == job_id:
            return
        # A customer DELETE cannot stop generation or reverse charges.
        if job and job["status"] not in {"completed", "failed", "cancelled"}:
            await self._cleanup(job)
            job["status"] = "cancelled"; self._persist()

    async def close(self):
        if self._current_job:
            try:
                await self.cancel(self._current_job)
            except ProviderFailure:
                pass
        for client in (self._api, self._downloads):
            if client is not None:
                await client.aclose()
        if self._lease:
            self._lease.close(); self._lease = None
