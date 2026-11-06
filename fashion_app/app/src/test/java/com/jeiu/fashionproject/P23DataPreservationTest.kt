package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.net.Uri
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection

/** Real auth/restore pipeline against loopback TLS only. All records/files/credentials are synthetic.
 * No DbSync worker is started: the original persisted queue is part of the preservation assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P23DataPreservationTest {
    // Robolectric can reuse a sandbox across test classes. DataAuth intentionally initializes
    // only once per real application process; simulate a fresh synthetic process here without
    // changing that production guard, or retaining another test's context/vault/account.
    private val nullableAuthFields = listOf("context", "vault", "recovery", "requestedRecovery",
        "requestedRecoveryServer", "binding")
    private val booleanAuthFields = listOf("storageLocked", "enabled", "restorePending", "legacyPending")
    private lateinit var previousAuthFields: Map<String, Any?>
    private var previousAuthState = "uninitialized"
    private val authState: MutableStateFlow<String>
        get() = ReflectionHelpers.getStaticField(DataAuth::class.java, "_state")

    @Before fun isolateSyntheticApplicationAuth() {
        previousAuthFields = (nullableAuthFields + booleanAuthFields).associateWith { name ->
            ReflectionHelpers.getStaticField<Any?>(DataAuth::class.java, name)
        }
        previousAuthState = DataAuth.state.value
        nullableAuthFields.forEach { ReflectionHelpers.setStaticField(DataAuth::class.java, it, null) }
        booleanAuthFields.forEach { ReflectionHelpers.setStaticField(DataAuth::class.java, it, false) }
        authState.value = "uninitialized"
    }

    @After fun restorePreviousApplicationAuth() {
        if (::previousAuthFields.isInitialized) {
            previousAuthFields.forEach { (name, value) ->
                ReflectionHelpers.setStaticField(DataAuth::class.java, name, value)
            }
            authState.value = previousAuthState
        }
    }

    @Test fun failedCancelledTimedOutAndEmptyAccountRecoveryNeverReplacesLocalRecords() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Context
        val photo=File(context.filesDir,"p23-synthetic-photo.bin").apply {writeBytes(byteArrayOf(1,3,5,7,9))}
        val originalPhoto=photo.readBytes()
        val item=WardrobeItem(id="p23-owned",name="Synthetic local garment",category="상의",colorName="test",
            colorValue=0xff123456, imageUri=Uri.fromFile(photo).toString(),addedAt=1L)
        WardrobeStore.writeForRestore(context,listOf(item))
        val stores=listOf("wardrobe_store","db_sync","manual_body_measurements","avatar_visual_profile_v1",
            "avatar_fitting_overlay_v1","profile_store","user_prefs","product_interests","saved_outfits")
        for (name in stores.drop(1)) {
            assertTrue(context.getSharedPreferences(name,0).edit().putString("p23-preserve","synthetic-$name").commit())
        }
        val pending="""{"wardrobe":{"path":"/api/wardrobe/sync","body":"synthetic pending original"}}"""
        assertTrue(context.getSharedPreferences("db_sync",0).edit().putString("pending",pending).commit())
        val original=stores.associateWith {context.getSharedPreferences(it,0).all.toMap()}
        fun assertPreserved() {
            stores.forEach {name -> assertEquals("Live store changed: $name",original[name],context.getSharedPreferences(name,0).all)}
            assertArrayEquals(originalPhoto,photo.readBytes())
            assertFalse("Rejected recovery must not install a replacement journal",RestoreJournal.hasPending(context))
        }

        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val server=MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
            start()
        }
        val previousTls=HttpsURLConnection.getDefaultSSLSocketFactory()
        val previousServer=BackendApi.BASE_URL
        val destination=server.url("/").toString().trimEnd('/')
        val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        val vault=CredentialVault(context) {key}
        val originalToken="A".repeat(43)
        val originalRecovery="B".repeat(43)
        val requestedRecovery="C".repeat(43)
        val switchedToken="D".repeat(43)
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
            BackendApi.BASE_URL=destination
            assertTrue(vault.write(JSONObject().put("token",originalToken).put("origin",destination)
                .put("recovery",originalRecovery).put("enabled",true)))
            DataAuth.initialize(context,vault)
            assertEquals("Synthetic account must initialize before recovery scenarios",originalToken,DataAuth.accessToken)
            assertTrue("Synthetic account starts enabled",DataAuth.canSync)

            assertSame(BackupRestore.Result.InvalidCode,BackupRestore.restore(context,"cancelled-invalid-input"))
            assertEquals(0,server.requestCount)
            assertPreserved()

            if (BuildConfig.PREVIEW_BUILD) {
                assertFalse(DbSync.isEnabled)
                assertSame(BackupRestore.Result.Offline,BackupRestore.restore(context,requestedRecovery))
                assertNull(BackendApi.getJson("/api/backup/snapshot"))
                assertEquals("Preview must not send auth or personal records even to a configured endpoint",0,server.requestCount)
                assertPreserved()
                return@runBlocking
            }

            // Both authentication rejection and server failure must retain the old account and data.
            for (code in listOf(401,500)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
                assertSame(BackupRestore.Result.Offline,BackupRestore.restore(context,requestedRecovery))
                assertEquals("/api/auth/recover",server.takeRequest(3,TimeUnit.SECONDS)?.path)
                assertEquals(originalToken,DataAuth.accessToken)
                assertEquals(originalRecovery,DataAuth.recoveryCode)
                assertTrue(DataAuth.isRestorePending)
                assertFalse(DataAuth.canSync)
                assertPreserved()
            }

            // Exercise the production HTTP read timeout, not a mock Boolean 'offline' response.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertSame(BackupRestore.Result.Offline,BackupRestore.restore(context,requestedRecovery))
            assertEquals("/api/auth/recover",server.takeRequest(3,TimeUnit.SECONDS)?.path)
            assertEquals(originalToken,DataAuth.accessToken)
            assertEquals(requestedRecovery,vault.read().getString("requested_recovery"))
            assertFalse(DataAuth.canSync)
            assertPreserved()

            // Cancel after the request was accepted but before response delivery. Never adopt a
            // delayed credential or touch local stores on behalf of a cancelled caller.
            val queuedDispatcher=server.dispatcher
            val releaseResponse=CountDownLatch(1)
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    check(releaseResponse.await(5,TimeUnit.SECONDS)) { "Synthetic cancellation was not released" }
                    return MockResponse().setBody(JSONObject().put("accessToken",switchedToken).put("dataEnabled",true).toString())
                }
            }
            val cancelled=launch(Dispatchers.Default) {BackupRestore.restore(context,requestedRecovery)}
            try {
                assertEquals("/api/auth/recover",server.takeRequest(3,TimeUnit.SECONDS)?.path)
            } finally {cancelled.cancel();releaseResponse.countDown()}
            cancelled.join()
            server.dispatcher=queuedDispatcher
            assertEquals(originalToken,DataAuth.accessToken)
            assertFalse(DataAuth.canSync)
            assertPreserved()

            // Authentication can succeed for a different account whose snapshot has no records.
            // The empty snapshot is NOT permission to replace this device's existing wardrobe.
            server.enqueue(MockResponse().setBody(JSONObject().put("accessToken",switchedToken).put("dataEnabled",true).toString()))
            server.enqueue(MockResponse().setBody("""{"ready":true}"""))
            server.enqueue(MockResponse().setBody("""{
                "version":1,"wardrobe":{"items":[]},"wearLogs":{"logs":[]},
                "profile":{"preferredStyles":[],"gender":null},"interests":{"items":[]},
                "tasteEvents":{"events":[]},"stylePreference":{"scores":[],"ratedCount":0},
                "savedOutfits":{"outfits":[]},"outfitPlans":{"plans":[]},"photos":{}
            }"""))
            assertSame(BackupRestore.Result.NotFound,BackupRestore.restore(context,requestedRecovery))
            assertEquals("/api/auth/recover",server.takeRequest(3,TimeUnit.SECONDS)?.path)
            assertEquals("/api/health/database",server.takeRequest(3,TimeUnit.SECONDS)?.path)
            assertEquals("/api/backup/snapshot",server.takeRequest(3,TimeUnit.SECONDS)?.path)
            assertEquals(switchedToken,DataAuth.accessToken)
            assertTrue(DataAuth.isRestorePending)
            assertFalse("Old local records must not sync into the new empty account",DataAuth.canSync)
            assertPreserved()

            val beforeStaleUnauthorized=vault.read().toString()
            DataAuth.markUnauthorized(originalToken)
            assertEquals(switchedToken,DataAuth.accessToken)
            assertEquals(beforeStaleUnauthorized,vault.read().toString())
            assertPreserved()
        } finally {
            DataAuth.clearAfterServerDelete()
            HttpsURLConnection.setDefaultSSLSocketFactory(previousTls)
            BackendApi.BASE_URL=previousServer
            server.shutdown()
        }
    }
}
