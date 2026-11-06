# P52 — Android Genlook integration and isolated QA candidate

## Latest completion — local QA18608 candidate preserved, not installed

Android main and unit-test compilation PASS. Focused checks: **62 PASS across14
classes, 0 final failures/errors/skips**, including native Robolectric Compose progress,
paired disclosure, previous-result recovery and real local PNG save/reopen with
synthetic data. Backend: **51 unique PASS**, not the sum of overlapping runs.
QA18608 build, Lint, trusted signing and local candidate checks PASS; a separate
delivery copy and verification records are now preserved.
**Not installed; no new external generation; NO-GO.**

Branch: `codex/fashionai-p52-android-genlook-20261106`.
P51 parent: `43200198a8cfc76d2a5b07db92be75dd422d48b8`.
P52 implementation checkpoint: `1b3cde1ecfdd1c1555ab71c3476f7473e9726be6` (WIP label preserved).
Final tested tree and APK execution SHA: `f2ba6272d0d50db6af464f7e94e17e026ab3b5cb`.
The intervening four UI-test files and this document changed, with no main/backend
execution-code change. All285 Kotlin source hashes from compile07 match this tree.
The final documentation-only commit is later than the execution SHA; no rebuild.
No Push, remote mutation, GitHub Actions, device access, account/DB creation,
deployment, personal-image access, new Genlook credit query or generation occurred.
Last observed balance remains **6 credits from P51**, not a new P52 observation.

## Actual delivery and verification

Delivery directory (copied, not moved):
`C:\Users\USER\Documents\ChatGPT\capston\deliveries\P52-QA-18608-20261106-213432-KST-8304f0fb`.

- APK: `fashionai-1.1.86.8-qa-vc18608-f2ba6272.apk`,149027053 bytes.
- Package: `com.jeiu.fashionproject.preview.qa`.
- Version: `1.1.86.8-qa` / `18608`; higher than historical QA18607.
- SHA256: `ba159604c203142c83bef320c4234e8e3547b88e928834d81bbd9714f593b856`.
- Trusted Preview/QA certificate SHA256:
  `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- Embedded source/branch/clean identity, package/version, signer, ZIP CRC, required
  DEX markers and APK secret-file checks PASS. Signing used the existing encrypted
  Preview-only key backup; the new restricted temporary key file was removed.
- ZIP16KB alignment PASS. Every native-library byte hash matches the previously
  LLVM-audited reference; its ELF LOAD16KB evidence is reused by exact identity.
  **No new16KB runtime or device compatibility execution is claimed.**
- `assembleDebug` + `lintDebug`: BUILD SUCCESSFUL in8m47s,50 tasks.
  Lint:0 Error/Fatal,166 Warning,3 Hint; warnings are not claimed resolved.
- `BUILD_INFO`, `LOCAL_VERIFICATION.json`, native/ZIP/Lint records,
  `TEST_SUMMARY.json`, copy hashes, installation conditions and preservation scope
  are included. `distributionVerified=false`: local candidate, not a CI publication
  or approved installation. The historical version floor needs a fresh device check.
- Previous delivery APKs18605 and18607 still match their recorded hashes.
  Other existing deliveries were not rewritten; no renewed all-files audit is claimed.

The completed build's first signing preflight was correctly blocked at601MiB RAM
while its single-use JVM was exiting. After the JVM exited and original resource
floors passed, `finish_qa_candidate.ps1` snapshotted and signed the completed APK
without rerunning Gradle. The private delivery helper initially mistook the empty
`signing-operation.log` file for a signing directory; its predicate was corrected
to reject directories and actual keystore/JKS/DPAPI files. It then copied all
artifacts successfully without overwriting evidence or weakening secret checks.

## Implemented changes

- Reuse the P51 paired generation contract: tops+bottoms, then shoes; maximum
  3-credit reservation. Existing provider, job IDs, authentication, image hashes,
  cancellation limitations, result retention and no-automatic-replay rules remain.
- Provider exposes transient, sanitized current-stage observations bound to its
  active job and input hash. API validates an exact field/enum/count schema and
  omits unavailable/invalid metadata. No journal migration or vendor identifiers
  are added to the progress response. Terminal jobs do not expose live progress.
- Android binds mode/count to the recorded model revision and rejects stage/phase
  regression, including regression after an omitted-progress response. Human text
  distinguishes the two generation stages without inventing an inference percentage.
- An unresolved previous remote execution/reconciliation prevents even preparing
  a new image request. Explicit same-job status recovery is required. Changing
  server configuration preserves the old journal and does not fetch or upload.
- Debug QA only: an explicit manual HTTPS origin and separate app-server user token
  may be stored in RAM. It never falls back to Preview/production authentication.
  Production host, trailing-dot host variant and direct vendor API origin are
  rejected. Password field is masked and uses a password keyboard. Connection
  visibility across IO/main threads is volatile. No token is saved with the job.
- Existing on-device and manual fitting routes remain. The distinct **서버형 마법봉 ·
  3종 AI** entry reuses the frozen person/top/bottom/shoes selection. The QA panel
  does not authorize a new installation, server deployment or paid/free-credit call.

## Found defects and limits

1. Provider upload completion initially reported `preparing`, reversing an earlier
   `uploading` phase. A red regression produced `[1,0,2]`; fixed to `submitting`.
   The relevant 21 backend tests passed after correction.
2. QA connection initially retained a `Bearer` prefix while existing transport
   adds its own prefix. Source review found the resulting double-prefix 401 defect;
   fixed to the same raw-token contract as DataAuth. Synthetic assertion passed.
3. Added rejection of trailing DNS root dots to avoid bypassing the blocked
   production-host comparison. This is origin validation, not proof of server trust.
4. A custom `StandardTestDispatcher` supplied to the Compose rule stalled automatic
   scrolling; bounded real swipes exposed a post-click timeout instead. Compact
   assertions proved the restored ticket and exactly one Resume node existed.
   The same test with the default Compose clock passed in17.719s, including swipes,
   GET3/POST0, both stages and uncertain-terminal handling. Three related old UI
   fixtures use that default clock now; their original data, sizes, scrolling and
   assertions all passed unchanged. This was test scheduling, not evidence of phone
   data loss. No app code or acceptance criterion was changed to hide the failure.

## Actual local evidence and reproduction

Private Android root:
`C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\fashion_app\build\p52-private`.
Backend root: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P52-20261106`.
These ignored paths contain logs/XML/runtime argument files, not committed images,
credentials or user data. Existing evidence is never overwritten.

