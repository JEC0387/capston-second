"""Transient stage disclosure only; no external network or AI inference."""
from copy import deepcopy
from types import SimpleNamespace
import pytest
from outfit_vton_api import OutfitService, verified_provider_progress

REV = 'genlook-paired-v1-20261106-' + 'a'*16
GOOD = {'mode':'clothing-pair-then-shoes','stage_index':1,'stage_count':2,'phase':'waiting_for_generation'}


@pytest.mark.parametrize('bad', [GOOD | {'stage_index': True}, GOOD | {'stage_index':0},
    GOOD | {'stage_count':3}, GOOD | {'phase':'finalizing'}, GOOD | {'phase':'private_error'},
    GOOD | {'mode':'sequential-3'}, GOOD | {'generation_id':'private'}])
def test_invalid_progress_is_not_disclosed(bad):
    with pytest.raises(ValueError): verified_provider_progress(bad, REV)


def test_legacy_and_pair_counts_are_bound_to_captured_revision():
    assert verified_provider_progress(GOOD,REV)==GOOD
    last=GOOD | {'stage_index':2,'phase':'finalizing'}
    assert verified_provider_progress(last,REV)==last
    seq=GOOD | {'mode':'sequential-3','stage_count':3,'stage_index':3,'phase':'finalizing'}
    assert verified_provider_progress(seq,'genlook-sequential-v2-preservation-20261106-abc')==seq
    with pytest.raises(ValueError): verified_provider_progress(GOOD,'unknown')


def test_progress_is_transient_hides_terminal_and_never_changes_journal():
    job={'request_id':'synthetic-job','owner':'private-owner','input_sha256':'b'*64,
         'model_id':'genlook/tryon-api-v1','model_revision':REV,'status':'running'}
    snapshots=[]
    def getter(identity,digest):
        snapshots.append((identity,digest));return GOOD
    service=SimpleNamespace(provider=SimpleNamespace(public_progress=getter))
    before=deepcopy(job)
    view=OutfitService.view(service,job)
    assert view['provider_progress']==GOOD and 'owner' not in view and job==before
    for state in ('queued','cancelling','cancelled','failed','completed','interrupted','timeout'):
        assert 'provider_progress' not in OutfitService.view(service,job | {'status':state})
    assert len(snapshots)==1
    service.provider.public_progress=lambda *_: GOOD | {'url':'private'}
    assert 'provider_progress' not in OutfitService.view(service,job)
    service.provider=SimpleNamespace() # P48 provider has no optional interface.
    assert 'provider_progress' not in OutfitService.view(service,job)
