"""Small contract tests only. No downloaded model, weight loading, or inference proof."""
import importlib
import json
import os
from pathlib import Path
import socket
import sys

import pytest

from p40_flatlay_probe import NAMESPACE, REVISION, Evidence, batch_model_image, namespace_source, offline_network_guard, quantize_linear_leaves, sha256, validate_mode, verify_source


def source_tree(tmp_path):
    root = tmp_path / "code"
    paths = {
        "src/fashn_vton/__init__.py": "raise AssertionError('parser-dependent top init must not execute')\n",
        "src/fashn_vton/preprocessing/__init__.py": "raise AssertionError('agnostic/parser init must not execute')\n",
        "src/fashn_vton/preprocessing/transforms.py": "MARKER = 'real source module'\n",
    }
    for name, text in paths.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({"revision": REVISION, "files": {name: sha256(root / name) for name in paths}}))
    return root, manifest


def test_source_manifest_rejects_modified_and_added_python_files(tmp_path):
    root, manifest = source_tree(tmp_path)
    verify_source(root, manifest)
    target = root / "src/fashn_vton/preprocessing/transforms.py"
    target.write_text("MODIFIED = True\n")
    with pytest.raises(ValueError, match="source_hash_mismatch"):
        verify_source(root, manifest)
    (root / "src/fashn_vton/extra.py").write_text("pass\n")
    with pytest.raises(ValueError, match="source_manifest_coverage"):
        verify_source(root, manifest)


def test_separate_namespace_executes_selected_source_without_parser_initializers(tmp_path):
    root, manifest = source_tree(tmp_path)
    before = set(sys.modules)
    verify_source(root, manifest)
    try:
        namespace_source(root)
        actual = importlib.import_module(NAMESPACE + ".preprocessing.transforms")
        assert actual.MARKER == "real source module"
        assert Path(actual.__file__).resolve() == (root / "src/fashn_vton/preprocessing/transforms.py").resolve()
        assert "fashn_human_parser" not in set(sys.modules) - before
        with pytest.raises(RuntimeError, match="fresh_experiment_process_required"):
            namespace_source(root)
    finally:
        for name in list(sys.modules):
            if name == NAMESPACE or name.startswith(NAMESPACE + "."):
                del sys.modules[name]


@pytest.mark.parametrize("category,flat,stage,dtype", [
    ("shoes", True, "infer", "float32"),
    ("tops", False, "infer", "float32"),
    ("bottoms", True, "step", "checkpoint"),
])
def test_unsupported_or_misleading_modes_are_rejected(category, flat, stage, dtype):
    with pytest.raises(ValueError):
        validate_mode(stage, category, flat, 20, dtype)


def test_offline_guard_blocks_connection_without_network_and_restores_api():
    original = socket.create_connection
    original_environment = os.environ.get("HF_HUB_OFFLINE")
    with offline_network_guard():
        with pytest.raises(RuntimeError, match="network_disallowed"):
            socket.create_connection(("example.invalid", 443))
    assert socket.create_connection is original
    assert os.environ.get("HF_HUB_OFFLINE") == original_environment


def test_explicit_bfloat16_experiment_is_allowed_without_claiming_operator_support():
    validate_mode("step", "tops", True, 20, "bfloat16")
    validate_mode("infer", "bottoms", True, 20, "bfloat16")
    validate_mode("infer", "tops", True, 20, "int8-dynamic")


