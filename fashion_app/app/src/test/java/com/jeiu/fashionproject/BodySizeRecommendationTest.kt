package com.jeiu.fashionproject

import com.jeiu.fashionproject.recommend.BodySizeRecommendation
import com.jeiu.fashionproject.recommend.GarmentSizeRow
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySizeRecommendationTest {
    private val body = BodyMeasurements(170f, 42f, 31f, 40f, 78f, 50f, 0.2f, 850f, 1L)

    private fun row(size: String, shoulder: Float?, type: String = "단면", category: String = "상의") =
        GarmentSizeRow("브랜드", category, size, shoulder, type)

    @Test fun selectsSmallestGarmentShoulderWiderThanBody() {
        val result = BodySizeRecommendation.suggest(body, listOf(
            row("L", 48f), row("S", 41f), row("M", 44f)
        ))
        assertEquals(1, result.size)
        assertEquals("M", result.single().sizeLabel)
        assertEquals(2f, result.single().shoulderEaseCm, 0.001f)
    }

    @Test fun excludesCircumferenceBottomsAndInvalidMeasurements() {
        val result = BodySizeRecommendation.suggest(body, listOf(
            row("둘레", 90f, "둘레"), row("하의", 50f, category = "하의"),
            row("결측", null), row("오류", Float.NaN), row("너무작음", 41f)
        ))
        assertTrue(result.isEmpty())
    }

    @Test fun neverUsesWaistOrHipAsCircumference() {
        val changed = body.copy(waistWidthCm = 70f, hipWidthCm = 80f)
        val rows = listOf(row("M", 44f))
        assertEquals(BodySizeRecommendation.suggest(body, rows), BodySizeRecommendation.suggest(changed, rows))
    }

    @Test fun declinesToRecommendWhenShoulderEaseIsBelowPrototypeMinimum() {
        assertTrue(BodySizeRecommendation.suggest(body, listOf(row("S", 43.9f))).isEmpty())
        assertEquals("M", BodySizeRecommendation.suggest(body, listOf(row("M", 44f))).single().sizeLabel)
    }

    @Test fun separatesBrandsAndCategories() {
        val rows = listOf(row("M", 44f), row("L", 47f, category = "아우터"),
            GarmentSizeRow("다른브랜드", "상의", "95", 45f, "단면"))
        assertEquals(3, BodySizeRecommendation.suggest(body, rows).size)
    }
}
