"""Synthetic offline manifest/runtime/process tests; never actual model inference."""
import asyncio
import hashlib
import json
import os
from pathlib import Path
import sys
from types import SimpleNamespace
from contextlib import nullcontext

import pytest

import outfit_vton_provider as provider
import outfit_vton_worker as worker
from outfit_vton_worker import prompt_for, read_image


def run(awaitable):
    return asyncio.run(awaitable)


def bundle(tmp_path):
    root = tmp_path / "synthetic-bundle"
    root.mkdir()
    documents = {name: {} for name in (
        "transformer/config.json", "text_encoder/config.json", "vae/config.json",
        "scheduler/scheduler_config.json", "tokenizer/tokenizer_config.json", "processor/preprocessor_config.json")}
    documents["model_index.json"] = dict(_class_name="QwenImageEditPlusPipeline", _diffusers_version="synthetic",
                                       **provider.COMPONENTS)
    for name, value in documents.items():
        path = root / name
        path.parent.mkdir(exist_ok=True, parents=True)
        path.write_text(json.dumps(value))
    for component in ("transformer", "text_encoder", "vae"):
        (root / component / "synthetic.safetensors").write_bytes(b"NOT A MODEL - HASH TEST ONLY")
    (root / "processor/chat_template.jinja").write_text("synthetic template")
    manifest = {"version": 1, "model_id": provider.MODEL_ID, "model_revision": "a" * 40,
        "license_review": {"approved": True, "purpose": "commercial-self-hosted", "review_id": "synthetic-unit-review",
            "components": {name: {"license": "synthetic-license", "source": "https://example.invalid/review",
                                  "commercial_use_reviewed": True}
                for name in ("model", "text_encoder", "tokenizer", "vae", "scheduler", "runtime")}},
        "runtime_packages": {name: "synthetic-version" for name in provider.PACKAGES},
        "files": {path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                  for path in root.rglob("*") if path.is_file()}}
    return root, manifest, write_manifest(root, manifest)


def write_manifest(root, manifest):
    raw = json.dumps(manifest).encode()
    (root / "outfit.manifest.json").write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()


def placements():
    return {role: {"center_x": .5, "center_y": .5, "width": .3, "scale": 1, "rotation": 0} for role in provider.ROLES}


def test_disabled_has_no_model_or_runtime_import(monkeypatch):
    def forbidden(*args):
        pytest.fail("disabled must not inspect model or runtime")
    monkeypatch.setattr(provider, "verify_bundle", forbidden)
    monkeypatch.setattr(provider, "private_work_root", forbidden)
    monkeypatch.setattr(provider.importlib.util, "find_spec", forbidden)
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings())
    assert not run(engine.check_availability()).ready
    with pytest.raises(provider.ProviderFailure, match="provider_disabled"):
        run(engine.generate("id", "a" * 64, {}, {}))


def test_approval_is_not_inferred_from_bundle_or_env(monkeypatch):
    monkeypatch.setattr(provider, "verify_bundle", lambda *a: pytest.fail("unapproved asset read"))
    monkeypatch.setattr(provider, "private_work_root", lambda *a: pytest.fail("unapproved work directory read"))
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(enabled=True))
    assert run(engine.check_availability()).reason == "approval_required"


def test_manifest_hash_and_every_asset_bound_without_model_loading(tmp_path):
    root, manifest, digest = bundle(tmp_path)
    assert provider.verify_bundle(root, digest)["model_revision"] == "a" * 40
    (root / "transformer/synthetic.safetensors").write_bytes(b"CHANGED")
    with pytest.raises(provider.ProviderFailure, match="bundle_integrity_failed"):
        provider.verify_bundle(root, digest)


