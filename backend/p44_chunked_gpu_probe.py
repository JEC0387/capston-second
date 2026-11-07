"""Offline real-weight 6912-token SelfAttention probe, query-bounded at 128.

One FASHN attention block only; not its complete joint MMDiT block or sampling.
CPU reference is checked against the original P42 ONNX at 16 tokens. Actual
CPU/GPU full-sequence outputs are persisted separately. Use p41_monitor.py;
only the coordinator executes this process, never alongside another model.
"""
from __future__ import annotations
import argparse
import gc
import json
import math
import os
from pathlib import Path
import sys
import time

from p40_flatlay_probe import offline_network_guard, sha256
from p44_accelerator_probe import (MODEL_SHA, MODEL_BYTES, RTOL, ATOL,
    array_sha, memory_snapshot, synthetic_samples, validate_dml_profile)

WEIGHT_SCHEMA = {
    "attention.qkv.bias": (3840,),
    "attention.norm.query_norm.scale": (128,),
    "attention.norm.key_norm.scale": (128,),
    "attention.proj.bias": (1280,),
    "onnx::MatMul_173": (1280,3840),
    "onnx::MatMul_208": (1280,1280),
}


def validate_dimensions(tokens, chunk):
    if tokens not in (16,6912) or type(chunk) is not int or not 1 <= chunk <= 128 or tokens % chunk:
        raise ValueError("only_reviewed_sequence_and_dividing_query_chunk_1_to_128")
    return 1*10*chunk*tokens*4


def load_weights(path):
    import onnx
    import numpy as np
    if path.stat().st_size != MODEL_BYTES or sha256(path) != MODEL_SHA:
        raise ValueError("p42_onnx_identity_mismatch")
    model = onnx.load(str(path))
    tensors = {t.name:t for t in model.graph.initializer}
    if set(tensors) != set(WEIGHT_SCHEMA):
        raise ValueError("p42_initializer_schema_changed")
    weights = {}
    for name, shape in WEIGHT_SCHEMA.items():
        value = onnx.numpy_helper.to_array(tensors[name]).copy()
        if value.shape != shape or value.dtype != np.float32 or not np.isfinite(value).all():
            raise ValueError("p42_initializer_invalid:"+name)
        weights[name] = value
    return weights


