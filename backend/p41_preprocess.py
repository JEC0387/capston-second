"""Offline, separate-process P41 input preparation; no generative model is loaded.

Reuse the reviewed P40 real DWPose / flat-lay segmentation-free transforms,
then persist only fixed-shape numeric NumPy tensors. This is not inference or
image-quality evidence. No parser, model download, pickle, server or device.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from p40_flatlay_probe import (
    Evidence, REVISION, WEIGHTS, namespace_source, offline_network_guard,
    prepare_real_inputs, sha256, verify_source, verify_weights,
)

ARRAYS_NAME = "prepared.npz"
MANIFEST_NAME = "prepared-manifest.json"
SCHEMA = "fashionai-p41-prepared-v1"
SHAPES = {
    "ca_images": (1, 3, 864, 576),
    "garment_images": (1, 3, 864, 576),
    "person_poses": (1, 1, 864, 576),
    "garment_poses": (1, 1, 864, 576),
    "garment_categories": (1,),
}
MAX_ARCHIVE_BYTES = 24 * 1024 * 1024
MAX_MANIFEST_BYTES = 128 * 1024
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")


def _hash_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _valid_hash(value) -> bool:
    return isinstance(value, str) and SHA_PATTERN.fullmatch(value) is not None


def _local_file(folder: Path, name: str) -> Path:
    """Only fixed children; never follow manifest filenames or external links."""
    path = folder / name
    if path.is_symlink() or path.resolve().parent != folder:
        raise ValueError("prepared_file_outside_folder")
    if not path.is_file():
        raise ValueError("prepared_file_missing")
    return path


def _validate_padding(value):
    if not isinstance(value, (list, tuple)) or len(value) != 4 or any(type(x) is not int for x in value):
        raise ValueError("invalid_padding")
    left, top, right, bottom = value
    if min(value) < 0 or left + right >= 576 or top + bottom >= 864:
        raise ValueError("invalid_padding")
    # Reviewed ResizePad fits one dimension and adds symmetric padding to the other.
    if right - left not in (0, 1) or bottom - top not in (0, 1) or ((left or right) and (top or bottom)):
        raise ValueError("invalid_padding")
    return list(value)


def _validate_array(name, array, category):
    import numpy as np
    expected_dtype = np.dtype("int64" if name == "garment_categories" else "float32")
    if array.dtype != expected_dtype or tuple(array.shape) != SHAPES[name]:
        raise ValueError("prepared_tensor_shape_or_dtype:" + name)
    if not array.flags.c_contiguous:
        raise ValueError("prepared_tensor_not_canonical:" + name)
    if name == "garment_categories":
        if int(array[0]) != {"tops": 1, "bottoms": 2}[category]:
            raise ValueError("prepared_category_mismatch")
    elif not bool(np.isfinite(array).all()) or float(array.min()) < -1.0 or float(array.max()) > 1.0:
        raise ValueError("prepared_tensor_nonfinite_or_range:" + name)


def _validate_metadata(record):
    if record.get("schema") != SCHEMA or record.get("category") not in {"tops", "bottoms"}:
        raise ValueError("prepared_manifest_contract")
    if record.get("flat_lay") is not True or record.get("segmentation_free") is not True or record.get("parser_used") is not False:
        raise ValueError("prepared_mode_not_supported")
    _validate_padding(record.get("padding_ltrb"))
    source = record.get("source", {})
    if source.get("revision") != REVISION or not _valid_hash(source.get("manifest_sha256")):
        raise ValueError("prepared_source_provenance")
    files = source.get("files")
    if not isinstance(files, dict) or not files or any(
        not isinstance(name, str) or not name.startswith("src/fashn_vton/") or
        ".." in name.split("/") or "\\" in name or not _valid_hash(value)
        for name, value in files.items()
    ):
        raise ValueError("prepared_source_provenance")
    if record.get("weights") != WEIGHTS:
        raise ValueError("prepared_weight_provenance")
    inputs = record.get("inputs", {})
    if set(inputs) != {"person", "garment"}:
        raise ValueError("prepared_input_provenance")
    for entry in inputs.values():
        if not isinstance(entry, dict) or not _valid_hash(entry.get("sha256")) or type(entry.get("bytes")) is not int or not 0 < entry["bytes"] <= 8 * 1024 * 1024:
            raise ValueError("prepared_input_provenance")
    archive = record.get("archive", {})
    if archive.get("file") != ARRAYS_NAME or not _valid_hash(archive.get("sha256")) or type(archive.get("bytes")) is not int or not 0 < archive["bytes"] <= MAX_ARCHIVE_BYTES:
        raise ValueError("prepared_archive_contract")
    tensors = record.get("tensors", {})
    if set(tensors) != set(SHAPES):
        raise ValueError("prepared_tensor_keys")
    for name, shape in SHAPES.items():
        entry = tensors[name]
        if not isinstance(entry, dict) or entry.get("shape") != list(shape) or entry.get("dtype") != ("int64" if name == "garment_categories" else "float32") or not _valid_hash(entry.get("sha256")):
            raise ValueError("prepared_tensor_manifest:" + name)


def load_prepared(folder: Path):
    """Return (CPU tensors, metadata) only after fixed contract + hashes pass.

    No path from JSON is opened. NPZ members are bounded before NumPy parsing,
    and allow_pickle=False forbids object-array execution. Hashes detect
    corruption, not malicious substitution of both an artifact and its manifest.
    """
    import numpy as np
    import torch
    folder = Path(folder).resolve(strict=True)
    manifest_path = _local_file(folder, MANIFEST_NAME)
    if not 0 < manifest_path.stat().st_size <= MAX_MANIFEST_BYTES:
        raise ValueError("prepared_manifest_size")
    record = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(record, dict):
        raise ValueError("prepared_manifest_contract")
    _validate_metadata(record)
    archive_path = _local_file(folder, ARRAYS_NAME)
    if archive_path.stat().st_size != record["archive"]["bytes"] or sha256(archive_path) != record["archive"]["sha256"]:
        raise ValueError("prepared_archive_hash_or_size")
    with zipfile.ZipFile(archive_path) as archive:
        entries = archive.infolist()
        expected_names = {name + ".npy" for name in SHAPES}
        if len(entries) != len(SHAPES) or {entry.filename for entry in entries} != expected_names:
            raise ValueError("prepared_archive_members")
        for entry in entries:
            name = entry.filename[:-4]
            nbytes = int(np.prod(SHAPES[name])) * (8 if name == "garment_categories" else 4)
            if entry.flag_bits & 1 or entry.compress_type != zipfile.ZIP_STORED or entry.file_size > nbytes + 4096 or entry.file_size < nbytes:
                raise ValueError("prepared_archive_member_size")
            # Validate the NPY allocation request before np.load can allocate it.
            with archive.open(entry) as member:
                version = np.lib.format.read_magic(member)
                if version == (1, 0):
                    shape, fortran, dtype = np.lib.format.read_array_header_1_0(member, max_header_size=4096)
                elif version == (2, 0):
                    shape, fortran, dtype = np.lib.format.read_array_header_2_0(member, max_header_size=4096)
                else:
                    raise ValueError("prepared_numpy_version")
                expected_dtype = np.dtype("int64" if name == "garment_categories" else "float32")
                if tuple(shape) != SHAPES[name] or fortran or dtype != expected_dtype or dtype.hasobject:
                    raise ValueError("prepared_numpy_header_contract")
                if member.tell() + nbytes != entry.file_size:
                    raise ValueError("prepared_numpy_payload_size")
    tensors = {}
    with np.load(archive_path, allow_pickle=False) as values:
        for name in SHAPES:
            array = values[name]
            _validate_array(name, array, record["category"])
            if _hash_bytes(array.tobytes(order="C")) != record["tensors"][name]["sha256"]:
                raise ValueError("prepared_tensor_hash:" + name)
            tensors[name] = torch.from_numpy(array)
    return tensors, record


def save_prepared(folder: Path, tensors: dict, padding, provenance: dict):
    """Write new files in an already unique Evidence folder, then reopen bit-exact."""
    import numpy as np
    folder = Path(folder).resolve(strict=True)
    if set(tensors) != set(SHAPES):
        raise ValueError("prepared_tensor_keys")
    arrays = {}
    for name, tensor in tensors.items():
        # Reject dtype drift rather than silently 'fixing' preprocessing output.
        array = tensor.detach().cpu().contiguous().numpy()
        _validate_array(name, array, provenance["category"])
        arrays[name] = array
    record = {**provenance, "schema": SCHEMA, "padding_ltrb": _validate_padding(padding),
              "flat_lay": True, "segmentation_free": True, "parser_used": False,
              "tensors": {name: {"shape": list(array.shape), "dtype": str(array.dtype),
                         "sha256": _hash_bytes(array.tobytes(order="C"))} for name, array in arrays.items()}}
    archive_path = folder / ARRAYS_NAME
    manifest_path = folder / MANIFEST_NAME
    if archive_path.exists() or manifest_path.exists():
        raise FileExistsError("prepared_evidence_already_exists")
    with archive_path.open("xb") as stream:
        np.savez(stream, **arrays)
    record["archive"] = {"file": ARRAYS_NAME, "bytes": archive_path.stat().st_size, "sha256": sha256(archive_path)}
    _validate_metadata(record)
    with manifest_path.open("x", encoding="utf-8") as stream:
        json.dump(record, stream, indent=2)
    restored, metadata = load_prepared(folder)
    for name, expected in arrays.items():
        # Byte comparison also distinguishes signed zero and is stricter than allclose.
        if restored[name].numpy().tobytes(order="C") != expected.tobytes(order="C"):
            raise ValueError("prepared_reopen_not_bitexact:" + name)
    return metadata


def cli(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-dir", "source-manifest", "weights-dir", "person", "garment", "output-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--category", choices=("tops", "bottoms"), default="tops")
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--confirm-flat-lay", action="store_true")
    parser.add_argument("--approved-nonpersonal-inputs", action="store_true")
    args = parser.parse_args(argv)
    if not args.confirm_flat_lay or not args.approved_nonpersonal_inputs:
        parser.error("explicit approved nonpersonal flat-lay inputs are required")
    if not 1 <= args.threads <= 8:
        parser.error("threads must be between 1 and 8")
    args.dtype = "float32"
    evidence = Evidence(args.output_dir)
    try:
        evidence.event("source_verification")
        verify_source(args.source_dir, args.source_manifest)
        source_record = json.loads(args.source_manifest.read_text(encoding="utf-8-sig"))
        evidence.event("weight_verification")
        verify_weights(args.weights_dir, tuple(WEIGHTS))
        input_records = {name: {"sha256": sha256(getattr(args, name)), "bytes": getattr(args, name).stat().st_size}
                         for name in ("person", "garment")}
        with offline_network_guard():
            namespace_source(args.source_dir)
            import torch
            torch.set_num_threads(args.threads)
            torch.set_num_interop_threads(1)
            tensors, pad = prepare_real_inputs(args, evidence)
            # Refuse input changes during pose preparation rather than binding stale hashes.
            for name, entry in input_records.items():
                if sha256(getattr(args, name)) != entry["sha256"]:
                    raise ValueError("input_changed_during_preparation")
            evidence.event("prepared_serialization")
            record = save_prepared(evidence.folder, tensors, pad.pad_fn.padding_mem,
                {"category": args.category, "inputs": input_records, "weights": dict(WEIGHTS),
                 "source": {"revision": REVISION, "manifest_sha256": sha256(args.source_manifest),
                            "files": source_record["files"]}})
            evidence.event("prepared_reopened_bitexact", archive_sha256=record["archive"]["sha256"],
                           category=args.category, padding_ltrb=record["padding_ltrb"], tensors=record["tensors"])
            evidence.finish(success=True, completed_stage="real_pose_and_fp32_preprocessing",
                            preprocessing_bitexact=True, model_loaded=False, inference_complete=False,
                            png_created=False, archive_sha256=record["archive"]["sha256"])
        return 0
    except Exception as error:
        failed_stage = evidence.stage
        evidence.event("failure", failed_stage=failed_stage, exception_type=type(error).__name__)
        evidence.finish(success=False, failed_stage=failed_stage, exception_type=type(error).__name__,
                        model_loaded=False, inference_complete=False, png_created=False)
        return 1


if __name__ == "__main__":
    raise SystemExit(cli())
