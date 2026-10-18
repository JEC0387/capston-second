package com.jeiu.fashionproject

import android.app.Application
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.PrivacyConsent
import com.jeiu.fashionproject.ui.screens.BodyAnalysisScreen
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisViewModel
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.runner.RunWith

/** Requires a connected device/emulator. No real photo or network request is made. */
@RunWith(AndroidJUnit4::class)
class BodyGalleryConsentTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var model:BodyAnalysisViewModel
    private var cameraLaunches = 0
    private var galleryLaunches = 0
    private var pickerRequestCode = 0
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            galleryLaunches++
            pickerRequestCode = requestCode
        }
    }
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        PrivacyConsent.withdraw(app)
        model=BodyAnalysisViewModel(app)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                BodyAnalysisScreen(onBack={},onStyleSuggestion={},onCameraClick={cameraLaunches++},viewModel=model)
            }
        }
        compose.onNodeWithText("키 (cm)").performTextInput("170")
    }
    @After fun cleanup() { compose.runOnIdle { model.reset();model.viewModelScope.cancel() } }
    @Test fun largeGallerySurfaceRequiresConsent() {
        compose.onNodeWithTag("bodyGalleryPicker").performClick()
        compose.onNodeWithText("체형 사진 이용 안내").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).filter(isOff()).assertCountEquals(1)
    }
    @Test fun galleryButtonRequiresSameConsent() {
        compose.onNodeWithTag("bodyGalleryButton").performScrollTo().performClick()
        compose.onNodeWithText("체형 사진 이용 안내").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).filter(isOff()).assertCountEquals(1)
    }
    @Test fun cameraWithoutHeightIsEnabledAndResumesAfterHeightAndConsent() {
        compose.runOnIdle { model.updateHeightInput("") }
        compose.onNodeWithTag("bodyCameraButton").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("분석에 사용할 키를 입력해주세요").assertIsDisplayed()
        compose.onNodeWithTag("bodyHeightContinue").assertIsNotEnabled()
        compose.onNodeWithTag("bodyHeightDialogInput").performTextInput("170.5")
        compose.onNodeWithTag("bodyHeightContinue").performClick()
        compose.onNodeWithText("체형 사진 이용 안내").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).filter(isOff()).assertCountEquals(1)
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.runOnIdle { Assert.assertEquals(1, cameraLaunches); Assert.assertEquals(0, galleryLaunches); Assert.assertFalse(PrivacyConsent.canSaveRecords(ApplicationProvider.getApplicationContext())) }
    }
    @Test fun galleryConsentResumesSystemPickerExactlyOnce() {
        compose.onNodeWithTag("bodyGalleryButton").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.runOnIdle { Assert.assertEquals(1, galleryLaunches); Assert.assertEquals(0, cameraLaunches) }
    }
    @Test fun decliningConsentDoesNotLaunchAnything() {
        compose.onNodeWithTag("bodyCameraButton").performScrollTo().performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { Assert.assertEquals(0, cameraLaunches); Assert.assertEquals(0, galleryLaunches) }
    }

    @Test fun selectedUnreadablePhotoReachesAnalysisAndShowsFailureRatherThanSuccess() {
        compose.onNodeWithTag("bodyGalleryButton").performScrollTo().performClick()
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.runOnIdle { registry.dispatchResult(pickerRequestCode, android.net.Uri.parse("content://invalid-test-provider/no-photo")) }
        compose.waitUntil(5000) { model.state.value is com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisUiState.Error }
        compose.onNodeWithText("처음으로 돌아가기").performScrollTo().assertIsDisplayed()
    }
    @Test fun cancellingPickerKeepsIdleAndAllowsAnotherSelection() {
        compose.onNodeWithTag("bodyGalleryButton").performScrollTo().performClick()
        compose.onNodeWithText("동의하고 시작").performClick()
        compose.runOnIdle { registry.dispatchResult(pickerRequestCode, null as android.net.Uri?) }
        compose.onNodeWithTag("bodyGalleryButton").performScrollTo().performClick()
        compose.runOnIdle { Assert.assertEquals(2, galleryLaunches) }
    }

}
