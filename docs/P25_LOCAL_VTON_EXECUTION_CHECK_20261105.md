# P25 — actual local VTON readiness and focused regression

## Actual generation: not executed / blocked

On 2026-11-05 KST, Windows reports only Intel Iris Xe Graphics and no `nvidia-smi`. The existing backend Python environment has no `torch`, `fashn_vton`, `onnxruntime` or `fashn_human_parser`. Its process environment has no configured local-VTON enablement, weights, token hashes, journal or research-license acceptance; no configured weight manifest exists. No authorized school/team GPU endpoint has been supplied since the prior handoff. This is one blocking execution-environment dependency, not an invitation to repeat GPU searches. No model, license acceptance, account, payment, external photo transfer or inference request was initiated.

There is **no new generated PNG, GPU timing, VRAM or Android actual-result evidence** for P25. Previous official-example generation is a separate public-demo engine, not personal-photo local-server success. Mock PNGs in tests are deliberately synthetic and are not delivery examples.

## Verified code path and minimal fix

- Photo-mode AI entry retains the explicit own-server route. `LocalVtonRepository` uses separate in-memory credentials and `/api/local-vton` endpoints; readiness validates engine identity, ready/configured flags, single garment and bounded TTL. It does not fall back to paid FASHN.
- Screen readiness enables the action only with a person input and valid selected garment, outside a pending/save operation. Consent is separate; the ViewModel checks readiness again before sending prepared images. An unknown POST outcome resumes the same request ID by GET rather than blindly POSTing again.
- Reproduced defect: after synthetic generation for garment A, selecting garment B hides the stale result and saving controls, but the displayed original image still had the accessibility description “자체 AI 생성 결과”. The new UI test failed on exactly that stale description before the fix (1 test, 1 failure).
- Minimal production fix: `LocalVtonScreen.kt` now bases its image description on `currentResult`, the same input-matched result used to choose displayed pixels. No generation, storage, authorization, photo processing or server behavior changed.
- `LocalVtonFlowTest.kt` adds that regression and a configured-but-unready server → explicit successful readiness check test. The latter asserts zero image preparation/POSTs during readiness checks.
- Existing official-demo reentry labeling is retained and covered by `P16DemoLabelTest`; previous results are not presented as a new request.

## Focused verification

- Before fix: `LocalVtonFlowTest.changingGarmentLabelsTheDisplayedOriginalRatherThanTheHiddenOldResult` failed as expected; original log/XML preserved privately.
- After fix: **36 Android/Robolectric tests passed**, zero failures/errors/skips, Gradle exit 0. Final XML: `AiTryOnFlowTest` 14, `LocalVtonFlowTest` 14, `LocalVtonRepositoryTest` 7, `P16DemoLabelTest` 1. This verifies synthetic ready/unready UI behavior, consent, duplicate suppression, cancellation, failure/retry, actual PNG/JPG encoding/decoding in the mock transport, and previous-result labeling. It does not verify a GPU or phone. App/test Kotlin compilation passed; unchanged deprecation warnings remain.
- Backend safety smoke: **6 passed, 20 deselected** in the bounded second run. Covers not-ready rejection, idempotency/conflict/cancel, model failure without output, restart without recomputation, timeout releasing the slot only after stopping, and missing weights/license. Synthetic in-process ASGI/FakeEngine only, no real inference.
- The first restricted backend invocation produced no output/result and was stopped after verifying its process identity. Its evidence remains; the second invocation used detailed output and a 120-second limit. No backend code/test expectation was altered to obtain a pass.
- No complete Android/Preview/Backend suite, CI, APK assembly, signing, installation or device operation was started for P25. P23 full-suite results apply to 71f57804, not to a newly released P25 APK.

Private logs/XML: `fashion_app/build/p25-private/`. They are excluded from Git. Only this handoff, the one-line screen fix and its focused test changes belong to the P25 commit. The commit is intentionally `[skip ci]`: focused correction checkpoint, not a new verified distribution.

## Delivery and next execution conditions

P24 original and delivery APK remain **18601**, source `71f57804250260975e803398616f4f25d6f66bc2`, SHA-256 `e8e7bebb1063d9b013b6a27caeb4f2fd5d30c31e14018c597ba01ca4bfc89435`. The new accessibility correction is source-only and is **not** in that APK. No new APK is required to preserve the existing candidate; shipping this correction later requires a separately validated build with the existing package/signer and a higher version. Do not relabel or replace 18601.

Local delivery directory: `C:\Users\USER\Documents\ChatGPT\capston\deliveries\P24-Preview-18601-20261105-021000-KST`.

P25 independently rehashed all eight original and delivery files against `P24_DELIVERY_VERIFICATION.json`: all match, including the APK hash above. No new device-data hash audit was performed. Initial local and remote HEAD both matched `0f7bc344abcd23b3d58f0df09113a08011d942ac`; the existing branch is retained.

When an authorized CUDA environment is actually supplied, follow `backend/LOCAL_VTON_SERVER.md`: verify CUDA/PyTorch and model/Parser dependencies, approved license scope and pre-approved weight checksums; configure isolated HTTPS, own-server token, private journal and bounded retention. Only then start the existing `local_vton_server:app` and check authenticated `/api/local-vton/config`. No readiness response alone proves inference. First run one authorized nonpersonal upper-body garment example, validate the newly returned PNG and request evidence, then test Android receive/compare/save. Downloads and license acceptance still require the applicable authorization; no such command was executed here.

Next device interaction needs only an explicitly authorized session: unlock/connect S25+, approve USB debugging if needed, and select the specifically approved test person/garment in the own-server flow after its environment is ready. Do not request a personal photo to work around missing GPU, enable public-demo personal inputs, or reinstall/reset existing apps. A later update requires separate authorization and actual installed package/signature/version plus data-preservation checks first.
