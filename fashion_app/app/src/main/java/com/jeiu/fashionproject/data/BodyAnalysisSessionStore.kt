package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 현재 앱 프로세스에만 존재하는 최근 유효 측정값. 사진과 URI는 보관하지 않는다. */
object BodyAnalysisSessionStore {
    private val _measurements = MutableStateFlow<BodyMeasurements?>(null)
    val measurements: StateFlow<BodyMeasurements?> = _measurements
    private val _clearGeneration = MutableStateFlow(0)
    val clearGeneration: StateFlow<Int> = _clearGeneration

    fun update(value: BodyMeasurements?) {
        if (value == null || !BodyMeasurementCalculator.validHeight(value.heightCm) ||
            !value.shoulderWidthCm.isFinite() || value.shoulderWidthCm !in 15f..75f ||
            !value.waistWidthCm.isFinite() || value.waistWidthCm !in 12f..80f ||
            !value.hipWidthCm.isFinite() || value.hipWidthCm !in 15f..85f ||
            (value.legLengthCm != null && (!value.legLengthCm.isFinite() || value.legLengthCm / value.heightCm !in 0.25f..0.65f)) ||
            (value.torsoLengthCm != null && (!value.torsoLengthCm.isFinite() || value.torsoLengthCm / value.heightCm !in 0.15f..0.40f)) ||
            !value.cmPerPixel.isFinite() || value.cmPerPixel <= 0f ||
            !value.measuredBodyHeightPx.isFinite() || value.measuredBodyHeightPx < 200f ||
            value.measuredAt <= 0L
        ) return
        _measurements.value = value
    }

    fun clear() {
        _measurements.value = null
        _clearGeneration.value++
    }
}
