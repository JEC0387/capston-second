package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class LocalVtonRepositoryTest {
    private val server=MockWebServer()
    private lateinit var connection: TryOnConnection
    private val id=UUID.randomUUID().toString()
    @Before fun setup() {
        server.start()
        assertTrue(LocalVtonSession.configure(server.url("/").toString(),"test-local-token-00000000000000000001",true))
        connection=LocalVtonSession.connection!!
    }
    @After fun cleanup() { LocalVtonSession.clear();TryOnSession.clear();server.shutdown() }
    private fun config(ready: Boolean=true,engine: String=LocalVtonRepository.ENGINE)=
        JSONObject().put("engine",engine).put("paid_api",false).put("ready",ready).put("configured",ready)
            .put("max_garments",1).put("result_ttl_seconds",300).put("reason","cuda_unavailable").toString()
    private fun png():ByteArray {
        val bitmap=Bitmap.createBitmap(256,384,Bitmap.Config.ARGB_8888)
        val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle()
        return out.toByteArray()
    }
    @Test fun devHttpIsExplicitAndExternalHttpOrMalformedBasesAreBlocked() {
        assertFalse(LocalVtonSession.configure("http://127.0.0.1:8000","A".repeat(43)))
        assertFalse(LocalVtonSession.configure("http://192.168.0.10:8000","A".repeat(43),true))
        for(base in listOf("https://user:pass@example.test","https://example.test/path","https://example.test?token=bad","https://example.test/#bad"))
            assertFalse(LocalVtonSession.configure(base,"A".repeat(43)))
        assertFalse(LocalVtonSession.configure("https://example.test","short"))
        assertTrue(LocalVtonSession.configure("https://example.test","A".repeat(43)))
    }
    @Test fun transportUsesOnlyLocalRoutesAndSeparateCredentials()=runBlocking {
        TryOnSession.configure("https://paid.example","paid-test-token-00000000000000000001")
        server.enqueue(MockResponse().setBody(config()))
        LocalVtonRepository.check(connection)
        assertEquals("/api/local-vton/config",server.takeRequest().path)
        server.enqueue(MockResponse().setBody("{}"))
        LocalVtonRepository.submit(connection,id,"inline-person",listOf("tops" to "inline-garment"))
        val request=server.takeRequest()
        assertEquals("/api/local-vton/jobs",request.path)
        val body=JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("consent"));assertEquals(1,body.getJSONArray("garments").length())
        assertEquals("flat-lay",body.getString("garment_photo_type"))
        server.enqueue(MockResponse().setHeader("Content-Type","image/png").setBody(okio.Buffer().write(png())))
        LocalVtonRepository.validatePng(LocalVtonRepository.result(connection,id))
        assertEquals("/api/local-vton/jobs/"+id+"/result",server.takeRequest().path)
        assertEquals("https://paid.example",TryOnSession.connection!!.base)
    }
    @Test fun unreadyOrWrongEngineNeverLooksLikeSuccess()=runBlocking {
        for(response in listOf(config(false),config(true,"paid-engine"))) {
            server.enqueue(MockResponse().setBody(response))
            try {LocalVtonRepository.check(connection);fail("Must reject unavailable/wrong engine")} catch(_:TryOnFailure) {}
            assertEquals("GET",server.takeRequest().method)
        }
        assertEquals(2,server.requestCount)
    }
    @Test fun unsupportedMultipleGarmentsAndPathTraversalNeverReachServer()=runBlocking {
        try {LocalVtonRepository.submit(connection,id,"x",listOf("tops" to "x","bottoms" to "y"));fail()} catch(_:TryOnFailure) {}
        try {LocalVtonRepository.status(connection,"../jobs");fail()} catch(_:TryOnFailure) {}
        assertEquals(0,server.requestCount)
    }
    @Test fun redirectIsNotFollowedToPaidOrOtherHost()=runBlocking {
        val other=MockWebServer();other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/api/tryon/jobs")))
            try {LocalVtonRepository.check(connection);fail()} catch(_:TryOnHttpFailure) {}
            assertEquals(1,server.requestCount);assertEquals(0,other.requestCount)
        } finally {other.shutdown()}
    }
    @Test fun pngAndJpgSavePreserveImageFormatAndInvalidOutputIsRejected()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        for(isPng in listOf(true,false)) {
            val file=File(app.cacheDir,if(isPng) "vton-test.png" else "vton-test.jpg")
            LocalVtonRepository.save(app,png(),Uri.fromFile(file),isPng)
            val bytes=file.readBytes()
            if(isPng) assertEquals(137,bytes[0].toInt() and 255) else assertEquals(255,bytes[0].toInt() and 255)
        }
        try {LocalVtonRepository.validatePng("fake image".toByteArray());fail()} catch(_:TryOnFailure) {}
        val truncated=png().copyOf(33) // Valid signature/IHDR but missing image data.
        try {LocalVtonRepository.validatePng(truncated);fail("Truncated PNG must not be shown as a generated result")}
        catch(_:TryOnFailure) {}
    }
}
