"""Opt-in, local-only three-garment GPU boundary. No download or hosted fallback.

The bundle manifest is an operator-reviewed deployment artifact, not a license
grant. Tests may inject a fake provider; the deployed default never returns one.
"""
from __future__ import annotations

import asyncio
from dataclasses import dataclass
import hashlib
import importlib.metadata
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import tempfile
from typing import Protocol

MODEL_ID = "Qwen/Qwen-Image-Edit-2511"
ROLES = ("tops", "bottoms", "shoes")
IMAGE_ROLES = ("person", *ROLES)
SHA = re.compile(r"[a-f0-9]{64}\Z")
REVISION = re.compile(r"[a-f0-9]{40}\Z")
PACKAGES = {"torch", "diffusers", "transformers", "accelerate", "safetensors", "Pillow"}
COMPONENTS = {
    "processor": ["transformers", "Qwen2VLProcessor"],
    "scheduler": ["diffusers", "FlowMatchEulerDiscreteScheduler"],
    "text_encoder": ["transformers", "Qwen2_5_VLForConditionalGeneration"],
    "tokenizer": ["transformers", "Qwen2Tokenizer"],
    "transformer": ["diffusers", "QwenImageTransformer2DModel"],
    "vae": ["diffusers", "AutoencoderKLQwenImage"],
}
MAX_PNG = 8 * 1024 * 1024


class ProviderFailure(RuntimeError):
    def __init__(self, code="provider_failed"):
        allowed = {"provider_disabled", "approval_required", "bundle_missing", "manifest_invalid",
                   "bundle_integrity_failed", "license_review_missing", "runtime_unavailable",
                   "cuda_unavailable", "vram_unapproved", "worker_failed", "worker_stop_unconfirmed",
                   "invalid_provider_input", "invalid_provider_result", "model_load_failed",
                   "model_inference_failed", "cuda_out_of_memory", "provider_busy",
                   "workdir_review_required", "provider_selection_invalid",
                   "genlook_disabled", "genlook_approval_required", "genlook_key_missing",
                   "genlook_policy_unreviewed", "genlook_budget_exhausted", "genlook_insufficient_credits",
                   "genlook_auth_failed", "genlook_rate_limited", "genlook_unavailable",
                   "genlook_remote_failed", "genlook_invalid_response", "genlook_result_untrusted",
                   "genlook_journal_unavailable", "genlook_reconciliation_required",
                   "genlook_cleanup_required", "genlook_request_already_recorded",
                   "genlook_invalid_input", "genlook_cancelled", "genlook_timeout",
                   "remote_execution_unconfirmed"}
        self.code = code if code in allowed else "provider_failed"
        super().__init__(self.code)


@dataclass(frozen=True)
class ProviderAvailability:
    ready: bool = False
    reason: str = "provider_disabled"
    model_id: str = MODEL_ID
    model_revision: str = ""
    model_sha256: str = ""
    license: str = "Apache-2.0; complete bundle review required"
    runtime: str = "self-hosted-cuda; not verified"
    roles: tuple[str, ...] = ROLES
    provider_info: dict | None = None


@dataclass(frozen=True)
class ProviderResult:
    png: bytes
    job_id: str
    input_sha256: str
    model_revision: str
    model_sha256: str
    source: dict | None = None


class VirtualTryOnProvider(Protocol):
    async def initialize(self) -> None: ...
    async def check_availability(self) -> ProviderAvailability: ...
    async def generate(self, job_id: str, input_sha256: str,
                       images: dict[str, bytes], placements: dict) -> ProviderResult: ...
    async def cancel(self, job_id: str) -> None: ...


class DisabledProvider:
    def __init__(self, reason="provider_disabled"):
        self.reason = ProviderFailure(reason).code

    async def initialize(self):
        return None

    async def check_availability(self):
        return ProviderAvailability(reason=self.reason)

    async def generate(self, job_id, input_sha256, images, placements):
        raise ProviderFailure(self.reason)

    async def cancel(self, job_id):
        return None


@dataclass(frozen=True)
class GpuProviderSettings:
    enabled: bool = False
    deployment_approved: bool = False
    bundle_dir: str = ""
    manifest_sha256: str = ""
    work_dir: str = ""
    min_vram_gib: int = 0

    @classmethod
    def environment(cls):
        raw = os.getenv("OUTFIT_VTON_MIN_VRAM_GIB", "0")
        return cls(os.getenv("OUTFIT_VTON_GPU_ENABLED") == "true",
                   os.getenv("OUTFIT_VTON_GPU_DEPLOYMENT_APPROVED") == "true",
                   os.getenv("OUTFIT_VTON_MODEL_BUNDLE", ""),
                   os.getenv("OUTFIT_VTON_MANIFEST_SHA256", ""),
                   os.getenv("OUTFIT_VTON_WORK_DIR", ""),
                   int(raw) if raw.isdigit() and len(raw) <= 3 else 0)


