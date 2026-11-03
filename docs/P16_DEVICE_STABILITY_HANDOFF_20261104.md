# P16 device stability — 2026-11-04

## Latest verified state (CI/APK checkpoint)

**Code/CI/APK verification passed; physical UI testing is waiting for the phone to be unlocked. Do not report P16 device interaction or 16KB runtime verification as complete.**

- Execution commit: `94e4e3a7b5b0a5017f934e50ab840260b067fba7`.
- [Final CI 41726982899](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899): all **7 jobs successful**.
- Android **440 passed**, Preview **204 passed**; each 0 failed/errors/skipped. Backend **151 passed + 8 subtests**, 7 warnings. Release/native safety **10 passed**. Lint **0 errors, 165 warnings, 2 hints**.
- Instrumentation sources/APKs compiled and signed. Physical instrumentation **not executed yet**: S25+ is ADB-authorized but `showing=true`, `inputRestricted=true`, screen off. User unlocked earlier, but it automatically locked again during CI. A second unlock request is pending; no lock bypass/settings changes.
- Preview `com.jeiu.fashionproject.preview`, `1.1.79.1-preview`, **17901**; [APK artifact 13276820721](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899/artifacts/13276820721).
- APK SHA-256: `95060779ed1af0804762b1c039da2e28d51ea9ba01b53a44b90148b0563e0119`.
- Local APK: `C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\fashion_app\build\p16-release\fashionai-1.1.79.1-preview-vc17901-94e4e3a7.apk`.
- Package/version, existing trusted certificate, clean source SHA, embedded identity, SHA256SUMS and full ZIP CRC independently verified locally. CI native reports match those exact downloaded APK hashes: **9/9 ELF64 libraries pass, ZIP passes, all four ABIs retained**, for both Preview and QA.
- Only the separate **FashionAI QA P16** app (`com.jeiu.fashionproject.preview.qa`) and its `.test` package were installed successfully. Their signer and exact instrumentation target were checked. No test fixture was executed/written yet.
- Existing Preview remains **17701 / 1.1.77.1-preview**; no user-app update/uninstall/clear. After QA installation, all **22 existing Preview data file hashes unchanged**, 0 missing/changed, 0 new files. Original app, photos, profiles, queue, journal and backups untouched.
- QA APK SHA-256: `c12e6e473e6b47e6c1e509491eaca390ecf4561a5104efe8f707702739504de1`; [separate QA artifact 13276252269](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899/artifacts/13276252269). It is not a Preview update.

| Required area | Final verified scope | Remaining physical check |
|---|---|---|
| Home/body readability | Exact long weather fixture at 384dp passed, no Home production change; draft/UI regressions passed | P16 body editor visibility on S25+; historical Home onset remains unknown |
| Seven parts / 0.5cm ruler | Automated regression passed; no unsupported ruler math rewrite | Guarded seven-part touch/input test |
| Garment selection/pan/scale/rotation | Selection/bounds/UI tests passed | Synthetic S25+ gestures |
| Auto-fit/undo | Reproduced original drift and corrected; repeat/no-op tests passed | Actual touch + repeated auto-fit + undo |
| Save/process restart | Guarded two-phase instrumentation ready and compiled | Execute both phases with different QA process IDs; not yet verified |
| Camera/photo analysis | Full automated regression passed; native smoke prepared | Actual bundled model smoke and camera capture; no real photo processed |
| 16KB | Final ELF/ZIP checks passed | Real 16KB runtime unavailable; S25+ is 4096-byte pages |
| P15 public AI | Existing input/privacy/request limits preserved; reentry label mock passed; existing journal files unchanged | P16 screen reentry, compare/save without generating again |
| Personal-photo VTON | No private input uploaded, no paid resource | Authorized private GPU, weights/licenses, approved input and server privacy setup still required |

### Resume exactly here

1. Do **not** rerun successful CI/tests, rebuild identical APKs, clear data or repeat public AI generation. Confirm branch/worktree and retain all ignored evidence.
2. After user unlocks, verify S25+ `device` and unlocked status with explicit serial. The QA app/tests are already installed; do not reinstall/reset automatically.
3. Run private helper `fashion_app/build/p16-private/run_device_qa.py first` with the existing Python environment. It targets only `.preview.qa`, refuses old output, checks unlock, runs `P16DeviceQaTest#editManipulateAndSaveSyntheticFixtures`, and saves output/evidence. Optional `capture_qa.py` captures only when QA has foreground focus.
4. If first phase succeeds, stop **only** `com.jeiu.fashionproject.preview.qa`, then run helper argument `restore`. If it fails, preserve partial JSON/logs and distinguish a test harness issue from an application issue before retrying; never weaken assertions.
5. Only after device QA is satisfactory: fingerprint current Preview with `device_audit.py preupdate`, verify it is still the known 17701 package/APK, and perform compatible `adb -s <S25 serial> install -r <verified Preview APK>` without `-d`, uninstall or clear. Fingerprint with `device_audit.py after`, inspect actual installed metadata and minimal P16 UI. The old 17701 APK remains preserved.
6. Append real outcomes and final documentation commit SHA here. Documentation-only update does not require a new APK/CI run.

