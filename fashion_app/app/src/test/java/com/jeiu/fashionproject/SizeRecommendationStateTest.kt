package com.jeiu.fashionproject

import com.jeiu.fashionproject.recommend.GarmentSizeRow
import com.jeiu.fashionproject.recommend.SizeRecommendationState
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeRecommendationStateTest {
    private val body = BodyMeasurements(170f, 39f, 31f, 40f, 78f, 50f, 0.2f, 850f, 1L)
    private fun row(label: String, shoulder: Float?, type: String = "단면") =
        GarmentSizeRow("개발용 샘플 (공식 아님)", "상의", label, shoulder, type)

    @Test fun demoChartProducesRecommendation() {
        val state = SizeRecommendationState.from(body, listOf(
            row("S", 38f), row("M", 41f), row("L", 44f), row("XL", 47f)
        ))
        assertTrue(state is SizeRecommendationState.Ready)
        val suggestion = (state as SizeRecommendationState.Ready).suggestions.single()
        assertEquals("M", suggestion.sizeLabel)
        assertEquals(2f, suggestion.shoulderEaseCm, 0.001f)
    }

    @Test fun emptyChartAndUnavailableServerAreDifferentStates() {
        assertEquals(SizeRecommendationState.NoCharts, SizeRecommendationState.from(body, emptyList()))
        assertEquals(SizeRecommendationState.Unavailable, SizeRecommendationState.from(body, null))
    }

    @Test fun absentBodyDoesNotRecommend() {
        assertEquals(SizeRecommendationState.MissingBody,
            SizeRecommendationState.from(null, listOf(row("M", 41f))))
    }

    @Test fun missingOrInvalidShoulderDoesNotCrashOrRecommend() {
        assertEquals(SizeRecommendationState.NoMatch,
            SizeRecommendationState.from(body, listOf(row("S", null), row("M", Float.NaN))))
    }

    @Test fun incompleteChartIdentityIsExcluded() {
        assertEquals(SizeRecommendationState.NoMatch,
            SizeRecommendationState.from(body, listOf(
                GarmentSizeRow("", "상의", "M", 41f, "단면"),
                GarmentSizeRow("개발용 샘플", "", "M", 41f, "단면"),
                GarmentSizeRow("개발용 샘플", "상의", "", 41f, "단면")
            )))
    }

    @Test fun circumferenceRowsAreExcluded() {
        assertEquals(SizeRecommendationState.NoMatch,
            SizeRecommendationState.from(body, listOf(row("M", 80f, "둘레"))))
    }

    @Test fun frontalWaistWidthNeverChangesRecommendation() {
        val rows = listOf(row("M", 41f))
        assertEquals(SizeRecommendationState.from(body, rows),
            SizeRecommendationState.from(body.copy(waistWidthCm = 75f), rows))
    }
}
