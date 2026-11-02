"""Allocate CI versions and verify the exact APK before publishing. No signing secrets are read here."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import zipfile


def version_code(run, attempt):
    if run < 1 or not 1 <= attempt <= 99:
        raise ValueError("Invalid run number/attempt; start a fresh workflow run")
    code = 10_000 + run * 100 + attempt
    if code > 2_100_000_000:
        raise ValueError("Android versionCode range exhausted")
    return code


def published_floor():
    """Fail closed on API errors; include all unexpired distributed artifacts across branches."""
    headers = {"Authorization": "Bearer " + os.environ["GITHUB_TOKEN"], "Accept": "application/vnd.github+json"}
    root = f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}"
    # Artifact expiration/deletion must not make an old workflow rerun distributable.
    request = urllib.request.Request(root + "/actions/workflows/android-debug-apk.yml/runs?per_page=100", headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        runs = json.load(response)["workflow_runs"]
    floor = max([3] + [version_code(run["run_number"], run["run_attempt"]) - 1 for run in runs])
    for page in range(1, 1000):
        url = f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/actions/artifacts?per_page=100&page={page}"
        request = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            artifacts = json.load(response)["artifacts"]
        for artifact in artifacts:
            match = re.match(r"fashionai-(?:preview-)?apk-vc(\d+)-", artifact["name"])
            if match:
                floor = max(floor, int(match[1]))
        if len(artifacts) < 100:
            return floor
    raise RuntimeError("Artifact history exceeded pagination limit")


def reject_downgrade(candidate, previous):
    if candidate <= previous:
        raise ValueError(f"versionCode {candidate} must exceed {previous}; start a NEW workflow run, not an old rerun")


def tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    suffix = ".bat" if name == "apksigner" and os.name == "nt" else ".exe" if os.name == "nt" else ""
    result = sdk / "build-tools" / "36.0.0" / (name + suffix)
    if not result.is_file():
        raise FileNotFoundError(result)
    return str(result)


def inspect(apk):
    badging = subprocess.check_output([tool("aapt"), "dump", "badging", str(apk)], text=True, encoding="utf-8")
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match:
        raise ValueError("APK manifest version missing")
    signature = subprocess.check_output([tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signature)
    if len(certificates) != 1:
        raise ValueError("Exactly one verified APK signer is required")
    return {"packageName": match[1], "versionCode": int(match[2]), "versionName": match[3],
            "certificateSha256": certificates[0].lower()}


def verify(apk, output, distribution=False, preview=False):
    manifest = inspect(apk)
    with zipfile.ZipFile(apk) as archive:
        # Build/signing secrets belong to CI only, never an APK entry or DEX constant.
        forbidden = [os.environ.get(name, "").encode() for name in ["ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_PASSWORD"]]
        key_path = os.environ.get("ANDROID_DISTRIBUTION_KEYSTORE")
        if key_path:
            forbidden.append(Path(key_path).read_bytes())
        for name in archive.namelist():
            if re.search(r"(^|/)(\.env|[^/]+\.(keystore|jks|p12))$", name, re.I):
                raise ValueError("Private configuration/signing file found inside APK")
            data = archive.read(name)
            if any(secret and len(secret) >= 16 and secret in data for secret in forbidden):
                raise ValueError("Signing secret found inside APK; publication blocked")
        identity = json.loads(archive.read("assets/build_identity.json"))
        # These compiled features must be present in this app, independent of APK filename.
        dex = [archive.read(name) for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        for marker in ["3D 기본 모델", "3D 비율 아바타 보기", "신체 치수 직접 입력", "AiTryOnScreen", "AppBuildInfo",
                       "LocalVtonScreen", "LocalVtonRepository", "자체 AI 착장 이미지 생성", "fashn-vton-1.5-local",
                       "AvatarMeasurementImport", "AvatarApplicationDialog", "3D 반영 전후 비교", "BodyMeasurementOverlay",
                       "PremiumAvatarEditor", "CmRuler", "PremiumAvatarMesh", "AvatarCentimeters", "3D 체형 조절",
                       "MannequinStage", "MannequinTopology", "PremiumAvatarMesh$Profile", "uStageMode"]:
            if not any(marker.encode("utf-8") in data for data in dex):
                raise ValueError(f"Required APK feature missing: {marker}")
        if any("DB 연결 안 됨 · 로컬 사용 가능".encode("utf-8") in data for data in dex):
            raise ValueError("Obsolete server message found in DEX")
        if any(b"P9MannequinReference" in data for data in dex):
            raise ValueError("Test-only reference geometry must not be packaged in the app")
    for field in ["versionCode", "versionName"]:
        if identity[field] != manifest[field]:
            raise ValueError(f"APK manifest/embedded identity mismatch: {field}")
    if manifest["packageName"] != "com.jeiu.fashionproject" + (".preview" if preview else ""):
        raise ValueError("Unexpected application ID; this would be a different app")
    if identity.get("preview") != preview:
        raise ValueError("Preview identity mismatch")
    reject_downgrade(manifest["versionCode"], 3)
    git_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if identity["gitSha"] != git_sha:
        raise ValueError("APK does not contain the current checkout SHA")
    if distribution:
        if not preview:
            raise ValueError("Primary package publication is blocked; approve a separate release/migration policy first")
        expected = {"versionCode": int(os.environ["FASHION_VERSION_CODE"]), "versionName": os.environ["FASHION_VERSION_NAME"] + "-preview",
                    "buildNumber": os.environ["FASHION_BUILD_NUMBER"], "branch": os.environ["GITHUB_REF_NAME"],
                    "gitSha": os.environ["GITHUB_SHA"], "sourceDirty": False}
        if any(identity[k] != value for k, value in expected.items()):
            raise ValueError("APK identity differs from CI source/version")
        certificate = os.environ.get("APK_SIGNING_CERT_SHA256", "").replace(":", "").lower()
        if not certificate or manifest["certificateSha256"] != certificate:
            raise ValueError("Missing or mismatched trusted distribution signer fingerprint; do not rotate keys")
        reject_downgrade(manifest["versionCode"], published_floor())
    output.mkdir(parents=True, exist_ok=True)
    filename = f"fashionai-{manifest['versionName']}-vc{manifest['versionCode']}-{identity['gitSha'][:8]}.apk"
    target = output / filename
    shutil.copy2(apk, target)
    sha256 = hashlib.sha256(target.read_bytes()).hexdigest()
    data = {**identity, **manifest, "apk": filename, "apkSha256": sha256, "distributionVerified": distribution}
    (output / "BUILD_INFO.txt").write_text("\n".join(f"{k}: {v}" for k, v in data.items()) + "\n", encoding="utf-8")
    (output / "BUILD_INFO.json").write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(f"{sha256}  {filename}\n", encoding="utf-8")
    print(json.dumps(data, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    command = commands.add_parser("verify")
    command.add_argument("apk", type=Path)
    command.add_argument("output", type=Path)
    command.add_argument("--distribution", action="store_true")
    command.add_argument("--preview", action="store_true")
    args = parser.parse_args()
    if args.command == "prepare":
        run, attempt = int(os.environ["GITHUB_RUN_NUMBER"]), int(os.environ["GITHUB_RUN_ATTEMPT"])
        code = version_code(run, attempt)
        reject_downgrade(code, published_floor())
        values = {"FASHION_VERSION_CODE": code, "FASHION_VERSION_NAME": f"1.1.{run}.{attempt}", "FASHION_BUILD_NUMBER": f"{run}.{attempt}"}
        with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as file:
            for key, value in values.items():
                file.write(f"{key}={value}\n")
        print(f"Allocated version {values['FASHION_VERSION_NAME']} ({code})")
    else:
        verify(args.apk, args.output, args.distribution, args.preview)
