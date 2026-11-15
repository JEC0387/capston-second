package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P61BodyConnectionTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val measured = ManualBodyMeasurements(174f, 43f, 96f, 82f, 100f, 80f, 46f, 10L)
    @Before @After fun clearFixtures() { ManualBodyStore.clear(app); AvatarProfileStore.clear(app) }

    @Test fun emptyEntryUsesLatestEditorWithoutInventedHeightAndWeight() {
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { ManualBodyEntryCard(null) {} } }
        compose.onNodeWithText("3D 기본 모델").assertDoesNotExist()
        compose.onNodeWithTag("manualBodyAvatar").performScrollTo().performClick()
        compose.onNodeWithTag("avatarHeight").assertTextContains("")
        compose.onAllNodesWithText("170").assertCountEquals(0)
        compose.onAllNodesWithText("65").assertCountEquals(0)
        compose.onNodeWithTag("openCmAvatar").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("manualAvatarBack").performScrollTo().performClick()
        compose.runOnIdle { assertNull(AvatarProfileStore.current.value); assertNull(ManualBodyStore.current.value) }
    }

    @Test fun manualImportComparisonAndEditorCancellationPreserveBothOriginalRecords() {
        val prior = AvatarProfile(170f, 70f)
        assertTrue(ManualBodyStore.update(app, measured, true)); assertTrue(AvatarProfileStore.save(app, prior))
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { AvatarSetupCard(autoImportManual = true) } }
        compose.onNodeWithTag("avatarHeight").assertTextContains("174.0")
        compose.onNodeWithTag("manualAvatarImport").assertDoesNotExist()
        compose.onNodeWithTag("openCmAvatar").performScrollTo().performClick()
        compose.onNodeWithTag("premiumAvatarEditor").assertExists()
        compose.onNodeWithContentDescription("3D 조절 닫기").performClick()
        compose.runOnIdle { assertEquals(prior, AvatarProfileStore.current.value); assertEquals(measured, ManualBodyStore.current.value) }
    }

    @Test fun manualValuesReachCentimeterRenderingWithOriginalProvenance() {
        val profile = AvatarMeasurementImport.manual(measured, AvatarProfile(174f, 72f))!!
        val cm = AvatarCentimeters.fromLegacy(profile, 100L)!!
        assertEquals(174f, cm.heightCm)
        assertEquals(43f, cm[AvatarPart.SHOULDER])
        assertEquals(96f, cm[AvatarPart.CHEST])
        assertEquals(82f, cm[AvatarPart.WAIST])
        assertEquals(100f, cm[AvatarPart.HIP])
        assertEquals(AvatarValueOrigin.MANUAL, cm.references.getValue(AvatarPart.WAIST).origin)
        assertTrue(cm.photoFrontWidths.isEmpty())
    }

    @Test fun explicitWritebackOnlyChangesOneMeasuredFieldAndRejectsOtherBodyOrInvalidValues() {
        val next = ManualAvatarMeasurements.proposed(measured, 174f, AvatarPart.WAIST, 84f, 20L)!!
        assertEquals(measured.copy(waistCircumferenceCm = 84f, enteredAt = 20L), next)
        assertNull(ManualAvatarMeasurements.proposed(measured, 170f, AvatarPart.WAIST, 84f, 20L))
        assertNull(ManualAvatarMeasurements.proposed(measured, 174f, AvatarPart.WAIST, Float.NaN, 20L))
        assertNull(ManualAvatarMeasurements.proposed(measured, 174f, AvatarPart.LEG, 200f, 20L))
        assertNull(ManualAvatarMeasurements.proposed(measured, 174f, AvatarPart.THIGH, 50f, 20L))
        assertEquals(82f, measured.waistCircumferenceCm)
    }

    @Test fun analysisSummaryShowsStoredCircumferencesSeparatelyFromPhoto() {
        compose.setContent { ManualBodySummary(measured, true) }
        compose.onNodeWithTag("analysisManualSummary").assertExists()
        compose.onNodeWithText("허리 둘레: 82.0 cm").assertExists()
        compose.onNodeWithText("사이즈 비교와 3D 초안에 사용해요. 사진의 정면 너비·체형 판정은 별도로 유지해요.").assertExists()
    }

    @Test fun visualEditsAndCancelledMeasuredWritebackNeverOverwriteOriginalMeasurements() {
        assertTrue(ManualBodyStore.update(app, measured, true))
        val profile = AvatarMeasurementImport.manual(measured, AvatarProfile(174f, 72f))!!
        val initial = profile.copy(centimeters = AvatarCentimeters.fromLegacy(profile, 100L))
        compose.setContent { PremiumAvatarEditor(initial, null, {}, onSave = { true }) }
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(measured, ManualBodyStore.current.value) }
        compose.onNodeWithTag("rulerDirectInput").performScrollTo().performClick()
        compose.onNodeWithTag("rulerInputField").performTextReplacement("84")
        compose.onNodeWithTag("rulerMeasured").performClick()
        compose.onNodeWithTag("rulerInputConfirm").performClick()
        compose.onNodeWithTag("cancelMeasuredWriteback").performClick()
        compose.runOnIdle { assertEquals(measured, ManualBodyStore.current.value) }
        compose.onNodeWithTag("rulerDirectInput").performScrollTo().performClick()
        compose.onNodeWithTag("rulerInputField").performTextReplacement("84")
        compose.onNodeWithTag("rulerMeasured").performClick()
        compose.onNodeWithTag("rulerInputConfirm").performClick()
        compose.onNodeWithTag("confirmMeasuredWriteback").performClick()
        compose.runOnIdle {
            ManualBodyStore.load(app)
            val saved = ManualBodyStore.current.value!!
            assertEquals(84f, saved.waistCircumferenceCm)
            assertEquals(measured.copy(waistCircumferenceCm = 84f, enteredAt = saved.enteredAt), saved)
            assertNull(AvatarProfileStore.current.value)
        }
    }
}
