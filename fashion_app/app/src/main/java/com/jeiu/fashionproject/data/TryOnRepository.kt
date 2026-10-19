package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** User-issued proxy credential stays in process memory, never BuildConfig/preferences/backups. */
data class TryOnConnection(val base: String, val token: String)
object TryOnSession {
    var connection by mutableStateOf<TryOnConnection?>(null)
        private set
    fun configure(base: String, token: String): Boolean {
        val uri = runCatching { URI(base.trim()) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.path !in listOf("", "/") || token.trim().length < 32) return false
        connection = TryOnConnection(base.trim().trimEnd('/'), token.trim())
        return true
    }
    fun clear() { connection = null }
}

data class TryOnJob(val status: String, val error: String?, val step: Int, val count: Int, val resultAvailable: Boolean)
class TryOnHttpFailure(val code: Int, message: String) : Exception(message)
interface TryOnGateway {
    suspend fun image(context: Context, source: String): String
    suspend fun check(connection: TryOnConnection)
    suspend fun submit(connection: TryOnConnection, id: String, person: String, garments: List<Pair<String, String>>)
    suspend fun status(connection: TryOnConnection, id: String): TryOnJob
    suspend fun result(connection: TryOnConnection, id: String): ByteArray
    suspend fun cancel(connection: TryOnConnection, id: String)
}

/** Only the authenticated app backend is contacted; FASHN credentials never enter the APK. */
object TryOnRepository : TryOnGateway {
    fun isConfigured() = TryOnSession.connection != null
    fun categoryFor(appCategory: String): String? = TryOnPlan.categoryFor(appCategory)

