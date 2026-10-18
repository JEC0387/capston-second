package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.jeiu.fashionproject.data.TryOnGarment
import com.jeiu.fashionproject.ui.screens.AiTryOnScreen
import com.jeiu.fashionproject.ui.screens.VirtualFittingScreen
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose UI, isolated Application/files. No paid request, external photo, or physical camera. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PresentationFittingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun missingServiceDoesNotPromiseGenerationAndCanReturnToClothes() {
        var returned = 0
        compose.setContent {
            Box(Modifier.height(360.dp)) { AiTryOnScreen(null, emptyList(), { returned++ }, serviceConfigured = false) }
        }
        compose.onNodeWithTag("aiTryOnGuide").performScrollTo().assertTextContains("수동 배치", substring = true)
        compose.onNodeWithText("원하는 옷을 선택하고 마법봉을 누르면 AI가 자연스럽게 입혀줘요 ✨").assertDoesNotExist()
        compose.onNodeWithTag("aiTryOnNotConfigured").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aiTryOnGenerate").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("옷 선택으로 돌아가기").performScrollTo().performClick()
        compose.runOnIdle { Assert.assertEquals(1, returned) }
    }
    @Test fun unsupportedShoesAreExplainedAndCannotStartGeneration() {
        compose.setContent {
            Box(Modifier.height(360.dp)) {
                AiTryOnScreen(null, listOf(TryOnGarment("shoe", "신발", "신발", "file:///shoe.jpg")), {}, serviceConfigured = false)
            }
        }
        compose.onNodeWithText("신발·액세서리는 이번 AI 생성에서 제외돼요. 수동 배치는 계속 사용할 수 있어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aiTryOnGenerate").assertIsNotEnabled()
        compose.onNodeWithText("AI 피팅이 완료됐어요! ✨").assertDoesNotExist()
    }
    @Test fun manualControlsRemainReachableWithPhotoInSmallViewport() {
        var requestCode = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { requestCode = code }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Box(Modifier.height(360.dp)) { VirtualFittingScreen(onBack = {}) }
            }
        }
        compose.onNodeWithText("전신 사진 선택").performClick()
        compose.runOnIdle { registry.dispatchResult(requestCode, Uri.parse("content://invalid-test-provider/manual-photo")) }
        compose.onNodeWithTag("manualFittingControls").assertIsDisplayed()
        compose.onNodeWithText("크기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("투명도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("입어볼 옷 선택 · 종류별로 한 벌씩 배치해요").performScrollTo().assertIsDisplayed()
    }
}
