# P31 — verified local Parser inputs (no downloads or license acceptance here)

P30 verified three FASHN/DWPose files, but the Parser constructor still selected an
ambient Hugging Face cache entry. Offline mode alone did not authenticate those bytes.
The independent `parser.manifest.json` below is now mandatory for CLI preflight,
server readiness and the real worker. The original `vton.manifest.json` format stays intact.

## Reviewed loading contract and limitations

- [Pinned VTON pipeline](https://github.com/fashn-AI/fashn-vton-1.5/blob/7c0f10af3f91ad4048fe9729c470a13ef905d25a/src/fashn_vton/pipeline.py)
  constructs the Parser from `_setup_hp_model(self)`.
- [Pinned Parser implementation](https://github.com/fashn-AI/fashn-human-parser/blob/f2771f2fb8655349e87e2869bbde7ace0bd06f2c/src/fashn_human_parser/parser.py)
  loads a `SegformerForSemanticSegmentation` model. Its normalization, prediction and
  labels remain inherited in our adapter. There is no separate base SegFormer checkpoint
  load in this pinned Parser path: the fine-tuned Parser safetensors include that model.
- Narrow subclasses replace only model construction. They explicitly use the validated
  local bundle with `local_files_only=True`, `use_safetensors=True`, `trust_remote_code=False`.
  No global monkey patch or fallback to ambient cache/repo/ref is used.
- Installed `fashn-vton` and `fashn-human-parser` must have official GitHub origins and
  the above immutable revisions in their PEP 610 metadata. PyPI-only/editable/unknown
  revisions are intentionally unverified. Runtime signatures are checked before adaptation.
- This metadata check is not protection against an administrator forging an installed
  distribution. Keep code, assets and reviewed manifests read-only to the inference user.
  Checksums are checked again immediately before load; concurrent hostile file mutation
  is not a supported operating model. No actual CUDA load has been verified in P31.

## Separate component review

The [Parser license notice](https://github.com/fashn-AI/fashn-human-parser/blob/f2771f2fb8655349e87e2869bbde7ace0bd06f2c/LICENSE)
refers to the [NVIDIA SegFormer license](https://github.com/NVlabs/SegFormer/blob/65fa8cfa9b52b6ee7e8897a98705abf8570f9e32/LICENSE).
The latter restricts use to non-commercial research/evaluation. This implementation
does not infer permission from the VTON model's separate license, accept terms, or
assert that a demonstration or commercial distribution is legally authorized.

`license_review` records an operator's separately approved review. Matching hashes
only prove that supplied files still match that record, not that the operator obtained
lawful permission or that the declared model origin is independently authenticated.
The operator must verify provenance and terms against the pinned official sources.
No names, emails, credentials or personal images belong in this manifest.

## Existing approved files only

After obtaining the necessary separate approvals, an operator may stage an existing,
reviewed Parser snapshot into a dedicated directory under `LOCAL_VTON_WEIGHTS`:

```text
parser.manifest.json
human-parser/config.json
human-parser/model.safetensors
licenses/human-parser.txt
licenses/segformer.txt
```

Use a **copy** of the explicitly selected approved snapshot, not a symlink to a mutable
shared HF cache. Do not scan an entire cache or copy unrelated models. No pickle/bin,
index, executable code, extra model file or symlink is allowed in `human-parser/`.
`config.json` must declare the SegFormer semantic-segmentation architecture and cannot
contain `auto_map` or quantization redirection. The exact two model files and two
license-evidence files must match SHA-256 entries. Missing, modified or unreviewed
records fail closed. The template below is deliberately **not executable approval**:

```json
{
  "version": 1,
  "model_repo": "fashn-ai/fashn-human-parser",
  "model_revision": "<independently reviewed 40-character HF snapshot commit>",
  "files": {
    "human-parser/config.json": "<approved SHA-256>",
    "human-parser/model.safetensors": "<approved SHA-256>",
    "licenses/human-parser.txt": "<reviewed original notice SHA-256>",
    "licenses/segformer.txt": "<reviewed original terms SHA-256>"
  },
  "license_review": {
    "reviewed": false,
    "purpose": "noncommercial-research-evaluation",
    "sources": {
      "human-parser": "https://github.com/fashn-AI/fashn-human-parser/blob/f2771f2fb8655349e87e2869bbde7ace0bd06f2c/LICENSE",
      "segformer": "https://github.com/NVlabs/SegFormer/blob/65fa8cfa9b52b6ee7e8897a98705abf8570f9e32/LICENSE"
    }
  }
}
```

Do not fill this from arbitrary local bytes and describe it as publisher verification.
No tool in this change downloads a Parser checkpoint, auto-accepts these terms or turns
`reviewed` on. The existing explicitly invoked main-weight downloader no longer creates
an unpinned Parser as a side effect and reports its remaining preparation requirements.

## Offline verification and next actual GPU execution

1. With the reviewed existing assets and pinned installed packages, run from `backend`:
   `python -m local_vton_parser --weights-dir /private/approved-weights`.
   This checks bytes/review records/code revisions without importing models or connecting
   to any service. A passing result still says `model_loaded=false`, `generated=false`.
2. Preserve the separate authenticated server settings and explicit research-license
   gate in `LOCAL_VTON_SERVER.md`. `parser_integrity_unavailable` means the service is
   not ready; the old Android client fails closed through its generic error text.
3. Only after an approved CUDA environment and nonpersonal test inputs exist, follow
   the explicit opt-in `test_local_vton_real_model.py` procedure. It invokes the real
   worker and new adapter, not a mock. Verify fresh PNG, timing/VRAM and quality manually.
4. Android receive/compare/save and any personal-photo consent remain a separate stage.
   Never replace these proofs with a synthetic adapter test or prior public-demo image.

P31 adds no GPU, account, paid resource, weights, personal input, installation or server
deployment. Actual GPU/runtime/quality compatibility remains unverified until step 3.
