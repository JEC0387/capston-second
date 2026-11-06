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

/** Synthetic protocol receipts only; no vendor API, billing, credentials or device access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P51ServerPairedModeTest {
    private val repo = ServerOutfitVtonRepository
    private val paired = "clothing-pair-then-shoes"
    private val pairedRevision = "genlook-paired-v1-20261106-0123456789abcdef"
    private val legacyRevisions = listOf("genlook-sequential-v1-20261106-test",
        "genlook-sequential-v2-preservation-20261106-0123456789abcdef")

    private fun info(mode: String) = JSONObject().put("name", "genlook").put("execution_location", "third_party")
        .put("upstream_model", "not_disclosed").put("mode", mode).put("max_credits_per_job", 3)
        .put("remote_cancellation_supported", false).put("remote_retention_days", 1)
        .put("notice", "Synthetic disclosure: separate external processing, record and backup retention.")
    private fun config(revision: String = pairedRevision, mode: String = paired) = JSONObject()
        .put("engine", repo.ENGINE).put("ready", true).put("model_id", "genlook/tryon-api-v1")
        .put("model_revision", revision).put("model_sha256", "a".repeat(64)).put("policy_version", "synthetic-policy")
        .put("retention_seconds", 300).put("max_image_bytes", 5 * 1024 * 1024).put("max_body_bytes", 29 * 1024 * 1024)
        .put("roles", JSONArray(listOf("tops", "bottoms", "shoes"))).put("provider_info", info(mode))
    private fun ticket(revision: String = pairedRevision, tag: String = "genlook") = OutfitServerTicket(
        "00000000-0000-4000-8000-000000000051", "b".repeat(64), revision, "https://synthetic.example",
        "c".repeat(64), "d".repeat(64), 1000, 301000, "a".repeat(64), tag)
    private fun source(mode: String = paired, ids: List<String> = listOf("synthetic-pair", "synthetic-shoes")) = JSONObject()
        .put("provider", "genlook").put("service", "tryon/v1").put("upstream_model", "not_disclosed")
        .put("mode", mode).put("generation_ids", JSONArray(ids)).put("credits_reserved", 3)
        .put("credits_balance_before", 9).put("credits_balance_after", 6).put("quality", "NOT_EVALUATED")
    private fun status(t: OutfitServerTicket = ticket(), receipt: JSONObject = source()) = JSONObject()
        .put("engine", repo.ENGINE).put("request_id", t.id).put("input_sha256", t.inputSha)
        .put("model_revision", t.revision).put("model_sha256", t.modelSha).put("status", "completed")
        .put("progress", 100).put("result_available", true).put("result_sha256", "e".repeat(64))
        .put("expires_at", 301).put("source", receipt)

    @Test fun pairedConfigAndTwoReceiptIdsKeepThreeCreditCeiling() {
        assertEquals(paired, repo.parseConfig(config()).providerInfo?.mode)
        val result = repo.parseStatus(status(), ticket())
        assertEquals(paired, result.source?.mode)
        assertEquals(listOf("synthetic-pair", "synthetic-shoes"), result.source?.generationIds)
        assertEquals(3, result.source?.creditsReserved)
        assertEquals(9, result.source?.creditsBalanceBefore)
        assertEquals(6, result.source?.creditsBalanceAfter)
        assertFalse(BuildConfig.SERVER_OUTFIT_APPROVED)
        assertEquals("deployment_not_approved", assertThrows(OutfitServerFailure::class.java) { repo.session() }.code)
    }

    @Test fun configRejectsModeRevisionMismatchUnknownRevisionAndWrongBudget() {
        val cases = listOf(config(mode = "sequential-3"), config(revision = legacyRevisions.first()),
            config(revision = "genlook-paired-v1-20261106-short"),
            config(revision = "genlook-unknown-v1-0123456789abcdef"),
            config(mode = "paired-2"), config().put("provider_info", info(paired).put("max_credits_per_job", 2)),
            config().put("provider_info", info(paired).put("max_credits_per_job", 4)),
            config().apply { remove("provider_info") })
        cases.forEach { json ->
            assertEquals("invalid_provider_disclosure", assertThrows(OutfitServerFailure::class.java) { repo.parseConfig(json) }.code)
            // Unready metadata must not conceal a mismatched external processing mode either.
            assertThrows(OutfitServerFailure::class.java) { repo.parseConfig(json.put("ready", false)) }
        }
    }

    @Test fun pairedReceiptRejectsMissingExtraDuplicateAndMisleadingValues() {
        val badSources = listOf(source(ids = listOf("one")), source(ids = listOf("one", "two", "three")),
            source(ids = listOf("same", "same")), source(ids = listOf("https://example.test/id", "two")),
            source().put("mode", "paired-2"), source().put("credits_reserved", 2),
            source().put("credits_balance_after", -1), source().put("quality", "PASS"))
        badSources.forEach { assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(receipt = it), ticket()) } }
        assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status().apply { remove("source") }, ticket()) }
        assertEquals("result_binding_mismatch", assertThrows(OutfitServerFailure::class.java) {
            repo.parseStatus(status().put("input_sha256", "f".repeat(64)), ticket())
        }.code)
    }

    @Test fun capturedRevisionPreventsBothModeDowngradeAndReinterpretation() {
        val sequentialReceipt = source("sequential-3", listOf("top", "bottom", "shoes"))
        assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(receipt = sequentialReceipt), ticket()) }
        legacyRevisions.forEach { revision ->
            val old = ticket(revision)
            assertEquals("sequential-3", repo.parseConfig(config(revision, "sequential-3")).providerInfo?.mode)
            assertEquals(3, repo.parseStatus(status(old, sequentialReceipt), old).source?.generationIds?.size)
            assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(old), old) }
        }
        val unknown = ticket("genlook-unrecognized-revision")
        assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(unknown), unknown) }
    }

    @Test fun oldTicketWithoutProviderTagStillReopensWithItsOriginalMode() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val folder = File(app.cacheDir, "p51-ticket-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = folder }
        val file = File(folder, "outfit-vton-ticket-v1.json")
        val old = ticket(legacyRevisions.first())
        try {
            PrivateOutfitTicketStore(context).write(old)
            file.writeText(JSONObject(file.readText()).apply { remove("providerName") }.toString())
            val reopened = PrivateOutfitTicketStore(context).read()!!
            assertEquals(old.copy(providerName = ""), reopened)
            repo.parseConfig(config()) // New server config cannot change the restored ticket.
            assertEquals("sequential-3", repo.parseStatus(status(reopened,
                source("sequential-3", listOf("top", "bottom", "shoes"))), reopened).source?.mode)
            assertThrows(OutfitServerFailure::class.java) { repo.parseStatus(status(reopened), reopened) }
        } finally { file.delete(); folder.delete() }
    }

    @Test fun pendingAndNonGenlookLegacyJobsNeedNoNewMetadata() {
        val pending = status().put("status", "running").put("progress", 10).put("result_available", false)
            .apply { remove("source"); remove("result_sha256") }
        assertNull(repo.parseStatus(pending, ticket()).source)
        val local = ticket("local-version", "")
        assertNull(repo.parseStatus(status(local).apply { remove("source") }, local).source)
        assertNull(repo.parseConfig(config().put("model_id", "local-model").put("model_revision", "local-version")
            .apply { remove("provider_info") }).providerInfo)
    }
}
