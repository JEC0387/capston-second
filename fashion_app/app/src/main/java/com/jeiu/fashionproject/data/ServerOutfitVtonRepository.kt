package com.jeiu.fashionproject.data

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Separate, opt-in server deployment. Never an implementation of DeviceVtonEngine. */
class OutfitServerFailure(val code: String) : IllegalStateException(code)

class OutfitServerSession(val base: String, internal val token: String) {
    val accountKey = deviceSha256((base + "\u0000" + token).toByteArray())
    override fun toString() = "OutfitServerSession(redacted)"
}

data class OutfitServerConfig(val ready: Boolean, val reason: String, val modelId: String,
    val revision: String, val modelSha: String, val policy: String, val retentionSeconds: Long,
    val maxImageBytes: Int, val maxBodyBytes: Int, val providerInfo: OutfitServerProviderInfo? = null,
    val maxInputSide: Int = 2048) {
    /** Server normalization is separate from the on-device model's 1024px default. */
    fun imageSpec() = DeviceModelSpec(modelId, revision, modelSha,
        DeviceGarmentRole.entries.toSet(), maxSide = maxInputSide)
}

/** Explicit third-party processing disclosure; never a statement that visual quality passed. */
data class OutfitServerProviderInfo(val name: String, val executionLocation: String, val upstreamModel: String,
    val mode: String, val maxCreditsPerJob: Int, val remoteCancellationSupported: Boolean,
    val remoteRetentionDays: Int, val notice: String)

data class OutfitServerSource(val provider: String, val service: String, val upstreamModel: String,
    val generationIds: List<String>, val mode: String, val creditsReserved: Int,
    val creditsBalanceBefore: Int?, val creditsBalanceAfter: Int?)

/** Provider-owned stage observations; not an estimate of inference percentage or quality. */
data class OutfitServerProgress(val mode: String, val stageIndex: Int, val stageCount: Int, val phase: String)

data class OutfitServerTicket(val id: String, val inputSha: String, val revision: String,
    val base: String, val accountKey: String, val selectionSha: String,
    val createdAt: Long, val expiresAt: Long, val modelSha: String, val providerName: String = "") {
    override fun toString() = "OutfitServerTicket(id=$id, images=not_persisted)"
}

data class OutfitServerStatus(val status: String, val error: String?, val progress: Double,
    val resultAvailable: Boolean, val resultSha: String?, val expiresAt: Long, val source: OutfitServerSource? = null,
    val providerProgress: OutfitServerProgress? = null)

/** Transport seam is injected only by tests. Production uses DataAuth's current bound account. */
interface OutfitServerGateway {
    fun session(): OutfitServerSession
    suspend fun config(session: OutfitServerSession): OutfitServerConfig
    suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus
    suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus
    suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket): OutfitServerStatus
    suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray
}

object ServerOutfitVtonRepository : OutfitServerGateway {
    const val ENGINE = "fashionai-outfit-vton-v1"
    const val PREFIX = "/api/outfit-vton"
    val terminal = setOf("completed", "failed", "cancelled", "timeout", "interrupted", "expired")
    private val statuses = terminal + setOf("queued", "running", "cancelling")
    private val sha = Regex("[a-f0-9]{64}")
    private val providerPhases = listOf("preparing", "uploading", "submitting", "waiting_for_generation",
        "validating_result", "finalizing")

    fun validBase(base: String): Boolean = runCatching {
        val uri = URI(base)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/") &&
            uri.port in -1..65535 && uri.port != 0
    }.getOrDefault(false)

