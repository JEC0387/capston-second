"""Fixed public research inputs and one official Space result URL boundary.

No network, SDK, account, private wardrobe, or production app integration.
The hashes identify the reviewed public samples; publication alone is not a
general license to substitute other people or images.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import os
from pathlib import Path
import stat
from urllib.parse import unquote, urlsplit


MAX_INPUT_BYTES = 10 * 1024 * 1024
OFFICIAL_RESULT_HOST = "qwen-qwen-image-2-1.hf.space"
_SAMPLES = (
    ("person", "person.webp", "2b5f14e0dfc96e1f5027eb3942da5e9dde7def714816a05d0e16e06b0e56f78a"),
    ("top", "top.webp", "b4a6863db950dbb5e2dc3881b566fe129655cee9dd0d0d8566fecfab3b6be4aa"),
    ("bottom", "bottom.jpg", "aaf50a567371bc9af71cfcabd82480b396b2c6b3f8df8118eee5d15f8b5c23b0"),
    ("shoes", "shoes.webp", "378a487abe2754ffdbd3caeef6338a077cfadf86049840598d4fbe2d8c0429a0"),
)


class PublicInputRejected(ValueError):
    """Only safe reason codes, without caller paths or data contents."""


@dataclass(frozen=True)
class PublicInput:
    role: str
    path: Path = field(repr=False)
    sha256: str
    size_bytes: int
    data: bytes = field(repr=False)


def _reject_links(path: Path) -> None:
    # Windows junctions are reparse points, not always Path.is_symlink().
    for component in (path, *path.parents):
        try:
            info = component.lstat()
        except OSError:
            raise PublicInputRejected("input_path_unavailable") from None
        if stat.S_ISLNK(info.st_mode) or (
                getattr(info, "st_file_attributes", 0) &
                getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
            raise PublicInputRejected("links_and_reparse_points_forbidden")


def load_public_inputs(selected_root: str | Path) -> tuple[PublicInput, ...]:
    """Load exactly person/top/bottom/shoes; arbitrary names/URLs are not accepted.

    Use the returned immutable bytes for upload, not a later path re-read. This
    prevents a changed local file from replacing an already checked sample.
    """
    if not isinstance(selected_root, (str, Path)):
        raise PublicInputRejected("input_root_type_invalid")
    raw_root = str(selected_root)
    if not raw_root or "://" in raw_root or any(ord(c) < 32 for c in raw_root):
        raise PublicInputRejected("local_sample_directory_required")
    root = Path(selected_root).absolute()
    _reject_links(root)
    if not root.is_dir():
        raise PublicInputRejected("sample_directory_required")
    resolved_root = root.resolve(strict=True)
    result = []
    for role, name, expected in _SAMPLES:
        path = root / name
        _reject_links(path)
        try:
            resolved = path.resolve(strict=True)
            if resolved.parent != resolved_root:
                raise PublicInputRejected("input_path_escape")
            if not stat.S_ISREG(path.lstat().st_mode):
                raise PublicInputRejected("regular_sample_required")
            with path.open("rb") as source:
                info = os.fstat(source.fileno())
                if not stat.S_ISREG(info.st_mode):
                    raise PublicInputRejected("regular_sample_required")
                if not 0 < info.st_size <= MAX_INPUT_BYTES:
                    raise PublicInputRejected("input_size_invalid")
                data = source.read(MAX_INPUT_BYTES + 1)
        except OSError:
            raise PublicInputRejected("input_read_failed") from None
        if not 0 < len(data) <= MAX_INPUT_BYTES:
            raise PublicInputRejected("input_size_invalid")
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            raise PublicInputRejected("unapproved_input_hash")
        result.append(PublicInput(role, resolved, actual, len(data), data))
    return tuple(result)


def validate_output_url(value: str) -> str:
    """Validate only; the caller must disable redirects during a bounded GET.

    This exact host does not authorize other Spaces, their URLs, or local paths.
    Returned URL still needs response byte limits, decoding, and request binding.
    """
    if not isinstance(value, str) or not value or len(value) > 4096:
        raise PublicInputRejected("invalid_output_url")
    if any(ord(c) <= 32 or ord(c) == 127 for c in value):
        raise PublicInputRejected("invalid_output_url")
    try:
        parsed = urlsplit(value)
        if (not value.startswith("https://") or parsed.scheme != "https" or
                parsed.netloc != OFFICIAL_RESULT_HOST or
                parsed.query or parsed.fragment or "?" in value or "#" in value or
                parsed.username is not None or parsed.password is not None or
                parsed.port is not None):
            raise PublicInputRejected("output_origin_not_allowed")
    except ValueError:
        raise PublicInputRejected("output_origin_not_allowed") from None
    prefix = "/gradio_api/file="
    if not parsed.path.startswith(prefix):
        raise PublicInputRejected("output_path_not_allowed")
    try:
        payload = unquote(parsed.path[len(prefix):], errors="strict")
    except UnicodeDecodeError:
        raise PublicInputRejected("output_path_not_allowed") from None
    if (not payload or payload.endswith("/") or "\\" in payload or
            "%" in payload or "?" in payload or "#" in payload or
            ":" in payload or "//" in payload or
            any(ord(c) < 32 or ord(c) == 127 for c in payload) or
            any(part in {".", ".."} for part in payload.split("/"))):
        raise PublicInputRejected("output_path_not_allowed")
    return value
