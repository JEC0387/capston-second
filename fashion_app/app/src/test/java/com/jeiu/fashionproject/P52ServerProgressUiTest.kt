package com.jeiu.fashionproject

import android.app.Application
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
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

/** Synthetic stage observations, no external request, key, image picker or generated output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w500dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class P52ServerProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }

    /** Bounded user gestures avoid the semantic ScrollBy loop observed in this Robolectric run. */
    private fun visible(label: String, matcher: SemanticsMatcher): SemanticsNodeInteraction {
        var lastBounds = "not observed"
        for (attempt in 0..10) {
            val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
            assertEquals("$label must identify exactly one node", 1, nodes.size)
            val node = nodes.single()
            val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val top = node.positionInRoot.y
            val bottom = top + node.size.height
            val fullyVisible = top >= viewport.top - 1f && bottom <= viewport.bottom + 1f
            val direction = if (fullyVisible) "visible" else if (top < viewport.top) "down" else "up"
            lastBounds = "target=[$top,$bottom] viewport=[${viewport.top},${viewport.bottom}]"
            println("P52_TOUCH_SCROLL label=$label attempt=$attempt $lastBounds direction=$direction")
            if (fullyVisible) return compose.onNode(matcher).assertIsDisplayed()
            if (attempt == 10) break
            compose.onRoot().performTouchInput {
                val x = width * .5f
                val upper = height * .2f
                val lower = height * .8f
                val startY = if (direction == "up") lower else upper
                val endY = if (direction == "up") upper else lower
                swipe(Offset(x, startY), Offset(x, endY), durationMillis = 600)
            }
        }
        throw AssertionError("$label was not fully visible after 10 touch swipes: $lastBounds")
    }

    private fun visibleTag(tag: String) = visible(tag, hasTestTag(tag))

    @Test fun pairedStagesDescribeObservedWorkAndDisappearOnUnconfirmedTerminalState() {
        val session = OutfitServerSession("https://synthetic.example", "synthetic-only")
        val mode = "clothing-pair-then-shoes"
        val revision = "genlook-paired-v1-20261106-0123456789abcdef"
        val ticket = OutfitServerTicket("00000000-0000-4000-8000-000000000052", "b".repeat(64), revision,
            session.base, session.accountKey, "c".repeat(64), 1000, 301000, "a".repeat(64), "genlook")
        val info = OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", mode, 3, false, 1,
            "Synthetic external processing policy; image and record retention differ.")
        val config = OutfitServerConfig(true, "", "genlook/tryon-api-v1", revision, ticket.modelSha,
            "synthetic-policy", 300, 5 * 1024 * 1024, 29 * 1024 * 1024, info)
        var observed = OutfitServerStatus("running", null, .1, false, null, 301000,
            providerProgress = OutfitServerProgress(mode, 1, 2, "waiting_for_generation"))
        val store = object : OutfitTicketStore {
            override fun read() = ticket
            override fun write(ticket: OutfitServerTicket) { error("No new journal") }
        }
        var gets = 0
        var posts = 0
        val gateway = object : OutfitServerGateway {
            override fun session() = session
            override suspend fun config(session: OutfitServerSession) = config
            override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
                posts++; error("No new generation")
            }
            override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus { gets++; return observed }
            override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus = error("No cancellation")
            override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray = error("No result")
        }
        val documents = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                    options: ActivityOptionsCompat?) { error("No file picker") }
            }
        }
        // A zero local wait budget ends polling after one observed GET without implying server cancellation.
        val vm = ServerOutfitVtonViewModel(gateway, { store }, clock = { 2000L }, localWaitMillis = 0)
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides documents) {
            MaterialTheme { ServerOutfitVtonScreen(null, {}, vm) }
        } }
        visibleTag("serverOutfitCheck").performClick()
        await { vm.state.config != null && !vm.state.busy }
        assertEquals("Recovered job must remain after config lookup", ticket, vm.state.ticket)
        // Assert the count before Compose's full-tree error renderer; preserve the same UI requirement.
        assertEquals("Exactly one existing-job lookup button must be present", 1,
            compose.onAllNodesWithTag("serverOutfitResume").fetchSemanticsNodes().size)
        visibleTag("serverOutfitResume").performClick()
        try { await { vm.state.phase == "waiting" && !vm.state.busy } }
        catch (failure: AssertionError) {
            throw AssertionError("Resume phase=${vm.state.phase} busy=${vm.state.busy} GET=$gets POST=$posts error=${vm.state.error}", failure)
        }
        visibleTag("serverOutfitStage").assertTextEquals("1/2단계 · 상의·하의 함께 · 생성 결과 대기 중")
        visible("stageDisclaimer", hasText("서버가 확인한 처리 단계예요. 추론 완료율이나 착장 품질 합격을 뜻하지 않아요."))
            .assertExists()

        observed = observed.copy(providerProgress = OutfitServerProgress(mode, 2, 2, "validating_result"))
        visibleTag("serverOutfitResume").performClick()
        await { vm.state.status?.providerProgress?.stageIndex == 2 && !vm.state.busy }
        visibleTag("serverOutfitStage").assertTextEquals("2/2단계 · 신발 · 반환 이미지 확인 중")
        assertNull(vm.state.result)

        observed = observed.copy(status = "interrupted", error = "remote_execution_unconfirmed", providerProgress = null)
        visibleTag("serverOutfitResume").performClick()
        await { vm.state.phase == "interrupted" && !vm.state.busy }
        compose.onNodeWithTag("serverOutfitStage").assertDoesNotExist()
        visibleTag("serverOutfitError").assertTextEquals(
            "Genlook 작업 종료·과금 여부를 확인하지 못했어요. 후속 생성을 차단했으며 외부 작업 취소·환불·삭제 완료를 뜻하지 않아요.")
        assertEquals(3, gets)
        assertEquals(0, posts)
        assertNull(vm.state.result)
        assertEquals(ticket, vm.state.ticket)
    }
}