def build_graphs(weights, tokens, chunk, folder):
    """Literal FP32 implementation of reviewed qkv/norm/rope and SDPA/proj.

    Query and key chunking are NOT interchangeable. All 6912 keys and values
    remain present in every query invocation; no local/linear attention.
    """
    import numpy as np
    import onnx
    from onnx import helper as h, numpy_helper as nh, TensorProto as T
    validate_dimensions(tokens,chunk)
    def write(name,nodes,initializers,inputs,outputs):
        # ORT's GatherSliceToSplitFusion derives names from input node names.
        # Unnamed independent Gather groups collide during graph optimization.
        for index, node in enumerate(nodes):
            node.name = f"{name}_{index}_{node.op_type}"
        graph = h.make_graph(nodes,name,inputs,outputs,initializers)
        model = h.make_model(graph,opset_imports=[h.make_opsetid("",18)],producer_name="FashionAI P44 bounded experiment")
        model.ir_version = 8
        onnx.checker.check_model(model)
        path = folder/(name+".onnx")
        if path.exists(): raise FileExistsError(path)
        onnx.save(model,str(path))
        return path
    def vi(name,shape): return h.make_tensor_value_info(name,T.FLOAT,shape)
    def init(name,value): return nh.from_array(np.asarray(value),name)
    n=[]
    constants=[init("qkv_weight",weights["onnx::MatMul_173"]),
        init("qkv_bias",weights["attention.qkv.bias"]),
        init("q_scale",weights["attention.norm.query_norm.scale"]),
        init("k_scale",weights["attention.norm.key_norm.scale"]),
        init("split_shape",np.array([1,chunk,3,10,128],dtype=np.int64)),
        init("rope_shape",np.array([1,10,chunk,64,1,2],dtype=np.int64)),
        init("qk_shape",np.array([1,10,chunk,128],dtype=np.int64)),
        init("last_axis",np.array([-1],dtype=np.int64)),
        init("zero",np.array(0,dtype=np.int64)),init("one_index",np.array(1,dtype=np.int64)),
        init("two",np.array(2,dtype=np.int64)),init("eps",np.array(1e-6,dtype=np.float32)),
        init("one_float",np.array(1.,dtype=np.float32))]
    n.extend([h.make_node("MatMul",["x","qkv_weight"],["qkv_linear"]),
        h.make_node("Add",["qkv_linear","qkv_bias"],["qkv"]),
        h.make_node("Reshape",["qkv","split_shape"],["split"]),
        h.make_node("Transpose",["split"],["head_first"],perm=[2,0,3,1,4]),
        h.make_node("Gather",["head_first","zero"],["q_raw"],axis=0),
        h.make_node("Gather",["head_first","one_index"],["k_raw"],axis=0),
        h.make_node("Gather",["head_first","two"],["v"],axis=0),
        h.make_node("Gather",["pe","zero"],["pe0"],axis=5),
        h.make_node("Gather",["pe","one_index"],["pe1"],axis=5)])
    for p in ("q","k"):
        n.extend([h.make_node("Mul",[p+"_raw",p+"_raw"],[p+"_sq"]),
            h.make_node("ReduceMean",[p+"_sq","last_axis"],[p+"_mean"],keepdims=1),
            h.make_node("Add",[p+"_mean","eps"],[p+"_var"]),
            h.make_node("Sqrt",[p+"_var"],[p+"_sqrt"]),
            h.make_node("Div",["one_float",p+"_sqrt"],[p+"_rrms"]),
            h.make_node("Mul",[p+"_raw",p+"_rrms"],[p+"_norm"]),
            h.make_node("Mul",[p+"_norm",p+"_scale"],[p+"_scaled"]),
            h.make_node("Reshape",[p+"_scaled","rope_shape"],[p+"_pairs"]),
            h.make_node("Gather",[p+"_pairs","zero"],[p+"_component0"],axis=5),
            h.make_node("Gather",[p+"_pairs","one_index"],[p+"_component1"],axis=5),
            h.make_node("Mul",["pe0",p+"_component0"],[p+"_rot0"]),
            h.make_node("Mul",["pe1",p+"_component1"],[p+"_rot1"]),
            h.make_node("Add",[p+"_rot0",p+"_rot1"],[p+"_rotated"]),
            h.make_node("Reshape",[p+"_rotated","qk_shape"],[p])])
    qkv = write("real-qkv-rope",n,constants,[vi("x",[1,chunk,1280]),vi("pe",[1,1,chunk,64,2,2])],
                [vi(p,[1,10,chunk,128]) for p in ("q","k","v")])
    # Scale q and k by sqrt(scale), matching Torch's ONNX SDPA decomposition.
    # Mathematically identical to official SDPA scale=1/sqrt(head_dim).
    constants=[init("sdpa_factor",np.array(128.**(-.25),dtype=np.float32)),
        init("merged_shape",np.array([1,chunk,1280],dtype=np.int64)),
        init("proj_weight",weights["onnx::MatMul_208"]),
        init("proj_bias",weights["attention.proj.bias"])]
    n=[h.make_node("Mul",["q","sdpa_factor"],["scaled_q"]),
       h.make_node("Mul",["k","sdpa_factor"],["scaled_k"]),
       h.make_node("Transpose",["scaled_k"],["kt"],perm=[0,1,3,2]),
       h.make_node("MatMul",["scaled_q","kt"],["bounded_scores"]),
       h.make_node("Softmax",["bounded_scores"],["probabilities"],axis=-1),
       h.make_node("MatMul",["probabilities","v"],["attended"]),
       h.make_node("Transpose",["attended"],["token_first"],perm=[0,2,1,3]),
       h.make_node("Reshape",["token_first","merged_shape"],["merged"]),
       h.make_node("MatMul",["merged","proj_weight"],["projected"]),
       h.make_node("Add",["projected","proj_bias"],["output"])]
    attention=write("real-query-attention-proj",n,constants,
        [vi("q",[1,10,chunk,128]),vi("k",[1,10,tokens,128]),vi("v",[1,10,tokens,128])],
        [vi("output",[1,chunk,1280])])
    return qkv,attention


