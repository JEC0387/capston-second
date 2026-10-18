package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/** Brand/category chart row. All dimensions are garment measurements, not body measurements. */
data class GarmentSizeRow(
    val brand: String,
    val category: String,
    val sizeLabel: String,
    val shoulderCm: Float?,
    val measureType: String,
    val sourceUrl: String = ""
)

data class ShoulderSizeSuggestion(
    val brand: String,
    val category: String,
    val sizeLabel: String,
    val garmentShoulderCm: Float,
    val shoulderEaseCm: Float,
    val expectedShoulderFit: String,
    val sourceUrl: String
)

object BodySizeRecommendation {
    // Conservative prototype rule, not a brand-validated fit standard.
    private const val MIN_SHOULDER_EASE_CM = 2f
    /**
     * Only compares frontal body shoulder width with garment shoulder flat width.
     * Waist/hip frontal widths are not circumferences or garment flat measurements.
     * This is a shoulder-only estimate; chest and full garment fit remain unknown.
     */
    fun suggest(body: BodyMeasurements, rows: List<GarmentSizeRow>): List<ShoulderSizeSuggestion> {
        val shoulder = body.shoulderWidthCm
        // This recommendation uses only shoulder flat width. Do not infer circumference from waist/hip.
        if (!com.jeiu.fashionproject.ui.viewmodel.BodyMeasurementCalculator.validHeight(body.heightCm) ||
            !shoulder.isFinite() || shoulder !in 15f..75f || shoulder/body.heightCm !in .12f.. .40f ||
            !body.cmPerPixel.isFinite() || body.cmPerPixel<=0f || !body.measuredBodyHeightPx.isFinite() ||
            body.measuredBodyHeightPx<200f || body.measuredAt<=0L ||
            kotlin.math.abs(body.cmPerPixel*body.measuredBodyHeightPx-body.heightCm)>body.heightCm*.0001f) return emptyList()
        return rows.asSequence()
            .filter { it.category == "상의" || it.category == "아우터" }
            .filter { it.measureType == "단면" && it.brand.isNotBlank() && it.sizeLabel.isNotBlank() }
            .filter { it.shoulderCm?.let { cm -> cm.isFinite() && cm - shoulder >= MIN_SHOULDER_EASE_CM } == true }
            .groupBy { it.brand.trim() to it.category }
            .mapNotNull { (key, candidates) ->
                val best = candidates.minByOrNull { it.shoulderCm!! } ?: return@mapNotNull null
                val garmentShoulder = best.shoulderCm!!
                val ease = garmentShoulder - shoulder
                ShoulderSizeSuggestion(
                    brand = key.first,
                    category = key.second,
                    sizeLabel = best.sizeLabel,
                    garmentShoulderCm = garmentShoulder,
                    shoulderEaseCm = ease,
                    expectedShoulderFit = when {
                        ease <= 5f -> "어깨에 약간 여유가 있을 수 있어요"
                        else -> "어깨가 넉넉할 수 있어요"
                    },
                    sourceUrl = best.sourceUrl
                )
            }
            .sortedWith(compareBy({ it.brand }, { it.category }))
    }
}
