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
import com.jeiu.fashionproject.ui.screens.ServerOutfitVtonScreen
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
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

/** Mock gateway + real PNG/SAF UI only, never evidence of model or phone execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class P48ServerOutfitUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
        finally { bitmap.recycle() }
    }
    private class Documents : ActivityResultRegistryOwner {
        var request = 0
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { request = requestCode }
        }
        fun reply(uri: Uri) = activityResultRegistry.dispatchResult(request, uri)
    }
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }

    @Test fun mockServerGenerateCompareSaveReopenAndReentryDoNotResubmit() {
        val selection = DeviceVtonSelection("content://synthetic/person", 1, DeviceGarmentRole.entries.map {
            DeviceGarmentInput("synthetic-$it", it, "content://synthetic/$it", "400,600,125,150,60,1,0,2") })
        val original = png(Color.RED); val result = png(Color.BLUE)
        var posts = 0
        val session = OutfitServerSession("https://synthetic.example", "synthetic-only")
        val cfg = OutfitServerConfig(true, "", "synthetic", "revision", "a".repeat(64), "test-policy", 300, 5*1024*1024, 29*1024*1024)
        var ticket: OutfitServerTicket? = null
        val store = object : OutfitTicketStore {
            override fun read() = ticket
            override fun write(value: OutfitServerTicket) { ticket = value }
        }
        val status = OutfitServerStatus("completed", null, 1.0, true, deviceSha256(result), System.currentTimeMillis() + 300000)
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = cfg
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus { posts++; return status }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket) = status
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket) = status.copy(status = "cancelled", resultAvailable = false)
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus) = result
        }
        val preparation = CompletableDeferred<Unit>()
        val vm = ServerOutfitVtonViewModel(gateway, { store }, { _, id, selected, model ->
            preparation.await()
            val person = DeviceImage(original, 1000, 2000)
            DevicePreparedJob(id, selected, model, person, DeviceGarmentRole.entries.associateWith { DeviceImage(png(Color.GREEN), 256, 256) }, "b".repeat(64))
        })
        val documents = Documents(); val showing = mutableStateOf(true)
        val selectedInput = mutableStateOf(selection)
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { if (showing.value) ServerOutfitVtonScreen(selectedInput.value, { showing.value = false }, vm) }
        } }
        compose.onNodeWithTag("serverOutfitPersonPreview").performScrollTo().assertExists()
        compose.onNodeWithTag("serverOutfitGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.ready == true }
        // Selection and server readiness alone cannot submit: the just-in-time consent dialog is required.
        compose.onNodeWithTag("serverOutfitGenerate").performClick()
        compose.onNodeWithTag("serverOutfitConsent").assertIsOff()
        compose.onNodeWithTag("serverOutfitConsentConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("serverOutfitConsent").performClick().assertIsOn()
        compose.onNodeWithTag("serverOutfitConsentConfirm").performClick()
        await { vm.state.phase == "preparing" && vm.state.busy }
        compose.onNodeWithTag("serverOutfitProgress").assertExists()
        compose.onNodeWithTag("serverOutfitGenerate").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitCancelPreparation").performClick()
        compose.runOnIdle { preparation.complete(Unit) }
        await { vm.state.phase == "cancelled" && !vm.state.busy }
        assertEquals(0, posts); assertNull(ticket)
        compose.onNodeWithTag("serverOutfitGenerate").performClick()
        compose.onNodeWithTag("serverOutfitConsent").performClick()
        compose.onNodeWithTag("serverOutfitConsentConfirm").performClick()
        await { vm.state.result != null && !vm.state.busy }
        assertEquals(1, posts)
        compose.onNodeWithTag("serverOutfitResultImage", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithTag("serverOutfitPersonPreview").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitShowOriginal").performScrollTo().performClick()
        compose.onNodeWithTag("serverOutfitOriginalImage", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("serverOutfitResultImage", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitShowResult").performScrollTo().performClick()
        compose.onNodeWithTag("serverOutfitResultImage", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("serverOutfitSave").performClick()
        compose.onNodeWithTag("serverOutfitOpen").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitSave").assertIsNotEnabled()
        val target = File(app.cacheDir, "p48-synthetic-${System.nanoTime()}.png")
        try {
            compose.runOnIdle { documents.reply(Uri.fromFile(target)) }
            compose.waitUntil(15000) { target.exists() && target.length() > 0 && compose.onAllNodesWithText(
                "사진을 저장했어요.").fetchSemanticsNodes().isNotEmpty() }
            assertArrayEquals(result, target.readBytes())
            val bitmap = BitmapFactory.decodeFile(target.path)
            try { assertEquals(Color.BLUE, bitmap.getPixel(32, 48)) } finally { bitmap.recycle() }
            compose.onNodeWithTag("serverOutfitOpen").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("저장 이미지").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("닫기").performClick()
            compose.onNodeWithTag("serverOutfitChangeClothes").performClick()
            compose.runOnIdle { showing.value = true }
            compose.onNodeWithTag("serverOutfitResultImage", useUnmergedTree = true).assertExists()
            assertEquals(1, posts)
        } finally { target.delete() }
    }
}
