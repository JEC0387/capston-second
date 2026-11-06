package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.LocalVtonSession
import com.jeiu.fashionproject.data.TryOnConnection
import com.jeiu.fashionproject.data.TryOnFailure
import com.jeiu.fashionproject.data.TryOnHttpFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in real FastAPI transport check, NOT model inference or device verification.
 * Uses an existing Python environment, loopback only, no downloads or input images.
 * Run with P30_VTON_PYTHON and P30_VTON_BACKEND_DIR. Missing setup is a reported skip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P30LocalVtonLiveContractTest {
    @Test fun realFastApiAuthenticatesAndAndroidRejectsUnpreparedModel() = runBlocking {
        val python = System.getenv("P30_VTON_PYTHON")
        val backend = System.getenv("P30_VTON_BACKEND_DIR")
        assumeTrue("Opt-in existing FastAPI Python environment not supplied", python != null && backend != null)
        require(File(python!!).isFile && File(backend!!, "local_vton_server.py").isFile)
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val base = "http://127.0.0.1:$port"
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        val temporary = Files.createTempDirectory("fashionai-p30-contract-").toFile()
        val journal = File(temporary, "jobs.json")
        // Android's compile stubs omit Redirect.DISCARD even though host JVMs support it.
        val nullOutput = File(if (System.getProperty("os.name", "").orEmpty().startsWith("Windows")) "NUL" else "/dev/null")
        val processBuilder = ProcessBuilder(python, "-m", "uvicorn", "local_vton_server:app",
            "--host", "127.0.0.1", "--port", port.toString(), "--no-access-log", "--log-level", "critical")
            .directory(File(backend))
            .redirectOutput(ProcessBuilder.Redirect.to(nullOutput))
            .redirectError(ProcessBuilder.Redirect.to(nullOutput))
            .apply {
                environment().keys.filter { it.startsWith("LOCAL_VTON_") }.toList().forEach { environment().remove(it) }
                environment()["LOCAL_VTON_ENABLED"] = "false"
                environment()["LOCAL_VTON_LOOPBACK_HTTP"] = "true"
                environment()["LOCAL_VTON_TOKEN_HASHES"] = digest
                environment()["LOCAL_VTON_JOURNAL"] = journal.absolutePath
                environment()["HF_HUB_OFFLINE"] = "1"
                environment()["TRANSFORMERS_OFFLINE"] = "1"
                environment()["PYTHONDONTWRITEBYTECODE"] = "1"
            }
        var process: Process? = null
        fun get(path: String, credential: String? = null): Pair<Int, String> {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 1000; connection.readTimeout = 1000
                connection.instanceFollowRedirects = false
                credential?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                val status = connection.responseCode
                val stream = if (status < 400) connection.inputStream else connection.errorStream
                return status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally { connection.disconnect() }
        }
        try {
            val running = processBuilder.start()
            process = running
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var health: Pair<Int, String>? = null
            while (System.nanoTime() < deadline && running.isAlive) {
                health = runCatching { get("/health") }.getOrNull()
                if (health?.first == 200) break
                Thread.sleep(100)
            }
            assertEquals("Real loopback FastAPI did not become reachable", 200, health?.first)
            assertEquals("FashionAI local VTON", JSONObject(health!!.second).getString("service"))
            assertFalse(JSONObject(health.second).getBoolean("ready"))
            assertEquals(401, get("/api/local-vton/config").first)
            val config = get("/api/local-vton/config", token)
            assertEquals(200, config.first)
            val payload = JSONObject(config.second)
            assertEquals("fashn-vton-1.5-local", payload.getString("engine"))
            assertEquals("disabled", payload.getString("reason"))
            assertFalse(payload.getBoolean("ready")); assertFalse(payload.getBoolean("paid_api"))
            assertTrue(LocalVtonSession.configure(base, token, allowLoopback = true))
            try {
                LocalVtonRepository.check(TryOnConnection(base, "invalid-test-token-00000000000000000"))
                fail("Real server must reject incorrect credentials")
            } catch (failure: TryOnHttpFailure) { assertEquals(401, failure.code) }
            try {
                LocalVtonRepository.check(LocalVtonSession.connection!!)
                fail("A reachable authenticated server without a model must not be ready")
            } catch (failure: TryOnFailure) {
                assertFalse(failure.message.isNullOrBlank())
                assertFalse(failure.message.orEmpty().contains(token))
            }
            // No POST, images or model result: transport/auth readiness only.
            if (journal.exists()) assertEquals(0, JSONObject(journal.readText()).length())
        } finally {
            LocalVtonSession.clear()
            process?.let {
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) }
                check(!it.isAlive) { "Test-owned server did not stop" }
            }
            check(temporary.canonicalFile.parentFile == File(requireNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile)
            check(temporary.deleteRecursively()) { "Test-owned temporary directory cleanup failed" }
        }
    }
}
