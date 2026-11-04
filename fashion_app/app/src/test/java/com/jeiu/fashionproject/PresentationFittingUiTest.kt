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
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarProfileStore
import com.jeiu.fashionproject.data.AvatarFittingStore
import com.jeiu.fashionproject.data.AvatarOverlay
import com.jeiu.fashionproject.data.WardrobeStore
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
        compose.onNodeWithTag("aiTryOnGuide").performScrollTo().assertTextContains("실제 착장 이미지를 생성할 수는 없어요", substring = true)
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
    @Test fun wandWithoutPhotoShowsInputNotice() {
        compose.setContent { VirtualFittingScreen {} }
        compose.onNodeWithTag("aiTryOnWand").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("먼저 전신 사진을 선택해주세요.")
        compose.onNodeWithTag("aiTryOnGuide").assertDoesNotExist()
    }
    @Test fun wandWithoutClothesShowsNoticeAndAlignmentRetainsManualScaleControls() {
        var requestCode = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I,O> onLaunch(code: Int, contract: ActivityResultContract<I,O>, input:I, options:ActivityOptionsCompat?) { requestCode=code }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry=registry }
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { VirtualFittingScreen {} } }
        compose.onNodeWithText("전신 사진 선택").performClick()
        compose.runOnIdle { registry.dispatchResult(requestCode, Uri.parse("content://invalid/manual")) }
        compose.onNodeWithTag("aiTryOnWand").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("먼저 옷장에서 입어볼 옷을 선택해주세요.")
        val id = com.jeiu.fashionproject.data.WardrobeStore.items.value.first().id
        compose.onNodeWithTag("manualWardrobe").performScrollTo()
        compose.onNodeWithTag("manualGarment-$id").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("벗기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("manualScaleUp").performScrollTo().performClick()
        compose.onNodeWithTag("manualScale").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(1.1f, .4f..3f)))
        compose.onNodeWithTag("manualAutoAlign").assertIsEnabled().performClick()
        // Without detected joints, auto alignment must preserve the user's
        // manual placement rather than inventing a body position.
        compose.onNodeWithTag("fittingInputNotice").assertExists()
        compose.onNodeWithTag("manualScale").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(1.1f, .4f..3f)))
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("자동핏을 적용하지 않았어요", substring = true)
        compose.onNodeWithTag("manualAlpha").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("벗기", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("manualAutoAlign").assertIsNotEnabled()
    }

    @Test fun photoMagicWandOpensGenerativeFlowWithoutUnapprovedNetworkCall() {
        com.jeiu.fashionproject.data.TryOnSession.clear()
        var requestCode = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I,O> onLaunch(code: Int, contract: ActivityResultContract<I,O>, input: I, options: ActivityOptionsCompat?) { requestCode = code }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { VirtualFittingScreen(onBack = {}) }
        }
        compose.onNodeWithText("전신 사진 선택").performClick()
        compose.runOnIdle { registry.dispatchResult(requestCode, Uri.parse("content://invalid/ai-wand")) }
        val id = WardrobeStore.items.value.first().id
        compose.onNodeWithTag("manualWardrobe").performScrollTo()
        compose.onNodeWithTag("manualGarment-$id").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("벗기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("manualAutoAlign").assertExists()
        compose.onNodeWithTag("aiTryOnWand").assertTextContains("마법봉 · AI 착장 사진 생성", substring = true)
        compose.onNodeWithTag("aiTryOnWand").performClick()
        compose.onNodeWithTag("aiTryOnGuide").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aiTryOnGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("aiTryOnSuccess").assertDoesNotExist()
    }

    @Test fun photoAndAvatarModesKeepSeparateEntryPaths() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        var setupOpened = 0
        compose.setContent { VirtualFittingScreen(onBack = {}, onAvatarSetup = { setupOpened++ }) }
        compose.onNodeWithTag("fittingAvatarMode").performClick()
        compose.onNodeWithTag("fittingGoAvatarSetup").assertIsDisplayed().performClick()
        compose.runOnIdle { Assert.assertEquals(1, setupOpened) }
        compose.onNodeWithTag("aiTryOnWand").assertDoesNotExist()
        compose.onNodeWithTag("fittingPhotoMode").performClick()
        compose.onNodeWithText("전신 사진 선택").assertIsDisplayed()
        compose.onNodeWithTag("aiTryOnWand").assertExists()
    }

    @Test fun savedProfileOpensAvatarCanvasWithoutRequestingPhoto() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        Assert.assertTrue(AvatarProfileStore.save(app, AvatarProfile(170f, 65f)))
        try {
            compose.setContent { VirtualFittingScreen(onBack = {}) }
            compose.onNodeWithTag("fittingAvatarMode").performClick()
            compose.onNodeWithTag("fittingAvatarViewport").assertExists()
            compose.onNodeWithTag("avatarAutoFit").assertIsNotEnabled()
            compose.onNodeWithText("전신 사진 선택").assertDoesNotExist()
        } finally { AvatarProfileStore.clear(app) }
    }

    @Test fun profileChangePreservesManualPlacementUntilExplicitAutoFit() {
        val app=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app);AvatarFittingStore.clear(app);WardrobeStore.load(app)
        val item=WardrobeStore.items.value.first();val prior=AvatarProfile(170f,65f)
        Assert.assertTrue(AvatarProfileStore.save(app,prior))
        Assert.assertTrue(AvatarFittingStore.save(app,listOf(AvatarOverlay(item.id,.5f,.4f,.3f,1.1f,8f))))
        try {
            compose.setContent { VirtualFittingScreen(onBack={}) }
            compose.onNodeWithTag("fittingAvatarMode").performClick()
            compose.waitUntil(5000) { runCatching {compose.onNodeWithTag("avatarAutoFit").assertIsEnabled()}.isSuccess }
            compose.runOnIdle { Assert.assertTrue(AvatarProfileStore.save(app,prior.copy(chest=1.2f,shoulder=1.1f))) }
            compose.onNodeWithTag("avatarSaveFitting").performClick()
            compose.runOnIdle {
                val retained=AvatarFittingStore.load(app).single()
                Assert.assertEquals(.3f,retained.width,.001f);Assert.assertEquals(1.1f,retained.scale,.001f)
                Assert.assertEquals(8f,retained.rotation,.001f)
            }
            compose.onNodeWithTag("avatarAutoFit").performClick()
            compose.onNodeWithTag("avatarSaveFitting").performClick()
            compose.runOnIdle {Assert.assertEquals(1f,AvatarFittingStore.load(app).single().scale,.001f)}
        } finally {AvatarFittingStore.clear(app);AvatarProfileStore.clear(app)}
    }

    @Test fun savedWardrobePlacementReturnsInAvatarMode() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        AvatarFittingStore.clear(app)
        WardrobeStore.load(app)
        val item = WardrobeStore.items.value.first()
        Assert.assertTrue(AvatarProfileStore.save(app, AvatarProfile(170f, 65f)))
        Assert.assertTrue(AvatarFittingStore.save(app,
            listOf(AvatarOverlay(item.id, .5f, .4f, .3f, 1.1f, 0f))))
        try {
            compose.setContent { VirtualFittingScreen(onBack = {}) }
            compose.onNodeWithTag("fittingAvatarMode").performClick()
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithTag("avatarAutoFit").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithTag("avatarAutoFit").assertIsEnabled()
            compose.onNodeWithTag("avatarSaveFitting").assertIsEnabled()
            compose.onNodeWithTag("avatarAutoFit").performClick()
            compose.onNodeWithTag("avatarSaveFitting").performClick()
            compose.runOnIdle {
                Assert.assertEquals(1f, AvatarFittingStore.load(app).single().scale)
            }
        } finally { AvatarFittingStore.clear(app); AvatarProfileStore.clear(app) }
    }

}
