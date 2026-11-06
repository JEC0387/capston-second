import pytest

from p42_mobile_probe import PREFIX, subgraph_keys


def test_checkpoint_prefix_is_exact_and_other_block_cannot_substitute():
    entries = {PREFIX + "norm.scale":{"shape":[128], "dtype":"BF16"},
               "double_blocks.1.img_attn.norm.scale":{"shape":[128], "dtype":"BF16"}}
    assert subgraph_keys(entries, {"norm.scale":(128,)}) == {"norm.scale":PREFIX + "norm.scale"}
    del entries[PREFIX + "norm.scale"]
    with pytest.raises(ValueError, match="schema_keys"):
        subgraph_keys(entries, {"norm.scale":(128,)})


@pytest.mark.parametrize("entry", [{"shape":[64],"dtype":"BF16"}, {"shape":[128],"dtype":"F32"}])
def test_wrong_weight_shape_or_precision_cannot_fall_back_to_random_weights(entry):
    with pytest.raises(ValueError, match="shape_or_dtype"):
        subgraph_keys({PREFIX+"norm.scale":entry}, {"norm.scale":(128,)})


def test_added_unreviewed_attention_tensor_is_rejected():
    entries = {PREFIX+"scale":{"shape":[128],"dtype":"BF16"},
               PREFIX+"new.weight":{"shape":[128],"dtype":"BF16"}}
    with pytest.raises(ValueError, match="schema_keys"):
        subgraph_keys(entries, {"scale":(128,)})


def test_missing_dependency_directory_records_failure_without_loading_model(tmp_path):
    import json
    from p42_mobile_probe import main
    output = tmp_path / "evidence"
    assert main(["--source-dir", str(tmp_path / "source"),
                 "--source-manifest", str(tmp_path / "manifest"),
                 "--weights-dir", str(tmp_path / "weights"),
                 "--output-dir", str(output),
                 "--onnx-deps", str(tmp_path / "missing")]) == 1
    result = json.loads((output / "result.json").read_text())
    assert result["status"] == "FAILED"
    assert result["failure"]["stage"] == "dependency_directory_validation"
    assert not result["whole_model_exported"] and not result["png_generated"]


def test_explicit_rope_promotion_preserves_coordinates_and_input():
    torch = pytest.importorskip("torch")
    from p42_mobile_probe import export_position_ids
    original = torch.tensor([[0., 0.125, -4.5, 71.]], dtype=torch.float32)
    before = original.clone()
    promoted = export_position_ids(original)
    assert promoted.dtype == torch.float64
    assert torch.equal(original, before) and original.dtype == torch.float32
    assert torch.equal(promoted, torch.tensor([[0., 0.125, -4.5, 71.]], dtype=torch.float64))


def test_explicit_rope_promotion_produces_valid_onnx_einsum(tmp_path):
    torch = pytest.importorskip("torch")
    onnx = pytest.importorskip("onnx")
    ort = pytest.importorskip("onnxruntime")
    np = pytest.importorskip("numpy")
    from p42_mobile_probe import export_position_ids

    class PositionProduct(torch.nn.Module):
        def forward(self, ids):
            # The failing pinned source contract: FP32 positions, FP64 omega.
            omega = torch.tensor([1., 0.25, 0.0625], dtype=torch.float64)
            return torch.einsum("...n,d->...nd", export_position_ids(ids), omega)

    ids = torch.tensor([[0., 0.125, -4.5, 71.]], dtype=torch.float32)
    path = tmp_path / "position-product.onnx"
    torch.onnx.export(PositionProduct().eval(), (ids,), str(path),
                      opset_version=18, dynamo=False, input_names=["ids"], output_names=["positions"])
    onnx.checker.check_model(str(path))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])
    for values in (ids.numpy(), ids.numpy() + np.float32(0.5)):
        expected = values.astype(np.float64)[..., None] * np.array([1., 0.25, 0.0625], dtype=np.float64)
        actual = session.run(None, {"ids": values})[0]
        np.testing.assert_array_equal(actual, expected)


@pytest.mark.parametrize("dtype, invalid_value", [("float64", False), ("float32", True)])
def test_precomputed_position_boundary_rejects_double_or_nonfinite(dtype, invalid_value):
    torch = pytest.importorskip("torch")
    from p42_mobile_probe import precompute_positions
    value = torch.zeros(1,1,2,64,2,2, dtype=getattr(torch,dtype))
    if invalid_value:
        value[0,0,0,0,0,0] = float("nan")
    with pytest.raises(ValueError, match="precomputed_position"):
        precompute_positions(lambda _: value,
                             ((torch.zeros(1,2,1280), torch.zeros(1,2,3)),))
