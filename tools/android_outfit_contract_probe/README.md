# Installed QA 18611 image-contract probe

This is an offline diagnostic, not a new app feature or an AI engine. It loads the
read-only installed QA APK and calls its real `parseConfig`, `imageSpec`, image
normalization, receipt binding and request encoder. No APK installation or app
private write is needed. Only the exact synthetic quadrant fixtures emitted by
`P57ServerImageContractTest` are accepted; arbitrary photographs are rejected.

Compile both Java sources against the installed Android SDK `android.jar`, then
convert the classes with D8 (`--min-api 28`) and package `classes.dex` in a JAR.
Keep generated files outside Git. Give every run a new
`/data/local/tmp/p57-<unique>` directory, put the four `*-original.png` fixtures and
synthetic `config.json` there, and make the JAR read-only before `app_process`.
The configured maximum side must be 2048, model/revision/policy must be the
synthetic values checked by the probe. Do not modify the app's real configuration.

The shell entry point is `OutfitContractProbe <installed QA base.apk> <fixture
directory>`. It rejects another package/version and writes a fresh `output-UUID`
directory with PNGs, request bytes, hashes and a receipt. Copy that directory to
private local evidence. Never substitute a different APK or alter a receipt to
make it pass. Inspect a failure before another attempt.

For transport verification, `loopback_validator.py <evidence> --token-file
<private ephemeral capability file> --port <unused port>` binds only to PC
127.0.0.1. It has no Genlook provider and no real user authentication. Use only an
owned, temporary ADB reverse mapping to that port. `LoopbackWireSender` accepts
only this device-loopback URL, the frozen payload path and a private token-file
path. Never put the token in a command argument or repository. No TLS or Android
security setting is changed. Stop the owned server, remove that one reverse
mapping and delete the two newly created capability files after the test.

The receiver checks HTTP body SHA/bytes against the independent device receipt,
then uses the existing FastAPI parser, PNG normalizer and strict product-reference
validator. Actual pixel alteration must still fail. An authenticated attempt is
single-use, including malformed/disconnected attempts; the caller does not retry.

**Evidence limits:** the probe does not exercise the UI photo picker, magic-wand
controller, production HTTPS/Bearer transport, external provider, generated-image
quality or saving. Device execution must be reported separately from local unit
tests. The synthetic sRGB ICC/EXIF tests do not prove all possible color profiles.
No result should be called new AI generation.
