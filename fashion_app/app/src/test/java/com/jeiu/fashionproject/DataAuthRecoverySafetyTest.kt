package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import com.jeiu.fashionproject.data.*
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DataAuthRecoverySafetyTest {
    @Test fun lostRecoveryResponseRetainsIntendedAccountAndServerAcrossRetry() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
        server.start()
        val previousTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        val previousServer = BackendApi.BASE_URL
        val context = RuntimeEnvironment.getApplication() as Context
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = CredentialVault(context) { key }
        val destination = server.url("/").toString().trimEnd('/')
        val oldToken = "A".repeat(43)
        val newToken = "B".repeat(43)
        val requestedCode = "C".repeat(43)
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
            BackendApi.BASE_URL = destination
            assertTrue(vault.write(JSONObject().put("token", oldToken).put("origin", destination)
                .put("recovery", "D".repeat(43)).put("enabled", true)))
            DataAuth.initialize(context, vault)
            assertTrue(DataAuth.canSync)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertFalse(DataAuth.recover(requestedCode))
            assertTrue(DataAuth.isRestorePending)
            assertFalse(DataAuth.canSync)
            val durable = vault.read()
            assertEquals(requestedCode, durable.getString("requested_recovery"))
            assertEquals(destination, durable.getString("requested_recovery_server"))
            val before = server.requestCount
            BackendApi.BASE_URL = "https://other.invalid"
            assertFalse(DataAuth.resumePendingAuthentication())
            assertEquals(before, server.requestCount)
            assertNull(DataAuth.accessToken)
            BackendApi.BASE_URL = destination
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(JSONObject().put("accessToken", newToken).put("dataEnabled", true).toString()))
            assertTrue(DataAuth.resumePendingAuthentication())
            assertEquals(newToken, DataAuth.accessToken)
            assertFalse(vault.read().has("requested_recovery"))
            assertTrue(DataAuth.isRestorePending)
            assertFalse(DataAuth.canSync)
            DataAuth.markUnauthorized(oldToken)
            assertEquals(newToken, DataAuth.accessToken)
            assertEquals("restore_pending", DataAuth.state.value)
            val plaintext = MockWebServer()
            plaintext.start()
            try {
                BackendApi.BASE_URL = plaintext.url("/").toString().trimEnd('/')
                assertNull(BackendApi.postJson("/api/profile/gender", "{\"gender\":\"private\"}"))
                assertNull(BackendApi.getJson("/api/backup/snapshot"))
                assertFalse(BackendApi.delete("/api/me"))
                assertEquals(0, plaintext.requestCount)
            } finally {
                plaintext.shutdown()
                BackendApi.BASE_URL = destination
            }

        } finally {
            DataAuth.clearAfterServerDelete()
            HttpsURLConnection.setDefaultSSLSocketFactory(previousTls)
            BackendApi.BASE_URL = previousServer
            server.shutdown()
        }
    }
}
