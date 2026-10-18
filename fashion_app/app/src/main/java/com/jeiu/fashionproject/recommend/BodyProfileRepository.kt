package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/** 세션 측정값을 우선 사용하고, 허용된 경우에만 저장된 서버 기록을 조회한다. */
object BodyProfileRepository {
    fun selectLatest(session: BodyMeasurements?, savedRecords: List<BodyRecord>?): BodyProfile? =
        BodyProfileAnalyzer.fromMeasurements(session) ?: BodyProfileAnalyzer.latestValid(savedRecords)

    suspend fun loadLatest(canReadSavedRecords: Boolean): BodyProfile? {
        val session = BodyAnalysisSessionStore.measurements.value
        BodyProfileAnalyzer.fromMeasurements(session)?.let { return it }
        return if (canReadSavedRecords) selectLatest(session, BodyRecords.load()) else null
    }
}