| Run | Actual outcome |
|---|---|
| backend01 | 50 PASS, 4.70s; synthetic provider/API and P50/P51 integration regressions |
| progress-red | 1 expected regression FAIL before phase-order correction |
| backend02 | 21 PASS, 3.83s after correction; overlaps backend01, not 21 new features |
| compile01 | 384MiB heap GC exhaustion; only the owned JVM was stopped after identity verification; not a source compile PASS |
| compile02 | Not started: RAM987MiB below1100MiB safety floor |
| compile03 | Actual main+test compilation PASS, 4m7s; real Gradle Test dependencies/runtime exported |
| junit01/02 | Private runner setup errors (`-J` argument-file placement, Java Result type ambiguity); corrected, no app verdict |
| junit03 | QA connection4 + stage/state7 PASS; screen JVM stopped with preserved GC/thread evidence |
| compile04 | Main+test compilation PASS, 3m29s; only UI diagnostic assertions changed |
| junit04 | Restored job and Resume count assertions passed; test scroll loop stopped with evidence, screen result not PASS |
| junit05-core | 47 PASS across P39 images/handoff, P46 availability, P48 entry/contract/controller, P50 disclosure and P51 paired contract |
| compile05/06/07 | PASS1m12s/1m13s/1m41s; main compilation reused, only test dependency work where needed |
| junit06-ui | 1 FAIL after bounded touch succeeded but post-click custom-scheduler wait timed out; actual failure XML retained |
| junit07-ui | 1 PASS17.719s with default Compose clock; same stage/text/request/journal criteria |
| junit08-ui-regression | 3 PASS: P51 paired/old-result, P50 external policy, P48 compare/save/reopen;26.672s/19.937s/19.0s |

Final unique Android count62 uses junit03's two passed classes, junit05-core,
junit07-ui and junit08-ui-regression. Interrupted/failed earlier attempts are not
counted as successes or added to the final total. No full CI/full-suite rerun.
Backend unique count51 uses backend01 and backend02 with20 overlapping cases
replaced by their post-fix result, not counted twice. The private test summary
records exact XML paths, SHA256, test names and source bindings.

