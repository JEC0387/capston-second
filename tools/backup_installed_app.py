"""Read-only, local encrypted safety copy of a debuggable app; never restores or migrates data."""
import argparse
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import subprocess
import tarfile
import xml.etree.ElementTree as ET
from windows_private_archive import save


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    package = "com.jeiu.fashionproject"
    # No force-stop, sqlite writes, external upload, or access to Android Keystore keys.
    result = subprocess.run([args.adb, "-d", "exec-out", "run-as", package, "tar", "-cf", "-",
                             "files", "shared_prefs", "databases", "no_backup"], capture_output=True, check=True)
    summary = {"files": 0, "sharedPreferences": 0, "databaseFiles": 0, "pendingSyncItems": None}
    with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
        members = archive.getmembers()
        for member in members:
            if not member.isfile():
                continue
            if member.name.startswith("files/"):
                summary["files"] += 1
            elif member.name.startswith("databases/"):
                summary["databaseFiles"] += 1
            elif member.name.startswith("shared_prefs/"):
                summary["sharedPreferences"] += 1
                if member.name == "shared_prefs/db_sync.xml":
                    root = ET.fromstring(archive.extractfile(member).read())
                    value = next((node.text for node in root if node.get("name") == "pending"), None)
                    if value:
                        summary["pendingSyncItems"] = len(json.loads(value))
        summary["sharedPreferenceStores"] = [Path(member.name).stem for member in members
            if member.isfile() and member.name.startswith("shared_prefs/") and member.name.endswith(".xml")]
    save(result.stdout, args.output, {"package": package, "createdUtc": datetime.now(timezone.utc).isoformat(),
        "kind": "read-only safety snapshot; live files may change; NOT a validated import", "summary": summary})
    print(json.dumps(summary, ensure_ascii=False))
