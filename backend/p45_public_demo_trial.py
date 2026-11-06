"""Explicit, one-shot, anonymous public research trial; NOT an app/cloud engine.

No server routes, credentials, model fallback, automatic retry or paid endpoint.
Use only in the separate P45 client venv. Inputs are pinned public sample hashes.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import time
from urllib.request import build_opener, HTTPRedirectHandler, Request

from p45_trial_journal import TrialJournal, TrialRequest
from p45_public_inputs import load_public_inputs, validate_output_url

HOST = "https://qwen-qwen-image-2-1.hf.space"
REVISION = "5694809f4c56a4e8c40787b5392cecac36493c5d"
PROMPT = (
    "Edit image 1 into one realistic full-body fashion photograph of the SAME person. "
    "Preserve that person's face, hair, skin, body proportions, pose, hands, camera angle "
    "and the background. Replace the outer top with ONLY the pink quilted jacket from "
    "image 2, preserving its pink color, quilting, collar, sleeves and details; do not "
    "copy the second model, face, body or beige trousers. Replace the trousers with "
    "the cream pleated linen trousers in image 3, adapted visually to the person in "
    "image 1; do not copy the mannequin or museum background. Replace BOTH shoes "
    "with a matching left and right pair of the silver Mary Jane shoe in image 4, "
    "preserving its toe, strap, buckle, metallic texture and heel shape. Remove the "
    "previous outer top, trousers and shoes without changing the person's identity. "
    "Keep both feet visible with natural ankle and trouser boundaries, realistic "
    "cloth folds, hand occlusion, contact shadows and consistent lighting. Exactly "
    "one person, no collage, no text, no extra shoes or limbs. This is a visual "
    "research edit, not a body measurement or fit guarantee."
)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def now():
    return datetime.now(timezone.utc).isoformat()


def read_https(url, limit):
    # Callers either use a fixed official metadata URL or validate_output_url.
    with build_opener(NoRedirect).open(Request(url, headers={"User-Agent": "FashionAI-P45-public-research"}), timeout=45) as r:
        content = r.read(limit + 1)
        if len(content) > limit:
            raise ValueError("response_too_large")
        return content


def validate_api(info):
    expected = {
        "/prepare_request": ["input_images", "original_prompt", "enable_extend", "custom_size", "quality", "seed", "randomize_seed"],
        "/generate_request": ["original_prompt", "enable_extend", "custom_size", "log_dir", "seed", "height", "width", "negative_prompt"],
    }
    for name, parameters in expected.items():
        actual = [p["parameter_name"] for p in info["named_endpoints"][name]["parameters"]]
        if actual != parameters:
            raise ValueError("public_api_contract_changed")


def result_url(result):
    if isinstance(result, dict):
        value = result.get("url")
    elif isinstance(result, str) and result.startswith("https://"):
        value = result
    else:
        value = getattr(result, "url", None)
    return validate_output_url(value)


def save_png(encoded, destination):
    from PIL import Image
    if not 0 < len(encoded) <= 16 * 1024 * 1024:
        raise ValueError("invalid_image_size")
    with Image.open(io.BytesIO(encoded)) as source:
        if source.format not in {"PNG", "JPEG", "WEBP"} or source.size != (768, 1024):
            raise ValueError("unexpected_result_format_or_dimensions")
        source.verify()
    with Image.open(io.BytesIO(encoded)) as source:
        source.load()
        with destination.open("xb") as output:
            source.convert("RGB").save(output, format="PNG")
    with Image.open(destination) as reopened:
        reopened.load()
        if reopened.format != "PNG" or reopened.size != (768, 1024):
            raise ValueError("saved_png_verification_failed")
    return hashlib.sha256(destination.read_bytes()).hexdigest()


def safe_error_fields(exc):
    """Provider errors may contain signed URLs or paths even for public inputs.

    Never stringify an exception or persist its arbitrary type name. Preserve
    only reviewed local reason codes; the caller also records the failed phase.
    """
    if isinstance(exc, TimeoutError):
        return {"error_type": "timeout", "error": "request_timeout"}
    if isinstance(exc, ValueError):
        known = {
            "response_too_large", "public_api_contract_changed",
            "invalid_image_size", "unexpected_result_format_or_dimensions",
            "saved_png_verification_failed", "reviewed_space_revision_not_running",
            "unexpected_auth_header",
        }
        reason = exc.args[0] if type(exc) is ValueError and len(exc.args) == 1 else None
        code = reason if type(reason) is str and reason in known else "validation_failed"
        return {"error_type": "validation", "error": code}
    return {"error_type": "external_request", "error": "external_request_failed"}


def main(prompt: str = PROMPT):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--execute-public-research-once", action="store_true")
    args = parser.parse_args()
    public = load_public_inputs(args.inputs)
    request = TrialRequest(*(item.sha256 for item in public), hashlib.sha256(prompt.encode()).hexdigest())
    if not args.execute_public_research_once:
        print(json.dumps({"preflight": "PASS", "network": False, "fingerprint": request.fingerprint}))
        return
    # One fixed evidence path and durable allowance for this experiment. Never
    # change this path to retry after a remote rejection or uncertain response.
    args.evidence.mkdir(parents=True, exist_ok=True)
    journal = TrialJournal(args.evidence / "one-shot.sqlite3")
    reservation = journal.reserve(request)
    record = {"engine": "qwen-official-public-research", "model": "Qwen/Qwen-Image-2.1",
              "revision": REVISION, "app_initiated": False, "android_verified": False,
              "quota_mode": "anonymous_public_ZeroGPU_no_account_no_billing",
              "cost_charged_by_this_client": 0, "gpu_model": "NOT_REPORTED",
              "peak_vram": "NOT_REPORTED", "quality": "NOT_EVALUATED",
              "created_at": now(), "fingerprint": request.fingerprint,
              "generation_submit_count": 0, "phase": "preflight"}
    client = None
    def persist():
        record["journal"] = asdict(journal.get(reservation.reservation_id))
        (args.evidence / "execution.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
        print(json.dumps({"phase": record["phase"], "at": now()}), flush=True)
    start = time.monotonic()
    persist()
    try:
        meta = json.loads(read_https("https://huggingface.co/api/spaces/Qwen/Qwen-Image-2.1", 1024 * 1024))
        if meta.get("sha") != REVISION or meta.get("runtime", {}).get("stage") != "RUNNING":
            raise ValueError("reviewed_space_revision_not_running")
        # Process-local isolation: never discover or forward existing HF tokens.
        for name in ("HF_TOKEN", "HUGGING_FACE_HUB_TOKEN", "HF_API_TOKEN"):
            os.environ.pop(name, None)
        os.environ["HF_HOME"] = str(args.evidence / "empty-hf-home")
        os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"
        os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
        os.environ["GRADIO_ANALYTICS_ENABLED"] = "False"
        import httpx
        from gradio_client import Client, handle_file
        client = Client(HOST, token=False, max_workers=1, verbose=False,
                        analytics_enabled=False, download_files=False,
                        httpx_kwargs={"timeout": httpx.Timeout(90, connect=15)})
        if any(k.lower() == "authorization" for k in client.headers):
            raise ValueError("unexpected_auth_header")
        validate_api(client.view_api(return_format="dict", print_info=False))
        staged = args.evidence / "public-input-copy"
        staged.mkdir(exist_ok=False)
        gallery = []
        for item in public:
            path = staged / item.path.name
            with path.open("xb") as output:
                output.write(item.data)
            gallery.append({"image": handle_file(str(path)), "caption": None})
        journal.mark_submitting(reservation.reservation_id)
        record["phase"] = "prepare"
        record["external_public_input_upload_started_at"] = now()
        persist()
        # Same client/session is required for the server's gr.State. The returned
        # prepare placeholder is deliberately ignored and never a generated image.
        prepare = client.submit(gallery, prompt, False, True, "speed", 42, False, api_name="/prepare_request")
        prepare.result(timeout=180)
        record["phase"] = "generation"
        record["generation_started_at"] = now()
        record["generation_submit_count"] = 1
        persist()
        job = client.submit(prompt, False, True, "./generation_logs_paper_case", 42, 1024, 768, "", api_name="/generate_request")
        result = job.result(timeout=480)
        record["result_received_at"] = now()
        record["phase"] = "download"
        persist()
        url = result_url(result)
        raw = read_https(url, 16 * 1024 * 1024)
        output_sha = save_png(raw, args.evidence / "new-generated-outfit.png")
        journal.mark_received(reservation.reservation_id, output_sha)
        record.update(phase="received", saved_at=now(), saved_png_sha256=output_sha,
                      downloaded_bytes=len(raw), downloaded_sha256=hashlib.sha256(raw).hexdigest(),
                      elapsed_seconds=round(time.monotonic()-start, 3), pc_reopen="PASS")
        persist()
    except Exception as exc:
        state = journal.get(reservation.reservation_id).state
        if state == "RESERVED":
            journal.mark_blocked(reservation.reservation_id, "preflight_failed")
        elif state == "SUBMITTING":
            journal.mark_unknown(reservation.reservation_id, "remote_or_download_error_no_retry")
        # Public inputs do not make provider exception messages safe to retain.
        record.update(failed_phase=record["phase"], phase="blocked",
                      **safe_error_fields(exc), elapsed_seconds=round(time.monotonic()-start, 3))
        persist()
        raise SystemExit(2)
    finally:
        if client is not None:
            client.close()


if __name__ == "__main__":
    main()
