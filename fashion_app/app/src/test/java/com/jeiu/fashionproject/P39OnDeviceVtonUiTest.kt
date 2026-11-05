package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.OnDeviceVtonScreen
import com.jeiu.fashionproject.ui.viewmodel.OnDeviceVtonViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic mock-engine UI/SAF checks only. These are not model inference or device evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class P39OnDeviceVtonUiTest {
    // IO completion must not resume dialog composition on a thread without Android's Looper.
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())

    private val spec = DeviceModelSpec("synthetic-test-only", "1", "a".repeat(64), DeviceGarmentRole.entries.toSet())
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun selection(revision: Long = 1, roles: List<DeviceGarmentRole> = DeviceGarmentRole.entries) =
        DeviceVtonSelection("file:///synthetic-person.png", revision, roles.map {
            DeviceGarmentInput("synthetic-${it.name}-$revision", it, "file:///synthetic-${it.name}.png", "placement-$revision")
        })

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return try { ByteArrayOutputStream().use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray()
        } } finally { bitmap.recycle() }
    }

    private fun prepared(id: String, selected: DeviceVtonSelection, model: DeviceModelSpec) = DevicePreparedJob(
        id, selected, model, DeviceImage(png(Color.GRAY), 64, 96),
        DeviceGarmentRole.entries.associateWith { DeviceImage(png(Color.GREEN), 64, 96) }, "b".repeat(64))

    private fun receipt(job: DevicePreparedJob, output: ByteArray) = DeviceEngineOutput(
        job.id, job.inputDigest, job.model.sha256, DeviceGarmentRole.entries.toSet(), output)

    private class Documents : ActivityResultRegistryOwner {
        var requestCode = 0
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                this@Documents.requestCode = requestCode
            }
        }
        fun reply(uri: Uri) = activityResultRegistry.dispatchResult(requestCode, uri)
    }

    private fun awaitModel(condition: () -> Boolean) {
        compose.waitUntil(15000) {
            // ViewModelScope resumes on the Android Handler, not the Compose effect clock.
            // Drain only currently due callbacks; do not advance the model's timeout clock.
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    @Test fun missingProductionModelBlocksWithoutReadingInputsOrCallingAnEngine() {
        val reads = AtomicInteger()
        val vm = OnDeviceVtonViewModel(preparer = { _, id, selected, model ->
            reads.incrementAndGet(); prepared(id, selected, model)
        })
        val documents = Documents()
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { OnDeviceVtonScreen(selection(), {}, vm) }
        } }
        compose.onNodeWithText("온디바이스 AI · 모델 미준비").assertExists()
        compose.onNodeWithTag("deviceVtonConsent").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithTag("deviceVtonCompare").performScrollTo().assertIsNotEnabled()
        assertEquals(0, reads.get())
        assertNull(vm.state.result)
    }

    @Test fun topOnlyCannotStartEvenWhenMockRuntimeIsAvailable() {
        val calls = AtomicInteger()
        val engine = object : DeviceVtonEngine {
            override val availability = DeviceEngineAvailability.Ready(spec)
            override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput {
                calls.incrementAndGet(); return receipt(job, png(Color.RED))
            }
        }
        val vm = OnDeviceVtonViewModel(engine, preparer = { _, id, selected, model -> prepared(id, selected, model) })
        val documents = Documents()
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { OnDeviceVtonScreen(selection(roles = listOf(DeviceGarmentRole.TOP)), {}, vm) }
        } }
        compose.onNodeWithText("상의·하의·신발 3종이 모두 필요해요.").assertExists()
        compose.onNodeWithText("하의 · 미선택").assertExists()
        compose.onNodeWithText("신발 · 미선택").assertExists()
        compose.onNodeWithTag("deviceVtonConsent").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().assertIsNotEnabled()
        assertEquals(0, calls.get())
    }

    @Test fun syntheticReceiptCompareAndSavePinsFirstPngAcrossSecondResultThenReopensWithoutGeneration() {
        val calls = AtomicInteger()
        val red = png(Color.RED); val blue = png(Color.BLUE)
        val engine = object : DeviceVtonEngine {
            override val availability = DeviceEngineAvailability.Ready(spec)
            override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput =
                receipt(job, if (calls.incrementAndGet() == 1) red else blue)
        }
        val vm = OnDeviceVtonViewModel(engine, preparer = { _, id, selected, model -> prepared(id, selected, model) })
        val chosen = mutableStateOf(selection())
        val showing = mutableStateOf(true)
        val documents = Documents()
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { if (showing.value) OnDeviceVtonScreen(chosen.value, { showing.value = false }, vm) }
        } }
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("deviceVtonConsent").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().performClick()
        awaitModel { vm.state.result != null }
        compose.onNodeWithTag("deviceVtonCompare").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonToggle").performClick()
        compose.onNodeWithText("원본 · 요청 당시 사진").assertExists()
        compose.onNodeWithText("비교 닫기").performClick()
        compose.onNodeWithTag("deviceVtonSave").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonSave").assertIsNotEnabled()
        // A later mock result must never replace the bytes owned by the already open picker.
        compose.runOnIdle {
            chosen.value = selection(2)
            vm.selectionChanged(chosen.value)
            vm.start(app, chosen.value, true)
        }
        awaitModel { calls.get() == 2 && !vm.state.pending }
        val target = File(app.cacheDir, "p39-synthetic-pinned-${System.nanoTime()}.png")
        try {
            compose.runOnIdle { documents.reply(Uri.fromFile(target)) }
            compose.waitUntil(15000) {
                target.exists() && target.length() > 0 && compose.onAllNodesWithText(
                    "선택한 결과 PNG를 저장했어요. 저장 이미지 열기로 다시 확인해주세요.").fetchSemanticsNodes().isNotEmpty()
            }
            assertArrayEquals(red, target.readBytes())
            val decoded = BitmapFactory.decodeFile(target.path)
            try { assertEquals(Color.RED, decoded.getPixel(32, 48)) } finally { decoded.recycle() }
            assertArrayEquals(blue, vm.state.result!!.png)
            compose.onNodeWithTag("deviceVtonBack").performScrollTo().performClick()
            compose.runOnIdle { showing.value = true }
            compose.onNodeWithTag("deviceVtonOpen").performScrollTo().performClick()
            compose.runOnIdle { documents.reply(Uri.fromFile(target)) }
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("저장 이미지 · 새 생성 아님").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("닫기").performClick()
            assertEquals(2, calls.get())
        } finally { target.delete() }
    }

    @Test fun screenReentryKeepsOneMockJobAndCancelNeverClaimsAResult() {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val engine = object : DeviceVtonEngine {
            override val availability = DeviceEngineAvailability.Ready(spec)
            override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput {
                calls.incrementAndGet(); gate.await(); return receipt(job, png(Color.RED))
            }
        }
        val vm = OnDeviceVtonViewModel(engine, preparer = { _, id, selected, model -> prepared(id, selected, model) })
        val showing = mutableStateOf(true)
        val documents = Documents()
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { if (showing.value) OnDeviceVtonScreen(selection(), { showing.value = false }, vm) }
        } }
        compose.onNodeWithTag("deviceVtonConsent").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().performClick()
        awaitModel { calls.get() == 1 }
        compose.onNodeWithTag("deviceVtonGenerate").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("deviceVtonBack").performScrollTo().performClick()
        compose.runOnIdle { showing.value = true }
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().assertIsNotEnabled()
        assertEquals(1, calls.get())
        compose.onNodeWithTag("deviceVtonCancel").performScrollTo().performClick()
        awaitModel { !vm.state.pending }
        compose.onNodeWithTag("deviceVtonCompare").performScrollTo().assertIsNotEnabled()
        assertNull(vm.state.result)
        assertEquals(1, calls.get())
    }

    @Test fun failedMockRetryLabelsPriorResultAndChangedClothesHideIt() {
        val calls = AtomicInteger()
        val engine = object : DeviceVtonEngine {
            override val availability = DeviceEngineAvailability.Ready(spec)
            override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput {
                if (calls.incrementAndGet() > 1) throw IllegalStateException("synthetic failure")
                return receipt(job, png(Color.RED))
            }
        }
        val vm = OnDeviceVtonViewModel(engine, preparer = { _, id, selected, model -> prepared(id, selected, model) })
        val chosen = mutableStateOf(selection())
        val documents = Documents()
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { OnDeviceVtonScreen(chosen.value, {}, vm) }
        } }
        compose.onNodeWithTag("deviceVtonConsent").performScrollTo().performClick()
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().performClick()
        awaitModel { vm.state.result != null && !vm.state.pending }
        compose.onNodeWithTag("deviceVtonGenerate").performScrollTo().performClick()
        awaitModel { calls.get() == 2 && !vm.state.pending }
        compose.onNodeWithText("이전에 생성한 결과 · 이번 시도 결과 아님").assertExists()
        compose.onNodeWithTag("deviceVtonCompare").performScrollTo().performClick()
        compose.onNodeWithText("이전 요청 결과 · 새 생성 아님").assertExists()
        compose.onNodeWithText("비교 닫기").performClick()
        compose.runOnIdle { chosen.value = selection(2) }
        compose.onNodeWithTag("deviceVtonCompare").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("deviceVtonSave").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("deviceVtonResultLabel").assertDoesNotExist()
        assertEquals(2, calls.get())
    }
}