Private evidence: `fashion_app/build/p16-private/{baseline-build.json,s25-data-before.json,qa-install-preservation.json,verify_download.py,run_device_qa.py,capture_qa.py}`; verified APK metadata in `p16-release/LOCAL_VERIFICATION.json` and `p16-qa-release/LOCAL_VERIFICATION.json`; final XML/native reports in `p16-ci-final-reports`. None are committed. Low PC disk space (~400MB remaining) means do not start another local full build or emulator image download.

## Checkpoint and safety

- Work branch: `codex/fashionai-p16-device-stability-20261104`.
- Base: P15 `1e0f66cad2a24ab9140584860299c33a6cc06311`; installed baseline Preview 17701, execution SHA `14beaef75b8504c4ca985a0550c89a49cbf9aa79`.
- Existing branches, worktrees, backups, private P15 result and user data retained. No production server/DB changes, paid calls, new public inference, personal-image transmission, clear/uninstall or signing-key rotation.
- The sections below preserve implementation-checkpoint history. The latest verified state above supersedes their earlier pending statuses; test source remains distinct from actual execution evidence.

## Findings and changes

| Area | Evidence / cause | Change | Verification at checkpoint |
|---|---|---|---|
| Home weather | User clarified only card height/button wrapping; original timing unknown. Current 384dp home was normal with different live weather. | Production Home unchanged. Regression uses exact reported clouds/17°C/55% and long recommendation at 384dp, checks one line, >=64dp action width, <=180dp card height and nonoverlap. | Native-font Robolectric test passed, including Loading -> same long weather replacement. Legacy graphics returned 8px for the eight-character label; criteria not relaxed. |
| Body draft | Two regression tests failed before fix: saved-store hydration could overwrite active/restored draft or a new profile. | Saveable dirty/imported/ratio state; hydrate only untouched draft; successful explicit save adopts profile. One primary cm entry, secondary viewer/source/restore controls remain in advanced settings. | Both new draft tests passed in focused run. |
| Ruler | Existing callback and geometry code already used current state. | No speculative ruler math rewrite; seven parts and 0.5cm snap retained. | Existing editor tests passed; isolated device seven-part test prepared. |
| Fitting | A status label above weighted canvas changed canvas size and repeated automatic fit. Minimum width overrode maximum height for extreme aspect ratio. | Status inside scroll controls; unchanged fit preserves previous undo; reject unsupported aspect without replacing garment; selection outline and semantics. | Five new fitting/bounds tests passed before final notice-scroll change; final rerun passed. |
| Public AI result | On installed P15 reentry, prior completed result still had “new request” description. | Reentry distinguishes previous result; save/errors remain visible. No adapter, token, journal, request-limit or endpoint changes. | Mock no-second-submit regression passed; no new real inference. |
| Native alignment | Exact baseline APK/AAR hashes identify three bad ELF64 libraries (MediaPipe arm64, CameraX image processing arm64/x86_64). ZIP alignment already passed. | MediaPipe 0.10.14 -> 0.10.26.1; CameraX 1.3.4 -> 1.4.2. Graphics Path and ML Kit unchanged. No ABI removed. | Five candidate AAR ELF64 libraries pass LLVM LOAD check. Final APK gate pending. |

## Native evidence and limits

