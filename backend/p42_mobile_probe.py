"""Bounded PC export probe of ONE real FASHN attention subgraph, not VTON.

Uses verified checkpoint weights, original hidden/head dimensions, and synthetic
16-token inputs. No image generation, whole-model export, mobile inference or
performance claim is made. Execute serially through p41_monitor.py.
"""
import argparse
import hashlib
import importlib
import importlib.metadata
import importlib.util
import json
from pathlib import Path
import sys
import time

from p40_flatlay_probe import (NAMESPACE, WEIGHTS, namespace_source,
                              offline_network_guard, sha256, verify_source)
from p41_streaming import TensorReader


PREFIX = "double_blocks.0.img_attn."


def export_position_ids(ids):
    """Make RoPE's existing FP64 promotion explicit at the export boundary.

    Pinned upstream creates FP64 omega. Torch eager promotes the mixed einsum,
    whereas legacy ONNX export emits mismatched input types unless made explicit.
    This does not reduce precision or change the official source implementation.
    """
    import torch
    return ids.to(dtype=torch.float64)


def precompute_positions(position, samples):
    """Keep the official FP64 trig calculation on CPU; export its FP32 result.

    This is an explicit graph partition, not lower-precision RoPE mathematics.
    Full-shape position constants and Android consumption are still unverified.
    """
    import torch
    prepared = []
    with torch.no_grad():
        for x, ids in samples:
            pe = position(ids)
            if (pe.dtype != torch.float32 or pe.device.type != "cpu"
                    or tuple(pe.shape) != (x.shape[0], 1, x.shape[1], 64, 2, 2)):
                raise ValueError("precomputed_position_requires_official_fp32_shape")
            if not bool(torch.isfinite(pe).all()):
                raise ValueError("precomputed_position_nonfinite")
            prepared.append((x, pe))
    return tuple(prepared)


def subgraph_keys(entries, expected_shapes):
    actual = {name[len(PREFIX):]: item for name, item in entries.items()
              if name.startswith(PREFIX)}
    if set(actual) != set(expected_shapes):
        raise ValueError("attention_checkpoint_schema_keys")
    for name, shape in expected_shapes.items():
        if actual[name]["shape"] != list(shape) or actual[name]["dtype"] != "BF16":
            raise ValueError("attention_checkpoint_shape_or_dtype:" + name)
    return {name: PREFIX + name for name in expected_shapes}


