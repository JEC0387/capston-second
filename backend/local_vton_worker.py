"""Private one-job process. Parent owns timeout/cancellation and removes the entire temporary directory."""
import json
import os
from pathlib import Path
import sys
import time
import threading
from contextlib import contextmanager

from local_vton import read_input, check_weights, verify_weight_manifest, require_offline_model_loading
from local_vton_parser import verify_parser_assets, verify_runtime_revisions, load_verified_pipeline


class WorkerStageFailure(RuntimeError):
    """Fixed public code only; never contains input paths or model exception text."""
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def failure_code(error):
    if error.__class__.__name__ == 'OutOfMemoryError':
        return 'cuda_out_of_memory'
    if isinstance(error, WorkerStageFailure) and error.code in ('model_load_failed', 'model_inference_failed'):
        return error.code
    return 'model_failed'


@contextmanager
def model_stage(code):
    try:
        yield
    except Exception as error:
        if error.__class__.__name__ == 'OutOfMemoryError':
            raise
        raise WorkerStageFailure(code) from None


def watch_parent(parent):
    """Do not leave a CUDA worker orphaned after abrupt server process loss."""
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.argtypes=(wintypes.DWORD,wintypes.BOOL,wintypes.DWORD)
        kernel.OpenProcess.restype=wintypes.HANDLE
        kernel.WaitForSingleObject.argtypes=(wintypes.HANDLE,wintypes.DWORD)
        kernel.WaitForSingleObject.restype=wintypes.DWORD
        handle=kernel.OpenProcess(0x00100000,False,parent)
        if handle:
            while kernel.WaitForSingleObject(handle,1000)==258:
                pass
    else:
        while os.getppid()==parent:
            time.sleep(1)
    os._exit(3)


def main():
    folder = Path(sys.argv[1])
    options = json.loads((folder / "request.json").read_text())
    threading.Thread(target=watch_parent,args=(options['parent_pid'],),daemon=True).start()
    # Model weights must be prepared by the operator; do not download during a user's request.
    require_offline_model_loading()
    # Startup preflight may be older than the operator's weight files. Recheck in
    # the actual worker before any model is imported/loaded; never silently reload
    # modified weights that no longer match the prepared manifest.
    verify_weight_manifest(Path(options["weights_dir"]))
    verify_parser_assets(Path(options["weights_dir"]))
    verify_runtime_revisions()
    import torch
    device = options["device"]
    if device == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("cuda_unavailable")
        torch.cuda.set_per_process_memory_fraction(options["memory_fraction"])
        torch.cuda.reset_peak_memory_stats()
    person, garment = read_input(folder / "person.png"), read_input(folder / "garment.png")
    started = time.perf_counter()
    with model_stage('model_load_failed'):
        pipeline = load_verified_pipeline(weights_dir=str(check_weights(Path(options["weights_dir"]))), device=device)
        if device == "cuda":
            torch.cuda.synchronize()
    loaded = time.perf_counter()
    with model_stage('model_inference_failed'):
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
    except Exception as error:
        # No traceback/input paths/photos/provider payloads are logged.
        code = failure_code(error)
        try:
            (Path(sys.argv[1])/'error.json').write_text(json.dumps({'error':code}))
        except Exception:
            pass
        sys.exit(2)
