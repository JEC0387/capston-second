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

    @Test fun avatarSetupKeepsTheExistingModelAndPhotoEntryAvailable() {
        var photo = 0
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = { photo++ }) }
        compose.onNodeWithTag("avatarManualExpand").performScrollTo().performClick()
        compose.onNodeWithText("3D 기본 모델").performScrollTo().performClick()
        compose.onNodeWithText("3D 비율 아바타 · 참고 모델").assertIsDisplayed()
        compose.onNodeWithText("3D 닫기").performClick()
        compose.onNodeWithText("사진으로 체형 분석하기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, photo) }
    }

    @Test fun avatarSetupGroupsAdjustmentsAndKeepsThreeSourceChoices() {
        compose.setContent { BodyAvatarSetupScreen(onBack = {}, onPhotoAnalysis = {}) }
        compose.onNodeWithTag("avatarShow").assertExists()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
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
        compose.onNodeWithTag("avatarShow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
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
        compose.onNodeWithTag("avatarShow").performScrollTo().performClick()
        compose.onNodeWithText("3D 비율 아바타 · 참고 모델").assertIsDisplayed()
        AvatarProfileStore.clear(app)
    }
}