    override fun session(): OutfitServerSession {
        if (ServerOutfitQaConnection.supported)
            return ServerOutfitQaConnection.sessionOrNull() ?: throw OutfitServerFailure("deployment_not_approved")
        if (!BuildConfig.SERVER_OUTFIT_APPROVED || BuildConfig.PREVIEW_BUILD)
            throw OutfitServerFailure("deployment_not_approved")
        val base = BackendApi.BASE_URL.trimEnd('/')
        if (!validBase(base) || !DataAuth.canSync) throw OutfitServerFailure("authentication_required")
        val token = DataAuth.authorizationFor(URL(base + PREFIX + "/config"))
            ?: throw OutfitServerFailure("authentication_required")
        if (base != BackendApi.BASE_URL.trimEnd('/')) throw OutfitServerFailure("account_changed")
        return OutfitServerSession(base, token)
    }

    private fun checkSession(captured: OutfitServerSession) {
        val current = session()
        if (captured.base != current.base || captured.accountKey != current.accountKey)
            throw OutfitServerFailure("account_changed")
    }

    fun parseConfig(json: JSONObject): OutfitServerConfig {
        if (json.optString("engine") != ENGINE) throw OutfitServerFailure("wrong_engine")
        val ready = json.optBoolean("ready", false)
        val value = OutfitServerConfig(ready, safeCode(json.optString("reason")),
            json.optString("model_id"), json.optString("model_revision"), json.optString("model_sha256"),
            json.optString("policy_version"), json.optLong("retention_seconds", 0),
            json.optInt("max_image_bytes", 0), json.optInt("max_body_bytes", 0),
            parseProviderInfo(json.opt("provider_info"), json.optString("model_id"), json.optString("model_revision")),
            if (json.has("max_input_side")) boundedInteger(json.opt("max_input_side"), 2048) ?: 0 else 2048)
        if (ready && (value.modelId.isBlank() || value.modelId.length > 128 || value.revision.isBlank() ||
                value.revision.length > 128 || !sha.matches(value.modelSha) ||
                value.policy.isBlank() || value.policy.length > 128 || value.retentionSeconds !in 1..86400 ||
                value.maxImageBytes !in 1..5 * 1024 * 1024 || value.maxBodyBytes !in 1..29 * 1024 * 1024 ||
                value.maxInputSide !in 256..2048 ||
                json.optJSONArray("roles")?.takeIf { it.length() == 3 }?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                    != setOf("tops", "bottoms", "shoes"))) throw OutfitServerFailure("invalid_config")
        return value
    }

    private fun boundedInteger(value: Any?, max: Int = Int.MAX_VALUE): Int? {
        if (value !is Int && value !is Long) return null
        return (value as Number).toLong().takeIf { it in 0..max.toLong() }?.toInt()
    }

    // Keep execution mode in the already durable revision, including restored P50 tickets.
    // A refreshed server config must not reinterpret an older job's generation receipt.
    private fun genlookMode(revision: String): String? = when {
        Regex("genlook-paired-v1-20261106-[a-f0-9]{16}").matches(revision) -> "clothing-pair-then-shoes"
        listOf("genlook-sequential-v1-", "genlook-sequential-v2-preservation-").any {
            revision.startsWith(it) && revision.length > it.length
        } -> "sequential-3"
        else -> null
    }

    private fun parseProviderInfo(raw: Any?, modelId: String, revision: String): OutfitServerProviderInfo? {
        if (raw == null || raw === JSONObject.NULL) {
            if (modelId.startsWith("genlook/")) throw OutfitServerFailure("invalid_provider_disclosure")
            return null
        }
        val info = raw as? JSONObject ?: throw OutfitServerFailure("invalid_provider_disclosure")
        val notice = info.opt("notice") as? String ?: throw OutfitServerFailure("invalid_provider_disclosure")
        val mode = genlookMode(revision) ?: throw OutfitServerFailure("invalid_provider_disclosure")
        if (modelId != "genlook/tryon-api-v1" || info.opt("name") != "genlook" ||
            info.opt("execution_location") != "third_party" || info.opt("upstream_model") != "not_disclosed" ||
            info.opt("mode") != mode || boundedInteger(info.opt("max_credits_per_job"), 3) != 3 ||
            info.opt("remote_cancellation_supported") != false || boundedInteger(info.opt("remote_retention_days"), 1) != 1 ||
            notice.isBlank() || notice.length > 1000 || notice.any { it.code < 32 })
            throw OutfitServerFailure("invalid_provider_disclosure")
        return OutfitServerProviderInfo("genlook", "third_party", "not_disclosed", mode, 3, false, 1, notice)
    }

