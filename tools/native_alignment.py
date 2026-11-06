"""Audit APK ELF LOAD alignment with LLVM and ZIP alignment with Android zipalign.

Static checks only: this never installs an APK or claims 16KB runtime compatibility.
Use the NDK's llvm-readelf (or LLVM's installed equivalent) and SDK Build Tools 35+.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

PAGE_SIZE = 16384
ABIS = {"arm64-v8a", "x86_64"}


def load_segments(program_headers):
    """Read LLVM --program-headers --wide output; reject absent/malformed LOAD rows."""
    segments = []
    for line in program_headers.splitlines():
        fields = line.split()
        if not fields or fields[0] != "LOAD":
            continue
        # LOAD Offset VirtAddr PhysAddr FileSiz MemSiz Flg... Align
        if len(fields) < 8:
            raise ValueError("Malformed LLVM LOAD row")
        try:
            offset, address = int(fields[1], 16), int(fields[2], 16)
            align = int(fields[-1], 16)
        except ValueError as error:
            raise ValueError("Malformed LLVM LOAD values") from error
        segments.append({"offset": offset, "virtualAddress": address, "alignment": align,
                         "aligned16Kb": align >= PAGE_SIZE and align & (align - 1) == 0
                         and (address - offset) % PAGE_SIZE == 0})
    if not segments:
        raise ValueError("No LLVM LOAD segments found; cannot certify alignment")
    return segments


def native_members(archive):
    names = [n for n in archive.namelist() if n.endswith(".so")]
    selected = [n for n in names if len(n.split("/")) == 3 and n.split("/")[0] == "lib"
                and n.split("/")[1] in ABIS]
    if len(selected) != len(set(selected)):
        raise ValueError("Duplicate native ZIP entry")
    if not selected:
        raise ValueError("No arm64-v8a/x86_64 native libraries found")
    return names, sorted(selected)


def find_llvm():
    suffix = ".exe" if os.name == "nt" else ""
    for variable in ("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "ANDROID_NDK_LATEST_HOME"):
        root = os.environ.get(variable)
        if root:
            for tool in Path(root).glob("toolchains/llvm/prebuilt/*/bin/llvm-readelf" + suffix):
                return str(tool)
    return shutil.which("llvm-readelf")


def audit(apk, llvm_readelf, zipalign, output):
    apk, output = Path(apk).resolve(), Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    version = "\n".join(subprocess.run([str(llvm_readelf), "--version"], check=True, capture_output=True,
                                      text=True, timeout=20).stdout.splitlines()[:3])
    zip_check = subprocess.run([str(zipalign), "-c", "-P", "16", "-v", "4", str(apk)],
                               capture_output=True, text=True, timeout=120)
    output.with_suffix(".zipalign.txt").write_text(zip_check.stdout + zip_check.stderr, encoding="utf-8")
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    report = {"apk": apk.name, "apkSha256": digest, "llvmVersion": version,
              "zipAligned16Kb": zip_check.returncode == 0,
              "runtime16KbVerified": False, "libraries": []}
    # Only this tool's freshly allocated directory is removed; no existing project artifacts touched.
    with tempfile.TemporaryDirectory(prefix="native-alignment-", dir=output.parent) as folder:
        temp = Path(folder).resolve()
        assert temp.parent == output.parent and temp.name.startswith("native-alignment-")
        with zipfile.ZipFile(apk) as archive:
            all_names, selected = native_members(archive)
            report["packagedAbis"] = sorted({n.split("/")[1] for n in all_names if n.startswith("lib/")})
            for index, name in enumerate(selected):
                # Generate extraction names ourselves; never extract untrusted ZIP paths.
                binary = archive.read(name)
                local = temp / (str(index) + ".so")
                local.write_bytes(binary)
                result = subprocess.run([str(llvm_readelf), "--program-headers", "--wide", str(local)],
                                        capture_output=True, text=True, timeout=60)
                item = {"path": name, "sha256": hashlib.sha256(binary).hexdigest()}
                try:
                    if binary[:5] != b"\x7fELF\x02" or result.returncode != 0:
                        raise ValueError("Not a readable ELF64 shared library")
                    item["loadSegments"] = load_segments(result.stdout)
                    item["elfLoadAligned16Kb"] = all(s["aligned16Kb"] for s in item["loadSegments"])
                except ValueError as error:
                    item["error"] = str(error)
                    item["elfLoadAligned16Kb"] = False
                item["llvmProgramHeaders"] = result.stdout
                report["libraries"].append(item)
    report["elfLoadAligned16Kb"] = all(x["elfLoadAligned16Kb"] for x in report["libraries"])
    report["staticChecksPassed"] = report["elfLoadAligned16Kb"] and report["zipAligned16Kb"]
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--llvm-readelf", default=find_llvm())
    parser.add_argument("--zipalign", default=shutil.which("zipalign"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.llvm_readelf or not args.zipalign:
        parser.error("Both official LLVM llvm-readelf and Android SDK zipalign are required")
    result = audit(args.apk, args.llvm_readelf, args.zipalign, args.output)
    print(json.dumps({k: result[k] for k in ("apk", "apkSha256", "packagedAbis", "zipAligned16Kb",
                                             "elfLoadAligned16Kb", "staticChecksPassed", "runtime16KbVerified")}))
    for item in result["libraries"]:
        print(("PASS " if item["elfLoadAligned16Kb"] else "FAIL ") + item["path"])
    return 0 if result["staticChecksPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
