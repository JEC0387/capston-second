# P57 team integration candidate

## Source boundaries

- Team base: `JEC0387/capston-second` main `411d008ebf542ec04c4966936303a686b4f784e5`, fetched 2026-11-07.
- Personal parent: `9ac1b229f8f1169175526b7fd42fe1d60ad2343f` (P56 final delivery).
- Candidate: `codex/fashionai-final-integration-20261107`. Separate worktree; both unrelated histories retained by a merge commit. Original worktree and branches preserved.
- No team main update, final merge, device install, user-data change, external generation or Actions execution is authorized by this candidate report.

## Integrated behavior

| Area | Resolution |
| --- | --- |
| Login / local data | Team provider chooser and guest entry retained; legacy UUID is never treated as a Bearer credential. Cancel/error/logout do not clear wardrobe, photos, DataAuth or sync queues. Social providers explicitly unavailable until a secure identity bridge is configured and tested. |
| Style / shop | Team eight-style preference taxonomy, optional gender filtering, shop filters/search and wardrobe-based recommendation integrated. Legacy raw labels/scores preserved; old search responses cannot overwrite a newer query. Real product IDs and catalog limitations retained. |
| Wardrobe photos | Team photo tidy action connected with explicit confirmation; originals retained, prepare-before-commit, cancellation and concurrent-edit checks. No automatic model download. |
| Body / fitting | Personal photo/avatar choice, size comparison, measured-width semantics, manual measurements and avatar fitting paths retained. P54 Genlook request freezing, authentication, credit limits, cancellation, result receipts and exact PNG saving retained. |
| Legacy FASHN entry | Unused team direct-call pipeline has no consent/journal/usage reservation. It now rejects before reading a photo; active screens keep the authenticated existing controllers. Team preprocessing helpers retained for review. |
| DB | Team original 005 preserved; auth 006/007 followups. Explicit personal lineage keeps original 001–006 hashes and adds 007 user-logins. Wrong lineage/checksum/unknown applied history remains blocked; no operating DB migration. |

## Mandatory P56 hash correction

The Android server controller accidentally inherited the on-device engine's 1024 maximum side. The approved 1024×1353 person became 512×676; products 930×1200, 850×1200 and 1200×704 became half-size derivatives. Server operator references described the original pixels. This was distinct from normal ICC/EXIF/PNG byte changes.

The server now advertises bounded `max_input_side=2048`; the Android server-only controller freezes that contract in both readiness and preparation. The on-device default remains 1024. A prepared job with another size contract is rejected. Request-body SHA, receipt/model binding and exact provider reference SHA checks remain enforced. Metadata stripping and full-resolution detail-source hashes remain distinct.

`P57ServerImageContractTest` creates synthetic markers at the original dimensions through the actual Android decoder, orientation transform and request encoder. `backend/verify_android_outfit_wire.py` checks the emitted bytes through FastAPI parsing and strict product references. It also changes a product pixel and requires rejection. These are offline integration evidence, not new AI generation or S25+ evidence.

## Local validation before integrated APK

| Run | Actual result | Scope / limitation |
| --- | --- | --- |
| Backend focused 03 | 119 passed + 8 subtests | Authentication, preservation, migration contracts; local only |
| Backend all 02 (Windows) | 599 passed, 35 environment failures, 37 skipped + 10 subtests | 35 failures require Unix fcntl/fsync |
| Ubuntu legacy tryon run01 | 39 passed, no failures/skips | Resolves all 35 Unix-only Windows failures; four overlap, do not sum |
| Backend consent 01 | 30 passed + 8 subtests | Authenticated HTTPS, purpose consent, input guards, sanitized errors |
| Android focused 01 (QA) | 232 executed: 230 passed, 2 failed | Recovery test wrongly included in QA; new PNG metadata expectation wrong |
| Android production 02 | 15 executed: 13 passed, 2 failed | Rotation/reopen and closet/consent UI passed; two localhost-only test certificates rejected numeric loopback |
| Android recovery 03 | 2 passed, no failures/skips | Correct loopback SAN, real TLS probe/request arrival, lost-response recovery and consent upload; no TLS checks disabled |
| Android-to-FastAPI wire | PASS | Actual Android decoder/JSON bytes, independent RGBA/dimensions, strict references and altered-pixel rejection; synthetic only |
| Release tools | 17 passed | Package/signature, native alignment tooling, retention and CI policy |

