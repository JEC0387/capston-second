package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jeiu.fashionproject.ui.screens.BodyMethodSelectionScreen
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
}