@pytest.mark.parametrize("case", ["extra", "pickle", "remote_code", "wrong_pipeline", "wrong_component", "missing_license", "missing_encoder", "shard_escape", "unlisted_shard", "quantization"])
def test_unreviewed_bundle_variants_are_blocked(tmp_path, case):
    root, manifest, digest = bundle(tmp_path)
    if case == "extra":
        (root / "extra.txt").write_text("not listed")
    elif case == "pickle":
        path = root / "evil.bin"; path.write_bytes(b"not executed")
        manifest["files"]["evil.bin"] = hashlib.sha256(path.read_bytes()).hexdigest()
    elif case in ("remote_code", "wrong_pipeline", "wrong_component"):
        name = "text_encoder/config.json" if case == "remote_code" else "model_index.json"
        value = json.loads((root / name).read_bytes())
        if case == "remote_code": value["auto_map"] = {"Model": "evil.py"}
        elif case == "wrong_pipeline": value["_class_name"] = "OtherPipeline"
        else: value["text_encoder"] = ["unreviewed", "UntrustedClass"]
        raw = json.dumps(value).encode(); (root / name).write_bytes(raw)
        manifest["files"][name] = hashlib.sha256(raw).hexdigest()
    elif case in ("shard_escape", "unlisted_shard", "quantization"):
        name = "transformer/invalid.index.json"
        value = {"quantization_config": {"quant_method": "unreviewed"}} if case == "quantization" else {
            "weight_map": {"synthetic.parameter": "../../outside.safetensors" if case == "shard_escape" else "unlisted.safetensors"}}
        raw = json.dumps(value).encode(); (root / name).write_bytes(raw)
        manifest["files"][name] = hashlib.sha256(raw).hexdigest()
    elif case == "missing_license":
        manifest["license_review"]["components"].pop("text_encoder")
    else:
        name = "text_encoder/synthetic.safetensors"
        (root / name).unlink(); manifest["files"].pop(name)
    if case != "extra": digest = write_manifest(root, manifest)
    with pytest.raises(provider.ProviderFailure): provider.verify_bundle(root, digest)


def test_runtime_version_mismatch_blocks_before_torch_import(tmp_path, monkeypatch):
    root, manifest, digest = bundle(tmp_path)
    monkeypatch.setattr(provider.importlib.metadata, "version", lambda name: "different-version")
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(True, True, str(root), digest, str(tmp_path / "work"), 80))
    assert run(engine.check_availability()).reason == "runtime_unavailable"


def test_worker_environment_never_inherits_credentials_or_network_model_fallback(tmp_path):
    parent = {"HF_TOKEN": "synthetic-secret", "AWS_SECRET_ACCESS_KEY": "synthetic-key", "PYTHONPATH": "untrusted",
              "HTTPS_PROXY": "https://synthetic-secret@example.invalid", "PATH": "runtime-bin"}
    before = dict(parent)
    child = provider.child_environment(parent, tmp_path)
    assert parent == before
    assert not set(parent).intersection(child) - {"PATH"}
    assert child["HF_HUB_OFFLINE"] == child["TRANSFORMERS_OFFLINE"] == "1"
    assert child["HF_HUB_DISABLE_IMPLICIT_TOKEN"] == "1"


def test_three_reference_order_and_placement_hints_are_finite_only():
    text = prompt_for(placements())
    assert "Image 2 supplies ONLY the top, image 3 ONLY the bottom" in text
    assert "image 4 ONLY the shoe design" in text
    assert "both shoes" in text
    invalid = placements(); invalid["shoes"]["center_x"] = float("nan")
    with pytest.raises(provider.ProviderFailure): prompt_for(invalid)
    invalid = placements(); invalid["tops"]["prompt"] = "untrusted free text"
    with pytest.raises(provider.ProviderFailure): prompt_for(invalid)


def test_api_placement_limits_preserved_and_only_hint_rotation_is_normalized():
    value = placements()
    value["tops"].update(center_x=-2, center_y=3, width=4, rotation=36000)
    original = json.loads(json.dumps(value))
    text = prompt_for(value)
    assert "center_x=-2.0000, center_y=3.0000, width=4.0000" in text
    assert "rotation=0.0000" in text and value == original
    value["tops"]["width"] = 0
    with pytest.raises(provider.ProviderFailure): prompt_for(value)


def test_nonimage_worker_input_is_not_model_success(tmp_path):
    image = tmp_path / "person.png"; image.write_bytes(b"not an image")
    with pytest.raises(Exception): read_image(image)


def test_existing_unowned_directory_is_never_adopted_or_cleaned(tmp_path):
    preserved = tmp_path / "existing-user-file.txt"
    preserved.write_text("must remain")
    with pytest.raises((provider.ProviderFailure, OSError)):
        provider.private_work_root(tmp_path)
    assert preserved.read_text() == "must remain"
    assert not (tmp_path / ".fashionai-outfit-private").exists()


