"""Offline bounded real-weight runtime-input Linear GPU probe, not whole VTON.

Execute only under p41_monitor.py, serially. Uses the existing verified BF16
checkpoint, promotes one weight to FP32, and submits 6912 synthetic tokens in
128-token chunks. No Torch import, download, fallback, PNG or Android claim.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import json
import os
from pathlib import Path
import sys
import time

from p40_flatlay_probe import WEIGHTS, offline_network_guard, sha256
from p41_streaming import TensorReader
from p44_accelerator_probe import RTOL, ATOL, memory_snapshot, validate_dml_profile

LAYERS = {
    "qkv": ("double_blocks.0.img_attn.qkv", 3840, 1280),
    "linear1": ("single_blocks.0.linear1", 8960, 1280),
}
SEQUENCE, CHUNK = 6912, 128


def numpy_tensor(reader, name):
    """Read exactly one verified tensor, preserving its BF16 bit values."""
    import numpy as np
    entry = reader.entries[name]
    if entry["dtype"] != "BF16":
        raise ValueError("representative_tensor_must_be_bf16")
    start, end = entry["data_offsets"]
    reader.file.seek(reader.base + start)
    raw = bytearray(end-start)
    if reader.file.readinto(raw) != len(raw):
        raise ValueError("tensor_changed_or_truncated")
    bits = np.frombuffer(raw, dtype="<u2").astype(np.uint32)
    bits <<= 16
    value = bits.view(np.float32).reshape(entry["shape"])
    if not np.isfinite(value).all():
        raise ValueError("nonfinite_checkpoint_tensor")
    reader.calls += 1
    reader.bytes_read += len(raw)
    reader.max_tensor_bytes = max(reader.max_tensor_bytes, len(raw))
    reader.seen.add(name)
    return value


def validate_layer(reader, label):
    prefix, outputs, inputs = LAYERS[label]
    for suffix, shape in (("weight", [outputs, inputs]), ("bias", [outputs])):
        entry = reader.entries.get(prefix+"."+suffix, {})
        if entry.get("shape") != shape or entry.get("dtype") != "BF16":
            raise ValueError("representative_checkpoint_schema:" + prefix + "." + suffix)
    return prefix, outputs, inputs


def make_graph(outputs, inputs):
    import onnx
    from onnx import TensorProto, helper
    # Weight and bias are graph INPUTS: no packing them into an initializer and
    # claiming a disk-streamed path when testing only cached constant weights.
    graph = helper.make_graph([
        helper.make_node("Transpose", ["weight"], ["wt"], perm=[1,0]),
        helper.make_node("MatMul", ["x", "wt"], ["product"]),
        helper.make_node("Add", ["product", "bias"], ["y"]),
    ], "p44-runtime-weight-linear", [
        helper.make_tensor_value_info("x", TensorProto.FLOAT, [1,CHUNK,inputs]),
        helper.make_tensor_value_info("weight", TensorProto.FLOAT, [outputs,inputs]),
        helper.make_tensor_value_info("bias", TensorProto.FLOAT, [outputs]),
    ], [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1,CHUNK,outputs])])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("",18)], ir_version=8)
    onnx.checker.check_model(model)
    if model.graph.initializer:
        raise ValueError("runtime_weight_became_constant")
    return model


def compare_chunk(expected, actual, outputs):
    import numpy as np
    shape = (1,CHUNK,outputs)
    if (expected.shape != shape or actual.shape != shape or expected.dtype != np.float32
            or actual.dtype != np.float32 or not np.isfinite(expected).all()
            or not np.isfinite(actual).all()):
        raise ValueError("linear_output_shape_dtype_or_nonfinite")
    difference = np.abs(expected-actual)
    max_abs = float(difference.max())
    mismatch_count = int(np.count_nonzero(difference > ATOL + RTOL*np.abs(expected)))
    return {"max_abs_error":max_abs, "mismatch_count":mismatch_count,
            "element_count":int(actual.size), "pass":mismatch_count == 0,
            "rtol":RTOL,"atol":ATOL}


def run_layer(reader, label, output, record):
    import numpy as np
    import onnxruntime as ort
    prefix, outputs, inputs = validate_layer(reader,label)
    graph = make_graph(outputs,inputs)
    path = output/(label+".onnx")
    with path.open("xb") as stream:
        stream.write(graph.SerializeToString())
    del graph
    record.update(layer=prefix,weight_shape=[outputs,inputs],sequence=SEQUENCE,chunk=CHUNK,
                  graph_sha256=sha256(path),runtime_weight_inputs=True,
                  full_sequence_completed=False,all_outputs_retained=False)
    record["full_sequence_output_bytes_if_retained"] = SEQUENCE*outputs*4
    record["single_chunk_output_bytes"] = CHUNK*outputs*4
    reader.max_linear_output_bytes = max(reader.max_linear_output_bytes,CHUNK*outputs*4)
    load_started = time.perf_counter()
    weight = numpy_tensor(reader,prefix+".weight")
    bias = numpy_tensor(reader,prefix+".bias")
    record["weight_load_seconds"] = time.perf_counter()-load_started
    record["weight_fp32_sha256"] = hashlib.sha256(memoryview(weight).cast("B")).hexdigest()
    record["memory_after_weight_load"] = memory_snapshot()
    cpu_options = ort.SessionOptions()
    cpu_options.intra_op_num_threads = cpu_options.inter_op_num_threads = 1
    cpu_options.enable_cpu_mem_arena = False
    cpu_options.enable_mem_pattern = False
    cpu_started = time.perf_counter()
    cpu = ort.InferenceSession(str(path),sess_options=cpu_options,providers=["CPUExecutionProvider"])
    record["cpu_session_seconds"] = time.perf_counter()-cpu_started
    gpu = None
    try:
        gpu_options = ort.SessionOptions()
        gpu_options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        gpu_options.enable_mem_pattern = False
        gpu_options.enable_cpu_mem_arena = False
        gpu_options.intra_op_num_threads = gpu_options.inter_op_num_threads = 1
        gpu_options.add_session_config_entry("session.disable_cpu_ep_fallback","1")
        gpu_options.enable_profiling = True
        gpu_options.profile_file_prefix = str(output/(label+"-dml-profile"))
        gpu_started = time.perf_counter()
        gpu = ort.InferenceSession(str(path),sess_options=gpu_options,
                                  providers=[("DmlExecutionProvider",{"device_id":0})])
        gpu.disable_fallback()
        record["gpu_session_seconds"] = time.perf_counter()-gpu_started
        record["registered_gpu_providers"] = gpu.get_providers()
        record["memory_after_sessions"] = memory_snapshot()
        rng = np.random.default_rng(4402)
        hashes = {k:hashlib.sha256() for k in ("input","cpu_output","gpu_output")}
        record["chunks"] = []
        for offset in range(0,SEQUENCE,CHUNK):
            x = rng.standard_normal((1,CHUNK,inputs),dtype=np.float32)
            feed = {"x":x,"weight":weight,"bias":bias}
            started = time.perf_counter()
            expected = cpu.run(None,feed)[0]
            cpu_seconds = time.perf_counter()-started
            started = time.perf_counter()
            actual = gpu.run(None,feed)[0]
            gpu_seconds = time.perf_counter()-started
            comparison = compare_chunk(expected,actual,outputs)
            comparison.update(offset=offset,cpu_seconds=cpu_seconds,gpu_seconds=gpu_seconds)
            record["chunks"].append(comparison)
            for name,value in (("input",x),("cpu_output",expected),("gpu_output",actual)):
                hashes[name].update(memoryview(value).cast("B"))
            print(json.dumps({"event":"linear_chunk","layer":label,**comparison}),flush=True)
            del feed,x,expected,actual
            if not comparison["pass"]:
                raise ValueError("linear_numerical_gate_failed:"+label+":"+str(offset))
        record["stream_sha256"] = {name:digest.hexdigest() for name,digest in hashes.items()}
        record["full_sequence_completed"] = True
        record["cpu_total_seconds"] = sum(c["cpu_seconds"] for c in record["chunks"])
        record["gpu_total_seconds"] = sum(c["gpu_seconds"] for c in record["chunks"])
        record["timing_includes_runtime_weight_submission"] = True
    finally:
        if gpu is not None:
            profile = Path(gpu.end_profiling())
            record["profile_path"] = str(profile)
            record["profile_sha256"] = sha256(profile)
            try:
                record["actual_provider_evidence"] = validate_dml_profile(json.loads(profile.read_text()))
            except Exception as error:
                record["provider_evidence_failure"] = str(error)
            del gpu
        del cpu,weight,bias
        gc.collect()
        record["memory_after_layer_release"] = memory_snapshot()
    if "provider_evidence_failure" in record:
        raise ValueError("gpu_provider_evidence_failed")


def main(argv=None):
    for name in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
        os.environ[name] = "1"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights-dir",type=Path,required=True)
    parser.add_argument("--onnx-deps",type=Path,required=True)
    parser.add_argument("--runtime-deps",type=Path,required=True)
    parser.add_argument("--output-dir",type=Path,required=True)
    parser.add_argument("--layer",choices=("qkv","linear1","both"),default="both")
    args = parser.parse_args(argv)
    args.output_dir.mkdir(parents=True,exist_ok=False)
    started = time.perf_counter()
    stage = "dependency_validation"
    reader = None
    result = {"status":"FAILED","scope":"runtime_input_weight_chunked_linear_PC_only",
              "whole_model_executed":False,"android_executed":False,"png_generated":False,
              "cpu_fallback_allowed":False,"layers":[],"memory_start":memory_snapshot()}
    try:
        if not args.runtime_deps.is_dir() or not args.onnx_deps.is_dir():
            raise ValueError("isolated_dependencies_missing")
        sys.path.insert(0,str(args.onnx_deps.resolve()))
        sys.path.insert(0,str(args.runtime_deps.resolve()))
        with offline_network_guard():
            import onnxruntime as ort
            result["ort_version"] = ort.__version__
            result["available_providers"] = ort.get_available_providers()
            if "DmlExecutionProvider" not in result["available_providers"]:
                raise ValueError("directml_provider_unavailable")
            stage = "checkpoint_full_sha_and_header"
            reader = TensorReader(args.weights_dir/"model.safetensors",WEIGHTS["model.safetensors"])
            result["checkpoint_sha256"] = WEIGHTS["model.safetensors"]
            result["checkpoint_size"] = reader.path.stat().st_size
            for label in LAYERS if args.layer == "both" else (args.layer,):
                stage = "linear_gpu_"+label
                record = {}; result["layers"].append(record)
                run_layer(reader,label,args.output_dir,record)
            result["checkpoint_read_stats"] = reader.stats()
            result["status"] = "PASS_GPU_LINEAR_ONLY"
            return 0
    except Exception as error:
        result["failure"] = {"stage":stage,"type":type(error).__name__,"reason":str(error)[:2000]}
        return 1
    finally:
        if reader is not None:
            result["checkpoint_read_stats"] = reader.stats()
            reader.close()
        result["elapsed_seconds"] = time.perf_counter()-started
        result["memory_final"] = memory_snapshot()
        with (args.output_dir/"result.json").open("x",encoding="utf-8") as stream:
            json.dump(result,stream,ensure_ascii=False,indent=2)
        print(json.dumps({"status":result["status"],"stage":stage,
                          "elapsed_seconds":result["elapsed_seconds"]}),flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
