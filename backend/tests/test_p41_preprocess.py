"""Synthetic persistence tests only; no real weights, DWPose or generation runs."""
import hashlib
import io
import json
import zipfile

import pytest

import p41_preprocess as prep


@pytest.fixture
def tensors():
    torch = pytest.importorskip("torch")
    values = {name: torch.zeros(shape, dtype=torch.float32) for name, shape in prep.SHAPES.items()
              if name != "garment_categories"}
    values["ca_images"][0, 0, 0, 0] = -1.0
    values["garment_images"][0, 2, 10, 20] = 1.0
    values["person_poses"][0, 0, 8, 9] = 0.5
    values["garment_categories"] = torch.tensor([1], dtype=torch.int64)
    return values


def provenance(category="tops"):
    return {"category": category, "inputs": {key: {"sha256": "a" * 64, "bytes": 100}
        for key in ("person", "garment")}, "weights": dict(prep.WEIGHTS),
        "source": {"revision": prep.REVISION, "manifest_sha256": "b" * 64,
                   "files": {"src/fashn_vton/example.py": "c" * 64}}}


def save(tmp_path, tensors):
    return prep.save_prepared(tmp_path, tensors, [72, 0, 72, 0], provenance())


def alter_manifest(tmp_path, change):
    path = tmp_path / prep.MANIFEST_NAME
    record = json.loads(path.read_text())
    change(record)
    path.write_text(json.dumps(record))


def test_fp32_exact_reopen_and_independent_known_pixels(tmp_path, tensors):
    save(tmp_path, tensors)
    restored, metadata = prep.load_prepared(tmp_path)
    assert metadata["padding_ltrb"] == [72, 0, 72, 0]
    assert restored["ca_images"][0, 0, 0, 0].item() == -1.0
    assert restored["garment_images"][0, 2, 10, 20].item() == 1.0
    assert restored["person_poses"][0, 0, 8, 9].item() == 0.5
    assert restored["garment_categories"].tolist() == [1]
    for name in tensors:
        assert restored[name].device.type == "cpu"
        assert restored[name].numpy().tobytes() == tensors[name].numpy().tobytes()


def test_existing_artifacts_are_not_overwritten(tmp_path, tensors):
    save(tmp_path, tensors)
    before = {path.name: path.read_bytes() for path in tmp_path.iterdir()}
    with pytest.raises(FileExistsError):
        save(tmp_path, tensors)
    assert {path.name: path.read_bytes() for path in tmp_path.iterdir()} == before


@pytest.mark.parametrize("change,reason", [
    (lambda r: r["archive"].update(file="../other.npz"), "archive_contract"),
    (lambda r: r.update(padding_ltrb=[576, 0, 0, 0]), "invalid_padding"),
    (lambda r: r.update(padding_ltrb=[1, 1, 1, 1]), "invalid_padding"),
    (lambda r: r.update(category="shoes"), "manifest_contract"),
    (lambda r: r["weights"].update({"model.safetensors": "0" * 64}), "weight_provenance"),
    (lambda r: r["tensors"]["ca_images"].update(sha256="0" * 64), "tensor_hash"),
    (lambda r: r["tensors"].pop("person_poses"), "tensor_keys"),
])
def test_tampered_metadata_is_rejected(tmp_path, tensors, change, reason):
    save(tmp_path, tensors)
    alter_manifest(tmp_path, change)
    with pytest.raises(ValueError, match=reason):
        prep.load_prepared(tmp_path)


def test_changed_archive_rejected_before_numpy_load(tmp_path, tensors, monkeypatch):
    import numpy as np
    save(tmp_path, tensors)
    with (tmp_path / prep.ARRAYS_NAME).open("ab") as stream:
        stream.write(b"unexpected")
    monkeypatch.setattr(np, "load", lambda *_a, **_k: pytest.fail("hash must be checked before parsing"))
    with pytest.raises(ValueError, match="archive_hash_or_size"):
        prep.load_prepared(tmp_path)


@pytest.mark.parametrize("bad", ["nan", "range", "dtype", "shape", "category"])
def test_noncanonical_tensor_contract_rejected(tmp_path, tensors, bad):
    torch = pytest.importorskip("torch")
    if bad == "nan":
        tensors["ca_images"][0, 0, 0, 0] = float("nan")
    elif bad == "range":
        tensors["ca_images"][0, 0, 0, 0] = 1.001
    elif bad == "dtype":
        tensors["ca_images"] = tensors["ca_images"].to(torch.bfloat16)
    elif bad == "shape":
        tensors["person_poses"] = torch.zeros((1, 864, 576))
    else:
        tensors["garment_categories"][0] = 2
    with pytest.raises((ValueError, TypeError)):
        save(tmp_path, tensors)


def test_archive_traversal_member_rejected_without_extracting(tmp_path, tensors):
    save(tmp_path, tensors)
    target = tmp_path / prep.ARRAYS_NAME
    with zipfile.ZipFile(target, "a") as archive:
        archive.writestr("../escaped.npy", b"not allowed")
    alter_manifest(tmp_path, lambda r: r["archive"].update(bytes=target.stat().st_size,
        sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    with pytest.raises(ValueError, match="archive_members"):
        prep.load_prepared(tmp_path)
    assert not (tmp_path.parent / "escaped.npy").exists()


def test_numpy_pickle_is_explicitly_disabled(tmp_path, tensors, monkeypatch):
    import numpy as np
    save(tmp_path, tensors)
    original_load = np.load
    seen = []
    def guarded_load(*args, **kwargs):
        seen.append(kwargs.get("allow_pickle"))
        return original_load(*args, **kwargs)
    monkeypatch.setattr(np, "load", guarded_load)
    prep.load_prepared(tmp_path)
    assert seen == [False]


def test_npy_allocation_shape_is_checked_before_numpy_load(tmp_path, tensors, monkeypatch):
    import numpy as np
    save(tmp_path, tensors)
    target = tmp_path / prep.ARRAYS_NAME
    with zipfile.ZipFile(target) as archive:
        members = {entry.filename: archive.read(entry) for entry in archive.infolist()}
    header = io.BytesIO()
    np.lib.format.write_array_header_1_0(header, {"descr": "<f4", "fortran_order": False,
                                               "shape": (1, 3, 864, 576000)})
    members["ca_images.npy"] = header.getvalue() + bytes(1 * 3 * 864 * 576 * 4)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, value in members.items():
            archive.writestr(name, value)
    alter_manifest(tmp_path, lambda r: r["archive"].update(bytes=target.stat().st_size,
        sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    monkeypatch.setattr(np, "load", lambda *_a, **_k: pytest.fail("header rejected before allocation"))
    with pytest.raises(ValueError, match="numpy_header_contract"):
        prep.load_prepared(tmp_path)


def test_cli_requires_explicit_input_authorization_before_creating_files(tmp_path):
    args = sum((["--" + key, str(tmp_path / key)] for key in
                ("source-dir", "source-manifest", "weights-dir", "person", "garment", "output-dir")), [])
    with pytest.raises(SystemExit):
        prep.cli(args)
    assert list(tmp_path.iterdir()) == []
