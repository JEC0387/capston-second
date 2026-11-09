package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.data.CredentialVault
import com.jeiu.fashionproject.data.DataAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection

/** Google · Kakao app rules with synthetic loopback TLS only; no external OAuth or real credentials. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthStoreBearerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    // Like P23DataPreservationTest, simulate a new process for each synthetic
    // account. The real application must keep its once-per-process init guard.
    private val nullableAuthFields = listOf("context", "vault", "recovery", "requestedRecovery",
        "requestedRecoveryServer", "binding")
    private val booleanAuthFields = listOf("storageLocked", "enabled", "restorePending", "legacyPending")
    private lateinit var previousAuthFields: Map<String, Any?>
    private lateinit var previousAuthPreferences: Map<String, *>
    private var previousAuthState = "uninitialized"
    private val authState: MutableStateFlow<String>
        get() = ReflectionHelpers.getStaticField(DataAuth::class.java, "_state")

    @Before fun isolateSyntheticAuthProcess() {
        previousAuthFields = (nullableAuthFields + booleanAuthFields).associateWith { name ->
            ReflectionHelpers.getStaticField<Any?>(DataAuth::class.java, name)
        }
        previousAuthPreferences = app.getSharedPreferences("data_auth", 0).all.toMap()
        previousAuthState = DataAuth.state.value
        nullableAuthFields.forEach { ReflectionHelpers.setStaticField(DataAuth::class.java, it, null) }
        booleanAuthFields.forEach { ReflectionHelpers.setStaticField(DataAuth::class.java, it, false) }
        authState.value = "uninitialized"
    }

    @After fun restorePreviousSyntheticAuthProcess() {
        if (!::previousAuthFields.isInitialized) return
        // Restore ciphertext too: a previous vault must not point at bytes written
        // using this test's independent AES key in a reused Robolectric sandbox.
        val editor = app.getSharedPreferences("data_auth", 0).edit().clear()
        previousAuthPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unsupported synthetic auth preference type")
            }
        }
        check(editor.commit())
        previousAuthFields.forEach { (name, value) ->
            ReflectionHelpers.setStaticField(DataAuth::class.java, name, value)
        }
        authState.value = previousAuthState
    }

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

    @Test fun emptySwitchedAccountCannotResumeUploadingPreviousAccountRecords() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
        val emptySnapshot = """{
            "version":1,"wardrobe":{"items":[]},"wearLogs":{"logs":[]},
            "profile":{"preferredStyles":[],"gender":null},"interests":{"items":[]},
            "tasteEvents":{"events":[]},"stylePreference":{"scores":[],"ratedCount":0},
            "savedOutfits":{"outfits":[]},"outfitPlans":{"plans":[]},"photos":{}
        }"""
        val originalToken = "A".repeat(43)
        val switchedToken = "B".repeat(43)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.path) {
                    "/api/auth/exchange" -> JSONObject().put("switched", true)
                        .put("accessToken", switchedToken).put("provider", "kakao")
                        .put("dataEnabled", true).put("name", "Synthetic B").toString()
                    "/api/health/database" -> """{"ready":true}"""
                    "/api/backup/snapshot" -> emptySnapshot
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val previousTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        val previousServer = BackendApi.BASE_URL
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = CredentialVault(app) { key }
        val records = app.getSharedPreferences("wardrobe_store", 0)
        val queue = app.getSharedPreferences("db_sync", 0)
        val pending = """{"wardrobe":{"path":"/api/wardrobe/sync","body":"synthetic-account-A"}}"""
        val original = File(app.filesDir, "wardrobe/social-preservation-original.bin")
        original.parentFile!!.mkdirs()
        original.writeBytes(byteArrayOf(1, 3, 5, 7))
        records.edit().putString("synthetic-account-A", "existing wardrobe").commit()
        queue.edit().putString("pending", pending).commit()
        val recordsBefore = records.all.toMap()
        val queueBefore = queue.all.toMap()
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
            BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
            assertTrue(vault.write(JSONObject().put("token", originalToken)
                .put("origin", BackendApi.BASE_URL).put("recovery", "C".repeat(43)).put("enabled", true)))
            DataAuth.initialize(app, vault)
            assertTrue(DataAuth.canSync)

            val result = AuthStore.finishBrowserLogin(app,
                Uri.parse("fashionai://login?code=abcdefghijklmnopqrstuvwxyz&provider=kakao"))

            assertEquals(recordsBefore, records.all)
            assertEquals(queueBefore, queue.all)
            assertArrayEquals(byteArrayOf(1, 3, 5, 7), original.readBytes())
            if (BuildConfig.PREVIEW_BUILD) {
                assertTrue(result is AuthStore.Result.Failed)
                assertEquals(0, server.requestCount)
                assertEquals(originalToken, DataAuth.accessToken)
                assertTrue(DataAuth.canSync)
            } else {
                val paths = List(3) { server.takeRequest(3, TimeUnit.SECONDS)?.path }
                assertEquals(listOf("/api/auth/exchange", "/api/health/database", "/api/backup/snapshot"), paths)
                assertEquals("No queued wardrobe POST may target the newly selected account", 3, server.requestCount)
                assertEquals(switchedToken, DataAuth.accessToken)
                assertFalse("An empty remote account is not permission to upload the previous account's records", DataAuth.canSync)
                assertTrue(DataAuth.isRestorePending)
                assertTrue(vault.read().getBoolean("restore_pending"))
                assertTrue("Account adoption must remain unresolved, not report completed login", result is AuthStore.Result.Failed)
            }
        } finally {
            // Only this Robolectric sandbox's synthetic credential is reset.
            DataAuth.clearAfterServerDelete()
            HttpsURLConnection.setDefaultSSLSocketFactory(previousTls)
            BackendApi.BASE_URL = previousServer
            server.shutdown()
        }
    }

    @Test fun invalidIdentityAndExpiredLoginDoNotInvalidateExistingBearer() = runBlocking {
        withSyntheticBearer { server, token, vault ->
            for (error in listOf("invalid_identity", "login_expired")) {
                val before = vault.read().toString()
                val responseBody = """{"detail":{"code":"$error","message":"synthetic remote message"}}"""
                socialFailureResponses(server, responseBody, statusCode = 200)
                val beforeRequests = server.requestCount
                val reply = DataAuth.socialLogin("/api/auth/exchange", JSONObject().put("code", "synthetic-expired-code"))
                if (BuildConfig.PREVIEW_BUILD) {
                    assertNull(reply)
                    assertEquals(0, server.requestCount)
                } else {
                    assertEquals(401, reply?.code)
                    // Both transports are valid: Android may expose the JSON, whereas
                    // desktop JDK fixed-length streaming can expose only status 401.
                    assertTrue(reply!!.body == responseBody || reply.body.isEmpty())
                    assertSocialRequestSequence(server, token, beforeRequests,
                        expectedStatusProbes = if (reply.body.isEmpty()) 1 else 0)
                }
                assertEquals(token, DataAuth.accessToken)
                assertTrue("Social identity errors do not mean the existing Bearer is invalid: $error", DataAuth.canSync)
                assertEquals("Existing credentials must remain unchanged", before, vault.read().toString())
            }
        }
    }

    @Test fun explicitAuthenticationRequiredStillDisablesRejectedBearer() = runBlocking {
        withSyntheticBearer { server, token, _ ->
            val responseBody = """{"detail":"authentication_required"}"""
            socialFailureResponses(server, responseBody, statusCode = 401)
            val reply = DataAuth.socialLogin("/api/auth/exchange", JSONObject().put("code", "synthetic-code"))
            assertEquals(token, DataAuth.accessToken)
            if (BuildConfig.PREVIEW_BUILD) {
                assertNull(reply)
                assertEquals(0, server.requestCount)
                assertTrue(DataAuth.canSync)
            } else {
                assertEquals(401, reply?.code)
                assertTrue(reply!!.body == responseBody || reply.body.isEmpty())
                assertSocialRequestSequence(server, token, 0,
                    expectedStatusProbes = if (reply.body.isEmpty()) 1 else 0)
                assertFalse(DataAuth.canSync)
                assertEquals("reauth_required", DataAuth.state.value)
            }
        }
    }

    @Test fun emptySocial401WithValidStatusPreservesExistingBearer() = runBlocking {
        assertEmptySocial401(statusCode = 200, mustDisable = false)
    }

    @Test fun emptySocial401WithRejectedStatusBlocksSynchronization() = runBlocking {
        assertEmptySocial401(statusCode = 401, mustDisable = true)
    }

    @Test fun emptySocial401WithUnavailableStatusDoesNotGuessInvalidBearer() = runBlocking {
        assertEmptySocial401(statusCode = 503, mustDisable = false)
    }

    private suspend fun assertEmptySocial401(statusCode: Int, mustDisable: Boolean) {
        withSyntheticBearer { server, token, vault ->
            socialFailureResponses(server, "", statusCode)
            val before = vault.read().toString()
            val reply = DataAuth.socialLogin("/api/auth/exchange", JSONObject().put("code", "synthetic-code"))
            assertEquals(token, DataAuth.accessToken)
            if (BuildConfig.PREVIEW_BUILD) {
                assertNull(reply)
                assertEquals(0, server.requestCount)
                assertTrue(DataAuth.canSync)
                assertEquals(before, vault.read().toString())
            } else {
                assertEquals(401, reply?.code)
                assertEquals("", reply?.body)
                assertSocialRequestSequence(server, token, 0, expectedStatusProbes = 1)
                assertEquals(!mustDisable, DataAuth.canSync)
                if (mustDisable) assertEquals("reauth_required", DataAuth.state.value)
                else assertEquals("An inconclusive social denial must preserve the existing credential", before, vault.read().toString())
            }
        }
    }

    private fun socialFailureResponses(server: MockWebServer, body: String, statusCode: Int) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/auth/exchange" -> MockResponse().setResponseCode(401)
                    .setHeader("Content-Type", "application/json").setBody(body)
                "/api/auth/status" -> MockResponse().setResponseCode(statusCode)
                    .setHeader("Content-Type", "application/json").setBody(
                        if (statusCode == 200) """{"authenticated":true,"dataEnabled":true}"""
                        else """{"detail":"synthetic-status-denial"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun assertSocialRequestSequence(server: MockWebServer, token: String, beforeRequests: Int,
                                           expectedStatusProbes: Int) {
        assertEquals("A failed social POST must never be replayed", 1 + expectedStatusProbes,
            server.requestCount - beforeRequests)
        val requests = List(1 + expectedStatusProbes) { server.takeRequest(3, TimeUnit.SECONDS)!! }
        assertEquals("POST", requests.first().method)
        assertEquals("/api/auth/exchange", requests.first().path)
        requests.drop(1).forEach {
            assertEquals("GET", it.method)
            assertEquals("/api/auth/status", it.path)
        }
        requests.forEach { assertEquals("Bearer $token", it.getHeader("Authorization")) }
    }

    @Test fun providerAvailabilityRespectsPreviewIsolationEvenWhenServerIsConfigured() = runBlocking {
        withSyntheticBearer { server, _, _ ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"google":true,"kakao":true,"facebook":true,"apple":true}"""))
            val providers = AuthStore.serverProviders()
            assertFalse(providers.getValue("facebook"))
            assertFalse(providers.getValue("apple"))
            if (BuildConfig.PREVIEW_BUILD) {
                assertFalse(AuthStore.isGoogleConfigured())
                assertTrue(providers.values.all { !it })
                assertTrue(AuthStore.signInWithGoogle(app) is AuthStore.Result.Failed)
                assertTrue(AuthStore.startBrowserLogin(app, AuthStore.Provider.KAKAO) is AuthStore.Result.Failed)
                assertEquals("QA must not query production login or launch an identity request", 0, server.requestCount)
            } else {
                assertTrue(providers.getValue("google"))
                assertTrue(providers.getValue("kakao"))
                assertEquals("/api/auth/providers", server.takeRequest(3, TimeUnit.SECONDS)?.path)
                assertEquals(1, server.requestCount)
            }
        }
    }

    private suspend fun withSyntheticBearer(block: suspend (MockWebServer, String, CredentialVault) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
        server.start()
        val previousTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        val previousServer = BackendApi.BASE_URL
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = CredentialVault(app) { key }
        val token = "D".repeat(43)
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
            BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
            assertTrue(vault.write(JSONObject().put("token", token).put("origin", BackendApi.BASE_URL)
                .put("recovery", "E".repeat(43)).put("enabled", true)))
            DataAuth.initialize(app, vault)
            assertTrue(DataAuth.canSync)
            block(server, token, vault)
        } finally {
            DataAuth.clearAfterServerDelete()
            HttpsURLConnection.setDefaultSSLSocketFactory(previousTls)
            BackendApi.BASE_URL = previousServer
            server.shutdown()
        }
    }
}
