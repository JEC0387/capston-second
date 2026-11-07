package com.jeiu.fashionproject

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ServerOutfitQaRecoveryTest {
    private val scheduler = TestCoroutineScheduler()
    private val controllers = mutableListOf<ServerOutfitVtonViewModel>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val old = OutfitServerSession("https://old.example", "synthetic-old")
    private val ticket = OutfitServerTicket("ac0df578-cdb6-4573-81ae-f7a986e1c08d", "a".repeat(64), "rev",
        old.base, old.accountKey, "b".repeat(64), 1000, 2000, "c".repeat(64))
    private class Store(var active: OutfitServerTicket?) : OutfitTicketStore {
        var archived: OutfitServerTicket? = null
        var fail = false
        override fun read() = active
        override fun write(ticket: OutfitServerTicket) { active = ticket }
        override fun archiveReviewed(ticket: OutfitServerTicket) {
            if (fail) error("synthetic failure")
            check(active == ticket); archived = ticket; active = null
        }
    }
    private class Gateway : OutfitServerGateway {
        var current = OutfitServerSession("https://new.example", "synthetic-new")
        var calls = 0
        var error: String? = null
        override fun session() = current
        override suspend fun config(session: OutfitServerSession) =
            OutfitServerConfig(true, "", "synthetic", "rev", "c".repeat(64), "policy", 300, 1000, 4000)
        override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
            calls++; error?.let { throw OutfitServerFailure(it) }
            return OutfitServerStatus("running", null, .1, false, null, 2000)
        }
        override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus = error("POST forbidden")
        override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("DELETE forbidden")
        override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray = error("download forbidden")
    }
    private fun vm(s: Store, g: Gateway, now: Long = 3000, qa: Boolean = true) = ServerOutfitVtonViewModel(
        gateway = g, storeFactory = { s }, clock = { now }, qaRecoveryAvailable = { qa }
    ).also { controllers += it; it.initialize(app); it.refreshConfig(); scheduler.runCurrent() }
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    @After fun after() { controllers.forEach { it.viewModelScope.cancel() }; scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun restoredExpiredTicketStillBlocksUntilExplicitReviewedQaArchiveAndFreshConsent() {
        val s = Store(ticket); val g = Gateway(); val v = vm(s, g)
        assertFalse(v.startAllowedByPreviousJob()) // The actual tunnel-change reproduction.
        assertTrue(v.canArchiveReviewedQaJob())
        v.archiveReviewedQaJob(false)
        assertEquals(ticket, s.active); assertFalse(v.startAllowedByPreviousJob())
        v.archiveReviewedQaJob(true)
        assertEquals(ticket, s.archived); assertNull(s.active); assertNull(v.state.ticket)
        assertNull(v.state.config); assertNull(v.state.configBinding); assertNull(v.state.status)
        assertNull(v.state.result); assertEquals(0, g.calls)
        v.start(app, null, true); scheduler.runCurrent(); assertNull(s.active)
    }
    @Test fun productionSameConnectionAndUnexpiredJobsRemainBlocked() {
        for ((qa, now, same) in listOf(Triple(false, 3000L, false), Triple(true, 1999L, false), Triple(true, 3000L, true))) {
            val s = Store(ticket); val g = Gateway().apply { if (same) current = old }; val v = vm(s, g, now, qa)
            assertFalse(v.canArchiveReviewedQaJob()); v.archiveReviewedQaJob(true)
            assertEquals(ticket, s.active); assertNull(s.archived); assertFalse(v.startAllowedByPreviousJob())
        }
    }
    @Test fun failedArchiveOrChangedConnectionKeepsActiveJournalAndBlocksNewPost() {
        val s = Store(ticket).apply { fail = true }; val g = Gateway(); val v = vm(s, g)
        v.archiveReviewedQaJob(true)
        assertEquals(ticket, s.active); assertEquals(ticket, v.state.ticket); assertFalse(v.startAllowedByPreviousJob())
        s.fail = false; g.current = OutfitServerSession("https://third.example", "synthetic-third")
        v.archiveReviewedQaJob(true); assertEquals(ticket, s.active); assertEquals(0, g.calls)
    }
    @Test fun unresolvedBillingCannotBeArchivedEvenAfterChangingTunnel() {
        val s = Store(ticket); val g = Gateway().apply { current = old; error = "genlook_reconciliation_required" }; val v = vm(s, g)
        v.resume(); scheduler.runCurrent()
        g.current = OutfitServerSession("https://new.example", "synthetic-new")
        v.invalidateConnection(); v.refreshConfig(); scheduler.runCurrent()
        assertFalse(v.canArchiveReviewedQaJob()); v.archiveReviewedQaJob(true)
        assertEquals(ticket, s.active); assertNull(s.archived)
    }
    @Test fun privateStoreArchivePreservesExactBytesAndRefusesOverwriteOrStaleTicket() {
        val store = PrivateOutfitTicketStore(app)
        store.write(ticket)
        val active = File(app.noBackupFilesDir, "outfit-vton-ticket-v1.json")
        val bytes = active.readBytes()
        try { store.archiveReviewed(ticket.copy(inputSha = "d".repeat(64))); fail("stale ticket") } catch (_: OutfitServerFailure) { }
        assertArrayEquals(bytes, active.readBytes())
        store.archiveReviewed(ticket)
        assertNull(store.read())
        val archived = File(app.noBackupFilesDir, "outfit-vton-reviewed-history/${ticket.id}-${deviceSha256(bytes)}.json")
        assertArrayEquals(bytes, archived.readBytes())
        store.write(ticket)
        try { store.archiveReviewed(ticket); fail("overwrite") } catch (_: OutfitServerFailure) { }
        assertArrayEquals(bytes, active.readBytes()); assertArrayEquals(bytes, archived.readBytes())
    }
}
