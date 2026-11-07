"""Negative synthetic routing tests, not approval or verification of real photos."""
import pytest

from p45_public_inputs import (
    MAX_INPUT_BYTES, PublicInputRejected, load_public_inputs, validate_output_url,
)


def test_unknown_image_bytes_are_rejected_without_logging_body(tmp_path):
    sample = b"synthetic-unapproved-person-data"
    (tmp_path / "person.webp").write_bytes(sample)
    with pytest.raises(PublicInputRejected, match="unapproved_input_hash") as error:
        load_public_inputs(tmp_path)
    assert sample.decode() not in str(error.value)
    assert str(tmp_path) not in str(error.value)


def test_missing_fixed_name_cannot_fall_back_to_other_image(tmp_path):
    (tmp_path / "different.webp").write_bytes(b"unapproved")
    with pytest.raises(PublicInputRejected, match="unavailable"):
        load_public_inputs(tmp_path)


@pytest.mark.parametrize("root", ["https://example.com/image.webp", "file:///private",
                                  "", "a\nprivate", None, ["person.webp"]])
def test_no_url_or_arbitrary_input_list(root):
    with pytest.raises(PublicInputRejected):
        load_public_inputs(root)


@pytest.mark.parametrize("size", [0, MAX_INPUT_BYTES + 1])
def test_size_limit_before_hash(tmp_path, size):
    path = tmp_path / "person.webp"
    with path.open("wb") as output:
        output.truncate(size)
    with pytest.raises(PublicInputRejected, match="size"):
        load_public_inputs(tmp_path)


def test_symlink_cannot_point_to_outside_sample(tmp_path):
    outside = tmp_path / "outside.webp"
    outside.write_bytes(b"not an approved sample")
    root = tmp_path / "selected"
    root.mkdir()
    try:
        (root / "person.webp").symlink_to(outside)
    except (OSError, NotImplementedError):
        pytest.skip("This test environment cannot create filesystem symlinks")
    with pytest.raises(PublicInputRejected, match="links"):
        load_public_inputs(root)


def test_exact_official_result_endpoint_allowed():
    value = "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/gradio/abcd/image.png"
    assert validate_output_url(value) == value


@pytest.mark.parametrize("value", [
    "http://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space.evil.example/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space:443/gradio_api/file=/tmp/image.png",
    "https://user@qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space@evil.example/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-edit.hf.space/gradio_api/file=/tmp/image.png",
    "https://127.0.0.1/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png?download=1",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png?",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png#part",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/../private.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/%2e%2e/private.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/%252e%252e/private.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=//evil.example/image.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=https://evil.example/image.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/%5cprivate.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/%00private.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/%ffprivate.png",
    " https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png",
    "https://qwen-qwen-image-2-1.hf.space/gradio_api/file=/tmp/image.png\n",
    None,
])
def test_other_output_destinations_or_ambiguous_paths_rejected(value):
    with pytest.raises(PublicInputRejected):
        validate_output_url(value)