Changed production files: `backend/genlook_outfit_provider.py`,
`backend/outfit_vton_api.py`, Android `ServerOutfitQaConnection.kt`,
`ServerOutfitVtonRepository.kt`, `OutfitQaConnectionPanel.kt`,
`ServerOutfitVtonScreen.kt`, `ServerOutfitVtonViewModel.kt`.
Changed/added tests: `test_genlook_p52_progress.py`, `test_outfit_p52_progress.py`,
`P52QaConnectionTest.kt`, `P52ServerProgressTest.kt`, `P52ServerProgressUiTest.kt`,
`P48ServerOutfitUiTest.kt`, `P50ServerProviderDisclosureUiTest.kt`,
`P51ServerPairedModeUiTest.kt`. This document records the handoff.

Compilation is single-worker, offline, nonparallel, in-process Kotlin, SerialGC,
2 active processors, Xmx768m. The actual Gradle Test classpath and resources are
exported by `prepare-runtime.init.gradle.kts`, then Gradle exits before one real
JUnit/Robolectric JVM runs. This avoids overlapping Gradle and test heaps. It does
not replace tests with mock implementations or claim device execution.

The source `compile-focused*.ps1`, `run_junit.py`, `P52JUnitRunner.java` and per-run
source hashes preserve the exact local commands. Use a fresh evidence directory
for any necessary rerun; do not rerun completed unchanged classes automatically.

At resume RAM was533MiB, no residual Java/Python builds. Later measurements fluctuated.
The user explicitly approved stopping the separate week2 Docker container and Docker
Desktop (not deleting them); Ubuntu WSL with another Codex session was retained.
Compile03 started at1482MiB available RAM and12221MiB Commit headroom. After its
JVM exited, available RAM recovered to about1996MiB. Apart from the explicitly
approved Docker stop, other user applications and global/system memory settings
were not changed. Disk before that build was5.94GiB free.

## Live evidence is P51, not an Android P52 result

Preserved P51 private evidence: `experiments/P51-20261106/live-01` outside this worktree.
Final real Genlook PNG:896x1184; SHA256
`4e7f002369eda89060f1bcc06dad1b710a13e5de154de2912084dd8b2af1ac38`.
Two actual requests used3credits; receive times15.532s+27.242s. This was a PC
coordinator trial using the real adapter helpers, not a deployed FastAPI route or
an Android-initiated request. Attribution/deletion acknowledgements remain in P51.

The jacket's button count/texture, covered trouser detail and shoe stripe/shape
drift remain quality defects. Face/hands were regenerated, not pixel-preserved.
P52 does not postprocess/relabel this image or inject it as a new app result.

## Exact remaining live QA prerequisites

1. Operator-approved isolated HTTPS service, existing PostgreSQL authentication
   schema and data-enabled QA user; no production DB/token reuse or auth bypass.
   Authentication uses the existing opaque40–100-character Bearer token, not JWT.
   The standalone outfit service does not issue accounts/tokens itself.
2. Existing entry: from an approved isolated backend environment,
   `python -m uvicorn outfit_vton_app:app --host 127.0.0.1 --port 8082 --workers 1 --no-access-log --no-proxy-headers`.
   It loads the sibling `.env` with override/interpolation disabled. Phone access
   requires approved HTTPS termination; loopback startup alone is not device access.
3. Existing enabled/deployment/privacy/cost/operator gates, policy/retention and
   `GENLOOK_MODE=clothing-pair-then-shoes` must be reviewed. Preserve P51's consumed
   trial ledger and intent markers. Provider v2 ledger is a different format; do not
   adopt/reset a historical trial or erase reservations to authorize another job.
4. A new bounded credit/input/processing approval: the previous3-credit allowance
   is consumed. The remaining6 credits do not authorize another request.
5. Separate version-specific QA APK installation approval, then fresh device
   package/signer/version/space and accessible data baseline. Preview18605 remains
   untouched. No uninstall, downgrade, reset or test-data injection into Preview.
6. S25+ new request→matching result→comparison→save→reopen→restart/requery, plus
   cancellation/network/error/duplicate cases and product-fidelity acceptance.

No fresh device-data hash audit was performed in P52. Preservation is by no device
mutation, not a renewed claim covering every phone store. Existing delivery APKs,
P38–P51 models/logs/images, Preview18605 and QA18607 remain protected.

Four actual agents handled independent development/review; only root ran tests,
local Git and packaging coordination. This is not ten concurrent agents or a
formal Release signing/deployment approval.
