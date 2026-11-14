package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.AvatarPart
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarValueOrigin
import com.jeiu.fashionproject.data.BodyDimension
import com.jeiu.fashionproject.data.BodyDimensionProfile
import com.jeiu.fashionproject.data.BodyDimensions
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.MeasurementOrigin
import kotlin.math.abs

internal enum class CircumferenceShape(val label: String) {
    CHEST_DOMINANT("가슴 둘레가 더 큰 체형"),
    HIP_DOMINANT("엉덩이 둘레가 더 큰 체형"),
    DEFINED_WAIST("허리 차이가 뚜렷한 체형"),
    SIMILAR_CIRCUMFERENCES("가슴·엉덩이 둘레가 비슷한 체형")
}

internal data class AvatarBodyResult(
    val measurements: BodyDimensionProfile,
    val shape: CircumferenceShape?,
    val missing: List<BodyDimension>,
    val chestHipDifference: Float?,
    val waistDifferencePercent: Float?
)

internal object AvatarBodyAnalysis {
    // Clothing guidance thresholds, in cm and comparable circumference ratios.
    // Keep a buffer around thresholds where small tape-measure differences matter.
    private const val BALANCE_DIFFERENCE_CM = 8f
    private const val BALANCE_MARGIN_CM = 1f
    private const val WAIST_RATIO = .80f
    private const val WAIST_MARGIN = .02f
    private val required = listOf(BodyDimension.CHEST_CIRCUMFERENCE,
        BodyDimension.WAIST_CIRCUMFERENCE, BodyDimension.HIP_CIRCUMFERENCE)

    fun analyze(profile: AvatarProfile): AvatarBodyResult {
        val measurements = measuredDimensions(profile)
        val missing = required.filter { measurements[it] == null }
        if (missing.isNotEmpty()) return AvatarBodyResult(measurements, null, missing, null, null)
        val chest = checkNotNull(measurements[BodyDimension.CHEST_CIRCUMFERENCE]).cm
        val waist = checkNotNull(measurements[BodyDimension.WAIST_CIRCUMFERENCE]).cm
        val hip = checkNotNull(measurements[BodyDimension.HIP_CIRCUMFERENCE]).cm
        val difference = chest - hip
        val waistRatio = waist / ((chest + hip) / 2f)
        val shape = when {
            abs(abs(difference) - BALANCE_DIFFERENCE_CM) <= BALANCE_MARGIN_CM -> null
            difference > BALANCE_DIFFERENCE_CM -> CircumferenceShape.CHEST_DOMINANT
            difference < -BALANCE_DIFFERENCE_CM -> CircumferenceShape.HIP_DOMINANT
            abs(waistRatio - WAIST_RATIO) <= WAIST_MARGIN + .000001f -> null
            waistRatio < WAIST_RATIO -> CircumferenceShape.DEFINED_WAIST
            else -> CircumferenceShape.SIMILAR_CIRCUMFERENCES
        }
        return AvatarBodyResult(measurements, shape, emptyList(), difference, (1f - waistRatio) * 100f)
    }

    fun measuredDimensions(profile: AvatarProfile): BodyDimensionProfile {
        if (!profile.valid()) return BodyDimensions.resolve(null, null)
        val cm = profile.centimeters
        fun measured(part: AvatarPart, dimension: BodyDimension): Float? {
            val reference = cm?.references?.get(part)
            if (reference != null) return reference.cm.takeIf { reference.origin == AvatarValueOrigin.MANUAL }
            return profile.applied[dimension]?.takeIf { it.origin == MeasurementOrigin.USER_ENTERED }?.cm
        }
        val timestamp = cm?.updatedAt ?: profile.applied.values.maxOfOrNull { it.at } ?: 1L
        val inputs = ManualBodyMeasurements(profile.heightCm,
            measured(AvatarPart.SHOULDER, BodyDimension.SHOULDER_WIDTH),
            measured(AvatarPart.CHEST, BodyDimension.CHEST_CIRCUMFERENCE),
            measured(AvatarPart.WAIST, BodyDimension.WAIST_CIRCUMFERENCE),
            measured(AvatarPart.HIP, BodyDimension.HIP_CIRCUMFERENCE),
            measured(AvatarPart.LEG, BodyDimension.LEG_LENGTH),
            measured(AvatarPart.TORSO, BodyDimension.TORSO_LENGTH), timestamp)
        // Use the same anatomical ranges and measurement definitions as manual entry.
        return BodyDimensions.resolve(inputs.takeIf { it.isValid() }, null)
    }
}
