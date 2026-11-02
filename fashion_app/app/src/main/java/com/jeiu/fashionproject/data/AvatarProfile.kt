package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class AvatarSource(val label: String) {
    BMI("키·몸무게 추정"), PHOTO("사진 정면 너비·길이 추정"), MANUAL("직접 입력한 신체 치수")
}

/** Visual estimate for a low-poly avatar. None of these values are tape measurements. */
data class AvatarProfile(
    val heightCm: Float, val weightKg: Float,
    val shoulder: Float = 1f, val waist: Float = 1f, val hip: Float = 1f,
    val thigh: Float = 1f, val leg: Float = 1f,
    val chest: Float = 1f, val torso: Float = 1f,
    val source: AvatarSource = AvatarSource.BMI,
    val applied: Map<BodyDimension, DimensionValue> = emptyMap()
) {
    fun valid() = heightCm.isFinite() && heightCm in 100f..230f &&
        weightKg.isFinite() && weightKg in 25f..300f &&
        listOf(shoulder, waist, hip, thigh, leg, chest, torso).all { it.isFinite() && it in .8f..1.2f } &&
        (if(source == AvatarSource.BMI) applied.isEmpty() else validApplied() &&
            applied[BodyDimension.HEIGHT]?.cm == heightCm && applied.isNotEmpty() && applied.all { (key,value) ->
                value.at > 0 && value.cm.isFinite() && value.cm > 0 &&
                    (source != AvatarSource.PHOTO || key !in setOf(BodyDimension.CHEST_CIRCUMFERENCE,
                        BodyDimension.WAIST_CIRCUMFERENCE,BodyDimension.HIP_CIRCUMFERENCE)) &&
                    (value.origin == MeasurementOrigin.USER_ENTERED && (source == AvatarSource.MANUAL || key == BodyDimension.HEIGHT) ||
                        source == AvatarSource.PHOTO && key != BodyDimension.HEIGHT && value.origin == MeasurementOrigin.PHOTO_ESTIMATE)
            } && AvatarParameters.from(BodyDimensionProfile(applied,emptyList(),emptyList())).isValid())

    private fun validApplied(): Boolean {
        fun cm(key:BodyDimension)=applied[key]?.cm
        if(source==AvatarSource.MANUAL) return applied.keys.none { it in setOf(BodyDimension.WAIST_FRONT_WIDTH,BodyDimension.HIP_FRONT_WIDTH) } &&
            ManualBodyMeasurements(heightCm,cm(BodyDimension.SHOULDER_WIDTH),cm(BodyDimension.CHEST_CIRCUMFERENCE),
                cm(BodyDimension.WAIST_CIRCUMFERENCE),cm(BodyDimension.HIP_CIRCUMFERENCE),
                cm(BodyDimension.LEG_LENGTH),cm(BodyDimension.TORSO_LENGTH),1L).isValid()
        fun width(key:BodyDimension,range:ClosedFloatingPointRange<Float>,ratio:ClosedFloatingPointRange<Float>)=
            cm(key)?.let { it in range && it/heightCm in ratio }==true
        return width(BodyDimension.SHOULDER_WIDTH,15f..75f,.12f.. .40f) &&
            width(BodyDimension.WAIST_FRONT_WIDTH,12f..80f,.09f.. .45f) &&
            width(BodyDimension.HIP_FRONT_WIDTH,15f..85f,.12f.. .45f) &&
            (cm(BodyDimension.LEG_LENGTH)?.div(heightCm)?.let { it in .25f.. .65f } ?: true) &&
            (cm(BodyDimension.TORSO_LENGTH)?.div(heightCm)?.let { it in .15f.. .40f } ?: true) &&
            (cm(BodyDimension.LEG_LENGTH)==null || cm(BodyDimension.TORSO_LENGTH)==null ||
                cm(BodyDimension.LEG_LENGTH)!!+cm(BodyDimension.TORSO_LENGTH)!!<=heightCm*.95f)
    }

    fun parameters(): AvatarParameters {
        require(valid())
        if(source != AvatarSource.BMI) {
            val base=AvatarParameters.from(BodyDimensionProfile(applied,emptyList(),emptyList()))
            return base.copy(shoulderCm=(base.shoulderCm*shoulder).coerceIn(heightCm*.10f,heightCm*.45f),
                chestWidthCm=(base.chestWidthCm*chest).coerceIn(heightCm*.10f,heightCm*.45f),
                waistWidthCm=(base.waistWidthCm*waist).coerceIn(heightCm*.10f,heightCm*.45f),
                hipWidthCm=(base.hipWidthCm*hip).coerceIn(heightCm*.10f,heightCm*.45f),
                legCm=(base.legCm*leg).coerceIn(heightCm*.25f,heightCm*.65f),
                torsoCm=(base.torsoCm*torso).coerceIn(heightCm*.15f,heightCm*.40f),
                sources=listOf(source.label)+base.sources,
                notes=base.notes+"표시 비율 조절은 원본 측정값을 바꾸지 않아요. 관절 위치는 기본 인체 표시 범위로 제한해요.")
        }
        val h = heightCm
        val bmi = weightKg / ((h / 100f) * (h / 100f))
        val fullness = ((bmi - 22f) * .012f).coerceIn(-.18f, .24f)
        return AvatarParameters(heightCm = h,
            shoulderCm = h * .235f * shoulder,
            chestWidthCm = h * .20f * (1f + fullness * .65f) * chest,
            waistWidthCm = h * .17f * (1f + fullness) * waist,
            hipWidthCm = h * .21f * (1f + fullness * .65f) * hip,
            legCm = h * .465f * leg,
            torsoCm = h * .27f * torso,
            sources = listOf("키·몸무게 입력과 사용자 표시 비율"),
            notes = listOf("BMI로 만든 시각적 추정 형상이에요. 실제 둘레·체지방·신체 치수 또는 옷 핏이 아니에요."))
    }
}

