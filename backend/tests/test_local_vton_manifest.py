import hashlib
import json
import pytest
from local_vton import LocalVtonError, check_weights, verify_weight_manifest


def test_integrity_manifest_detects_missing_or_modified_weights(tmp_path):
    names = ['model.safetensors', 'dwpose/yolox_l.onnx', 'dwpose/dw-ll_ucoco_384.onnx']
    for name in names:
        path=tmp_path/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'synthetic test data')
    with pytest.raises(LocalVtonError, match='manifest'):
        verify_weight_manifest(tmp_path)
    (tmp_path/'vton.manifest.json').write_text(json.dumps({'files':{name:hashlib.sha256((tmp_path/name).read_bytes()).hexdigest() for name in names}}))
    verify_weight_manifest(tmp_path)
    (tmp_path/names[0]).write_bytes(b'changed')
    with pytest.raises(LocalVtonError, match='mismatched'):
        verify_weight_manifest(tmp_path)


def test_zero_length_and_download_pointer_are_not_model_weights(tmp_path):
    (tmp_path/'dwpose').mkdir()
    for name in ['model.safetensors','dwpose/yolox_l.onnx','dwpose/dw-ll_ucoco_384.onnx']:
        (tmp_path/name).write_bytes(b'x')
    (tmp_path/'model.safetensors').write_bytes(b'')
    with pytest.raises(LocalVtonError):check_weights(tmp_path)
    (tmp_path/'model.safetensors').write_bytes(b'version https://git-lfs.github.com/spec/v1')
    with pytest.raises(LocalVtonError,match='pointer'):check_weights(tmp_path)


def test_official_only_downloader_pins_revision_and_verifies_hashes_without_network(tmp_path,monkeypatch):
    import sys
    from types import SimpleNamespace
    import prepare_local_vton_weights as setup
    data=b'synthetic weights for contract testing only'
    expected=hashlib.sha256(data).hexdigest()
    observed=[]
    class Api:
        def model_info(self,repo,files_metadata):
            assert files_metadata
            names=['model.safetensors'] if repo=='fashn-ai/fashn-vton-1.5' else ['yolox_l.onnx','dw-ll_ucoco_384.onnx']
            return SimpleNamespace(sha='a'*40,siblings=[SimpleNamespace(rfilename=name,lfs=SimpleNamespace(sha256=expected)) for name in names])
    def download(repo,name,revision,local_dir):
        assert repo in ('fashn-ai/fashn-vton-1.5','fashn-ai/DWPose') and revision=='a'*40
        observed.append((repo,name));local_dir.mkdir(parents=True,exist_ok=True)
        path=local_dir/name;path.write_bytes(data);return str(path)
    monkeypatch.setitem(sys.modules,'huggingface_hub',SimpleNamespace(HfApi=Api,hf_hub_download=download))
    monkeypatch.setitem(sys.modules,'fashn_human_parser',SimpleNamespace(FashnHumanParser=lambda device:pytest.fail('No unverified parser auto-download')))
    monkeypatch.setattr(sys,'argv',['setup','--weights-dir',str(tmp_path),'--accept-parser-research-license'])
    setup.main()
    verify_weight_manifest(tmp_path)
    assert len(observed)==3
    assert len(json.loads((tmp_path/'vton.manifest.json').read_text())['sources'])==2


def test_downloader_requires_explicit_license_review(tmp_path,monkeypatch):
    import sys
    import prepare_local_vton_weights as setup
    monkeypatch.setattr(sys,'argv',['setup','--weights-dir',str(tmp_path/'not_created')])
    with pytest.raises(SystemExit):setup.main()
    assert not (tmp_path/'not_created').exists()
