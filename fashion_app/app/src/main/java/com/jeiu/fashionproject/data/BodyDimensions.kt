package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import kotlin.math.abs

enum class BodyDimension(val label: String) {
    HEIGHT("키"), SHOULDER_WIDTH("어깨 너비"), CHEST_CIRCUMFERENCE("가슴 둘레"),
    WAIST_CIRCUMFERENCE("허리 둘레"), HIP_CIRCUMFERENCE("엉덩이 둘레"),
    LEG_LENGTH("다리 길이 (고관절~발목)"), TORSO_LENGTH("상체 길이 (어깨~고관절)"),
    WAIST_FRONT_WIDTH("허리 정면 너비"), HIP_FRONT_WIDTH("엉덩이 정면 너비")
}
enum class MeasurementOrigin(val label: String) { USER_ENTERED("직접 입력"), PHOTO_ESTIMATE("사진 추정") }
data class DimensionValue(val cm: Float, val origin: MeasurementOrigin, val at: Long)
data class DimensionComparison(val dimension: BodyDimension, val enteredCm: Float, val photoCm: Float) {
    val differenceCm: Float get() = photoCm - enteredCm
}
data class BodyDimensionProfile(
    val values: Map<BodyDimension, DimensionValue>,
    val comparisons: List<DimensionComparison>,
    val notes: List<String>
) {
    operator fun get(dimension: BodyDimension): DimensionValue? = values[dimension]
}

/** One local, source-aware representation for analysis, avatar and size recommendation.
 * Frontal widths NEVER become circumferences. Manual values win only for identical dimensions.
 */
object BodyDimensions {
    fun resolve(manual: ManualBodyMeasurements?, photo: BodyMeasurements?): BodyDimensionProfile {
        val values = linkedMapOf<BodyDimension, DimensionValue>()
        val notes = mutableListOf<String>()
        val entered = manual?.takeIf { it.isValid() }
        val estimated = photo?.takeIf(BodyMeasurementCalculator::isValid)
        if (manual != null && entered == null) notes += "직접 입력값의 범위를 확인해주세요. 원본 저장값을 자동으로 지우지 않아요."
        if (photo != null && estimated == null) notes += "사진 환산 조건이 불충분해 추정값을 사용하지 않았어요."
        val compatible = entered == null || estimated == null || abs(entered.heightCm - estimated.heightCm) <= .5f
        if (!compatible) notes += "사진 환산 키와 현재 입력 키가 달라 사진 추정값을 섞지 않았어요. 같은 키로 다시 분석해주세요."
        fun put(key: BodyDimension, cm: Float?, origin: MeasurementOrigin, at: Long) {
            if (cm != null && cm.isFinite() && cm > 0f) values[key] = DimensionValue(cm, origin, at)
        }
        if (estimated != null && compatible) {
            put(BodyDimension.HEIGHT, estimated.heightCm, MeasurementOrigin.USER_ENTERED, estimated.measuredAt)
            listOf(BodyDimension.SHOULDER_WIDTH to estimated.shoulderWidthCm,
                BodyDimension.WAIST_FRONT_WIDTH to estimated.waistWidthCm,
                BodyDimension.HIP_FRONT_WIDTH to estimated.hipWidthCm,
                BodyDimension.LEG_LENGTH to estimated.legLengthCm,
                BodyDimension.TORSO_LENGTH to estimated.torsoLengthCm).forEach { (key, cm) ->
                put(key,cm,MeasurementOrigin.PHOTO_ESTIMATE,estimated.measuredAt)
            }
            notes += "정면 사진의 너비는 의복·각도·윤곽의 영향을 받아요. 가슴·허리·엉덩이 둘레를 사진에서 확정하지 않아요."
        }
        val comparisons = mutableListOf<DimensionComparison>()
        if (entered != null) {
            listOf(BodyDimension.HEIGHT to entered.heightCm,
                BodyDimension.SHOULDER_WIDTH to entered.shoulderWidthCm,
                BodyDimension.CHEST_CIRCUMFERENCE to entered.chestCircumferenceCm,
                BodyDimension.WAIST_CIRCUMFERENCE to entered.waistCircumferenceCm,
                BodyDimension.HIP_CIRCUMFERENCE to entered.hipCircumferenceCm,
                BodyDimension.LEG_LENGTH to entered.legLengthCm,
                BodyDimension.TORSO_LENGTH to entered.torsoLengthCm).forEach { (key, cm) ->
                val prior = values[key]
                if (cm != null && prior?.origin == MeasurementOrigin.PHOTO_ESTIMATE)
                    comparisons += DimensionComparison(key,cm,prior.cm)
                put(key,cm,MeasurementOrigin.USER_ENTERED,entered.enteredAt)
            }
        }
        calibrationFactor(comparisons)?.let { f ->
            // 직접 잰 어깨 · 다리 · 상체 길이와 사진 추정값의 비율로, 직접 재지 않은 정면 너비도 같은 비율로 보정한다.
            listOf(BodyDimension.WAIST_FRONT_WIDTH, BodyDimension.HIP_FRONT_WIDTH).forEach { key ->
                values[key]?.takeIf { it.origin == MeasurementOrigin.PHOTO_ESTIMATE }?.let { v ->
                    values[key] = v.copy(cm = v.cm * f)
                }
            }
            notes += "직접 입력한 치수로 사진 추정값을 보정했어요 (×${"%.2f".format(f)})."
        }
        return BodyDimensionProfile(values,comparisons,notes)
    }

    /** 직접 입력값 / 사진 추정값 비율의 중앙값 (0.85~1.15로 제한). 차이가 1% 미만이거나 비교값이 없으면 null */
    fun calibrationFactor(comparisons: List<DimensionComparison>): Float? {
        val ratios = comparisons
            .filter { it.dimension in setOf(BodyDimension.SHOULDER_WIDTH, BodyDimension.LEG_LENGTH, BodyDimension.TORSO_LENGTH) }
            .filter { it.enteredCm.isFinite() && it.photoCm.isFinite() && it.enteredCm > 0f && it.photoCm > 0f }
            .map { it.enteredCm / it.photoCm }
            .sorted()
        if (ratios.isEmpty()) return null
        val median = if (ratios.size % 2 == 1) ratios[ratios.size / 2]
            else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2f
        val f = median.coerceIn(0.85f, 1.15f)
        return f.takeIf { abs(it - 1f) >= 0.01f }
    }
}

data class MeasurementDifferenceReport(val count: Int, val meanAbsoluteDifferenceCm: Double)
object MeasurementEvaluation {
    /** Paired measurements only. A difference from user input is not a population accuracy score. */
    fun summarize(pairs: List<DimensionComparison>): MeasurementDifferenceReport? {
        val valid = pairs.filter { it.enteredCm.isFinite() && it.photoCm.isFinite() && it.enteredCm > 0 && it.photoCm > 0 }
        return if (valid.isEmpty()) null else MeasurementDifferenceReport(valid.size,
            valid.map { abs(it.differenceCm).toDouble() }.average())
    }
}