- Physical S25+ reports `PAGE_SIZE=4096`, 1440x3120, density 600, font scale 1.0 (~384dp).
- Baseline seven packaged ELF64 files: three failures above; graphics.path and xeno_native already aligned.
- `tools/native_alignment.py` independently checks every packaged arm64/x86_64 ELF LOAD (LLVM), rejects missing/malformed/duplicate evidence, and runs SDK `zipalign -c -P 16 -v 4`.
- Final APK gate must pass before upload. It explicitly reports `runtime16KbVerified=false`.
- Actual 16KB system execution is **unverified**. Existing local emulator images are not a confirmed 16KB runtime; do not infer runtime success from disappearing warning, 4KB S25 execution, or static checks.
- Official sources: [Android page sizes](https://developer.android.com/guide/practices/page-sizes), [MediaPipe v0.10.26](https://github.com/google-ai-edge/mediapipe/releases/tag/v0.10.26), [CameraX 1.4.2](https://developer.android.com/jetpack/androidx/releases/camera#1.4.2).
- Existing public certificate remains `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`; no private signing material in this document or repository.

## Test isolation and release safeguards

- Regular Preview stays `com.jeiu.fashionproject.preview` / FashionAI Preview.
- Opt-in QA uses `-PfashionPreview=true -PfashionDeviceQa=true`: `com.jeiu.fashionproject.preview.qa` / FashionAI QA P16. Preview network isolation retained; separate Android sandbox.
- QA artifact cannot pass Preview distribution verification. Both APKs must match the existing trusted signer. Instrumentation package/signature also checked.
- `P16DeviceQaTest` requires exact QA package, both build flags, and `-e p16DeviceQa true` before storage writes. Fixtures are generated synthetic PNGs only; no camera/gallery selection, external API or user storage access.
- Run `editManipulateAndSaveSyntheticFixtures` separately, then stop **only QA** and run `restoreSavedFixturesAfterSeparateProcessRestart`. Distinct PID and exact profile/placement restoration required.
- Prior partial/success evidence blocks automatic rerun. Preserve it before any explicitly chosen QA-only retry. Never run unrelated storage instrumentation on the user's Preview.
- CPU/Compose tests are not real-device rendering evidence. Native blank-image segmentation smoke is not camera capture, body accuracy or VTON generation.
- Manual photo-mode placement remains session state; do not claim process-restored photo-mode placement from avatar-mode QA evidence.

## Tests so far

- Original failures preserved privately: 2 draft, 2 fitting/bounds; later 47-test run had 45 pass and 2 pending (Home font fixture and prior-result label).
- Final focused rerun: 8 passed, 0 failed/skipped (Home 2, prior-result label 1, fitting 4, bounds 1). Save/error status visibility refinement is included in final CI.
- Release safety + native gate Python tests: 9 passed.
- Initial instrumentation compile found a missing closing parenthesis in the new test; corrected. Final instrumentation compile passed.
- Final full Android/Preview/backend/Lint/CI, signed APK, physical QA and update checks pending. Do not sum different build configurations into a single total.

## Modified files

- `.github/workflows/android-debug-apk.yml`: Preview regression filters, ELF/ZIP publication gate, separately signed/verified QA artifacts; preserve original Preview artifact before QA outputs replace build directory.
- `fashion_app/app/build.gradle.kts`: opt-in QA identity/flags; original Preview unchanged.
- `fashion_app/gradle/libs.versions.toml`: the two native dependency families only.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarSetupCard.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/PremiumAvatarEditor.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/OfficialDemoScreen.kt`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/{BodyMethodSelectionUiTest,HomeWeatherUiTest,P9EditorUiTest,P16AvatarDraftTest,P16DemoLabelTest,P16FittingUiTest,P16GarmentBoundsTest}.kt`
- `fashion_app/app/src/androidTest/java/com/jeiu/fashionproject/P16DeviceQaTest.kt`
- `tools/{apk_release.py,test_apk_release.py,native_alignment.py,test_native_alignment.py}`
- This document. Existing tests only navigate retained controls in their new advanced section; none deleted or weakened.

## Private evidence and next steps

### First CI and release-check correction

Run [41723404080](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41723404080), code `ac9ad3e0fdd04c858ba2172c5bbc8f4cf4ff9266`: Android **440/440**, Preview **204/204**, no failures/skips; backend **151 + 8 subtests**; five server-start configurations successful. Lint XML: 0 errors/fatal, 165 warnings. Both final Preview and QA APKs passed all ELF64/ZIP checks with all four original ABIs retained.

Publication correctly stopped at the final instrumentation verifier: Android test APK omits version fields, while the application verifier requires them. No APK artifact was published or installed. Added a separate instrumentation verifier checking exact test package, target package and trusted certificate; normal application version checks remain strict. Regression also rejects wrong target/signer/package and proves a versionless test APK still cannot pass normal application inspection. Tools tests now **10 passed**. A new CI run is required for this actual release-gate correction; this is not a documentation-only rebuild.

Local ignored folders under `fashion_app/build/`: `p16-private` (file fingerprints, minimal device screenshots), `p16-baseline` (original failing XML), `p16-native` (LLVM/AAR alignment and provenance). Do not commit them or user pictures.

1. Finish focused tests and instrumented-source compile; fix only evidenced failures.
2. Commit feature checkpoints, push P16 branch once, confirm all CI jobs and artifact gates.
3. Verify artifact package, signer, increasing code, source identity, SHA256 and ZIP integrity. Preserve P15 artifacts.
4. With authorized ADB: fingerprint existing Preview data, update compatible APK without deletion; compare fingerprints. Install separate QA package/test and execute guarded two-phase test. Record actual outcomes, not harness intent.
5. Record final CI/APK/device evidence here; documentation-only follow-up must not rebuild identical code.
6. Separately obtain a real 16KB runtime for camera/analysis/3D/fitting checks. Personal-photo AI still needs an authorized private GPU server, licensed weights, approved input and retention/auth configuration; public-example success is not personal-photo fitting.
