package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import com.jeiu.fashionproject.data.BodyDimensions
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore

/** 세션 측정값을 우선 사용하고, 허용된 경우에만 저장된 서버 기록을 조회한다. */
object BodyProfileRepository {
    fun current(manual: ManualBodyMeasurements?, session: BodyMeasurements?): BodyProfile? =
        BodyProfileAnalyzer.fromDimensions(BodyDimensions.resolve(manual,session))
    fun selectLatest(session: BodyMeasurements?, savedRecords: List<BodyRecord>?): BodyProfile? =
        BodyProfileAnalyzer.fromMeasurements(session) ?: BodyProfileAnalyzer.latestValid(savedRecords)

    suspend fun loadLatest(canReadSavedRecords: Boolean): BodyProfile? {
        val session = BodyAnalysisSessionStore.measurements.value
        val manual = ManualBodyStore.current.value
        current(manual,session)?.let { return it }
        // An incomplete current profile must not be silently replaced with an older/differently calibrated one.
        if (manual != null || session != null) return null
        return if (canReadSavedRecords) selectLatest(session, BodyRecords.load()) else null
    }
}
