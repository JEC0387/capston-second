# P44 PC accelerator probe — scope and reproduction

This document describes an executable experiment, not a successful run. The
root coordinator owns actual execution and preserves `result.json`, process
monitor logs, runtime/driver versions, and hashes in separate experiment paths.
The existing Android engines, data, installed APKs and GPU drivers are unchanged.

## Evidence required

`backend/p44_accelerator_probe.py` consumes the existing P42 real-weight
attention ONNX file (26,246,974 bytes; SHA-256
`59593a1748881b4f040b476b64f79da42ac4379f4dbb951c6af8fb385f6f632a`).
It does not download weights, read private images, or register an app engine.

Two deterministic synthetic inputs have shape `x=[1,16,1280]` and
`pe=[1,1,16,64,2,2]`. Position tensors are calculated with the pinned official
FASHN formula; **both hashes must exactly match the P42 Torch EmbedND outputs**.
Feature inputs intentionally use a new NumPy seed, shared by CPU and GPU.
The CPU reference is ONNX Runtime. It is released before GPU compilation.
FP32 outputs must match at the existing P42 tolerance (rtol 1e-4, atol 1e-5).
Failure is preserved; the tolerance is not relaxed to accommodate hardware.

For OpenVINO, the compile target is explicitly GPU, never AUTO/HETERO. The
effective execution devices must all be GPU. FP32 precision is requested.
Known GPU-plugin primitives with internal CPU implementations are rejected.
For DirectML, sequential execution and disabled memory patterns are mandatory.
CPU EP fallback is disabled; actual profiling events must identify only
`DmlExecutionProvider`. Merely registering the provider is insufficient.

The main process records RSS/private commit and compile/infer times. Run the
probe serially under the existing `p41_monitor.py`; these snapshots do not
replace system-memory and process peak measurements by that monitor.

## Runtime choice and official sources

OpenVINO lists Intel Iris Xe and Windows 10/11, but requires an installed GPU
driver. A named GPU in Windows inventory does not prove that the runtime can
use its driver. See [system requirements](https://docs.openvino.ai/2025/about-openvino/release-notes-openvino/system-requirements.html).
Use an isolated package target for the runtime only; do not replace the P40
environment or install a driver during this experiment. The [pip installation
guide](https://docs.openvino.ai/2025/get-started/install-openvino/install-openvino-pip.html)
documents the package. The [GPU documentation](https://docs.openvino.ai/2025/openvino-workflow/running-inference/inference-devices-and-modes/gpu-device.html)
describes GPU operation and its internal CPU-primitives caveat.

DirectML is a Windows DirectX 12 execution provider, not an Android provider.
The existing runtime family is 1.21.0; an isolated matching
`onnxruntime-directml==1.21.0` avoids changing the existing ORT installation.
Its official [execution-provider documentation](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html)
requires sequential execution, disabled memory patterns, and no concurrent
Run calls on one session. An old Intel driver may still reject compilation;
an actual run is required before claiming compatibility.

## Invocation (root coordinator only)

Use the already authorized base Python interpreter and P40 dependency paths.
The explicit isolated runtime directory takes precedence without processing
`.pth` files. Select one engine and a **new** output path per attempt:

```text
python backend/p44_accelerator_probe.py --engine directml --runtime-deps <isolated-directml>
  --model <P42/mobile/subgraph04/real-attention-subgraph.onnx>
  --p42-result <P42/mobile/subgraph04/result.json> --output-dir <new-evidence-path>
```

For OpenVINO select `--engine openvino` with its own isolated target. Root must
wrap the actual interpreter with existing memory protection; no parallel heavy
model/session execution. Dependency installation and execution are not done by
the read-only/review agent.

## What success would and would not establish

A passing probe establishes execution of one actual-weight, 16-token attention
block on this PC GPU and numerical comparison with CPU. It does **not** prove
full FASHN loading, full-sequence memory safety, sampling, PNG quality, Android
conversion, Qualcomm NPU compatibility, or S25+ execution/performance.

The full model has 971,813,808 parameters (3,887,255,232 FP32 bytes before runtime
copies/activations). Its BF16 source checkpoint is 1,943,668,048 bytes. Iris Xe
shares system memory; it does not add dedicated VRAM to the 7.73 GiB PC. FASHN's
full-sequence attention is 6,912 tokens rather than 16. A conventional FP32
`[1,10,6912,6912]` score tensor alone is 1,911,029,760 bytes; a runtime may fuse
or otherwise avoid it, but that must be observed rather than assumed.

P42 separated official CPU positional encoding because the original FP64 Cos
was unsupported by ORT CPU. This partition cannot be generalized to a full
Android engine without validating full-shape inputs and all remaining operators.
The one-block timing cannot be extrapolated into a promised whole-model latency.
FASHN supports tops/bottoms/one-pieces; this probe adds no shoe-generation path.
The reviewed parser-free flat-lay experiment does not confer a Human Parser
distribution license. No final release GO follows from a subgraph probe.

## Follow-up: bounded full-sequence attention experiment

`backend/p44_chunked_gpu_probe.py` adds a separate, non-production experiment.
It reads the six exact P42 FP32 initializers and builds two explicit ONNX
graphs: qkv + RMSNorm + RoPE, then full-key/value attention + projection.
First, both new graphs are compared on CPU against the unchanged P42 ONNX for
both official 16-token positional fixtures; a separate NumPy reference is also
checked against P42. Either mismatch stops before the larger experiment.

For 6,912 synthetic tokens, queries are processed in chunks of 128 while every
query still sees all 6,912 keys and values. The largest explicit score tensor is
35,389,440 bytes rather than the 1,911,029,760-byte full score. This does not
reduce the attention's semantic receptive field or use approximate attention.
The full-sequence synthetic coordinate grid has 72 columns and is **not** a
claim about actual person/garment condition preparation.

CPU reference arrays and GPU arrays are stored separately as `.npy` files;
CPU Q/K/V mappings are closed before GPU compilation. Warm-up work is excluded
from measured inference sums. DML profiling rejects any executed CPU EP node.
The 54 K/V transfers remain a real cost and may remove a GPU speed benefit.
Memory snapshots are supplementary: root must use the existing process/system
monitor and must not run another model experiment concurrently.

Add `--onnx-deps <P42/mobile/onnx-deps02> --tokens 6912 --query-chunk 128` to the
corresponding shared arguments. For an initial bounded full-path check use
`--tokens 16 --query-chunk 16`. No execution result is asserted in this section;
consult the coordinator's new result/monitor files. Even full-sequence success
only covers one SelfAttention block, not the complete joint MMDiT or sampler.
