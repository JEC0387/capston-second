"""Local synthetic protocol/file tests. Not live generation or Android E2E."""
import io
import hashlib
import pytest
from PIL import Image
from p45_public_demo_trial import validate_api, result_url, save_png


def api():
    names = {
        "/prepare_request": ["input_images", "original_prompt", "enable_extend", "custom_size", "quality", "seed", "randomize_seed"],
        "/generate_request": ["original_prompt", "enable_extend", "custom_size", "log_dir", "seed", "height", "width", "negative_prompt"],
    }
    return {"named_endpoints": {k: {"parameters": [{"parameter_name": p} for p in v]} for k,v in names.items()}}


def test_reviewed_contract_only():
    validate_api(api())
    changed = api()
    changed["named_endpoints"]["/generate_request"]["parameters"].reverse()
    with pytest.raises(ValueError):
        validate_api(changed)


def test_prepare_placeholder_or_local_path_is_not_result():
    with pytest.raises((ValueError, TypeError)):
        result_url({"path": "/tmp/placeholder.png"})
    with pytest.raises((ValueError, TypeError)):
        result_url("C:/user/private.png")


def image_bytes(fmt="PNG", size=(768, 1024)):
    data=io.BytesIO()
    Image.new("RGB", size, (12, 34, 56)).save(data, format=fmt)
    return data.getvalue()


def test_decode_convert_reopen_and_no_overwrite(tmp_path):
    destination=tmp_path / "synthetic.png"
    encoded=image_bytes("WEBP")
    digest=save_png(encoded, destination)
    assert digest == hashlib.sha256(destination.read_bytes()).hexdigest()
    with Image.open(destination) as result:
        assert result.format == "PNG" and result.size == (768, 1024)
    with pytest.raises(FileExistsError):
        save_png(encoded, destination)
    assert hashlib.sha256(destination.read_bytes()).hexdigest() == digest


@pytest.mark.parametrize("data", [b"", b"not an image", image_bytes(size=(32, 32)), image_bytes()[:100]])
def test_invalid_result_does_not_create_success_file(tmp_path, data):
    destination=tmp_path / "not-created.png"
    with pytest.raises((ValueError, OSError, SyntaxError)):
        save_png(data, destination)
    assert not destination.exists()
