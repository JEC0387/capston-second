import hashlib
import json
import struct

import pytest

from p41_streaming import TensorReader
from p44_linear_gpu_probe import CHUNK, compare_chunk, main, make_graph, numpy_tensor, validate_layer


def test_bf16_exact_promotion_preserves_checkpoint_values(tmp_path):
    np = pytest.importorskip("numpy")
    header = json.dumps({"probe":{"dtype":"BF16","shape":[5],"data_offsets":[0,10]}}).encode()
    payload = struct.pack("<5H",0x0000,0x3f80,0xbf80,0x3fc0,0x0080)
    checkpoint = tmp_path/"small.safetensors"
    raw = struct.pack("<Q",len(header))+header+payload
    checkpoint.write_bytes(raw)
    reader = TensorReader(checkpoint,hashlib.sha256(raw).hexdigest())
    try:
        value = numpy_tensor(reader,"probe")
        np.testing.assert_array_equal(value,np.array([0,1,-1,1.5,2**-126],dtype=np.float32))
        assert reader.stats()["tensor_reads"] == 1
        assert reader.stats()["bytes_read"] == 10
        assert reader.stats()["unique_tensors_read"] == 1
    finally:
        reader.close()


def test_mismatch_is_failure_and_nonfinite_rejected():
    np = pytest.importorskip("numpy")
    expected = np.zeros((1,CHUNK,3),dtype=np.float32)
    assert not compare_chunk(expected,np.ones_like(expected),3)["pass"]
    with pytest.raises(ValueError,match="nonfinite"):
        compare_chunk(expected,np.full_like(expected,float("nan")),3)
    assert compare_chunk(expected,expected.copy(),3)["pass"]


def test_checkpoint_shapes_are_not_inferred_or_silently_changed():
    class WrongReader:
        entries = {"double_blocks.0.img_attn.qkv.weight":{"dtype":"BF16","shape":[2,2]}}
    with pytest.raises(ValueError,match="checkpoint_schema"):
        validate_layer(WrongReader(),"qkv")


def test_weights_are_runtime_inputs_not_initializers():
    pytest.importorskip("onnx")
    graph = make_graph(3,2).graph
    assert not graph.initializer
    assert [v.name for v in graph.input] == ["x","weight","bias"]
    assert [n.op_type for n in graph.node] == ["Transpose","MatMul","Add"]


def test_missing_runtime_records_failure_without_loading_model(tmp_path):
    output = tmp_path/"evidence"
    assert main(["--weights-dir",str(tmp_path),"--onnx-deps",str(tmp_path/"missing"),
                 "--runtime-deps",str(tmp_path/"missing"),"--output-dir",str(output)]) == 1
    result = json.loads((output/"result.json").read_text())
    assert result["failure"]["reason"] == "isolated_dependencies_missing"
    assert result["status"] == "FAILED"
    assert not result["png_generated"] and not result["android_executed"]
    assert not result["whole_model_executed"]


def test_existing_evidence_never_overwritten(tmp_path):
    output = tmp_path/"existing"
    output.mkdir()
    marker = output/"result.json"
    marker.write_text("previous")
    with pytest.raises(FileExistsError):
        main(["--weights-dir","unused","--onnx-deps","unused","--runtime-deps","unused",
              "--output-dir",str(output)])
    assert marker.read_text() == "previous"
