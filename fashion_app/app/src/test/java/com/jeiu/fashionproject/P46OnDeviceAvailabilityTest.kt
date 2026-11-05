package com.jeiu.fashionproject

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.DeviceVtonStatus
import com.jeiu.fashionproject.ui.viewmodel.OnDeviceVtonViewModel
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

/** Dynamic engine boundary tests only. No image decoder, native model or inference runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class P46OnDeviceAvailabilityTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val models = mutableListOf<OnDeviceVtonViewModel>()
    private val roles = DeviceGarmentRole.entries.toSet()
    private val valid = DeviceModelSpec("approved-model-a", "rev-a", "a".repeat(64), roles, 1024)
    private val preparedModels = mutableListOf<DeviceModelSpec>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val input = DeviceVtonSelection("content://synthetic/person", 1,
        roles.map { DeviceGarmentInput("synthetic-$it", it, "content://synthetic/$it") })

    @Before fun installDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private class ChangingEngine(
        private val first: DeviceEngineAvailability,
        private val later: DeviceEngineAvailability
    ) : DeviceVtonEngine {
        var reads = 0
        var generationCalls = 0
        override val availability: DeviceEngineAvailability
            get() = if (reads++ == 0) first else later
        override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput {
            generationCalls++
            // A native model can disappear after a valid readiness snapshot.
            // The controller must handle this as a failed attempt, never UI crash/success.
            throw IllegalStateException("synthetic-native-model-unloaded")
        }
    }

    private fun controller(engine: DeviceVtonEngine) = OnDeviceVtonViewModel(
        engine = engine,
        preparer = { _, id, selection, spec ->
            preparedModels += spec
            // Opaque synthetic placeholders; generate always throws before output validation.
            val image = DeviceImage(byteArrayOf(1, 2, 3), 32, 64)
            DevicePreparedJob(id, selection, spec, image, roles.associateWith { image }, "b".repeat(64))
        }, workDispatcher = dispatcher
    ).also(models::add)

    @Test fun readyThenUnavailableDoesNotCrashAndNativeFailureRemainsFailure() {
        val engine = ChangingEngine(DeviceEngineAvailability.Ready(valid),
            DeviceEngineAvailability.Unavailable("model unloaded"))
        val vm = controller(engine)
        vm.start(app, input, approved = true)
        scheduler.runCurrent()
        assertEquals("one validated readiness snapshot per request", 1, engine.reads)
        assertEquals(listOf(valid), preparedModels)
        assertEquals(1, engine.generationCalls)
        assertEquals(DeviceVtonStatus.FAILED, vm.state.status)
        assertNull(vm.state.result)
        assertFalse(vm.state.pending)
        assertFalse(vm.state.error.orEmpty().contains("synthetic-native"))
    }

    @Test fun replacementAfterValidationCannotSubstituteUnsupportedModel() {
        val unsupported = DeviceModelSpec("replacement-b", "rev-b", "c".repeat(64),
            setOf(DeviceGarmentRole.TOP), 8192)
        val engine = ChangingEngine(DeviceEngineAvailability.Ready(valid), DeviceEngineAvailability.Ready(unsupported))
        val vm = controller(engine)
        vm.start(app, input, approved = true)
        scheduler.runCurrent()
        assertEquals("must prepare exactly the model that passed validation", listOf(valid), preparedModels)
        assertEquals(1, engine.reads)
        assertEquals(DeviceVtonStatus.FAILED, vm.state.status)
        assertNull(vm.state.result)
    }

    @Test fun invalidFirstSnapshotIsRejectedBeforeReadingAnyInput() {
        val invalid = listOf(
            DeviceModelSpec("invalid-side", "r", "a".repeat(64), roles, 31),
            DeviceModelSpec("invalid-side", "r", "a".repeat(64), roles, 2049),
            DeviceModelSpec("missing-shoes", "r", "a".repeat(64), roles - DeviceGarmentRole.SHOES, 1024)
        )
        for (spec in invalid) {
            val engine = ChangingEngine(DeviceEngineAvailability.Ready(spec), DeviceEngineAvailability.Ready(valid))
            val vm = controller(engine)
            vm.start(app, input, approved = true)
            scheduler.runCurrent()
            assertEquals(1, engine.reads)
            assertEquals(0, engine.generationCalls)
            assertEquals(DeviceVtonStatus.BLOCKED, vm.state.status)
        }
        assertTrue(preparedModels.isEmpty())
    }

    @Test fun unavailableFirstSnapshotCannotBecomeReadyMidRequest() {
        val engine = ChangingEngine(DeviceEngineAvailability.Unavailable("not installed"),
            DeviceEngineAvailability.Ready(valid))
        val vm = controller(engine)
        vm.start(app, input, approved = true)
        scheduler.runCurrent()
        assertEquals(1, engine.reads)
        assertEquals(0, engine.generationCalls)
        assertEquals(DeviceVtonStatus.BLOCKED, vm.state.status)
        assertTrue(preparedModels.isEmpty())
        assertNull(vm.state.result)
    }
}
