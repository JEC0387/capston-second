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
    val maxImageBytes: Int, val maxBodyBytes: Int)

data class OutfitServerTicket(val id: String, val inputSha: String, val revision: String,
    val base: String, val accountKey: String, val selectionSha: String,
    val createdAt: Long, val expiresAt: Long, val modelSha: String) {
    override fun toString() = "OutfitServerTicket(id=$id, images=not_persisted)"
}

data class OutfitServerStatus(val status: String, val error: String?, val progress: Double,
    val resultAvailable: Boolean, val resultSha: String?, val expiresAt: Long)

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

    fun validBase(base: String): Boolean = runCatching {
        val uri = URI(base)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/") &&
            uri.port in -1..65535 && uri.port != 0
    }.getOrDefault(false)

    override fun session(): OutfitServerSession {
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
            json.optInt("max_image_bytes", 0), json.optInt("max_body_bytes", 0))
        if (ready && (value.modelId.isBlank() || value.modelId.length > 128 || value.revision.isBlank() ||
                value.revision.length > 128 || !sha.matches(value.modelSha) ||
                value.policy.isBlank() || value.policy.length > 128 || value.retentionSeconds !in 1..86400 ||
                value.maxImageBytes !in 1..5 * 1024 * 1024 || value.maxBodyBytes !in 1..29 * 1024 * 1024 ||
                json.optJSONArray("roles")?.takeIf { it.length() == 3 }?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                    != setOf("tops", "bottoms", "shoes"))) throw OutfitServerFailure("invalid_config")
        return value
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
            progress / 100.0, available, resultSha, (expiry * 1000).toLong())
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
        else -> "승인된 생성 서버가 준비되지 않았거나 요청을 완료하지 못했어요. 이전 사진을 새 AI 결과로 대신하지 않아요."
    }
}

/** Only opaque job/account hashes and policy expiry persist; no photos, URI or credentials. */
interface OutfitTicketStore {
    fun read(): OutfitServerTicket?
    fun write(ticket: OutfitServerTicket)
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
            j.getLong("expiresAt"), j.getString("modelSha"))
        ServerOutfitVtonRepository.requireUuid(ticket.id)
        if (!ServerOutfitVtonRepository.validBase(ticket.base) || ticket.revision.length !in 1..128 ||
            listOf(ticket.inputSha, ticket.accountKey, ticket.selectionSha, ticket.modelSha).any { !Regex("[a-f0-9]{64}").matches(it) } ||
            ticket.createdAt <= 0 || ticket.expiresAt <= ticket.createdAt) throw OutfitServerFailure("journal_unreadable")
        return ticket
    }
    override fun write(ticket: OutfitServerTicket) {
        val bytes = JSONObject().put("id", ticket.id).put("inputSha", ticket.inputSha).put("revision", ticket.revision)
            .put("base", ticket.base).put("accountKey", ticket.accountKey).put("selectionSha", ticket.selectionSha)
            .put("createdAt", ticket.createdAt).put("expiresAt", ticket.expiresAt).put("modelSha", ticket.modelSha).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw OutfitServerFailure("journal_unavailable") }
    }
}
