package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/** 저장 실패와 무관하게 이번 분석값을 먼저 세션에 반영한다. */
object BodyAnalysisCompletion {
    fun complete(measurements: BodyMeasurements?, saveRecords: Boolean, save: () -> Unit) {
        if (!BodyAnalysisSessionStore.isValid(measurements)) return
        BodyAnalysisSessionStore.update(measurements)
        if (saveRecords) runCatching(save)
    }
}
