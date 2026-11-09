FashionFit approved artwork — 2026-11-09

selected-icon-source.png is the user-supplied final design, copied unchanged from
FashionFit_Selected_Icon_Source.png. SHA256:
b34cdffaf0643359584ceb4a60bacdf94ed7a1203e90acda54b050f371d48111

Reproduce Android assets with the project development Python/Pillow environment:
python tools/fashionfit_icons.py --preview-dir fashion_app/build/fashionfit-icon-review

No generative image tool, new design or external image service is used. The script
replaces only the neutral dark region connected to the outer source edge. Original
warm-ribbon pixels are asserted unchanged before resampling. The entire source tile
is scaled within an adaptive 108dp layer; ribbon pixels remain in the 66dp safe circle.
Legacy 48/72/96/144/192px normal and round resources are lossless WebP. The themed
layer is the ribbon silhouette, separately tinted by the Android launcher.

Official adaptive-icon layer/safe-zone reference:
https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

Android applicationId, OAuth scheme fashionai://login, encryption aliases/AAD,
database schema and VTON protocol identifiers deliberately retain their existing
values. They are compatibility contracts, not visible branding. SERVER_OUTFIT_APPROVED
stays false; the production API URL is not changed.

Launcher labels: 패션핏 / 패션핏 Preview / 패션핏 QA.
Visible internal brand: FashionFit (login, onboarding and app information).
No device installation or AI request is part of this resource conversion.
