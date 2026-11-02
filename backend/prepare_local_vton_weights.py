"""Explicit official-only preparation; no upload, paid API or inference. Run on the GPU owner's machine."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights-dir", type=Path, required=True)
    parser.add_argument("--accept-parser-research-license", action="store_true")
    args = parser.parse_args()
    if not args.accept_parser_research_license:
        parser.error("Review the FASHN Human Parser / NVIDIA SegFormer research-only license first")
    from huggingface_hub import HfApi, hf_hub_download
    args.weights_dir.mkdir(parents=True, exist_ok=True)
    result = {"files": {}, "sources": []}
    for repo, names, subdir in [("fashn-ai/fashn-vton-1.5", ["model.safetensors"], ""),
            ("fashn-ai/DWPose", ["yolox_l.onnx", "dw-ll_ucoco_384.onnx"], "dwpose")]:
        info = HfApi().model_info(repo, files_metadata=True)
        result["sources"].append({"repo": repo, "revision": info.sha})
        for name in names:
            sibling = next(item for item in info.siblings if item.rfilename == name)
            expected = sibling.lfs.sha256 if sibling.lfs else None
            if not expected:
                raise RuntimeError("Official file has no LFS SHA-256; do not accept an unverified download")
            path = Path(hf_hub_download(repo, name, revision=info.sha, local_dir=args.weights_dir / subdir))
            digest = hashlib.sha256()
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise RuntimeError("Official weight checksum mismatch")
            result["files"][f"{subdir}/{name}".lstrip("/")] = expected
    # Official parser package prepares its own Hugging Face cache. Server workers use offline mode.
    from fashn_human_parser import FashnHumanParser
    FashnHumanParser(device="cpu")
    (args.weights_dir / "vton.manifest.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print("Official weights and parser cache prepared; no inference performed")


if __name__ == "__main__":
    main()
