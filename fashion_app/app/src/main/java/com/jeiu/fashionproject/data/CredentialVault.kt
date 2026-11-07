package com.jeiu.fashionproject.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption; an unreadable vault is retained for explicit recovery. */
internal class CredentialVault(
    context: Context,
    private val keyProvider: () -> SecretKey = { deviceKey() }
) {
    private val prefs = context.getSharedPreferences("data_auth", Context.MODE_PRIVATE)

    fun read(): JSONObject {
        prefs.getString("vault_v1", null)?.let { encoded ->
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD("FashionAI.data_auth.v1".toByteArray())
            return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
        val legacy = JSONObject()
        listOf("token", "recovery", "origin").forEach { key -> prefs.getString(key, null)?.let { legacy.put(key, it) } }
        listOf("enabled", "restore_pending", "legacy_pending").forEach { key ->
            if (prefs.contains(key)) legacy.put(key, prefs.getBoolean(key, false))
        }
        // Commit ciphertext and remove plaintext in the same preferences transaction.
        if (legacy.length() > 0) check(write(legacy)) { "credential migration failed" }
        return legacy
    }

    fun write(value: JSONObject): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD("FashionAI.data_auth.v1".toByteArray())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return prefs.edit().clear().putString("vault_v1", encoded).commit()
    }

    companion object {
        @Synchronized private fun deviceKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey("fashionai.data-auth.v1", null) as? SecretKey)?.let { return it }
            // Never silently replace a key that is present but inaccessible.
            check(!store.containsAlias("fashionai.data-auth.v1"))
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("fashionai.data-auth.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
    }
}
