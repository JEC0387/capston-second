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

Integrated QA APK / final QA consent tests / Lint / signer verification follow this source checkpoint. No new device installation or data audit has occurred. Original installed QA 18610 and Preview 18605 remain untouched.

## Release limits

Social OAuth provider operation, operational HTTPS deployment, reviewed personal-photo processing policy, formal Release signing and new-version device acceptance are not verified. P54 historical real E2E evidence remains valid only for that tested version/environment. Product-detail fidelity defects are not relabelled PASS. No additional Genlook credits used.

PR HEAD uses `[skip ci]` to prevent automatic Actions expenditure. Required PR checks are not removed or bypassed and may remain pending; this is a review candidate, not merge/release authorization.