    private fun parseSource(raw: Any?): OutfitServerSource? {
        if (raw == null || raw === JSONObject.NULL) return null
        val source = raw as? JSONObject ?: throw OutfitServerFailure("invalid_result_source")
        val mode = source.opt("mode") as? String ?: throw OutfitServerFailure("invalid_result_source")
        val expectedCount = when (mode) {
            "sequential-3" -> 3
            "clothing-pair-then-shoes" -> 2
            else -> throw OutfitServerFailure("invalid_result_source")
        }
        val ids = source.optJSONArray("generation_ids") ?: throw OutfitServerFailure("invalid_result_source")
        val values = (0 until ids.length()).map { ids.opt(it) as? String ?: throw OutfitServerFailure("invalid_result_source") }
        fun balance(key: String): Int? = if (!source.has(key) || source.isNull(key)) null else
            boundedInteger(source.opt(key)) ?: throw OutfitServerFailure("invalid_result_source")
        if (source.opt("provider") != "genlook" || source.opt("service") != "tryon/v1" ||
            source.opt("upstream_model") != "not_disclosed" ||
            source.opt("quality") != "NOT_EVALUATED" || boundedInteger(source.opt("credits_reserved"), 3) != 3 ||
            values.size != expectedCount || values.toSet().size != expectedCount || values.any { !Regex("[A-Za-z0-9_-]{1,128}").matches(it) })
            throw OutfitServerFailure("invalid_result_source")
        return OutfitServerSource("genlook", "tryon/v1", "not_disclosed", values.toList(), mode, 3,
            balance("credits_balance_before"), balance("credits_balance_after"))
    }

    internal fun isGenlook(ticket: OutfitServerTicket): Boolean = ticket.providerName == "genlook" ||
        ticket.revision.startsWith("genlook-")

    internal fun requireResultSource(ticket: OutfitServerTicket, status: OutfitServerStatus) {
        if (status.resultAvailable && isGenlook(ticket) && status.source == null ||
            !isGenlook(ticket) && status.source != null ||
            status.source != null && status.source.mode != genlookMode(ticket.revision))
            throw OutfitServerFailure("invalid_result_source")
    }

    private fun parseProviderProgress(raw: Any?, state: String, ticket: OutfitServerTicket): OutfitServerProgress? {
        if (raw == null || raw === JSONObject.NULL) return null
        val value = raw as? JSONObject ?: throw OutfitServerFailure("invalid_provider_progress")
        val mode = genlookMode(ticket.revision) ?: throw OutfitServerFailure("invalid_provider_progress")
        val count = if (mode == "clothing-pair-then-shoes") 2 else 3
        val index = boundedInteger(value.opt("stage_index"), count)
        val phase = value.opt("phase") as? String ?: throw OutfitServerFailure("invalid_provider_progress")
        if (state != "running" || !isGenlook(ticket) ||
            value.keys().asSequence().toSet() != setOf("mode", "stage_index", "stage_count", "phase") ||
            value.opt("mode") != mode || boundedInteger(value.opt("stage_count"), count) != count ||
            index == null || index < 1 || phase !in providerPhases || phase == "finalizing" && index != count)
            throw OutfitServerFailure("invalid_provider_progress")
        return OutfitServerProgress(mode, index, count, phase)
    }

    internal fun requireProgressTransition(previous: OutfitServerProgress?, current: OutfitServerProgress) {
        if (previous != null && (previous.mode != current.mode || previous.stageCount != current.stageCount ||
                current.stageIndex < previous.stageIndex || current.stageIndex == previous.stageIndex &&
                providerPhases.indexOf(current.phase) < providerPhases.indexOf(previous.phase)))
            throw OutfitServerFailure("invalid_provider_progress")
    }

