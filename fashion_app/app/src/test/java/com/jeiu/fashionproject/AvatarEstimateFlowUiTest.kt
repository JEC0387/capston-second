package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.navigation.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.screens.BodyAvatarSetupScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarEstimateFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() { AuthStore.continueAsGuest(app); AvatarProfileStore.clear(app); ManualBodyStore.clear(app) }
    @After fun cleanup() { AvatarProfileStore.clear(app); ManualBodyStore.clear(app) }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag !in setOf("avatarSave", "avatarAnalyze", "openEditor")) node.performScrollTo()
        node.performClick()
    }
    private fun enter(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun body() { enter("avatarHeight", "160"); enter("avatarWeight", "55") }
    private fun ruler() = compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription].removeSuffix(" cm").toFloat()

    @Test fun modelOnlySaveAnalyzeEditReanalyzeDoesNotCreateMeasuredData() {
        compose.setContent { BodyAvatarSetupScreen({}, {}) }
        click("avatarSave")
        compose.onNodeWithTag("avatarAnalysisInputError").assertTextEquals("키와 몸무게를 확인해 주세요.")
        compose.onNodeWithTag("avatarAnalyze").assertDoesNotExist()
        body()
        click("avatarSave")
        compose.onNodeWithTag("avatarAnalyze").assertIsEnabled().performClick()
        compose.onNodeWithTag("avatarModelSource").assertTextEquals("3D 모델 기반 추정")
        val before = compose.onNodeWithTag("avatarModelWaistEvidence").fetchSemanticsNode().config[SemanticsProperties.Text].toString()
        compose.onNodeWithTag("avatarBodyDetails").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로").performClick()
        click("rulerPlus"); click("avatarSave"); click("avatarAnalyze")
        val after = compose.onNodeWithTag("avatarModelWaistEvidence").fetchSemanticsNode().config[SemanticsProperties.Text].toString()
        assertNotEquals(before, after)
        compose.runOnIdle {
            assertTrue(AvatarAnalysisInput.recorded(checkNotNull(AvatarProfileStore.current.value)).isEmpty())
            assertNull(ManualBodyStore.current.value)
        }
    }

    @Test fun everyPartAndTabKeepsItsDraftThroughSaveResultAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BodyAvatarSetupScreen({}, {}) }
        body(); click("avatarRulerTab")
        val values = linkedMapOf<AvatarPart,Float>()
        for (part in listOf(AvatarPart.WAIST, AvatarPart.CHEST, AvatarPart.SHOULDER, AvatarPart.HIP)) {
            click("analysisPart-${part.name}")
            val before = ruler(); click("rulerPlus"); values[part] = before + .5f
            assertEquals(before + .5f, ruler(), 0f)
        }
        click("analysisPart-WAIST"); assertEquals(values[AvatarPart.WAIST]!!, ruler(), 0f)
        click("avatarDirectTab")
        values.forEach { (part, value) -> compose.onNodeWithTag("avatarMeasure${part.name}")
            .performScrollTo().assertTextContains(String.format(java.util.Locale.KOREA, "%.1f", value)) }
        restoration.emulateSavedInstanceStateRestore()
        click("avatarRulerTab"); assertEquals(values[AvatarPart.WAIST]!!, ruler(), 0f)
        click("avatarSave"); click("avatarAnalyze")
        compose.onNodeWithTag("avatarModelSource").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("avatarModelSource").assertExists()
        click("avatarBodyEdit")
        compose.runOnIdle { AvatarProfileStore.load(app) }
        for ((part, value) in values) {
            click("analysisPart-${part.name}"); assertEquals(value, ruler(), 0f)
        }
    }

    @Test fun directMeasuredInputsStillReachOnlyMeasuredResults() {
        compose.setContent { BodyAvatarSetupScreen({}, {}) }
        body(); click("avatarDirectTab")
        for ((part, value) in listOf("CHEST" to "88", "WAIST" to "71", "HIP" to "95")) enter("avatarMeasure$part", value)
        click("avatarSave"); click("avatarAnalyze")
        compose.onNodeWithText("3D 입력 기반 분석").assertExists()
        compose.onNodeWithTag("avatarModelSource").assertDoesNotExist()
        compose.runOnIdle { assertEquals(AvatarAnalysisPath.MEASURED, AvatarModelAnalysis.path(AvatarProfileStore.current.value)) }
    }

    @Test fun leavingActualNavigationAndReenteringRestoresUnsavedDraftWithoutSavingOverData() {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "entry") {
                composable("entry") { Button({ nav.openBodyAvatar() }, Modifier.testTag("openEditor")) { Text("열기") } }
                composable(Screen.BodyAnalysisAvatar.route) {
                    BodyAvatarSetupScreen({ nav.closeBodyAvatar() }, {})
                }
            }
        }
        click("openEditor"); body(); click("avatarRulerTab"); click("rulerPlus")
        val changed = ruler()
        compose.onNodeWithContentDescription("뒤로").performClick()
        click("openEditor")
        compose.onNodeWithTag("avatarAnalysisPreview").assertExists()
        assertEquals(changed, ruler(), 0f)
        compose.runOnIdle { assertNull(AvatarProfileStore.current.value) }
        click("avatarSave");click("avatarAnalyze")
        compose.onNodeWithTag("avatarModelSource").assertExists()
    }
}
