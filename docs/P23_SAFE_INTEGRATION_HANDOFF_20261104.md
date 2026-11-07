# P23 — Safe guest entry and data preservation

## Latest verified result — 2026-11-05 KST

Execution SHA: `71f57804250260975e803398616f4f25d6f66bc2`. Implementation and tests are committed and pushed on the existing branch. This final handoff update is documentation-only; do not rebuild the same application for it.

- [CI #86](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41859832925): focused authentication/preservation tests, full Android tests, Preview regression, Backend, five server-start environments, Lint, instrumented-test compilation, Preview/QA build, package/signature/version/source gates and ZIP/ELF static checks passed.
- Backend: **151 passed plus 8 subtests**; release/native/retention policy tests: **13 passed**. Final Android/Preview XML could not be downloaded; do not substitute prior-run counts or add repeated runs together. Device instrumentation was compiled, not executed.
- **GitHub publication is blocked**: both APK and report uploads failed with `Artifact storage quota has been hit`. GitHub says usage is recalculated every 6–12 hours. The final read still reports workflow `in_progress`, conclusion `null`, although validation and cleanup steps are complete; its artifacts endpoint reports zero artifacts. This is not an overall green workflow or a downloadable GitHub APK. No protected #79/#80/#81 artifact was deleted, and no workflow was cancelled.
- A local fallback was built from the same clean execution SHA and signed with the existing trusted Preview key. All final local candidate checks and the fresh monotonic-version floor passed. It was **not installed** and is **not a GitHub artifact**; `distributionVerified:false` deliberately remains in its build information.

### Verified local candidate

- File: `fashion_app/build/p23-release/fashionai-1.1.86.1-preview-vc18601-71f57804.apk` (relative to this worktree).
- Package: `com.jeiu.fashionproject.preview`; version `1.1.86.1-preview`, code **18601** (greater than 18101; checked floor 18600).
- SHA-256: `e8e7bebb1063d9b013b6a27caeb4f2fd5d30c31e14018c597ba01ca4bfc89435`.
- Certificate SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9` (existing trusted signer).
- Embedded branch/build/source identity, APK CRC, P22/P23 DEX markers, package and signature verified. Actual signing secret bytes were checked against APK contents without printing them. Temporary private signing material was removed; the original protected backup remains untouched.
- Current APK passed SDK ZIP 16 KB alignment. All 18 packaged native files and their file set exactly match the pinned P22 reference APK; nine ELF64 libraries reuse that byte-identical LLVM audit. This is static evidence, not a new local LLVM run or actual 16 KB device execution.
- Evidence alongside the APK: `BUILD_INFO.json`, `BUILD_INFO.txt`, `SHA256SUMS.txt`, `LOCAL_VERIFICATION.json`, `VERSION_FLOOR.json`, `native-alignment.json`. Final `localCandidateChecksPassed`, `versionFloorVerified` and native static checks are true.
- The unavailable CI-built APK has a different hash (`9ac1d95308b2de6b3fb8daf659a11508d9c8653663b210249530285dd1487ecd`); do not apply that hash to the local file.

Local candidate attempt 01 failed before compilation because an empty signing environment variable was interpreted as a path. Attempt 02 failed during DEX transformation with Windows system-resource exhaustion. Both logs were preserved and neither restored the signing key. Attempt 03 passed using one worker, two active JVM processors and a 1.5 GB SerialGC heap; it reused completed tasks without clearing caches. Final C: free space was about 18.8 GiB. No user files, SDK, AVD or older APK were removed.

The earlier combined-suite failure was fixed solely by isolating the synthetic DataAuth lifecycle in the test fixture. Final Linux CI passed both auth classes together and the full suites; the earlier Windows loopback-TLS limitation is not claimed resolved. Synthetic records/queue preservation passed, but phone data was not accessed or freshly hash-audited.

Next: retain this local candidate and reports; after GitHub storage accounting permits uploads, publish only through the existing trusted release gates. Do not lower version checks or delete protected artifacts to bypass the block. Real OAuth/operational recovery and all device/AI limitations below remain outstanding.

## Scope and implementation

- Existing branch: `codex/fashionai-p17-camera-personal-vton-20261104`; baseline execution SHA `c14fca05ce60a148be08e5e47499c91b005f3485`. No new branch/worktree or forced reset.
- My Page now opens an explicit account/guest guide. The previous “logout” only navigated to onboarding; the replacement accurately separates navigation from authentication and data deletion. A fabricated fallback email is removed.
- Google/Kakao/Facebook/Apple remain visibly unconfigured and disabled. No OAuth exchange or account adoption was introduced. Existing authenticated backup is a separate explicit entry.
- Onboarding retains its existing style-save action and adds entry without committing the style draft. Account cancellation/guest entry does not clear local records or flush the queue.
- Backup status no longer equates zero pending requests with completed server backup. Empty recovery explains that local records remain and cross-account synchronization is paused.
- DataAuth, DbSync, BackupRestore transaction logic, restore journals, recovery-code validation and Preview personal-network isolation remain unchanged. Product/style integration, photo/avatar analysis selection, size comparison and fitting routes are retained.

## Selective team review

Reference: `JEC0387/capston-second` at `411d008ebf542ec04c4966936303a686b4f784e5`.

Only the provider-list and guest-entry concepts were adapted. No team file, authentication store, server API or recommendation engine was wholesale copied.

Excluded `AuthStore.acceptLogin` behavior that clears local data on missing/same-code server records, and `signOut` behavior that deletes local data even after failed/timed-out synchronization. Its recursive preferences/photo/fitting-cache deletion path was not imported. A real social login/logout implementation requires a compatible authenticated ownership and recovery contract plus configured providers; it remains unavailable rather than reporting simulated success.

## Earlier execution history (preserved)

The entries below record intermediate failures; the latest verified result above supersedes their pending state without erasing the failure evidence.

During validation, the same remote branch advanced through `26860c1` to `f7fb4b21219085777692901d245028dcd44c5131` (AI wand wording and binding results to original inputs). Those non-overlapping commits were reviewed and fast-forwarded without discarding local P23 changes. Existing CI #83 was left running. See the separate `P23_AI_WAND_FLOW_20261104.md`; its actual-inference limitations still apply.

Local focused run01 stopped after the reused Gradle daemon reported inaccessible cache/registry directories; only the identified client and its daemon were stopped, without deleting caches. Run02 failed before tests because PowerShell split an unquoted Kotlin compiler property. Run03 uses a quoted property, a single-use daemon and in-process compilation. Prior logs are retained under ignored `fashion_app/build/p23-private/`; no environment failure counts as a passed test.

New synthetic tests exercise guest draft preservation, actual My Page entry, disabled providers, explicit callback separation, honest backup status, and authentication/recovery errors. Loopback TLS tests cover invalid input, 401/500, HTTP timeout, cancellation after accepted request, an empty different-account snapshot, and a stale old-token rejection. They compare nine synthetic stores, a synthetic file, the pending queue and absence of a replacement restore journal. Preview follows a separate zero-private-request branch. These are not real OAuth-provider tests or a fresh audit of device data.

Run03 completed 56 tests: 54 passed, 2 failed. All six new UI tests passed. Existing `DataAuthRecoverySafetyTest` and new `P23DataPreservationTest` both failed before HTTP reached the loopback TLS server; the exact cause is being diagnosed with run04 TLS tracing (only those two classes, no app/security-rule changes). Python release/native/retention tests: 13 passed. No failed test is counted as success.

Subsequent evidence: run04 repeated the two Windows loopback failures; TLS tracing did not expose the swallowed client exception. Private direct-TLS diagnostics run05/run06 failed respectively during init-script configuration and discovery (no tests found), so they are not successful tests or evidence of an app defect. Further Windows diagnostic iteration was stopped in favor of the supported Linux CI path.

CI #84 (`41853553236`, `addcf8e`) passed 480/481 Android tests; the new preservation test failed. CI #85 (`41856405800`, `b728cf3`) passed P23 independently but again failed the combined 481-test suite. Full exception logging confirmed the synthetic initial account was null: the preceding recovery test leaves DataAuth's process-lifetime singleton initialized, so the new test's synthetic vault is not loaded. The fix is confined to a test fixture that provides a fresh synthetic application lifetime and restores the previous singleton state afterward. Production initialization, authorization and assertions remain unchanged. The focused CI group now includes both recovery test classes before APK assembly.

Incoming CI #83 (`41848653118`, source `f7fb4b2`) completed its validation/build gates but failed APK/report uploads: GitHub reports artifact storage quota exceeded and usage recalculation every 6–12 hours. This is not P23 guest/data-preservation validation. No protected artifacts are deleted and upload failures are not hidden.

Three actual parallel agents handled UI development, safety tests and CI policy. The coordinator owns integration, Gradle and Git. No phone, ADB, camera, gallery, production database, Railway deployment, external image upload or paid service is used.

## Artifact policy

Future Preview APK and release metadata: 30 days (covering November 16). QA APK and instrumentation: 3 days. Test/Lint reports: 7 days. Existing success, trusted certificate, monotonic version, source SHA and native alignment gates remain in place. Runs #79/#80/#81 are not deleted or retroactively altered.

P22 CI run `41840132897` completed all seven jobs successfully, with Android 472 / Preview 227, backend 151 plus 8 subtests, and Preview 18101 validated locally. Its old pre-CI pending text does not supersede the actual evidence in ignored `fashion_app/build/p22-release/LOCAL_VERIFICATION.json`. P23 must use its own final CI results.

## Files in this integration checkpoint

- `.github/workflows/android-debug-apk.yml`: new-artifact retention and P23 Preview/release-policy tests.
- `tools/test_artifact_retention.py`: retention and existing distribution-gate checks.
- `fashion_app/gradle/test-runtime.init.gradle.kts`: full test exception details for diagnosis when report upload is unavailable.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/AppNavigation.kt`: honest start-screen callback name only.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AccountAccessDialog.kt`: unconfigured providers, guest/backup/start separation.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BackupRestoreDialog.kt`: honest pending/empty-recovery status.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MyPageScreen.kt`: account guide and no fabricated email.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/OnboardingScreen.kt`: non-mutating guest entry.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P23AccountAccessUiTest.kt`.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P23BackupStatusUiTest.kt`.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P23DataPreservationTest.kt`.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P23GuestEntryUiTest.kt`.
- This handoff. Separately preserved incoming AI wand changes retain their original commits/document.

## Remaining external validation

- No installation is authorized for this task; installed Preview/QA 18001 remain untouched. New APK verification is offline and does not prove device update/data preservation.
- Real OAuth, operational authenticated server synchronization/recovery, valid full-body analysis accuracy, personal-photo VTON and actual 16 KB runtime remain separate unverified items.
- No GPU search or download, no repeated P19/P22 implementation, and no claim of complete team integration.
