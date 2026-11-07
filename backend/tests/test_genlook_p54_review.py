"""Synthetic HTTP evidence for the operator gate, never real generation quality."""
import asyncio
import hashlib
import json
from uuid import uuid4

import httpx
import pytest

import genlook_outfit_provider as mod
from tests.test_genlook_p51_pair import PairService, images, placements, settings


async def waiting(provider, tmp_path, identity):
    directory = tmp_path / "pair-ledger" / "reviews" / identity
    for _ in range(100):
        if (directory / "request.json").exists():
            return directory, json.loads((directory / "request.json").read_text())
        await asyncio.sleep(.01)
    raise AssertionError("review request not reached")


def decide(directory, binding, action):
    temporary = directory / "decision.next"
    temporary.write_text(json.dumps(binding | {"decision": action}))
    temporary.replace(directory / "decision.json")


def test_approve_bound_pair_proceeds_once_and_preserves_budget_and_restart(tmp_path):
    async def check():
        fake = PairService()
        config = settings(tmp_path, max_total_credits=3, max_total_microusd=120000)
        provider = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        identity = str(uuid4())
        task = asyncio.create_task(provider.generate(identity, "a" * 64, images(), placements()))
        try:
            directory, binding = await waiting(provider, tmp_path, identity)
            assert len(fake.posts) == 1 and fake.balance == 7
            assert binding["job_id"] == identity and binding["input_sha256"] == "a" * 64
            assert binding["model_revision"] == provider.revision
            assert binding["result_sha256"] == hashlib.sha256((directory / "intermediate.png").read_bytes()).hexdigest()
            assert (directory / "intermediate.png").read_bytes() == mod.verified_png(fake.outputs[0])
            progress = provider.public_progress(identity, "a" * 64)
            assert progress == {"mode": mod.PAIRED_MODE, "stage_index": 1, "stage_count": 2, "phase": "validating_result"}
            await asyncio.sleep(.25)
            assert len(fake.posts) == 1, "no shoes without explicit decision"
            decide(directory, binding, "approve")
            result = await task
            assert result.png == mod.verified_png(fake.outputs[1]) and len(fake.posts) == 2
            assert fake.balance == 6
            assert provider._ledger["jobs"][identity]["credits_reserved"] == 3
            assert (await provider.check_availability()).reason == "genlook_budget_exhausted"
        finally:
            if not task.done(): task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            await provider.close()
        calls = len(fake.calls)
        restored = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        try:
            assert (await restored.check_availability()).reason == "genlook_budget_exhausted"
            assert len(fake.calls) == calls
        finally:
            await restored.close()
    asyncio.run(check())


@pytest.mark.parametrize("failure", ["reject", "wrong_job", "wrong_input", "wrong_result", "wrong_model", "wrong_nonce", "mutated_png", "timeout", "cancel"])
def test_review_failure_never_submits_shoes_or_replays_paid_post(tmp_path, failure):
    async def check():
        fake = PairService()
        provider = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake), review_timeout_seconds=1)
        identity = str(uuid4())
        task = asyncio.create_task(provider.generate(identity, "a" * 64, images(), placements()))
        try:
            directory, binding = await waiting(provider, tmp_path, identity)
            if failure == "cancel":
                await provider.cancel(identity)
            elif failure != "timeout":
                if failure == "wrong_job": binding["job_id"] = str(uuid4())
                if failure == "wrong_input": binding["input_sha256"] = "b" * 64
                if failure == "wrong_result": binding["result_sha256"] = "b" * 64
                if failure == "wrong_model": binding["model_revision"] = "wrong-model"
                if failure == "wrong_nonce": binding["nonce"] = uuid4().hex
                if failure == "mutated_png": (directory / "intermediate.png").write_bytes(mod.verified_png(fake.outputs[1]))
                decide(directory, binding, "reject" if failure == "reject" else "approve")
            if failure == "cancel":
                with pytest.raises(asyncio.CancelledError): await task
            else:
                expected = {"reject": "genlook_review_rejected", "timeout": "genlook_review_timeout"}.get(failure, "genlook_review_invalid")
                with pytest.raises(mod.ProviderFailure, match=expected): await task
            assert len(fake.posts) == 1 and len(fake.uploads) == 1 and fake.balance == 7
            assert len(fake.deletes) == 3, "customer and two created garments cleaned, no uncreated shoe DELETE"
            job = provider._ledger["jobs"][identity]
            assert job["credits_reserved"] == 3
            assert job["stages"][1]["state"] == "unsubmitted"
            assert job["status"] == ("cleanup_failed" if failure == "mutated_png" else
                                     "cancelled" if failure == "cancel" else "failed")
            before = len(fake.posts)
            # Changed review bytes cannot be safely deleted as the owned result;
            # stronger cleanup reconciliation blocks all work, including replay.
            retry_error = "genlook_cleanup_required" if failure == "mutated_png" else "genlook_request_already_recorded"
            with pytest.raises(mod.ProviderFailure, match=retry_error):
                await provider.generate(identity, "a" * 64, images(), placements())
            assert len(fake.posts) == before
        finally:
            if not task.done(): task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            await provider.close()
    asyncio.run(check())


def test_review_opt_in_rejects_invalid_mode_and_time_without_network(tmp_path):
    async def check():
        for value, mode in [(True, mod.PAIRED_MODE), (-1, mod.PAIRED_MODE), (31, mod.PAIRED_MODE), (5, mod.SEQUENTIAL_MODE)]:
            fake = PairService()
            provider = mod.GenlookProvider(settings(tmp_path, mode=mode), transport=httpx.MockTransport(fake), review_timeout_seconds=value)
            try:
                assert (await provider.check_availability()).reason == "genlook_policy_unreviewed"
                assert not fake.calls
            finally:
                await provider.close()
    asyncio.run(check())
