package com.jeiu.fashionproject.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class DemoFailure(val kind: String) : IOException(kind)
data class DemoImage(val png: ByteArray, val width: Int, val height: Int, val sha256: String)

/** Deliberately not a TryOnGateway: no person bytes, arbitrary URL, account or local-server token input. */
interface OfficialDemoGateway {
    suspend fun check()
    suspend fun example(id: String): DemoImage
    suspend fun submit(id: String): String
    suspend fun awaitResult(eventId: String, progress: suspend (String) -> Unit): String
    suspend fun result(url: String): DemoImage
}

object OfficialDemoContract {
    const val ENGINE = "official-fashn-public-example-only"
    const val ORIGIN = "https://fashn-ai-fashn-vton-1-5.hf.space"
    const val EXAMPLE = "official-tops-3"
    const val ASSETS = "https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5/resolve/main/assets/examples/"
    const val PERSON_SHA = "befcbc71f0bdd331d1148974f2db5377fa5b8d90969d6153a5d27cb1a135f418"
    // Observed official resolve redirect for this exact example on 2026-11-04; query signatures stay in memory.
    private const val PERSON_CDN_PATH = "/xet-bridge-us/697e65ed440ea4e00c9311ad/6c12397c245919e4373b6c1da509fd0d1c20804fc6f45d25f0aa50971a7145f2"
    val parameters = listOf("person_image", "garment_image", "category", "garment_photo_type",
        "num_timesteps", "guidance_scale", "seed", "segmentation_free")
    fun exampleId(id: String) { if (id != EXAMPLE) throw DemoFailure("invalid_example") }
    fun eventId(id: String) { if (!Regex("[a-f0-9]{32}").matches(id)) throw DemoFailure("invalid_event") }
    fun body(id: String): String {
        exampleId(id)
        val data = JSONArray()
        listOf("person3.png", "garment3.jpeg").forEach {
            data.put(JSONObject().put("path", ASSETS + it).put("meta", JSONObject().put("_type", "gradio.FileData")))
        }
        data.put("tops").put("flat-lay").put(50).put(1.5).put(42).put(true)
        return JSONObject().put("data", data).toString()
    }
    fun resultUrl(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host == "fashn-ai-fashn-vton-1-5.hf.space" &&
            u.port == -1 && u.userInfo == null && u.rawQuery == null && u.rawFragment == null &&
            u.rawPath.startsWith("/gradio_api/file=") && !u.rawPath.contains("%", true) &&
            u.path.substringAfter("/gradio_api/file=").startsWith("/tmp/gradio/") &&
            !u.path.split('/').contains("..") && u.path.endsWith(".webp")
    }.getOrDefault(false)
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun exampleRedirect(u: URI): Boolean = u.scheme == "https" && u.userInfo == null && u.port == -1 &&
        u.rawFragment == null && ((u.host == "us.aws.cdn.hf.co" && u.rawPath == PERSON_CDN_PATH) ||
        (u.host == "huggingface.co" && u.rawPath.startsWith("/api/resolve-cache/spaces/fashn-ai/fashn-vton-1.5/") &&
            u.rawPath.endsWith("/assets/examples/person3.png") && !u.path.split('/').contains("..")))
    fun verifyInfo(text: String) {
        val endpoint = JSONObject(text).getJSONObject("named_endpoints").getJSONObject("/try_on")
        val params = endpoint.getJSONArray("parameters")
        if (endpoint.optString("api_visibility") != "public" || params.length() != parameters.size ||
            parameters.indices.any { params.getJSONObject(it).optString("parameter_name") != parameters[it] } ||
            endpoint.getJSONArray("returns").length() != 1) throw DemoFailure("contract_changed")
    }
    fun message(kind: String): String = when (kind) {
        "quota" -> "공개 서비스 사용량 한도에 도달했어요. 계정·IP 변경이나 자동 재시도는 하지 않아요."
        "expired" -> "공개 서비스 작업 또는 결과가 만료됐거나 없어졌어요. 새 생성을 자동 요청하지 않아요."
        "unavailable" -> "공식 공개 데모에 현재 접근할 수 없어요. 자체 서버나 유료 API로 전환하지 않아요."
        "contract_changed" -> "공개 API 명세가 달라졌어요. 개발자가 확인하기 전 생성을 중단해요."
        "server_failed" -> "공개 데모가 생성 실패를 반환했어요. 원인 확인 전 재생성하지 않아요."
        "unknown_submission" -> "전송 응답이 유실되어 작업 번호를 받지 못했어요. 서버에서는 실행됐을 수 있어 POST를 반복하지 않아요."
        "stream_closed", "network" -> "통신이 끝났거나 끊겼어요. 작업 번호가 있으면 같은 작업 조회만 다시 할 수 있어요."
        "timeout" -> "앱의 대기 제한을 넘었어요. 서버 생성 취소는 확인되지 않았어요."
        "storage" -> "안전한 작업 기록을 저장하지 못했어요. 중복 생성을 막기 위해 중단해요."
        "image_invalid", "unsafe_result" -> "결과 주소·형식·크기 검증에 실패했어요. 이미지로 표시하지 않아요."
        else -> "공식 예제 시험을 진행하지 못했어요. 입력·응답을 확인해야 해요."
    }
}

