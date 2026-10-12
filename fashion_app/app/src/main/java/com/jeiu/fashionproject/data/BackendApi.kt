package com.jeiu.fashionproject.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * FastAPI 백엔드 연동 클라이언트.
 *
 * - 에뮬레이터에서 PC의 localhost는 10.0.2.2 로 접근한다.
 * - 실기기 테스트 시 BASE_URL을 PC의 내부 IP(예: http://192.168.0.x:8000)로 변경.
 * - 백엔드 실행: backend 폴더에서  python -m uvicorn main:app --host 0.0.0.0 --port 8000
 */
data class DetectedObject(val label: String, val confidence: Float, val bbox: List<Int>)

data class DetectionResult(
    val totalCount: Int,
    val detections: List<DetectedObject>,
    val counts: Map<String, Int>,
    val resultImageUrl: String   // 바운딩 박스가 그려진 결과 이미지 (서버 상대경로)
)

/** 옷 종류 분류 후보 하나. label은 DeepFashion2 종류(예: trousers) 또는 COCO 가방(예: handbag). */
data class ClothingCandidate(
    val label: String,
    val confidence: Float,
    val area: Float,                 // 박스 면적 / 이미지 면적
    val maskPngBase64: String?       // 옷 윤곽(흑백 PNG) — "옷만 오려내기"에 사용, 가방은 없음
)

object BackendApi {

    var BASE_URL = "http://10.0.2.2:8000"

    // ── 공용 HTTP 헬퍼 ──────────────────────────────────────────────

    /** GET 요청 → 응답 본문 문자열. 실패 시 null (호출부가 로컬 폴백) */
    suspend fun getJson(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 5_000
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            if (code in 200..299) body else null
        }.getOrNull()
    }

    /** JSON 본문으로 POST → 응답 본문 문자열. 실패 시 null */
    suspend fun postJson(path: String, jsonBody: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3_000
                readTimeout = 5_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            if (code in 200..299) body else null
        }.getOrNull()
    }

    /** 서버가 살아있는지 빠르게 확인 (홈 진입 시 1회) */
    suspend fun isServerUp(): Boolean = getJson("/") != null

    /** YOLOv8 객체 탐지: 이미지를 multipart로 업로드하고 탐지 결과를 받는다. */
    suspend fun detectObjects(context: Context, uri: Uri, confThreshold: Float = 0.25f): DetectionResult =
        withContext(Dispatchers.IO) {
            val json = JSONObject(postPhoto(context, "/api/object-detection", uri, confThreshold))
            val detections = buildList {
                val arr = json.getJSONArray("detections")
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    val bboxArr = d.getJSONArray("bbox")
                    add(
                        DetectedObject(
                            label = d.getString("label"),
                            confidence = d.getDouble("confidence").toFloat(),
                            bbox = List(bboxArr.length()) { bboxArr.getInt(it) }
                        )
                    )
                }
            }
            val counts = buildMap {
                val c = json.getJSONObject("counts")
                c.keys().forEach { k -> put(k, c.getInt(k)) }
            }
            DetectionResult(
                totalCount = json.getInt("totalCount"),
                detections = detections,
                counts = counts,
                resultImageUrl = BASE_URL + json.getString("resultImageUrl")
            )
        }

    /** 옷 종류 분류 (DeepFashion2 + COCO 가방류): 크게 잡힌 순서의 후보 목록을 받는다. */
    suspend fun classifyClothing(context: Context, uri: Uri, confThreshold: Float = 0.3f): List<ClothingCandidate> =
        withContext(Dispatchers.IO) {
            // 첫 호출 때 서버가 모델을 불러오느라 오래 걸릴 수 있어 읽기 제한을 넉넉히 둔다
            val json = JSONObject(postPhoto(context, "/api/clothing/classify", uri, confThreshold, readTimeoutMs = 60_000))
            val arr = json.getJSONArray("candidates")
            List(arr.length()) { i ->
                val c = arr.getJSONObject(i)
                ClothingCandidate(
                    label = c.getString("label"),
                    confidence = c.getDouble("confidence").toFloat(),
                    area = c.getDouble("area").toFloat(),
                    maskPngBase64 = if (c.isNull("mask")) null else c.getString("mask")
                )
            }
        }

    /** 사진 한 장을 multipart(photo + conf_threshold)로 올리고 응답 본문을 받는다. */
    private fun postPhoto(
        context: Context,
        path: String,
        uri: Uri,
        confThreshold: Float,
        readTimeoutMs: Int = 30_000
    ): String {
        val boundary = "----FashionBoundary${System.currentTimeMillis()}"
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5_000
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("이미지를 읽을 수 없습니다")

        DataOutputStream(conn.outputStream).use { out ->
            // conf_threshold 필드
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"conf_threshold\"\r\n\r\n")
            out.writeBytes("$confThreshold\r\n")
            // photo 파일 필드
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"upload.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(imageBytes)
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
        }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("서버 오류 ($code)")
        return body
    }

    // ── 실시간 날씨 ─────────────────────────────────────────────────
    suspend fun fetchWeather(lat: Double, lon: Double): ServerWeather? {
        val body = getJson("/api/weather?lat=$lat&lon=$lon") ?: return null
        return runCatching {
            val j = JSONObject(body)
            ServerWeather(
                condition = j.getString("condition"),
                temp = j.getInt("temp"),
                humidity = j.getInt("humidity"),
                conditionId = j.getInt("conditionId"),
                advice = j.getString("advice")
            )
        }.getOrNull()
    }

    // ── 스타일 취향 학습 ────────────────────────────────────────────
    suspend fun getStylePreference(): StylePrefData? =
        getJson("/api/style-preference")?.let { parsePref(it) }

    suspend fun rateStylePreference(style: String, like: Boolean): StylePrefData? {
        val body = JSONObject().put("style", style).put("like", like).toString()
        return postJson("/api/style-preference/rate", body)?.let { parsePref(it) }
    }

    private fun parsePref(body: String): StylePrefData? = runCatching {
        val j = JSONObject(body)
        val scores = linkedMapOf<String, Int>()
        val arr = j.getJSONArray("scores")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            scores[o.getString("style")] = o.getInt("score")
        }
        StylePrefData(scores, j.getInt("ratedCount"), j.getString("topStyle"))
    }.getOrNull()

    // ── 프로필 ─────────────────────────────────────────────────────
    suspend fun getProfile(): ServerProfile? {
        val body = getJson("/api/profile") ?: return null
        return runCatching {
            val j = JSONObject(body)
            ServerProfile(
                nickname = j.getString("nickname"),
                email = j.getString("email"),
                wardrobeCount = j.getInt("wardrobeCount"),
                outfitCount = j.getInt("outfitCount"),
                favoriteCount = j.getInt("favoriteCount"),
                bodyType = if (j.isNull("bodyType")) null else j.optString("bodyType"),
            )
        }.getOrNull()
    }
}

// ── 서버 응답 데이터 모델 ──────────────────────────────────────────
data class ServerWeather(
    val condition: String, val temp: Int, val humidity: Int,
    val conditionId: Int, val advice: String
)

data class StylePrefData(
    val scores: Map<String, Int>, val ratedCount: Int, val topStyle: String
)

data class ServerProfile(
    val nickname: String, val email: String, val wardrobeCount: Int,
    val outfitCount: Int, val favoriteCount: Int, val bodyType: String?
)
