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
import kotlinx.coroutines.test.StandardTestDispatcher
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
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }

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
            MaterialTheme { ServerOutfitVtonScreen(null, {}, vm) }
        } }
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.providerInfo != null && !vm.state.busy }
        // Assert the complete disclosure. assertTextContains defaults to matching a
        // complete semantics text element, not an arbitrary substring of it.
        compose.onNodeWithTag("serverOutfitProvider").performScrollTo()
            .assertTextEquals("외부 처리: Genlook · 스마트폰이 아닌 제3자 서버에서 실행")
        compose.onNodeWithTag("serverOutfitCost").performScrollTo()
            .assertTextEquals("요청당 최대 3크레딧을 예약해요. 실제 청구액·환불 확정이 아니며 자동 충전하지 않아요.")
        compose.onNodeWithTag("serverOutfitExternalRetention").performScrollTo()
            .assertTextEquals("Genlook 이미지 보관 설정 1일 · 앱 서버 결과 보관 시간과 별도예요. 생성 기록·동의·백업 보관 및 삭제 제한은 아래 정책을 확인해주세요.")
        compose.onNodeWithTag("serverOutfitConsentText").performScrollTo()
            .assertTextEquals("선택한 인물·상의·하의·신발 4장의 이미지와 중간 생성 결과를 승인된 앱 서버를 통해 Genlook으로 전송하고 표시된 비용·보관 정책으로 처리하는 데 동의해요.")
        compose.onNodeWithTag("serverOutfitConsent").performScrollTo().performClick().assertIsOn()
        // Changing the disclosed provider policy resets consent even if the API policy ID is unchanged.
        config = config.copy(providerInfo = info.copy(notice = "Revised synthetic processing disclosure."))
        compose.onNodeWithTag("serverOutfitCheck").performScrollTo().performClick()
        await { vm.state.config?.providerInfo?.notice == "Revised synthetic processing disclosure." && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitConsent").performScrollTo().assertIsOff()
        compose.onNodeWithTag("serverOutfitGenerate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("serverOutfitCancel").performScrollTo().assertTextEquals("대기 종료·후속 생성 중단").performClick()
        await { vm.state.phase == "interrupted" && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitError").performScrollTo()
            .assertTextEquals("Genlook 작업 종료·과금 여부를 확인하지 못했어요. 후속 생성을 차단했으며 외부 작업 취소·환불·삭제 완료를 뜻하지 않아요.")
        assertEquals(1, cancelRequests)
        assertEquals(0, posts)
        assertNull(vm.state.result)
    }
}