    override suspend fun config(session: OutfitServerSession) = parseConfig(JSONObject(
        request(session, "/config").body.toString(Charsets.UTF_8)))

    fun selectionSha(selection: DeviceVtonSelection): String = deviceSha256(JSONObject()
        .put("person", selection.person).put("revision", selection.revision)
        .put("garments", JSONArray().apply { selection.garments.sortedBy { it.role.ordinal }.forEach {
            put(JSONObject().put("id", it.id).put("role", it.role.name)
                .put("source", it.source).put("placement", it.placementKey))
        } }).toString().toByteArray())

    fun placement(key: String, photoWidth: Int, photoHeight: Int): JSONObject {
        val v = key.split(',').map { it.toDoubleOrNull() ?: throw OutfitServerFailure("invalid_placement") }
        if (v.size != 8 || v.any { !it.isFinite() } || v[0] <= 0 || v[1] <= 0 || v[7] <= 0 ||
            photoWidth <= 0 || photoHeight <= 0)
            throw OutfitServerFailure("invalid_placement")
        // Match ContentScale.Fit: canvas letterboxing is not part of the photo.
        val fit = minOf(v[0] / photoWidth, v[1] / photoHeight)
        val shownWidth = photoWidth * fit; val shownHeight = photoHeight * fit
        val left = (v[0] - shownWidth) / 2; val top = (v[1] - shownHeight) / 2
        val x = (v[2] - left) / shownWidth; val y = (v[3] - top) / shownHeight
        val width = v[4] / shownWidth
        if (x !in -2.0..3.0 || y !in -2.0..3.0 || width <= 0 || width > 4 ||
            v[5] < .05 || v[5] > 20 || v[6] !in -36000.0..36000.0)
            throw OutfitServerFailure("invalid_placement")
        return JSONObject().put("center_x", x).put("center_y", y).put("width", width)
            .put("scale", v[5]).put("rotation", v[6])
    }

