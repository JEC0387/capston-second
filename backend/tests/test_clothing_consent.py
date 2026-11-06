"""Synthetic photo endpoint security tests; no model downloads or external API calls."""
import io

import numpy as np
import pytest
from PIL import Image

import main
from tests.test_auth_api import client_and_db, register, bearer


PATHS = [
    ("/api/clothing/classify", "clothing-classification-v1"),
    ("/api/object-detection", "object-detection-v1"),
]


@pytest.fixture
def authorized(monkeypatch):
    client, _, engine = client_and_db(monkeypatch)
    try:
        yield client, bearer(register(client)["accessToken"])
    finally:
        client.close()
        main.app.dependency_overrides.clear()
        engine.dispose()


def photo_bytes():
    output = io.BytesIO()
    Image.new("RGB", (64, 96), "white").save(output, "PNG")
    return output.getvalue()


@pytest.mark.parametrize("path,consent", PATHS)
@pytest.mark.parametrize("case", ["missing_auth", "invalid_auth", "device_id", "no_consent", "wrong_consent", "http"])
def test_auth_and_specific_consent_block_before_image_processing(authorized, monkeypatch, path, consent, case):
    client, access = authorized
    headers = {**access, "X-Photo-Processing-Consent": consent}
    scheme = "https"
    expected = 403
    if case == "missing_auth":
        headers.pop("Authorization"); expected = 401
    elif case == "invalid_auth":
        headers["Authorization"] = "Bearer invalid"; expected = 401
    elif case == "device_id":
        headers.pop("Authorization"); headers["X-Device-Id"] = "synthetic-legacy"; expected = 401
    elif case == "no_consent":
        headers.pop("X-Photo-Processing-Consent")
    elif case == "wrong_consent":
        headers["X-Photo-Processing-Consent"] = (
            "object-detection-v1" if consent == "clothing-classification-v1" else "clothing-classification-v1")
    else:
        scheme = "http"
        headers["X-Forwarded-Proto"] = "https"  # A client cannot assert trusted TLS.

    async def forbidden_read(_):
        pytest.fail("Photo decoded before authenticated, specific consent")

    monkeypatch.setattr(main, "read_analysis_photo", forbidden_read)
    response = client.post(scheme + "://testserver" + path, headers=headers,
                           files={"photo": ("synthetic.png", b"not-an-image", "image/png")})
    assert response.status_code == expected
    assert response.headers["cache-control"] == "no-store"


@pytest.mark.parametrize("path,consent", PATHS)
def test_authenticated_consent_preserves_photo_form_and_response_contract(authorized, monkeypatch, tmp_path, path, consent):
    client, access = authorized
    monkeypatch.chdir(tmp_path)
    calls = []

    def classify(image, conf_threshold):
        calls.append((image.size, conf_threshold))
        return {"imageWidth": image.width, "imageHeight": image.height, "candidates": []}

    def detect(image, conf_threshold):
        calls.append((image.size, conf_threshold))
        return [], {}, np.full((image.height, image.width, 3), 255, dtype=np.uint8)

    monkeypatch.setattr(main.fashion_detection, "classify_clothing", classify)
    monkeypatch.setattr(main.detection, "detect_objects", detect)
    response = client.post("https://testserver" + path,
                           headers={**access, "X-Photo-Processing-Consent": consent},
                           data={"conf_threshold": "0.4"},
                           files={"photo": ("synthetic.png", photo_bytes(), "image/png")})
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert calls == [((64, 96), 0.4)]
    if path.endswith("classify"):
        assert response.json() == {"imageWidth": 64, "imageHeight": 96, "candidates": []}
    else:
        assert response.json()["resultImageUrl"].startswith("data:image/jpeg;base64,")
    assert not (tmp_path / "uploads").exists()


@pytest.mark.parametrize("path,consent", PATHS)
def test_authorized_requests_keep_existing_input_guards(authorized, monkeypatch, path, consent):
    client, access = authorized
    headers = {**access, "X-Photo-Processing-Consent": consent}
    endpoint = "https://testserver" + path
    assert client.post(endpoint, headers=headers).status_code == 422
    assert client.post(endpoint, headers=headers,
                       files={"photo": ("bad.txt", b"text", "text/plain")}).status_code == 400
    assert client.post(endpoint, headers=headers,
                       files={"photo": ("bad.png", b"invalid", "image/png")}).status_code == 400
    assert client.post(endpoint, headers=headers, data={"conf_threshold": "1.1"},
                       files={"photo": ("photo.png", photo_bytes(), "image/png")}).status_code == 400
    monkeypatch.setattr(main, "MAX_ANALYSIS_UPLOAD", 16)
    assert client.post(endpoint, headers=headers,
                       files={"photo": ("big.png", b"x" * 17, "image/png")}).status_code == 413


def test_model_unavailable_response_does_not_expose_provider_or_local_paths(authorized, monkeypatch):
    client, access = authorized

    def unavailable(*args, **kwargs):
        raise FileNotFoundError("synthetic-private-filesystem-and-provider-message")

    monkeypatch.setattr(main.fashion_detection, "classify_clothing", unavailable)
    response = client.post("https://testserver/api/clothing/classify",
                           headers={**access, "X-Photo-Processing-Consent": "clothing-classification-v1"},
                           files={"photo": ("photo.png", photo_bytes(), "image/png")})
    assert response.status_code == 503
    assert response.json() == {"detail": "clothing_classifier_unavailable"}
    assert "synthetic-private" not in response.text
