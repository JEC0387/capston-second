"""Create/reuse an encrypted local PREVIEW key backup and install it via official gh Secrets APIs."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import zipfile
from windows_private_archive import save, transform


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gh", required=True)
    parser.add_argument("--keytool", required=True)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()
    if args.backup.exists():
        with zipfile.ZipFile(args.backup) as archive:
            data = json.loads(transform(archive.read("payload.dpapi"), True))
        if data.get("purpose") != "preview-only":
            raise RuntimeError("Not a Preview key backup")
    else:
        password = secrets.token_urlsafe(36)
        environment = dict(os.environ, FASHION_SIGNING_PASSWORD=password)
        with tempfile.TemporaryDirectory(prefix="fashionai-preview-key-") as folder:
            key = Path(folder) / "preview.p12"
            command = [args.keytool, "-genkeypair", "-storetype", "PKCS12", "-keystore", str(key),
                "-alias", "fashionai-preview", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                "-dname", "CN=FashionAI Preview,O=FashionAI,C=KR", "-storepass:env", "FASHION_SIGNING_PASSWORD",
                "-keypass:env", "FASHION_SIGNING_PASSWORD"]
            subprocess.run(command, env=environment, capture_output=True, check=True)
            certificate = subprocess.check_output([args.keytool, "-exportcert", "-keystore", str(key),
                "-alias", "fashionai-preview", "-storepass:env", "FASHION_SIGNING_PASSWORD"], env=environment)
            data = {"purpose": "preview-only", "keyStoreBase64": base64.b64encode(key.read_bytes()).decode(),
                "password": password, "alias": "fashionai-preview", "certificateSha256": hashlib.sha256(certificate).hexdigest()}
        save(json.dumps(data).encode(), args.backup, {"purpose": "preview-only signing", "applicationId": "com.jeiu.fashionproject.preview",
            "certificateSha256": data["certificateSha256"], "createdUtc": datetime.now(timezone.utc).isoformat()})
    # gh encrypts secrets with the repository public key. Never extract gh/GCM credentials.
    for name, value in {"ANDROID_DEBUG_KEYSTORE_BASE64": data["keyStoreBase64"],
                        "ANDROID_KEYSTORE_PASSWORD": data["password"], "ANDROID_KEY_PASSWORD": data["password"]}.items():
        subprocess.run([args.gh, "secret", "set", name, "--repo", args.repo], input=value, text=True, capture_output=True, check=True)
        print("Configured encrypted repository secret: " + name)
    for name, value in {"ANDROID_KEY_ALIAS": data["alias"], "ANDROID_SIGNING_CERT_SHA256": data["certificateSha256"]}.items():
        subprocess.run([args.gh, "variable", "set", name, "--repo", args.repo, "--body", value], capture_output=True, check=True)
    print("Preview certificate SHA-256: " + data["certificateSha256"])
