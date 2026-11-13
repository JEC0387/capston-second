# FashionFit 1.1.86.25 Release verification

- APK source: `e99da0440bfdcf3f97761749990dd1a8a78c5f50` from a clean worktree.
- Package: `com.jeiu.fashionproject`, version name `1.1.86.25`, version code `18625`.
- Signed file: `FashionFit-1.1.86.25-release-vc18625.apk`.
- Signed APK SHA-256: `b0261ecf6d7228666eda248c94db404265c8061309233cc027de7aede4fa6076`.
- Verified release certificate SHA-256: `8741fa599ce7db7088ee452121cf7bfbc5cd45b324e5ce076812fd42e0712ba3`.
- APK contents verified: Railway production URL, `SERVER_OUTFIT_APPROVED=true`, and debug, Preview, QA, and installtest disabled.
- Release assembly, APK signature verification, 16 KiB ZIP alignment and ELF alignment for nine 64-bit libraries passed. Release Lint reported 0 errors, 170 warnings and 3 hints.
- Android regression: 42 passed across the bundled outfit, local image, server contract and controller, result UI, image contract and consent suites. Release guard, verifier and alignment unit tests: 13 passed.

## Image compatibility

The failing default outfit (`sample_10` denim shirt with `shop_denim_shirt.webp`, `sample_06` slacks with `shop_officer_pants.webp`, and `sample_12` loafers) passed with the bundled resources, Android bitmap decoder, actual preparer and request body builder. The request contained the original three selected IDs and three canonical PNGs. The source bytes were unchanged.

The default checked shirt, wide jeans and sneakers (`sample_04`, `sample_05`, `sample_11`) also passed using the actual bundled JPEGs. The suite covers PNG/JPEG, static WebP including transparency, read failure, truncated WebP, oversized input, unsupported and animated input, selection retention, and zero submissions or ticket writes on preparation failure. The first test run exposed a Windows fixture URI issue, resolved by constructing the fixture URI through Android's `Uri.fromFile` before the final run.

The reported physical-device success was generation and result display in 18624. Device installation, login, production Genlook generation, result display, save and reopen were not exercised for 18625. No production photo transfer or paid request was made by this verification.
