package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class BodyMeasurementCalculatorTest {
    private val pixels = BodyWidths(209f, 156f, 198f, 850f, 392f, 250f, true)
    private fun calculate(p: BodyWidths? = pixels, height: Float? = 170f) =
        BodyMeasurementCalculator.calculate(height, p, 1700000000000L)

    @Test fun normalConversionAndTimestamp() {
        val result = calculate()!!
        assertEquals(0.2f, result.cmPerPixel, 0.0001f)
        assertEquals(41.8f, result.shoulderWidthCm, 0.001f)
        assertEquals(31.2f, result.waistWidthCm, 0.001f)
        assertEquals(39.6f, result.hipWidthCm, 0.001f)
        assertEquals(78.4f, result.legLengthCm!!, 0.001f)
        assertEquals(50f, result.torsoLengthCm!!, 0.001f)
        assertEquals(1700000000000L, result.measuredAt)
    }
    @Test fun changedHeightScalesEveryLength() {
        val a = calculate(height = 170f)!!; val b = calculate(height = 159f)!!
        assertEquals(a.cmPerPixel * 159f / 170f, b.cmPerPixel, 0.0001f)
        assertEquals(a.shoulderWidthCm * 159f / 170f, b.shoulderWidthCm, 0.001f)
        assertEquals(a.waistWidthCm * 159f / 170f, b.waistWidthCm, 0.001f)
        assertEquals(a.hipWidthCm * 159f / 170f, b.hipWidthCm, 0.001f)
        assertEquals(a.legLengthCm!! * 159f / 170f, b.legLengthCm!!, 0.001f)
    }
    @Test fun invalidBodyHeightsAreRejected() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 199f).forEach {
            assertNull(calculate(pixels.copy(bodyHeightPx = it)))
        }
        assertNull(calculate(pixels.copy(bodyHeightPx = null)))
    }
    @Test fun invalidWidthsAreRejectedWithoutClamping() {
        listOf(0f, -20f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1f, 10000f).forEach {
            assertNull(calculate(pixels.copy(shoulder = it)))
            assertNull(calculate(pixels.copy(waist = it)))
            assertNull(calculate(pixels.copy(hip = it)))
        }
    }
    @Test fun missingOrUnreliableSegmentationIsNotReplacedByPoseEstimates() {
        assertNull(calculate(null))
        assertNull(calculate(pixels.copy(fullBodyReliable = false)))
        assertNull(calculate(BodyWidths(209f, 156f, 198f)))
    }
    @Test fun invalidUserHeightsAreRejected() {
        listOf(null, 0f, -170f, 99f, 231f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertNull(calculate(height = it))
        }
    }
    @Test fun inputValidationAcceptsOnlySupportedNumericFormat() {
        listOf("", "NaN", "Infinity", "-170", "1e2", "170cm", "170,5", ".", "99", "231", "170.55").forEach {
            assertNull(it, BodyMeasurementCalculator.parseHeight(it))
        }
        assertEquals(159f, BodyMeasurementCalculator.parseHeight("159")!!, 0f)
        assertEquals(170.5f, BodyMeasurementCalculator.parseHeight("170.5")!!, 0f)
        assertEquals(100f, BodyMeasurementCalculator.parseHeight("100")!!, 0f)
        assertEquals(230f, BodyMeasurementCalculator.parseHeight("230")!!, 0f)
        assertTrue(BodyMeasurementCalculator.acceptsHeightInput("170.")) // Editable, not yet valid.
        assertFalse(BodyMeasurementCalculator.acceptsHeightInput("Infinity"))
    }
    @Test fun invalidOptionalLengthsNeverLeakIntoResults() {
        listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY, 1f, 10000f).forEach {
            assertNull(calculate(pixels.copy(legLengthPx = it)))
            assertNull(calculate(pixels.copy(torsoLengthPx = it)))
        }
        assertNull(calculate(pixels.copy(legLengthPx = null))!!.legLengthCm)
    }
    @Test fun proportionalBitmapResizingDoesNotChangeCm() {
        val half = pixels.copy(shoulder = 104.5f, waist = 78f, hip = 99f, bodyHeightPx = 425f,
            legLengthPx = 196f, torsoLengthPx = 125f)
        assertEquals(calculate()!!.shoulderWidthCm, calculate(half)!!.shoulderWidthCm, 0.001f)
    }
    @Test fun overflowAndTinyFiniteValuesAreRejected() {
        assertNull(calculate(pixels.copy(shoulder = Float.MAX_VALUE)))
        assertNull(calculate(pixels.copy(waist = Float.MIN_VALUE)))
        assertNull(calculate(pixels.copy(bodyHeightPx = Float.MAX_VALUE)))
    }
}