@pytest.mark.parametrize("name", ["outfit-owned-interrupted", "unknown-folder", "unknown-file.png"])
def test_startup_leftovers_block_before_model_inspection_and_are_preserved(tmp_path, monkeypatch, name):
    root = provider.private_work_root(tmp_path / "private")
    target = root / name
    if not name.endswith(".png"):
        target.mkdir()
        target = target / "person.png"
    original = b"SYNTHETIC PRIVATE INPUT - NOT A PHOTO"
    target.write_bytes(original)
    marker = (root / ".fashionai-outfit-private").read_bytes()
    monkeypatch.setattr(provider, "verify_bundle", lambda *a: pytest.fail("leftover must block before asset inspection"))
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(
        enabled=True, deployment_approved=True, bundle_dir=str(tmp_path / "unused-bundle"), work_dir=str(root)))
    available = run(engine.check_availability())
    assert not available.ready and available.reason == "workdir_review_required"
    with pytest.raises(provider.ProviderFailure, match="workdir_review_required"):
        run(engine.generate("synthetic-job", "a" * 64, {}, {}))
    assert target.read_bytes() == original
    assert (root / ".fashionai-outfit-private").read_bytes() == marker


def test_idle_cached_readiness_rechecks_private_root_without_deleting_leftovers(tmp_path):
    root = provider.private_work_root(tmp_path / "private")
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(work_dir=str(root)))
    engine._initialized = True
    engine._availability = provider.ProviderAvailability(True, "")
    assert run(engine.check_availability()).ready
    leftover = root / "outfit-owned-interrupted"; leftover.mkdir()
    image = leftover / "tops.png"; image.write_bytes(b"synthetic bytes remain")
    assert run(engine.check_availability()).reason == "workdir_review_required"
    assert image.read_bytes() == b"synthetic bytes remain"
    assert not run(engine.check_availability()).ready


@pytest.mark.parametrize("active", ["launching", "worker"])
def test_current_owned_job_does_not_trigger_orphan_block(tmp_path, active):
    root = provider.private_work_root(tmp_path / "private")
    current = root / "outfit-owned-current"; current.mkdir()
    (current / "person.png").write_bytes(b"synthetic active input")
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(work_dir=str(root)))
    engine._initialized = True
    engine._availability = provider.ProviderAvailability(True, "")
    if active == "launching": engine._busy = True
    else: engine._processes["current-job"] = SimpleNamespace(returncode=None)
    assert run(engine.check_availability()).ready
    assert (current / "person.png").read_bytes() == b"synthetic active input"
    engine._busy = False; engine._processes.clear()
    assert run(engine.check_availability()).reason == "workdir_review_required"


def test_new_job_rechecks_root_after_readiness_and_never_starts_worker(tmp_path, monkeypatch):
    root = provider.private_work_root(tmp_path / "private")
    engine = provider.LocalGpuProvider(provider.GpuProviderSettings(work_dir=str(root)))
    ready = provider.ProviderAvailability(True, "", model_revision="a" * 40, model_sha256="b" * 64)
    unknown = root / "arrived-after-readiness.png"
    async def readiness():
        unknown.write_bytes(b"must not be removed")
        return ready
    monkeypatch.setattr(engine, "check_availability", readiness)
    monkeypatch.setattr(provider.asyncio, "create_subprocess_exec", lambda *a, **k: pytest.fail("worker must not start"))
    with pytest.raises(provider.ProviderFailure, match="workdir_review_required"):
        run(engine.generate("synthetic-job", "c" * 64,
            {role: b"synthetic" for role in provider.IMAGE_ROLES}, placements()))
    assert not engine._busy and not engine._processes
    assert not engine._availability.ready and engine._availability.reason == "workdir_review_required"
    assert unknown.read_bytes() == b"must not be removed"


