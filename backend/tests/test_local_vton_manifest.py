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
