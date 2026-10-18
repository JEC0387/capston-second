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

    fun isValid(value: BodyMeasurements?) = BodyMeasurementCalculator.isValid(value)

    fun update(value: BodyMeasurements?) {
        if (!isValid(value)) return
        _measurements.value = value
    }

    fun clear() {
        _measurements.value = null
        _clearGeneration.value++
    }
}
