package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** No HTTP or real inference. Tests exercise durable requests against a synthetic gateway. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class P48ServerOutfitControllerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val controllers = mutableListOf<ServerOutfitVtonViewModel>()
    private var prepareCalls = 0
    private var preparedMaxSide: Int? = null
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cfg = OutfitServerConfig(true, "", "synthetic", "rev", "a".repeat(64), "policy", 300, 5*1024*1024, 29*1024*1024)
    private fun selection(n: Long = 1) = DeviceVtonSelection("content://synthetic/person", n, DeviceGarmentRole.entries.map {
        DeviceGarmentInput("synthetic-$it-$n", it, "content://synthetic/$it", "400,600,125,150,60,1,0,2") })
    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return try { ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } } finally { b.recycle() }
    }
    private class Store : OutfitTicketStore {
        var ticket: OutfitServerTicket? = null
        var failWrite = false
        override fun read() = ticket
        override fun write(ticket: OutfitServerTicket) { if (failWrite) error("synthetic private error"); this.ticket = ticket }
    }
    private inner class Gateway(val store: Store) : OutfitServerGateway {
        var current = OutfitServerSession("https://synthetic.example", "synthetic-not-a-real-token")
        var posts = 0; var gets = 0; var cancels = 0; var resultReads = 0
        var config = cfg
        var lost = false
        var gate: CompletableDeferred<Unit>? = null
        var returnedStatus = "completed"
        var resultBytes = png(Color.BLUE)
        override fun session() = current
        override suspend fun config(session: OutfitServerSession) = config
        private fun state() = OutfitServerStatus(returnedStatus, null, if (returnedStatus == "completed") 1.0 else .1,
            returnedStatus == "completed", deviceSha256(resultBytes), 301000)
        override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
            assertEquals(ticket, store.ticket) // Durable before the POST.
            assertEquals(ticket.inputSha, deviceSha256(body))
            posts++; gate?.await()
            if (lost) error("synthetic lost response")
            return state()
        }
        override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus { gets++; return state() }
        override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
            cancels++; returnedStatus = "cancelled"; return state()
        }
        override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray {
            resultReads++; return resultBytes
        }
    }
    private fun vm(g: Gateway, store: Store) = ServerOutfitVtonViewModel(g, { store }, { _, id, selected, model ->
        prepareCalls++
        preparedMaxSide = model.maxSide
        val p = DeviceImage(png(Color.RED), 1000, 2000)
        DevicePreparedJob(id, selected, model, p, DeviceGarmentRole.entries.associateWith { DeviceImage(png(Color.GREEN), 256, 256) }, "b".repeat(64))
    }, clock = { 1000 + scheduler.currentTime }, pollMillis = 100, localWaitMillis = 500).also {
        controllers += it; it.initialize(app)
    }
    private fun ready(vm: ServerOutfitVtonViewModel) { vm.refreshConfig(); scheduler.runCurrent() }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { controllers.forEach { it.viewModelScope.cancel() }; scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun consentReadinessAndPersistenceFailurePreventPosting() {
        val store = Store(); val g = Gateway(store); val vm = vm(g, store)
        vm.start(app, selection(), true); scheduler.runCurrent(); assertEquals(0, g.posts)
        ready(vm); vm.start(app, selection(), false); scheduler.runCurrent(); assertEquals(0, g.posts)
        store.failWrite = true
        vm.start(app, selection(), true); scheduler.runCurrent(); assertEquals(0, g.posts); assertNull(vm.state.result)
    }

    @Test fun serverPreparationUsesItsNegotiatedImageSpecAndKeepsOnDeviceDefaultSeparate() {
        val store = Store()
        val g = Gateway(store).apply { config = cfg.copy(maxInputSide = 1536) }
        val vm = vm(g, store)
        ready(vm)
        assertTrue(vm.inputReady(selection()))
        vm.start(app, selection(), true); scheduler.runCurrent()
        assertEquals(1536, preparedMaxSide)
        assertEquals(1, g.posts)
        assertEquals(1024, DeviceModelSpec("synthetic", "rev", "a".repeat(64), DeviceGarmentRole.entries.toSet()).maxSide)
    }

    @Test fun duplicateClicksKeepFrozenRequestAndResultIsNotSubstitutedAfterClothesChange() {
        val store = Store(); val g = Gateway(store).apply { gate = CompletableDeferred() }; val vm = vm(g, store)
        ready(vm); vm.selectionChanged(selection())
        vm.start(app, selection(), true); vm.start(app, selection(2), true); scheduler.runCurrent()
        assertEquals(1, g.posts)
        val frozen = store.ticket!!
        vm.selectionChanged(selection(2)); g.gate!!.complete(Unit); scheduler.runCurrent()
        assertEquals(frozen, store.ticket); assertEquals(1, g.posts)
        assertNotNull(vm.state.result); assertTrue(vm.state.previous)
    }

    @Test fun processRestartAfterResponseLossOnlyQueriesSameJobAndCanReceiveVerifiedPng() {
        val store = Store(); val g = Gateway(store).apply { lost = true }; val first = vm(g, store)
        ready(first); first.start(app, selection(), true); scheduler.runCurrent()
        assertEquals(1, g.posts); assertNotNull(store.ticket); assertNull(first.state.result)
        first.start(app, selection(2), true); scheduler.runCurrent(); assertEquals(1, g.posts)
        first.viewModelScope.cancel(); scheduler.runCurrent()
        val second = vm(g, store)
        second.resume(); scheduler.runCurrent()
        assertEquals(1, g.posts); assertEquals(1, g.gets); assertNotNull(second.state.result)
        assertTrue(second.state.previous); assertNull(second.state.result!!.original)
    }

    @Test fun cancelWaitsForServerAndAccountChangeCannotDownloadOrShowAnotherAccountsResult() {
        val store = Store(); val g = Gateway(store).apply { gate = CompletableDeferred(); returnedStatus = "running" }; val vm = vm(g, store)
        ready(vm); vm.start(app, selection(), true); scheduler.runCurrent(); vm.cancel()
        assertEquals(0, g.cancels)
        g.gate!!.complete(Unit); scheduler.runCurrent()
        assertEquals(1, g.cancels); assertEquals("cancelled", vm.state.status!!.status); assertNull(vm.state.result)
        g.current = OutfitServerSession("https://synthetic.example", "different-account-synthetic")
        vm.resume(); scheduler.runCurrent()
        assertEquals(0, g.gets); assertEquals(0, g.resultReads); assertFalse(vm.canDisplay())
    }

    @Test fun localWaitTimeoutDoesNotClaimRemoteCancellationOrResubmit() {
        val store = Store(); val g = Gateway(store).apply { returnedStatus = "running" }; val vm = vm(g, store)
        ready(vm); vm.start(app, selection(), true); scheduler.runCurrent(); scheduler.advanceTimeBy(600); scheduler.runCurrent()
        assertEquals("waiting", vm.state.phase); assertEquals(0, g.cancels); assertEquals(1, g.posts)
        vm.start(app, selection(2), true); scheduler.runCurrent(); assertEquals(1, g.posts)
    }

    @Test fun configAndConsentForOldServerAccountCannotAuthorizeAnotherBeforeImageReads() {
        val store = Store(); val g = Gateway(store); val vm = vm(g, store)
        ready(vm)
        val oldBinding = vm.state.configBinding
        g.current = OutfitServerSession("https://different-server.example", "different-synthetic-account")
        vm.start(app, selection(), true); scheduler.runCurrent()
        assertEquals(0, prepareCalls); assertEquals(0, g.posts); assertNull(store.ticket)
        assertNull(vm.state.config); assertNull(vm.state.configBinding)
        vm.refreshConfig(); scheduler.runCurrent()
        assertNotEquals(oldBinding, vm.state.configBinding)
        assertEquals("different-server.example", vm.state.configHost)
    }

    @Test fun missingDuplicateAndNonLocalInputsAreRejectedBeforePreparationOrPosting() {
        val store = Store(); val g = Gateway(store); val vm = vm(g, store)
        ready(vm)
        val valid = selection()
        val garments = valid.garments
        val invalid = listOf(null, valid.copy(person = ""), valid.copy(revision = -1),
            valid.copy(garments = garments.take(1)), valid.copy(garments = garments.take(2)),
            valid.copy(garments = listOf(garments[0], garments[0], garments[2])),
            valid.copy(garments = garments.map { it.copy(id = "same-id") }),
            valid.copy(garments = garments.map { it.copy(source = "https://synthetic.example/image.png") }))
        invalid.forEach { input ->
            assertFalse(vm.inputReady(input))
            vm.start(app, input, true); scheduler.runCurrent()
            assertEquals(0, prepareCalls); assertEquals(0, g.posts); assertNull(store.ticket)
        }
        assertTrue(vm.inputReady(valid))
    }
}
