package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.ui.screens.LoginScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w600dp-h1000dp")
class P57LoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun providerChooserNeverPromisesRestoreAndGuestPreservesLocalRecords() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val names = listOf("wardrobe_store", "profile_store", "db_sync", "data_auth")
        names.forEach { app.getSharedPreferences(it, 0).edit().putString("p57-ui", "synthetic original").commit() }
        val before = names.associateWith { app.getSharedPreferences(it, 0).all.toMap() }
        var entered = 0
        compose.setContent { LoginScreen(onDone = { entered++ }) }
        compose.onNodeWithTag("loginUnconfigured").assertTextContains("아직 설정되지", substring = true)
        listOf("google", "kakao", "facebook", "apple").forEach {
            compose.onNodeWithTag("login_$it").performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithTag("guestLogin").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, entered)
            names.forEach { assertEquals(before[it], app.getSharedPreferences(it, 0).all) }
        }
    }
}
