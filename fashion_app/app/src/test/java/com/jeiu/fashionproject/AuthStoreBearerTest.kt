package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AuthStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Google · Kakao on the Bearer model — app-side rules only (no network, no real credentials). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthStoreBearerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun onlyKakaoAndGoogleAreOffered() {
        assertEquals(listOf(AuthStore.Provider.KAKAO, AuthStore.Provider.GOOGLE), AuthStore.ENABLED_PROVIDERS)
    }

    @Test fun bearerSignedInStateSurvivesRestart() {
        app.getSharedPreferences("auth", 0).edit().putString("mode", "SIGNED_IN").putString("email", "a@example.com")
            .putString("name", "구글A").putString("provider", "google").putBoolean("bearer_v2", true).commit()
        AuthStore.load(app)
        assertEquals(AuthStore.Mode.SIGNED_IN, AuthStore.mode.value)
        assertEquals(AuthStore.Account("a@example.com", "구글A", AuthStore.Provider.GOOGLE), AuthStore.account.value)
        // Logout keeps every record and only returns to the chooser.
        runBlocking { assertTrue(AuthStore.signOut(app)) }
        assertEquals(AuthStore.Mode.NONE, AuthStore.mode.value)
        assertNull(AuthStore.account.value)
        assertFalse(app.getSharedPreferences("auth", 0).getBoolean("bearer_v2", false))
    }

    @Test fun facebookAppleAndForeignRedirectsNeverReachTheServer() = runBlocking {
        listOf(AuthStore.Provider.FACEBOOK, AuthStore.Provider.APPLE, AuthStore.Provider.GOOGLE).forEach {
            assertTrue(AuthStore.startBrowserLogin(app, it) is AuthStore.Result.Failed)
        }
        listOf("fashionai://login?code=abcdefghijklmnopqrstuvwxyz&provider=facebook",
            "fashionai://login?code=abcdefghijklmnopqrstuvwxyz&provider=google",
            "fashionai://login?code=short&provider=kakao").forEach {
            assertTrue(it, AuthStore.finishBrowserLogin(app, Uri.parse(it)) is AuthStore.Result.Failed)
        }
    }

    @Test fun knownServerCodesMapToFixedSentences() {
        fun detail(code: String) = """{"detail":{"code":"$code","message":"remote-text"}}"""
        assertTrue(AuthStore.messageFor(409, detail("provider_already_linked")).contains("이미 다른 계정"))
        assertTrue(AuthStore.messageFor(409, detail("social_account_requires_review")).contains("운영자"))
        assertTrue(AuthStore.messageFor(409, detail("legacy_decision_required")).contains("백업"))
        assertTrue(AuthStore.messageFor(401, detail("login_expired")).contains("시간이 지났"))
        listOf("provider_already_linked", "unknown_code").forEach {
            assertFalse(AuthStore.messageFor(409, detail(it)).contains("remote-text"))
        }
    }
}
