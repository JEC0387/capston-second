package com.jeiu.fashionproject

import android.app.Application
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

/** Synthetic gateway and metadata only. No file picker, external call, billing or phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class P50ServerProviderDisclosureUiTest {
    @get:Rule val compose = createComposeRule()
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
    private fun completeSelection() = DeviceVtonSelection("content://synthetic/person", 1, listOf(
        DeviceGarmentInput("top", DeviceGarmentRole.TOP, "content://synthetic/top"),
        DeviceGarmentInput("bottom", DeviceGarmentRole.BOTTOM, "content://synthetic/bottom"),
        DeviceGarmentInput("shoes", DeviceGarmentRole.SHOES, "content://synthetic/shoes")))

    @Test fun externalDisclosureConsentResetAndCancellationAreVisibleWithoutPosting() {
        val session = OutfitServerSession("https://synthetic.example", "synthetic-only")
        val info = OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", "sequential-3", 3, false, 1,
            "Synthetic reviewed policy: EU storage, global generation and separate backup retention.")
        var config = OutfitServerConfig(true, "", "genlook/tryon-api-v1", "genlook-sequential-v1-test",
            "a".repeat(64), "synthetic-policy", 300, 5*1024*1024, 29*1024*1024, info)
        val ticket = OutfitServerTicket("00000000-0000-4000-8000-000000000050", "b".repeat(64),
            config.revision, session.base, session.accountKey, "c".repeat(64), 1000, 301000, config.modelSha, "genlook")
        val store = object : OutfitTicketStore {
            override fun read() = ticket
            override fun write(ticket: OutfitServerTicket) { error("No generation submission is allowed in this test") }
        }
        var posts = 0
        var cancelRequests = 0
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = config
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++; error("Unexpected POST")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket) =
                OutfitServerStatus("running", null, .1, false, null, System.currentTimeMillis()+300000)
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus {
                cancelRequests++
                return OutfitServerStatus("interrupted", "remote_execution_unconfirmed", .1, false, null, System.currentTimeMillis()+300000)
            }
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray =
                error("No generated result is available")
        }
        val documents = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    error("No gallery or storage picker may open")
                }
            }
        }
        val vm = ServerOutfitVtonViewModel(gateway, { store })
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { ServerOutfitVtonScreen(completeSelection(), {}, vm) }
        } }
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.providerInfo != null && !vm.state.busy }
        // Policy and cost are intentionally shown at the final consent point, not on the main screen.
        compose.onNodeWithTag("serverOutfitProvider").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("serverOutfitConsentText").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitPolicyDetails").assertDoesNotExist()
        assertEquals("An unresolved prior request prevents a second paid generation", ticket, vm.state.ticket)
        // Changing the disclosed provider policy resets consent even if the API policy ID is unchanged.
        config = config.copy(providerInfo = info.copy(notice = "Revised synthetic processing disclosure."))
        vm.invalidateConnection()
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.providerInfo?.notice == "Revised synthetic processing disclosure." && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitGenerate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("serverOutfitConsent").assertDoesNotExist()
        compose.onNodeWithTag("serverOutfitCancel").performScrollTo().assertTextEquals("대기 종료").performClick()
        await { vm.state.phase == "interrupted" && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitError").performScrollTo()
            .assertTextEquals("요청 상태를 확인하지 못했어요. 기존 요청만 조회할 수 있어요.")
        assertEquals(1, cancelRequests)
        assertEquals(0, posts)
        assertNull(vm.state.result)
    }
}
