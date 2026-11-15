# Fourth-app installation candidate

Opt in with `-PfashionInstallTest=true` and build **debug** tasks only.
This creates `com.jeiu.fashionproject.installtest`, launcher label `패션핏 설치검증`,
and a `-installtest` version suffix. The namespace and Kotlin classes remain
`com.jeiu.fashionproject`. It is not an update to primary, Preview, or device QA.

Use an independent local debug key at
`fashion_app/.installtest-signing/installtest-debug.p12`, alias
`installtestdebugkey`, with the standard debug password `android`. Generate it
once with JDK keytool (RSA 2048 or stronger), keep it outside Git/delivery and
Gradle clean outputs, and
reuse it for later install-test updates. This is not a production signing key.
Do not supply `ANDROID_DISTRIBUTION_KEYSTORE`; the build rejects it for this target.
Existing default, Preview, and QA signing settings are unchanged.

For parity with private Release 18614, also pass
`-PfashionOutfitServerApproved=true`. This keeps the server AI opt-in enabled but
does not waive Bearer authentication, photo consent, budget, provider readiness,
or result integrity checks. No API keys are packaged. Without the opt-in, the
existing default-disabled server policy applies. Do not generate/upload images
as part of installation verification.

The fourth app uses `fashionfit-installtest://login`, not `fashionai://login`.
Google and Kakao are deliberately unavailable in this unregistered package,
including direct callback processing, before any provider/network/browser access.
Guest mode and local records remain available. Registration of a new OAuth
package/certificate/callback and explicit follow-up validation are required before
enabling social login; never redirect its login into an existing app.

Example (set the existing version environment variables before invoking Gradle):

```text
gradlew.bat :app:assembleDebug :app:lintDebug -PfashionInstallTest=true -PfashionOutfitServerApproved=true --offline --max-workers=1 --no-parallel
```

Run `InstallTestIsolationTest`, `FashionFitBrandingTest`, consent/image-contract,
server controller/recovery tests with these flags. Run normal auth preservation
tests in the normal variant. `InstallTestIsolationTest` is variant-specific and is
not a claim that social login or actual AI generation succeeded.

Verify the actual APK applicationId, label, version, signature, CRC, merged
manifest authorities/deep links, and embedded build identity. Do not use the
primary-package publication verifier as permission to distribute this debug app.
Keep old APKs. An ADB inventory with no device is **not** an installation pass.
Never uninstall, clear, replace, or downgrade any existing package to make the
fourth app install. A later device test must compare installed IDs first.
