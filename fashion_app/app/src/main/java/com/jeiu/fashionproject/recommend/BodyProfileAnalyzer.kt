package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/** 사진에서 실제로 얻은 길이만 사용한다. 너비를 둘레나 옷의 핏으로 해석하지 않는다. */
data class BodyProfile(val legToHeightRatio: Double, val torsoToLegRatio: Double)

object BodyProfileAnalyzer {
    fun fromMeasurements(measurements: BodyMeasurements?): BodyProfile? = measurements?.let {
        fromLengths(it.heightCm, it.legLengthCm, it.torsoLengthCm)
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
        .sortedByDescending { it.measuredAt }
        .mapNotNull { fromLengths(it.heightCm, it.legCm, it.torsoCm) }
        .firstOrNull()
}
