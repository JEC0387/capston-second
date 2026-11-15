package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/**
 * 추천에 쓰는 체형 정보. 길이 비율은 사진 · 직접 입력에서 얻은 값만 쓴다(없으면 NaN).
 * bodyType 은 사진 분석과 직접 입력한 둘레를 합친 4가지 분류(BodyShapeFusion), gender 는 체형별 제안을 고를 때 쓴다.
 */
data class BodyProfile(
    val legToHeightRatio: Double,
    val torsoToLegRatio: Double,
    val bodyType: String? = null,
    val gender: com.jeiu.fashionproject.data.Gender? = null
) {
    val hasLengths: Boolean get() = legToHeightRatio.isFinite() && torsoToLegRatio.isFinite() && torsoToLegRatio > 0.0
}

object BodyProfileAnalyzer {
    fun fromDimensions(profile: com.jeiu.fashionproject.data.BodyDimensionProfile): BodyProfile? = fromLengths(
        profile[com.jeiu.fashionproject.data.BodyDimension.HEIGHT]?.cm,
        profile[com.jeiu.fashionproject.data.BodyDimension.LEG_LENGTH]?.cm,
        profile[com.jeiu.fashionproject.data.BodyDimension.TORSO_LENGTH]?.cm)
    fun fromMeasurements(measurements: BodyMeasurements?): BodyProfile? = measurements?.let {
        if (BodyMeasurementCalculator.isValid(it)) fromLengths(it.heightCm, it.legLengthCm, it.torsoLengthCm) else null
    }

    fun fromLengths(heightCm: Float?, legCm: Float?, torsoCm: Float?): BodyProfile? {
        if (heightCm == null || !BodyMeasurementCalculator.validHeight(heightCm)) return null
        if (legCm == null || !legCm.isFinite() || legCm / heightCm !in 0.25f..0.65f) return null
        if (torsoCm == null || !torsoCm.isFinite() || torsoCm / heightCm !in 0.15f..0.40f) return null
        return BodyProfile(legCm.toDouble() / heightCm, torsoCm.toDouble() / legCm)
    }

    /** 서버 오류는 null이다. 샘플을 제외하고 가장 최근의 유효한 기록을 고른다. */
    fun latestValid(records: List<BodyRecord>?): BodyProfile? = records.orEmpty()
        .asSequence()
        .filter { !it.isSample && it.measuredAt > 0L }
        .filter { record -> listOf(record.shoulderCm,record.waistCm,record.hipCm).all { value ->
            value == null || (value.isFinite() && value in 12f..85f)
        } }
        .sortedByDescending { it.measuredAt }
        .mapNotNull { fromLengths(it.heightCm, it.legCm, it.torsoCm) }
        .firstOrNull()
}