def test_real_tensor_grayscale_pose_gains_channel_axis_and_preserves_pixels():
    # Optional only in the ordinary app environment; P40's torch environment
    # must run this test (a skip there is not a passed regression).
    torch = pytest.importorskip("torch")
    pose = torch.tensor([[-1.0, 0.0, 1.0], [1.0, 0.0, -1.0]])
    rgb = torch.ones((3, 2, 3))
    noisy_images = torch.zeros((1, 3, 2, 3))
    # Actual legacy rank failure, using real tensors rather than a mocked model.
    with pytest.raises(RuntimeError, match="same number of dimensions"):
        torch.cat((noisy_images, rgb.unsqueeze(0), pose.unsqueeze(0)), dim=1)
    for dtype in (torch.float32, torch.bfloat16):
        batched_pose = batch_model_image(pose, dtype)
        batched_rgb = batch_model_image(rgb, dtype)
        assert batched_pose.shape == (1, 1, 2, 3)
        assert batched_rgb.shape == (1, 3, 2, 3)
        assert batched_pose.dtype == batched_rgb.dtype == dtype
        assert torch.equal(batched_pose[0, 0], pose.to(dtype=dtype))
        assert torch.equal(batched_rgb[0], rgb.to(dtype=dtype))
        assert torch.cat((noisy_images.to(dtype=dtype), batched_rgb, batched_pose), dim=1).shape == (1, 7, 2, 3)
    with pytest.raises(ValueError, match="hw_or_chw"):
        batch_model_image(noisy_images, torch.float32)


def test_real_dynamic_int8_linear_preserves_aliases_bias_shape_and_bounded_output_error():
    torch = pytest.importorskip("torch")
    # Exact BF16-representable fixture values and independently calculated FP32
    # result. This tests a real quantized kernel, not a stub or model-quality claim.
    layer = torch.nn.Linear(3, 2, bias=True).to(dtype=torch.bfloat16)
    with torch.no_grad():
        layer.weight.copy_(torch.tensor([[0.5, -0.25, 0.75], [-0.5, 0.25, 0.5]]))
        layer.bias.copy_(torch.tensor([0.125, -0.25]))
    model = torch.nn.ModuleDict({"first": layer, "same_layer": layer,
                                "nested": torch.nn.Sequential(torch.nn.Linear(2, 2, bias=False)),
                                "embedding": torch.nn.Embedding(2, 3).to(dtype=torch.bfloat16)})
    with torch.no_grad():
        model["nested"][0].weight.copy_(torch.eye(2))
    stats = quantize_linear_leaves(model)
    assert stats["linear_modules"] == 2
    assert stats["quantized_weight_elements"] == 10
    assert stats["shared_module_aliases"] == 1
    assert model["first"] is model["same_layer"]
    assert tuple(model["first"].weight().shape) == (2, 3)
    assert tuple(model["first"].bias().shape) == (2,)
    assert model["first"].weight().dtype == torch.qint8
    assert model["first"].bias().dtype == torch.float32
    assert model["nested"][0].bias() is None
    assert model["embedding"].weight.dtype == torch.float32
    inputs = torch.tensor([[1.0, 2.0, -1.0], [-1.0, 0.0, 1.0], [0.5, -0.5, 0.25]])
    expected = torch.tensor([[-0.625, -0.75], [0.375, 0.75], [0.6875, -0.5]])
    with torch.inference_mode():
        actual = model["first"](inputs)
        repeated = model["same_layer"](inputs)
        without_bias = model["nested"](torch.tensor([[1.0, -1.0], [0.0, 0.5]]))
    assert torch.isfinite(actual).all()
    assert torch.equal(actual, repeated)
    assert actual.shape == expected.shape
    assert torch.max(torch.abs(actual - expected)).item() < 0.03
    assert torch.max(torch.abs(without_bias - torch.tensor([[1.0, -1.0], [0.0, 0.5]]))).item() < 0.02


def test_evidence_never_reuses_old_output_folder(tmp_path):
    folder = tmp_path / "new-run"
    evidence = Evidence(folder)
    evidence.event("source_verification")
    evidence.finish(success=True, inference_complete=False, png_created=False)
    record = json.loads((folder / "final.json").read_text())
    assert not record["inference_complete"]
    assert not record["android_verified"]
    assert not record["parser_used"]
    with pytest.raises(FileExistsError):
        Evidence(folder)
