package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.AvatarSetupCard
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Isolated Robolectric storage only. Never run this fixture against a user's installed app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P16AvatarDraftTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before @After fun resetFixture() {
        app.getSharedPreferences("avatar_visual_profile_v1", 0).edit().clear().commit()
        AvatarProfileStore.load(app)
    }

    @Test fun laterSavedProfileEmissionDoesNotOverwriteTypedDraft() {
        assertTrue(AvatarProfileStore.save(app, AvatarProfile(170f, 65f)))
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { AvatarSetupCard() } }
        compose.onNodeWithTag("avatarHeight").performScrollTo().performTextReplacement("181")
        compose.onNodeWithTag("avatarWeight").performScrollTo().performTextReplacement("81")

        val external = AvatarProfile(175f, 72f)
        compose.runOnIdle { assertTrue(AvatarProfileStore.save(app, external)) }

        compose.onNodeWithTag("avatarHeight").assertTextContains("181")
        compose.onNodeWithTag("avatarWeight").assertTextContains("81")
        compose.runOnIdle { assertEquals(external, AvatarProfileStore.current.value) }
    }

    @Test fun newBodyDraftSurvivesStateRestoreWithoutReplacingSavedCmProfile() {
        val old = AvatarProfile(170f, 65f, centimeters = AvatarCentimeters.generate(170f, 65f, 1)!!)
        assertTrue(AvatarProfileStore.save(app, old))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { AvatarSetupCard() } }
        compose.onNodeWithTag("avatarStartNew").performScrollTo().performClick()
        compose.onNodeWithTag("avatarHeight").performScrollTo().performTextReplacement("181")
        compose.onNodeWithTag("avatarWeight").performScrollTo().performTextReplacement("81")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("avatarHeight").assertTextContains("181")
        compose.onNodeWithTag("avatarWeight").assertTextContains("81")
        compose.onNodeWithTag("avatarHeight").performScrollTo().performTextReplacement("182")
        compose.onNodeWithTag("avatarHeight").assertTextContains("182")
        compose.onNodeWithTag("openCmAvatar").performScrollTo().performClick()
        val generatedWaist = compose.onNodeWithTag("avatarCmRuler").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(AvatarCentimeters.generate(182f, 81f, 1)!![AvatarPart.WAIST], generatedWaist)
        compose.onNodeWithContentDescription("3D 조절 닫기").performClick()
        compose.runOnIdle { assertEquals(old, AvatarProfileStore.current.value) }
    }
}
