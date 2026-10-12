package com.jeiu.fashionproject.ui.viewmodel

/** Widths and lengths are all in the SAME orientation-corrected analysis bitmap coordinates. */
data class BodyWidths(
    val shoulder: Float, val waist: Float, val hip: Float,
    val bodyHeightPx: Float? = null,
    val legLengthPx: Float? = null,
    val torsoLengthPx: Float? = null,
    val fullBodyReliable: Boolean = false
)

/** Value object inside BodyAnalysisResult; widths are frontal estimates, never circumferences. */
data class BodyMeasurements(
    val heightCm: Float,
    val shoulderWidthCm: Float,
    val waistWidthCm: Float,
    val hipWidthCm: Float,
    val legLengthCm: Float?,
    val torsoLengthCm: Float?,
    val cmPerPixel: Float,
    val measuredBodyHeightPx: Float,
    val measuredAt: Long // UTC Unix epoch milliseconds, independent of device display timezone.
)

object BodyMeasurementCalculator {
    // Supported range for this adolescent/adult fashion prototype, not a medical normal range.
    // Children/extreme statures need separately validated pose and proportion thresholds.
    const val MIN_HEIGHT_CM = 100f
    const val MAX_HEIGHT_CM = 230f
    private val heightPattern = Regex("[0-9]{1,3}(\\.[0-9])?")
    private val editableHeightPattern = Regex("[0-9]{0,3}(\\.[0-9]?)?")

    fun acceptsHeightInput(text: String) = editableHeightPattern.matches(text)
    fun parseHeight(text: String): Float? = if (heightPattern.matches(text))
        text.toFloatOrNull()?.takeIf(::validHeight) else null
    fun validHeight(value: Float) = value.isFinite() && value in MIN_HEIGHT_CM..MAX_HEIGHT_CM

    fun calculate(heightCm: Float?, pixels: BodyWidths?, measuredAt: Long): BodyMeasurements? {
        if (heightCm == null || !validHeight(heightCm) || pixels == null || !pixels.fullBodyReliable) return null
        val heightPx = pixels.bodyHeightPx ?: return null
        // Below 200 analysis pixels, boundary errors are too large for this prototype.
        if (!heightPx.isFinite() || heightPx < 200f || measuredAt <= 0L) return null
        val scale = heightCm / heightPx
        if (!scale.isFinite() || scale <= 0f) return null
        fun width(px: Float, range: ClosedFloatingPointRange<Float>): Float? {
            if (!px.isFinite() || px <= 0f) return null
            val cm = px * scale
            return cm.takeIf { it.isFinite() && it in range }
        }
        // Broad sanity limits, not anthropometric truth; reject rather than clamp/fabricate.
        val shoulder = width(pixels.shoulder, 15f..75f) ?: return null
        val waist = width(pixels.waist, 12f..80f) ?: return null
        val hip = width(pixels.hip, 15f..85f) ?: return null
        if (shoulder / heightCm !in 0.12f..0.40f || waist / heightCm !in 0.09f..0.45f ||
            hip / heightCm !in 0.12f..0.45f) return null
        fun length(px: Float?, relativeRange: ClosedFloatingPointRange<Float>): Float? {
            if (px == null || !px.isFinite() || px <= 0f) return null
            val cm = px * scale
            return cm.takeIf { it.isFinite() && it / heightCm in relativeRange }
        }
        val leg = length(pixels.legLengthPx, 0.25f..0.65f)
        val torso = length(pixels.torsoLengthPx, 0.15f..0.40f)
        // An explicitly invalid optional value invalidates the result too.
        if ((pixels.legLengthPx != null && leg == null) || (pixels.torsoLengthPx != null && torso == null)) return null
        return BodyMeasurements(heightCm, shoulder, waist, hip, leg, torso, scale, heightPx, measuredAt)
    }
}
