package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P57PhotoConsentTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Suppress("DEPRECATION")
    @Test fun legacyPaidEntryFailsBeforeReadingInvalidPhotoOrReportingProgress() = runBlocking {
        var progress = 0
        val failure = runCatching {
            TryOnPipeline.run(app, Uri.parse("content://missing-provider/no-photo"),
                TryOnPlan(emptyList(), emptyList(), null), 1) { progress++ }
        }.exceptionOrNull()
        assertTrue(failure is TryOnFailure)
        assertTrue(failure!!.message!!.contains("전송 동의"))
        assertEquals(0, progress)
    }

    @Test fun confirmationIsBoundToPhotoHttpsServerPurposeAndSingleRequest() {
        val photo = Uri.parse("content://synthetic/image-A")
        val consent = ClothingPhotoConsent.confirm(photo.toString(), "https://example.invalid")!!
        assertTrue(consent.matches(photo, "https://example.invalid"))
        assertFalse(consent.matches(Uri.parse("content://synthetic/image-B"), "https://example.invalid"))
        assertFalse(consent.matches(photo, "https://another.invalid"))
        assertEquals(ClothingPhotoConsent.CLASSIFY, consent.purpose)
        assertTrue(consent.claimOnce()); assertFalse(consent.claimOnce())
        assertNull(ClothingPhotoConsent.confirm(photo.toString(), "http://example.invalid"))
        assertNull(ClothingPhotoConsent.confirm(photo.toString(), "https://user:secret@example.invalid"))
        assertNull(ClothingPhotoConsent.confirm(photo.toString(), "https://example.invalid?secret=value"))
    }

    @Test fun noConsentAndWrongImageAreRejectedBeforeReadingOrOpeningNetwork() = runBlocking {
        val server = MockWebServer().apply { start() }
        val old = BackendApi.BASE_URL
        val missing = Uri.parse("content://missing-provider/no-photo")
        try {
            BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
            val classify = runCatching { BackendApi.classifyClothing(app, missing) }.exceptionOrNull()
            val detect = runCatching { BackendApi.detectObjects(app, missing) }.exceptionOrNull()
            assertTrue(classify is IllegalStateException); assertTrue(classify!!.message!!.contains("동의"))
            assertTrue(detect is IllegalStateException); assertTrue(detect!!.message!!.contains("동의"))
            BackendApi.BASE_URL = "https://example.invalid"
            val otherPhoto = ClothingPhotoConsent.confirm("content://different/photo", BackendApi.BASE_URL)!!
            assertTrue(runCatching { BackendApi.classifyClothing(app, missing, consent = otherPhoto) }.exceptionOrNull() is IllegalStateException)
            assertEquals(0, server.requestCount)
        } finally { BackendApi.BASE_URL = old; server.shutdown() }
    }

    @Test fun productionRequiresBearerAndPurposeHeaderWhilePreviewSendsNothing() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
            start()
        }
        val previousTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        val old = BackendApi.BASE_URL
        val photo = File(app.cacheDir, "p57-synthetic-upload.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val uri = Uri.fromFile(photo)
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
            BackendApi.BASE_URL = server.url("/").toString().trimEnd('/')
            val consent = ClothingPhotoConsent.confirm(uri.toString(), BackendApi.BASE_URL)!!
            // Missing authentication cannot read a deliberately invalid URI or contact the server.
            val missing = Uri.parse("content://missing-provider/no-photo")
            assertTrue(runCatching { BackendApi.classifyClothing(app, missing,
                consent = ClothingPhotoConsent.confirm(missing.toString(), BackendApi.BASE_URL)) }.isFailure)
            assertEquals(0, server.requestCount)
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val vault = CredentialVault(app) { key }
            val token = "S".repeat(43)
            assertTrue(vault.write(JSONObject().put("token", token).put("origin", BackendApi.BASE_URL).put("enabled", true)))
            DataAuth.initialize(app, vault)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"candidates\":[]}"))
            val result = runCatching { BackendApi.classifyClothing(app, uri, consent = consent) }
            if (BuildConfig.PREVIEW_BUILD) {
                assertTrue(result.exceptionOrNull() is IllegalStateException)
                assertEquals(0, server.requestCount)
            } else {
                assertEquals(emptyList<ClothingCandidate>(), result.getOrThrow())
                val request = server.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("/api/clothing/classify", request.path)
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertEquals(ClothingPhotoConsent.CLASSIFY, request.getHeader("X-Photo-Processing-Consent"))
                assertTrue(request.body.readByteArray().asList().windowed(4).any { it == listOf<Byte>(1, 2, 3, 4) })
                assertTrue(runCatching { BackendApi.classifyClothing(app, uri, consent = consent) }.isFailure)
                assertEquals(1, server.requestCount)
            }
            val wrongPurpose = ClothingPhotoConsent.confirm(uri.toString(), BackendApi.BASE_URL)!!
            assertTrue(runCatching { BackendApi.detectObjects(app, uri, consent = wrongPurpose) }.isFailure)
        } finally {
            DataAuth.clearAfterServerDelete()
            BackendApi.BASE_URL = old
            HttpsURLConnection.setDefaultSSLSocketFactory(previousTls)
            server.shutdown()
        }
    }
}