def dependency_inventory():
    found = {}
    for module, distribution in (("torch", "torch"), ("onnx", "onnx"),
            ("onnxruntime", "onnxruntime"), ("onnxscript", "onnxscript"),
            ("litert_torch", "litert-torch"), ("ai_edge_torch", "ai-edge-torch")):
        present = importlib.util.find_spec(module) is not None
        try:
            version = importlib.metadata.version(distribution) if present else None
        except importlib.metadata.PackageNotFoundError:
            version = "unknown"
        found[module] = {"available": present, "version": version}
    return found


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--weights-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--onnx-deps", type=Path,
                        help="Explicit isolated package target; does not process .pth files")
    args = parser.parse_args(argv)
    args.output_dir.mkdir(parents=True, exist_ok=False)
    result = {"scope": "one_official_attention_subgraph_16_synthetic_tokens",
              "whole_model_exported": False, "android_executed": False,
              "png_generated": False, "weights_random": False,
              "device_latency_measured": False}
    started = time.monotonic()
    reader = None
    stage = "dependency_directory_validation"
    try:
        if args.onnx_deps:
            if not args.onnx_deps.is_dir():
                raise ValueError("explicit_onnx_dependencies_directory_missing")
            sys.path.insert(0, str(args.onnx_deps.resolve()))
        stage = "source_validation"
        verify_source(args.source_dir, args.source_manifest)
        result["source_manifest_sha256"] = sha256(args.source_manifest)
        with offline_network_guard():
            result["dependencies"] = dependency_inventory()
            import torch
            torch.set_num_threads(1)
            torch.set_num_interop_threads(1)
            namespace_source(args.source_dir)
            upstream = importlib.import_module(NAMESPACE + ".tryon_mmdit")
            stage = "real_checkpoint_loading"
            reader = TensorReader(args.weights_dir / "model.safetensors", WEIGHTS["model.safetensors"])
            with torch.device("meta"):
                attention = upstream.SelfAttention(dim=1280, num_heads=10, qkv_bias=True)
            keys = subgraph_keys(reader.entries,
                                 {k: tuple(v.shape) for k, v in attention.state_dict().items()})
            attention.load_state_dict({key: reader.tensor(full) for key, full in keys.items()},
                                      strict=True, assign=True)
            if any(value.is_meta for value in attention.parameters()):
                raise RuntimeError("unmaterialized_attention_parameter")
            result["checkpoint_sha256"] = WEIGHTS["model.safetensors"]
            result["checkpoint_tensors"] = list(keys.values())
            result["checkpoint_read_stats"] = reader.stats()

            class AttentionWithPosition(torch.nn.Module):
                def __init__(self):
                    super().__init__()
                    self.attention = attention
                    self.position = upstream.EmbedND(dim=128, theta=10000, axes_dim=(16,56,56))

                def forward(self, x, ids):
                    return self.attention(x, self.position(ids))

            class AttentionWithPrecomputedPosition(torch.nn.Module):
                def __init__(self):
                    super().__init__()
                    self.attention = attention

                def forward(self, x, pe):
                    return self.attention(x, pe)

            baseline = AttentionWithPosition().eval().requires_grad_(False)
            model = AttentionWithPrecomputedPosition().eval().requires_grad_(False)
            torch.manual_seed(4201)
            x = torch.randn(1,16,1280)
            ids = torch.zeros(1,16,3)
            ids[0,:,1] = torch.arange(16) // 4
            ids[0,:,2] = torch.arange(16) % 4
            inputs = ((x,ids), (x * 0.7 + 0.2, ids + torch.tensor([[[0.,1.,2.]]])))
            stage = "official_position_cpu_precomputation"
            export_inputs = precompute_positions(baseline.position, inputs)
            stage = "export_adapter_eager_compare"
            with torch.no_grad():
                expected = [baseline(*sample) for sample in inputs]
                adapted = [model(*sample) for sample in export_inputs]
            for target, value in zip(expected, adapted):
                torch.testing.assert_close(value, target, rtol=0, atol=0)
            result["export_adapter"] = {
                "change": "unchanged_official_position_precomputed_on_CPU_as_FP32_graph_input",
                "baseline": "unchanged_official_EmbedND_with_FP32_ids",
                "max_abs_error": max(float((a-b).abs().max()) for a,b in zip(expected, adapted)),
                "position_math_precision_changed": False,
                "full_shape_android_position_consumption_verified": False,
                "position_tensor_sha256": [hashlib.sha256(pe.numpy().tobytes()).hexdigest()
                                           for _,pe in export_inputs],
            }
            result["original_input_shapes"] = [list(t.shape) for t in inputs[0]]
            result["input_shapes"] = [list(t.shape) for t in export_inputs[0]]
            stage = "torch_export"
            exported = torch.export.export(model, export_inputs[0], strict=True)
            with torch.no_grad():
                actual = [exported.module()(*sample) for sample in export_inputs]
            for target, value in zip(expected, actual):
                torch.testing.assert_close(value, target, rtol=1e-5, atol=1e-5)
            export_path = args.output_dir / "real-attention-subgraph.pt2"
            torch.export.save(exported, export_path)
            result["torch_export"] = {"status":"PASS", "sha256":sha256(export_path),
                "bytes":export_path.stat().st_size,
                "max_abs_error":max(float((a-b).abs().max()) for a,b in zip(expected,actual)),
                "operators":sorted({str(node.target) for node in exported.graph.nodes if node.op=="call_function"})}
            stage = "onnx_export"
            if not result["dependencies"]["onnx"]["available"]:
                result["onnx"] = {"status":"BLOCKED", "reason":"onnx_package_not_installed",
                                  "unsupported_operator":None}
            else:
                import onnx
                onnx_path = args.output_dir / "real-attention-subgraph.onnx"
                torch.onnx.export(model, export_inputs[0], str(onnx_path), opset_version=18,
                                  dynamo=False, input_names=["x","pe"], output_names=["output"])
                onnx.checker.check_model(str(onnx_path))
                graph = onnx.load(str(onnx_path))
                result["onnx"] = {"status":"EXPORTED", "sha256":sha256(onnx_path),
                    "bytes":onnx_path.stat().st_size,
                    "operators":sorted({node.op_type for node in graph.graph.node})}
                del graph
                if result["dependencies"]["onnxruntime"]["available"]:
                    stage = "onnxruntime_cpu_compare"
                    import onnxruntime as ort
                    options = ort.SessionOptions()
                    options.intra_op_num_threads = 1
                    options.inter_op_num_threads = 1
                    options.enable_cpu_mem_arena = False
                    session = ort.InferenceSession(str(onnx_path), sess_options=options,
                                                   providers=["CPUExecutionProvider"])
                    maximum = 0.0
                    for target, sample in zip(expected, export_inputs):
                        output = session.run(None, {name:t.numpy() for name,t in zip(("x","pe"),sample)})[0]
                        value = torch.from_numpy(output)
                        torch.testing.assert_close(value, target, rtol=1e-4, atol=1e-5)
                        maximum = max(maximum, float((value-target).abs().max()))
                    result["onnx"]["status"] = "PASS_CPU_SUBGRAPH_ONLY"
                    result["onnx"]["max_abs_error"] = maximum
                    result["onnx"]["providers"] = session.get_providers()
                else:
                    result["onnx"]["runtime_compare"] = "BLOCKED_onnxruntime_missing"
            result["status"] = "SUBGRAPH_PROBE_COMPLETED"
            return 0
    except Exception as error:
        result["status"] = "FAILED"
        result["failure"] = {"stage":stage, "type":type(error).__name__, "reason":str(error)[:2000]}
        return 1
    finally:
        if reader:
            reader.close()
        result["elapsed_seconds"] = time.monotonic()-started
        with (args.output_dir / "result.json").open("x", encoding="utf-8") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status":result["status"], "stage":stage,
                          "elapsed_seconds":result["elapsed_seconds"]}))


if __name__ == "__main__":
    raise SystemExit(main())
