"""Opt-in, self-hosted *real* image generation via FASHN VTON v1.5.

This is an OFFLINE CLI and does not expose an HTTP endpoint, contact the paid
FASHN API, or modify the app's existing authenticated try-on workflow.
Model weights and all photos remain outside Git. No substitute overlay/mock
is returned on real inference failure.
"""
from __future__ import annotations

import argparse
import json
import hashlib
import os
import sys
import tempfile
from pathlib import Path
from typing import Callable

from PIL import Image, ImageOps

CATEGORIES = ("tops", "bottoms", "one-pieces")
MAX_INPUT_BYTES = 12 * 1024 * 1024
MAX_INPUT_PIXELS = 20_000_000
MAX_INPUT_SIDE = 1296


class LocalVtonError(Exception):
    """Actionable error with no input image or token content."""


def check_weights(directory: Path) -> Path:
    directory = directory.expanduser().resolve()
    model = directory / "model.safetensors"
    dwpose = directory / "dwpose"
    required = ("yolox_l.onnx", "dw-ll_ucoco_384.onnx")
    paths = [model] + [dwpose / name for name in required]
    if any(not path.is_file() or path.stat().st_size == 0 for path in paths):
        raise LocalVtonError(
            "Model weights missing: prepare model.safetensors, dwpose/yolox_l.onnx, "
            "dwpose/dw-ll_ucoco_384.onnx with the official "
            "fashn-vton-1.5/scripts/download_weights.py"
        )
    for path in paths:
        with path.open("rb") as stream:
            if stream.read(80).startswith((b"version https://git-lfs", b"<!DOCTYPE", b"<html")):
                raise LocalVtonError("Model weights contain a pointer/download error, not model data")
    return directory


def verify_weight_manifest(directory: Path) -> None:
    """Server preflight verifies hashes against a manifest made by the official-only downloader."""
    directory = check_weights(directory)
    try:
        manifest = json.loads((directory / "vton.manifest.json").read_text(encoding="utf-8"))
        required = {"model.safetensors", "dwpose/yolox_l.onnx", "dwpose/dw-ll_ucoco_384.onnx"}
        if set(manifest["files"]) != required:
            raise ValueError()
        for name, expected in manifest["files"].items():
            if not isinstance(expected, str) or len(expected) != 64:
                raise ValueError()
            digest = hashlib.sha256()
            with (directory / name).open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise ValueError()
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise LocalVtonError("Verified weight manifest missing or mismatched; run prepare_local_vton_weights.py") from exc


def read_input(path: Path) -> Image.Image:
    if not path.is_file() or path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
        raise LocalVtonError("Input must be an existing JPEG/PNG/WebP image")
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise LocalVtonError("Input image exceeds 12 MiB")
    try:
        with Image.open(path) as opened:
            if getattr(opened, "n_frames", 1) != 1:
                raise LocalVtonError("Animated images are not supported")
            if min(opened.size) < 32 or opened.width * opened.height > MAX_INPUT_PIXELS:
                raise LocalVtonError("Image dimensions are outside the supported range")
            # Correct orientation and explicitly discard original EXIF/metadata.
            image = ImageOps.exif_transpose(opened)
            image.thumbnail((MAX_INPUT_SIDE, MAX_INPUT_SIDE))
            rgba = image.convert("RGBA")
            rgb = Image.new("RGB", rgba.size, "white")
            rgb.paste(rgba, mask=rgba.getchannel("A"))
            return rgb
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise LocalVtonError("Could not decode the input image safely") from exc


def validate_device(device: str, allow_cpu: bool) -> None:
    if device == "cpu":
        if not allow_cpu:
            raise LocalVtonError("CPU generation is very slow. Pass --allow-cpu explicitly.")
        return
    try:
        import torch
    except ImportError as exc:
        raise LocalVtonError("PyTorch is not installed in this environment") from exc
    if not torch.cuda.is_available():
        raise LocalVtonError("No CUDA GPU detected. Use a CUDA machine or explicitly --device cpu --allow-cpu.")


