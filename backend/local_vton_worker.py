"""Private one-job process. Parent owns timeout/cancellation and removes the entire temporary directory."""
import json
import os
from pathlib import Path
import sys
import time

from local_vton import read_input, check_weights


def main():
    folder = Path(sys.argv[1])
    options = json.loads((folder / "request.json").read_text())
    # Model weights must be prepared by the operator; do not download during a user's request.
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    import torch
    from fashn_vton import TryOnPipeline
    device = options["device"]
    if device == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("cuda_unavailable")
        torch.cuda.set_per_process_memory_fraction(options["memory_fraction"])
        torch.cuda.reset_peak_memory_stats()
    person, garment = read_input(folder / "person.png"), read_input(folder / "garment.png")
    started = time.perf_counter()
    pipeline = TryOnPipeline(weights_dir=str(check_weights(Path(options["weights_dir"]))), device=device)
    if device == "cuda":
        torch.cuda.synchronize()
    loaded = time.perf_counter()
    with torch.inference_mode():
        result = pipeline(person_image=person, garment_image=garment, category=options["category"],
            garment_photo_type=options["garment_photo_type"], num_samples=1,
            num_timesteps=options["num_timesteps"], seed=options["seed"], segmentation_free=True)
    if device == "cuda":
        torch.cuda.synchronize()
    finished = time.perf_counter()
    image = result.images[0].convert("RGB")
    image.info.clear()
    image.save(folder / "result.png", "PNG")
    metrics = {"model_load_seconds": loaded-started, "inference_seconds": finished-loaded,
        "vram_peak_bytes": torch.cuda.max_memory_allocated() if device == "cuda" else None,
        "width": image.width, "height": image.height, "device": device}
    (folder / "metrics.json").write_text(json.dumps(metrics))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # No traceback/input paths/photos/provider payloads are logged.
        sys.exit(2)
