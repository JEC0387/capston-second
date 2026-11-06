# P16 device stability — 2026-11-04

## Latest verified state (S25+ QA and update completed)

**Code/CI/APK checks and both isolated S25+ instrumentation phases passed. Existing Preview was updated without deletion. Real 16KB runtime and real-camera capture remain unverified; model joint visual quality still needs follow-up.**

- Execution commit: `94e4e3a7b5b0a5017f934e50ab840260b067fba7`.
- [Final CI 41726982899](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899): all **7 jobs successful**.
- Android **440 passed**, Preview **204 passed**; each 0 failed/errors/skipped. Backend **151 passed + 8 subtests**, 7 warnings. Release/native safety **10 passed**. Lint **0 errors, 165 warnings, 2 hints**.
- After the user unlocked S25+, physical instrumentation ran: `editManipulateAndSaveSyntheticFixtures` **1/1 passed (7.176s)**; QA-only process stop followed by `restoreSavedFixturesAfterSeparateProcessRestart` **1/1 passed (1.365s)**. Evidence verifies distinct process IDs and exact seven-part profile/manual placement restoration. No lock bypass or device-setting changes.
- Preview `com.jeiu.fashionproject.preview`, `1.1.79.1-preview`, **17901**; [APK artifact 13276820721](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899/artifacts/13276820721).
- APK SHA-256: `95060779ed1af0804762b1c039da2e28d51ea9ba01b53a44b90148b0563e0119`.
- Local APK: `C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\fashion_app\build\p16-release\fashionai-1.1.79.1-preview-vc17901-94e4e3a7.apk`.
- Package/version, existing trusted certificate, clean source SHA, embedded identity, SHA256SUMS and full ZIP CRC independently verified locally. CI native reports match those exact downloaded APK hashes: **9/9 ELF64 libraries pass, ZIP passes, all four ABIs retained**, for both Preview and QA.
- **FashionAI QA P16** (`com.jeiu.fashionproject.preview.qa`) and its `.test` package are separate from user data. Five generated synthetic garment images and a synthetic profile were used only there. No external AI requests, real photos or user-record edits.
- Existing Preview successfully updated **17701 -> 17901** with `adb install -r`, after matching installed P15 APK hash to the known signer and verifying the increased candidate version. Installed P16 APK hash equals the verified artifact. Cold launch succeeded; body entry and actual OpenGL mannequin/ruler screen were inspected without editing/saving user measurements. No uninstall/clear/downgrade.
- Immediately before/after update: all **22 existing Preview data file hashes unchanged**, 0 missing/changed, 0 new files. After normal Home/UI navigation, 19 remained identical; `weather_alert.xml`, `recommendation_log.xml` and `files/profileInstalled` changed. Existing Home code records displayed recommendations and runtime weather/profile state; these files were not restored/overwritten by test fixtures. There is no before-content snapshot to independently prove entry-by-entry recommendation-log preservation. Wardrobe, body profile, placements, queue/journal and other unchanged files retain their hashes. Original app and old backups/APKs untouched.
- QA APK SHA-256: `c12e6e473e6b47e6c1e509491eaca390ecf4561a5104efe8f707702739504de1`; [separate QA artifact 13276252269](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899/artifacts/13276252269). It is not a Preview update.

| Required area | Final verified scope | Remaining physical check |
|---|---|---|
| Home/body readability | Exact long weather fixture at 384dp passed; no Home production change. Actual P16 Home action is horizontal (256x69 physical px); primary body entry shown | Historical onset unknown; exact historical weather text was injected only in the automated fixture |
| Seven parts / 0.5cm ruler | S25+ cancel preserves profile; direct input/drag/0.5cm snap, seven independent values and approved save passed | Numeric/model behavior is not physical measurement accuracy |
| Garment selection/pan/scale/rotation | S25+ synthetic top/bottom selection, single drag, multitouch scale/rotation, independent garment state passed | Broad real-photo/device coverage remains future work |
| Auto-fit/undo | S25+ repeated/no-op auto-fit and exact undo passed; opaque/tall failures preserve previous garment; asymmetric alpha-margin case passed | Flat overlay alignment is not 3D cloth simulation or AI image generation |
| Save/process restart | Both real-device phases passed with different QA PIDs and exact persisted profile/placements | Scope is synthetic avatar-mode QA; not photo-mode process restoration |
| Camera/photo analysis | Full automated regression and actual bundled MediaPipe inference on a generated blank bitmap passed; invalid body rejected | Camera capture/real-photo accuracy not tested |
| 16KB | Final ELF/ZIP checks passed | Real 16KB runtime unavailable; S25+ is 4096-byte pages |
| P15 public AI | Existing input/privacy/request limits preserved; reentry label mock passed; existing journal files unchanged | P16 screen reentry, compare/save without generating again |
| Personal-photo VTON | No private input uploaded, no paid resource | Authorized private GPU, weights/licenses, approved input and server privacy setup still required |

### Completed resumption and remaining actions

1. Do **not** rerun successful CI/tests, rebuild identical APKs, clear data or repeat public AI generation. Confirm branch/worktree and retain all ignored evidence.
2. QA installation, `run_device_qa.py first`, QA-only stop, `restore`, Preview preupdate fingerprints, compatible update and after fingerprints are **completed**. Helpers refuse overwriting existing output; do not automatically repeat them or replace their evidence.
3. Physical UI proof: body primary action visible; Home action horizontal; native mannequin/ruler displayed. A private screenshot shows rough wrist/pelvis transitions. Geometry/shaders were not changed in P16, and visual perfection is **not** claimed. Save button's lower portion requires scrolling at current viewport; scrolling and save were exercised in QA.
4. Remaining: real 16KB environment, actual camera capture/photo regression, deeper joint visual review, and P15 result compare/save reentry on P16 without a new generation. Personal-photo VTON still needs the authorized private GPU/server path.
5. Document-only handoff updates do not need a new APK or repeated CI. The execution SHA remains `94e4e3a7b5b0a5017f934e50ab840260b067fba7`.

Private evidence: `fashion_app/build/p16-private/{baseline-build.json,s25-data-before.json,qa-install-preservation.json,verify_download.py,run_device_qa.py,capture_qa.py}`; verified APK metadata in `p16-release/LOCAL_VERIFICATION.json` and `p16-qa-release/LOCAL_VERIFICATION.json`; final XML/native reports in `p16-ci-final-reports`. None are committed. Low PC disk space (~400MB remaining) means do not start another local full build or emulator image download.

Completed device evidence additionally includes `qa-94e4e3a7-{first,restore}.{txt,json}`, `s25-data-{preupdate,after}.json`, `s25-data-comparison.json`, `installed-p16.json`, `body-entry-after-update.json`, `home-action-after-update.json`, `s25-after-ui-preservation.json`, and private `native-avatar-after-update.png`. Screenshot/profile values must not enter GitHub or APK artifacts.

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
