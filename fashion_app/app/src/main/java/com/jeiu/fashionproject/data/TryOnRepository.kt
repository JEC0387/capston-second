package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * FASHN AI 가상피팅 API (tryon-v1.6).
 * 1) POST /v1/run 으로 사람 사진 + 옷 사진을 보내면 작업 id를 받고
 * 2) GET /v1/status/{id} 를 3초마다 확인해서 completed 가 되면 결과 이미지 URL을 받는다.
 * 한 번 입힐 때마다 1크레딧이 차감된다.
 */
object TryOnRepository {
    private const val BASE = "https://api.fashn.ai/v1"

    fun hasKey() = BuildConfig.FASHN_API_KEY.isNotBlank()

    /** 앱 옷장 카테고리 → FASHN category. 신발·가방은 지원하지 않아서 null. */
    fun categoryFor(appCategory: String): String? = when (appCategory) {
        "상의", "아우터" -> "tops"
        "하의" -> "bottoms"
        "원피스" -> "one-pieces"
        else -> null
    }

    /** 갤러리/카메라 Uri 사진을 API 처리 크기(864x1296)에 맞게 줄여 data URI(JPEG base64)로 바꾼다. */
    suspend fun uriToDataUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 864 && bounds.outHeight / (sample * 2) >= 1296) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("사진을 열 수 없어요")
        val scale = minOf(1f, 1296f / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 90, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * modelImage(사람 사진: URL 또는 data URI)에 garmentUrl 옷을 입힌 결과 이미지 URL을 돌려준다.
     * 이전 결과 URL을 modelImage로 다시 넣으면 상의 → 하의 순서로 겹쳐 입힐 수 있다.
     */
    suspend fun tryOn(modelImage: String, garmentUrl: String, category: String): String = withContext(Dispatchers.IO) {
        if (!hasKey()) error("FASHN_API_KEY가 없어요. local.properties에 키를 넣고 다시 빌드해 주세요.")
        val body = JSONObject()
            .put("model_name", "tryon-v1.6")
            .put("inputs", JSONObject()
                .put("model_image", modelImage)
                .put("garment_image", garmentUrl)
                .put("category", category)
                .put("mode", "balanced")
                .put("output_format", "jpeg"))
        val run = request("$BASE/run", body.toString())
        run.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { error(friendly(it)) }
        val id = run.getString("id")

        repeat(40) { // 최대 약 2분
            delay(3_000)
            val st = request("$BASE/status/$id", null)
            when (st.optString("status")) {
                "completed" -> return@withContext st.getJSONArray("output").getString(0)
                "starting", "in_queue", "processing" -> Unit
                else -> error(friendly(st.opt("error")?.toString() ?: "가상피팅에 실패했어요"))
            }
        }
        error("가상피팅이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요.")
    }

    private fun friendly(raw: String): String = when {
        raw.contains("PoseError", true) -> "사진에서 사람의 전신 자세를 찾지 못했어요. 전신이 잘 보이는 사진으로 다시 해 주세요."
        raw.contains("credit", true) -> "FASHN 크레딧이 부족해요."
        else -> "가상피팅 실패: $raw"
    }

    private fun request(url: String, jsonBody: String?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (jsonBody != null) "POST" else "GET"
            setRequestProperty("Authorization", "Bearer ${BuildConfig.FASHN_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 20_000
            readTimeout = 60_000
            if (jsonBody != null) {
                doOutput = true
                outputStream.use { it.write(jsonBody.toByteArray()) }
            }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code == 401) error("FASHN API 키가 올바르지 않아요 (401)")
        if (code == 402) error("FASHN 크레딧이 부족해요 (402)")
        if (code !in 200..299) error("가상피팅 서버 오류 (HTTP $code)")
        return JSONObject(text)
    }
}
