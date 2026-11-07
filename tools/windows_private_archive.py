"""Windows user-bound DPAPI archive. Never writes plaintext payload to disk."""
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import zipfile


class Blob(ctypes.Structure):
    _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]


def transform(data, decrypt=False):
    if os.name != "nt":
        raise RuntimeError("DPAPI archive requires the same Windows user account")
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = Blob()
    crypt = ctypes.windll.crypt32
    if decrypt:
        ok = crypt.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target))
    else:
        ok = crypt.CryptProtectData(ctypes.byref(source), "FashionAI private backup", None, None, None, 1, ctypes.byref(target))
    if not ok:
        raise ctypes.WinError()
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        ctypes.windll.kernel32.LocalFree(target.data)


def save(payload: bytes, output: Path, metadata: dict):
    if output.exists():
        raise FileExistsError("Refusing to overwrite a private backup")
    encrypted = transform(payload)
    if transform(encrypted, decrypt=True) != payload:
        raise RuntimeError("DPAPI round-trip verification failed")
    metadata = dict(metadata, ciphertextSha256=hashlib.sha256(encrypted).hexdigest(),
                    encryption="Windows DPAPI CurrentUser; preserve this Windows profile; not a portable transfer file")
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("payload.dpapi", encrypted)
        archive.writestr("MANIFEST.json", json.dumps(metadata, ensure_ascii=False, indent=2))
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None or transform(archive.read("payload.dpapi"), True) != payload:
            raise RuntimeError("Written private backup verification failed")
    print(json.dumps({"backup": str(output.resolve()), "verified": True, "encrypted": True}))
