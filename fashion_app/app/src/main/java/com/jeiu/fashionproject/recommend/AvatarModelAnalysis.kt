package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.AvatarAnalysisInput
import com.jeiu.fashionproject.data.AvatarCentimeters
import com.jeiu.fashionproject.data.AvatarPart
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.AvatarValueOrigin
import kotlin.math.abs

internal enum class AvatarAnalysisPath { MODEL_ESTIMATE, MEASURED }

internal enum class ModelShape(val label: String) {
    UPPER_EMPHASIS("상체가 강조된 모델"), LOWER_EMPHASIS("하체가 강조된 모델"),
    DEFINED_WAIST("허리선이 뚜렷한 모델"), BALANCED("상·하체가 비슷한 모델")
}

/** Ratios of the displayed model, NOT measurements of a person or a clothing size. */
internal data class AvatarModelResult(
    val shape: ModelShape,
    val chestToHip: Float,
    val waistToTorso: Float,
    val shoulderToHip: Float,
    val legToHeight: Float,
    val usesModelCircumferences: Boolean,
    val thighToHip: Float?
)

internal object AvatarModelAnalysis {
    // Visual descriptions only: 8% torso contrast; 20% waist indentation.
    // These are not the tape-measure classifier's cm thresholds or population norms.
    internal const val FRONT_BALANCE = .08f
    internal const val WAIST_OUTLINE = .80f

    fun analyze(profile: AvatarProfile): AvatarModelResult? {
        if (!profile.valid()) return null
        val model = profile.parameters().takeIf { it.isValid() } ?: return null
        // Current mesh uses its own ellipse circumferences, including depth changes.
        // Legacy/photo-width avatars have only front widths: never infer a circumference.
        val cm = model.centimeters
        val chest = cm?.get(AvatarPart.CHEST) ?: model.chestWidthCm
        val waist = cm?.get(AvatarPart.WAIST) ?: model.waistWidthCm
        val hip = cm?.get(AvatarPart.HIP) ?: model.hipWidthCm
        val chestToHip = chest / hip
        val waistToTorso = waist / ((chest + hip) / 2f)
        val shape = when {
            chestToHip > 1f + FRONT_BALANCE -> ModelShape.UPPER_EMPHASIS
            chestToHip < 1f - FRONT_BALANCE -> ModelShape.LOWER_EMPHASIS
            waistToTorso < WAIST_OUTLINE -> ModelShape.DEFINED_WAIST
            else -> ModelShape.BALANCED
        }
        return AvatarModelResult(shape, chestToHip, waistToTorso,
            model.shoulderCm / model.hipWidthCm, model.legCm / model.heightCm,
            cm != null, cm?.let { it[AvatarPart.THIGH] / it[AvatarPart.HIP] })
    }

    fun path(profile: AvatarProfile?): AvatarAnalysisPath? {
        if (profile == null || !profile.valid()) return null
        val recorded = AvatarAnalysisInput.recorded(profile)
        val visualEdit = profile.centimeters?.values?.any { (part, value) ->
            value.origin == AvatarValueOrigin.ADJUSTED &&
                recorded[part]?.let { abs(AvatarCentimeters.snap(it) - value.cm) > .0001f } != false
        } ?: listOf(profile.shoulder, profile.chest, profile.waist, profile.hip,
            profile.thigh, profile.leg, profile.torso).any { it != 1f }
        return if (!visualEdit && AvatarBodyAnalysis.analyze(profile).missing.isEmpty()) AvatarAnalysisPath.MEASURED
        else if (analyze(profile) != null) AvatarAnalysisPath.MODEL_ESTIMATE else null
    }
}
