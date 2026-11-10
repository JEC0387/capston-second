package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.ui.screens.BodyAnalysisScreen
import com.jeiu.fashionproject.ui.viewmodel.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarProfileStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BodyAnalysisResultUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val photo = Uri.parse("content://test/body-result")
    private val measurements = BodyMeasurements(170f, 40f, 28f, 37f, 80f, 46f, .1f, 1700f, 1L)

    @Before @After fun isolateAvatar() {
        app.getSharedPreferences("avatar_visual_profile_v1",0).edit().clear().commit()
        AvatarProfileStore.load(app)
    }

    @Suppress("UNCHECKED_CAST")
    private fun showResult(measured: BodyMeasurements?) {
        val model = BodyAnalysisViewModel(app)
        val result = BodyAnalysisResult("균형형", .8f, .65f, .75f,
            .47f, listOf("어깨와 골반 비율이 비슷해요"), listOf("기본 핏 참고"),
            measurements = measured)
        val field = BodyAnalysisViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        (field.get(model) as MutableStateFlow<BodyAnalysisUiState>).value =
            BodyAnalysisUiState.Success(result, photo)
        compose.setContent { Box(Modifier.height(360.dp)) {
            BodyAnalysisScreen(onBack = {}, onStyleSuggestion = {}, onCameraClick = {},
                viewModel = model, onHistory = {})
        } }
    }

    @Test fun resultStartsWithPhotoTypeAndCmThenExpandsDetailsAndSize() {
        showResult(measurements)
        compose.onNodeWithContentDescription("분석한 사진").assertExists()
        compose.onNodeWithText("균형형").assertExists()
        compose.onNodeWithText("측정 결과 · 키 기준 cm 추정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("신체 비율 추정 (정면 너비, 둘레 아님)").assertDoesNotExist()
        compose.onNodeWithTag("bodyDetailsExpand").performScrollTo().performClick()
        compose.onNodeWithText("신체 비율 추정 (정면 너비, 둘레 아님)").assertExists()
        compose.onNodeWithTag("bodySizeExpand").performScrollTo().performClick()
        compose.onNodeWithText("체형 기반 사이즈 추천").assertExists()
        compose.onNodeWithTag("measuredSizeOpen").assertExists()
        compose.onNodeWithTag("bodyStyleExpand").performScrollTo().performClick()
        compose.onNodeWithText("기본 핏 참고").assertExists()
    }

    @Test fun photoMeasurementOpensApprovalBeforeChangingSavedAvatar() {
        val prior=AvatarProfile(170f,65f)
        assertTrue(AvatarProfileStore.save(app,prior))
        showResult(measurements)
        compose.onNodeWithTag("bodyAvatarFromPhoto").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("avatarApplyCancel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("avatarApplyCancel").performClick()
        compose.runOnIdle { assertEquals(prior,AvatarProfileStore.current.value) }
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarImportPhoto").assertExists()
    }

    @Test fun firstPhotoImportRequiresUserWeightBeforeApprovalAndDoesNotSaveAutomatically() {
        showResult(measurements)
        compose.onNodeWithTag("bodyAvatarFromPhoto").performScrollTo().performClick()
        compose.onNodeWithTag("avatarApplyCancel").assertDoesNotExist()
        compose.onNodeWithTag("avatarHeight").performScrollTo().performTextReplacement("170")
        compose.onNodeWithTag("avatarWeight").performScrollTo().performTextReplacement("65")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("avatarApplyCancel").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertNull(AvatarProfileStore.current.value) }
        compose.onNodeWithTag("avatarApplyCancel").performClick()
        compose.runOnIdle { assertNull(AvatarProfileStore.current.value) }
    }

    @Test fun absentMeasurementsNeverOfferCompletedAvatarImport() {
        showResult(null)
        compose.onNodeWithTag("bodyAvatarFromPhoto").assertDoesNotExist()
        compose.onNodeWithText("유효한 cm 측정값이 없어 이 사진으로 3D 모델을 만들 수 없어요.")
            .performScrollTo().assertIsDisplayed()
    }
}
