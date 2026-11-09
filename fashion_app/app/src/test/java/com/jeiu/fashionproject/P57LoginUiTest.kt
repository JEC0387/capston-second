package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.ui.screens.LoginScreen
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.data.AuthStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.After
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
    private val server = MockWebServer()
    private lateinit var previousServer: String
    @Before fun isolateProviderLookup() {
        AuthStore.continueAsGuest(ApplicationProvider.getApplicationContext<Application>())
        previousServer = BackendApi.BASE_URL
        server.enqueue(MockResponse().setBody("""{"google":false,"kakao":false,"facebook":false,"apple":false}"""))
        server.start()
        BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
    }
    @After fun restoreProviderLookup() {
        BackendApi.BASE_URL = previousServer
        server.shutdown()
    }

    @Test fun providerChooserNeverPromisesRestoreAndGuestPreservesLocalRecords() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val names = listOf("wardrobe_store", "profile_store", "db_sync", "data_auth")
        names.forEach { app.getSharedPreferences(it, 0).edit().putString("p57-ui", "synthetic original").commit() }
        val before = names.associateWith { app.getSharedPreferences(it, 0).all.toMap() }
        var entered = 0
        compose.setContent { LoginScreen(onDone = { entered++ }) }
        // A local fixture reports unconfigured providers; never contact Railway.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("loginUnconfigured").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("loginUnconfigured").assertTextContains(
            "Google·카카오 로그인을 아직 사용할 수 없어요", substring = true)
        compose.onNodeWithTag("loginConfigurationReason").assertDoesNotExist()
        compose.onNodeWithTag("loginDetails").performScrollTo().performClick()
        compose.onNodeWithTag("loginConfigurationReason").performScrollTo().assertTextEquals(AuthStore.UNAVAILABLE)
        listOf("google", "kakao").forEach {
            compose.onNodeWithTag("login_$it").performScrollTo().assertIsNotEnabled()
        }
        // Facebook · Apple are not offered at all.
        listOf("facebook", "apple").forEach { compose.onNodeWithTag("login_$it").assertDoesNotExist() }
        compose.onNodeWithTag("guestLogin").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, entered)
            names.forEach { assertEquals(before[it], app.getSharedPreferences(it, 0).all) }
        }
    }
}
