"""Read-only APK update preflight. Never uninstall, clear data, install, or access app-private data."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from apk_release import inspect


def compatibility(installed, candidate):
    if installed["packageName"] != candidate["packageName"]:
        return "BLOCKED: different package; it would install as a different app"
    if installed["certificateSha256"] != candidate["certificateSha256"]:
        return "BLOCKED: different signing certificate; obtain an APK signed by the ORIGINAL key. Keep the installed app and its data."
    if candidate["versionCode"] <= installed["versionCode"]:
        return "BLOCKED: versionCode must be higher than the installed app"
    return "COMPATIBLE: package, signer and version pass. Device installation/GPU behavior still requires testing."


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--installed-apk", type=Path)
    source.add_argument("--serial", help="Authorized adb device serial; reads/pulls only the installed base APK")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    candidate = inspect(args.candidate)
    with tempfile.TemporaryDirectory(prefix="fashionai-apk-check-") as folder:
        installed = args.installed_apk
        if args.serial:
            base = [args.adb, "-s", args.serial]
            paths = subprocess.check_output(base + ["shell", "pm", "path", candidate["packageName"]], text=True)
            remote = next((line.removeprefix("package:").strip() for line in paths.splitlines() if line.endswith("/base.apk")), None)
            if not remote:
                raise SystemExit("Installed base.apk not found for this package/user. Check work profile or cloned app.")
            installed = Path(folder) / "installed-base.apk"
            subprocess.run(base + ["pull", remote, str(installed)], check=True)
        original = inspect(installed)
        print(json.dumps({"installed": original, "candidate": candidate}, indent=2))
        result = compatibility(original, candidate)
        print(result)
        raise SystemExit(0 if result.startswith("COMPATIBLE:") else 2)
