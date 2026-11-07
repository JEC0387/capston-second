"""Temporary loopback-only synthetic wire verifier; no AI provider or real user auth.

The diagnostic capability guards this one-shot local test only. It is not a
production API endpoint or substitute for the app's HTTPS/Bearer acceptance.
"""
import argparse
import hashlib
import hmac
import json
from pathlib import Path
import sys
import threading

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "backend"))
from fastapi import FastAPI, HTTPException, Request
from verify_android_outfit_wire import verify
import uvicorn


def create_validator(folder, token):
    folder = Path(folder).resolve()
    receipt = json.loads((folder / "receipt.json").read_text(encoding="utf8"))
    if receipt.get("synthetic_only") is not True or receipt.get("actual_device") is not True:
        raise ValueError("actual_device_synthetic_receipt_required")
    body_sha = receipt["body_sha256"]
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    app.state.received = False
    app.state.receive_lock = threading.Lock()

    def authenticate(request):
        if not hmac.compare_digest(request.headers.get("authorization", ""), "Bearer " + token):
            raise HTTPException(401, "diagnostic_auth_required")

    @app.get("/p57/health")
    async def health(request: Request):
        authenticate(request)
        return {"diagnostic_only": True, "external_provider_calls": 0}

    @app.post("/p57/validate")
    async def validate(request: Request):
        authenticate(request)
        if request.headers.get("content-type") != "application/json":
            raise HTTPException(415, "json_required")
        # Reserve exactly one authenticated attempt before the first awaited stream read.
        # Even an invalid/disconnected attempt stays consumed; no automatic retry is implied.
        with app.state.receive_lock:
            if app.state.received:
                raise HTTPException(409, "already_received_no_retry")
            app.state.received = True
        raw = bytearray()
        async for chunk in request.stream():
            if len(raw) + len(chunk) > 29 * 1024 * 1024:
                raise HTTPException(413, "diagnostic_body_limit")
            raw.extend(chunk)
        digest = hashlib.sha256(raw).hexdigest()
        if digest != body_sha or request.headers.get("x-p57-body-sha256") != body_sha:
            raise HTTPException(422, "wire_hash_mismatch")
        # The body sent by the installed APK must match its independently pulled evidence.
        if bytes(raw) != (folder / "payload.json").read_bytes():
            raise HTTPException(422, "wire_bytes_mismatch")
        with (folder / "http-received-payload.json").open("xb") as out:
            out.write(raw)
        result = verify(folder)
        result.update(actual_device=True, transport="S25+ diagnostic HTTP over USB adb reverse to PC loopback",
                      app_ui_transport_tested=False, production_https_auth_tested=False,
                      received_body_sha256=digest, external_provider_calls=0)
        with (folder / "HTTP_SERVER_VERIFIED.json").open("x", encoding="utf8") as out:
            json.dump(result, out, indent=2)
        return {"status": "PASS", "received_body_sha256": digest, "external_provider_calls": 0}

    return app


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    token = args.token_file.read_text(encoding="ascii").strip()
    if len(token) != 64 or any(c not in "0123456789abcdef" for c in token):
        raise SystemExit("diagnostic capability malformed")
    uvicorn.run(create_validator(args.folder, token), host="127.0.0.1", port=args.port,
                access_log=False, log_level="warning")
