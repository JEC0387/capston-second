package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AvatarProfileStore
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarPart
import com.jeiu.fashionproject.data.AvatarCentimeters
import com.jeiu.fashionproject.data.AvatarCm
import com.jeiu.fashionproject.data.AvatarValueOrigin
import com.jeiu.fashionproject.ui.screens.BodyMethodSelectionScreen
import com.jeiu.fashionproject.ui.screens.BodyAvatarSetupScreen
import com.jeiu.fashionproject.ui.screens.BodySizeComparisonScreen
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BodyMethodSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun photoAndAvatarCardsNavigateToDistinctFlows() {
        var photo = 0
        var avatar = 0
        compose.setContent {
            BodyMethodSelectionScreen(
                onBack = {}, onPhotoAnalysis = { photo++ }, onAvatarSetup = { avatar++ })
        }
        compose.onNodeWithTag("bodyMethodPhoto").assertIsDisplayed().performClick()
        compose.onNodeWithTag("bodyMethodAvatar").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, photo)
            assertEquals(1, avatar)
        }
    }

    @Test fun fourEntryPathsAndBackRemainReachable() {
        var photo = 0; var avatar = 0; var size = 0; var history = 0; var back = 0
        compose.setContent { BodyMethodSelectionScreen(onBack = { back++ },
            onPhotoAnalysis = { photo++ }, onAvatarSetup = { avatar++ },
            onSizeComparison = { size++ }, onHistory = { history++ }) }
        compose.onNodeWithTag("bodyMethodPhoto").performClick()
        compose.onNodeWithTag("bodyMethodAvatar").performClick()
        compose.onNodeWithTag("bodyMethodSize").performScrollTo().performClick()
        compose.onNodeWithTag("bodyMethodHistory").performScrollTo().performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.runOnIdle {
            assertEquals(1, photo); assertEquals(1, avatar)
            assertEquals(1, size); assertEquals(1, history); assertEquals(1, back)
        }
    }

    @Test fun sizeComparisonOffersMeasurementOrPhotoEntry() {
        var photo = 0
        compose.setContent { BodySizeComparisonScreen(onBack = {}, onPhotoAnalysis = { photo++ }) }
        compose.onNodeWithTag("sizeComparisonGuidance").assertIsDisplayed()
        compose.onNodeWithTag("sizeComparePhoto").performClick()
        compose.onNodeWithTag("manualBodyCard").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, photo) }
    }

    @Test fun avatarSetupOpensLatestMeasurementFlowAndKeepsPhotoEntryAvailable() {
        var photo = 0
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { photo++ }) }
        compose.onNodeWithTag("avatarManualExpand").performScrollTo().performClick()
        compose.onNodeWithText("3D 기본 모델").assertDoesNotExist()
        compose.onNodeWithTag("manualBodyAvatar").performScrollTo().performClick()
        compose.onNodeWithTag("manualAvatarBack").assertExists().performScrollTo().performClick()
        compose.onNodeWithText("사진으로 체형 분석하기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, photo) }
    }

    @Test fun avatarSetupGroupsAdjustmentsAndKeepsThreeSourceChoices() {
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = {}) }
        compose.onNodeWithTag("openCmAvatar").assertExists()
        compose.onNodeWithTag("avatarShow").assertDoesNotExist()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarShow").assertExists()
        compose.onNodeWithTag("avatarImportBmi").assertExists()
        compose.onNodeWithTag("avatarImportPhoto").assertIsNotEnabled()
        compose.onNodeWithTag("avatarImportManual").assertIsNotEnabled()
        compose.onNodeWithTag("avatarShoulder").assertDoesNotExist()
        compose.onNodeWithTag("avatarAdjustToggle").performScrollTo().performClick()
        listOf("avatarShoulder", "avatarChest", "avatarWaist", "avatarHip",
            "avatarThigh", "avatarLeg", "avatarTorso").forEach {
            compose.onNodeWithTag(it).assertExists()
        }
        compose.onNodeWithTag("avatarAdjustToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarShoulder").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h360dp")
    fun avatarActionsRemainReachableOnShortScreenWithLargeText() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = {})
            }
        }
        compose.onNodeWithTag("openCmAvatar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarShow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatarSave").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatarManualExpand").performScrollTo().assertIsDisplayed()
    }

    @Test fun avatarSetupAcceptsHeightWeightAndSavesVisualProfile() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = {}) }
        compose.onNodeWithTag("avatarHeight").performTextReplacement("175")
        compose.onNodeWithTag("avatarWeight").performTextReplacement("72")
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSave").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(175f, AvatarProfileStore.current.value?.heightCm)
            assertEquals(72f, AvatarProfileStore.current.value?.weightKg)
        }
        compose.onNodeWithTag("avatarBodyResult").assertIsDisplayed()
        compose.onNodeWithTag("avatarBodyEdit").performScrollTo().performClick()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarShow").performScrollTo().performClick()
        compose.onNodeWithText("3D 비율 아바타 · 참고 모델").assertIsDisplayed()
        AvatarProfileStore.clear(app)
    }

    @Test fun savedMeasuredAvatarRestoresAnalysisAndOpensFittingWithoutPhoto() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cm = checkNotNull(AvatarCentimeters.generate(170f, 65f, 1L))
        val references = mapOf(AvatarPart.SHOULDER to 41f, AvatarPart.CHEST to 96f, AvatarPart.WAIST to 68f, AvatarPart.HIP to 99f)
            .mapValues { (_, value) -> AvatarCm(value, AvatarValueOrigin.MANUAL) }
        org.junit.Assert.assertTrue(AvatarProfileStore.save(app, AvatarProfile(
            170f, 65f, centimeters = cm.copy(references = references))))
        try {
            var fitting = 0
            compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { error("No photo needed") },
                onFitting = { fitting++ }) }
            compose.onNodeWithText("3D 입력 기반 분석").assertIsDisplayed()
            compose.onNodeWithText("허리 차이가 뚜렷한 체형").assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyDetails").performScrollTo().performClick()
            compose.onNodeWithText("허리 둘레 68.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyFitting").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, fitting) }
            compose.onNodeWithTag("avatarBodyEdit").performScrollTo().performClick()
            compose.onNodeWithTag("openCmAvatar").performScrollTo().assertIsDisplayed()
        } finally {
            AvatarProfileStore.clear(app)
        }
    }

    @Test fun aPhotoFreeRulerSaveOpensAnalysisAndResavingUpdatesIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        try {
            compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { error("No photo needed") }) }
            compose.onNodeWithTag("avatarHeight").performScrollTo().performTextReplacement("170")
            compose.onNodeWithTag("avatarWeight").performScrollTo().performTextReplacement("65")
            compose.onNodeWithTag("openCmAvatar").performScrollTo().performClick()
            inputMeasured(AvatarPart.CHEST, "96")
            inputMeasured(AvatarPart.WAIST, "70")
            inputMeasured(AvatarPart.HIP, "99")
            compose.onNodeWithTag("avatarBodyResult").assertDoesNotExist()
            compose.onNodeWithTag("cmEditorSave").performClick()
            compose.onNodeWithTag("avatarApplyApprove").performClick()
            compose.onNodeWithText("허리 차이가 뚜렷한 체형").assertIsDisplayed()

            compose.onNodeWithTag("avatarBodyEdit").performScrollTo().performClick()
            compose.onNodeWithTag("openCmAvatar").performScrollTo().performClick()
            inputMeasured(AvatarPart.WAIST, "88")
            inputMeasured(AvatarPart.SHOULDER, "44")
            inputMeasured(AvatarPart.HIP, "110")
            compose.onNodeWithTag("cmEditorSave").performClick()
            compose.onNodeWithTag("avatarApplyApprove").performClick()
            compose.onNodeWithText("엉덩이 둘레가 더 큰 체형").assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyDetails").performScrollTo().performClick()
            compose.onNodeWithText("어깨 너비 44.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("허리 둘레 88.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("엉덩이 둘레 110.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
        } finally {
            AvatarProfileStore.clear(app)
        }
    }

    private fun inputMeasured(part: AvatarPart, value: String) {
        compose.onNodeWithTag("cmPart-${part.name}").performScrollTo().performClick()
        compose.onNodeWithTag("rulerDirectInput").performScrollTo().performClick()
        compose.onNodeWithTag("rulerInputField").performTextReplacement(value)
        compose.onNodeWithTag("rulerMeasured").performClick()
        compose.onNodeWithTag("rulerInputConfirm").performClick()
    }
}
