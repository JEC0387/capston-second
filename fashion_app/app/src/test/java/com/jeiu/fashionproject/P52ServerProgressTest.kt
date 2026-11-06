package com.jeiu.fashionproject

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Strict synthetic progress and recovery checks only. No external request or generation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class P52ServerProgressTest {
    private val repo = ServerOutfitVtonRepository
    private val mode = "clothing-pair-then-shoes"
    private val session = OutfitServerSession("https://synthetic.example", "synthetic-only")
    private fun ticket(revision: String = "genlook-paired-v1-20261106-0123456789abcdef", tag: String = "genlook") =
        OutfitServerTicket("00000000-0000-4000-8000-000000000052", "b".repeat(64), revision, session.base,
            session.accountKey, "c".repeat(64), 1000, 301000, "a".repeat(64), tag)
    private fun progress(stage: Int = 1, phase: String = "waiting_for_generation", progressMode: String = mode, count: Int = 2) =
        JSONObject().put("mode", progressMode).put("stage_index", stage).put("stage_count", count).put("phase", phase)
    private fun status(t: OutfitServerTicket = ticket()) = JSONObject().put("engine", repo.ENGINE)
        .put("request_id", t.id).put("input_sha256", t.inputSha).put("model_revision", t.revision)
        .put("model_sha256", t.modelSha).put("status", "running").put("progress", 10)
        .put("result_available", false).put("expires_at", 301).put("provider_progress", progress())

    @Test fun pairedAndLegacySequentialStagesBindToCapturedRevision() {
        val paired = repo.parseStatus(status(), ticket()).providerProgress!!
        assertEquals(OutfitServerProgress(mode, 1, 2, "waiting_for_generation"), paired)
        assertEquals("finalizing", repo.parseStatus(status().put("provider_progress", progress(2, "finalizing")), ticket()).providerProgress?.phase)
        listOf("genlook-sequential-v1-20261106-old", "genlook-sequential-v2-preservation-20261106-0123456789abcdef").forEach { revision ->
            val old = ticket(revision, "")
            val received = repo.parseStatus(status(old).put("provider_progress",
                progress(3, "finalizing", "sequential-3", 3)), old)
            assertEquals(OutfitServerProgress("sequential-3", 3, 3, "finalizing"), received.providerProgress)
            assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(old), old) }
        }
    }

    @Test fun unknownExtraWrongModeAndUnboundedProgressFailClosed() {
        val bad = listOf(progress().put("mode", "sequential-3"), progress().put("phase", "downloaded"),
            progress().put("stage_count", 3), progress().put("stage_count", "2"),
            progress(0), progress(3), progress().put("stage_index", 1.5), progress().put("stage_index", "1"),
            progress().put("generation_id", "must-not-leak"), progress().apply { remove("phase") },
            progress(1, "finalizing"))
        bad.forEach { p -> assertEquals("invalid_provider_progress", assertThrows(OutfitServerFailure::class.java) {
            repo.parseStatus(status().put("provider_progress", p), ticket())
        }.code) }
        assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status().put("provider_progress", "running"), ticket()) }
    }

    @Test fun progressIsRunningOnlyAndOldResponsesMayOmitIt() {
        listOf("queued", "cancelling", "cancelled", "interrupted", "failed", "completed", "timeout", "expired").forEach { phase ->
            assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status().put("status", phase), ticket()) }
            assertNull(repo.parseStatus(status().put("status", phase).apply { remove("provider_progress") }, ticket()).providerProgress)
        }
        assertNull(repo.parseStatus(status().put("provider_progress", JSONObject.NULL), ticket()).providerProgress)
        val local = ticket("local-version", "")
        assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(local), local) }
        assertNull(repo.parseStatus(status(local).apply { remove("provider_progress") }, local).providerProgress)
    }

    @Test fun monotonicPhaseAndStageValidationRejectsRegression() {
        val phases = listOf("preparing", "uploading", "submitting", "waiting_for_generation", "validating_result", "finalizing")
        phases.zipWithNext().forEach { (before, after) ->
            repo.requireProgressTransition(OutfitServerProgress(mode, 2, 2, before), OutfitServerProgress(mode, 2, 2, after))
            assertThrows(OutfitServerFailure::class.java) {
                repo.requireProgressTransition(OutfitServerProgress(mode, 2, 2, after), OutfitServerProgress(mode, 2, 2, before))
            }
        }
        repo.requireProgressTransition(OutfitServerProgress(mode, 1, 2, "validating_result"), OutfitServerProgress(mode, 2, 2, "preparing"))
        assertThrows(OutfitServerFailure::class.java) {
            repo.requireProgressTransition(OutfitServerProgress(mode, 2, 2, "preparing"), OutfitServerProgress(mode, 1, 2, "validating_result"))
        }
    }

    @Test fun omittedProgressDoesNotEraseSameJobRegressionGuardOrResubmit() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val old = ticket()
        val store = object : OutfitTicketStore {
            override fun read() = old
            override fun write(ticket: OutfitServerTicket) { error("Must preserve original journal") }
        }
        var gets = 0
        var posts = 0
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession): OutfitServerConfig = error("No config fetch")
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++; error("No new POST")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
                gets++
                val p = when (gets) { 1 -> OutfitServerProgress(mode, 2, 2, "waiting_for_generation")
                    2 -> null; else -> OutfitServerProgress(mode, 1, 2, "waiting_for_generation") }
                return OutfitServerStatus("running", null, .1, false, null, 301000, providerProgress = p)
            }
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("No cancel")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray = error("No result")
        }
        val vm = ServerOutfitVtonViewModel(gateway, { store }, clock = { 1000 + scheduler.currentTime }, pollMillis = 1, localWaitMillis = 50)
        try {
            vm.initialize(ApplicationProvider.getApplicationContext<Application>())
            vm.resume(); scheduler.advanceUntilIdle()
            assertEquals("blocked", vm.state.phase)
            assertEquals(repo.friendly("invalid_provider_progress"), vm.state.error)
            assertEquals(old, vm.state.ticket)
            assertNull(vm.state.result)
            assertEquals(3, gets)
            assertEquals(0, posts)
        } finally { vm.viewModelScope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun connectionChangeInvalidatesConfigWithoutFetchingOrReplacingJournal() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val old = ticket()
        var configs = 0
        val store = object : OutfitTicketStore {
            override fun read() = old
            override fun write(ticket: OutfitServerTicket) { error("Must preserve old journal") }
        }
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession): OutfitServerConfig {
                configs++
                return OutfitServerConfig(true, "", "genlook/tryon-api-v1", old.revision, old.modelSha,
                    "synthetic-policy", 300, 1024, 4096,
                    OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", mode, 3, false, 1, "Synthetic policy"))
            }
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus = error("No POST")
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("No auto GET")
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("No auto DELETE")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray = error("No download")
        }
        val vm = ServerOutfitVtonViewModel(gateway, { store })
        try {
            vm.initialize(ApplicationProvider.getApplicationContext<Application>())
            vm.refreshConfig(); scheduler.runCurrent()
            assertNotNull(vm.state.config); assertEquals(session.accountKey, vm.state.configBinding)
            vm.invalidateConnection(); scheduler.runCurrent()
            assertNull(vm.state.config); assertNull(vm.state.configBinding); assertNull(vm.state.configHost)
            assertNull(vm.state.status); assertNull(vm.state.result); assertNull(vm.state.error)
            assertEquals(old, vm.state.ticket); assertTrue(vm.state.previous)
            assertEquals("recovered", vm.state.phase); assertEquals(1, configs)
        } finally { vm.viewModelScope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun uncertainTerminalOrBlockedReconciliationPreventsPreparationUntilExplicitRecovery() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val old = ticket()
        val selected = DeviceVtonSelection("content://synthetic/person", 1, DeviceGarmentRole.entries.map {
            DeviceGarmentInput("synthetic-${it.name}", it, "content://synthetic/${it.name}", "placement-${it.name}")
        })
        val store = object : OutfitTicketStore {
            override fun read() = old
            override fun write(ticket: OutfitServerTicket) { error("Old journal must remain") }
        }
        var observedError: String? = null
        var throwReconciliation = false
        var gets = 0
        var posts = 0
        var prepares = 0
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = OutfitServerConfig(true, "", "genlook/tryon-api-v1",
                old.revision, old.modelSha, "synthetic-policy", 300, 5 * 1024 * 1024, 29 * 1024 * 1024,
                OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", mode, 3, false, 1, "Synthetic policy"))
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++; error("No POST while unresolved")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
                gets++
                if (throwReconciliation) throw OutfitServerFailure("genlook_reconciliation_required")
                return OutfitServerStatus(if (observedError == null) "cancelled" else "interrupted",
                    observedError, .1, false, null, 301000)
            }
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("No DELETE")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray = error("No download")
        }
        val vm = ServerOutfitVtonViewModel(gateway, { store }, preparer = { _, _, _, _ -> prepares++; error("No input read while unresolved") })
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            vm.initialize(app); vm.refreshConfig(); scheduler.runCurrent()
            assertTrue(vm.inputReady(selected))
            listOf("remote_execution_unconfirmed", "worker_stop_unconfirmed", "genlook_reconciliation_required", "genlook_journal_unavailable").forEach { code ->
                observedError = code
                vm.resume(); scheduler.runCurrent()
                assertFalse(vm.startAllowedByPreviousJob())
                vm.start(app, selected, true); scheduler.runCurrent()
                assertEquals(0, prepares); assertEquals(0, posts)
                assertEquals(old, vm.state.ticket)
                observedError = null
                vm.resume(); scheduler.runCurrent()
                assertTrue(vm.startAllowedByPreviousJob())
            }
            // Even a previously resolved status cannot override a later explicit reconciliation failure.
            throwReconciliation = true
            vm.resume(); scheduler.runCurrent()
            assertEquals("blocked", vm.state.phase); assertFalse(vm.startAllowedByPreviousJob())
            vm.start(app, selected, true); scheduler.runCurrent()
            assertEquals(0, prepares); assertEquals(0, posts)
            throwReconciliation = false
            vm.resume(); scheduler.runCurrent()
            assertTrue(vm.startAllowedByPreviousJob()); assertEquals(10, gets)
            assertEquals(old, vm.state.ticket)
        } finally { vm.viewModelScope.cancel(); scheduler.runCurrent(); Dispatchers.resetMain() }
    }
}
