package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.ManualBodyMeasurements
import kotlin.math.abs

/**
 * 사진 분석 체형 + 직접 입력한 둘레를 합쳐 최종 체형을 정한다.
 *
 * 정면 사진은 '너비'만 볼 수 있어 옷 · 자세 · 각도에 흔들린다. 줄자로 잰 가슴 · 허리 · 엉덩이 둘레가 있으면
 * 그 값을 우선하고, 사진 결과와 같으면 "두 방법이 일치"로 표시한다.
 * 둘레 분류 기준: 가슴과 엉덩이 둘레 차이 5% · 허리/엉덩이 0.75 (일반적인 체형 구분 원칙, 의학적 판정 아님).
 */
object BodyShapeFusion {

    enum class Source(val label: String) { PHOTO("사진 기반 분석"), MANUAL("직접 입력 치수"), BOTH("사진 + 직접 입력 치수") }

    data class Result(val type: String, val source: Source, val agreed: Boolean?, val note: String?)

    /** 직접 입력한 가슴 · 허리 · 엉덩이 둘레로 본 체형. 셋 중 하나라도 없으면 null */
    fun fromManual(m: ManualBodyMeasurements?): String? {
        if (m == null) return null
        val bust = m.chestCircumferenceCm ?: return null
        val waist = m.waistCircumferenceCm ?: return null
        val hip = m.hipCircumferenceCm ?: return null
        if (listOf(bust, waist, hip).any { !it.isFinite() || it <= 0f }) return null
        val diff = (bust - hip) / hip
        return when {
            waist / hip <= 0.75f && abs(diff) <= 0.05f -> "모래시계형"
            diff >= 0.05f -> "역삼각형"
            diff <= -0.05f -> "삼각형"
            else -> "직사각형"
        }
    }

    fun fuse(photoType: String?, manual: ManualBodyMeasurements?): Result? {
        val manualType = fromManual(manual)
        return when {
            photoType != null && manualType != null && photoType == manualType ->
                Result(manualType, Source.BOTH, true, "사진 분석과 직접 잰 둘레가 같은 체형이에요.")
            photoType != null && manualType != null ->
                Result(manualType, Source.BOTH, false, "사진은 ${photoType}으로 보였지만, 직접 잰 둘레를 우선했어요.")
            manualType != null -> Result(manualType, Source.MANUAL, null, "사진 분석까지 하면 두 결과를 비교해 더 정확해져요.")
            photoType != null -> Result(photoType, Source.PHOTO, null, "가슴 · 허리 · 엉덩이 둘레를 직접 입력하면 더 정확해져요.")
            else -> null
        }
    }
}
