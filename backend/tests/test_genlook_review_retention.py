"""Synthetic private review retention only; no external requests or real photos."""
import asyncio
import hashlib
import json
import time
from pathlib import Path
from uuid import uuid4

import httpx
import pytest

import genlook_outfit_provider as mod
from tests.test_genlook_p51_pair import PairService, images, placements, settings
from tests.test_genlook_p54_review import decide, waiting


OWNER_FILE = ".intermediate-owner-v1.json"


@pytest.mark.parametrize("outcome", ["complete", "reject", "timeout", "cancel", "invalid_decision"])
def test_new_intermediate_is_removed_after_every_review_exit(tmp_path, outcome):
    async def check():
        fake = PairService()
        provider = mod.GenlookProvider(settings(tmp_path), transport=httpx.MockTransport(fake),
                                      review_timeout_seconds=1)
        identity = str(uuid4())
        task = asyncio.create_task(provider.generate(identity, "a" * 64, images(), placements()))
        try:
            directory, binding = await waiting(provider, tmp_path, identity)
            assert (directory / "intermediate.png").is_file()
            unrelated = directory / "operator-note.txt"
            unrelated.write_text("synthetic note must remain")
            if outcome == "cancel":
                await provider.cancel(identity)
            elif outcome != "timeout":
                if outcome == "invalid_decision":
                    binding = binding | {"input_sha256": "b" * 64}
                decide(directory, binding, "reject" if outcome == "reject" else "approve")
            if outcome == "complete":
                result = await task
                assert result.png == mod.verified_png(fake.outputs[1])
            elif outcome == "cancel":
                with pytest.raises(asyncio.CancelledError):
                    await task
            else:
                code = {"reject": "genlook_review_rejected", "timeout": "genlook_review_timeout",
                        "invalid_decision": "genlook_review_invalid"}[outcome]
                with pytest.raises(mod.ProviderFailure, match=code):
                    await task
            assert not (directory / "intermediate.png").exists(), "Review PNG outlived its bounded purpose"
            assert (directory / "request.json").is_file(), "Non-image audit binding remains"
            assert (directory / OWNER_FILE).is_file(), "Cleanup ownership remains auditable"
            assert unrelated.read_text() == "synthetic note must remain"
            assert len(fake.posts) == (2 if outcome == "complete" else 1)
            assert fake.balance == (6 if outcome == "complete" else 7)
        finally:
            if not task.done():
                task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            await provider.close()
    asyncio.run(check())


async def completed_fixture(tmp_path):
    fake = PairService()
    config = settings(tmp_path)
    provider = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
    identity = str(uuid4())
    task = asyncio.create_task(provider.generate(identity, "a" * 64, images(), placements()))
    directory, binding = await waiting(provider, tmp_path, identity)
    decide(directory, binding, "approve")
    try:
        await task
        namespace = provider._ledger["namespace"]
        contract_sha = provider.contract_sha
    finally:
        if not task.done():
            task.cancel()
        await asyncio.gather(task, return_exceptions=True)
        await provider.close()
    return fake, config, identity, directory, binding, namespace, contract_sha


def owned_marker(identity, binding, namespace, contract_sha):
    now = time.time()
    return dict(version=1, namespace=namespace, job_id=identity,
                input_sha256=binding["input_sha256"], result_sha256=binding["result_sha256"],
                contract_sha256=contract_sha, created_at=now, expires_at=now + 5)


@pytest.mark.parametrize("interrupted", [False, True])
def test_restart_erases_only_owned_image_and_preserves_unmarked_p54_evidence(tmp_path, interrupted):
    async def check():
        fake, config, identity, directory, binding, namespace, digest = await completed_fixture(tmp_path)
        png = mod.verified_png(fake.outputs[0])
        # Simulate a prior owned process interrupted before its final unlink.
        (directory / "intermediate.png").write_bytes(png)
        (directory / OWNER_FILE).write_text(json.dumps(owned_marker(identity, binding, namespace, digest)))
        legacy = directory.parent / str(uuid4())
        legacy.mkdir()
        (legacy / "intermediate.png").write_bytes(png)
        if interrupted:
            journal = directory.parent.parent / "ledger.json"
            value = json.loads(journal.read_text())
            job = value["jobs"][identity]
            job["status"] = "reserved"
            job["balance_after"] = None
            job["stages"][1].update(generation_id=None, state="unsubmitted", result_sha=None)
            journal.write_text(json.dumps(value))
        calls = len(fake.calls)
        restored = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        try:
            available = await restored.check_availability()
            if interrupted:
                assert not available.ready and available.reason == "genlook_reconciliation_required"
            else:
                assert available.ready
            assert not (directory / "intermediate.png").exists()
            assert (legacy / "intermediate.png").read_bytes() == png
            assert len(fake.calls) == calls, "Startup cleanup must not contact a provider"
        finally:
            await restored.close()
    asyncio.run(check())


