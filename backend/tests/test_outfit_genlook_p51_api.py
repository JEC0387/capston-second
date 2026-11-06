"""P51 mode-bound API receipt and restart tests; never external generation."""
from dataclasses import replace
import pytest
from fastapi.testclient import TestClient

from outfit_vton_api import create_app
from backend.tests.test_outfit_genlook_integration import RemoteFixture, submission
from backend.tests.test_outfit_vton_api import settings, test_user, post, finish, status

MODE = "clothing-pair-then-shoes"
REVISION = "genlook-paired-v1-20261106-" + "a" * 16


def paired():
    provider = RemoteFixture()
    provider.available = replace(provider.available, model_revision=REVISION,
        provider_info=provider.available.provider_info | {"mode": MODE})
    provider.receipt = provider.receipt | {"mode": MODE, "generation_ids": ["pair-generation", "shoes-generation"]}
    return provider


def test_two_request_receipt_result_restart_and_owner_are_bound(tmp_path):
    provider = paired(); config = settings(tmp_path); request = submission(provider)
    with TestClient(create_app(config, provider, test_user), base_url="https://testserver") as client:
        public = client.get("/api/outfit-vton/config", headers={"Authorization": "Bearer synthetic-a"}).json()
        assert public['ready'] and public['provider_info']['mode'] == MODE
        assert post(client, request).status_code == 202
        result = finish(client, request['request_id'])
        assert result['status'] == 'completed' and result['source']['generation_ids'] == ['pair-generation', 'shoes-generation']
        assert result['quality'] == 'NOT_EVALUATED'
        assert post(client, request).status_code == 202 and provider.calls == 1
        assert status(client, request['request_id'], 'synthetic-b').status_code == 404
        path = '/api/outfit-vton/jobs/' + request['request_id'] + '/result'
        image = client.get(path, headers={'Authorization': 'Bearer synthetic-a'})
        assert image.status_code == 200 and image.content.startswith(b'\x89PNG\r\n\x1a\n')
    # Current provider mode must not reinterpret an old completed paired job.
    fresh = RemoteFixture()
    with TestClient(create_app(config, fresh, test_user), base_url='https://testserver') as client:
        restored = status(client, request['request_id']).json()
        assert restored['status'] == 'completed' and restored['source']['mode'] == MODE
        assert client.get(path, headers={'Authorization': 'Bearer synthetic-a'}).content == image.content
        assert fresh.calls == 0


@pytest.mark.parametrize('ids', [['same', 'same'], ['one'], ['one', 'two', 'three']])
def test_paired_receipt_requires_two_distinct_generation_ids(tmp_path, ids):
    provider = paired(); provider.receipt['generation_ids'] = ids
    request = submission(provider)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url='https://testserver') as client:
        assert post(client, request).status_code == 202
        result = finish(client, request['request_id'])
        assert result['status'] == 'failed' and not result['result_available']


def test_valid_sequential_receipt_cannot_complete_paired_job(tmp_path):
    provider = paired(); provider.receipt = RemoteFixture().receipt
    request = submission(provider)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url='https://testserver') as client:
        assert post(client, request).status_code == 202
        assert finish(client, request['request_id'])['status'] == 'failed'


@pytest.mark.parametrize('revision', ['genlook-sequential-v2-preservation-20261106-' + 'a'*16, 'unknown'])
def test_wrong_revision_disclosure_blocks_before_input(tmp_path, revision):
    provider = paired(); provider.available = replace(provider.available, model_revision=revision)
    with TestClient(create_app(settings(tmp_path), provider, test_user), base_url='https://testserver') as client:
        public = client.get('/api/outfit-vton/config', headers={'Authorization': 'Bearer synthetic-a'}).json()
        assert not public['ready'] and public['reason'] == 'invalid_model_contract'
        assert post(client, submission(provider)).status_code == 503 and provider.calls == 0
