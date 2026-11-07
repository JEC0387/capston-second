package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.ProfileStore
import com.jeiu.fashionproject.ui.screens.OnboardingScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P23GuestEntryUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun localEntryDiscardsStyleDraftAndPreservesExistingRecordsAndQueue() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        ProfileStore.writeForRestore(app,listOf("미니멀"))
        val names=listOf("profile_store","wardrobe_store","db_sync","privacy_consent","user_prefs")
        names.drop(1).forEach {app.getSharedPreferences(it,0).edit().putString("p23-preserved","synthetic original").commit()}
        val before=names.associateWith {app.getSharedPreferences(it,0).all.toMap()}
        var entered=0
        compose.setContent {OnboardingScreen(onStart={entered++})}
        compose.onNodeWithText("미니멀").performScrollTo().performClick()
        compose.onNodeWithText("캐주얼").performScrollTo().performClick()
        compose.onNodeWithTag("continueLocalGuest").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1,entered)
            assertEquals(listOf("미니멀"),ProfileStore.preferredStyles(app))
            names.forEach {assertEquals(before[it],app.getSharedPreferences(it,0).all)}
        }
    }
}
