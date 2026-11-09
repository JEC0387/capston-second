package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.AvatarApplicationDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h480dp")
class P61CompactComparisonTest {
    @get:Rule val compose=createComposeRule()
    @Test fun compactComparisonHidesDetailsAndKeepsBothActionsVisibleAtLargeFont() {
        var saved=0; var cancelled=0
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.3f)) {
                MaterialTheme { AvatarApplicationDialog(null,AvatarProfile(174.125f,72f),{cancelled++},{saved++},true) }
            }
        }
        compose.onNodeWithText("키: 없음 → 174.1 cm").assertExists()
        compose.onNodeWithText("다리 표시 길이",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("avatarApplyApprove").assertIsDisplayed()
        compose.onNodeWithTag("avatarApplyCancel").assertIsDisplayed()
        compose.onNodeWithTag("avatarComparisonDetails").performScrollTo().performClick()
        compose.onNodeWithText("다리 표시 길이",substring=true).assertExists()
        compose.onNodeWithTag("avatarApplyApprove").assertIsDisplayed()
        compose.onNodeWithTag("avatarApplyCancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0,saved); assertEquals(1,cancelled) }
    }
    @Test fun centimetersUseCircumferenceAndLegacyWidthsAreNeverComparedAsCircumference() {
        val cm=AvatarCentimeters.fromLegacy(AvatarProfile(174f,72f),100L)!!
        compose.setContent { AvatarApplicationDialog(AvatarProfile(170f,65f),AvatarProfile(174f,72f,centimeters=cm),{}, {}) }
        compose.onNodeWithText("가슴 표시 너비",substring=true).assertExists()
        compose.onNodeWithText("가슴 둘레",substring=true).assertDoesNotExist()
    }
}
