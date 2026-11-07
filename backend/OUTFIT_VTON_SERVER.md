# P48 three-garment server: operator contract

This is a separate, opt-in self-hosted GPU service for one person image and three garment references. Its default is **disabled**. The code does not deploy a server, download weights, accept a license, register an account, send photos to a public demo, or replace the existing local/FASHN/2D engines. P48 has not verified this provider with a real GPU, four-reference model inference, personal photos, or Galaxy S25+ end-to-end generation. A generated file would still require visual review; the API deliberately returns `quality: NOT_EVALUATED`.

## Deployment boundary

- Entry point: `outfit_vton_app:app`, separate from the existing application. Route only `/api/outfit-vton/*` to it behind an operator-approved HTTPS origin. The Android origin must match the existing DataAuth account origin; do not forward account tokens to a public demo or arbitrary server.
- Authentication is the existing `auth_api.current_user` Bearer dependency and existing account database. Provision the approved database connection through deployment secret storage. This service does not implement registration/login or create/migrate the database. Keep `DB_AUTO_CREATE=false`; do not modify production DB or Railway configuration as a setup shortcut.
- Run exactly **one Uvicorn worker and one service instance per storage directory/GPU**. A storage lease and active-job guard reject conflicting owners. They are not a distributed queue or multi-replica scheduling solution.
- API storage and provider work directories must be separate, private paths outside static/uploads, Git, backups shared with unrelated users, and APK/Actions artifacts. Existing directories without the corresponding ownership marker are rejected. Do not create a marker to adopt somebody else's directory.
- POSIX permissions are restricted by the code. On Windows, the deployment operator must review NTFS ACLs for the service account; `chmod` alone does not establish Windows privacy. Inputs, generated images, and job metadata require an approved retention, access, incident-response, and deletion policy before personal-photo service operation.

Both example files remain disabled:

| File | Settings and default |
| --- | --- |
| `outfit-api.env.example` | `OUTFIT_VTON_ENABLED=false`, `OUTFIT_VTON_DEPLOYMENT_APPROVED=false`, empty policy/storage, retention `0` (unconfigured), timeout `240`, daily jobs `2`, loopback HTTP `false` |
| `outfit-vton.env.example` | `OUTFIT_VTON_GPU_ENABLED=false`, `OUTFIT_VTON_GPU_DEPLOYMENT_APPROVED=false`, empty bundle/manifest hash/work directory, minimum reviewed VRAM `0` (unconfigured) |

These files are documentation, not automatically loaded environment configuration. Approval flags are an operator attestation, not permission granted by this document. API retention must be explicitly agreed and between 1 and 86,400 seconds; timeout is greater than zero and at most 1,800 seconds; daily generation limit is 1–20. `OUTFIT_VTON_MIN_VRAM_GIB` must be an operator-reviewed integer in the supported range, based on the complete runtime and available GPU capacity. It is not a measured guarantee that four-reference inference fits.

For an approved isolated deployment, use its dedicated environment and the repository's reviewed backend requirements. A disabled loopback process can be started from `backend` without loading a model:

```text
python -m uvicorn outfit_vton_app:app --host 127.0.0.1 --port 8082 --workers 1 --no-access-log --no-proxy-headers
```

This command does not make the service ready or permit Android HTTP. API transport checks `request.url.scheme == "https"`. `OUTFIT_VTON_LOOPBACK_HTTP=true` is restricted to local synthetic checks with a loopback client and loopback host; Android requires HTTPS.

For an approved local TLS reverse proxy, configure Uvicorn to trust only that proxy's actual IP, for example `--proxy-headers --forwarded-allow-ips 127.0.0.1` instead of `--no-proxy-headers`. The listener must remain inaccessible to untrusted clients, and the proxy must replace incoming forwarded headers. Never use `--forwarded-allow-ips '*'` or trust client-supplied forwarding headers. Alternatively terminate TLS directly with the operator's existing certificate/key files. Do not print credentials or put them into commands, repository files, access logs, or artifacts. Proxy/body/debug logs also need explicit privacy review; `--no-access-log` covers only Uvicorn access logs.

## Model and complete-bundle review

