"""Synthetic bytes and fake loaders only; not licensed model assets or GPU evidence."""
import hashlib
import json
from pathlib import Path
import sys
from types import SimpleNamespace

from PIL import Image
import pytest
from local_vton import LocalVtonError, run_inference
from local_vton_parser import (ParserIntegrityError, verify_parser_assets, verify_runtime_revisions,
                               _pipeline_class, MODEL_COMMIT, PARSER_COMMIT)
from .vton_parser_fixtures import parser_fixture


def base_inputs(tmp_path):
    weights = tmp_path / 'weights'
    names = ('model.safetensors', 'dwpose/yolox_l.onnx', 'dwpose/dw-ll_ucoco_384.onnx')
    for name in names:
        path = weights / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'synthetic VTON contract fixture')
    (weights / 'vton.manifest.json').write_text(json.dumps({'files': {
        name: hashlib.sha256((weights / name).read_bytes()).hexdigest() for name in names}}))
    for name in ('person.png', 'garment.png'):
        Image.new('RGB', (256, 384), 'white').save(tmp_path / name)
    return dict(person=tmp_path/'person.png', garment=tmp_path/'garment.png', weights_dir=weights,
                output=tmp_path/'result.png', category='tops', device='cpu', allow_cpu=True)


def test_dry_run_rejects_missing_parser_manifest(tmp_path):
    with pytest.raises(LocalVtonError, match='parser'):
        run_inference(**base_inputs(tmp_path), dry_run=True)


def test_server_cannot_report_ready_from_only_three_main_weights(tmp_path, monkeypatch):
    from local_vton_server import LocalSettings, SubprocessEngine
    inputs = base_inputs(tmp_path)
    monkeypatch.setattr('local_vton_server.importlib.util.find_spec', lambda _: object())
    monkeypatch.setitem(sys.modules, 'torch', SimpleNamespace())
    engine = SubprocessEngine(LocalSettings(enabled=True, weights=str(inputs['weights_dir']),
                                            device='cpu', allow_cpu=True))
    assert engine.reason == 'parser_integrity_unavailable'


@pytest.fixture
def bundle(tmp_path):
    inputs = base_inputs(tmp_path)
    parser_fixture(inputs['weights_dir'])
    return inputs['weights_dir']


def update_manifest(bundle, change):
    path = bundle/'parser.manifest.json'
    value = json.loads(path.read_text())
    change(value)
    path.write_text(json.dumps(value))


def test_valid_operator_bundle_remains_local_and_main_manifest_compatible(bundle):
    from local_vton import verify_weight_manifest
    verify_weight_manifest(bundle)
    assert verify_parser_assets(bundle) == bundle/'human-parser'


@pytest.mark.parametrize('name', ['human-parser/config.json', 'human-parser/model.safetensors',
                                  'licenses/human-parser.txt', 'licenses/segformer.txt'])
def test_each_missing_or_tampered_dependency_fails_closed(bundle, name):
    path = bundle/name
    original = path.read_bytes()
    path.write_bytes(original+b'changed')
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)
    path.unlink()
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)


@pytest.mark.parametrize('field,value', [('reviewed', False), ('reviewed', 'true'),
                                       ('purpose', 'commercial'), ('sources', {})])
def test_license_approval_is_component_specific_and_not_inferred(bundle, field, value):
    update_manifest(bundle, lambda m: m['license_review'].__setitem__(field, value))
    with pytest.raises(ParserIntegrityError, match='license'): verify_parser_assets(bundle)


@pytest.mark.parametrize('field,value', [('model_repo','untrusted/model'), ('model_revision','main'),
                                       ('files', {'../outside': '0'*64})])
def test_unpinned_or_outside_bundle_manifest_is_rejected(bundle, field, value):
    update_manifest(bundle, lambda m: m.__setitem__(field, value))
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)


@pytest.mark.parametrize('name', ['pytorch_model.bin', 'model.safetensors.index.json', 'remote.py'])
def test_unlisted_loading_alternatives_rejected(bundle, name):
    (bundle/'human-parser'/name).write_bytes(b'synthetic extra')
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)


@pytest.mark.parametrize('field,value', [('model_type','custom'), ('auto_map',{}),
                                       ('quantization_config',{}), ('architectures',['RemoteModel'])])
def test_config_cannot_redirect_model_loading_even_with_matching_hash(bundle, field, value):
    path = bundle/'human-parser/config.json'
    config = json.loads(path.read_text()); config[field]=value; path.write_text(json.dumps(config))
    update_manifest(bundle, lambda m: m['files'].__setitem__('human-parser/config.json',
                                                           hashlib.sha256(path.read_bytes()).hexdigest()))
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)


