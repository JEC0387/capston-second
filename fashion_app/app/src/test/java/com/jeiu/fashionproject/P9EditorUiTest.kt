package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.PremiumAvatarEditor
import com.jeiu.fashionproject.ui.screens.BodyAvatarSetupScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P9EditorUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val body=AvatarCentimeters.generate(170f,65f,1)!!
    private val initial=AvatarProfile(170f,65f,centimeters=body)
    @Before @After fun clear() {app.getSharedPreferences("avatar_visual_profile_v1",0).edit().clear().commit();AvatarProfileStore.load(app)}
    private fun value()=compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    @Test fun plusMinusDirectInputAndDragShareTheValueAndSaveRequiresApproval() {
        val previous=AvatarProfile(170f,65f);AvatarProfileStore.save(app,previous)
        var writes=0;var captured:AvatarProfile?=null
        compose.setContent {PremiumAvatarEditor(initial,previous,{},onSave={writes++;captured=it;AvatarProfileStore.save(app,it)})}
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        assertEquals(body[AvatarPart.WAIST]+.5f,value())
        compose.onNodeWithTag("rulerMinus").performScrollTo().performClick()
        assertEquals(body[AvatarPart.WAIST],value())
        compose.onNodeWithTag("rulerDirectInput").performScrollTo().performClick()
        compose.onNodeWithTag("rulerInputField").performTextReplacement("77.5")
        compose.onNodeWithTag("rulerInputConfirm").performClick()
        assertEquals(77.5f,value())
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().performTouchInput {swipeLeft()}
        val final=value();assertTrue(final>77.5f)
        compose.onNodeWithTag("rulerCurrentValue").assertTextContains(String.format(Locale.KOREA,"%.1f cm",final))
        compose.onNodeWithTag("cmEditorSave").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(0,writes);assertEquals(previous,AvatarProfileStore.current.value)}
        compose.onNodeWithTag("avatarApplyCancel").performClick()
        compose.onNodeWithTag("cmEditorSave").assertIsDisplayed().performClick()
        compose.onNodeWithTag("avatarApplyApprove").performClick()
        compose.runOnIdle {assertEquals(1,writes);assertEquals(final,captured!!.centimeters!![AvatarPart.WAIST])}
    }
    @Test fun switchingBodyPartsPreservesEveryUnsavedMeasurementUntilExplicitSave() {
        var captured:AvatarProfile?=null
        compose.setContent { PremiumAvatarEditor(initial,null,{},onSave={captured=it;AvatarProfileStore.save(app,it)}) }
        val waist=body[AvatarPart.WAIST]+.5f
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        assertEquals(waist,value())
        compose.onNodeWithTag("cmPart-SHOULDER").performScrollTo().performClick()
        val shoulder=body[AvatarPart.SHOULDER]+.5f
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        assertEquals(shoulder,value())
        compose.onNodeWithTag("cmPart-CHEST").performScrollTo().performClick()
        val chest=body[AvatarPart.CHEST]+.5f
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        assertEquals(chest,value())
        compose.onNodeWithTag("cmPart-WAIST").performScrollTo().performClick()
        assertEquals(waist,value())
        compose.onNodeWithTag("cmPart-SHOULDER").performScrollTo().performClick()
        assertEquals(shoulder,value())
        compose.onNodeWithTag("cmPart-CHEST").performScrollTo().performClick()
        assertEquals(chest,value())
        compose.onNodeWithTag("cmEditorSave").assertIsDisplayed().performClick()
        compose.onNodeWithTag("avatarApplyApprove").performClick()
        compose.runOnIdle {
            val saved=checkNotNull(captured?.centimeters)
            assertEquals(waist,saved[AvatarPart.WAIST])
            assertEquals(shoulder,saved[AvatarPart.SHOULDER])
            assertEquals(chest,saved[AvatarPart.CHEST])
            AvatarProfileStore.load(app)
            assertEquals(saved,AvatarProfileStore.current.value!!.centimeters)
        }
    }

    @Test fun rulerCanStillDragAfterMultipleBodyPartChanges() {
        compose.setContent {PremiumAvatarEditor(initial,null,{},onSave={true})}
        compose.onNodeWithTag("cmPart-HIP").performScrollTo().performClick()
        compose.onNodeWithTag("cmPart-HIP").assertIsSelected()
        val hipBefore=value()
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().performTouchInput { swipeLeft() }
        val hipAfter=value()
        assertTrue("Dragging HIP ruler should update cm: $hipBefore -> $hipAfter",hipAfter>hipBefore)
        compose.onNodeWithTag("cmPart-WAIST").performScrollTo().performClick()
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().performTouchInput { swipeLeft() }
        val waistAfter=value()
        assertTrue("Dragging WAIST ruler should update cm",waistAfter>body[AvatarPart.WAIST])
        compose.onNodeWithTag("cmPart-HIP").performScrollTo().performClick()
        assertEquals(hipAfter,value())
    }

    @Test fun invalidKeyboardValueCannotChangeTheRuler() {
        compose.setContent {PremiumAvatarEditor(initial,null,{},onSave={false})}
        compose.onNodeWithTag("rulerDirectInput").performScrollTo().performClick()
        compose.onNodeWithTag("rulerInputField").performTextReplacement("77.3")
        compose.onNodeWithTag("rulerInputConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("rulerInputCancel").performClick()
        assertEquals(body[AvatarPart.WAIST],value())
    }
    @Test fun savedInstanceStateRestoresDraftAndSelectedPartWithoutWriting() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {PremiumAvatarEditor(initial,null,{},onSave={error("No automatic save")})}
        compose.onNodeWithTag("cmPart-THIGH").performScrollTo().performClick()
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        val changed=value();restoration.emulateSavedInstanceStateRestore()
        assertEquals(changed,value())
        compose.onNodeWithTag("cmPartLabel").assertTextContains("허벅지 둘레")
        assertNull(AvatarProfileStore.current.value)
    }
    @Test @Config(qualifiers="w320dp-h360dp") fun shortScreenAndLargeTextKeepModelAndActionsReachable() {
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
            PremiumAvatarEditor(initial,null,{},onSave={false},onApplyToFitting={})
        }}
        compose.onNodeWithTag("cmAvatarViewport").assertIsDisplayed()
        compose.onNodeWithTag("cmEditorCancel").assertIsDisplayed()
        compose.onNodeWithTag("cmPart-TORSO").performScrollTo().performClick()
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cmEditorSave").assertIsDisplayed().assertIsDisplayed()
        compose.onNodeWithTag("cmApplyFitting").assertDoesNotExist()
        compose.onNodeWithText("초기 추정값 복원").assertDoesNotExist()
        compose.onNodeWithTag("cmEditorAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("cmApplyFitting").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("초기 추정값 복원").performScrollTo().assertIsDisplayed()
    }
    @Test fun exitingUnsavedDraftKeepsOriginalProfile() {
        AvatarProfileStore.save(app,AvatarProfile(170f,65f));val previous=AvatarProfileStore.current.value;var closed=false
        compose.setContent {PremiumAvatarEditor(initial,previous,{closed=true},onSave={false})}
        compose.onNodeWithTag("rulerPlus").performScrollTo().performClick()
        compose.onNodeWithContentDescription("3D 조절 닫기").performClick()
        compose.onNodeWithTag("cmDiscardDraft").performClick()
        compose.runOnIdle {assertTrue(closed);assertEquals(previous,AvatarProfileStore.current.value)}
    }
    @Test fun setupAndLegacySaveDoNotDropACmProfile() {
        AvatarProfileStore.save(app,initial)
        compose.setContent {BodyAvatarSetupScreen({}, {})}
        compose.onNodeWithTag("openCmAvatar").performScrollTo().performClick()
        compose.onNodeWithTag("premiumAvatarEditor").assertIsDisplayed()
        compose.onNodeWithContentDescription("3D 조절 닫기").performClick()
        compose.onNodeWithTag("avatarAdvancedToggle").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSave").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(body,AvatarProfileStore.current.value!!.centimeters)}
    }
}
