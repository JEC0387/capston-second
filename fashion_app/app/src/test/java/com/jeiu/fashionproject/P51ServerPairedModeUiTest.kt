package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.ServerOutfitVtonScreen
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** UI state and synthetic PNG only; not a vendor generation, payment or device result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class P51ServerPairedModeUiTest {
    @get:Rule val compose = createComposeRule()
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
    private fun completeSelection() = DeviceVtonSelection("content://synthetic/person", 1, listOf(
        DeviceGarmentInput("top", DeviceGarmentRole.TOP, "content://synthetic/top"),
        DeviceGarmentInput("bottom", DeviceGarmentRole.BOTTOM, "content://synthetic/bottom"),
        DeviceGarmentInput("shoes", DeviceGarmentRole.SHOES, "content://synthetic/shoes")))

    @Test fun pairedDisclosureResetsConsentAndDoesNotRelabelRestoredSequentialResult() {
        val session = OutfitServerSession("https://synthetic.example", "synthetic-only")
        val info = OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", "sequential-3", 3, false, 1,
            "Synthetic unchanged policy: external processing and separate record retention.")
        var config = OutfitServerConfig(true, "", "genlook/tryon-api-v1",
            "genlook-sequential-v2-preservation-20261106-0123456789abcdef", "a".repeat(64),
            "synthetic-policy", 300, 5 * 1024 * 1024, 29 * 1024 * 1024, info)
        val oldTicket = OutfitServerTicket("00000000-0000-4000-8000-000000000051", "b".repeat(64),
            config.revision, session.base, session.accountKey, "c".repeat(64), 1000, 301000, config.modelSha, "")
        val png = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(Color.BLUE); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            finally { bitmap.recycle() }
            output.toByteArray()
        }
        val oldSource = OutfitServerSource("genlook", "tryon/v1", "not_disclosed",
            listOf("synthetic-top", "synthetic-bottom", "synthetic-shoes"), "sequential-3", 3, 12, 9)
        val store = object : OutfitTicketStore {
            override fun read() = oldTicket
            override fun write(ticket: OutfitServerTicket) { error("Must not replace old journal") }
        }
        var posts = 0
        var reads = 0
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = config
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++; error("No generation is allowed")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
                assertEquals(oldTicket, ticket)
                return OutfitServerStatus("completed", null, 1.0, true, deviceSha256(png), 301000, oldSource)
            }
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus =
                error("No cancellation needed")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray {
                assertEquals(oldTicket, ticket); reads++; return png.copyOf()
            }
        }
        val documents = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                    options: ActivityOptionsCompat?) { error("No image or document picker may open") }
            }
        }
        val vm = ServerOutfitVtonViewModel(gateway, { store }, clock = { 2000L })
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { ServerOutfitVtonScreen(completeSelection(), {}, vm) }
        } }
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config != null && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitGenerate").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitPolicyDetails").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitConsent").assertDoesNotExist()
        assertEquals("Recovered unresolved work blocks another request", oldTicket, vm.state.ticket)

        config = config.copy(revision = "genlook-paired-v1-20261106-fedcba9876543210", modelSha = "e".repeat(64),
            providerInfo = info.copy(mode = "clothing-pair-then-shoes"))
        vm.invalidateConnection()
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.providerInfo?.mode == "clothing-pair-then-shoes" && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitGenerate").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitConsent").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitCancel").assertExists()

        compose.onNodeWithTag("serverOutfitResume").performClick()
        await { vm.state.result != null && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitResultLabel").performScrollTo().assertTextEquals("이전에 요청한 착장이에요.")
        compose.onNodeWithTag("serverOutfitResultSource").assertDoesNotExist()
        compose.onNodeWithText("Genlook Try-On API").assertDoesNotExist()
        assertEquals(0, posts)
        assertEquals(1, reads)
        assertEquals(oldTicket, vm.state.ticket)
        assertArrayEquals(png, vm.state.result!!.png)
    }
}