def run_inference(
    *, person: Path, garment: Path, category: str, weights_dir: Path,
    output: Path, device: str = "cuda", allow_cpu: bool = False,
    garment_photo_type: str = "flat-lay", num_timesteps: int = 30,
    seed: int = 42, dry_run: bool = False, overwrite: bool = False,
    pipeline_factory: Callable | None = None,
) -> dict:
    """Generate one result image; fake factory is injectable for unit tests only."""
    if category not in CATEGORIES:
        raise LocalVtonError("Category must be tops, bottoms or one-pieces")
    if garment_photo_type not in ("flat-lay", "model"):
        raise LocalVtonError("Garment photo type must be flat-lay or model")
    if not 20 <= num_timesteps <= 50:
        raise LocalVtonError("Sampling steps must be 20..50")
    checked_weights = check_weights(weights_dir)
    person_image = read_input(person)
    garment_image = read_input(garment)
    validate_device(device, allow_cpu)

    if output.suffix.lower() != ".png":
        raise LocalVtonError("Output must be a PNG file")
    if not output.parent.is_dir():
        raise LocalVtonError("Output directory must exist (keep private photos out of Git)")
    if output.is_symlink() or (output.exists() and not overwrite):
        raise LocalVtonError("Output already exists or is a symlink; use --overwrite only for a regular file")

    info = {
        "engine": "fashn-vton-1.5-local",
        "category": category,
        "device": device,
        "generated": False,
        "output": str(output),
    }
    if dry_run:
        return info  # Preflight is NOT a generated image.

    if pipeline_factory is None:
        try:
            from fashn_vton import TryOnPipeline
        except ImportError as exc:
            raise LocalVtonError("Install the official fashn-vton-1.5 package before generation") from exc
        pipeline_factory = TryOnPipeline

    # The official pipeline uses locally prepared model weights and may download
    # human parser weights on first use. It does NOT invoke the paid FASHN API.
    try:
        pipeline = pipeline_factory(weights_dir=str(checked_weights), device=device)
        result = pipeline(
            person_image=person_image, garment_image=garment_image,
            category=category, garment_photo_type=garment_photo_type,
            num_samples=1, num_timesteps=num_timesteps, seed=seed,
            segmentation_free=True,
        )
        images = getattr(result, "images", None)
        if not images or not isinstance(images[0], Image.Image):
            raise LocalVtonError("Inference produced no valid image")
        generated = images[0].convert("RGB")
        if min(generated.size) < 32 or generated.width * generated.height > MAX_INPUT_PIXELS:
            raise LocalVtonError("Generated image dimensions are invalid")
    except LocalVtonError:
        raise
    except Exception as exc:
        # Do not log raw prompts, original photos or provider payloads.
        raise LocalVtonError("Local model inference failed; no output was published") from exc

    name = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".vton-", suffix=".png", delete=False) as temporary:
            name = temporary.name
            generated.save(temporary, format="PNG")
            temporary.flush()
            os.fsync(temporary.fileno())
        # This replaces the destination only when --overwrite was explicitly set.
        os.replace(name, output)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)
    info["generated"] = True
    return info


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Self-hosted FASHN VTON v1.5 (no paid API)")
    parser.add_argument("--person", type=Path, required=True)
    parser.add_argument("--garment", type=Path, required=True)
    parser.add_argument("--category", choices=CATEGORIES, required=True)
    parser.add_argument("--weights-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--device", choices=("cuda", "cpu"), default="cuda")
    parser.add_argument("--allow-cpu", action="store_true")
    parser.add_argument("--garment-photo-type", choices=("flat-lay", "model"), default="flat-lay")
    parser.add_argument("--num-timesteps", type=int, default=30)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--dry-run", action="store_true", help="Only check setup; does not generate")
    parser.add_argument("--overwrite", action="store_true", help="Replace an existing regular PNG")
    options = parser.parse_args(argv)
    try:
        result = run_inference(
            person=options.person, garment=options.garment,
            category=options.category, weights_dir=options.weights_dir,
            output=options.output, device=options.device, allow_cpu=options.allow_cpu,
            garment_photo_type=options.garment_photo_type, num_timesteps=options.num_timesteps,
            seed=options.seed, dry_run=options.dry_run, overwrite=options.overwrite,
        )
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except LocalVtonError as exc:
        print(f"Local VTON unavailable: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