def test_pointer_is_not_a_prepared_parser_even_if_hash_matches(bundle):
    path = bundle/'human-parser/model.safetensors'
    path.write_bytes(b'version https://git-lfs.github.com/spec/v1')
    update_manifest(bundle, lambda m: m['files'].__setitem__('human-parser/model.safetensors',
                                                           hashlib.sha256(path.read_bytes()).hexdigest()))
    with pytest.raises(ParserIntegrityError): verify_parser_assets(bundle)


def test_pinned_package_sources_are_required(monkeypatch):
    commits = {'fashn-vton':MODEL_COMMIT, 'fashn-human-parser':PARSER_COMMIT}
    repos = {'fashn-vton':'fashn-vton-1.5', 'fashn-human-parser':'fashn-human-parser'}
    def distribution(name):
        return SimpleNamespace(read_text=lambda _: json.dumps({'url':'https://github.com/fashn-AI/'+repos[name]+'.git',
                                                               'vcs_info': {'commit_id':commits[name]}}))
    monkeypatch.setattr('local_vton_parser.importlib.metadata.distribution', distribution)
    verify_runtime_revisions()
    commits['fashn-human-parser']='0'*40
    with pytest.raises(ParserIntegrityError,match='revision'): verify_runtime_revisions()
    commits['fashn-human-parser']=PARSER_COMMIT;repos['fashn-human-parser']='other-repo'
    with pytest.raises(ParserIntegrityError,match='revision'): verify_runtime_revisions()


def test_adapter_uses_exact_local_bundle_and_no_remote_or_pickle_fallback(bundle):
    calls=[]
    model=SimpleNamespace(to=lambda device:calls.append(('device',device)), eval=lambda:calls.append(('eval',)))
    class Segformer:
        @classmethod
        def from_pretrained(cls, model_id, **options):
            calls.append((model_id,options));return model
    class Parser:
        def __init__(self, model_id='remote/repo', device=None):
            pytest.fail('Upstream ambient-cache constructor must not run')
        def predict(self, image): return image
    class Pipeline:
        def __init__(self, **kwargs):
            self.device=SimpleNamespace(type='cpu');self._setup_hp_model()
        def _setup_hp_model(self): pytest.fail('Unverified parser hook must not run')
    adapted=_pipeline_class(Pipeline,Parser,Segformer,verify_parser_assets(bundle))
    loaded=adapted()
    assert calls[0] == (str(bundle/'human-parser'),
                        {'local_files_only':True,'use_safetensors':True,'trust_remote_code':False})
    assert loaded.hp_model.predict('synthetic') == 'synthetic'
    assert Pipeline._setup_hp_model.__name__ == '_setup_hp_model'  # No global monkey patch.


def test_changed_upstream_hook_contract_is_rejected(bundle):
    class Pipeline:
        def _setup_hp_model(self, unexpected): pass
    class Parser:
        def __init__(self,model_id=None,device=None): pass
    with pytest.raises(ParserIntegrityError,match='contract'):
        _pipeline_class(Pipeline,Parser,object,bundle/'human-parser')


def test_worker_rechecks_parser_before_importing_or_loading_model(tmp_path, monkeypatch):
    import local_vton_worker as worker
    inputs=base_inputs(tmp_path);parser_fixture(inputs['weights_dir'])
    (inputs['weights_dir']/'human-parser/model.safetensors').write_bytes(b'modified after startup')
    (tmp_path/'request.json').write_text(json.dumps({'parent_pid':1,'weights_dir':str(inputs['weights_dir'])}))
    monkeypatch.setattr(sys,'argv',['worker',str(tmp_path)])
    monkeypatch.setattr(worker.threading,'Thread',lambda **_:SimpleNamespace(start=lambda:None))
    monkeypatch.setattr(worker,'load_verified_pipeline',lambda **_:pytest.fail('No loading on parser mismatch'))
    with pytest.raises(ParserIntegrityError):worker.main()
    assert not (tmp_path/'result.png').exists()


def test_offline_parser_cli_reports_missing_setup_without_private_path(tmp_path, monkeypatch, capsys):
    import local_vton_parser as parser
    monkeypatch.setattr(sys,'argv',['parser','--weights-dir',str(tmp_path/'private-operator-path')])
    assert parser.main() == 2
    output=capsys.readouterr().out
    assert 'private-operator-path' not in output
    assert json.loads(output) == {'ready':False,'reason':'parser_manifest_missing_or_mismatched','generated':False}


def test_offline_parser_cli_success_is_not_model_execution(bundle, monkeypatch, capsys):
    import local_vton_parser as parser
    monkeypatch.setattr(sys,'argv',['parser','--weights-dir',str(bundle)])
    monkeypatch.setattr(parser,'verify_runtime_revisions',lambda:None)
    assert parser.main() == 0
    output=json.loads(capsys.readouterr().out)
    assert output['assets_and_revisions_verified'] is True
    assert output['model_loaded'] is False and output['generated'] is False
    assert output['license_legally_authorized'] == 'operator_review_required'
