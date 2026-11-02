"""No network, downloaded models, paid calls, or real photos are used in these tests."""
from pathlib import Path
from types import SimpleNamespace

import pytest
from PIL import Image

from local_vton import LocalVtonError, main, read_input, run_inference


@pytest.fixture
def inputs(tmp_path: Path):
    weights = tmp_path / "private-weights"
    (weights / "dwpose").mkdir(parents=True)
    (weights / "model.safetensors").write_bytes(b"fixture; not a real model")
    (weights / "dwpose" / "detector.onnx").write_bytes(b"fixture")
    person = tmp_path / "person.png"
    garment = tmp_path / "garment.jpg"
    Image.new("RGBA", (500, 850), (20, 120, 200, 0)).save(person)
    Image.new("RGB", (500, 650), "red").save(garment)
    return {"person": person, "garment": garment, "weights_dir": weights,
            "output": tmp_path / "generated.png", "category": "tops",
            "device": "cpu", "allow_cpu": True}


def test_offline_preflight_never_generates_or_loads_model(inputs):
    result = run_inference(**inputs, dry_run=True,
                           pipeline_factory=lambda **_: pytest.fail("must not construct pipeline"))
    assert result["generated"] is False
    assert not inputs["output"].exists()
    assert result["engine"] == "fashn-vton-1.5-local"


def test_genuine_pipeline_contract_and_atomic_image_output(inputs):
    calls = []

    class FakePipeline:
        def __call__(self, **kwargs):
            calls.append(kwargs)
            assert kwargs["person_image"].mode == "RGB"
            assert kwargs["garment_image"].mode == "RGB"
            assert kwargs["person_image"].getpixel((0, 0)) == (255, 255, 255)
            return SimpleNamespace(images=[Image.new("RGB", (576, 864), "blue")])

    def factory(**kwargs):
        assert kwargs["device"] == "cpu"
        assert kwargs["weights_dir"] == str(inputs["weights_dir"].resolve())
        return FakePipeline()

    result = run_inference(**inputs, pipeline_factory=factory)
    assert result["generated"] is True and inputs["output"].is_file()
    assert Image.open(inputs["output"]).size == (576, 864)
    assert calls[0]["category"] == "tops"
    assert calls[0]["num_samples"] == 1
    assert calls[0]["garment_photo_type"] == "flat-lay"
    assert calls[0]["segmentation_free"] is True
    assert calls[0]["num_timesteps"] == 30
    assert not list(inputs["output"].parent.glob(".vton-*"))


@pytest.mark.parametrize("category", ["bottoms", "one-pieces"])
def test_other_supported_categories(inputs, category):
    inputs["category"] = category
    result = run_inference(**inputs, dry_run=True)
    assert result["category"] == category


@pytest.mark.parametrize("bad", ["shoes", "glasses", "", "TOPS"])
def test_unavailable_categories_fail_closed(inputs, bad):
    inputs["category"] = bad
    with pytest.raises(LocalVtonError, match="Category"):
        run_inference(**inputs, dry_run=True)


def test_missing_weights_and_unconfigured_cpu_fail_closed(inputs):
    (inputs["weights_dir"] / "model.safetensors").unlink()
    with pytest.raises(LocalVtonError, match="weights missing"):
        run_inference(**inputs, dry_run=True)
    inputs["weights_dir"].joinpath("model.safetensors").write_bytes(b"x")
    inputs["allow_cpu"] = False
    with pytest.raises(LocalVtonError, match="allow-cpu"):
        run_inference(**inputs, dry_run=True)


def test_existing_result_never_overwritten_implicitly(inputs):
    inputs["output"].write_text("original - do not destroy")
    with pytest.raises(LocalVtonError, match="already exists"):
        run_inference(**inputs, dry_run=True)
    assert inputs["output"].read_text() == "original - do not destroy"
    assert run_inference(**inputs, dry_run=True, overwrite=True)["generated"] is False
    assert inputs["output"].read_text() == "original - do not destroy"


def test_model_failure_does_not_publish_fake_output(inputs):
    def factory(**kwargs):
        class Broken:
            def __call__(self, **kwargs):
                raise RuntimeError("internal model error with input data")
        return Broken()
    with pytest.raises(LocalVtonError, match="inference failed") as err:
        run_inference(**inputs, pipeline_factory=factory)
    assert "internal model error" not in str(err.value)
    assert not inputs["output"].exists()


def test_empty_or_invalid_image_result_does_not_publish(inputs):
    with pytest.raises(LocalVtonError, match="no valid image"):
        run_inference(**inputs, pipeline_factory=lambda **_: lambda **kwargs:
                      SimpleNamespace(images=[]))
    assert not inputs["output"].exists()


def test_metadata_removed_and_dimensions_limited(inputs, tmp_path):
    image = Image.new("RGB", (1600, 1000), "green")
    exif = image.getexif()
    exif[274] = 6
    path = tmp_path / "exif.jpg"
    image.save(path, exif=exif)
    decoded = read_input(path)
    assert decoded.mode == "RGB"
    assert max(decoded.size) <= 1296
    assert decoded.size[0] < decoded.size[1]
    assert not decoded.getexif()


def test_invalid_image_and_disguised_url_fail_closed(inputs, tmp_path):
    inputs["person"].write_bytes(b"not an image")
    with pytest.raises(LocalVtonError, match="decode"):
        run_inference(**inputs, dry_run=True)
    inputs["person"] = tmp_path / "https_photo.txt"
    inputs["person"].write_text("https://example.test/unsafe")
    with pytest.raises(LocalVtonError, match="existing JPEG"):
        run_inference(**inputs, dry_run=True)


def test_wrong_output_extension_and_sampling_fail_closed(inputs):
    with pytest.raises(LocalVtonError, match="Sampling"):
        run_inference(**inputs, num_timesteps=2, dry_run=True)
    inputs["output"] = inputs["output"].with_suffix(".jpg")
    with pytest.raises(LocalVtonError, match="PNG"):
        run_inference(**inputs, dry_run=True)


def test_cli_dry_run_is_explicitly_not_generation(inputs, capsys):
    args = ["--person", str(inputs["person"]), "--garment", str(inputs["garment"]),
            "--category", "tops", "--weights-dir", str(inputs["weights_dir"]),
            "--output", str(inputs["output"]), "--device", "cpu", "--allow-cpu",
            "--dry-run"]
    assert main(args) == 0
    assert '"generated": false' in capsys.readouterr().out.lower()
    assert not inputs["output"].exists()
    args.remove("--allow-cpu")
    assert main(args) == 2
    assert not inputs["output"].exists()
