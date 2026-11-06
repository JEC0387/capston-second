"""One private Qwen-2511 CUDA job. No model download, public API or fallback."""
from __future__ import annotations

import io
import json
import math
import os
from pathlib import Path
import sys
import threading
import time

from outfit_vton_provider import IMAGE_ROLES, ROLES, MAX_PNG, ProviderFailure, verify_bundle, verify_runtime


def prompt_for(placements):
    if not isinstance(placements, dict) or set(placements) != set(ROLES):
        raise ProviderFailure("invalid_provider_input")
    hints = []
    # Same photo-normalized bounds as the API. Preserve the original placement
    # in the receipt; equivalent full rotations are normalized only in the hint.
    limits = {"center_x": (-2, 3), "center_y": (-2, 3), "width": (0, 4), "scale": (.05, 20), "rotation": (-36000, 36000)}
    for role in ROLES:
        placement = placements[role]
        if not isinstance(placement, dict) or set(placement) != set(limits):
            raise ProviderFailure("invalid_provider_input")
        for key, bounds in limits.items():
            value = placement[key]
            if (type(value) not in (int, float) or not math.isfinite(value) or not bounds[0] <= value <= bounds[1] or
                    key == "width" and value == 0):
                raise ProviderFailure("invalid_provider_input")
        hint = dict(placement)
        hint["rotation"] = math.remainder(hint["rotation"], 360)
        if hint["rotation"] == 0:
            hint["rotation"] = 0.0
        hints.append(role + " approximate normalized placement " + ", ".join(f"{key}={hint[key]:.4f}" for key in limits))
    return (
        "Edit only the clothing in image 1. Image 1 is the sole person, pose, face, hair, hands, feet, camera and background reference. "
        "Preserve their identity, body proportions, pose and background. Image 2 supplies ONLY the top, image 3 ONLY the bottom, "
        "and image 4 ONLY the shoe design. Replace the existing top, bottom and both shoes with these three products in ONE image. "
        "Do not copy garment-model people, faces, backgrounds or poses. Preserve product color, pattern, neckline, sleeves, "
        "waist and material. Infer a matching left/right shoe pair at the person's actual feet without extra shoes or limbs. "
        "Use natural cloth folds, hand occlusion, contact shadows, and ankle/trouser/shoe boundaries. No collage or text. "
        "Placement hints are user layout guidance, not body measurements or permission to change pose: " + "; ".join(hints)
    )


def read_image(path):
    from PIL import Image
    if not path.is_file() or path.is_symlink() or not 0 < path.stat().st_size <= MAX_PNG:
        raise ProviderFailure("invalid_provider_input")
    raw = path.read_bytes()
    with Image.open(io.BytesIO(raw)) as image:
        if image.format != "PNG" or getattr(image, "n_frames", 1) != 1 or min(image.size) < 128 or image.width * image.height > 20_000_000:
            raise ProviderFailure("invalid_provider_input")
        image.load()
        converted = image.convert("RGBA")
        background = Image.new("RGB", image.size, "white")
        background.paste(converted, mask=converted.getchannel("A"))
        return background


def run(folder: Path):
    from local_vton_worker import watch_parent  # Reuse parent-death watchdog, not its FASHN loader.
    options = json.loads((folder / "request.json").read_bytes())
    if type(options.get("parent_pid")) is not int or options["parent_pid"] <= 0:
        raise ProviderFailure("invalid_provider_input")
    threading.Thread(target=watch_parent, args=(options["parent_pid"],), daemon=True).start()
    # Offline flags are set before importing any model runtime, both here and in
    # the child environment. Only an operator-staged local directory is accepted.
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_IMPLICIT_TOKEN="1",
                      HF_HUB_DISABLE_TELEMETRY="1", DO_NOT_TRACK="1")
    root = Path(options["bundle_dir"])
    manifest = verify_bundle(root, options["manifest_sha256"])
    verify_runtime(manifest)
    if manifest["model_revision"] != options["model_revision"]:
        raise ProviderFailure("bundle_integrity_failed")
    prompt = prompt_for(options["placements"])
    images = [read_image(folder / (role + ".png")) for role in IMAGE_ROLES]
    import torch
    if not torch.cuda.is_available():
        raise ProviderFailure("cuda_unavailable")
    from diffusers import QwenImageEditPlusPipeline
    start = time.monotonic()
    torch.cuda.reset_peak_memory_stats()
    try:
        # Exact local files and safetensors only: no remote repository/code,
        # torch pickle, LoRA discovery, automatic CPU fallback, or dynamic model.
        pipeline = QwenImageEditPlusPipeline.from_pretrained(str(root), torch_dtype=torch.bfloat16,
            local_files_only=True, use_safetensors=True, trust_remote_code=False)
        pipeline.to("cuda")
        pipeline.set_progress_bar_config(disable=True)
    except torch.cuda.OutOfMemoryError:
        raise ProviderFailure("cuda_out_of_memory") from None
    except Exception:
        raise ProviderFailure("model_load_failed") from None
    loaded = time.monotonic()
    load_peak = torch.cuda.max_memory_allocated()
    try:
        with torch.inference_mode():
            output = pipeline(image=images, prompt=prompt, generator=torch.Generator(device="cuda").manual_seed(42),
                true_cfg_scale=4.0, negative_prompt=" ", num_inference_steps=40,
                guidance_scale=1.0, num_images_per_prompt=1)
        torch.cuda.synchronize()
    except torch.cuda.OutOfMemoryError:
        raise ProviderFailure("cuda_out_of_memory") from None
    except Exception:
        raise ProviderFailure("model_inference_failed") from None
    if len(output.images) != 1:
        raise ProviderFailure("invalid_provider_result")
    image = output.images[0].convert("RGB")
    if min(image.size) < 128 or image.width * image.height > 20_000_000:
        raise ProviderFailure("invalid_provider_result")
    image.info.clear()
    image.save(folder / "result.png", "PNG")
    if (folder / "result.png").stat().st_size > MAX_PNG:
        raise ProviderFailure("invalid_provider_result")
    (folder / "metrics.json").write_text(json.dumps({
        "model_load_seconds": loaded-start, "inference_seconds": time.monotonic()-loaded,
        "model_load_peak_vram_bytes": load_peak,
        "peak_vram_bytes": torch.cuda.max_memory_allocated(), "width": image.width, "height": image.height,
        "quality": "NOT_EVALUATED", "runtime": "cuda"}), encoding="utf-8")


if __name__ == "__main__":
    try:
        run(Path(sys.argv[1]))
    except BaseException as error:
        # Do not serialize exception text, image paths, credentials or tracebacks.
        code = error.code if isinstance(error, ProviderFailure) else "worker_failed"
        try:
            (Path(sys.argv[1]) / "error.json").write_text(json.dumps({"error": code}), encoding="utf-8")
        except Exception:
            pass
        sys.exit(2)
