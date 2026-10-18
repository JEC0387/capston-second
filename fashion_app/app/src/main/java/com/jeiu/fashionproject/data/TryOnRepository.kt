package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import android.net.Uri
import android.util.Base64
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * FASHN AI 가상피팅 API (tryon-v1.6).
 * 1) POST /v1/run 으로 사람 사진 + 옷 사진을 보내면 작업 id를 받고
 * 2) GET /v1/status/{id} 를 3초마다 확인해서 completed 가 되면 결과 이미지 URL을 받는다.
 * 생성은 유료이며 요청별 요금·크레딧은 현재 서비스 정책을 확인한다.
 */
object TryOnRepository {
    private const val BASE = "https://api.fashn.ai/v1"

    fun hasKey() = BuildConfig.FASHN_API_KEY.isNotBlank()

    /** 앱 옷장 카테고리 → FASHN category. 신발·가방은 지원하지 않아서 null. */
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
            val out = ByteArrayOutputStream()
            if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw TryOnFailure("사진을 준비하지 못했어요.")
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (transformed !== bitmap) transformed?.recycle()
            bitmap?.recycle()
            if (original !== bitmap && original !== transformed) original?.recycle()
        }
    }

    /**
     * modelImage(사람 사진: URL 또는 data URI)에 garmentUrl 옷을 입힌 결과 이미지 URL을 돌려준다.
     * 이전 결과 URL을 modelImage로 다시 넣으면 상의 → 하의 순서로 겹쳐 입힐 수 있다.
     */
    suspend fun tryOn(modelImage: String, garmentUrl: String, category: String): String = withContext(Dispatchers.IO) {
        if (!hasKey()) throw TryOnFailure("AI 피팅 서비스가 아직 연결되지 않았어요.")
        if (category !in setOf("tops", "bottoms", "one-pieces")) throw TryOnFailure("AI가 지원하지 않는 의류 종류예요.")
        val body = JSONObject()
            .put("model_name", "tryon-v1.6")
            .put("inputs", JSONObject()
                .put("model_image", modelImage)
                .put("garment_image", garmentUrl)
                .put("category", category)
                .put("mode", "balanced")
                .put("num_samples", 1)
                .put("output_format", "jpeg"))
        val run = request("$BASE/run", body.toString())
        run.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { throw TryOnFailure(friendly(it)) }
        val id = run.getString("id")

        return@withContext withTimeout(180_000L) {
            repeat(40) { // 네트워크 지연을 포함한 실제 대기 한도도 별도로 적용
                coroutineContext.ensureActive()
                delay(3_000)
                val st = request("$BASE/status/$id", null)
                when (st.optString("status")) {
                    "completed" -> {
                        val output = st.optJSONArray("output")?.optString(0).orEmpty()
                        if (!TryOnBatch.isResultUrl(output)) throw TryOnFailure("AI 결과 이미지 주소를 확인하지 못했어요.")
                        return@withTimeout output
                    }
                    "starting", "in_queue", "processing" -> Unit
                    else -> throw TryOnFailure(friendly(st.opt("error")?.toString().orEmpty()))
                }
            }
            throw TryOnFailure("가상피팅이 오래 걸려요. 서버 처리·차감 여부를 확인한 뒤 재시도해주세요.")
        }
    }

    private fun friendly(raw: String): String = when {
        raw.contains("PoseError", true) -> "사진에서 사람의 전신 자세를 찾지 못했어요. 전신이 잘 보이는 사진으로 다시 해 주세요."
        raw.contains("credit", true) -> "FASHN 크레딧이 부족해요."
        else -> "AI 피팅을 완료하지 못했어요. 사진과 서비스 상태를 확인해주세요."
    }

    /** Save only a validated JPEG result using the system document picker; no broad storage permission. */
    suspend fun saveResult(context: Context, source: String, target: Uri) = withContext(Dispatchers.IO) {
        if (!TryOnBatch.isResultUrl(source)) throw TryOnFailure("결과 이미지 주소가 올바르지 않아요.")
        val conn = URL(source).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            if (conn.responseCode !in 200..299) throw TryOnFailure("결과 이미지를 내려받지 못했어요.")
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 20 * 1024 * 1024) throw TryOnFailure("결과 이미지가 너무 커요.")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            if (bytes.size < 3 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte())
                throw TryOnFailure("저장할 JPEG 이미지를 확인하지 못했어요.")
            coroutineContext.ensureActive()
            context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                ?: throw TryOnFailure("저장 위치를 열지 못했어요.")
        } finally { conn.disconnect() }
    }

    private suspend fun request(url: String, jsonBody: String?): JSONObject {
        coroutineContext.ensureActive()
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = if (jsonBody != null) "POST" else "GET"
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.FASHN_API_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (jsonBody != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(jsonBody.toByteArray()) }
            }
            val code = conn.responseCode
            if (code == 401) throw TryOnFailure("AI 피팅 서비스 인증을 확인해주세요. (401)")
            if (code == 402) throw TryOnFailure("FASHN 크레딧이 부족해요. (402)")
            if (code !in 200..299) throw TryOnFailure("AI 피팅 서버에 연결하지 못했어요. (HTTP $code)")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            coroutineContext.ensureActive()
            return JSONObject(text)
        } catch (_: IOException) {
            throw TryOnFailure("AI 피팅 서비스에 연결하지 못했어요. 인터넷 연결을 확인한 뒤 다시 시도해주세요.")
        } finally { conn.disconnect() }
    }
}
