package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** Synthetic wire/file validation, no HTTP, model or production account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P48ServerOutfitContractTest {
    private val repo = ServerOutfitVtonRepository
    private val id = "00000000-0000-4000-8000-000000000048"
    private fun config() = JSONObject().put("engine", repo.ENGINE).put("ready", true)
        .put("model_id", "synthetic-three-role").put("model_revision", "test-rev")
        .put("model_sha256", "a".repeat(64)).put("policy_version", "test-policy")
        .put("retention_seconds", 300).put("max_image_bytes", 5 * 1024 * 1024)
        .put("max_body_bytes", 29 * 1024 * 1024).put("roles", JSONArray(listOf("tops", "bottoms", "shoes")))
    private fun selected() = DeviceVtonSelection("content://synthetic/person", 7, DeviceGarmentRole.entries.map {
        DeviceGarmentInput(it.name, it, "content://synthetic/$it", "400,600,125,150,60,1.5,15,2") })
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
        finally { bitmap.recycle() }
    }
    private fun ticket() = OutfitServerTicket(id, "b".repeat(64), "test-rev", "https://approved.example",
        "c".repeat(64), "d".repeat(64), 1000, 301000, "a".repeat(64))

    @Test fun deploymentAndHttpsRemainFailClosed() {
        assertFalse(BuildConfig.SERVER_OUTFIT_APPROVED)
        val failure = assertThrows(OutfitServerFailure::class.java) { repo.session() }
        assertEquals("deployment_not_approved", failure.code)
        listOf("http://localhost", "https://user:pass@example.com", "https://example.com?token=x",
            "https://example.com/path", "https://example.com#x").forEach { assertFalse(repo.validBase(it)) }
        assertTrue(repo.validBase("https://approved.example"))
    }

    @Test fun readyConfigRequiresEveryRoleHashAndExplicitPolicy() {
        assertTrue(repo.parseConfig(config()).ready)
        listOf(config().put("model_sha256", "bad"), config().put("policy_version", ""),
            config().put("retention_seconds", 0), config().put("roles", JSONArray(listOf("tops", "bottoms"))),
            config().put("engine", "fashn-vton-1.5-local")).forEach {
            assertThrows(OutfitServerFailure::class.java) { repo.parseConfig(it) }
        }
        assertFalse(repo.parseConfig(JSONObject().put("engine", repo.ENGINE).put("ready", false).put("reason", "disabled")).ready)
    }

    @Test fun missingConfigApiIsDistinctFromAnUnknownSubmittedJob() {
        assertEquals("server_api_unavailable", repo.httpFailureCode("/config", 404))
        assertEquals("job_unknown", repo.httpFailureCode("/jobs/$id", 404))
        assertEquals("authentication_required", repo.httpFailureCode("/config", 401))
        assertEquals("usage_limit", repo.httpFailureCode("/jobs", 429))
        assertEquals("engine_unavailable", repo.httpFailureCode("/jobs", 503))
        assertNotEquals(repo.friendly("job_unknown"), repo.friendly("server_api_unavailable"))
    }

    @Test fun photoFitPlacementUsesIndependentLetterboxExpectation() {
        val p = repo.placement("400,600,125,150,60,1.5,15,2", 1000, 2000)
        assertEquals(.25, p.getDouble("center_x"), .000001)
        assertEquals(.25, p.getDouble("center_y"), .000001)
        assertEquals(.20, p.getDouble("width"), .000001)
        assertEquals(1.5, p.getDouble("scale"), 0.0)
        assertEquals(15.0, p.getDouble("rotation"), 0.0)
        // Landscape photo: top/bottom letterbox, not full-canvas y normalization.
        val landscape = repo.placement("400,600,100,250,80,1,0,1", 2000, 1000)
        assertEquals(.25, landscape.getDouble("center_x"), .000001)
        assertEquals(.25, landscape.getDouble("center_y"), .000001)
        assertEquals(.20, landscape.getDouble("width"), .000001)
        assertEquals(.05, repo.placement("400,600,125,150,60,0.05,0,2", 1000, 2000)
            .getDouble("scale"), 0.0)
        assertThrows(OutfitServerFailure::class.java) {
            repo.placement("400,600,125,150,60,0.049,0,2", 1000, 2000)
        }
        listOf("0,600,1,2,60,1,0,1", "400,600,NaN,2,60,1,0,1", "not-placement").forEach {
            assertThrows(OutfitServerFailure::class.java) { repo.placement(it, 1000, 2000) }
        }
    }

    @Test fun exactBodyBindsAllFourFrozenImagesRolesAndConsent() {
        val cfg = repo.parseConfig(config()); val input = DeviceImage(image(), 1000, 2000)
        val job = DevicePreparedJob(id, selected(), DeviceModelSpec(cfg.modelId, cfg.revision, cfg.modelSha,
            DeviceGarmentRole.entries.toSet()), input, DeviceGarmentRole.entries.associateWith { input }, "e".repeat(64))
        val first = repo.requestBody(job, cfg)
        val json = JSONObject(String(first))
        assertEquals(id, json.getString("request_id"))
        assertEquals("test-policy", json.getJSONObject("consent").getString("policy_version"))
        assertTrue(json.getJSONObject("consent").getBoolean("accepted"))
        assertEquals(listOf("tops", "bottoms", "shoes"), (0..2).map { json.getJSONArray("garments").getJSONObject(it).getString("category") })
        assertArrayEquals(first, repo.requestBody(job, cfg))
        assertNotEquals(repo.selectionSha(selected()), repo.selectionSha(selected().copy(revision = 8)))
    }

    @Test fun responseAndBinaryMustMatchExactJobBodyModelAndHash() {
        val ticket = ticket(); val bytes = image(); val hash = deviceSha256(bytes)
        fun status() = JSONObject().put("engine", repo.ENGINE).put("request_id", id)
            .put("input_sha256", ticket.inputSha).put("model_revision", ticket.revision).put("model_sha256", ticket.modelSha)
            .put("status", "completed").put("progress", 100).put("result_available", true)
            .put("result_sha256", hash).put("expires_at", 301)
        val result = repo.parseStatus(status(), ticket)
        assertEquals(1.0, result.progress, 0.0)
        assertEquals(.1, repo.parseStatus(status().put("status", "running").put("result_available", false)
            .put("progress", 10), ticket).progress, 0.0)
        val headers = mapOf("X-Request-Id" to id, "X-Input-SHA256" to ticket.inputSha,
            "X-Model-Revision" to ticket.revision, "X-Result-SHA256" to hash)
        repo.validateDownload(ticket, result, headers, bytes)
        listOf(status().put("request_id", "another"), status().put("input_sha256", "f".repeat(64)),
            status().put("model_revision", "another"), status().put("model_sha256", "f".repeat(64)),
            status().put("progress", 101), status().put("status", "running")).forEach {
            assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(it, ticket) }
        }
        assertThrows(OutfitServerFailure::class.java) { repo.validateDownload(ticket, result, headers,
            bytes.copyOf().apply { this[lastIndex] = 0 }) }
    }

    @Test fun ticketPersistsMetadataOnlyAndUnreadableRecordIsPreserved() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val path = File(app.noBackupFilesDir, "outfit-vton-ticket-v1.json")
        assertFalse(path.exists())
        try {
            val store = PrivateOutfitTicketStore(app)
            store.write(ticket()); assertEquals(ticket(), PrivateOutfitTicketStore(app).read())
            val raw = path.readText()
            assertFalse(raw.contains("content://")); assertFalse(raw.contains("data:image")); assertFalse(raw.contains("Bearer"))
            path.writeText("synthetic-corruption")
            assertThrows(Exception::class.java) { store.read() }
            assertEquals("synthetic-corruption", path.readText())
        } finally { path.delete() } // Owned synthetic test file only.
    }
}
