import hashlib
import json
import pytest
from p41_infer import parse_args, validate_prepared_source


def required():
    return [item for flag in ('source-dir','source-manifest','weights-dir','prepared-dir','output-dir') for item in ('--'+flag,'placeholder')]


@pytest.mark.parametrize('steps',[1,2,4])
def test_short_schedule_is_explicit_diagnostic_only(steps):
    with pytest.raises(SystemExit): parse_args(required()+['--steps',str(steps)])
    assert parse_args(required()+['--steps',str(steps),'--diagnostic']).diagnostic


def test_mixed_preprocessing_implementation_is_not_silently_accepted(tmp_path):
    path=tmp_path/'manifest.json'
    path.write_text(json.dumps({'files':{'source.py':'a'*64}}))
    metadata={'source':{'manifest_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'files':{'source.py':'a'*64}}}
    validate_prepared_source(metadata,path)
    metadata['source']['files']['source.py']='b'*64
    with pytest.raises(ValueError,match='does_not_match'): validate_prepared_source(metadata,path)


@pytest.mark.parametrize('extra',[
    ['--steps','1','--diagnostic','--resume','old'],
    ['--steps','20','--diagnostic','--stop-after-step','1'],
    ['--steps','20','--stop-after-step','20'],
    ['--steps','20','--stop-after-step','0'],
])
def test_diagnostic_and_final_states_cannot_be_resumed_as_standard(extra):
    with pytest.raises(SystemExit): parse_args(required()+extra)


def test_standard_chunk_retains_full_schedule():
    args=parse_args(required()+['--steps','20','--stop-after-step','1'])
    assert args.steps==20 and args.stop_after_step==1 and not args.diagnostic