/** Fresh client: no app interceptors, Authorization, cookies, redirects or automatic POST retries. */
class OfficialDemoRepository internal constructor(private val client: OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(65, TimeUnit.SECONDS)
    .callTimeout(300, TimeUnit.SECONDS).build()) : OfficialDemoGateway {
    private fun status(code: Int) {
        if (code !in 200..299) throw DemoFailure(when (code) {
            429 -> "quota"; 404, 410 -> "expired"; 401, 403, 503 -> "unavailable"; else -> "network"
        })
    }
    private suspend fun bytes(request: Request, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            status(response.code)
            val body = response.body ?: throw DemoFailure("network")
            if (body.contentLength() > limit) throw DemoFailure("image_invalid")
            body.byteStream().use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    if (out.size() + n > limit) throw DemoFailure("image_invalid")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        }
    }
    override suspend fun check() {
        try { OfficialDemoContract.verifyInfo(bytes(Request.Builder().url(OfficialDemoContract.ORIGIN +
            "/gradio_api/info").build(), 512 * 1024).toString(Charsets.UTF_8)) }
        catch (e: DemoFailure) { throw e }
        catch (_: org.json.JSONException) { throw DemoFailure("contract_changed") }
    }
    override suspend fun example(id: String): DemoImage {
        OfficialDemoContract.exampleId(id)
        // Only this public example's reviewed Hub cache/CDN redirect is allowed; SHA-256 is checked as well.
        var url = OfficialDemoContract.ASSETS + "person3.png"
        repeat(4) {
            val next = withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code in setOf(301, 302, 307, 308)) response.header("Location")
                        ?: throw DemoFailure("image_invalid") else { status(response.code); null }
                }
            }
            if (next == null) {
                val raw = bytes(Request.Builder().url(url).build(), 8 * 1024 * 1024)
                if (OfficialDemoContract.sha(raw) != OfficialDemoContract.PERSON_SHA) throw DemoFailure("contract_changed")
                return withContext(Dispatchers.Default) { decode(raw) }
            }
            val u = URI(url).resolve(next)
            if (!OfficialDemoContract.exampleRedirect(u)) throw DemoFailure("image_invalid")
            url = u.toString()
        }
        throw DemoFailure("image_invalid")
    }
    override suspend fun submit(id: String): String {
        val payload = OfficialDemoContract.body(id).toByteArray(Charsets.UTF_8)
        val oneShot = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = payload.size.toLong()
            // retryOnConnectionFailure(false) alone does NOT prevent a 503/Retry-After: 0 follow-up.
            override fun isOneShot() = true
            override fun writeTo(sink: okio.BufferedSink) { sink.write(payload) }
        }
        val request = Request.Builder().url(OfficialDemoContract.ORIGIN + "/gradio_api/call/try_on")
            .post(oneShot).build()
        val value = JSONObject(bytes(request, 16 * 1024).toString(Charsets.UTF_8)).getString("event_id")
        OfficialDemoContract.eventId(value)
        return value
    }
    override suspend fun awaitResult(eventId: String, progress: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        OfficialDemoContract.eventId(eventId)
        val req = Request.Builder().url(OfficialDemoContract.ORIGIN + "/gradio_api/call/try_on/" + eventId).build()
        client.newCall(req).execute().use { response ->
            status(response.code)
            if (response.header("Content-Type")?.substringBefore(';') != "text/event-stream") throw DemoFailure("stream_closed")
            val source = response.body?.source() ?: throw DemoFailure("stream_closed")
            var event = ""
            var total = 0
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8LineStrict(64 * 1024)
                total += line.length
                if (total > 512 * 1024) throw DemoFailure("stream_closed")
                when {
                    line.startsWith("event:") -> event = line.substringAfter(':').trim()
                    line.startsWith("data:") -> {
                        val data = line.substringAfter(':').trim()
                        when (event) {
                            "complete" -> {
                                val values = JSONArray(data)
                                if (values.length() != 1) throw DemoFailure("unsafe_result")
                                val url = values.getJSONObject(0).getString("url")
                                if (!OfficialDemoContract.resultUrl(url)) throw DemoFailure("unsafe_result")
                                return@withContext url
                            }
                            "error" -> throw DemoFailure(if (listOf("quota", "exceeded", "limit").any { data.contains(it, true) }) "quota" else "server_failed")
                            "heartbeat", "generating" -> progress("공개 서비스 대기열·생성 대기 중 · 진행률은 제공되지 않아요")
                        }
                    }
                }
            }
        }
        throw DemoFailure("stream_closed")
    }
    override suspend fun result(url: String): DemoImage {
        if (!OfficialDemoContract.resultUrl(url)) throw DemoFailure("unsafe_result")
        val raw = bytes(Request.Builder().url(url).build(), 8 * 1024 * 1024)
        return withContext(Dispatchers.Default) { decode(raw) }
    }
    companion object {
        fun decode(bytes: ByteArray): DemoImage {
            if (bytes.size !in 12..8 * 1024 * 1024) throw DemoFailure("image_invalid")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outMimeType !in setOf("image/png", "image/jpeg", "image/webp") ||
                minOf(bounds.outWidth, bounds.outHeight) < 32 ||
                bounds.outWidth.toLong() * bounds.outHeight > 4_000_000) throw DemoFailure("image_invalid")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw DemoFailure("image_invalid")
            try {
                val out = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) || out.size() > 16 * 1024 * 1024) throw DemoFailure("image_invalid")
                val png = out.toByteArray()
                return DemoImage(png, bitmap.width, bitmap.height, OfficialDemoContract.sha(png))
            } finally { bitmap.recycle() }
        }
    }
}
