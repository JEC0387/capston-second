package com.jeiu.fashionproject

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.data.DataAuth
import com.jeiu.fashionproject.data.OutfitServerFailure
import com.jeiu.fashionproject.data.ServerOutfitVtonRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only this opt-in package has a separate signer and disabled, unregistered OAuth clients. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InstallTestIsolationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun installVariantOnly() { assumeTrue(BuildConfig.INSTALL_TEST_BUILD) }

    @Test fun packageAndLauncherAreIndependentWhileNamespaceIsRetained() {
        assertEquals("com.jeiu.fashionproject.installtest", app.packageName)
        assertEquals("패션핏 설치검증", app.packageManager.getApplicationLabel(app.applicationInfo))
        assertFalse(BuildConfig.PREVIEW_BUILD)
        assertFalse(BuildConfig.DEVICE_QA_BUILD)
        assertTrue(BuildConfig.DEBUG)
        assertEquals("com.jeiu.fashionproject.MainActivity", MainActivity::class.java.name)
    }

    @Test fun productionCallbackCannotResolveToTheFourthApp() {
        fun handlers(scheme: String) = app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://login"))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName), 0)
        assertTrue(handlers("fashionai").isEmpty())
        assertEquals(1, handlers("fashionfit-installtest").size)
        assertEquals("fashionfit-installtest", AuthStore.REDIRECT_SCHEME)
    }

    @Test fun unregisteredSocialClientsFailBeforeBrowserOrAccountAccess() = runBlocking {
        assertFalse(AuthStore.isGoogleConfigured())
        assertTrue(AuthStore.serverProviders().values.none { it })
        assertTrue(AuthStore.signInWithGoogle(app) is AuthStore.Result.Failed)
        assertTrue(AuthStore.startBrowserLogin(app, AuthStore.Provider.KAKAO) is AuthStore.Result.Failed)
        val callback = Uri.parse("fashionfit-installtest://login?provider=kakao&code=synthetic_not_a_real_code")
        AuthStore.handleRedirect(callback)
        assertNull(AuthStore.consumeRedirect())
        assertTrue(AuthStore.finishBrowserLogin(app, callback) is AuthStore.Result.Failed)
    }

    @Test fun guestAndSignOutPreserveLocalRecords() = runBlocking {
        val records = app.getSharedPreferences("installtest_preservation_fixture", 0)
        assertTrue(records.edit().putString("record", "synthetic-record").commit())
        assertTrue(AuthStore.continueAsGuest(app))
        assertTrue(AuthStore.signOut(app))
        assertEquals("synthetic-record", records.getString("record", null))
    }

    @Test fun enabledServerPathStillRequiresAuthentication() {
        assertTrue(BuildConfig.SERVER_OUTFIT_APPROVED)
        assertFalse(DataAuth.canSync)
        val failure = assertThrows(OutfitServerFailure::class.java) { ServerOutfitVtonRepository.session() }
        assertEquals("authentication_required", failure.code)
    }
}
