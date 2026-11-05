"""Inspect the pinned full FASHN ATen shape graph using meta tensors only.

No real weights are loaded or serialized. A successful graph capture is NOT an
ONNX/LiteRT artifact, numeric validation, model inference or Android execution.
Run only as an owned child of p41_monitor.py, after other model workers exit.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib
import json
from pathlib import Path
import time

from p40_flatlay_probe import (NAMESPACE, namespace_source, offline_network_guard,
                              sha256, verify_source)


def tensor_metadata(value):
    """Record only structural metadata; never materialize or read tensor values."""
    if hasattr(value, "shape") and hasattr(value, "dtype"):
        return {"shape": [str(d) for d in value.shape], "dtype": str(value.dtype),
                "device": str(value.device)}
    if isinstance(value, (tuple, list)):
        return [tensor_metadata(item) for item in value]
    if isinstance(value, dict):
        return {str(key): tensor_metadata(item) for key, item in value.items()}
    return {"metadata_type": type(value).__name__}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    args.output_dir.mkdir(parents=True, exist_ok=False)
    result = {"scope": "whole_original_model_meta_shape_graph_only",
              "whole_meta_graph_captured": False, "real_weights_loaded": False,
              "real_weights_exported": False, "onnx_exported": False,
              "litert_exported": False, "numerically_validated": False,
              "android_executed": False, "png_generated": False}
    started = time.monotonic()
    stage = "source_verification"

    def event(name, **fields):
        nonlocal stage
        stage = name
        record = {"stage": name, "utc": datetime.now(timezone.utc).isoformat(),
                  "elapsed_seconds": time.monotonic()-started, **fields}
        with (args.output_dir / "events.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(record, ensure_ascii=False)+"\n")
        print(json.dumps({"stage": name, "elapsed_seconds": record["elapsed_seconds"]}), flush=True)

    try:
        event(stage)
        verify_source(args.source_dir, args.source_manifest)
        result["source_manifest_sha256"] = sha256(args.source_manifest)
        result["source_revision"] = json.loads(args.source_manifest.read_text(encoding="utf-8-sig"))["revision"]
        with offline_network_guard():
            import torch
            torch.set_num_threads(1)
            torch.set_num_interop_threads(1)
            result["torch_version"] = str(torch.__version__)
            namespace_source(args.source_dir)
            upstream = importlib.import_module(NAMESPACE + ".tryon_mmdit")
            event("meta_model_and_inputs")
            with torch.device("meta"):
                model = upstream.TryOnModel().eval().requires_grad_(False)
                x = torch.empty(1,3,864,576, dtype=torch.float32)
                times = torch.empty(1, dtype=torch.float32)
                kwargs = {
                    "ca_images": torch.empty_like(x), "garment_images": torch.empty_like(x),
                    "person_poses": torch.empty(1,1,864,576, dtype=torch.float32),
                    "garment_poses": torch.empty(1,1,864,576, dtype=torch.float32),
                    "garment_categories": torch.empty(1, dtype=torch.int64),
                    "mask": torch.empty(1, dtype=torch.bool),
                }
            state = model.state_dict()
            if not state or any(not tensor.is_meta for tensor in state.values()):
                raise RuntimeError("all_model_state_must_remain_meta")
            if any(not tensor.is_meta for tensor in (x,times,*kwargs.values())):
                raise RuntimeError("all_inputs_must_remain_meta")
            result["meta_parameter_elements"] = sum(p.numel() for p in model.parameters())
            result["meta_state_tensor_count"] = len(state)
            result["meta_state_logical_bytes"] = sum(t.numel()*t.element_size() for t in state.values())
            result["input_metadata"] = {"x": tensor_metadata(x), "times": tensor_metadata(times),
                                         **{name:tensor_metadata(value) for name,value in kwargs.items()}}
            del state
            event("strict_torch_export_started", parameter_elements=result["meta_parameter_elements"])
            exported = torch.export.export(model, (x,times), kwargs=kwargs, strict=True)
            if any(not tensor.is_meta for tensor in exported.state_dict.values()):
                raise RuntimeError("export_unexpected_real_parameter_materialization")
            event("strict_torch_export_captured")
            nodes = []
            for node in exported.graph.nodes:
                nodes.append({"name": node.name, "operation": node.op, "target": str(node.target),
                              "value_metadata": tensor_metadata(node.meta.get("val"))})
            counts = Counter(node["target"] for node in nodes if node["operation"]=="call_function")
            inventory = {"scope": result["scope"], "nodes": nodes,
                         "operator_counts": dict(sorted(counts.items()))}
            data = json.dumps(inventory, ensure_ascii=False, indent=2).encode("utf-8")
            with (args.output_dir / "graph-inventory.json").open("xb") as stream:
                stream.write(data)
            result.update(status="META_GRAPH_CAPTURED_ONLY", whole_meta_graph_captured=True,
                          graph_node_count=len(nodes), operator_counts=inventory["operator_counts"],
                          inventory_sha256=hashlib.sha256(data).hexdigest(), inventory_bytes=len(data))
            event("inventory_saved")
            return 0
    except Exception as error:
        result["status"] = "FAILED"
        result["failure"] = {"stage": stage, "type": type(error).__name__, "reason": str(error)[:3000]}
        event("failure_recorded", failed_stage=stage, exception_type=type(error).__name__)
        return 1
    finally:
        result["elapsed_seconds"] = time.monotonic()-started
        with (args.output_dir / "result.json").open("x", encoding="utf-8") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    raise SystemExit(main())