The intended model is **Qwen/Qwen-Image-Edit-2511**, whose official model card identifies Apache-2.0. It is distinct from Qwen-Image-2.1/research-license services and from any public Space. The official Diffusers interface is `QwenImageEditPlusPipeline` with an image list. Model license metadata does not grant permission to use unrelated hosted infrastructure, guarantee output rights for arbitrary inputs, or complete the review of every dependency. Sources: [official model card](https://huggingface.co/Qwen/Qwen-Image-Edit-2511), [official model index](https://huggingface.co/Qwen/Qwen-Image-Edit-2511/blob/main/model_index.json), [Diffusers implementation](https://github.com/huggingface/diffusers/blob/main/src/diffusers/pipelines/qwenimage/pipeline_qwenimage_edit_plus.py).

The operator and accountable license/security reviewer must stage a complete approved snapshot and review code, model weights, text encoder, tokenizer, VAE, scheduler, processor, CUDA/runtime dependencies, input rights, and proposed commercial self-hosting. Pin the official full revision and every runtime distribution version. No placeholder revision, fabricated checksum, assumed license approval, or copied approval from another model may enable the provider.

The manifest is a deployment artifact named `outfit.manifest.json` at the absolute local bundle root. Its schema is:

| Field | Exact requirement |
| --- | --- |
| `version` | Integer `1` |
| `model_id` | `Qwen/Qwen-Image-Edit-2511` |
| `model_revision` | Actual official snapshot's 40-character lowercase hexadecimal revision |
| `license_review.approved` | Boolean `true` only after accountable review |
| `license_review.purpose` | `commercial-self-hosted` |
| `license_review.review_id` | Nonempty reference to the actual review and responsible reviewer |
| `license_review.components` | Exactly `model`, `text_encoder`, `tokenizer`, `vae`, `scheduler`, `runtime` |
| Each component review | Nonempty `license`, official HTTPS `source`, and `commercial_use_reviewed: true`; the review record must cover bundled processor/code and transitive dependencies as well |
| `runtime_packages` | Exactly `torch`, `diffusers`, `transformers`, `accelerate`, `safetensors`, `Pillow`, each with its exact reviewed installed distribution version |
| `files` | Complete mapping of relative POSIX paths to actual lowercase SHA-256 values, excluding the manifest itself |

The manifest's SHA-256 becomes `OUTFIT_VTON_MANIFEST_SHA256`. API/provider `model_sha256` means **this reviewed complete-manifest digest**, not a single model-weight checksum. Each asset is separately bound by `files`. The current code does not supply a pre-approved runtime lockfile or a validated GPU image: an operator must qualify and pin the complete environment, not install unpinned `latest` packages and declare compatibility.

The local snapshot must contain:

- `model_index.json`, `transformer/config.json`, `text_encoder/config.json`, `vae/config.json`, `scheduler/scheduler_config.json`, `tokenizer/tokenizer_config.json`, `processor/preprocessor_config.json`.
- At least one `.safetensors` file under each of `transformer/`, `text_encoder/`, and `vae/`, and every additional file referenced by the approved snapshot.
- Only listed regular files with approved extensions (`.json`, `.safetensors`, `.txt`, `.model`, `.md`, `.jinja`, or root `LICENSE`). Unlisted/empty/missing files, symlinks, junctions/reparse points, pickle weights, Python modules, `auto_map`, and `quantization_config` are rejected. Shard indexes may refer only to listed local safetensors; traversal and remote/unlisted shard references are rejected.

The model index is restricted to `QwenImageEditPlusPipeline` and the following built-in classes:

| Component | Library / class |
| --- | --- |
| processor | transformers / Qwen2VLProcessor |
| scheduler | diffusers / FlowMatchEulerDiscreteScheduler |
| text_encoder | transformers / Qwen2_5_VLForConditionalGeneration |
| tokenizer | transformers / Qwen2Tokenizer |
| transformer | diffusers / QwenImageTransformer2DModel |
| vae | diffusers / AutoencoderKLQwenImage |

Create checksums from the already approved local snapshot; do not download or accept terms as part of verification. For example, in an isolated operator session, `Get-FileHash -Algorithm SHA256 -LiteralPath <approved-file>` computes one file's digest. Use lowercase digests and `/`-separated paths relative to the bundle for `files`; enumerate and review every regular file, not just weight shards. Record exact installed versions with `importlib.metadata.version` for the six listed distributions. After the review is complete, serialize the final manifest, compute its real SHA-256, and provide that digest through deployment configuration. Changing even one manifest byte requires a new reviewed digest.

The following read-only local check validates the existing staged bundle and installed package versions without loading a model or using a network. Run from `backend` in the isolated reviewed environment; it intentionally fails if approval metadata or actual hashes are absent:

```python
import os
from pathlib import Path
from outfit_vton_provider import verify_bundle, verify_runtime

manifest = verify_bundle(
    Path(os.environ["OUTFIT_VTON_MODEL_BUNDLE"]),
    os.environ["OUTFIT_VTON_MANIFEST_SHA256"],
)
verify_runtime(manifest)
print("Local asset and runtime identity checks passed; GPU inference is not verified.")
```

Full file verification happens again inside the worker. Runtime package compatibility, CUDA availability, approved memory capacity, and successful execution remain separate checks. The loader uses a local directory, safetensors, `local_files_only=True`, and `trust_remote_code=False`; offline flags and an isolated cache prevent automatic downloads. No CPU, hosted demo, paid API, 2D, or synthetic-success fallback exists.

## Authenticated API contract

Every route uses existing Bearer account authentication and HTTPS. Job ownership derives from that account; a guessed UUID or device identifier cannot retrieve another account's job. Unknown/wrong-owner jobs return 404. The API does not accept arbitrary remote image URLs or local paths.

| Method / path | Contract |
| --- | --- |
| `GET /api/outfit-vton/config` | Returns `engine`, readiness/reason, exact model identity, policy/retention, image/body limits, and roles. This is readiness, not inference or quality proof. |
| `POST /api/outfit-vton/jobs` | JSON plus canonical UUID `Idempotency-Key`, identical to `request_id`; validates consent, model revision, all four images and all three garment roles; returns 202 and durable job status. |
| `GET /api/outfit-vton/jobs/{request_id}` | Account-owned status; no new generation. |
| `GET /api/outfit-vton/jobs/{request_id}/result` | Verified PNG only for an available completed job; returns request/input/model/result identity headers and `Cache-Control: no-store`. |
| `DELETE /api/outfit-vton/jobs/{request_id}` | Cancel and await owned worker termination, or delete a completed result; keeps the job tombstone and usage reservation. |

POST has exactly these top-level fields: `request_id`, `model_revision`, `consent`, `person`, `garments`. `consent` contains `accepted: true` and the current `policy_version`. `person` is a PNG/JPEG/WebP base64 data URI. `garments` contains exactly one each of `tops`, `bottoms`, and `shoes`; each has a distinct `id`, `category`, `image` data URI, and `placement`.

Placement is photo-normalized metadata with exactly `center_x`, `center_y`, `width`, `scale`, `rotation`. Finite numeric bounds are center coordinates `[-2,3]`, width `(0,4]`, scale `[0.05,20]`, and rotation `[-36000,36000]` degrees. The worker normalizes equivalent rotations only in its prompt hint. These are approximate layout hints, not body measurements or a guarantee of exact garment warp or shoe alignment.

Inputs are bounded before decoding: JSON at most 29 MiB, each decoded source at most 5 MiB; actual MIME/format must agree; animated/multi-frame images are rejected. Person minimum dimension is 256, garment minimum is 128, and each source is at most 20 million pixels. EXIF orientation is applied, metadata removed, and images reduced to fit 2048×2048. Results must be decodable bounded PNGs, at most 8 MiB. A result equal to a canonical input is rejected, but this check does not prove model inference or visual quality by itself.

The exact request bytes are SHA-256 bound. Retry the same UUID with the exact same frozen body only; a different body gives 409. A previous request/status response is reused without spending another generation. Do not automatically POST again with a fresh UUID after a lost response. Daily reservations persist across cancel/restart; the engine permits one active generation, with bounded authenticated upload attempts. Rejected busy, unavailable, expired, timeout, cancelled, interrupted, and failed conditions remain distinct.

Result headers include `X-Request-Id`, `X-Input-SHA256`, `X-Model-Revision`, and `X-Result-SHA256`. The client must bind them to its frozen request and validate bytes before comparison or storage. `completed` means returned-image contract validation, **not** natural garment quality or all-three-product accuracy.

## Worker lifecycle, retention, and shutdown

The API owns request reservation, persistence, status, timeout, owner checks, and result retrieval. The provider implements `initialize`, `check_availability`, `generate`, and `cancel`. It starts one owned subprocess, loads only the reviewed model, and passes `[person, tops, bottoms, shoes]` in that order. Current settings are 40 steps, BF16 CUDA, seed 42, true CFG 4.0, guidance 1.0, one image. These settings follow the multi-image pipeline contract; they have not established real four-reference quality, performance, or memory requirements on available hardware.

Cancel/timeout terminate and join only that owned subprocess. If termination cannot be confirmed, readiness remains blocked with `worker_stop_unconfirmed`; do not clear the journal and pretend capacity is free. A parent-death watchdog is reused from the existing worker infrastructure. No other process, user's program, or GPU workload is killed.

Retention has three different scopes:

1. **Inputs and worker intermediate files:** API keeps decoded inputs in job memory and clears its image dictionary at job completion. Provider writes them only in an owned temporary job folder and removes that folder when its managed job exits. This is not guaranteed secure memory erasure. Parent SIGKILL or an abrupt OS/power failure can leave the four input PNGs, request metadata and intermediate files behind. Approved startup, idle readiness checks and the check immediately before a new job reject any item other than the ownership marker with `workdir_review_required`; no new upload/generation is accepted through a ready provider in that state. The current owned active worker is excluded from the empty-directory check. The service does not open, adopt or automatically delete leftover/unknown entries and remains blocked until operator review and explicit restart. The operator must verify ownership, actual process termination, authorized retention and secure cleanup; crash-time input TTL deletion is **not guaranteed** by this detection. Worker output and metrics are transient there; they are not a permanent inference evidence archive.
2. **Result PNG:** private API storage retains it for the configured interval measured from successful completion. A one-second janitor and request-time purge remove expired results; DELETE also removes a completed result. A stopped server cannot perform wall-clock deletion until it runs again; storage lifecycle controls must match the approved policy. Restart validates surviving result bytes/hash before exposing them. It never injects an older PNG as a new result.
3. **Job metadata/tombstones:** `jobs.json` retains account-derived owner hash, IDs, input/model/result digests, policy, timestamps, status, and usage reservation. It contains no image bodies, token, garment names or raw account ID, but remains potentially linkable personal metadata. **Result TTL does not delete metadata.** Capacity is 10,000 records; exhaustion fails closed. An explicit approved archival/deletion/retention design is required before long-term operation. Do not wipe the journal to reset quotas or replay old UUIDs.

Graceful shutdown waits for owned workers and releases the storage lease. Restart marks unfinished jobs interrupted; it does not regenerate them automatically. Failed/cancelled/timeout jobs cannot expose partial PNGs. Private storage errors disable readiness instead of reporting save success. Access to the folder, system crash recovery, backups, and storage lifecycle remain operator responsibilities.

The child receives a restricted environment and no inherited API/account/Hugging Face tokens or proxy settings. It records safe error codes rather than raw exception messages, input paths or tracebacks. `model_load_seconds`, `inference_seconds`, load/overall peak allocated VRAM, dimensions, and `quality: NOT_EVALUATED` are available in the transient worker metrics after successful execution; they are not measured P48 results.

## Acceptance boundary

Before enabling personal-photo traffic, the accountable deployment/privacy owner must approve the endpoint/origin, existing account authorization, complete asset/runtime licenses, GPU capacity, retention including tombstones/backups, consent wording, and incident handling. The model operator must prove real loading/inference and evaluate tops, bottoms and both shoes together on authorized test inputs. Android QA must then verify a new request, matching result download, comparison, save, reopen, cancellation/error recovery and account isolation in the approved environment.

Mocked worker/provider tests validate contracts and lifecycle only. They do not establish model generation, four-reference visual quality, server deployment, personal-photo permission, S25+ inference, or release readiness. No working GPU deployment or model bundle was provisioned by this implementation. These external prerequisites remain blocking; the existing local/manual and research-example engines must keep their original labels and limitations.
