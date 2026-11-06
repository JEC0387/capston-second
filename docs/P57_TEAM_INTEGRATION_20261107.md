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

## Validation checkpoint (in progress)

- Backend focused authentication/data/migration: **119 passed + 8 subtests** (Windows local, DB disabled).
- Full Windows Backend: **599 passed / 35 environment failures / 37 skipped + 10 subtests**. All 35 failures are unchanged legacy Unix-only FASHN journal tests (`fcntl` unavailable); Linux verification pending. Do not sum repeated runs.
- Android compile, focused UI/data contracts, integrated APK and hash wire evidence pending at this source checkpoint. Initial integration compiler errors are preserved in separate local logs.
- Original installed QA 18610 / Preview 18605 untouched. No new device data preservation audit claimed.

## Release limits

Social OAuth provider operation, operational HTTPS deployment, reviewed personal-photo processing policy, formal Release signing and new-version device acceptance are not verified. P54 historical real E2E evidence remains valid only for that tested version/environment. Product-detail fidelity defects are not relabelled PASS. No additional Genlook credits used.

PR HEAD uses `[skip ci]` to prevent automatic Actions expenditure. Required PR checks are not removed or bypassed and may remain pending; this is a review candidate, not merge/release authorization.
