package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.OfficialDemoScreen
import com.jeiu.fashionproject.ui.viewmodel.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficialDemoTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val journal = MemoryJournal()
    private val fake = Fake()
    private val model = OfficialDemoViewModel(fake, journal, true) { 1_000L }
    private var visible by mutableStateOf(true)
    private val output get() = File(app.cacheDir, "official-demo-test-save.png")
    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertTrue(contract is androidx.activity.result.contract.ActivityResultContracts.CreateDocument)
                dispatchResult(requestCode, Uri.fromFile(output))
            }
        }
    }
    @After fun cleanup() { compose.runOnIdle { model.viewModelScope.cancel() } }
    private fun screen() { compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        if (visible) OfficialDemoScreen({}, model)
    } } }
    private fun ready() {
        compose.runOnIdle { model.check() }
        compose.waitUntil(5000) { model.canStart }
    }
    private fun start() { compose.runOnIdle { model.start(OfficialDemoContract.EXAMPLE, true) } }
    private fun done() { compose.waitUntil(5000) { !model.state.working } }

    @Test fun onlyOfficialIdAndExactReviewedParametersCanBeSubmitted() {
        val body = JSONObject(OfficialDemoContract.body(OfficialDemoContract.EXAMPLE)).getJSONArray("data")
        assertEquals(8, body.length()); assertEquals("tops", body.getString(2)); assertEquals("flat-lay", body.getString(3))
        for (bad in listOf("https://example.com/private.png", "content://photo/1", "C:/photo.png", "", "official-tops-3/../x")) {
            assertThrows(DemoFailure::class.java) { OfficialDemoContract.body(bad) }
        }
    }
    @Test fun resultSourceRejectsArbitraryHostsCredentialsQueriesAndTraversal() {
        assertTrue(OfficialDemoContract.resultUrl(URL))
        listOf(URL.replace("https", "http"), URL.replace(".hf.space", ".evil.test"),
            URL.replace("https://", "https://token@"), "$URL?token=x", "$URL#x",
            URL.replace("/tmp/gradio/", "/tmp/gradio/../"), URL.replace("/tmp/gradio/", "/tmp/gradio/%2e%2e/"),
            "file:///tmp/gradio/a.webp").forEach { assertFalse(it, OfficialDemoContract.resultUrl(it)) }
    }
    @Test fun consentDeclineAndUserPhotoControlsNeverSubmit() {
        screen(); ready()
        compose.onNodeWithTag("officialDemoExample").performScrollTo().performClick()
        compose.onNodeWithTag("officialDemoGenerate").performClick()
        compose.onNodeWithText("전송하지 않기").performClick()
        compose.onNodeWithText("원본 전신 사진 선택").assertDoesNotExist()
        compose.onNodeWithText("자체 AI 서버 설정").assertDoesNotExist()
        assertEquals(0, fake.posts)
    }
    @Test fun mockedUiRequestComparisonAndPngSaveAreClearlyDemoOnly() {
        screen(); ready()
        compose.onNodeWithTag("officialDemoExample").performScrollTo().performClick()
        compose.onNodeWithTag("officialDemoGenerate").performClick()
        compose.onNodeWithTag("officialDemoConsent").performClick(); done()
        compose.onNodeWithTag("officialDemoSuccess").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("officialDemoCompare").performScrollTo().performClick()
        compose.onNodeWithContentDescription("공식 예제 원본").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("officialDemoSave").performScrollTo().performClick()
        compose.waitUntil(5000) {
            // IO completion posts to the paused Robolectric Android looper, not the Compose frame clock.
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !model.state.working
        }
        assertTrue(model.state.message, model.state.savedAt != 0L)
        assertArrayEquals(fake.image.png, output.readBytes())
        assertEquals(1, fake.posts)
        assertTrue(JSONObject(journal.value!!).has("savedAt"))
    }
    @Test fun doubleTapAndNavigationDoNotPostTwice() {
        fake.gate = CompletableDeferred(); screen(); ready(); start()
        compose.waitUntil(5000) { model.eventId != null }
        compose.runOnIdle { model.start(OfficialDemoContract.EXAMPLE, true); visible = false }
        compose.runOnIdle { visible = true; model.resume(); fake.gate!!.complete(Unit) }
        done(); assertEquals(1, fake.posts)
    }
    @Test fun responseLostDoesNotRetryPostOrShowSuccess() {
        fake.submitError = IOException("lost"); ready(); start(); done()
        assertNull(model.eventId); assertNull(model.state.result)
        assertTrue(model.state.message.contains("유실"))
        compose.runOnIdle { model.resume(); model.start(OfficialDemoContract.EXAMPLE, true); model.prepareNextTest(true) }
        assertEquals(1, fake.posts); assertFalse(model.canStart)
    }
    @Test fun journalFailurePreventsAnyPost() {
        journal.fail = true; ready(); start(); done()
        assertEquals(0, fake.posts); assertEquals("storage", model.state.phase)
    }
    @Test fun disconnectRestoresSameEventAfterProcessRecreationUsingGetOnly() {
        fake.awaitError = DemoFailure("stream_closed"); ready(); start(); done()
        val restored = OfficialDemoViewModel(fake, journal, true)
        compose.runOnIdle { fake.awaitError = null; restored.resume() }
        compose.waitUntil(5000) { restored.state.result != null }
        assertEquals(1, fake.posts); assertEquals(ID, restored.eventId)
        compose.runOnIdle { restored.viewModelScope.cancel() }
    }
    @Test fun stopDuringSubmitStillPersistsAcceptedEventWithoutPretendingServerCancelled() {
        fake.submitGate = CompletableDeferred(); ready(); start()
        compose.waitUntil(5000) { fake.posts == 1 }
        compose.runOnIdle { model.stopWaiting(); fake.submitGate!!.complete(Unit) }; done()
        assertEquals(ID, model.eventId); assertEquals(0, fake.gets)
        assertTrue(model.state.message.contains("서버 생성 취소"))
        compose.runOnIdle { model.resume() }; done(); assertNotNull(model.state.result); assertEquals(1, fake.posts)
    }
    @Test fun expiredAndQuotaAreDistinctAndNeverBecomeSuccess() {
        fake.awaitError = DemoFailure("expired"); ready(); start(); done()
        assertEquals("expired", model.state.phase); assertFalse(model.canResume); assertNull(model.state.result)
        assertNotEquals(OfficialDemoContract.message("expired"), OfficialDemoContract.message("quota"))
    }
    @Test fun adapterHasNoApplicationTokenHeadersAndParsesNewSseResult() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()) }.build()
            val repo = OfficialDemoRepository(client)
            server.enqueue(MockResponse().setBody("{\"event_id\":\"$ID\"}"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: heartbeat\ndata: null\n\nevent: complete\ndata: [{\"url\":\"$URL\"}]\n\n"))
            assertEquals(ID, repo.submit(OfficialDemoContract.EXAMPLE))
            assertEquals(URL, repo.awaitResult(ID) {})
            val post = server.takeRequest(); assertEquals("POST", post.method)
            assertNull(post.getHeader("Authorization")); assertNull(post.getHeader("Cookie")); assertNull(post.getHeader("X-Device-Id"))
            assertEquals("GET", server.takeRequest().method); assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun actualDecodeRejectsHtmlAndOversizedPixelsAndConvertsWebpToPng() {
        assertThrows(DemoFailure::class.java) { OfficialDemoRepository.decode("<html>not an image</html>".toByteArray()) }
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out); bitmap.recycle()
        val image = OfficialDemoRepository.decode(out.toByteArray())
        assertEquals(64, image.width); assertEquals(96, image.height)
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71), image.png.take(4).toByteArray())
        assertEquals(OfficialDemoContract.sha(image.png), image.sha256)
        val large = Bitmap.createBitmap(2100, 2100, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream(); large.compress(Bitmap.CompressFormat.PNG, 100, encoded); large.recycle()
        assertThrows(DemoFailure::class.java) { OfficialDemoRepository.decode(encoded.toByteArray()) }
    }
    @Test fun runtimeContractMismatchBlocksGeneration() {
        assertThrows(Exception::class.java) { OfficialDemoContract.verifyInfo("{\"named_endpoints\":{}}") }
    }
    @Test fun onlyReviewedOfficialExampleCdnRedirectIsAccepted() {
        val url = "https://us.aws.cdn.hf.co/xet-bridge-us/697e65ed440ea4e00c9311ad/6c12397c245919e4373b6c1da509fd0d1c20804fc6f45d25f0aa50971a7145f2?public-example-signature=test"
        assertTrue(OfficialDemoContract.exampleRedirect(java.net.URI(url)))
        assertFalse(OfficialDemoContract.exampleRedirect(java.net.URI(url.replace("us.aws.cdn.hf.co", "evil.test"))))
        assertFalse(OfficialDemoContract.exampleRedirect(java.net.URI(url.replace("/xet-bridge-us/", "/another-file/"))))
        assertFalse(OfficialDemoContract.exampleRedirect(java.net.URI(url.replace("https://", "https://user@"))))
    }
    @Test fun newAttemptClearsPreviousResultAndRequiresFreshConsentAfterCooldown() {
        var clock = 1000L
        val current = OfficialDemoViewModel(fake, journal, true) { clock }
        compose.runOnIdle { current.check() }; compose.waitUntil(5000) { current.canStart }
        compose.runOnIdle { current.start(OfficialDemoContract.EXAMPLE, false) }; assertEquals(0, fake.posts)
        compose.runOnIdle { current.start(OfficialDemoContract.EXAMPLE, true) }
        compose.waitUntil(5000) { current.state.result != null }
        compose.runOnIdle { current.prepareNextTest(true) }; assertFalse(current.canStart)
        compose.runOnIdle { clock += 86_400_001; current.prepareNextTest(true); current.check() }
        compose.waitUntil(5000) { current.canStart }
        assertNull(current.state.result)
        compose.runOnIdle { fake.awaitError = DemoFailure("server_failed"); current.start(OfficialDemoContract.EXAMPLE, true) }
        compose.waitUntil(5000) { !current.state.working }
        assertNull(current.state.result); assertEquals("failed", current.state.phase)
        compose.runOnIdle { current.viewModelScope.cancel() }
    }
    @Test fun adapterDoesNotFollowRedirectAndSeparatesQuotaAndClosedStream() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()) }.build()
            val repo = OfficialDemoRepository(client)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.test/photo"))
            try { repo.submit(OfficialDemoContract.EXAMPLE); fail("Redirect accepted") } catch (e: DemoFailure) { assertEquals("network", e.kind) }
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(429))
            try { repo.submit(OfficialDemoContract.EXAMPLE); fail("Quota accepted") } catch (e: DemoFailure) { assertEquals("quota", e.kind) }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: heartbeat\ndata: null\n\n"))
            try { repo.awaitResult(ID) {}; fail("Closed stream accepted") } catch (e: DemoFailure) { assertEquals("stream_closed", e.kind) }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun retryAfterZeroCannotRepeatGenerationPost() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()) }.build()
            val repo = OfficialDemoRepository(client)
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("{\"event_id\":\"$ID\"}"))
            try { repo.submit(OfficialDemoContract.EXAMPLE); fail("A generation POST was repeated") }
            catch (e: DemoFailure) { assertEquals("unavailable", e.kind) }
            assertEquals(1, server.requestCount)
            assertEquals("POST", server.takeRequest().method)
        } finally { server.shutdown() }
    }
    class MemoryJournal : DemoJournal {
        var value: String? = null; var fail = false
        override fun read() = value
        override fun write(value: String) { if (fail) throw IOException(); this.value = value }
    }
    class Fake : OfficialDemoGateway {
        var posts = 0; var gets = 0
        var submitError: Exception? = null; var awaitError: Exception? = null
        var gate: CompletableDeferred<Unit>? = null; var submitGate: CompletableDeferred<Unit>? = null
        val image: DemoImage by lazy {
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle()
            OfficialDemoRepository.decode(out.toByteArray())
        }
        override suspend fun check() {}
        override suspend fun example(id: String) = image
        override suspend fun submit(id: String): String { posts++; submitGate?.await(); submitError?.let { throw it }; return ID }
        override suspend fun awaitResult(eventId: String, progress: suspend (String) -> Unit): String {
            gets++; gate?.await(); awaitError?.let { throw it }; return URL
        }
        override suspend fun result(url: String) = image
    }
    companion object {
        const val ID = "0123456789abcdef0123456789abcdef"
        const val URL = "https://fashn-ai-fashn-vton-1-5.hf.space/gradio_api/file=/tmp/gradio/test/image.webp"
    }
}