The test-only EXIF correction now independently checks PNG chunks/CRC, absence of eXIf, exact rotated pixels and full-pixel equality after reopening. DataAuth runtime code is unchanged. The failed runs remain preserved; no repeated-run totals are presented as unique tests.

The final source includes one-use photo/destination/purpose-bound consent, Bearer authentication and HTTPS before reading/uploading an image. Photo selection is local only. QA/Preview intentionally block production classification uploads. The classifier's successful synthetic HTTP contract is not evidence of actual model accuracy.

The approved Ubuntu isolated venv plus downloaded wheels/test files used **53,961,069 bytes (51.46 MiB)**, below 150 MB. No global Python/other WSL work/device data changed. Windows skips include optional model libraries/CUDA, three real PostgreSQL tests (service not configured), and symlink capability; no real PostgreSQL migration is claimed.

Final QA consent tests: **6 passed / 0 failed / 0 skipped** on executable source `11d15476580b83680f1ca22d714756424e9b20ff`. No new device installation or data audit has occurred. Original installed QA 18610 and Preview 18605 remain untouched.

## Release limits

Social OAuth provider operation, operational HTTPS deployment, reviewed personal-photo processing policy, formal Release signing and new-version device acceptance are not verified. P54 historical real E2E evidence remains valid only for that tested version/environment. Product-detail fidelity defects are not relabelled PASS. No additional Genlook credits used.

PR HEAD uses `[skip ci]` to prevent automatic Actions expenditure. Required PR checks are not removed or bypassed and may remain pending; this is a review candidate, not merge/release authorization.

## Verified integrated QA delivery

- Executable source: `11d15476580b83680f1ca22d714756424e9b20ff` (clean source identity embedded in APK).
- APK: `fashionai-1.1.86.11-qa-vc18611-11d15476.apk`, 149,174,509 bytes.
- SHA-256: `03a79b325363f43137f1139a9180aa6d1f51a2db2bc44bcad9f18d2154f616cc`.
- Package: `com.jeiu.fashionproject.preview.qa`; version **18611 / 1.1.86.11-qa**.
- Trusted certificate: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- Local delivery: workspace `deliveries/P57-INTEGRATED-QA-18611-20261107-0446-KST/`; Nine files including the APK and verification records copied without moving/overwriting prior deliveries. Each copied file's hash matched its source.
- Kotlin compilation, APK assembly and Lint PASS. Lint **0 Error/Fatal, 167 Warning, 3 Hint**. Initial 768 MiB D8 heap exhaustion is preserved; packaging resumed with a fresh single-worker 1280 MiB JVM, reusing completed compile/tests. No global setting changes.
- Post-build resource guard correctly delayed signing while the Gradle daemon exited. A later read-only check found no build process and 2095 MiB available RAM. Same raw APK/source/CRC/Lint were checked and signed without rebuilding.
- Package/certificate/version match the preserved QA 18610 APK file. This is a static update-compatibility check, not a fresh device preflight.
- ZIP CRC, duplicate entries, embedded source identity, compiled hash/consent/team feature markers, signing-secret exclusion, exact native-library bytes versus the previous LLVM audit, and fresh 16 KiB ZIP alignment PASS. Actual 16 KiB runtime NOT TESTED.
- Executable-checkpoint outgoing Git objects relative to team main: 1,016 unique blobs / 11,055,050 bytes checked; configured Genlook secret, private-key/token patterns and forbidden artifacts: **0 findings**. Secret values were never output.

## Acceptance boundaries

The integrated source and QA APK are real artifacts, not a completed production release. Team social login remains BLOCKED until the authenticated account-link bridge and provider configuration are implemented/reviewed; UUID/X-Device-Id adoption is deliberately rejected. Guest/local wardrobe use remains available. QA/Preview server clothing classification remains intentionally blocked; local processing/manual registration remains available.

P57 has **no new installation, personal-data audit, Genlook call or AI credit consumption**. New APK device E2E and preservation are NOT TESTED pending version-specific approval and fresh preflight. Real PostgreSQL migration, operational HTTPS/privacy controls, formal Release signing and product-fidelity acceptance remain unverified. Production decision: **NO-GO**. Required PR checks are preserved; `[skip ci]` avoids unapproved Actions usage and does not authorize merging.