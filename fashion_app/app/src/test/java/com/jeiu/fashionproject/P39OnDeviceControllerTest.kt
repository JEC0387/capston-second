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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Synthetic adapter contract tests only: these do not load a model or prove AI inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class P39OnDeviceControllerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val models = mutableListOf<OnDeviceVtonViewModel>()
    private val allRoles = DeviceGarmentRole.entries.toSet()
    private val modelSpec = DeviceModelSpec("synthetic-test-only", "1", "a".repeat(64), allRoles)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val person by lazy { DeviceImage(png(Color.RED), 32, 64) }
    private val garment by lazy { DeviceImage(png(Color.GREEN), 32, 64) }
    private val resultBytes by lazy { png(Color.BLUE) }
    private var prepareCalls = 0

    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun selection(suffix: String = "a") = DeviceVtonSelection("content://synthetic/person", 1,
        allRoles.map { DeviceGarmentInput("$it-$suffix", it, "content://synthetic/$it-$suffix", "0,0,1,0") })

    private inner class Fake : DeviceVtonEngine {
        override val availability = DeviceEngineAvailability.Ready(modelSpec)
        val jobs = mutableListOf<DevicePreparedJob>()
        var cancellations = 0
        var action: suspend (DevicePreparedJob) -> DeviceEngineOutput = { output(it) }
        override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput {
            jobs += job
            return action(job)
        }
        override fun requestCancellation(jobId: String) { cancellations++ }
    }

    private fun output(job: DevicePreparedJob, jobId: String = job.id, digest: String = job.inputDigest,
        modelSha: String = job.model.sha256, categories: Set<DeviceGarmentRole> = allRoles,
        bytes: ByteArray = resultBytes) = DeviceEngineOutput(jobId, digest, modelSha, categories, bytes)

    private fun controller(engine: DeviceVtonEngine, timeout: Long = 120_000) = OnDeviceVtonViewModel(
        engine, { _, id, selection, model ->
            prepareCalls++
            DevicePreparedJob(id, selection, model, person, allRoles.associateWith { garment }, "b".repeat(64))
        }, timeout, dispatcher).also(models::add)

    @Test fun productionMissingModelAndMissingConsentBlockBeforeReadingPhotos() {
        val unavailable = controller(UnavailableDeviceVtonEngine)
        unavailable.start(app, selection(), approved = true)
        scheduler.runCurrent()
        assertEquals(DeviceVtonStatus.BLOCKED, unavailable.state.status)
        assertEquals(0, prepareCalls)
        assertNull(unavailable.state.result)
        val fake = Fake()
        val ready = controller(fake)
        ready.start(app, selection(), approved = false)
        ready.start(app, selection().copy(garments = selection().garments.dropLast(1)), approved = true)
        scheduler.runCurrent()
        assertEquals(0, prepareCalls)
        assertTrue(fake.jobs.isEmpty())
        assertEquals(DeviceVtonStatus.BLOCKED, ready.state.status)
    }

    @Test fun oneFrozenThreeGarmentRequestPreventsDuplicatesAndCopiesBytes() {
        val gate = CompletableDeferred<Unit>()
        val fake = Fake().apply { action = { job -> gate.await(); output(job) } }
        val vm = controller(fake)
        val mutableGarments = selection().garments.toMutableList()
        val input = DeviceVtonSelection(selection().person, 1, mutableGarments)
        vm.start(app, input, true)
        mutableGarments.clear()
        vm.start(app, selection("b"), true)
        scheduler.runCurrent()
        assertEquals(1, fake.jobs.size)
        assertEquals(allRoles, fake.jobs.single().garments.keys)
        assertEquals(3, fake.jobs.single().selection.garments.size)
        val firstPersonByte = person.bytes[0]
        fake.jobs.single().person.bytes[0] = 0
        assertEquals(firstPersonByte, person.bytes[0])
        gate.complete(Unit)
        scheduler.runCurrent()
        assertEquals(DeviceVtonStatus.SUCCEEDED, vm.state.status)
        assertFalse(vm.state.pending)
        assertTrue(vm.resultMatches(input))
        val receipt = requireNotNull(vm.state.result)
        val expected = receipt.png
        receipt.png[0] = 0
        assertArrayEquals(expected, receipt.png)
        vm.selectionChanged(input.copy())
        assertEquals(1, fake.jobs.size)
        assertFalse(vm.state.resultFromPreviousAttempt)
    }

    @Test fun wrongJobDigestModelRoleMalformedPngAndInputPassthroughCannotBecomeSuccess() {
        val cases: List<(DevicePreparedJob) -> DeviceEngineOutput> = listOf(
            { output(it, jobId = "another-job") },
            { output(it, digest = "c".repeat(64)) },
            { output(it, modelSha = "d".repeat(64)) },
            { output(it, categories = setOf(DeviceGarmentRole.TOP)) },
            { output(it, bytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) },
            { output(it, bytes = it.person.bytes) },
            { output(it, bytes = it.garments.getValue(DeviceGarmentRole.SHOES).bytes) }
        )
        cases.forEachIndexed { index, invalid ->
            val fake = Fake().apply { action = { invalid(it) } }
            val vm = controller(fake)
            vm.start(app, selection(), true)
            scheduler.runCurrent()
            assertEquals("invalid receipt case $index", DeviceVtonStatus.FAILED, vm.state.status)
            assertNull(vm.state.result)
            assertFalse(vm.state.pending)
        }
    }

    @Test fun changedGarmentHidesOldBytesAndRejectsNonCooperativeLateResult() {
        val fake = Fake()
        val vm = controller(fake)
        val first = selection()
        vm.start(app, first, true)
        scheduler.runCurrent()
        val retained = requireNotNull(vm.state.result).png
        val gate = CompletableDeferred<Unit>()
        fake.action = { job -> withContext(NonCancellable) { gate.await(); output(job) } }
        vm.start(app, first, true)
        scheduler.runCurrent()
        val next = selection("b")
        vm.selectionChanged(next)
        assertFalse(vm.resultMatches(next))
        assertTrue(vm.state.pending)
        vm.start(app, next, true)
        scheduler.runCurrent()
        assertEquals(2, fake.jobs.size)
        gate.complete(Unit)
        scheduler.runCurrent()
        assertEquals(DeviceVtonStatus.CANCELLED, vm.state.status)
        assertFalse(vm.state.pending)
        assertArrayEquals(retained, requireNotNull(vm.state.result).png)
        assertTrue(vm.state.resultFromPreviousAttempt)
        assertFalse(vm.resultMatches(next))
    }

    @Test fun timeoutKeepsLockUntilNativeReturnsWithoutAutomaticRetry() {
        val gate = CompletableDeferred<Unit>()
        val fake = Fake().apply { action = { job -> withContext(NonCancellable) { gate.await(); output(job) } } }
        val vm = controller(fake, timeout = 500)
        vm.start(app, selection(), true)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(500)
        scheduler.runCurrent()
        assertEquals(DeviceVtonStatus.TIMED_OUT, vm.state.status)
        assertTrue(vm.state.pending)
        assertEquals(1, fake.cancellations)
        vm.start(app, selection(), true)
        scheduler.runCurrent()
        assertEquals(1, fake.jobs.size)
        gate.complete(Unit)
        scheduler.runCurrent()
        assertFalse(vm.state.pending)
        assertNull(vm.state.result)
        assertEquals(DeviceVtonStatus.TIMED_OUT, vm.state.status)
        scheduler.advanceTimeBy(2_000)
        scheduler.runCurrent()
        assertEquals(1, fake.jobs.size)
    }

    @Test fun immediateCancelUnlocksAndExplicitRetryUsesNewJobWithoutLeakingErrors() {
        val fake = Fake()
        val vm = controller(fake)
        vm.start(app, selection(), true)
        val cancelledId = vm.state.jobId
        vm.cancel()
        scheduler.runCurrent()
        assertFalse(vm.state.pending)
        assertTrue(fake.jobs.isEmpty())
        vm.start(app, selection(), true)
        scheduler.runCurrent()
        assertNotEquals(cancelledId, vm.state.jobId)
        assertEquals(DeviceVtonStatus.SUCCEEDED, vm.state.status)
        val previous = requireNotNull(vm.state.result).png
        fake.action = { throw IllegalStateException("private-path-and-token-must-not-escape") }
        vm.start(app, selection(), true)
        scheduler.runCurrent()
        assertEquals(DeviceVtonStatus.FAILED, vm.state.status)
        assertArrayEquals(previous, requireNotNull(vm.state.result).png)
        assertTrue(vm.state.resultFromPreviousAttempt)
        assertFalse(vm.state.error.orEmpty().contains("private-path"))
        assertTrue(vm.resultMatches(selection()))
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