    fun requestBody(job: DevicePreparedJob, config: OutfitServerConfig): ByteArray {
        if (!config.ready || job.model.revision != config.revision || job.model.sha256 != config.modelSha ||
            job.model.maxSide != config.maxInputSide ||
            job.garments.keys != DeviceGarmentRole.entries.toSet()) throw OutfitServerFailure("invalid_config")
        requireUuid(job.id)
        fun image(value: DeviceImage): String {
            val bytes = value.bytes
            if (bytes.size > config.maxImageBytes) throw OutfitServerFailure("image_size")
            return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        val garments = JSONArray()
        DeviceGarmentRole.entries.forEach { role ->
            val selected = job.selection.garments.single { it.role == role }
            garments.put(JSONObject().put("id", selected.id).put("category", when (role) {
                DeviceGarmentRole.TOP -> "tops"; DeviceGarmentRole.BOTTOM -> "bottoms"; DeviceGarmentRole.SHOES -> "shoes"
            }).put("image", image(job.garments.getValue(role)))
                .put("placement", placement(selected.placementKey, job.person.width, job.person.height)))
        }
        return JSONObject().put("request_id", job.id).put("model_revision", config.revision)
            .put("consent", JSONObject().put("accepted", true).put("policy_version", config.policy))
            .put("person", image(job.person)).put("garments", garments).toString().toByteArray(Charsets.UTF_8)
            .also { if (it.size > config.maxBodyBytes) throw OutfitServerFailure("body_size") }
    }

    fun parseStatus(json: JSONObject, ticket: OutfitServerTicket): OutfitServerStatus {
        if (json.optString("engine") != ENGINE || json.optString("request_id") != ticket.id ||
            json.optString("input_sha256") != ticket.inputSha || json.optString("model_revision") != ticket.revision ||
            json.optString("model_sha256") != ticket.modelSha)
            throw OutfitServerFailure("result_binding_mismatch")
        val state = json.optString("status")
        val progress = json.optDouble("progress", 0.0)
        val available = json.optBoolean("result_available", false)
        val resultSha = json.optString("result_sha256").takeIf { sha.matches(it) }
        val expiry = json.optDouble("expires_at", 0.0)
        if (state !in statuses || !progress.isFinite() || progress !in 0.0..100.0 ||
            !expiry.isFinite() || expiry <= 0 || available && (state != "completed" || resultSha == null))
            throw OutfitServerFailure("invalid_status")
        return OutfitServerStatus(state, json.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let(::safeCode),
            progress / 100.0, available, resultSha, (expiry * 1000).toLong(), parseSource(json.opt("source")),
            parseProviderProgress(json.opt("provider_progress"), state, ticket))
            .also { requireResultSource(ticket, it) }
    }

    override suspend fun submit(session: OutfitServerSession, ticket: OutfitServerTicket, body: ByteArray): OutfitServerStatus {
        if (deviceSha256(body) != ticket.inputSha) throw OutfitServerFailure("input_changed")
        return parseStatus(JSONObject(request(session, "/jobs", "POST", body, ticket.id).body.toString(Charsets.UTF_8)), ticket)
    }
    override suspend fun status(session: OutfitServerSession, ticket: OutfitServerTicket) =
        parseStatus(JSONObject(request(session, "/jobs/${requireUuid(ticket.id)}").body.toString(Charsets.UTF_8)), ticket)
    override suspend fun cancel(session: OutfitServerSession, ticket: OutfitServerTicket) =
        parseStatus(JSONObject(request(session, "/jobs/${requireUuid(ticket.id)}", "DELETE").body.toString(Charsets.UTF_8)), ticket)

    fun validateDownload(ticket: OutfitServerTicket, status: OutfitServerStatus, headers: Map<String, String>, bytes: ByteArray) {
        requireResultSource(ticket, status)
        if (status.status != "completed" || !status.resultAvailable ||
            headers["X-Request-Id"] != ticket.id || headers["X-Input-SHA256"] != ticket.inputSha ||
            headers["X-Model-Revision"] != ticket.revision || headers["X-Result-SHA256"] != status.resultSha ||
            deviceSha256(bytes) != status.resultSha) throw OutfitServerFailure("result_binding_mismatch")
        OnDeviceVtonImages.validateResultPng(bytes)
    }

    override suspend fun result(session: OutfitServerSession, ticket: OutfitServerTicket, status: OutfitServerStatus): ByteArray {
        val reply = request(session, "/jobs/${requireUuid(ticket.id)}/result", binary = true)
        validateDownload(ticket, status, reply.headers, reply.body)
        return reply.body
    }

    private data class Reply(val body: ByteArray, val headers: Map<String, String>)
    private suspend fun request(session: OutfitServerSession, path: String, method: String = "GET",
        body: ByteArray? = null, id: String? = null, binary: Boolean = false): Reply = withContext(Dispatchers.IO) {
        checkSession(session)
        val conn = (URL(session.base + PREFIX + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; instanceFollowRedirects = false; connectTimeout = 10000; readTimeout = 20000
            setRequestProperty("Authorization", "Bearer ${session.token}")
            setRequestProperty("Accept", if (binary) "image/png" else "application/json")
            if (body != null) {
                doOutput = true; setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Idempotency-Key", id); setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            checkSession(session); coroutineContext.ensureActive()
            if (body != null) conn.outputStream.use { stream ->
                var offset = 0
                while (offset < body.size) {
                    checkSession(session); coroutineContext.ensureActive()
                    val count = minOf(16384, body.size - offset); stream.write(body, offset, count); offset += count
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw OutfitServerFailure(httpFailureCode(path, code))
            // Never log/retain provider error bodies, redirects or signed URLs.
            val limit = if (binary) 8 * 1024 * 1024 else 32768
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive(); checkSession(session)
                    val count = input.read(buffer); if (count < 0) break
                    if (count == 0 || out.size() + count > limit) throw OutfitServerFailure("invalid_response")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            checkSession(session)
            Reply(bytes, listOf("X-Request-Id", "X-Input-SHA256", "X-Model-Revision", "X-Result-SHA256")
                .associateWith { conn.getHeaderField(it).orEmpty() })
        } finally { conn.disconnect() }
    }

    internal fun httpFailureCode(path: String, code: Int): String = when (code) {
        401 -> "authentication_required"
        403 -> "consent_or_transport_required"
        404 -> if (path == "/config") "server_api_unavailable" else "job_unknown"
        409 -> "request_conflict"
        410 -> "result_expired"
        413, 422 -> "input_rejected"
        429 -> "usage_limit"
        503 -> "engine_unavailable"
        else -> "server_error"
    }

    fun requireUuid(id: String): String = id.also {
        if (runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false).not())
            throw OutfitServerFailure("invalid_request_id")
    }
    private fun safeCode(value: String) = value.takeIf { Regex("[a-z][a-z0-9_]{0,63}").matches(it) } ?: "server_unavailable"
    fun friendly(code: String?) = when (code) {
        "deployment_not_approved" -> "서버 기반 3종 AI는 별도 배포 승인이 필요해요. 현재 앱에서는 사진을 전송하지 않아요."
        "authentication_required", "account_changed" -> "현재 서버 계정 인증이 필요하거나 계정이 바뀌었어요. 기존 기록은 유지해요."
        "server_api_unavailable" -> "연결한 서버에 3종 AI API가 배포되지 않았거나 연결 경로가 달라요. 서버 배포와 연결 주소를 확인해주세요. 사진은 전송하지 않았어요."
        "job_unknown" -> "서버에서 작업을 확인하지 못했어요. 응답 유실 여부가 불명확해 새 요청을 자동 전송하지 않아요."
        "result_expired", "expired" -> "서버 보관 기간이 지나 결과를 받을 수 없어요. 직접 저장한 파일은 그대로예요."
        "usage_limit" -> "서버의 사용량 한도에 도달했어요. 자동 재시도하지 않아요."
        "request_conflict", "result_binding_mismatch", "input_changed" -> "요청과 결과 정보가 일치하지 않아 차단했어요."
        "invalid_placement", "input_rejected", "image_size", "body_size" -> "사진·의류 3종·배치 정보와 이미지 크기를 확인해주세요."
        "invalid_provider_disclosure", "invalid_result_source" -> "외부 처리 조건이나 결과 출처가 확인되지 않아 차단했어요."
        "invalid_provider_progress" -> "서버 진행 정보가 작업과 맞지 않거나 이전 단계로 되돌아가 표시를 중단했어요. 기존 작업 번호는 유지하며 새 요청을 자동 전송하지 않아요."
        "genlook_disabled", "genlook_approval_required", "genlook_policy_unreviewed" -> "Genlook의 비용·외부 사진 처리·보관 정책 승인이 필요해요. 승인 전에는 전송하지 않아요."
        "genlook_key_missing", "genlook_auth_failed" -> "서버의 Genlook 인증 설정을 확인해야 해요. 키를 앱이나 채팅에 입력하지 마세요."
        "genlook_budget_exhausted", "genlook_insufficient_credits" -> "승인된 시험 예산 또는 Genlook 크레딧이 부족해요. 자동 충전하거나 생성 요청을 반복하지 않아요."
        "genlook_rate_limited" -> "Genlook 호출 제한에 도달했어요. 생성 요청을 자동 반복하지 않아요."
        "genlook_reconciliation_required", "remote_execution_unconfirmed" -> "Genlook 작업 종료·과금 여부를 확인하지 못했어요. 후속 생성을 차단했으며 외부 작업 취소·환불·삭제 완료를 뜻하지 않아요."
        "genlook_journal_unavailable" -> "외부 생성 기록을 안전하게 저장하지 못해 후속 요청을 차단했어요. 기존 기록은 유지해요."
        "genlook_result_untrusted", "genlook_invalid_response" -> "Genlook 결과의 출처·형식·작업 연결을 확인하지 못해 표시하지 않아요."
        "genlook_remote_failed", "genlook_unavailable" -> "Genlook 생성을 완료하지 못했어요. 이전 이미지를 새 생성 결과로 표시하거나 자동 재생성하지 않아요."
        else -> "승인된 생성 서버가 준비되지 않았거나 요청을 완료하지 못했어요. 이전 사진을 새 AI 결과로 대신하지 않아요."
    }
}

/** Only opaque job/account hashes and policy expiry persist; no photos, URI or credentials. */
interface OutfitTicketStore {
    fun read(): OutfitServerTicket?
    fun write(ticket: OutfitServerTicket)
    /** Preserve the exact journal; only the explicit, reviewed QA recovery action may call this. */
    fun archiveReviewed(ticket: OutfitServerTicket) { throw OutfitServerFailure("journal_unavailable") }
}

class PrivateOutfitTicketStore(context: Context) : OutfitTicketStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "outfit-vton-ticket-v1.json"))
    override fun read(): OutfitServerTicket? {
        if (!file.baseFile.exists()) return null
        if (file.baseFile.length() > 8192) throw OutfitServerFailure("journal_unreadable")
        val raw = file.readFully()
        if (raw.size > 8192) throw OutfitServerFailure("journal_unreadable")
        val j = JSONObject(String(raw, Charsets.UTF_8))
        val ticket = OutfitServerTicket(j.getString("id"), j.getString("inputSha"), j.getString("revision"),
            j.getString("base"), j.getString("accountKey"), j.getString("selectionSha"), j.getLong("createdAt"),
            j.getLong("expiresAt"), j.getString("modelSha"), j.optString("providerName", ""))
        ServerOutfitVtonRepository.requireUuid(ticket.id)
        if (!ServerOutfitVtonRepository.validBase(ticket.base) || ticket.revision.length !in 1..128 ||
            listOf(ticket.inputSha, ticket.accountKey, ticket.selectionSha, ticket.modelSha).any { !Regex("[a-f0-9]{64}").matches(it) } ||
            ticket.providerName !in setOf("", "genlook") ||
            ticket.createdAt <= 0 || ticket.expiresAt <= ticket.createdAt) throw OutfitServerFailure("journal_unreadable")
        return ticket
    }
    override fun write(ticket: OutfitServerTicket) {
        val bytes = JSONObject().put("id", ticket.id).put("inputSha", ticket.inputSha).put("revision", ticket.revision)
            .put("base", ticket.base).put("accountKey", ticket.accountKey).put("selectionSha", ticket.selectionSha)
            .put("createdAt", ticket.createdAt).put("expiresAt", ticket.expiresAt).put("modelSha", ticket.modelSha)
            .put("providerName", ticket.providerName).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw OutfitServerFailure("journal_unavailable") }
    }

    override fun archiveReviewed(ticket: OutfitServerTicket) {
        // readFully resolves AtomicFile recovery before moving; never recreate a corrupt journal.
        if (read() != ticket) throw OutfitServerFailure("journal_unavailable")
        val bytes = file.readFully()
        val history = File(file.baseFile.parentFile, "outfit-vton-reviewed-history")
        if (!history.isDirectory && !history.mkdir()) throw OutfitServerFailure("journal_unavailable")
        val destination = File(history, "${ticket.id}-${deviceSha256(bytes)}.json")
        // Same-filesystem rename preserves all bytes and does not overwrite older evidence.
        if (destination.exists() || !file.baseFile.renameTo(destination))
            throw OutfitServerFailure("journal_unavailable")
    }
}
