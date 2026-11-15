# P63 — Release candidate acceptance, 2026-11-10

Base: P62 `af3e48e48092078ab596c28db436223967d2a5fd`.
Branch: `codex/fashionfit-p63-release-20261110`. PR #23 and main are not merged.

## Implemented changes

- `BodyAvatarViewport` exposes a lifecycle-bound capture operation. GLES draws and reads its own framebuffer on the GL thread; no screenshot of a black `SurfaceView` is substituted. The adapter checks current geometry, front camera, surface dimensions, opacity, blank frames and a five-second deadline. Cancellation releases owned bitmaps.
- 3D export composites local garment pixels using the existing fitting renderer, slot order, placement, scale, rotation and opacity. It does not generate new clothing or modify saved body/wardrobe/placement records. Export is disabled during model inspection. A changed input invalidates the result; document-picker bytes are frozen separately. PNG save and reopen reuse existing integrity-checked storage.
- User-facing labels distinguish **3D 착장 이미지 저장** from **AI로 입어보기**. 3D-to-Genlook remains unsupported/unverified; no avatar is automatically uploaded.
- Formal Release hides URL editing, DB diagnostics and development status controls. Existing installtest/QA code and authentication safeguards remain intact.
- Genlook HTTP failures retain their actual status and only an allowlisted HTTPS reason. Arbitrary error bodies, signed URLs, tokens and provider messages are not displayed or persisted. 401 authentication, 403 transport/consent, 422 input and 503 engine readiness remain separate. No retry or paid request was added.
- Selectively integrated Docker proxy configuration and tests from `eeb069cb43a5a49fa686fb48a9b076b91ccca446`; no Android overwrite or workflow change. Railway wildcard trust requires operator verification of ingress isolation; it is not proof that every direct/private peer is trustworthy.

## Read-only server and device observations

- Railway `/health`: HTTP 200, `alive`.
- `/api/auth/providers`: HTTP 200, Google/Kakao true. This is not a successful OAuth login or proof of certificate registration.
- Unauthenticated `/api/outfit-vton/config`: HTTP 401 `authentication_required`, not 404. A valid authorized session is still needed to independently verify proxy scheme handling and Genlook readiness in production. No token was copied or account created.
- Formal Release has `INSTALL_TEST_BUILD=false`, `PREVIEW_BUILD=false`, package `com.jeiu.fashionproject`, existing production URL and configured public Google web client ID. Installtest's intentional Google/Kakao block is not removed or bypassed.
- Existing connected primary app is version 1.0 / code 1, certificate SHA256 `3d540a7a4c5a9d9e5e9dfa81286337d3c10a74a37c1ddeb5f6e916610a26f3b3`. It cannot be updated by the dedicated Release certificate `8741fa599ce7db7088ee452121cf7bfbc5cd45b324e5ce076812fd42e0712ba3`. No uninstall, clear-data or overwrite was attempted.
- Preview 18605, QA 18611 and installtest 18618 were observed. ADB disconnected before a new file-hash inventory could be collected. Device data equality is **NOT TESTED**, not a claimed full preservation pass. No device data writes were performed.

## Validation boundaries

Final local Android run: **771 tests / 763 PASS / 0 FAIL / 8 SKIP**, `:app:testDebugUnitTest`, 5m49s, after the fixes below. The eight skips are six installtest-only cases in this formal-package build and two externally enabled loopback/live-server cases. The focused run passed **47/47** before this final whole-suite run. Backend focused validation passed **90 tests** (12 deployment/proxy + 78 auth/social/consent/outfit API/progress). No GitHub Actions run was started. Earlier failing logs/XML are retained locally, not relabeled as passes. Release packaging, Lint, signing and artifact hashes are recorded in the separate local delivery manifest after this source commit.

A completed export could resume in the same frame as a placement edit; the stale result is now both hidden and discarded. Large text uses full-width stacked actions with centered, full-width text measurement. The 320dp / 2x font test uses NATIVE text rendering; LEGACY's placeholder glyph widths are not physical layout evidence. A wrap-content label also reported intrinsic-width overflow despite space remaining in its parent; labels now receive the actual available width rather than rounding their intrinsic width. The no-overflow assertion remains intact. The full suite also exposed an intermittent photo-drop wait; the same drag scenarios passed in isolation. That fixture now drains Main before observing the asynchronously decoded selection, with the same ten-second limit and unchanged drag/selection/undo assertions. No photo-drop runtime logic changed.

Existing body-analysis fixtures were corrected to supply an actual user-approved saved profile instead of assuming the removed fabricated 65kg default; the first-import path is tested separately. The pure demo contract test now tears down its scope on the UI thread without requiring a Compose root it never created. No request, storage or quality assertion was relaxed.

The synthetic framebuffer tests verify RGBA conversion, vertical orientation, bounds, composition, PNG encoding and byte-exact save/reopen. They do not exercise a physical GPU/driver and are not real model/cloth quality evidence. Compose tests separately verify disabled export, duplicate clicks, cancellation and stale-result rejection.

The [official Genlook create contract](https://genlook.app/docs/tryon-api/endpoints/create-try-on), checked on 2026-11-10, accepts `person.image` and product reference images. This schema is not evidence that our rendered mannequin is a supported, quality-validated person input. No 3D-to-Genlook request was made. The existing photo path retains its two-stage clothing/shoes contract and one-day output retention; no provider request schema or credit budget was changed.

P54's historical real S25+ Genlook E2E PASS and product-detail quality FAIL are preserved as history, not claimed as P63 execution. No new Genlook generation, credit expenditure, image upload, production deployment or DB migration occurred.

## Remaining release blockers and owners

1. **Device owner / QA:** a device without a conflicting primary package (or the original signing key's legitimate owner) is needed for non-destructive formal Release installation. Verify rendered 3D + garments, PNG reopening, orientation, background/foreground, actual login and account isolation. Do not delete the existing app to bypass signing.
2. **Railway administrator:** confirm deployed source, start command, trusted proxy ingress, authorized config readiness, persistent journals, consent/retention and per-user/request budget limits. `RAILWAY_PROJECT_ID` alone does not prove safe wildcard proxy trust. Do not expose PostgreSQL/direct container ports.
3. **OAuth administrators:** verify formal package and Release certificate in Google Android credentials and matching web audience. Kakao currently uses the server/browser flow, not a newly introduced native SDK: verify `KAKAO_REST_API_KEY`, optional enabled client secret, `PUBLIC_BASE_URL` and the registered HTTPS `/api/auth/kakao/callback` URI, then the one-time `fashionai://login` handoff. Provider-enabled booleans do not establish these registrations or a successful exchange.
4. **User / operator:** approve a concrete new bounded Genlook test only after session, server readiness, input rights and budget are confirmed. Historical credit approvals are not reused. Product buttons/pockets/waist/shoe fidelity remains unproven.
5. **Release owner:** retain the existing private key and separate password store; complete external encrypted backup. CI/review and any main merge remain separately authorized.

**Service launch: NO-GO until actual Release device acceptance, OAuth and authenticated generation/quality/operating-policy conditions pass.** A signed Release APK is a candidate artifact, not evidence of these outcomes.
