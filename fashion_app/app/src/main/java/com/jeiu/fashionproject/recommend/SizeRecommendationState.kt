package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

/** Distinguishes a failed request from an empty or unusable chart response. */
sealed interface SizeRecommendationState {
    data object MissingBody : SizeRecommendationState
    data object Unavailable : SizeRecommendationState
    data object NoCharts : SizeRecommendationState
    data object NoMatch : SizeRecommendationState
    data class Ready(val suggestions: List<ShoulderSizeSuggestion>) : SizeRecommendationState

    companion object {
        fun from(body: BodyMeasurements?, rows: List<GarmentSizeRow>?): SizeRecommendationState {
            if (body == null) return MissingBody
            if (rows == null) return Unavailable
            if (rows.isEmpty()) return NoCharts
            val suggestions = BodySizeRecommendation.suggest(body, rows)
            return if (suggestions.isEmpty()) NoMatch else Ready(suggestions)
        }
    }
}
