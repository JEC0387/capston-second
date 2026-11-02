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
import com.jeiu.fashionproject.ui.components.AvatarSetupCard
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P7AvatarUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before @After fun reset() {
        app.getSharedPreferences("avatar_visual_profile_v1",0).edit().clear().commit()
        AvatarProfileStore.load(app);BodyAnalysisSessionStore.clear()
    }
    private fun show() {compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) {
        AvatarSetupCard(BodyMeasurements(170f,40f,28f,37f,80f,46f,.1f,1700f,1L))
    } }}
    @Test fun cancellationPreservesPriorProfileAndApprovalIsNotAutomaticSave() {
        val prior=AvatarProfile(175f,72f);assertTrue(AvatarProfileStore.save(app,prior));show()
        compose.onNodeWithTag("avatarImportPhoto").performScrollTo().performClick()
        compose.onNodeWithTag("avatarApplyCancel").performClick()
        compose.runOnIdle {assertEquals(prior,AvatarProfileStore.current.value)}
        compose.onNodeWithTag("avatarImportPhoto").performScrollTo().performClick()
        compose.onNodeWithTag("avatarApplyApprove").performClick()
        compose.runOnIdle {assertEquals(prior,AvatarProfileStore.current.value)}
        compose.onNodeWithTag("avatarSave").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(AvatarSource.PHOTO,AvatarProfileStore.current.value!!.source)}
    }
    @Test fun photoResultCanOpenSourceComparisonDirectlyWithoutSaving() {
        val prior = AvatarProfile(175f, 72f)
        assertTrue(AvatarProfileStore.save(app, prior))
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) {
            AvatarSetupCard(BodyMeasurements(170f,40f,28f,37f,80f,46f,.1f,1700f,1L),
                autoImportPhoto = true)
        } }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("avatarApplyCancel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("avatarApplyCancel").performClick()
        compose.runOnIdle { assertEquals(prior, AvatarProfileStore.current.value) }
    }
}