@pytest.mark.parametrize("fault", ["wrong_namespace", "wrong_job", "wrong_input", "wrong_result",
                                    "unknown_job", "malformed_marker", "changed_image"])
def test_restart_refuses_unproven_ownership_without_deleting_image(tmp_path, fault):
    async def check():
        fake, config, identity, directory, binding, namespace, digest = await completed_fixture(tmp_path)
        png = mod.verified_png(fake.outputs[0])
        marker = owned_marker(identity, binding, namespace, digest)
        if fault == "wrong_namespace": marker["namespace"] = "b" * 32
        if fault == "wrong_job": marker["job_id"] = str(uuid4())
        if fault == "wrong_input": marker["input_sha256"] = "b" * 64
        if fault == "wrong_result": marker["result_sha256"] = "b" * 64
        if fault == "unknown_job":
            directory = directory.parent / str(uuid4())
            directory.mkdir()
            marker["job_id"] = directory.name
        if fault == "changed_image": png = mod.verified_png(fake.outputs[1])
        image = directory / "intermediate.png"
        image.write_bytes(png)
        (directory / OWNER_FILE).write_text("{" if fault == "malformed_marker" else json.dumps(marker))
        calls = len(fake.calls)
        restored = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        try:
            available = await restored.check_availability()
            assert not available.ready
            assert available.reason in {"genlook_cleanup_required", "genlook_journal_unavailable"}
            assert image.read_bytes() == png
            assert len(fake.calls) == calls
        finally:
            await restored.close()
    asyncio.run(check())


@pytest.mark.parametrize("fault", ["unlink_refused", "owner_removed"])
def test_failed_local_cleanup_blocks_shoes_and_keeps_image_for_reconciliation(tmp_path, monkeypatch, fault):
    async def check():
        fake = PairService()
        config = settings(tmp_path)
        provider = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        identity = str(uuid4())
        task = asyncio.create_task(provider.generate(identity, "a" * 64, images(), placements()))
        try:
            directory, binding = await waiting(provider, tmp_path, identity)
            image = directory / "intermediate.png"
            digest = hashlib.sha256(image.read_bytes()).hexdigest()
            original = Path.unlink
            def fail_owned(path, *args, **kwargs):
                if path == image:
                    raise OSError("synthetic cleanup refusal")
                return original(path, *args, **kwargs)
            if fault == "owner_removed":
                (directory / OWNER_FILE).unlink()
            else:
                monkeypatch.setattr(Path, "unlink", fail_owned)
            decide(directory, binding, "approve")
            with pytest.raises(mod.ProviderFailure, match="genlook_cleanup_required"):
                await task
            assert len(fake.posts) == 1 and fake.balance == 7
            assert len(fake.deletes) == 3, "Local refusal must not skip customer and two-product remote cleanup"
            assert hashlib.sha256(image.read_bytes()).hexdigest() == digest
            assert (await provider.check_availability()).reason == "genlook_cleanup_required"
        finally:
            if not task.done(): task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            await provider.close()
        calls = len(fake.calls)
        restored = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
        try:
            available = await restored.check_availability()
            assert not available.ready, "Restart must retain unresolved local-image cleanup refusal"
            assert available.reason in {"genlook_cleanup_required", "genlook_reconciliation_required"}
            assert hashlib.sha256(image.read_bytes()).hexdigest() == digest
            with pytest.raises(mod.ProviderFailure, match="genlook_(cleanup|reconciliation)_required"):
                await restored.generate(str(uuid4()), "b" * 64, images(), placements())
            assert len(fake.calls) == calls, "Unresolved cleanup must reject before any HTTP or paid replay"
            if fault == "owner_removed":
                # Reconciliation-blocked startup has no outbound client. Return
                # a safe error, never replace durable cleanup failure or retry.
                with pytest.raises(mod.ProviderFailure, match="genlook_cleanup_required"):
                    await restored.cancel(identity)
                assert len(fake.posts) == 1 and fake.balance == 7
                assert len(fake.calls) == calls
        finally:
            await restored.close()
        if fault == "owner_removed":
            calls = len(fake.calls)
            after_cancel = mod.GenlookProvider(config, transport=httpx.MockTransport(fake), review_timeout_seconds=5)
            try:
                available = await after_cancel.check_availability()
                assert not available.ready, "Cancellation must not clear durable local-cleanup refusal"
                assert available.reason in {"genlook_cleanup_required", "genlook_reconciliation_required"}
                assert hashlib.sha256(image.read_bytes()).hexdigest() == digest
                with pytest.raises(mod.ProviderFailure, match="genlook_(cleanup|reconciliation)_required"):
                    await after_cancel.generate(str(uuid4()), "c" * 64, images(), placements())
                assert len(fake.calls) == calls
            finally:
                await after_cancel.close()
    asyncio.run(check())
