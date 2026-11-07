"""Privacy regression tests. External providers and paid FASHN are never called."""
import base64
import io
import json
from unittest import mock

import numpy as np
from fastapi.testclient import TestClient
from PIL import Image

import main
import naver
import unsplash
from tests.test_auth_api import client_and_db, register, bearer


def test_legacy_body_analysis_rejects_upload_without_saving(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    client = TestClient(main.app)
    response = client.post("/api/body-analysis", files={"photo": ("person.jpg", b"private photo", "image/jpeg")})
    assert response.status_code == 410
    assert not (tmp_path / "uploads").exists()


def test_public_upload_route_is_gone_without_deleting_existing_files(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    legacy = tmp_path / "uploads"
    legacy.mkdir()
    (legacy / "old.jpg").write_bytes(b"private legacy bytes")
    client = TestClient(main.app)
    assert client.get("/uploads/old.jpg").status_code == 404
    assert (legacy / "old.jpg").read_bytes() == b"private legacy bytes"


def test_object_detection_result_is_inline_and_never_publicly_saved(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    def fake_detection(image, conf_threshold):
        return [{"label": "person", "confidence": .9, "bbox": [1, 1, 10, 10]}], {"person": 1}, np.full((64, 64, 3), 128, dtype=np.uint8)
    monkeypatch.setattr(main.detection, "detect_objects", fake_detection)
    photo = io.BytesIO()
    Image.new("RGB", (64, 64), "white").save(photo, "JPEG")
    client, _, engine = client_and_db(monkeypatch)
    try:
        headers = bearer(register(client)["accessToken"])
        headers["X-Photo-Processing-Consent"] = "object-detection-v1"
        result = client.post("https://testserver/api/object-detection", headers=headers,
                             files={"photo": ("item.jpg", photo.getvalue(), "image/jpeg")})
        assert result.status_code == 200, result.text
        assert result.headers["cache-control"] == "no-store"
        url = result.json()["resultImageUrl"]
        assert url.startswith("data:image/jpeg;base64,")
        assert base64.b64decode(url.split(",", 1)[1]).startswith(b"\xff\xd8")
        assert not (tmp_path / "uploads").exists()
    finally:
        client.close()
        main.app.dependency_overrides.clear()
        engine.dispose()


def test_shopping_insight_proxy_uses_server_credentials_only(monkeypatch):
    naver._insight_cache = None
    calls = []
    payload = {"results": [{"data": [{"period": "2026-01", "ratio": 42.0}]}]}
    def fake_open(req, timeout=None):
        calls.append(req)
        return io.BytesIO(json.dumps(payload).encode())
    with mock.patch.object(naver, "NAVER_API_KEY_ID", "mock-id"), \
            mock.patch.object(naver, "NAVER_API_KEY", "mock-key"), \
            mock.patch.object(naver.urllib.request, "urlopen", fake_open):
        client = TestClient(main.app)
        assert client.get("/api/naver/shopping-insight").json() == payload
        assert client.get("/api/naver/shopping-insight").json() == payload
    assert len(calls) == 1
    assert calls[0].get_header("X-ncp-apigw-api-key") == "mock-key"


def test_optional_unsplash_proxy_fails_closed_without_server_key(monkeypatch):
    monkeypatch.delenv("UNSPLASH_ACCESS_KEY", raising=False)
    client = TestClient(main.app)
    assert client.get("/api/unsplash/photo", params={"query": "coat"}).status_code == 503
    assert client.get("/api/unsplash/photo").status_code == 422
