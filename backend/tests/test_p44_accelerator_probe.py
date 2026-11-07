import json
import math

import pytest

from p44_accelerator_probe import (
    compare_arrays, main, official_position_formula,
    validate_dml_profile, validate_execution_devices,
)


@pytest.mark.parametrize("devices", [[], ["CPU"], ["GPU", "CPU"], ["AUTO"], ["NPU"]])
def test_non_gpu_execution_cannot_pass(devices):
    with pytest.raises(ValueError, match="non_gpu"):
        validate_execution_devices(devices)


def test_gpu_device_identifier_is_preserved():
    assert validate_execution_devices(["GPU.0"]) == ["GPU.0"]


@pytest.mark.parametrize("events", [[], [{"cat":"Node","args":{"provider":"CPUExecutionProvider"}}],
    [{"cat":"Node","args":{"provider":"DmlExecutionProvider"}},
     {"cat":"Node","args":{"provider":"CPUExecutionProvider"}}]])
def test_directml_profile_must_show_actual_gpu_execution_only(events):
    with pytest.raises(ValueError):
        validate_dml_profile(events)


def test_directml_profile_ignores_non_execution_metadata():
    events = [{"cat":"Session","args":{}},
              {"cat":"Node","args":{"provider":"DmlExecutionProvider"}}]
    assert validate_dml_profile(events) == {"executed_node_events":1,"providers":["DmlExecutionProvider"]}


def test_official_rotation_zero_and_first_axis_quarter_turn():
    np = pytest.importorskip("numpy")
    ids = np.zeros((1,16,3),dtype=np.float32)
    pe = official_position_formula(ids)
    assert pe.shape == (1,1,16,64,2,2) and pe.dtype == np.float32
    np.testing.assert_array_equal(pe[0,0,0],np.broadcast_to(np.eye(2,dtype=np.float32),(64,2,2)))
    ids[0,0,0] = math.pi/2
    turned = official_position_formula(ids)
    # Independently known unit rotation; float32 input angle explains tiny cos.
    np.testing.assert_allclose(turned[0,0,0,0],[[0,-1],[1,0]],atol=1e-7,rtol=0)
    np.testing.assert_array_equal(turned[0,0,0,8:],pe[0,0,0,8:])


def test_finite_wrong_output_cannot_be_reported_as_gpu_success():
    np = pytest.importorskip("numpy")
    reference = np.zeros((1,16,1280),dtype=np.float32)
    with pytest.raises(AssertionError):
        compare_arrays(reference,np.ones_like(reference))
    with pytest.raises(ValueError,match="finite"):
        compare_arrays(reference,np.full_like(reference,float("nan")))


def test_bad_runtime_path_records_failure_without_model_load(tmp_path):
    output = tmp_path/"new-evidence"
    assert main(["--model",str(tmp_path/"model.onnx"),"--p42-result",str(tmp_path/"p42.json"),
                 "--engine","directml","--runtime-deps",str(tmp_path/"missing"),
                 "--output-dir",str(output)]) == 1
    result = json.loads((output/"result.json").read_text())
    assert result["status"] == "FAILED"
    assert result["failure"]["reason"] == "isolated_runtime_directory_missing"
    assert not result["png_generated"] and not result["whole_model_executed"]
    assert not result["android_executed"]


def test_existing_evidence_directory_is_never_overwritten(tmp_path):
    output = tmp_path/"evidence"
    output.mkdir()
    marker = output/"result.json"
    marker.write_text("previous evidence",encoding="utf-8")
    with pytest.raises(FileExistsError):
        main(["--model","unused","--p42-result","unused","--engine","openvino",
              "--runtime-deps","unused","--output-dir",str(output)])
    assert marker.read_text() == "previous evidence"
