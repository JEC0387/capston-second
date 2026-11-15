package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.BodyAvatarSetupScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w420dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P70BodyFlowUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() {
        AuthStore.continueAsGuest(app)
        AvatarProfileStore.clear(app)
        ManualBodyStore.clear(app)
    }
    @After fun cleanup() { AvatarProfileStore.clear(app); ManualBodyStore.clear(app) }
    private fun click(tag:String) {
        val node = compose.onNodeWithTag(tag)
        if (tag !in setOf("avatarSave", "avatarAnalyze")) node.performScrollTo()
        node.performClick()
    }
    private fun enter(tag:String,value:String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun createMeasuredBody() {
        enter("avatarHeight","160");enter("avatarWeight","55")
        compose.onNodeWithTag("avatarAnalysisPreview").assertIsDisplayed()
        click("avatarDirectTab")
        for((part,cm) in listOf("CHEST" to "88","WAIST" to "71","HIP" to "95","SHOULDER" to "39"))
            enter("avatarMeasure"+part,cm)
    }
    private fun rulerValue():Float = compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription].removeSuffix(" cm").toFloat()

    @Test fun rulerDragHalfStepsPartsAndBothTabsShareValuesWithoutChangingTapeReference() {
        compose.setContent { BodyAvatarSetupScreen({}, {}) }
        createMeasuredBody()
        click("avatarRulerTab")
        click("rulerPlus");assertEquals(71.5f,rulerValue(),0f)
        click("rulerMinus");assertEquals(71f,rulerValue(),0f)
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().performTouchInput {
            swipe(center,center-Offset(56f,0f),500)
        }
        val dragged=rulerValue()
        assertTrue(dragged>71f);assertEquals(0f,dragged*2%1,0f)
        click("analysisPart-CHEST");assertEquals(88f,rulerValue(),0f)
        click("analysisPart-WAIST");assertEquals(dragged,rulerValue(),0f)
        click("avatarDirectTab")
        compose.onNodeWithTag("avatarMeasureWAIST").performScrollTo()
            .assertTextContains(String.format(Locale.KOREA,"%.1f",dragged))
        click("avatarRulerTab");assertEquals(dragged,rulerValue(),0f)
        click("avatarSave")
        compose.onNodeWithTag("avatarAnalyze").assertExists()
        compose.runOnIdle {
            val cm=checkNotNull(AvatarProfileStore.current.value?.centimeters)
            assertEquals(dragged,cm[AvatarPart.WAIST],0f)
            assertEquals(AvatarValueOrigin.ADJUSTED,cm.values.getValue(AvatarPart.WAIST).origin)
            assertEquals(71f,cm.references.getValue(AvatarPart.WAIST).cm,0f)
        }
        click("rulerPlus")
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        click("analysisConfirmMeasured")
        val confirmed=rulerValue()
        click("avatarSave");click("avatarAnalyze")
        compose.onNodeWithTag("avatarBodyResult").assertExists()
        compose.onNodeWithTag("avatarBodyFitting").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(confirmed,AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value))[AvatarPart.WAIST])
        }
    }

    @Test fun savedBodyReloadsAndUnsavedChangesCannotAnalyzeAfterStateRestoration() {
        var screen by mutableIntStateOf(0)
        val restoration=StateRestorationTester(compose)
        restoration.setContent { key(screen) { BodyAvatarSetupScreen({}, {}) } }
        createMeasuredBody()
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        click("avatarSave")
        compose.onNodeWithTag("avatarSavedStatus").assertExists()
        compose.runOnIdle { AvatarProfileStore.load(app);screen++ }
        compose.onNodeWithTag("avatarAnalyze").assertIsEnabled()
        click("avatarDirectTab");enter("avatarMeasureWAIST","72.3")
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("avatarMeasureWAIST").performScrollTo().assertTextContains("72.3")
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        compose.runOnIdle { assertEquals(71f,AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value))[AvatarPart.WAIST]) }
        click("avatarSave");click("avatarAnalyze")
        compose.onNodeWithTag("avatarBodyResult").assertExists()
        click("avatarBodyEdit");click("avatarDirectTab")
        compose.onNodeWithTag("avatarMeasureWAIST").performScrollTo().assertTextContains("72.3")
        compose.runOnIdle {
            AvatarProfileStore.load(app)
            assertEquals(72.3f,AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value))[AvatarPart.WAIST])
        }
    }

    @Test @Config(qualifiers="w320dp-h568dp")
    fun wholePreviewStaysPinnedWhileRulerAndLargeTextControlsScroll() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                BodyAvatarSetupScreen({}, {})
            }
        }
        createMeasuredBody()
        click("avatarRulerTab")
        val preview = compose.onNodeWithTag("avatarAnalysisPreview")
        val before = preview.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("avatarCmRuler").performScrollTo().assertIsDisplayed()
        preview.assertIsDisplayed()
        val ruler = compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode().boundsInRoot
        assertTrue("model and ruler cannot overlap", before.bottom <= ruler.top)
        assertEquals(before, preview.fetchSemanticsNode().boundsInRoot)
        click("rulerPlus"); assertEquals(71.5f, rulerValue(), 0f)
        click("rulerMinus"); assertEquals(71f, rulerValue(), 0f)
        click("avatarOptionalMeasures")
        preview.assertIsDisplayed()
        assertEquals(before, preview.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("avatarSave").assertIsDisplayed()
        click("avatarSave")
        compose.onNodeWithTag("avatarAnalyze").assertIsDisplayed()
    }

    @Test fun guessedRulerValuesRequireExplicitMeasuredConfirmationBeforeAnalysis() {
        compose.setContent { BodyAvatarSetupScreen({}, {}) }
        enter("avatarHeight","160");enter("avatarWeight","55")
        click("rulerPlus");click("avatarSave")
        compose.onNodeWithTag("avatarAnalyze").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value)).isEmpty()) }
        for(part in listOf("CHEST","WAIST","HIP")) {
            click("analysisPart-"+part);click("analysisConfirmMeasured")
        }
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        click("avatarSave")
        compose.onNodeWithTag("avatarAnalyze").assertIsEnabled()
    }
}
