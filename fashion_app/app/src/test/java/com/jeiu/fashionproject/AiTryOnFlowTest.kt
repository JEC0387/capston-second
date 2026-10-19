package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.TryOnViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiTryOnFlowTest {
    @get:Rule val compose = createComposeRule()
    private val photo = Uri.parse("content://mock/person-original.jpg")
    private val clothes = listOf(TryOnGarment("top", "선택한 상의", "상의", "content://mock/garment-original.jpg"))
    private val saved = SavedStateHandle()
    private val fake = FakeGateway()
    private val model = TryOnViewModel(saved).also { it.gateway = fake }
    private var visible by mutableStateOf(true)
    private var noEntryInputs by mutableStateOf(false)
    private var registryCode = 0
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { registryCode = requestCode }
    }
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun connect() { assertTrue(TryOnSession.configure("https://mock.example", "mock-personal-access-token-0000000001")) }
    @After fun cleanup() { compose.runOnIdle { model.viewModelScope.cancel(); TryOnSession.clear() } }
    private fun screen() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Box(Modifier.height(640.dp)) {
                    if (visible) AiTryOnScreen(if (noEntryInputs) null else photo, if (noEntryInputs) emptyList() else clothes, { visible = false }, model = model)
                }
            }
        }
    }
    private fun approve() {
        compose.onNodeWithTag("aiTryOnGenerate").performClick()
        compose.onNodeWithTag("aiTryOnConfirm").performClick()
    }
    private fun finished() { compose.waitUntil(5000) { !model.state.pending } }
    @Test fun declineConsentNeverUploadsOrGenerates() {
        screen(); compose.onNodeWithTag("aiTryOnGenerate").performClick()
        compose.onNodeWithText("전송하지 않기").performClick()
        compose.runOnIdle { assertEquals(0, fake.posts); assertTrue(fake.images.isEmpty()) }
    }
    @Test fun successUsesOriginalInputsAndSupportsComparison() {
        screen(); approve(); finished()
        compose.runOnIdle {
            assertEquals(1, fake.posts)
            assertEquals(listOf(photo.toString(), clothes[0].image), fake.images)
        }
        compose.onNodeWithTag("aiTryOnSuccess").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("원본과 비교").performScrollTo().performClick()
        compose.onNodeWithText("AI 결과 보기").assertIsDisplayed().performClick()
    }
    @Test fun duplicateClicksAndReentryDuringLoadingUseOnePaidSubmission() {
        fake.submitGate = CompletableDeferred()
        screen(); approve()
        compose.onNodeWithTag("aiTryOnGenerate").assertIsNotEnabled()
        compose.runOnIdle { model.generate(app, photo, clothes, true); visible = false }
        compose.runOnIdle { noEntryInputs = true; visible = true; model.resume() }
        compose.onNodeWithText("상의 · 선택한 상의").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aiTryOnGenerate").assertIsNotEnabled()
        compose.runOnIdle { fake.submitGate!!.complete(Unit) }
        finished(); compose.runOnIdle { assertEquals(1, fake.posts) }
    }
    @Test fun apiErrorAndRetryRequireAnotherApprovalAndKeepPreviousResult() {
        screen(); approve(); finished()
        compose.runOnIdle { fake.statusValue = TryOnJob("failed", "pose", 1, 1, false) }
        approve(); finished()
        compose.runOnIdle { assertEquals(2, fake.posts); assertNotNull(model.state.result); assertTrue(model.state.error!!.contains("전신")) }
        compose.onNodeWithTag("aiTryOnGenerate").performClick()
        compose.onNodeWithText("전송하지 않기").performClick()
        compose.runOnIdle { assertEquals(2, fake.posts) }
    }
    @Test fun serverTimeoutIsShownWithoutAutomaticRegeneration() {
        fake.statusValue = TryOnJob("timeout", "timeout", 1, 1, false)
        screen(); approve(); finished()
        compose.runOnIdle { assertEquals(1, fake.posts); assertTrue(model.state.error!!.contains("초과")); assertNull(model.state.result) }
    }
    @Test fun unknownPostResponseOnlyResumesByGet() {
        fake.loseResponse = true; screen(); approve()
        compose.waitUntil(5000) { model.state.pending && !model.state.watching }
        compose.onNodeWithTag("aiTryOnGenerate").assertIsNotEnabled()
        compose.runOnIdle { fake.loseResponse = false; model.resume() }
        finished(); compose.runOnIdle { assertEquals(1, fake.posts); assertTrue(fake.gets > 0) }
    }
    @Test fun processRestorationUsesSavedRequestIdAndNeverPosts() {
        screen()
        val restored = TryOnViewModel(SavedStateHandle(mapOf("requestId" to "saved-id", "base" to "https://mock.example"))).also { it.gateway = fake }
        compose.runOnIdle { restored.resume() }
        compose.waitUntil(5000) { !restored.state.pending }
        compose.runOnIdle { assertEquals(0, fake.posts); assertEquals("saved-id", fake.lastGet); restored.viewModelScope.cancel() }
    }
    @Test fun jpegSavingUsesDocumentPickerAndActualResultBytes() {
        screen(); approve(); finished()
        val file = File(app.cacheDir, "mock-result.jpg")
        compose.onNodeWithText("결과 이미지 저장").performScrollTo().performClick()
        compose.runOnIdle { registry.dispatchResult(registryCode, Uri.fromFile(file)) }
        compose.waitUntil(5000) { file.exists() && file.length() == fake.jpeg.size.toLong() }
        assertArrayEquals(fake.jpeg, file.readBytes()); file.delete()
    }
    @Test fun missingPhotoAndMissingClothesNeverReachGateway() {
        screen()
        compose.runOnIdle {
            model.generate(app, null, clothes, true)
            assertTrue(model.state.error!!.contains("전신"))
            model.generate(app, photo, emptyList(), true)
            model.generate(app, photo, clothes, false)
            assertEquals(0, fake.posts); assertTrue(fake.images.isEmpty())
        }
    }
    @Test fun cancellationContactsExistingJobAndNeverPostsAgain() {
        fake.submitGate = CompletableDeferred(); screen(); approve()
        compose.runOnIdle { model.cancel(); fake.submitGate!!.complete(Unit) }
        finished(); compose.runOnIdle { assertEquals(1, fake.posts); assertEquals(1, fake.cancels) }
    }
    @Test fun proxySettingsRejectHttpAndCredentialsInUrl() {
        screen()
        assertFalse(TryOnSession.configure("http://example.com", "x".repeat(40)))
        assertFalse(TryOnSession.configure("https://user:pass@example.com", "x".repeat(40)))
        assertFalse(TryOnSession.configure("https://example.com", "short"))
    }

    private class FakeGateway : TryOnGateway {
        var posts = 0; var gets = 0; var cancels = 0; var lastGet: String? = null
        var loseResponse = false
        var submitGate: CompletableDeferred<Unit>? = null
        var statusValue = TryOnJob("completed", null, 1, 1, true)
        val images = mutableListOf<String>()
        // A genuine local JPEG, never a manual-composite fallback.
        val jpeg: ByteArray = java.io.ByteArrayOutputStream().also {
            val bitmap = android.graphics.Bitmap.createBitmap(32, 48, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle()
        }.toByteArray()
        override suspend fun image(context: Context, source: String): String { images += source; return "mock-encoded-$source" }
        override suspend fun check(connection: TryOnConnection) {}
        override suspend fun submit(connection: TryOnConnection, id: String, person: String, garments: List<Pair<String,String>>) {
            posts++; submitGate?.await(); if (loseResponse) throw IOException("mock response lost")
        }
        override suspend fun status(connection: TryOnConnection, id: String): TryOnJob { gets++; lastGet = id; return statusValue }
        override suspend fun result(connection: TryOnConnection, id: String) = jpeg
        override suspend fun cancel(connection: TryOnConnection, id: String) { cancels++; statusValue = TryOnJob("cancelled", "cancelled", 1, 1, false) }
    }
}
