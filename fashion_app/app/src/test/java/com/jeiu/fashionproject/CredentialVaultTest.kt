package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import com.jeiu.fashionproject.data.CredentialVault
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CredentialVaultTest {
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Before fun clear() { context.getSharedPreferences("data_auth", 0).edit().clear().commit() }

    @Test fun migrationEncryptsAndAtomicallyRemovesPlaintext() {
        val prefs = context.getSharedPreferences("data_auth", 0)
        prefs.edit().putString("token", "secret-token").putString("recovery", "secret-recovery")
            .putString("origin", "https://example.test").putBoolean("restore_pending", true).commit()
        val vault = CredentialVault(context) { key }
        assertEquals("secret-token", vault.read().getString("token"))
        assertFalse(prefs.contains("token"))
        assertFalse(prefs.all.toString().contains("secret-token"))
        assertTrue(CredentialVault(context) { key }.read().getBoolean("restore_pending"))
    }

    @Test fun writesUseDifferentNoncesAndAuthenticateCiphertext() {
        val vault = CredentialVault(context) { key }
        val prefs = context.getSharedPreferences("data_auth", 0)
        val saved = JSONObject().put("token", "private")
        assertTrue(vault.write(saved))
        val first = prefs.getString("vault_v1", null)
        assertTrue(vault.write(saved))
        assertNotEquals(first, prefs.getString("vault_v1", null))
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(runCatching { CredentialVault(context) { wrongKey }.read() }.isFailure)
        assertEquals("private", vault.read().getString("token"))
    }

    @Test fun missingKeyNeverErasesLegacyCredentials() {
        val prefs = context.getSharedPreferences("data_auth", 0)
        prefs.edit().putString("token", "preserve-me").commit()
        assertTrue(runCatching { CredentialVault(context) { error("key unavailable") }.read() }.isFailure)
        assertEquals("preserve-me", prefs.getString("token", null))
    }
}
