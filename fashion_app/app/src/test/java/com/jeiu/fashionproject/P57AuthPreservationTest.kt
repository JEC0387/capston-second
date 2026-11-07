package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AuthStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic records only. No OAuth, network, real credentials or device data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P57AuthPreservationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val records = listOf("wardrobe_store", "profile_store", "avatar_visual_profile_v1",
        "product_interests", "privacy_consent", "user_prefs", "db_sync", "data_auth")
    private lateinit var before: Map<String, Map<String, *>>
    private lateinit var files: List<File>

    @Before fun seedIsolatedRecords() {
        records.forEach { name ->
            app.getSharedPreferences(name, 0).edit().putString("p57_synthetic", "existing-$name").commit()
        }
        app.getSharedPreferences("db_sync", 0).edit()
            .putString("device_id", "synthetic-original-owner")
            .putString("pending", "[{\"operation\":\"wardrobe\",\"id\":\"synthetic\"}]").commit()
        files = listOf(File(app.filesDir, "wardrobe/p57-original.bin"),
            File(app.cacheDir, "tryon/p57-result.bin"), File(app.noBackupFilesDir, "p57-restore-journal.bin"))
        files.forEach { it.parentFile!!.mkdirs(); it.writeBytes(byteArrayOf(1, 3, 7, 9)) }
        before = records.associateWith { app.getSharedPreferences(it, 0).all.toMap() }
        AuthStore.consumeRedirect()
    }

    private fun assertRecordsPreserved() {
        records.forEach { assertEquals(it, before[it], app.getSharedPreferences(it, 0).all) }
        files.forEach { assertTrue(it.name, it.isFile); assertArrayEquals(byteArrayOf(1, 3, 7, 9), it.readBytes()) }
    }

    @Test fun localGuestAndSignOutKeepOwnerPendingWorkPhotosAndRecovery() = runBlocking {
        assertTrue(AuthStore.continueAsGuest(app))
        assertEquals(AuthStore.Mode.GUEST, AuthStore.mode.value)
        assertRecordsPreserved()
        // No dependency on a successful flush or a reachable server; no local wipe on timeout.
        assertTrue(AuthStore.signOut(app))
        assertEquals(AuthStore.Mode.NONE, AuthStore.mode.value)
        assertNull(AuthStore.account.value)
        assertRecordsPreserved()
    }

    @Test fun unavailableProvidersAndCancelledCallbackCannotChangeAccountOrData() = runBlocking {
        AuthStore.continueAsGuest(app)
        // The app may carry the public Google web client ID; the server switch still keeps every login off.
        assertTrue(AuthStore.serverProviders().values.all { !it })
        assertTrue(AuthStore.signInWithGoogle(app) is AuthStore.Result.Failed)
        AuthStore.Provider.entries.forEach {
            assertTrue(AuthStore.startBrowserLogin(app, it) is AuthStore.Result.Failed)
        }
        assertSame(AuthStore.Result.Cancelled,
            AuthStore.finishBrowserLogin(app, Uri.parse("fashionai://login?error=cancelled")))
        assertTrue(AuthStore.finishBrowserLogin(app,
            Uri.parse("fashionai://login?code=unverified-account-switch&provider=apple")) is AuthStore.Result.Failed)
        assertEquals(AuthStore.Mode.GUEST, AuthStore.mode.value)
        assertRecordsPreserved()
    }

    @Test fun legacySignedInPreferenceAndUuidResponseAreNotAuthenticationOrRestore() {
        val prefs = app.getSharedPreferences("auth", 0)
        prefs.edit().putString("mode", "SIGNED_IN").putString("email", "synthetic@example.invalid")
            .putString("name", "legacy").putString("provider", "google").commit()
        val authBefore = prefs.all.toMap()
        AuthStore.load(app)
        assertEquals(AuthStore.Mode.GUEST, AuthStore.mode.value)
        assertNull(AuthStore.account.value)
        assertEquals(authBefore, prefs.all)
        val metadata = AuthStore.parseLogin("""{"accountKey":"3f1c2a9e-0000-4000-8000-123456789abc","isNewAccount":true,"switched":true}""")
        assertNotNull(metadata)
        assertNull(AuthStore.account.value)
        assertEquals(AuthStore.Mode.GUEST, AuthStore.mode.value)
        assertRecordsPreserved()
    }

    @Test fun callbackAndRemoteErrorTextCannotBypassAuthenticationOrLeakDiagnostics() = runBlocking {
        val invalid = listOf("https://evil.example/login?code=x", "fashionai://user@login?code=x",
            "fashionai://login:123?code=x", "fashionai://login/extra?code=x", "fashionai://login?code=x#token")
        invalid.forEach {
            AuthStore.handleRedirect(Uri.parse(it))
            assertNull(AuthStore.consumeRedirect())
            assertTrue(AuthStore.finishBrowserLogin(app, Uri.parse(it)) is AuthStore.Result.Failed)
        }
        for (status in listOf(-1, 401, 500, 502, 503)) {
            assertFalse(AuthStore.messageFor(status, """{"detail":"synthetic-sensitive-diagnostic"}""")
                .contains("synthetic-sensitive-diagnostic"))
        }
        assertRecordsPreserved()
    }
}
