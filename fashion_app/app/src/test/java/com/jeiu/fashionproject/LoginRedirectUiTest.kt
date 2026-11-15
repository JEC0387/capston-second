package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.navigation.LoginRedirectNavigation
import com.jeiu.fashionproject.navigation.Screen
import com.jeiu.fashionproject.ui.screens.LoginRedirectEffect
import com.jeiu.fashionproject.ui.screens.acceptLoginRedirect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose lifecycle; the suspended exchange is synthetic, no OAuth/network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginRedirectUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun clearCallback() { AuthStore.consumeRedirect() }

    @Test fun pendingBrowserIntentRoutesFromColdStartAndIsConsumedOnce() {
        val callback = Uri.parse("fashionai://login?provider=kakao&code=abcdefghijklmnopqrstuvwxyz")
        val intent = Intent(Intent.ACTION_VIEW, callback)
        acceptLoginRedirect(intent)
        assertNull("Accepted callback must not remain on the Activity intent", intent.data)
        assertEquals(callback, AuthStore.redirect.value)
        // Foreign links must not overwrite a valid pending callback.
        val foreign = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/login"))
        acceptLoginRedirect(foreign)
        assertNotNull(foreign.data)
        assertEquals(callback, AuthStore.redirect.value)
        var exchanges = 0
        compose.setContent {
            val nav = rememberNavController()
            LoginRedirectNavigation(nav)
            NavHost(nav, startDestination = "synthetic-start") {
                composable("synthetic-start") { Text("Start") }
                composable(Screen.Login.route) {
                    Text("Login callback")
                    LoginRedirectEffect { uri -> assertEquals(callback, uri); exchanges++ }
                }
            }
        }
        compose.waitUntil(5_000) { exchanges == 1 }
        compose.onNodeWithText("Login callback").assertIsDisplayed()
        compose.runOnIdle {
            acceptLoginRedirect(intent)
            assertNull(AuthStore.redirect.value)
            assertEquals(1, exchanges)
        }
    }

    @Test fun consumingCallbackDoesNotCancelItsOwnSuspendedExchange() {
        val response = CompletableDeferred<Unit>()
        var started = 0
        var completed = 0
        var cancelled = false
        AuthStore.handleRedirect(Uri.parse("fashionai://login?provider=kakao&code=abcdefghijklmnopqrstuvwxyz"))
        compose.setContent {
            LoginRedirectEffect {
                started++
                try { response.await(); completed++ }
                catch (failure: CancellationException) { cancelled = true; throw failure }
            }
        }
        try {
            compose.waitUntil(5_000) { started == 1 }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull(AuthStore.redirect.value)
                assertFalse("Clearing the consumed callback must not cancel its exchange", cancelled)
                response.complete(Unit)
            }
            compose.waitUntil(5_000) { completed == 1 }
            compose.runOnIdle { assertEquals(1, started) }
        } finally { response.complete(Unit) }
    }
}
