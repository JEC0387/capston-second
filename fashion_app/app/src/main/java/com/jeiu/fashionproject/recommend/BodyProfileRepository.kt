package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import com.jeiu.fashionproject.data.BodyDimensions
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.data.AvatarProfile

/** 세션 측정값을 우선 사용하고, 허용된 경우에만 저장된 서버 기록을 조회한다. */
object BodyProfileRepository {
    fun current(manual: ManualBodyMeasurements?, session: BodyMeasurements?, avatar: AvatarProfile? = null): BodyProfile? =
        avatar?.let { BodyProfileAnalyzer.fromDimensions(AvatarBodyAnalysis.measuredDimensions(it)) }
            ?: BodyProfileAnalyzer.fromDimensions(BodyDimensions.resolve(manual,session))
    /**
     * 길이 비율 프로필에 체형 분류(사진 분석 + 직접 입력 둘레)와 성별을 붙인다.
     * 길이 정보가 없어도 체형 분류만으로 추천에 반영할 수 있게 NaN 비율의 프로필을 만든다.
     */
    fun withBodyType(base: BodyProfile?, manual: ManualBodyMeasurements?, photoType: String?,
                     gender: com.jeiu.fashionproject.data.Gender?): BodyProfile? {
        val type = BodyShapeFusion.fuse(photoType, manual)?.type
        if (type == null) return base?.copy(gender = gender)
        return (base ?: BodyProfile(Double.NaN, Double.NaN)).copy(bodyType = type, gender = gender)
    }
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
