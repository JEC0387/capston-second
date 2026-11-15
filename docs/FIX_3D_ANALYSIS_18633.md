# 18633 3D analysis correction

Base APK/source: `1.1.86.33` / `b0c72f355629d8bd4e09fbdfb912aed93a3b0429`.
Work branch: `codex/fix-3d-analysis-18633-20261115`. Candidate version: `1.1.86.34` / `18634`.

## Cause and behavior

The editor allowed a height/weight model to be saved, but its analysis button required all three tape circumferences. The only result calculator read measured references, never ruler adjustments. Two previous UI tests intentionally asserted this blocking behavior.

The measured classifier and its thresholds remain unchanged. A separate model calculator now consumes the same validated `AvatarProfile.parameters()` used by the renderer:

- Modern centimeter models compare their rendering circumferences (including depth changes). Legacy models compare front widths only; they are never converted into circumferences.
- Descriptive model traits use 8% upper/lower contrast and 20% waist indentation. These are rendering descriptors, not validated population/medical/size cutoffs.
- The result is labelled `3D 모델 기반 추정`; evidence is expressed as ratios of the model, not asserted human centimeters. No model-derived values are written to measured/photographic stores and no model-derived clothing size is asserted.
- Complete measured references, without separate ruler changes, still use the original measured analysis. Ruler edits use the edited model while preserving tape references unchanged. Invalid profiles/inputs remain blocked.
- Save-before-analysis and re-save-after-edit remain. Results can return directly to the editor. Navigation saves/restores the owner-keyed editor draft when leaving/reopening, without implicitly saving over the user's durable profile.
- Current photo analysis, wardrobe, authentication, Genlook and server contracts are unchanged.

## Existing QR and replacement boundary

The QR decodes to this **tag/name** URL, not an expiring URL or numeric asset ID:

`https://github.com/JEC0387/capston-second/releases/download/fashionfit-1.1.86.33-p70-20261115/FashionFit-1.1.86.33-release-vc18633.apk`

Original APK SHA-256: `4d29f9b0321a48c2d64a81e7e37a05017527cc47aaa2a89955b06a99160baa57`.
Original size: `140131470` bytes. The original local delivery and independently downloaded identical copy remain preserved outside Git.

No existing Release/asset/QR is modified in this change. Replacing the asset requires the user's final approval. If approved, keep the tag and filename to preserve the URL, clearly describe the new **internal** version in the Release notes, replace/update associated SHA information, then download through the exact QR URL without authentication and compare the new SHA/certificate/version. A stable URL does not guarantee immediate CDN freshness or overwrite a previously downloaded phone file.

Rollback after approval: upload the preserved original APK under the original tag/filename and restore the original checksums/Release description; verify the QR URL downloads the original hash again. Asset replacement is not atomic, so a temporary download gap is possible. An APK already updated on a device must not be force-downgraded or uninstalled as rollback.

The corrected APK remains a separately named local candidate until approval. No paid AI call or operational server change is part of this fix. Automatic tests and prior/live device evidence must be reported separately.