def numpy_qkv(x,pe,w):
    import numpy as np
    qkv=x @ w["onnx::MatMul_173"]+w["attention.qkv.bias"]
    q,k,v=qkv.reshape(1,x.shape[1],3,10,128).transpose(2,0,3,1,4)
    out=[]
    for p,value in (("query",q),("key",k)):
        norm=value*np.reciprocal(np.sqrt(np.mean(value*value,axis=-1,keepdims=True)+np.float32(1e-6)))
        norm*=w[f"attention.norm.{p}_norm.scale"]
        pairs=norm.reshape(1,10,x.shape[1],64,1,2)
        out.append((pe[...,0]*pairs[...,0]+pe[...,1]*pairs[...,1]).reshape(norm.shape))
    return (*out,v.copy())


def numpy_attention(q,k,v,w):
    import numpy as np
    if q.shape[-2]>128: raise ValueError("unbounded_query_attention_forbidden")
    # The reference uses one combined scale; different operation ordering vs
    # ONNX is independently compared, never used to enlarge the tolerance.
    scores=q @ k.transpose(0,1,3,2)
    scores*=np.float32(1/math.sqrt(128))
    scores-=np.max(scores,axis=-1,keepdims=True)
    np.exp(scores,out=scores)
    scores/=np.sum(scores,axis=-1,keepdims=True)
    attended=scores @ v
    return attended.transpose(0,2,1,3).reshape(1,q.shape[-2],1280) @ w["onnx::MatMul_208"]+w["attention.proj.bias"]


def compare(reference,actual):
    import numpy as np
    if reference.shape!=actual.shape or actual.dtype!=np.float32 or not np.isfinite(actual).all():
        raise ValueError("comparison_shape_dtype_finite")
    np.testing.assert_allclose(actual,reference,rtol=RTOL,atol=ATOL)
    return float(np.max(np.abs(reference-actual)))


def cpu_contract(path,record,w):
    import onnxruntime as ort
    options=ort.SessionOptions(); options.intra_op_num_threads=options.inter_op_num_threads=1
    options.enable_cpu_mem_arena=False
    session=ort.InferenceSession(str(path),sess_options=options,providers=["CPUExecutionProvider"])
    errors=[]
    for sample in synthetic_samples(record):
        expected=session.run(None,sample)[0]
        q,k,v=numpy_qkv(sample["x"],sample["pe"],w)
        errors.append(compare(expected,numpy_attention(q,k,v,w)))
    del session
    gc.collect()
    return errors


def split_graph_cpu_contract(path,record,w,folder):
    """Compare newly written split ONNX directly to unchanged official P42.

    This detects shared reference/graph mistakes before costly 6912 work.
    Both official positional fixtures are used, not full-grid assumptions.
    """
    import onnxruntime as ort
    folder.mkdir(exist_ok=False)
    qpath,apath=build_graphs(w,16,16,folder)
    options=ort.SessionOptions(); options.intra_op_num_threads=options.inter_op_num_threads=1
    options.enable_cpu_mem_arena=False
    original=ort.InferenceSession(str(path),sess_options=options,providers=["CPUExecutionProvider"])
    qsession=ort.InferenceSession(str(qpath),sess_options=options,providers=["CPUExecutionProvider"])
    asession=ort.InferenceSession(str(apath),sess_options=options,providers=["CPUExecutionProvider"])
    errors=[]
    for sample in synthetic_samples(record):
        expected=original.run(None,sample)[0]
        q,k,v=qsession.run(None,sample)
        actual=asession.run(None,{"q":q,"k":k,"v":v})[0]
        errors.append(compare(expected,actual))
    del original,qsession,asession
    gc.collect()
    return errors


