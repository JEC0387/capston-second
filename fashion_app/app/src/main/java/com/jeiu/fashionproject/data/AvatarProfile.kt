package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Visual estimate for a low-poly avatar. None of these values are tape measurements. */
data class AvatarProfile(
    val heightCm: Float, val weightKg: Float,
    val shoulder: Float = 1f, val waist: Float = 1f, val hip: Float = 1f,
    val thigh: Float = 1f, val leg: Float = 1f
) {
    fun valid() = heightCm.isFinite() && heightCm in 100f..230f &&
        weightKg.isFinite() && weightKg in 25f..300f &&
        listOf(shoulder, waist, hip, thigh, leg).all { it.isFinite() && it in .8f..1.2f }

    fun parameters(): AvatarParameters {
        require(valid())
        val h = heightCm
        val bmi = weightKg / ((h / 100f) * (h / 100f))
        val fullness = ((bmi - 22f) * .012f).coerceIn(-.18f, .24f)
        return AvatarParameters(heightCm = h,
            shoulderCm = h * .235f * shoulder,
            chestWidthCm = h * .20f * (1f + fullness * .65f),
            waistWidthCm = h * .17f * (1f + fullness) * waist,
            hipWidthCm = h * .21f * (1f + fullness * .65f) * hip,
            legCm = h * .465f * leg,
            torsoCm = h * .27f,
            sources = listOf("키·몸무게 입력과 사용자 표시 비율"),
            notes = listOf("BMI로 만든 시각적 추정 형상이에요. 실제 둘레·체지방·신체 치수 또는 옷 핏이 아니에요."))
    }
}

/** Separate from photo and manually measured data; invalid original data is never overwritten on load. */
object AvatarProfileStore {
    private const val PREFS = "avatar_visual_profile_v1"
    private const val KEY = "profile"
    private val _current = MutableStateFlow<AvatarProfile?>(null)
    val current: StateFlow<AvatarProfile?> = _current
    private val _issue = MutableStateFlow<String?>(null)
    val issue: StateFlow<String?> = _issue

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw == null) { _current.value = null; _issue.value = null; return }
        val parsed = runCatching {
            val json = JSONObject(raw)
            require(json.getInt("schema") == 1)
            AvatarProfile(json.getDouble("height").toFloat(), json.getDouble("weight").toFloat(),
                json.getDouble("shoulder").toFloat(), json.getDouble("waist").toFloat(),
                json.getDouble("hip").toFloat(), json.getDouble("thigh").toFloat(),
                json.getDouble("leg").toFloat()).takeIf { it.valid() }
        }.getOrNull()
        _current.value = parsed
        _issue.value = if (parsed == null) "저장된 3D 표시값을 읽지 못했어요. 원본을 보존했어요." else null
    }

    fun save(context: Context, profile: AvatarProfile): Boolean {
        if (!profile.valid() || _issue.value != null) return false
        val json = JSONObject().put("schema", 1).put("height", profile.heightCm)
            .put("weight", profile.weightKg).put("shoulder", profile.shoulder)
            .put("waist", profile.waist).put("hip", profile.hip)
            .put("thigh", profile.thigh).put("leg", profile.leg)
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).commit()
        if (ok) _current.value = profile
        return ok
    }

    fun clear(context: Context): Boolean {
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
        if (ok) { _current.value = null; _issue.value = null }
        return ok
    }
}
