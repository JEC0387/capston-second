# P61 installation-test integration

Base: 6402c31a7ea16a32b5e3731827708355e45d34a8. Local branch only; no push, device installation, production mutations, OAuth login, photo upload or Genlook generation.

## Implemented
- ManualBodyEntryCard opens AvatarSetupCard and the existing centimeter editor instead of an empty legacy avatar. New height/weight fields are blank; saved measurements and explicit/saved weight create a proposal. Comparison approval changes the draft only; saving is separate.
- BodyAnalysisScreen hydrates and displays original manual measurements separately from photo classifications. Existing BodyDimensions/MeasuredSizeCard contracts continue using measured circumferences, never photo widths converted to circumference.
- PremiumAvatarEditor retains visual adjustments separately. Only freshly typed values marked as measured can propose a single-field writeback, with a second confirmation, validation and unchanged-record check. Cancellation leaves original measurements unchanged.
- Primary AI action opens ServerOutfitVtonScreen with frozen selected inputs. Local/on-device/external experimental entry points remain under advanced controls. Manual placement and export remain available separately.
- Server/DB/account/provider readiness are reported separately. Installtest background authentication cannot create a production account. Existing approved credentials still use the original Bearer path. No token copying or security bypass.

## Read-only server evidence
2026-11-09: health 200; database health 200, ready=true, syncReady=true, authProtocol=2. Anonymous auth/status and outfit-vton/config 401. Public providers endpoint reports Google/Kakao true. This proves endpoint/database readiness only, not an authenticated installation or provider credit/key validity. AUTH_REGISTRATION_ENABLED cannot be inferred from these GETs. No registration POST was sent.

## OAuth handoff (public identifiers only)
Package: com.jeiu.fashionproject.installtest
Signer SHA-1: 5683cdee55438bea606b13cb7fa8250a28eeb25b
Signer SHA-256: f4739f9bd1ececba78da1e5b61ec9545edcffc278723c37dd381cdca76f744b3
App callback: fashionfit-installtest://login
Kakao current server redirect: https://fashion-backend-production-aba0.up.railway.app/api/auth/kakao/callback (PUBLIC_BASE_URL may override; operator must verify).
The current backend browser-return contract hardcodes fashionai://login and com.jeiu.fashionproject. Merely registering a SHA or enabling the installtest button cannot fix this. A separately approved server callback routing design must bind the initiating app/account/state to an allowlisted callback and preserve the primary package callback. Do not accept arbitrary redirect URLs or weaken state binding.
Google requires an Android OAuth client for the package/SHA-1 and a compatible web client/audience on Android and server. Console registration and test-user approval are not verified. Kakao currently uses server REST login, not the native Android SDK; the server HTTPS callback registration and app return route must both be correct. Native SDK key-hash registration alone cannot repair this browser flow.
Sources:
- https://developer.android.com/identity/authorization
- https://developers.kakao.com/docs/en/kakaologin/rest-api
- https://developers.kakao.com/docs/en/kakaologin/prerequisite

## External responsibilities / acceptance
- Server operator: provide an approved, legitimate account provisioning/recovery procedure for this independent sandbox; verify registration policy and Genlook deployment/privacy/budget flags without disclosing secrets.
- OAuth console owners: register this certificate/package and review the callback contract before enabling login. Google/Kakao remain BLOCKED.
- Device owner: separately approve the new version installation. This task found zero ADB devices; physical UI, update data comparison and actual generation are NOT TESTED.
- Real Genlook generation, credit consumption and production changes are prohibited in P61. Existing historical success is not a new P61 result.

The candidate is debug-signed, private installtest software, not a formal Release. Existing package, key, database schemas, private credentials, old APKs and all device data are preserved. APK audit and current test results are included in the separate P61 delivery directory.

Current P61 installtest focused run: 151 passed, 0 failed, 0 skipped. Real Compose/Robolectric plus synthetic image and storage fixtures; not device or Genlook generation evidence. Initial obsolete legacy-button expectation and a stale compiled test-selector attempt are preserved in local first-attempt logs; final source was recompiled before the passing run.

Normal-build auth regression: 24 final cases passed, 0 failed/skipped (23 initially passed; the fixed-position primary button test was corrected to assert visibility instead of scroll, then both entry cases passed). No application security assertion was relaxed. Original failed XML and final XML are retained locally. An operator-approved account must be dedicated to this sandbox; do not reuse another installed app's token/recovery code.
