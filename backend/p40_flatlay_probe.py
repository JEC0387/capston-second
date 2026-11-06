"""Isolated, offline FASHN v1.5 CPU experiment; never used by the production API.

Restricted to a real flat-lay garment and segmentation_free=True. At reviewed
FASHN commit 7c0f10af, both corresponding masking functions return the input
unchanged, so their parser predictions are unused. This adapter does NOT load,
mock, replace, or accept the license of Human Parser. Model-worn garments are
not supported here. Real person poses still come from the approved DWPose files.

Preprocessing/sampling adapted from FASHN AI's Apache-2.0 pipeline.py at that
commit. Keep the downloaded upstream LICENSE with this experiment. Differences:
restricted parser-free flat-lay path; source/weight checks; meta+assign loading;
CPU resource/stage evidence; one-step diagnostic cannot save a completion PNG.
Optional CPU bfloat16 is an experiment, not the official CPU float32 default;
operator support is established only by an actual forward pass, never assumed.
Optional dynamic INT8 converts actual Linear weights one leaf at a time while
keeping remaining modules/activations FP32. Its numerical quality is unverified.
No production license, authentication, or readiness gate is changed.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import gc
import hashlib
import importlib
from importlib.machinery import ModuleSpec
import json
import os
from pathlib import Path
import socket
import sys
import time
from types import ModuleType

REVISION = "7c0f10af3f91ad4048fe9729c470a13ef905d25a"
WEIGHTS = {
    "model.safetensors": "d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a",
    "dwpose/yolox_l.onnx": "7860ae79de6c89a3c1eb72ae9a2756c0ccfbe04b7791bb5880afabd97855a411",
    "dwpose/dw-ll_ucoco_384.onnx": "724f4ff2439ed61afb86fb8a1951ec39c6220682803b4a8bd4f598cd913b1843",
}
NAMESPACE = "_fashionai_p40_reviewed_fashn"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_source(source: Path, manifest: Path) -> None:
    record = json.loads(manifest.read_text(encoding="utf-8-sig"))
    if record.get("revision") != REVISION:
        raise ValueError("unreviewed_source_revision")
    root = source.resolve()
    actual = {p.relative_to(root).as_posix() for p in (root / "src/fashn_vton").rglob("*.py")}
    expected = record.get("files", {})
    if not actual or actual != set(expected):
        raise ValueError("source_manifest_coverage")
    for name in sorted(actual):
        path = (root / name).resolve()
        if not path.is_relative_to(root) or sha256(path) != expected[name]:
            raise ValueError("source_hash_mismatch")


def verify_weights(root: Path, names: tuple[str, ...]) -> None:
    for name in names:
        path = root / name
        if not path.is_file() or sha256(path) != WEIGHTS[name]:
            raise ValueError("weight_hash_mismatch:" + name)


def validate_mode(stage: str, category: str, flat_lay_confirmed: bool, steps: int, dtype: str) -> None:
    if stage not in {"load", "pose", "step", "infer"} or category not in {"tops", "bottoms"}:
        raise ValueError("unsupported_experiment_mode")
    if steps not in {20, 30, 50}:
        raise ValueError("unsupported_sampling_schedule")
    if stage != "load" and not flat_lay_confirmed:
        raise ValueError("real_flat_lay_input_not_confirmed")
    if dtype not in {"float32", "bfloat16", "int8-dynamic", "checkpoint"} or (stage in {"step", "infer"} and dtype == "checkpoint"):
        raise ValueError("checkpoint_dtype_is_load_diagnostic_only")


@contextmanager
def offline_network_guard():
    """Block Python socket connect paths; no claim this is an OS native firewall."""
    environment = {key: os.environ.get(key) for key in
                   ("HF_HUB_OFFLINE", "TRANSFORMERS_OFFLINE", "HF_HUB_DISABLE_TELEMETRY")}
    for key in environment:
        os.environ[key] = "1"
    previous = socket.socket.connect, socket.socket.connect_ex, socket.create_connection
    def blocked(*_args, **_kwargs):
        raise RuntimeError("network_disallowed_in_offline_experiment")
    socket.socket.connect = blocked
    socket.socket.connect_ex = blocked
    socket.create_connection = blocked
    try:
        yield
    finally:
        socket.socket.connect, socket.socket.connect_ex, socket.create_connection = previous
        for key, value in environment.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def namespace_source(source: Path) -> str:
    """Import genuine reviewed files, without executing parser-dependent package __init__.

    This is a separate namespace package, not a fake fashn_human_parser module.
    All imported submodules execute their actual upstream source.
    """
    if any(k == NAMESPACE or k.startswith(NAMESPACE + ".") for k in sys.modules):
        raise RuntimeError("fresh_experiment_process_required")
    for suffix, path in (("", source / "src/fashn_vton"),
                         (".preprocessing", source / "src/fashn_vton/preprocessing")):
        name = NAMESPACE + suffix
        module = ModuleType(name)
        module.__package__ = name
        module.__path__ = [str(path)]
        module.__spec__ = ModuleSpec(name, loader=None, is_package=True)
        module.__spec__.submodule_search_locations = [str(path)]
        sys.modules[name] = module
    return NAMESPACE


def memory_bytes() -> dict:
    try:
        if os.name == "nt":
            import ctypes
            from ctypes import wintypes
            class Counters(ctypes.Structure):
                _fields_ = [("cb", wintypes.DWORD), ("faults", wintypes.DWORD)] + [
                    (name, ctypes.c_size_t) for name in ("peak_rss", "rss", "peak_paged", "paged",
                    "peak_nonpaged", "nonpaged", "pagefile", "peak_pagefile")]
            kernel = ctypes.WinDLL("kernel32", use_last_error=True)
            psapi = ctypes.WinDLL("psapi", use_last_error=True)
            kernel.GetCurrentProcess.restype = wintypes.HANDLE
            psapi.GetProcessMemoryInfo.argtypes = (wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD)
            counters = Counters(); counters.cb = ctypes.sizeof(counters)
            if not psapi.GetProcessMemoryInfo(kernel.GetCurrentProcess(), ctypes.byref(counters), counters.cb):
                raise OSError("memory_measurement_failed")
            return {"rss_bytes": counters.rss, "peak_rss_bytes": counters.peak_rss,
                    "private_commit_bytes": counters.pagefile, "peak_commit_bytes": counters.peak_pagefile}
        import resource
        return {"peak_rss_bytes": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * 1024}
    except (OSError, ImportError):
        return {"peak_rss_bytes": None}


class Evidence:
    def __init__(self, folder: Path):
        folder.mkdir(parents=True, exist_ok=False)
        self.folder = folder
        self.started = time.perf_counter()
        self.stage = "preflight"

    def event(self, stage: str, **details):
        self.stage = stage
        value = {"stage": stage, "utc": datetime.now(timezone.utc).isoformat(),
                 "elapsed_seconds": time.perf_counter() - self.started, **memory_bytes(), **details}
        with (self.folder / "events.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(value) + "\n"); stream.flush()
        print(json.dumps(value), flush=True)

    def finish(self, **details):
        with (self.folder / "final.json").open("x", encoding="utf-8") as stream:
            json.dump({"model_revision": REVISION, "device": "cpu", "android_verified": False,
                       "parser_used": False, "scope": "flat-lay + segmentation-free experimental adapter",
                       "elapsed_seconds": time.perf_counter() - self.started,
                       **memory_bytes(), **details}, stream, indent=2)


def quantize_linear_leaves(model, evidence: Evidence | None = None) -> dict:
    """Quantize genuine Linear leaves without copying or floating the whole model.

    A temporary holder allows quantize_dynamic to replace a root Linear. Parent
    module keys, shared module aliases, dimensions and bias presence are kept.
    No claim of output fidelity follows from successful weight conversion.
    """
    import torch
    from torch.ao.nn.quantized.dynamic import Linear as DynamicLinear
    from torch.ao.quantization import quantize_dynamic

    supported = torch.backends.quantized.supported_engines
    engine = torch.backends.quantized.engine
    if engine not in {"x86", "fbgemm"}:
        if "fbgemm" not in supported:
            raise RuntimeError("cpu_dynamic_int8_backend_unavailable")
        engine = "fbgemm"
        torch.backends.quantized.engine = engine
    if engine not in supported:
        raise RuntimeError("cpu_dynamic_int8_backend_unavailable")
    if isinstance(model, torch.nn.Linear):
        raise ValueError("quantization_requires_parent_container")

    stats = {"backend": engine, "linear_modules": 0, "quantized_weight_elements": 0,
             "source_parameter_elements": sum(p.numel() for p in model.parameters()),
             "shared_module_aliases": 0}
    converted = {}
    visited = {id(model)}

    def visit(parent):
        # Snapshot names only: retaining every old leaf would defeat reclamation.
        for name in tuple(parent._modules):
            child = parent._modules[name]
            if child is None:
                continue
            identity = id(child)
            if identity in converted:
                parent._modules[name] = converted[identity]
                stats["shared_module_aliases"] += 1
                continue
            if type(child) is torch.nn.Linear:
                in_features, out_features = child.in_features, child.out_features
                had_bias = child.bias is not None
                elements = child.weight.numel()
                if evidence is not None:
                    evidence.event("int8_leaf_conversion_started", index=stats["linear_modules"],
                                   in_features=in_features, out_features=out_features, has_bias=had_bias)
                # Only this leaf becomes FP32; original BF16 storage is released
                # as each leaf is replaced. FBGEMM needs FP32 bias/activations.
                child.float().eval()
                holder = torch.nn.Sequential(child)
                quantize_dynamic(holder, {torch.nn.Linear}, dtype=torch.qint8, inplace=True)
                replacement = holder[0]
                if not isinstance(replacement, DynamicLinear):
                    raise RuntimeError("linear_quantization_did_not_replace_module")
                if (replacement.in_features, replacement.out_features) != (in_features, out_features):
                    raise RuntimeError("quantized_linear_shape_changed")
                if (replacement.bias() is not None) != had_bias:
                    raise RuntimeError("quantized_linear_bias_contract_changed")
                parent._modules[name] = replacement
                converted[identity] = replacement
                stats["linear_modules"] += 1
                stats["quantized_weight_elements"] += elements
                del holder, child
            elif identity not in visited:
                visited.add(identity)
                visit(child)

    visit(model)
    if stats["linear_modules"] == 0:
        raise RuntimeError("no_linear_weights_quantized")
    # Packed INT8 weights are not floating Parameters. Only the small remaining
    # convolution/embedding/norm parameters and buffers are converted here.
    model.float().eval()
    stats["remaining_float_parameter_elements"] = sum(p.numel() for p in model.parameters())
    stats["activation_dtype"] = "torch.float32"
    stats["quality"] = "NOT_REVIEWED"
    if evidence is not None:
        evidence.event("int8_conversion_completed", **stats, forward_support_verified=False)
    return stats


def load_core(source: Path, weights: Path, dtype: str, evidence: Evidence):
    import torch
    from safetensors.torch import load_file
    module = importlib.import_module(NAMESPACE + ".tryon_mmdit")
    evidence.event("core_meta_initialization")
    with torch.device("meta"):
        model = module.TryOnModel()
    evidence.event("core_checkpoint_mapping")
    state = load_file(str(weights / "model.safetensors"), device="cpu")
    stored_dtypes = sorted({str(value.dtype) for value in state.values()})
    model.load_state_dict(state, strict=True, assign=True)
    del state
    gc.collect()
    evidence.event("core_weights_assigned", checkpoint_dtypes=stored_dtypes,
                   parameters=sum(p.numel() for p in model.parameters()))
    if dtype in {"float32", "bfloat16"}:
        requested = torch.float32 if dtype == "float32" else torch.bfloat16
        evidence.event("core_precision_selected", precision=dtype,
                       official_cpu_precision=dtype == "float32", forward_support_verified=False)
        model.to(device="cpu", dtype=requested)
    elif dtype == "int8-dynamic":
        evidence.event("core_precision_selected", precision=dtype,
                       official_cpu_precision=False, forward_support_verified=False)
        quantize_linear_leaves(model, evidence)
    if any(p.is_meta for p in model.parameters()) or any(b.is_meta for b in model.buffers()):
        raise RuntimeError("unmaterialized_model_tensor")
    model.eval()
    evidence.event("core_loaded", parameter_dtypes=sorted({str(p.dtype) for p in model.parameters()}),
                   packed_linear_dtype="torch.qint8" if dtype == "int8-dynamic" else None)
    return model


def batch_model_image(tensor, dtype):
    """Upstream single-sample repeat adds the channel axis for grayscale poses.

    numpy_to_torch returns CHW for RGB but HW for grayscale. The official
    pipeline's repeat(num_samples, 1, 1, 1) promotes the latter to N1HW.
    """
    if tensor.ndim not in {2, 3}:
        raise ValueError("model_image_requires_hw_or_chw")
    if tensor.ndim == 3 and tensor.shape[0] not in {1, 3}:
        raise ValueError("model_image_channel_count")
    return tensor.unsqueeze(0).repeat(1, 1, 1, 1).to("cpu", dtype=dtype)


def prepare_real_inputs(args, evidence: Evidence):
    import cv2
    import numpy as np
    import torch
    from PIL import Image, ImageOps
    transforms = importlib.import_module(NAMESPACE + ".preprocessing.transforms")
    pose_module = importlib.import_module(NAMESPACE + ".dwpose")
    utils = importlib.import_module(NAMESPACE + ".utils")
    def image(path: Path):
        if path.stat().st_size > 8 * 1024 * 1024:
            raise ValueError("input_too_large")
        with Image.open(path) as loaded:
            if min(loaded.size) < 32 or loaded.width * loaded.height > 20_000_000:
                raise ValueError("input_dimensions_unsupported")
            return ImageOps.exif_transpose(loaded).convert("RGB")
    evidence.event("input_validation", person_sha256=sha256(args.person), garment_sha256=sha256(args.garment))
    pre_resize = transforms.AspectPreserveResize((864, 864), mode="fit", backend="pil")
    person = np.array(pre_resize(image(args.person), allow_upsampling=False))
    garment = np.array(pre_resize(image(args.garment), allow_upsampling=False))
    evidence.event("pose_model_loading")
    detector = pose_module.DWposeDetector(checkpoints_dir=str(args.weights_dir / "dwpose"), device="cpu")
    evidence.event("pose_inference_started")
    person_pose = detector(person[..., ::-1])
    visible = int((person_pose["bodies"]["subset"] >= 0).sum())
    if visible < 6:
        raise ValueError("insufficient_real_person_keypoints")
    # No person exists in a flat-lay garment. This exact missing-garment-pose convention
    # comes from upstream; person keypoints above are never substituted or fabricated.
    garment_pose = utils.get_dummy_dw_keypoints()
    person_pose_image = pose_module.draw_pose(person_pose, *person.shape[:2], grayscale=True)
    garment_pose_image = pose_module.draw_pose(garment_pose, *garment.shape[:2], grayscale=True)
    del detector
    gc.collect()
    evidence.event("real_pose_completed", visible_person_keypoints=visible, flat_lay_garment_pose="upstream-no-person-convention")
    # Official agnostic.py returns these pixels unchanged for this restricted mode.
    pad = transforms.ResizePad((576, 864), backend="opencv")
    person_input = pad(person, mem_padding=True)
    garment_input = pad(garment)
    person_pose_image = pad(person_pose_image, interpolation=cv2.INTER_NEAREST_EXACT)
    garment_pose_image = pad(garment_pose_image, interpolation=cv2.INTER_NEAREST_EXACT)
    inference_dtype = torch.bfloat16 if args.dtype == "bfloat16" else torch.float32
    def tensor(value):
        normalized = utils.normalize_uint8_to_neg1_1(utils.numpy_to_torch(value))
        return batch_model_image(normalized, inference_dtype)
    return {"ca_images": tensor(person_input), "garment_images": tensor(garment_input),
            "person_poses": tensor(person_pose_image), "garment_poses": tensor(garment_pose_image),
            "garment_categories": torch.tensor([1 if args.category == "tops" else 2], device="cpu")}, pad


def sample(model, inputs, steps: int, seed: int, step_only: bool, evidence: Evidence):
    import torch
    utils = importlib.import_module(NAMESPACE + ".utils")
    evidence.event("sampling_input_contract", inputs={
        name: {"shape": list(value.shape), "dtype": str(value.dtype)}
        for name, value in inputs.items()
    })
    torch.manual_seed(seed)
    images = torch.randn((1, model.channels_in, *model.input_shape), dtype=inputs["ca_images"].dtype, device="cpu")
    schedule = utils.get_rf_schedule(num_steps=steps, mu=1.5)
    with torch.inference_mode():
        for index, (current, previous) in enumerate(zip(schedule[:-1], schedule[1:])):
            evidence.event("denoise_step_started", index=index, requested_steps=steps)
            times = torch.full((1,), current, dtype=images.dtype, device="cpu")
            prediction = model.forward_for_cfg(images, times, **inputs)
            conditional, unconditional = prediction["v_c"], prediction["v_u"]
            velocity = conditional if index >= steps - 1 else unconditional + 1.5 * (conditional - unconditional)
            images = images + (previous - current) * velocity
            if not bool(torch.isfinite(images).all()):
                raise RuntimeError("nonfinite_denoise_output")
            evidence.event("denoise_step_completed", index=index, requested_steps=steps)
            if step_only:
                return None
    return utils.tensor_to_pil(images[0].to(dtype=torch.float).clamp_(-1, 1), unnormalize=True)


def cli(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--weights-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True, help="New, non-existing experiment evidence folder")
    parser.add_argument("--stage", choices=("load", "pose", "step", "infer"), required=True)
    parser.add_argument("--person", type=Path)
    parser.add_argument("--garment", type=Path)
    parser.add_argument("--category", choices=("tops", "bottoms"), default="tops")
    parser.add_argument("--confirm-flat-lay", action="store_true")
    parser.add_argument("--approved-nonpersonal-inputs", action="store_true")
    parser.add_argument("--dtype", choices=("float32", "bfloat16", "int8-dynamic", "checkpoint"), default="float32",
                        help="CPU bfloat16/dynamic INT8 are experimental; checkpoint is load/pose diagnostic only")
    parser.add_argument("--steps", type=int, choices=(20, 30, 50), default=20)
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv)
    validate_mode(args.stage, args.category, args.confirm_flat_lay, args.steps, args.dtype)
    if not 1 <= args.threads <= 8:
        parser.error("threads must be between 1 and 8")
    if args.stage != "load" and (not args.approved_nonpersonal_inputs or not args.person or not args.garment):
        parser.error("explicit approved nonpersonal person/flat-lay inputs are required")
    evidence = Evidence(args.output_dir)
    try:
        evidence.event("source_verification")
        verify_source(args.source_dir, args.source_manifest)
        names = tuple(WEIGHTS) if args.stage in {"step", "infer"} else (
            ("model.safetensors",) if args.stage == "load" else tuple(k for k in WEIGHTS if k.startswith("dwpose/")))
        evidence.event("weight_verification")
        verify_weights(args.weights_dir, names)
        with offline_network_guard():
            namespace_source(args.source_dir)
            import torch
            torch.set_num_threads(args.threads)
            torch.set_num_interop_threads(1)
            evidence.event("runtime_loaded", torch_version=torch.__version__, cuda_available=torch.cuda.is_available(),
                           threads=args.threads, requested_dtype=args.dtype,
                           official_cpu_precision=args.dtype == "float32")
            if args.stage != "load":
                inputs, pad = prepare_real_inputs(args, evidence)
                if args.stage == "pose":
                    evidence.finish(success=True, completed_stage="real_pose", model_loaded=False, inference_complete=False, png_created=False)
                    return 0
            model = load_core(args.source_dir, args.weights_dir, args.dtype, evidence)
            if args.stage == "load":
                evidence.finish(success=True, completed_stage="model_load", model_loaded=True, inference_complete=False, png_created=False)
                return 0
            result = sample(model, inputs, args.steps, args.seed, args.stage == "step", evidence)
            if args.stage == "step":
                evidence.finish(success=True, completed_stage="one_real_denoise_step", model_loaded=True,
                                inference_complete=False, png_created=False, completed_steps=1, requested_steps=args.steps)
                return 0
            result = pad.unpad(result).convert("RGB")
            result.info.clear()
            output = evidence.folder / "new-model-result.png"
            pending = evidence.folder / "unverified-result.partial"
            evidence.event("png_encoding_started")
            with pending.open("xb") as stream:
                result.save(stream, format="PNG")
            from PIL import Image
            with Image.open(pending) as checked:
                checked.verify()
            with Image.open(pending) as checked:
                checked.load()
                if checked.format != "PNG" or checked.size != result.size or checked.mode != "RGB":
                    raise RuntimeError("generated_png_decode_mismatch")
            pending.rename(output)
            evidence.event("png_verified", width=result.width, height=result.height, sha256=sha256(output))
            evidence.finish(success=True, completed_stage="inference", model_loaded=True, inference_complete=True,
                            png_created=True, output_sha256=sha256(output), category=args.category,
                            quality="NOT_REVIEWED", completed_steps=args.steps, seed=args.seed,
                            inference_dtype=args.dtype, official_cpu_precision=args.dtype == "float32")
            return 0
    except Exception as error:
        stage = evidence.stage
        evidence.event("failure", failed_stage=stage, exception_type=type(error).__name__, reason=str(error)[:500])
        evidence.finish(success=False, failed_stage=stage, exception_type=type(error).__name__, inference_complete=False,
                        png_created=(evidence.folder / "new-model-result.png").exists(),
                        incomplete_file_exists=(evidence.folder / "unverified-result.partial").exists())
        return 1


if __name__ == "__main__":
    raise SystemExit(cli())
