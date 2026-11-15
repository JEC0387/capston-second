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

    @Test fun estimatedBodyCanBeSavedButDoesNotUnlockMeasuredAnalysis() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        var photo = 0
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { photo++ }) }
        enter("avatarHeight", "170")
        enter("avatarWeight", "65")
        compose.onNodeWithTag("avatarAnalysisPreview").assertIsDisplayed()
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        compose.onNodeWithTag("avatarSave").performClick()
        compose.onNodeWithTag("avatarAnalyze").assertIsNotEnabled()
        compose.onNodeWithTag("avatarAnalysisMissing").assertExists()
        compose.onNodeWithTag("avatarBodyResult").assertDoesNotExist()
        compose.onNodeWithTag("avatarAdvancedToggle").assertDoesNotExist()
        compose.onNodeWithText("사진으로 체형 분석하기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, photo)
            org.junit.Assert.assertTrue(com.jeiu.fashionproject.data.AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value)).isEmpty())
        }
    }

    @Test @Config(qualifiers = "w320dp-h360dp")
    fun formControlsRemainReachableOnShortScreenWithLargeText() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AvatarProfileStore.clear(app)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = {})
            }
        }
        enter("avatarHeight", "170")
        enter("avatarWeight", "65")
        compose.onNodeWithTag("avatarAnalysisPreview").assertIsDisplayed()
        compose.onNodeWithTag("avatarDirectTab").performScrollTo().performClick()
        AvatarPart.entries.filter { it in listOf(AvatarPart.CHEST, AvatarPart.WAIST, AvatarPart.HIP, AvatarPart.SHOULDER) }
            .forEach { compose.onNodeWithTag("avatarMeasure${it.name}").performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("avatarOptionalMeasures").performScrollTo().performClick()
        compose.onNodeWithTag("avatarMeasureLEG").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatarSave").assertIsDisplayed()
    }

    @Test fun savedMeasuredAvatarReopensAnalysisWithoutFittingOrPhoto() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cm = checkNotNull(AvatarCentimeters.generate(170f, 65f, 1L))
        val references = mapOf(AvatarPart.SHOULDER to 41f, AvatarPart.CHEST to 96f, AvatarPart.WAIST to 68f, AvatarPart.HIP to 99f)
            .mapValues { (_, value) -> AvatarCm(value, AvatarValueOrigin.MANUAL) }
        org.junit.Assert.assertTrue(AvatarProfileStore.save(app, AvatarProfile(
            170f, 65f, centimeters = cm.copy(references = references))))
        try {
            compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { error("No photo needed") }) }
            compose.onNodeWithTag("avatarAnalyze").performClick()
            compose.onNodeWithText("3D 입력 기반 분석").assertIsDisplayed()
            compose.onNodeWithText("허리 차이가 뚜렷한 체형").assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyDetails").performScrollTo().performClick()
            compose.onNodeWithText("허리 둘레 68.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyFitting").assertDoesNotExist()
            compose.onNodeWithTag("avatarBodyEdit").performScrollTo().performClick()
            compose.onNodeWithTag("avatarDirectTab").performScrollTo().performClick()
            compose.onNodeWithTag("avatarMeasureWAIST").performScrollTo().assertIsDisplayed()
        } finally {
            AvatarProfileStore.clear(app)
        }
    }

    @Test fun saveThenAnalyzeKeepsExactMeasurementsAndEditsRequireResaving() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val wardrobe = com.jeiu.fashionproject.data.WardrobeStore.items.value
        val account = com.jeiu.fashionproject.data.AuthStore.mode.value
        AvatarProfileStore.clear(app)
        try {
            compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { error("No photo needed") }) }
            enter("avatarHeight", "170")
            enter("avatarWeight", "65")
            compose.onNodeWithTag("avatarAnalysisPreview").assertIsDisplayed()
            compose.onNodeWithTag("avatarDirectTab").performScrollTo().performClick()
            enter("avatarMeasureCHEST", "96")
            enter("avatarMeasureWAIST", "20")
            enter("avatarMeasureHIP", "99")
            compose.onNodeWithTag("avatarSave").performClick()
            compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
            compose.onNodeWithTag("avatarAnalysisInputError").assertExists()
            compose.onNodeWithTag("avatarBodyResult").assertDoesNotExist()
            enter("avatarMeasureWAIST", "70.2")
            enter("avatarMeasureSHOULDER", "41")
            compose.onNodeWithTag("avatarSave").performClick()
            compose.onNodeWithTag("avatarBodyResult").assertDoesNotExist()
            compose.onNodeWithTag("avatarAnalyze").performClick()
            compose.onNodeWithText("허리 차이가 뚜렷한 체형").assertIsDisplayed()
            compose.runOnIdle {
                val cm = checkNotNull(AvatarProfileStore.current.value?.centimeters)
                assertEquals(70.2f, cm.references[AvatarPart.WAIST]?.cm)
                assertEquals(70f, cm[AvatarPart.WAIST])
            }

            compose.onNodeWithTag("avatarBodyEdit").performScrollTo().performClick()
            compose.onNodeWithTag("avatarDirectTab").performScrollTo().performClick()
            enter("avatarMeasureWAIST", "88")
            compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
            enter("avatarMeasureSHOULDER", "44")
            enter("avatarMeasureHIP", "110")
            compose.onNodeWithTag("avatarSave").performClick()
            compose.onNodeWithTag("avatarAnalyze").performClick()
            compose.onNodeWithText("엉덩이 둘레가 더 큰 체형").assertIsDisplayed()
            compose.onNodeWithTag("avatarBodyDetails").performScrollTo().performClick()
            compose.onNodeWithText("어깨 너비 44.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("허리 둘레 88.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("엉덩이 둘레 110.0 cm · 직접 잰 값").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                AvatarProfileStore.load(app)
                val cm = checkNotNull(AvatarProfileStore.current.value?.centimeters)
                assertEquals(88f, cm.references[AvatarPart.WAIST]?.cm)
                assertEquals(wardrobe, com.jeiu.fashionproject.data.WardrobeStore.items.value)
                assertEquals(account, com.jeiu.fashionproject.data.AuthStore.mode.value)
            }
        } finally {
            AvatarProfileStore.clear(app)
        }
    }

    private fun enter(tag: String, number: String) =
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(number)
}
