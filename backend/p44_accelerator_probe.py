"""Bounded offline PC GPU probe of the P42 real-weight attention subgraph.

Run serially under p41_monitor.py. This is NOT whole-model or Android inference.
No fallback, model download, image input, engine registration, or GPU driver
installation is performed. CPU reference sessions are freed before GPU compile.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import json
from pathlib import Path
import sys
import time

from p40_flatlay_probe import offline_network_guard, sha256

MODEL_SHA = "59593a1748881b4f040b476b64f79da42ac4379f4dbb951c6af8fb385f6f632a"
MODEL_BYTES = 26246974
RTOL, ATOL = 1e-4, 1e-5  # P42 numerical gate, not a visual-quality criterion.


def array_sha(array):
    return hashlib.sha256(array.tobytes(order="C")).hexdigest()


def official_position_formula(ids):
    """NumPy equivalent of pinned FASHN EmbedND(128,10000,(16,56,56)).

    Its bytes MUST match both P42 tensors actually produced by official Torch
    EmbedND before it is accepted as a fixture. No approximate PE is accepted.
    """
    import numpy as np
    if ids.shape != (1, 16, 3) or not np.isfinite(ids).all():
        raise ValueError("invalid_synthetic_position_ids")
    axes = []
    for index, dim in enumerate((16, 56, 56)):
        scale = np.arange(0, dim, 2, dtype=np.float64) / dim
        omega = 1.0 / (10000.0 ** scale)
        angle = ids[..., index, None].astype(np.float64) * omega
        c, s = np.cos(angle), np.sin(angle)
        axes.append(np.stack((c, -s, s, c), axis=-1).reshape(1,16,dim//2,2,2).astype(np.float32))
    return np.concatenate(axes, axis=-3)[:, None, ...]


def synthetic_samples(p42_record):
    import numpy as np
    expected = p42_record["export_adapter"]["position_tensor_sha256"]
    if len(expected) != 2 or p42_record["onnx"]["sha256"] != MODEL_SHA:
        raise ValueError("p42_reference_identity_mismatch")
    rng = np.random.default_rng(4401)
    x = rng.standard_normal((1,16,1280)).astype(np.float32)
    ids = np.zeros((1,16,3), dtype=np.float32)
    ids[0,:,1], ids[0,:,2] = np.arange(16)//4, np.arange(16)%4
    pairs = ((x, ids), (x*np.float32(.7)+np.float32(.2), ids+np.array([[[0,1,2]]], dtype=np.float32)))
    samples = []
    for index, (features, positions) in enumerate(pairs):
        pe = official_position_formula(positions)
        if array_sha(pe) != expected[index]:
            raise ValueError("official_p42_position_bytes_mismatch:" + str(index))
        samples.append({"x": features, "pe": pe})
    return samples


def validate_execution_devices(devices):
    if isinstance(devices, str):
        devices = [devices]
    if not devices or any(not (d == "GPU" or d.startswith("GPU.")) for d in devices):
        raise ValueError("non_gpu_execution_device_or_fallback")
    return list(devices)


def validate_dml_profile(events):
    nodes = [e for e in events if e.get("cat") == "Node" and e.get("args", {}).get("provider")]
    if not nodes:
        raise ValueError("missing_actual_provider_evidence")
    providers = sorted({e["args"]["provider"] for e in nodes})
    if providers != ["DmlExecutionProvider"]:
        raise ValueError("cpu_or_other_provider_executed:" + ",".join(providers))
    return {"executed_node_events": len(nodes), "providers": providers}


def compare_arrays(expected, actual):
    import numpy as np
    if actual.shape != (1,16,1280) or actual.dtype != np.float32 or not np.isfinite(actual).all():
        raise ValueError("gpu_output_shape_dtype_or_finite")
    np.testing.assert_allclose(actual, expected, rtol=RTOL, atol=ATOL)
    return {"max_abs_error":float(np.max(np.abs(actual-expected))),
            "output_sha256":array_sha(actual), "rtol":RTOL, "atol":ATOL}


def memory_snapshot():
    """Current process RSS/private commit; peak coverage belongs to monitor."""
    if sys.platform != "win32":
        return {"status":"unavailable_non_windows"}
    import ctypes
    from ctypes import wintypes
    class Counters(ctypes.Structure):
        _fields_ = [("cb",wintypes.DWORD),("PageFaultCount",wintypes.DWORD)] + [
            (name,ctypes.c_size_t) for name in ("PeakWorkingSetSize","WorkingSetSize",
            "QuotaPeakPagedPoolUsage","QuotaPagedPoolUsage","QuotaPeakNonPagedPoolUsage",
            "QuotaNonPagedPoolUsage","PagefileUsage","PeakPagefileUsage","PrivateUsage")]
    c = Counters(); c.cb = ctypes.sizeof(c)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    psapi = ctypes.WinDLL("psapi", use_last_error=True)
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    psapi.GetProcessMemoryInfo.argtypes = [wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD]
    if not psapi.GetProcessMemoryInfo(kernel.GetCurrentProcess(),ctypes.byref(c),c.cb):
        return {"status":"GetProcessMemoryInfo_failed", "error":ctypes.get_last_error()}
    return {"rss_bytes":c.WorkingSetSize,"peak_rss_bytes":c.PeakWorkingSetSize,
            "private_commit_bytes":c.PrivateUsage,"peak_pagefile_bytes":c.PeakPagefileUsage}


def cpu_reference(path, samples, result):
    import onnxruntime as ort
    options = ort.SessionOptions()
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    options.enable_cpu_mem_arena = False
    started = time.perf_counter()
    session = ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])
    schema = {item.name:(item.shape,item.type) for item in session.get_inputs()}
    if schema != {"x":([1,16,1280],"tensor(float)"),"pe":([1,1,16,64,2,2],"tensor(float)")}:
        raise ValueError("unexpected_onnx_input_schema")
    outputs = [session.run(None, sample)[0] for sample in samples]
    result["cpu_reference"] = {"runtime_version":ort.__version__, "providers":session.get_providers(),
        "elapsed_seconds":time.perf_counter()-started,"memory":memory_snapshot()}
    del session
    gc.collect()
    result["memory_after_cpu_session_release"] = memory_snapshot()
    return outputs


def probe_openvino(path, samples, expected, result):
    import openvino as ov
    core = ov.Core()
    available = list(core.available_devices)
    result["runtime"] = {"name":"openvino", "version":ov.get_version(), "available_devices":available}
    if not any(d == "GPU" or d.startswith("GPU.") for d in available):
        raise RuntimeError("no_openvino_gpu_device_driver_or_hardware")
    result["runtime"]["device_full_name"] = str(core.get_property("GPU", "FULL_DEVICE_NAME"))
    model = core.read_model(str(path))
    # GPU plugin documents CPU implementations for these primitives; none is
    # expected in this attention block. Reject instead of claiming all-GPU work.
    ops = sorted({op.get_type_name() for op in model.get_ops()})
    result["runtime"]["operators"] = ops
    if set(ops) & {"Proposal","NonMaxSuppression","DetectionOutput"}:
        raise ValueError("gpu_plugin_internal_cpu_primitive_present")
    query = core.query_model(model, "GPU")
    result["runtime"]["query_devices"] = sorted(set(query.values()))
    config = {"INFERENCE_PRECISION_HINT":"f32", "PERFORMANCE_HINT":"LATENCY", "NUM_STREAMS":"1"}
    result["runtime"]["requested_config"] = config
    result["memory_before_gpu_compile"] = memory_snapshot()
    started = time.perf_counter()
    compiled = core.compile_model(model, "GPU", config)
    result["gpu_compile_seconds"] = time.perf_counter()-started
    result["memory_after_gpu_compile"] = memory_snapshot()
    result["runtime"]["execution_devices"] = validate_execution_devices(compiled.get_property("EXECUTION_DEVICES"))
    result["runtime"]["effective_precision_hint"] = str(compiled.get_property("INFERENCE_PRECISION_HINT"))
    comparisons = []
    for reference, sample in zip(expected, samples):
        started = time.perf_counter()
        actual = compiled(sample)[compiled.output(0)].copy()
        comparison = compare_arrays(reference,actual)
        comparison.update(inference_seconds=time.perf_counter()-started, memory=memory_snapshot())
        comparisons.append(comparison)
    result["comparisons"] = comparisons


def probe_directml(path, samples, expected, result, output):
    import onnxruntime as ort
    available = ort.get_available_providers()
    result["runtime"] = {"name":"onnxruntime-directml", "version":ort.__version__, "available_providers":available}
    if "DmlExecutionProvider" not in available:
        raise RuntimeError("directml_provider_not_available")
    options = ort.SessionOptions()
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.enable_mem_pattern = False
    options.enable_cpu_mem_arena = False
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    options.add_session_config_entry("session.disable_cpu_ep_fallback", "1")
    options.enable_profiling = True
    options.profile_file_prefix = str(output / "directml-profile")
    result["runtime"]["requested_provider"] = {"name":"DmlExecutionProvider","device_id":0,
        "cpu_fallback_disabled":True,"physical_adapter_identity":"requires_correlated_host_inventory"}
    result["memory_before_gpu_compile"] = memory_snapshot()
    started = time.perf_counter()
    session = ort.InferenceSession(str(path),sess_options=options,
                                  providers=[("DmlExecutionProvider", {"device_id":0})])
    session.disable_fallback()
    result["gpu_compile_seconds"] = time.perf_counter()-started
    result["memory_after_gpu_compile"] = memory_snapshot()
    result["runtime"]["registered_providers"] = session.get_providers()
    comparisons = []
    try:
        for reference, sample in zip(expected,samples):
            started = time.perf_counter()
            actual = session.run(None,sample)[0]
            comparison = compare_arrays(reference,actual)
            comparison.update(inference_seconds=time.perf_counter()-started,memory=memory_snapshot())
            comparisons.append(comparison)
    finally:
        profile_path = Path(session.end_profiling())
        result["runtime"]["profile_path"] = str(profile_path)
        result["runtime"]["profile_sha256"] = sha256(profile_path)
        result["runtime"]["actual_provider_evidence"] = validate_dml_profile(json.loads(profile_path.read_text(encoding="utf-8")))
    result["comparisons"] = comparisons


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model",type=Path,required=True)
    parser.add_argument("--p42-result",type=Path,required=True)
    parser.add_argument("--engine",choices=("openvino","directml"),required=True)
    parser.add_argument("--runtime-deps",type=Path,required=True)
    parser.add_argument("--output-dir",type=Path,required=True)
    args = parser.parse_args(argv)
    args.output_dir.mkdir(parents=True,exist_ok=False)
    started = time.perf_counter(); stage = "identity_validation"
    result = {"status":"FAILED", "scope":"PC_real_weight_16_token_attention_subgraph_only",
        "whole_model_executed":False,"android_executed":False,"png_generated":False,
        "full_sequence_tested":False,"device_performance_claim":False,"memory_start":memory_snapshot()}
    try:
        if not args.runtime_deps.is_dir():
            raise ValueError("isolated_runtime_directory_missing")
        if args.model.stat().st_size != MODEL_BYTES or sha256(args.model) != MODEL_SHA:
            raise ValueError("p42_onnx_model_identity_mismatch")
        result["model"] = {"sha256":MODEL_SHA,"bytes":MODEL_BYTES}
        record = json.loads(args.p42_result.read_text(encoding="utf-8-sig"))
        sys.path.insert(0,str(args.runtime_deps.resolve()))
        with offline_network_guard():
            stage = "official_pe_fixture_validation"
            samples = synthetic_samples(record)
            result["fixtures"] = [{name:{"shape":list(a.shape),"sha256":array_sha(a)} for name,a in s.items()} for s in samples]
            result["pe_bitexact_to_p42_official_embednd"] = True
            stage = "cpu_reference"
            expected = cpu_reference(args.model,samples,result)
            stage = args.engine + "_gpu_compile_and_compare"
            if args.engine == "openvino":
                probe_openvino(args.model,samples,expected,result)
            else:
                probe_directml(args.model,samples,expected,result,args.output_dir)
            result["status"] = "PASS_GPU_SUBGRAPH_ONLY"
            return 0
    except Exception as error:
        result["failure"] = {"stage":stage,"type":type(error).__name__,"reason":str(error)[:4000]}
        return 1
    finally:
        result["elapsed_seconds"] = time.perf_counter()-started
        result["memory_final"] = memory_snapshot()
        with (args.output_dir/"result.json").open("x",encoding="utf-8") as stream:
            json.dump(result,stream,ensure_ascii=False,indent=2)
        print(json.dumps({"status":result["status"],"stage":stage,"elapsed_seconds":result["elapsed_seconds"]}),flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
