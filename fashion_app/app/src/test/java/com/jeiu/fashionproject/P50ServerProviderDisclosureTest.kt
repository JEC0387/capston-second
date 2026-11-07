package com.jeiu.fashionproject

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Wire/metadata tests only: no Genlook API, credentials, model or device access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P50ServerProviderDisclosureTest {
    private val repo = ServerOutfitVtonRepository
    private fun info() = JSONObject().put("name", "genlook").put("execution_location", "third_party")
        .put("upstream_model", "not_disclosed").put("mode", "sequential-3").put("max_credits_per_job", 3)
        .put("remote_cancellation_supported", false).put("remote_retention_days", 1)
        .put("notice", "Synthetic policy: separate image, record and backup retention; no remote cancel promise.")
    private fun config() = JSONObject().put("engine", repo.ENGINE).put("ready", true)
        .put("model_id", "genlook/tryon-api-v1").put("model_revision", "genlook-sequential-v1-20261106-test")
        .put("model_sha256", "a".repeat(64)).put("policy_version", "synthetic-policy")
        .put("retention_seconds", 300).put("max_image_bytes", 5 * 1024 * 1024)
        .put("max_body_bytes", 29 * 1024 * 1024).put("roles", JSONArray(listOf("tops", "bottoms", "shoes")))
        .put("provider_info", info())
    private fun ticket() = OutfitServerTicket("00000000-0000-4000-8000-000000000050", "b".repeat(64),
        "genlook-sequential-v1-20261106-test", "https://approved.example", "c".repeat(64), "d".repeat(64),
        1000, 301000, "a".repeat(64), "genlook")
    private fun source() = JSONObject().put("provider", "genlook").put("service", "tryon/v1")
        .put("upstream_model", "not_disclosed").put("mode", "sequential-3")
        .put("generation_ids", JSONArray(listOf("synthetic-top", "synthetic-bottom", "synthetic-shoes")))
        .put("credits_reserved", 3).put("credits_balance_before", 10).put("credits_balance_after", 7)
        .put("quality", "NOT_EVALUATED")
    private fun completed() = JSONObject().put("engine", repo.ENGINE).put("request_id", ticket().id)
        .put("input_sha256", ticket().inputSha).put("model_revision", ticket().revision)
        .put("model_sha256", ticket().modelSha).put("status", "completed").put("progress", 100)
        .put("result_available", true).put("result_sha256", "e".repeat(64)).put("expires_at", 301)
        .put("source", source())

    @Test fun thirdPartyConfigRequiresExactDisclosureAndDoesNotEnablePreview() {
        val value = repo.parseConfig(config())
        assertEquals("genlook", value.providerInfo?.name)
        assertEquals(3, value.providerInfo?.maxCreditsPerJob)
        assertEquals(false, value.providerInfo?.remoteCancellationSupported)
        val legacy = config().put("model_id", "synthetic-local").apply { remove("provider_info") }
        assertNull(repo.parseConfig(legacy).providerInfo)
        assertFalse(BuildConfig.SERVER_OUTFIT_APPROVED)
        assertEquals("deployment_not_approved", assertThrows(OutfitServerFailure::class.java) { repo.session() }.code)
    }

    @Test fun missingAndMisleadingProviderDisclosureFailsClosed() {
        val missing = config().apply { remove("provider_info") }
        val invalid = listOf(missing, config().put("provider_info", "genlook"),
            config().put("provider_info", info().put("name", "local")),
            config().put("provider_info", info().put("execution_location", "ondevice")),
            config().put("provider_info", info().put("upstream_model", "invented-version")),
            config().put("provider_info", info().put("max_credits_per_job", "3")),
            config().put("provider_info", info().put("max_credits_per_job", 4)),
            config().put("provider_info", info().put("remote_cancellation_supported", true)),
            config().put("provider_info", info().put("remote_retention_days", 0)),
            config().put("provider_info", info().put("notice", "")),
            config().put("provider_info", info().put("notice", "x".repeat(1001))))
        invalid.forEach { assertEquals("invalid_provider_disclosure",
            assertThrows(OutfitServerFailure::class.java) { repo.parseConfig(it) }.code) }
    }

    @Test fun completedGenlookNeedsBoundDistinctThreeGenerationReceipt() {
        val result = repo.parseStatus(completed(), ticket())
        assertEquals(listOf("synthetic-top", "synthetic-bottom", "synthetic-shoes"), result.source?.generationIds)
        assertEquals(3, result.source?.creditsReserved)
        assertEquals(10, result.source?.creditsBalanceBefore)
        assertEquals(7, result.source?.creditsBalanceAfter)
        val missing = completed().apply { remove("source") }
        val invalid = listOf(missing, completed().put("source", source().put("provider", "local")),
            completed().put("source", source().put("quality", "PASS")),
            completed().put("source", source().put("generation_ids", JSONArray(listOf("same", "same", "same")))),
            completed().put("source", source().put("generation_ids", JSONArray(listOf("one", "two")))),
            completed().put("source", source().put("generation_ids", JSONArray(listOf("https://example.test/a", "b", "c")))),
            completed().put("source", source().put("credits_reserved", 1)),
            completed().put("source", source().put("credits_balance_after", -1)))
        invalid.forEach { assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(it, ticket()) } }
        // The source receipt does not weaken the original request/manifest identity binding.
        assertEquals("result_binding_mismatch", assertThrows(OutfitServerFailure::class.java) {
            repo.parseStatus(completed().put("input_sha256", "f".repeat(64)), ticket())
        }.code)
    }

    @Test fun sourceAbsentOnPendingOrLegacyResultRemainsCompatible() {
        val pending = completed().put("status", "running").put("progress", 10).put("result_available", false)
            .apply { remove("source"); remove("result_sha256") }
        assertNull(repo.parseStatus(pending, ticket()).source)
        val legacyTicket = ticket().copy(revision = "local-revision", providerName = "")
        val legacy = completed().put("model_revision", "local-revision").apply { remove("source") }
        assertNull(repo.parseStatus(legacy, legacyTicket).source)
        assertThrows(OutfitServerFailure::class.java) {
            repo.parseStatus(completed().put("model_revision", "local-revision"), legacyTicket)
        }
        // Pre-P50 tickets without providerName still recognize a Genlook contract revision.
        assertThrows(OutfitServerFailure::class.java) {
            repo.parseStatus(completed().apply { remove("source") }, ticket().copy(providerName = ""))
        }
    }

    @Test fun durableProviderTagSurvivesReopenAndLegacyTicketStillReads() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val folder = File(app.cacheDir, "p50-ticket-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = folder }
        val file = File(folder, "outfit-vton-ticket-v1.json")
        try {
            PrivateOutfitTicketStore(context).write(ticket())
            assertEquals(ticket(), PrivateOutfitTicketStore(context).read())
            val raw = JSONObject(file.readText()).apply { remove("providerName") }
            file.writeText(raw.toString())
            assertEquals("", PrivateOutfitTicketStore(context).read()?.providerName)
            assertTrue(repo.isGenlook(PrivateOutfitTicketStore(context).read()!!))
        } finally { file.delete(); folder.delete() }
    }

    @Test fun costAndRemoteCancellationErrorsDoNotPromiseRefundOrSuccess() {
        assertTrue(repo.friendly("genlook_insufficient_credits").contains("자동 충전"))
        assertTrue(repo.friendly("remote_execution_unconfirmed").contains("완료를 뜻하지"))
        assertTrue(repo.friendly("genlook_auth_failed").contains("키를 앱이나 채팅"))
        assertFalse(repo.friendly("secret-provider-error-body").contains("secret-provider-error-body"))
    }
}