    /** 갤러리/카메라 Uri 사진을 긴 변 1296px 이내로 줄여 data URI(JPEG base64)로 바꾼다. */
    suspend fun uriToDataUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        var transformed: Bitmap? = null
        var original: Bitmap? = null
        try {
            bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = minOf(1f, 1296f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2592) sample *= 2
                val decoded = context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw TryOnFailure("사진을 열 수 없어요.")
                original = decoded
                val orientation = runCatching { context.contentResolver.openInputStream(uri).use {
                    if (it == null) ExifInterface.ORIENTATION_NORMAL else ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                    val scale = minOf(1f, 1296f / maxOf(decoded.width, decoded.height))
                    postScale(scale, scale)
                }
                transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                transformed!!
            }
            coroutineContext.ensureActive()
            // Transparent cutouts need a white background before JPEG encoding.
            if (bitmap!!.hasAlpha()) {
                val white = Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(white).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap!!, 0f, 0f, null) }
                if (bitmap !== original && bitmap !== transformed) bitmap?.recycle()
                bitmap = white
            }
            val out = ByteArrayOutputStream()
            if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw TryOnFailure("사진을 준비하지 못했어요.")
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (transformed !== bitmap) transformed?.recycle()
            bitmap?.recycle()
            if (original !== bitmap && original !== transformed) original?.recycle()
        }
    }

    override suspend fun image(context: Context, source: String): String {
        val uri = Uri.parse(source)
        if (uri.scheme != "https") return uriToDataUri(context, uri)
        // Product images are fetched without the proxy credential. No redirects or unbounded input.
        return withContext(Dispatchers.IO) {
            val bytes = download(source, null)
            val temporary = File.createTempFile("tryon-input-", ".img", context.cacheDir)
            try {
                temporary.writeBytes(bytes)
                uriToDataUri(context, Uri.fromFile(temporary))
            } finally { temporary.delete() }
        }
    }
    override suspend fun check(connection: TryOnConnection) {
        request(connection, "/config")
    }
    override suspend fun submit(connection: TryOnConnection, id: String, person: String, garments: List<Pair<String, String>>) {
        val items = JSONArray()
        garments.forEach { (category, image) -> items.put(JSONObject().put("category", category).put("image", image)) }
        request(connection, "/jobs", "POST", JSONObject().put("request_id", id).put("person", person)
            .put("garments", items).put("consent", true).toString())
    }
    override suspend fun status(connection: TryOnConnection, id: String): TryOnJob {
        val value = request(connection, "/jobs/$id")
        return TryOnJob(value.getString("status"), value.optString("error").takeUnless { it.isBlank() || it == "null" },
            value.optInt("step"), value.optInt("count"), value.optBoolean("result_available"))
    }
    override suspend fun result(connection: TryOnConnection, id: String): ByteArray = withContext(Dispatchers.IO) {
        val bytes = download(connection.base + "/api/tryon/jobs/$id/result", connection.token)
        validateJpeg(bytes)
        bytes
    }
    override suspend fun cancel(connection: TryOnConnection, id: String) {
        request(connection, "/jobs/$id", "DELETE")
    }
    fun friendly(code: String?): String = when (code) {
        "pose" -> "전신 자세를 찾지 못했어요. 정면 전신 사진을 선택해주세요."
        "credit" -> "서버의 FASHN 크레딧이 부족해요. 운영자에게 문의해주세요."
        "provider_auth", "not_configured", "journal_unavailable" -> "AI 서버 또는 인증·사용량 보호 설정이 준비되지 않았어요."
        "unauthorized" -> "서버에서 발급받은 개인 접속 토큰을 확인해주세요."
        "https_required" -> "AI 사진 전송에는 HTTPS 서버가 필요해요."
        "daily_limit", "rate_limit", "busy", "provider_busy" -> "사용량 한도에 도달했거나 서버가 사용 중이에요. 잠시 뒤 확인해주세요."
        "timeout", "provider_connection_unknown" -> "처리 시간이 초과됐거나 전송 결과가 불확실해요. 이미 시작된 작업은 과금될 수 있어요. 자동 재요청하지 않았어요."
        "cancelled" -> "대기를 취소했어요. 이미 FASHN에 전송된 작업·요금은 취소되지 않을 수 있어요."
        "server_restarted" -> "서버가 재시작됐어요. 중복 과금을 막기 위해 기존 생성 요청을 다시 보내지 않았어요. 운영자에게 처리·차감을 확인해주세요."
        "result_expired" -> "결과 보관 시간이 만료됐거나 서버가 재시작됐어요. 저장한 사진을 확인해주세요."
        "active_job", "idempotency_conflict" -> "이미 진행 중인 작업이 있어요. 기존 작업 상태를 확인해주세요."
        "invalid_input", "image_size" -> "사진 형식·크기를 확인해주세요. 상의·하의 또는 원피스만 지원해요."
        else -> "AI 생성에 실패했어요. 입력 사진과 서버 상태를 확인해주세요."
    }
    /** Existing JPEG validation + system document picker, now reusing authenticated result bytes. */
    suspend fun saveResult(context: Context, bytes: ByteArray, target: Uri) = withContext(Dispatchers.IO) {
        validateJpeg(bytes)
        coroutineContext.ensureActive()
        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
            ?: throw TryOnFailure("저장 위치를 열지 못했어요.")
    }
    private fun validateJpeg(bytes: ByteArray) {
        if (bytes.size !in 3..(20 * 1024 * 1024) || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte())
            throw TryOnFailure("저장할 JPEG 이미지를 확인하지 못했어요.")
    }
    private suspend fun request(connection: TryOnConnection, path: String, method: String = "GET", body: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val conn = open(connection.base + "/api/tryon" + path, connection.token)
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.use { String(it.readBytesLimited(8192)) }
                val detail = runCatching { JSONObject(error ?: "{}").optString("detail") }.getOrNull()
                throw TryOnHttpFailure(code, friendly(detail))
            }
            JSONObject(conn.inputStream.use { String(it.readBytesLimited(64 * 1024)) })
        } finally { conn.disconnect() }
    }
    private fun open(url: String, token: String?): HttpURLConnection {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) throw TryOnFailure("HTTPS 주소가 필요해요.")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }
    private suspend fun download(url: String, token: String?): ByteArray {
        val conn = open(url, token)
        return try {
            if (conn.responseCode !in 200..299) throw TryOnHttpFailure(conn.responseCode, "결과 또는 의류 이미지를 내려받지 못했어요.")
            conn.inputStream.use { it.readBytesLimited(if (token == null) 5 * 1024 * 1024 else 20 * 1024 * 1024) }
        } finally { conn.disconnect() }
    }
    private suspend fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            coroutineContext.ensureActive()
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) throw TryOnFailure("이미지가 너무 커요.")
            output.write(buffer, 0, count)
        }
    }
}
