# Genlook provider — P50/P51 operator contract

**Default disabled; release NO-GO.** P51 subsequently generated a real three-item PNG through two approved PC-initiated requests (balance9→7→6), with product-detail defects and no Android/S25+ E2E. See the P51 addendum below and its handoff for the latest scope. Historical P50 top-only trial (10→9) failed preservation because the unselected skirt pattern changed; P50 quality preparation made no extra calls. No credits were bought. Preserve the existing on-device/manual engines and Preview/QA isolation.

## Verified service boundary

The current [create endpoint](https://genlook.app/docs/tryon-api/endpoints/create-try-on) accepts 1–4 products. However, [multi-product rules](https://genlook.app/docs/tryon-api/multi-product) permit clothing only; including shoes with other products produces `MULTI_PRODUCT_NOT_SUPPORTED`. This is not a three-product single-call implementation. The default `sequential-3` performs exactly three serial single-product stages; optional P51 paired mode is described below:

1. Original person + top → verified intermediate image.
2. That actual intermediate + bottom → verified intermediate image.
3. That actual intermediate + shoes → verified final PNG.

The next stage never substitutes the original or a previous request's image. Each single-product stage costs 1 credit at standard resolution; all three reserve **3 credits**. The alternative top+bottom together, then shoes, also costs 2+1 credits. A pure review-only planner and multipart body builder are implemented in `genlook_request_contract.py`; the live provider and Android still require the existing sequential-3 contract. No two-call sequence is activated or disguised as three generation IDs. Sequential editing may alter the face, pose, prior garment, hands or background. No quality guarantee follows from the API schema; actual approved input/output review is required.

The single-stage request builder now names the nonselected garments, patterns, face, hair, hands and background requested to remain unchanged. These are descriptions in the documented product field, not a vendor preservation guarantee. No undocumented mask/pose-lock/prompt fields are sent. This changes the provider contract revision to `genlook-sequential-v2-preservation-20261106`. Old ledger fingerprints remain fail-closed; do not delete/reset them or replay the old charged trial.

`outfit_region_preservation.py` is a separate **offline reviewed-mask experiment**, not a provider result path. It rejects stale hashes, unreviewed alignment, size mismatch and invalid masks; it preserves source RGBA outside the effective edit mask and feathers only inward. A local composite is not a new inference or raw vendor output. Its provenance requires a visible external AI-derived label and visual review, since restoring background can remove the vendor's embedded badge. No automatic segmentation/registration is supplied. Never return it with the current unmodified Genlook receipt. The existing-image experiment restored protected pixels but still showed shoulder/cuff/waist seams, so it is not production enabled.

## Account, terms and spending approval

[Genlook terms](https://genlook.app/terms) describe commercial API integration within the customer's plan. Using an API key accepts service terms and the [DPA](https://genlook.app/dpa); an existing key alone is not approval for this project. An accountable client must approve the existing account, terms, data processing, input rights and bounded trial spend before any authenticated API call. Do not create accounts, purchase credits, enable automatic recharge, or accept terms on the client's behalf.

The [pricing notice](https://genlook.app/changelog/try-on-api-plans) lists PAYG packs from **USD5 for 125 credits**, nominally USD0.04 per credit. Three standard calls therefore consume USD0.12 worth of those credits, excluding taxes and subject to the actual account offer. This is not a purchase quote or permission to spend. Plans can incur overages; do not assume a displayed credit balance prevents billing. The implementation requires operator confirmation of prepaid-only use and automatic top-up being off. No purchasing/top-up endpoint or billing configuration change is implemented.

`GET /account/credits` returns `creditBalance` (integer), not a monetary billing receipt; enterprise managed balances can be informational. The adapter checks at least 3 credits before the job and the remaining stage requirement before each upload. It records available balance snapshots but does not infer exact charges from their difference. Other consumers of the account can change the balance. [Credits contract](https://genlook.app/docs/tryon-api/endpoints/check-credits)

## Configuration and launch

Reuse the existing P48 API deployment, Bearer authentication, consent/policy version, request ID, result integrity and private storage. Do not modify production `main.py`, account DB or Railway to activate this experiment. The Android caller must use the same approved HTTPS/account origin; its bearer token must never be sent to Genlook.

- Optional dependency: `requirements-genlook.txt` pins `httpx==0.28.1`. Install only into the approved isolated backend environment, alongside the existing reviewed backend dependencies. No GPU runtime is required by this adapter.
- `OUTFIT_VTON_PROVIDER=genlook` selects the new provider. The existing P48 API flags and policy still need approval; selecting a provider is insufficient to make the service ready.
- `genlook.env.example` is documentation and contains no key. It is not automatically loaded. All flags default false, budgets zero, and review/secret/path fields empty.
- Entry point remains `outfit_vton_app:app`. Follow [P48 HTTPS, authentication and storage setup](OUTFIT_VTON_SERVER.md); do not expose an unapproved public listener.
- The entry point explicitly loads its sibling `backend/.env` before importing account/provider settings, with `override=False` and `interpolate=False`. It does not search the current working directory, change existing process values, expand secret text, or automatically enable generation. `python-dotenv` is already an existing dependency. The user's private `.env` is ignored by Git; never copy its contents into a report or an APK. Existing process settings take precedence, so inspect only safe readiness flags before execution rather than assuming the file's disabled values override them.

| Setting | Required meaning before enablement |
|---|---|
| `GENLOOK_ENABLED` | Explicitly enable only the reviewed adapter configuration |
| `GENLOOK_DEPLOYMENT_APPROVED` | Approved service deployment/origin |
| `GENLOOK_PAID_CALLS_APPROVED` | Explicit call/spend permission, even when a key exists |
| `GENLOOK_PRIVACY_APPROVED` | Approved recipient, inputs, retention and deletion handling |
| `GENLOOK_PREPAID_CONFIRMED` | Account is reviewed for prepaid-only operation |
| `GENLOOK_AUTO_TOPUP_DISABLED_CONFIRMED` | Automatic top-up is confirmed off outside this adapter |
| `GENLOOK_API_KEY` | Server secret; never Android, Git, command-line literals or logs |
| `GENLOOK_TERMS_REVIEW_ID`, `GENLOOK_PRIVACY_REVIEW_ID`, `GENLOOK_COST_REVIEW_ID` | References to actual accountable approvals, not fabricated flags |
| `GENLOOK_LEDGER_DIR` | Absolute private path, separate from P48 image storage; one owner only |
| `GENLOOK_MAX_TOTAL_CREDITS` | Lifetime ceiling for this ledger; at least 3, at most 3000 |
| `GENLOOK_UNIT_PRICE_MICROUSD` | Reviewed unit-price ceiling; USD1 = 1,000,000 micro-USD |
| `GENLOOK_MAX_TOTAL_MICROUSD` | Reviewed total ceiling; must cover the reserved 3-credit job |
| `GENLOOK_TIMEOUT_SECONDS` | Whole adapter job bound; default240, supported10–1800 seconds |

These flags are attestations, not a substitute for account verification. `check_availability` checks local configuration; it does not prove account acceptance, live credits, API uptime or generation quality. Existing Preview/QA builds remain unable to enable server uploads. New runtime activation and any device installation require their separate approvals.

## Exact HTTP flow

The fixed API origin is `https://api.genlook.app/tryon/v1`. Only API calls receive `x-api-key`. No ambient proxy, redirects or automatic transport retry are used.

| Step | Request / validated response |
|---|---|
| Balance | `GET /account/credits` → bounded integer `creditBalance` |
| Stage person upload | `POST /images/upload`, multipart `file`, `crop=false`, per-job non-PII `externalUserId`, `keepForDays=1` →201 and opaque `imageId` |
| Single-product generation | `POST /try-on`, multipart `data` JSON and product PNG; `products` has one inline product with owned `externalId`, title/description, `validForDays:1`, `images[].source.fileKey`; `person.image.source.id` uses the upload response; output uses standard resolution and AI label |
| Accepted job | 201, `status:PENDING`, unique `generationId`, and exactly the expected `productExternalIds` |
| Poll | `GET /generations/{id}` with matching `generationId`; accepts only `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`; pending polling interval1s |
| Result | Completed `resultImageUrl` is downloaded using a separate client, without API key, and decoded/normalized to PNG |

Direct `person` URL/fileKey input is automatically cropped to4:5; uploading with `crop=false` is intentional to preserve full-body framing before generation. Upload ID is opaque and may contain path separators; never interpret it as a local file. [Upload contract](https://genlook.app/docs/tryon-api/endpoints/upload-image)

Result URLs are temporary; the documented example host is `storage.googleapis.com`, and the DPA describes expiring signed links. The downloader accepts only HTTPS on that exact host (port443/default), rejects credentials/fragments/redirects, and bounds bytes, MIME, dimensions, frame count and decoding. JPEG/WebP is actually decoded and converted to PNG, not renamed. Do not log/store signed URLs in public evidence. A changed vendor host must undergo review rather than silently widen the allowlist. [Generation status](https://genlook.app/docs/tryon-api/endpoints/generation-status)

The vendor response does not disclose an exact underlying model, weight revision or input digest. Provenance records `genlook`, `tryon/v1`, three generation IDs, sequential mode and local input/result identities; upstream model is `not_disclosed`. P48's model revision/hash fields bind the reviewed adapter contract/configuration, **not vendor weight bytes**. `quality: NOT_EVALUATED` remains. Successful PNG decoding is not visual-quality acceptance.

## Durable accounting, duplicates and cancellation

The separate private ledger is locked to one adapter instance and bound to the reviewed configuration/account fingerprint. It records job/input identities, a3-credit reservation, owned remote IDs, stage/result hashes and safe states; it does not contain photos, API keys or result URLs. Writes are atomic/fsynced before every paid POST. The same job is never re-posted, including after response loss or restart. Reservations remain after failure/cancel; claimed vendor refunds do not automatically enlarge the local budget. Capacity is1000 jobs. Do not erase/edit the ledger, move to a fresh folder, or change approval values to bypass spend limits or uncertain work. Key/config rotation requires an accountable preservation/reconciliation procedure, not a silent reset.

No single-generation cancel endpoint or server-side idempotency key guarantee was found in the official endpoint index. The [SDK](https://genlook.app/docs/tryon-api/sdk) distinguishes client request timeouts and restricts retries of potentially executed requests. This adapter is stricter: no POST retries. Timeout, network failure or malformed response after a generation POST can mean execution and charging already occurred.

Stopping the app's wait is **not** remote cancellation. If submission/polling is unresolved, the journal is marked uncertain and new use is blocked for reconciliation. Do not report that remote execution stopped, payment was refunded, or photos were erased. Reconcile the recorded generation IDs and owned customer/product namespace through the approved account. A lost response with no generation ID may require vendor support; do not recreate the job to obtain an ID.

## Retention, erasure and privacy limits

Each stage uses one-day upload/product settings. After all three remote jobs are terminal and the final bytes are verified, the adapter requests scoped customer and product cleanup before returning success:

- `DELETE /customers/:externalUserId` →204 even when absent. It removes tagged person photos and result images and anonymizes the user link in historical counts. [Customer deletion](https://genlook.app/docs/tryon-api/endpoints/delete-customer)
- `DELETE /products/:externalId` leaves historical generations. The live single-top trial returned204 for the created top but404 `PRODUCT_NOT_FOUND` for a never-created bottom. Do not infer the customer endpoint's absent-resource behavior applies to products. The adapter deletes only products from accepted stages with a persisted generation ID, accepts200/204, and treats errors for those created products as unconfirmed cleanup. [Product deletion](https://genlook.app/docs/tryon-api/endpoints/list-products)

Deletion is scoped to this job's generated namespace, never an existing user's unrelated records. Partial failure also cleans up when remote work is known terminal. Cleanup failure blocks success and leaves reconciliation metadata. A customer DELETE does not document cancellation of in-flight generation or guaranteed removal of outputs created afterward; uncertain jobs remain blocked rather than claiming deletion.

Vendor image retention is distinct from P48 local result TTL. The [DPA](https://genlook.app/dpa) lists photos/results1,3or7days, unprocessed originals1day, try-on/security records2years, consent5years and residual backups up to30days. Thus a successful cleanup request is not proof that every vendor record/backup vanished immediately. It also lists EU storage with possible generation processing through Google Vertex(global), RunPod(EU), and Comfy Cloud(US). Account owners can see their API photos/results in the dashboard. The client must review recipients, international processing and all retention scopes; never promise own-server-only processing or use for personal photos before approval. [API privacy policy](https://genlook.app/privacy)

## Required live acceptance

No account, unused free credits, spend approval, or real result is assumed. Once authorized, use approved public inputs for one bounded3-credit sequence, with no automatic re-generation. Record actual stage IDs, dates, dimensions, final PNG SHA-256, elapsed time, available balance snapshots, cleanup outcomes and vendor model disclosure status. Compare face/pose/hands/background and top/bottom/shoe product details at each stage; reject drift or missing footwear. Then verify the approved Android build's request→result→compare→save→reopen and cancellation/re-entry behavior on S25+ with data preservation.

Until those live checks, operating/privacy approvals and release/signature gates pass, **NO-GO** remains. This document authorizes no service calls, payment, deployment, device installation or data transfer.
# P51 paired-mode addendum (2026-11-06)

`GENLOOK_MODE=clothing-pair-then-shoes` is an **opt-in** alternative to the unchanged
default `sequential-3`. It sends tops and bottoms in one 2-credit request, then the
actual returned image with shoes in one 1-credit request. Default resolution,
`crop=false`, upload `keepForDays=1`, and maximum 3-credit reservation remain.
The API field `keepForDays` corresponds to SDK `retentionDays`; the uploaded image
ID controls retention. Footwear is not included in the multi-clothing request.

Paired mode uses revision `genlook-paired-v1-20261106-<16hex>` and a v2 spend ledger
with **two real generation IDs** and three product IDs. Existing sequential v1
ledgers and contract hashes remain unchanged. Switching modes against an existing
ledger fails closed. Do not delete/reset a ledger to create new spending capacity;
an operator must reconcile previous spending and approve any new contract/budget.
FastAPI and Android bind receipt mode/ID count to the captured job revision,
including restored jobs, rather than reinterpret a job using current config.

P51 made exactly two authorized real requests from a private PC trial coordinator,
not Android or an active deployed FastAPI route. The first result was visually
reviewed before permitting shoes; the API returned a three-item 896x1184 PNG.
This private trial's **manual intermediate quality gate is not an automated
quality detector in the production provider**. Production result quality remains
`NOT_EVALUATED`; optional two-request execution does not guarantee garment fidelity.
No deployment flags or private environment settings were enabled by this change.
The three-credit P51 trial authorization is exhausted; it is not permission for
another app job. See `docs/P51_GENLOOK_THREE_GARMENT_20261106.md` for actual evidence,
quality defects, Android validation limits and remaining activation conditions.