def test_cancel_joins_only_own_subprocess():
    async def check():
        engine = provider.LocalGpuProvider(provider.GpuProviderSettings())
        child = await asyncio.create_subprocess_exec(sys.executable, "-c", "import time; time.sleep(60)",
            stdin=asyncio.subprocess.DEVNULL, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
        engine._processes["synthetic-job"] = child
        try:
            await engine.cancel("unrelated-job")
            assert child.returncode is None
            await engine.cancel("synthetic-job")
            assert child.returncode is not None
        finally:
            if child.returncode is None:
                child.kill(); await child.wait()
    run(check())


def test_worker_calls_offline_pinned_pipeline_with_four_images_and_writes_new_png(tmp_path, monkeypatch):
    """Fake runtime only: verifies actual worker wiring, not GPU or model quality."""
    from PIL import Image
    folder = tmp_path / "private-job"; folder.mkdir()
    colors = [(30, 0, 0), (0, 60, 0), (0, 0, 90), (120, 120, 0)]
    for role, color in zip(provider.IMAGE_ROLES, colors):
        Image.new("RGB", (256, 384), color).save(folder / (role + ".png"))
    options = dict(parent_pid=1, bundle_dir=str(tmp_path / "operator-bundle"),
                   manifest_sha256="a" * 64, model_revision="b" * 40, placements=placements())
    (folder / "request.json").write_text(json.dumps(options))
    calls = []
    class FakePipeline:
        @classmethod
        def from_pretrained(cls, directory, **kwargs):
            assert directory == options["bundle_dir"]
            assert kwargs == dict(torch_dtype="synthetic-bf16", local_files_only=True,
                                  use_safetensors=True, trust_remote_code=False)
            calls.append("load"); return cls()
        def to(self, device): assert device == "cuda"; return self
        def set_progress_bar_config(self, **kwargs): assert kwargs == {"disable": True}
        def __call__(self, **kwargs):
            assert [image.getpixel((0, 0)) for image in kwargs["image"]] == colors
            assert kwargs["num_inference_steps"] == 40 and kwargs["num_images_per_prompt"] == 1
            assert kwargs["true_cfg_scale"] == 4.0 and kwargs["guidance_scale"] == 1.0
            assert kwargs["generator"] == ("cuda", 42)
            assert "image 4 ONLY the shoe design" in kwargs["prompt"]
            calls.append("infer")
            return SimpleNamespace(images=[Image.new("RGB", (256, 384), (13, 27, 41))])
    class Generator:
        def __init__(self, device): self.device = device
        def manual_seed(self, seed): return self.device, seed
    cuda = SimpleNamespace(is_available=lambda: True,
        reset_peak_memory_stats=lambda: calls.append("reset_peak"),
        max_memory_allocated=lambda: 30 if "infer" in calls else 20,
        synchronize=lambda: None, OutOfMemoryError=type("SyntheticCudaOOM", (RuntimeError,), {}))
    monkeypatch.setitem(sys.modules, "torch", SimpleNamespace(cuda=cuda, bfloat16="synthetic-bf16",
                                                             Generator=Generator, inference_mode=nullcontext))
    monkeypatch.setitem(sys.modules, "diffusers", SimpleNamespace(QwenImageEditPlusPipeline=FakePipeline))
    monkeypatch.setitem(sys.modules, "local_vton_worker", SimpleNamespace(watch_parent=lambda parent: None))
    monkeypatch.setattr(worker, "threading", SimpleNamespace(Thread=lambda **kwargs: SimpleNamespace(start=lambda: None)))
    monkeypatch.setattr(worker, "verify_bundle", lambda path, digest: {"model_revision": "b" * 40})
    monkeypatch.setattr(worker, "verify_runtime", lambda manifest: None)
    monkeypatch.setattr(worker, "os", SimpleNamespace(environ={}))
    worker.run(folder)
    assert calls == ["reset_peak", "load", "infer"]
    with Image.open(folder / "result.png") as image:
        image.load(); assert image.format == "PNG" and image.getpixel((0, 0)) == (13, 27, 41)
    metrics = json.loads((folder / "metrics.json").read_bytes())
    assert metrics["quality"] == "NOT_EVALUATED"
    assert metrics["model_load_peak_vram_bytes"] == 20 and metrics["peak_vram_bytes"] == 30


def test_native_launch_cancellation_does_not_orphan_worker(tmp_path, monkeypatch):
    class FakeProcess:
        returncode = None
        def terminate(self): self.returncode = -15
        def kill(self): self.returncode = -9
        async def wait(self): return self.returncode
    async def check():
        launched = asyncio.Event(); release = asyncio.Event(); child = FakeProcess()
        async def start(*args, **kwargs):
            launched.set(); await release.wait(); return child
        monkeypatch.setattr(provider.asyncio, "create_subprocess_exec", start)
        engine = provider.LocalGpuProvider(provider.GpuProviderSettings(work_dir=str(tmp_path / "private")))
        engine._initialized = True
        engine._availability = provider.ProviderAvailability(True, "", model_revision="a" * 40, model_sha256="b" * 64)
        task = asyncio.create_task(engine.generate("synthetic-job", "c" * 64,
                                  {role: b"synthetic-not-model-image" for role in provider.IMAGE_ROLES}, placements()))
        await launched.wait()
        with pytest.raises(provider.ProviderFailure, match="provider_busy"):
            await engine.generate("second-job", "d" * 64, {}, {})
        task.cancel(); release.set()
        with pytest.raises(asyncio.CancelledError): await task
        assert child.returncode == -15 and not engine._processes and not engine._busy
        assert [p.name for p in (tmp_path / "private").iterdir()] == [".fashionai-outfit-private"]
        assert (await engine.check_availability()).ready
    run(check())
