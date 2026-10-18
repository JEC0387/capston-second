package com.jeiu.fashionproject.data

import android.content.Context
import org.json.JSONObject

/**
 * 서버 DB(body_measurements 표)에 쌓인 체형 측정 기록 1건.
 * 사진은 없고 숫자만 있다. cm 값은 측정에 실패한 날이면 null.
 */
data class BodyRecord(
    val id: Int,
    val bodyType: String,
    val confidence: Int,
    val shoulderToHip: Float?,   // 어깨 너비 ÷ 엉덩이 너비
    val waistToHip: Float?,      // 허리 너비 ÷ 엉덩이 너비
    val legRatio: Float?,        // 다리 길이 ÷ 키
    val heightCm: Float?,
    val shoulderCm: Float?,      // 정면 너비 추정값 (둘레 아님)
    val waistCm: Float?,
    val hipCm: Float?,
    val legCm: Float?,
    val measuredAt: Long,        // Unix 밀리초
    val torsoCm: Float? = null,
    val isSample: Boolean = false // 시연용 샘플 기록 (서버가 처음 한 번 넣어 줌, 실제 측정 아님)
)

/** 체형 측정 기록 불러오기 · 지우기 (서버 GET/DELETE /api/body-measurements) */
object BodyRecords {

    /** 측정 기록 (최근 것부터). 서버에 연결되지 않으면 null. */
    suspend fun load(limit: Int = 30): List<BodyRecord>? {
        val body = BackendApi.getJson("/api/body-measurements?limit=$limit") ?: return null
        return runCatching { parse(body) }.getOrNull()
    }

    /** 내 측정 기록 전부 삭제. 성공하면 true. */
    suspend fun deleteAll(): Boolean = DbSync.deleteBodyRecords()

    /** 시연용 샘플 기록만 삭제. 성공하면 true. (지운 샘플은 다시 생기지 않음) */
    suspend fun deleteSamples(): Boolean = BackendApi.delete("/api/body-measurements?samplesOnly=true")

    fun parse(body: String): List<BodyRecord> {
        val arr = JSONObject(body).getJSONArray("measurements")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            fun num(key: String): Float? =
                if (o.has(key) && !o.isNull(key)) o.getDouble(key).toFloat().takeIf { it.isFinite() && it > 0f } else null
            BodyRecord(
                id = o.getInt("id"),
                bodyType = o.optString("bodyType", ""),
                confidence = o.optInt("confidence", 0),
                shoulderToHip = num("shoulderToHip"),
                waistToHip = num("waistToHip"),
                legRatio = num("legRatio"),
                heightCm = num("heightCm"),
                shoulderCm = num("shoulderCm"),
                waistCm = num("waistCm"),
                hipCm = num("hipCm"),
                legCm = num("legCm"),
                torsoCm = num("torsoCm"),
                measuredAt = if (o.isNull("measuredAt")) 0L else o.optLong("measuredAt", 0L),
                isSample = o.optBoolean("isSample", false)
            )
        }
    }
}

/**
 * 체형 사진 개인정보 동의 (폰 안에 저장).
 *
 * - 동의해야 체형 촬영 · 사진 선택을 시작할 수 있다. 사진은 폰 안에서만 분석하고 서버로 보내지 않는다.
 * - "측정 기록 저장"은 따로 고를 수 있다. 끄면 분석 결과 숫자도 서버에 남기지 않는다.
 */
object PrivacyConsent {
    private const val PREFS = "privacy_consent"
    private const val KEY_AGREED_AT = "body_photo_agreed_at"
    private const val KEY_SAVE_RECORDS = "save_body_records"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAgreed(context: Context): Boolean = prefs(context).getLong(KEY_AGREED_AT, 0L) > 0L

    /** 동의한 시각 (Unix 밀리초, 동의 전이면 0) */
    fun agreedAt(context: Context): Long = prefs(context).getLong(KEY_AGREED_AT, 0L)

    fun agree(context: Context, saveRecords: Boolean) {
        prefs(context).edit()
            .putLong(KEY_AGREED_AT, System.currentTimeMillis())
            .putBoolean(KEY_SAVE_RECORDS, saveRecords)
            .apply()
    }

    /** 동의 철회 — 다음 촬영 때 동의 화면이 다시 나온다 */
    fun withdraw(context: Context) {
        prefs(context).edit().remove(KEY_AGREED_AT).putBoolean(KEY_SAVE_RECORDS, false).apply()
        DbSync.clearPendingBodyRecords()
        BodyAnalysisSessionStore.clear()
    }

    /** 분석 결과 숫자를 서버에 기록으로 남겨도 되는지 (동의 + 저장 켬) */
    fun canSaveRecords(context: Context): Boolean =
        hasAgreed(context) && prefs(context).getBoolean(KEY_SAVE_RECORDS, false)

    fun setSaveRecords(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVE_RECORDS, on).apply()
        if (!on) DbSync.clearPendingBodyRecords()
    }
}