def position_chunk(start,count):
    import numpy as np
    # Full FASHN length, synthetic coordinate grid only; no claim that these are
    # actual person/garment conditions or the joint sequence layout.
    ids=np.zeros((1,count,3),dtype=np.float32)
    ids[0,:,1]=np.arange(start,start+count)//72
    ids[0,:,2]=np.arange(start,start+count)%72
    axes=[]
    for i,dim in enumerate((16,56,56)):
        omega=1./(10000.**(np.arange(0,dim,2,dtype=np.float64)/dim))
        angle=ids[...,i,None].astype(np.float64)*omega
        c,s=np.cos(angle),np.sin(angle)
        axes.append(np.stack((c,-s,s,c),axis=-1).reshape(1,count,dim//2,2,2).astype(np.float32))
    return np.concatenate(axes,axis=-3)[:,None]


def new_array(path,shape):
    import numpy as np
    if path.exists(): raise FileExistsError(path)
    return np.lib.format.open_memmap(path,mode="w+",dtype=np.float32,shape=shape)


def close_array(value):
    value.flush()
    value._mmap.close()


def dml_session(path,prefix):
    import onnxruntime as ort
    if "DmlExecutionProvider" not in ort.get_available_providers(): raise RuntimeError("directml_unavailable")
    options=ort.SessionOptions(); options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL
    options.enable_mem_pattern=False; options.enable_cpu_mem_arena=False
    options.intra_op_num_threads=options.inter_op_num_threads=1
    options.add_session_config_entry("session.disable_cpu_ep_fallback","1")
    options.enable_profiling=True; options.profile_file_prefix=str(prefix)
    session=ort.InferenceSession(str(path),sess_options=options,providers=[("DmlExecutionProvider",{"device_id":0})])
    session.disable_fallback()
    return session


def profile_record(session):
    path=Path(session.end_profiling())
    return {"file":path.name,"sha256":sha256(path),
            **validate_dml_profile(json.loads(path.read_text(encoding="utf-8")))}


def main(argv=None):
    # Only this owned worker; never mutate machine/user environment settings.
    for name in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
        os.environ[name] = "1"
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("model","p42-result","runtime-deps","onnx-deps","output-dir"):
        p.add_argument("--"+name,type=Path,required=True)
    p.add_argument("--tokens",type=int,choices=(16,6912),default=6912)
    p.add_argument("--query-chunk",type=int,default=128)
    a=p.parse_args(argv)
    score_bytes=validate_dimensions(a.tokens,a.query_chunk)
    a.output_dir.mkdir(parents=True,exist_ok=False)
    result={"status":"FAILED","scope":"single_real_weight_self_attention_block_synthetic_full_sequence",
        "whole_model_executed":False,"png_generated":False,"android_executed":False,
        "tokens":a.tokens,"query_chunk":a.query_chunk,"maximum_score_bytes":score_bytes,
        "full_n_by_n_score_created":False,"memory_start":memory_snapshot()}
    started=time.perf_counter(); stage="dependency_validation"
    try:
        for path in (a.onnx_deps,a.runtime_deps):
            if not path.is_dir(): raise ValueError("explicit_dependency_directory_missing")
            sys.path.insert(0,str(path.resolve()))
        with offline_network_guard():
            import numpy as np
            import onnxruntime as ort
            # Respect coordinator's thread limit; never change system settings.
            result["runtime"]={"version":ort.__version__,"providers":ort.get_available_providers()}
            stage="real_weight_loading"
            w=load_weights(a.model)
            result["model_sha256"]=MODEL_SHA
            stage="cpu_reference_official_contract"
            record=json.loads(a.p42_result.read_text(encoding="utf-8-sig"))
            result["cpu_reference_vs_official_max_abs"]=cpu_contract(a.model,record,w)
            stage="split_graph_cpu_official_contract"
            result["split_graph_vs_official_max_abs"]=split_graph_cpu_contract(a.model,record,w,a.output_dir/"contract16")
            stage="bounded_onnx_graph_creation"
            qkv_path,attn_path=build_graphs(w,a.tokens,a.query_chunk,a.output_dir)
            result["graphs"]={path.name:{"sha256":sha256(path),"bytes":path.stat().st_size} for path in (qkv_path,attn_path)}
            shape=(1,10,a.tokens,128); outshape=(1,a.tokens,1280)
            x=new_array(a.output_dir/"synthetic-input.npy",outshape)
            cpu=[new_array(a.output_dir/("cpu-"+n+".npy"),shape) for n in ("q","k","v")]
            reference=new_array(a.output_dir/"cpu-output.npy",outshape)
            rng=np.random.default_rng(4411)
            stage="cpu_full_sequence_reference"
            t=time.perf_counter()
            for s in range(0,a.tokens,a.query_chunk):
                e=s+a.query_chunk
                x[:,s:e]=rng.standard_normal((1,a.query_chunk,1280)).astype(np.float32)
                prepared=numpy_qkv(x[:,s:e],position_chunk(s,a.query_chunk),w)
                for target,value in zip(cpu,prepared): target[:,:,s:e]=value
                del prepared
            result["cpu_qkv_seconds"]=time.perf_counter()-t
            # The warm-up is a separate actual first query, excluded from timing.
            warm=numpy_attention(cpu[0][:,:,:a.query_chunk],cpu[1],cpu[2],w)
            del warm
            t=time.perf_counter()
            for s in range(0,a.tokens,a.query_chunk):
                reference[:,s:s+a.query_chunk]=numpy_attention(cpu[0][:,:,s:s+a.query_chunk],cpu[1],cpu[2],w)
            result["cpu_attention_proj_seconds"]=time.perf_counter()-t
            result["memory_after_cpu_reference"]=memory_snapshot()
            for value in cpu: close_array(value)
            del cpu; gc.collect()
            reference.flush(); x.flush()
            result["memory_after_cpu_release"]=memory_snapshot()
            stage="gpu_qkv_compile"
            t=time.perf_counter(); session=dml_session(qkv_path,a.output_dir/"qkv-profile")
            result["gpu_qkv_compile_seconds"]=time.perf_counter()-t
            gpu=[new_array(a.output_dir/("gpu-"+n+".npy"),shape) for n in ("q","k","v")]
            session.run(None,{"x":np.ascontiguousarray(x[:,:a.query_chunk]),"pe":position_chunk(0,a.query_chunk)})
            stage="gpu_qkv_inference"
            timings=[]; max_qkv=0.
            for s in range(0,a.tokens,a.query_chunk):
                inputs={"x":np.ascontiguousarray(x[:,s:s+a.query_chunk]),"pe":position_chunk(s,a.query_chunk)}
                t=time.perf_counter(); actual=session.run(None,inputs); timings.append(time.perf_counter()-t)
                expected=numpy_qkv(inputs["x"],inputs["pe"],w)
                for target,value,baseline in zip(gpu,actual,expected):
                    max_qkv=max(max_qkv,compare(baseline,value)); target[:,:,s:s+a.query_chunk]=value
                del expected,actual
            result["gpu_qkv_seconds"]=sum(timings); result["qkv_max_abs_error"]=max_qkv
            result["qkv_provider_evidence"]=profile_record(session)
            del session; gc.collect()
            for value in gpu: value.flush()
            stage="gpu_attention_compile"
            t=time.perf_counter(); session=dml_session(attn_path,a.output_dir/"attention-profile")
            result["gpu_attention_compile_seconds"]=time.perf_counter()-t
            output=new_array(a.output_dir/"gpu-output.npy",outshape)
            inputs={"q":np.ascontiguousarray(gpu[0][:,:,:a.query_chunk]),"k":gpu[1],"v":gpu[2]}
            t=time.perf_counter(); session.run(None,inputs)
            result["gpu_attention_warmup_seconds"]=time.perf_counter()-t
            stage="gpu_full_sequence_query_chunks"
            timings=[]; maximum=0.
            for s in range(0,a.tokens,a.query_chunk):
                inputs["q"]=np.ascontiguousarray(gpu[0][:,:,s:s+a.query_chunk])
                t=time.perf_counter(); actual=session.run(None,inputs)[0]; timings.append(time.perf_counter()-t)
                maximum=max(maximum,compare(reference[:,s:s+a.query_chunk],actual))
                output[:,s:s+a.query_chunk]=actual
            result["gpu_attention_proj_seconds"]=sum(timings)
            result["gpu_attention_chunk_seconds"]=timings
            result["full_output_max_abs_error"]=maximum
            result["attention_provider_evidence"]=profile_record(session)
            result["memory_after_gpu_inference"]=memory_snapshot()
            del session,inputs; gc.collect()
            for value in (*gpu,output,reference,x): close_array(value)
            result["output_sha256"]={name:sha256(a.output_dir/name) for name in ("synthetic-input.npy","cpu-output.npy","gpu-output.npy")}
            result["status"]="PASS_SINGLE_ATTENTION_BLOCK_ONLY"
            return 0
    except Exception as error:
        result["failure"]={"stage":stage,"type":type(error).__name__,"reason":str(error)[:4000]}
        return 1
    finally:
        result["elapsed_seconds"]=time.perf_counter()-started
        result["memory_final"]=memory_snapshot()
        with (a.output_dir/"result.json").open("x",encoding="utf-8") as stream:
            json.dump(result,stream,ensure_ascii=False,indent=2)
        print(json.dumps({"status":result["status"],"stage":stage,"seconds":result["elapsed_seconds"]}),flush=True)


if __name__=="__main__": raise SystemExit(main())