def _regular_local(path: Path):
    for part in (path, *path.parents):
        info = part.lstat()
        if part.is_symlink() or getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400):
            raise ProviderFailure("bundle_integrity_failed")


def _digest(path: Path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_bundle(root: Path, expected_manifest_sha256: str) -> dict:
    """Verify every staged asset; no ambient HF cache, pickle or Python module."""
    try:
        if not root.is_absolute() or not SHA.fullmatch(expected_manifest_sha256):
            raise ProviderFailure("manifest_invalid")
        _regular_local(root)
        manifest_path = root / "outfit.manifest.json"
        _regular_local(manifest_path)
        if not manifest_path.is_file() or manifest_path.stat().st_size > 1024 * 1024:
            raise ProviderFailure("manifest_invalid")
        raw = manifest_path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected_manifest_sha256:
            raise ProviderFailure("bundle_integrity_failed")
        manifest = json.loads(raw)
        if (manifest.get("version") != 1 or manifest.get("model_id") != MODEL_ID or
                not REVISION.fullmatch(manifest.get("model_revision", ""))):
            raise ProviderFailure("manifest_invalid")
        review = manifest.get("license_review", {})
        if (review.get("approved") is not True or review.get("purpose") != "commercial-self-hosted" or
                not isinstance(review.get("review_id"), str) or not review["review_id"].strip() or
                set(review.get("components", {})) != {"model", "text_encoder", "tokenizer", "vae", "scheduler", "runtime"}):
            raise ProviderFailure("license_review_missing")
        for component in review["components"].values():
            if (not isinstance(component, dict) or not component.get("license") or
                    not isinstance(component.get("source"), str) or not component["source"].startswith("https://") or
                    component.get("commercial_use_reviewed") is not True):
                raise ProviderFailure("license_review_missing")
        packages = manifest.get("runtime_packages", {})
        if set(packages) != PACKAGES or any(not isinstance(v, str) or not v or len(v) > 100 for v in packages.values()):
            raise ProviderFailure("manifest_invalid")
        files = manifest.get("files", {})
        if not isinstance(files, dict) or not files or len(files) > 1000:
            raise ProviderFailure("manifest_invalid")
        required = {"model_index.json", "transformer/config.json", "text_encoder/config.json", "vae/config.json",
                    "scheduler/scheduler_config.json", "tokenizer/tokenizer_config.json", "processor/preprocessor_config.json"}
        if not required.issubset(files) or any(not any(name.startswith(component + "/") and name.endswith(".safetensors")
                for name in files) for component in ("transformer", "text_encoder", "vae")):
            raise ProviderFailure("manifest_invalid")
        observed = set()
        for path in root.rglob("*"):
            _regular_local(path)
            if path.is_file() and path != manifest_path:
                observed.add(path.relative_to(root).as_posix())
        if observed != set(files):
            raise ProviderFailure("bundle_integrity_failed")
        for name, expected in files.items():
            relative = PurePosixPath(name)
            if (relative.is_absolute() or ".." in relative.parts or "\\" in name or ":" in name or
                    (relative.suffix not in {".json", ".safetensors", ".txt", ".model", ".md", ".jinja"} and name != "LICENSE") or
                    not isinstance(expected, str) or not SHA.fullmatch(expected)):
                raise ProviderFailure("manifest_invalid")
            path = root.joinpath(*relative.parts)
            if not path.is_file() or path.stat().st_size == 0 or _digest(path) != expected:
                raise ProviderFailure("bundle_integrity_failed")
            if relative.suffix == ".json":
                if path.stat().st_size > 64 * 1024 * 1024:
                    raise ProviderFailure("manifest_invalid")
                data = json.loads(path.read_bytes())
                # Inspect keys, not raw tokenizer text. An index is not permission
                # to read a shard outside the exact hash-reviewed local bundle.
                def unsafe_config(value):
                    if isinstance(value, dict):
                        if "auto_map" in value or "quantization_config" in value:
                            return True
                        if "weight_map" in value:
                            weight_map = value["weight_map"]
                            if not isinstance(weight_map, dict) or not weight_map:
                                return True
                            for shard in weight_map.values():
                                if not isinstance(shard, str):
                                    return True
                                part = PurePosixPath(shard)
                                if (part.is_absolute() or ".." in part.parts or "\\" in shard or ":" in shard or
                                        part.suffix != ".safetensors" or
                                        (relative.parent / part).as_posix() not in files):
                                    return True
                        return any(unsafe_config(v) for v in value.values())
                    return isinstance(value, list) and any(unsafe_config(v) for v in value)
                if unsafe_config(data):
                    raise ProviderFailure("manifest_invalid")
        index = json.loads((root / "model_index.json").read_bytes())
        if (index.get("_class_name") != "QwenImageEditPlusPipeline" or
                set(index) != {"_class_name", "_diffusers_version", *COMPONENTS} or
                any(index.get(name) != value for name, value in COMPONENTS.items())):
            raise ProviderFailure("manifest_invalid")
        return manifest
    except ProviderFailure:
        raise
    except (OSError, ValueError, TypeError, AttributeError, RecursionError):
        raise ProviderFailure("manifest_invalid") from None


def verify_runtime(manifest: dict):
    try:
        if any(importlib.metadata.version(name) != version for name, version in manifest["runtime_packages"].items()):
            raise ProviderFailure("runtime_unavailable")
    except (importlib.metadata.PackageNotFoundError, KeyError):
        raise ProviderFailure("runtime_unavailable") from None


def child_environment(parent: dict, scratch: Path):
    result = {k: v for k, v in parent.items() if k.upper() in {
        "SYSTEMROOT", "WINDIR", "SYSTEMDRIVE", "PATH", "LD_LIBRARY_PATH", "CUDA_VISIBLE_DEVICES"}}
    result.update(HF_HOME=str(scratch / "offline-cache"), HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1",
                  HF_HUB_DISABLE_IMPLICIT_TOKEN="1", HF_HUB_DISABLE_TELEMETRY="1", DO_NOT_TRACK="1",
                  PYTHONNOUSERSITE="1", PYTHONIOENCODING="utf-8", PYTHONUNBUFFERED="1")
    return result


def private_work_root(path: Path):
    """Require an empty owned root; never adopt or erase crash leftovers."""
    if not path.is_absolute():
        raise ProviderFailure("bundle_missing")
    marker = path / ".fashionai-outfit-private"
    if path.exists():
        _regular_local(path)
        _regular_local(marker)
        if not marker.is_file() or marker.read_bytes() != b"fashionai-outfit-vton-v1\n":
            raise ProviderFailure("bundle_integrity_failed")
        if os.name != "nt" and path.stat().st_mode & 0o077:
            raise ProviderFailure("bundle_integrity_failed")
    else:
        path.mkdir(parents=True, mode=0o700)
        _regular_local(path)
        with marker.open("xb") as stream:
            stream.write(b"fashionai-outfit-vton-v1\n")
        os.chmod(marker, 0o600)
    # TemporaryDirectory cannot clean up after a parent SIGKILL/power loss.
    # Inspect names only: do not open, adopt or delete any leftover/unknown item.
    if any(entry.name != marker.name for entry in path.iterdir()):
        raise ProviderFailure("workdir_review_required")
    return path


class LocalGpuProvider:
    """One owned CUDA subprocess; never connects to public research Spaces."""
    def __init__(self, settings: GpuProviderSettings | None = None):
        self.settings = settings or GpuProviderSettings.environment()
        self._availability = ProviderAvailability()
        self._processes: dict[str, asyncio.subprocess.Process] = {}
        self._busy = False
        self._initialized = False

    async def initialize(self):
        # Disabled checks do not import torch or inspect arbitrary model folders.
        if not self.settings.enabled:
            self._availability = ProviderAvailability(reason="provider_disabled")
        elif not self.settings.deployment_approved:
            self._availability = ProviderAvailability(reason="approval_required")
        else:
            try:
                if not self.settings.bundle_dir or not self.settings.work_dir:
                    raise ProviderFailure("bundle_missing")
                private_work_root(Path(self.settings.work_dir))
                manifest = await asyncio.to_thread(verify_bundle, Path(self.settings.bundle_dir), self.settings.manifest_sha256)
                verify_runtime(manifest)
                if importlib.util.find_spec("torch") is None:
                    raise ProviderFailure("runtime_unavailable")
                import torch
                if not torch.cuda.is_available():
                    raise ProviderFailure("cuda_unavailable")
                # Operator sets this after an approved full-runtime memory review;
                # a hardcoded optimistic VRAM number must not declare readiness.
                if not 1 <= self.settings.min_vram_gib <= 192 or torch.cuda.get_device_properties(0).total_memory < self.settings.min_vram_gib * 1024**3:
                    raise ProviderFailure("vram_unapproved")
                self._availability = ProviderAvailability(True, "", MODEL_ID, manifest["model_revision"],
                    self.settings.manifest_sha256, "operator-reviewed complete bundle", "self-hosted-cuda")
            except ProviderFailure as failure:
                self._availability = ProviderAvailability(reason=failure.code)
            except Exception:
                self._availability = ProviderAvailability(reason="runtime_unavailable")
        self._initialized = True

    async def check_availability(self):
        if not self._initialized:
            await self.initialize()
        if self._availability.ready and not self._busy and not self._processes:
            try:
                private_work_root(Path(self.settings.work_dir))
            except (ProviderFailure, OSError) as failure:
                reason = failure.code if isinstance(failure, ProviderFailure) else "workdir_review_required"
                self._availability = ProviderAvailability(reason=reason)
        # Blocked state is sticky until operator review and an explicit restart.
        # An active owned worker is exempt from the empty-root check, not cleanup.
        return self._availability

    async def _stop(self, process):
        if process.returncode is not None:
            return
        try:
            process.terminate()
        except ProcessLookupError:
            await process.wait()
            return
        try:
            await asyncio.wait_for(asyncio.shield(process.wait()), timeout=5)
        except asyncio.TimeoutError:
            try:
                process.kill()
            except ProcessLookupError:
                pass
            # Do not report cancellation or release the service lock while native
            # work remains alive. Parent worker watchdog also prevents orphans.
            await process.wait()

    async def cancel(self, job_id):
        process = self._processes.get(job_id)
        if process is not None:
            await self._stop(process)

    async def generate(self, job_id, input_sha256, images, placements):
        available = await self.check_availability()
        if not available.ready:
            raise ProviderFailure(available.reason)
        if self._busy or self._processes:
            raise ProviderFailure("provider_busy")
        if (not re.fullmatch(r"[a-zA-Z0-9-]{1,64}", job_id) or not SHA.fullmatch(input_sha256) or
                set(images) != set(IMAGE_ROLES) or set(placements) != set(ROLES) or
                any(not isinstance(b, bytes) or not 0 < len(b) <= MAX_PNG for b in images.values())):
            raise ProviderFailure("invalid_provider_input")
        self._busy = True
        try:
            return await self._generate_owned(job_id, input_sha256, images, placements, available)
        finally:
            self._busy = False

    async def _generate_owned(self, job_id, input_sha256, images, placements, available):
        try:
            work = private_work_root(Path(self.settings.work_dir))
        except (ProviderFailure, OSError) as failure:
            reason = failure.code if isinstance(failure, ProviderFailure) else "workdir_review_required"
            self._availability = ProviderAvailability(reason=reason)
            raise ProviderFailure(reason) from None
        with tempfile.TemporaryDirectory(prefix="outfit-owned-", dir=work) as temporary:
            folder = Path(temporary)
            try:
                for role, raw in images.items():
                    with (folder / (role + ".png")).open("xb") as stream:
                        stream.write(raw)
                    os.chmod(folder / (role + ".png"), 0o600)
                request = dict(job_id=job_id, input_sha256=input_sha256,
                    bundle_dir=self.settings.bundle_dir, manifest_sha256=self.settings.manifest_sha256,
                    model_revision=available.model_revision, placements=placements, parent_pid=os.getpid())
                (folder / "request.json").write_text(json.dumps(request), encoding="utf-8")
                os.chmod(folder / "request.json", 0o600)
                launch = asyncio.create_task(asyncio.create_subprocess_exec(sys.executable,
                    str(Path(__file__).with_name("outfit_vton_worker.py")), str(folder),
                    stdin=asyncio.subprocess.DEVNULL, stdout=asyncio.subprocess.DEVNULL,
                    stderr=asyncio.subprocess.DEVNULL, env=child_environment(dict(os.environ), folder)))
                try:
                    process = await asyncio.shield(launch)
                except asyncio.CancelledError:
                    process = await launch
                    await self._stop(process)
                    raise
                self._processes[job_id] = process
                try:
                    if await process.wait() != 0:
                        code = "worker_failed"
                        error = folder / "error.json"
                        if error.is_file() and error.stat().st_size < 1024:
                            code = json.loads(error.read_bytes()).get("error", code)
                        raise ProviderFailure(code)
                    result = folder / "result.png"
                    if not result.is_file() or result.stat().st_size > MAX_PNG:
                        raise ProviderFailure("invalid_provider_result")
                    raw = result.read_bytes()
                    # Service validates PNG format, CRC/decode, and request receipt.
                    return ProviderResult(raw, job_id, input_sha256, available.model_revision, available.model_sha256)
                finally:
                    await self._stop(process)
                    self._processes.pop(job_id, None)
            except (asyncio.CancelledError, ProviderFailure):
                raise
            except Exception:
                raise ProviderFailure("worker_failed") from None


def configured_provider():
    selected = os.getenv("OUTFIT_VTON_PROVIDER", "local_gpu")
    if selected == "genlook":
        try:
            from genlook_outfit_provider import GenlookProvider
        except ImportError:
            return DisabledProvider("runtime_unavailable")
        return GenlookProvider()
    if selected != "local_gpu":
        return DisabledProvider("provider_selection_invalid")
    settings = GpuProviderSettings.environment()
    return LocalGpuProvider(settings) if settings.enabled else DisabledProvider()
