package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.LocalVtonSession
import com.jeiu.fashionproject.data.TryOnConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/** MOCK engine over real loopback FastAPI. Tests repository transport and save/read,
 * NOT actual AI inference, a Compose screen, an emulator or a physical phone.
 * Requires existing P32_VTON_PYTHON and P32_VTON_BACKEND_DIR; never downloads anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P32LocalVtonLoopbackTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun syntheticInput(color: Int): String {
        val image = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(color)
            val output = ByteArrayOutputStream()
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
        } finally { image.recycle() }
    }

    private suspend fun completed(connection: TryOnConnection, id: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val state = LocalVtonRepository.status(connection, id)
            if (state.status == "completed") {
                assertTrue(state.resultAvailable)
                return
            }
            assertTrue("Synthetic job unexpectedly terminated: ${state.status}", state.status in setOf("queued", "running"))
            delay(50)
        }
        fail("Synthetic job did not complete within the bounded wait")
    }

    @Test fun realLoopbackRequestsBindNewSyntheticResultsAndPersistReadableImages() = runBlocking {
        val python = System.getenv("P32_VTON_PYTHON")
        val backend = System.getenv("P32_VTON_BACKEND_DIR")
        assumeTrue("Existing Python environment not supplied; loopback test not run", python != null && backend != null)
        require(File(python!!).isFile && File(backend!!, "tests/p32_android_loopback_server.py").isFile)
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val base = "http://127.0.0.1:$port"
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val temporary = Files.createTempDirectory("fashionai-p32-synthetic-").toFile()
        File(temporary, "SYNTHETIC_TEST_ONLY").writeText("No AI model or personal inputs")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val saved = File(app.cacheDir, "p32-synthetic-${UUID.randomUUID()}.png")
        val nullOutput = File(if (System.getProperty("os.name", "").orEmpty().startsWith("Windows")) "NUL" else "/dev/null")
        val builder = ProcessBuilder(python, "-m", "tests.p32_android_loopback_server", port.toString(), temporary.absolutePath)
            .directory(File(backend))
            .redirectOutput(ProcessBuilder.Redirect.to(nullOutput))
            .redirectError(ProcessBuilder.Redirect.to(nullOutput))
            .apply {
                environment().keys.filter { it.startsWith("LOCAL_VTON_") }.toList().forEach { environment().remove(it) }
                environment()["LOCAL_VTON_ENABLED"] = "false"
                environment()["P32_SYNTHETIC_TEST_ONLY"] = "approved-local-test-fixture"
                environment()["P32_TEST_TOKEN_SHA256"] = sha(token.toByteArray())
                environment()["HF_HUB_OFFLINE"] = "1"
                environment()["TRANSFORMERS_OFFLINE"] = "1"
                environment()["PYTHONDONTWRITEBYTECODE"] = "1"
            }
        var process: Process? = null
        try {
            val running = builder.start()
            process = running
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var reachable = false
            while (System.nanoTime() < deadline && running.isAlive) {
                reachable = runCatching {
                    val connection = URL("$base/health").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 500; connection.readTimeout = 500
                        connection.instanceFollowRedirects = false
                        connection.responseCode == 200
                    } finally { connection.disconnect() }
                }.getOrDefault(false)
                if (reachable) break
                delay(100)
            }
            assertTrue("Synthetic loopback server failed to start", reachable)
            assertTrue(LocalVtonSession.configure(base, token, allowLoopback = true))
            val connection = LocalVtonSession.connection!!
            LocalVtonRepository.check(connection)
            val person = syntheticInput(Color.GRAY)
            val red = syntheticInput(Color.RED)
            val blue = syntheticInput(Color.BLUE)
            val first = UUID.randomUUID().toString()
            val second = UUID.randomUUID().toString()
            LocalVtonRepository.submit(connection, first, person, listOf("tops" to red))
            completed(connection, first)
            val firstResult = LocalVtonRepository.result(connection, first)
            LocalVtonRepository.submit(connection, first, person, listOf("tops" to red))
            completed(connection, first)
            assertArrayEquals(firstResult, LocalVtonRepository.result(connection, first))
            assertEquals(1, JSONObject(File(temporary, "synthetic-calls.json").readText()).getInt("calls"))
            LocalVtonRepository.submit(connection, second, person, listOf("tops" to blue))
            completed(connection, second)
            val secondResult = LocalVtonRepository.result(connection, second)
            assertNotEquals(sha(firstResult), sha(secondResult))
            assertArrayEquals(firstResult, LocalVtonRepository.result(connection, first))
            assertEquals(2, JSONObject(File(temporary, "synthetic-calls.json").readText()).getInt("calls"))
            val journal = JSONObject(File(temporary, "jobs.json").readText())
            assertEquals(2, journal.length())
            assertEquals(first, journal.getJSONObject(first).getString("id"))
            assertEquals(second, journal.getJSONObject(second).getString("id"))
            LocalVtonRepository.save(app, secondResult, Uri.fromFile(saved), png = true)
            val reopened = LocalVtonRepository.openSavedImage(app, Uri.fromFile(saved))
            assertArrayEquals(saved.readBytes(), reopened)
            val expected = BitmapFactory.decodeByteArray(secondResult, 0, secondResult.size)
            val actual = BitmapFactory.decodeByteArray(reopened, 0, reopened.size)
            try {
                assertEquals(256, actual.width); assertEquals(384, actual.height)
                assertTrue("Saved PNG must preserve the received synthetic pixels", expected.sameAs(actual))
            } finally { expected.recycle(); actual.recycle() }
        } finally {
            LocalVtonSession.clear()
            process?.let {
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) }
                check(!it.isAlive) { "Test-owned server did not stop" }
            }
            if (saved.exists()) check(saved.delete())
            check(temporary.canonicalFile.parentFile == File(requireNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile)
            check(temporary.deleteRecursively()) { "Test-owned temporary directory cleanup failed" }
        }
    }
}