/** Creates a preview only. Callers must request approval before applying/saving. No width-to-circumference conversion. */
object AvatarMeasurementImport {
    fun photo(value: com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements?, prior: AvatarProfile? = null): AvatarProfile? {
        if(!BodyAnalysisSessionStore.isValid(value)) return null
        return create(BodyDimensions.resolve(null,value),AvatarSource.PHOTO,prior)
    }
    fun manual(value: ManualBodyMeasurements?, prior: AvatarProfile? = null): AvatarProfile? {
        if(value?.isValid()!=true) return null
        return create(BodyDimensions.resolve(value,null),AvatarSource.MANUAL,prior)
    }
    private fun create(profile: BodyDimensionProfile,source: AvatarSource,prior: AvatarProfile?): AvatarProfile? =
        AvatarProfile(profile[BodyDimension.HEIGHT]!!.cm,prior?.weightKg ?: 65f,
            source=source,applied=profile.values.toMap()).takeIf { it.valid() }
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
            val schema=json.getInt("schema");require(schema in 1..2)
            AvatarProfile(json.getDouble("height").toFloat(), json.getDouble("weight").toFloat(),
                json.getDouble("shoulder").toFloat(), json.getDouble("waist").toFloat(),
                json.getDouble("hip").toFloat(), json.getDouble("thigh").toFloat(),
                json.getDouble("leg").toFloat(),
                if(schema==2) json.getDouble("chest").toFloat() else 1f,
                if(schema==2) json.getDouble("torso").toFloat() else 1f,
                if(schema==2) AvatarSource.valueOf(json.getString("source")) else AvatarSource.BMI,
                if(schema==2) json.getJSONObject("applied").let { values -> values.keys().asSequence().associate { key ->
                    val item=values.getJSONObject(key)
                    BodyDimension.valueOf(key) to DimensionValue(item.getDouble("cm").toFloat(),
                        MeasurementOrigin.valueOf(item.getString("origin")),item.getLong("at"))
                } } else emptyMap()).takeIf { it.valid() }
        }.getOrNull()
        _current.value = parsed
        _issue.value = if (parsed == null) "저장된 3D 표시값을 읽지 못했어요. 원본을 보존했어요." else null
    }

    fun save(context: Context, profile: AvatarProfile): Boolean {
        load(context) // Recheck disk corruption, even if another caller has not loaded the store yet.
        if (!profile.valid() || _issue.value != null) return false
        val json = JSONObject().put("schema", 2).put("height", profile.heightCm)
            .put("weight", profile.weightKg).put("shoulder", profile.shoulder)
            .put("waist", profile.waist).put("hip", profile.hip)
            .put("thigh", profile.thigh).put("leg", profile.leg).put("chest",profile.chest).put("torso",profile.torso)
            .put("source",profile.source.name).put("applied",JSONObject().also { values ->
                profile.applied.forEach { (key,value) -> values.put(key.name,JSONObject().put("cm",value.cm)
                    .put("origin",value.origin.name).put("at",value.at)) }
            })
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val original=prefs.getString(KEY,null)
        val edit=prefs.edit()
        if(original!=null && JSONObject(original).getInt("schema")==1 && !prefs.contains("profile_v1_preserved"))
            edit.putString("profile_v1_preserved",original)
        val ok = edit.putString(KEY, json.toString()).commit()
        if (ok) _current.value = profile
        return ok
    }

    fun clear(context: Context): Boolean {
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).remove("profile_v1_preserved").commit()
        if (ok) { _current.value = null; _issue.value = null }
        return ok
    }
}
