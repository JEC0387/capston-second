package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.camera.*
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class SilhouetteMeasurerTest {
    private fun mask(): FloatArray {
        val a = FloatArray(200 * 400)
        fun rect(l: Int, t: Int, r: Int, b: Int) { for (y in t until b) for (x in l until r) a[y * 200 + x] = 0.99f }
        rect(85, 40, 115, 80); rect(93, 80, 107, 90) // crown, neck
        rect(65, 90, 135, 150); rect(73, 150, 127, 180); rect(65, 180, 135, 215)
        rect(70, 215, 95, 350); rect(105, 215, 130, 350)
        rect(65, 350, 97, 360); rect(103, 350, 135, 360)
        rect(45, 110, 65, 120); rect(135, 110, 155, 120) // arm connections below shoulder band
        rect(40, 115, 55, 210); rect(145, 115, 160, 210)
        return a
    }
    private fun pose(scale: Float = 5f): Map<Joint, GuidePoint> = mapOf(
        Joint.NOSE to (100f to 65f), Joint.LEFT_EYE to (94f to 60f), Joint.RIGHT_EYE to (106f to 60f),
        Joint.LEFT_SHOULDER to (70f to 105f), Joint.RIGHT_SHOULDER to (130f to 105f),
        Joint.LEFT_ELBOW to (50f to 150f), Joint.RIGHT_ELBOW to (150f to 150f),
        Joint.LEFT_WRIST to (45f to 205f), Joint.RIGHT_WRIST to (155f to 205f),
        Joint.LEFT_HIP to (80f to 200f), Joint.RIGHT_HIP to (120f to 200f),
        Joint.LEFT_KNEE to (82f to 270f), Joint.RIGHT_KNEE to (118f to 270f),
        Joint.LEFT_ANKLE to (82f to 340f), Joint.RIGHT_ANKLE to (118f to 340f),
        Joint.LEFT_FOOT to (80f to 355f), Joint.RIGHT_FOOT to (120f to 355f)
    ).mapValues { GuidePoint(it.value.first * scale, it.value.second * scale, 0.99f) }
    private fun measure(a: FloatArray = mask(), points: Map<Joint, GuidePoint> = pose()) =
        SilhouetteMeasurer.measure(a, 200, 400, 1000, 2000, points)
    private fun cm(p: BodyWidths?) = BodyMeasurementCalculator.calculate(170f, p, 1700000000000L)

    @Test fun measuresCrownToSolesInBitmapPixelsAndExcludesArms() {
        val p = measure()!!
        assertTrue(p.fullBodyReliable)
        assertEquals(1600f, p.bodyHeightPx!!, 0.01f)
        assertEquals(350f, p.shoulder, 0.01f)
        assertEquals(270f, p.waist, 0.01f)
        assertEquals(350f, p.hip, 0.01f)
        assertNotNull(cm(p))
    }
    @Test fun bitmapResolutionDoesNotChangeCm() {
        val smaller = SilhouetteMeasurer.measure(mask(), 200, 400, 500, 1000, pose(2.5f))
        assertEquals(cm(measure())!!.waistWidthCm, cm(smaller)!!.waistWidthCm, 0.001f)
    }
    @Test fun nonSquareMaskUsesSeparateHorizontalAndVerticalScale() {
        val original = mask()
        val wideMask = FloatArray(400 * 400) { original[(it / 400) * 200 + (it % 400) / 2] }
        val p = SilhouetteMeasurer.measure(wideMask, 400, 400, 1000, 2000, pose())!!
        assertEquals(measure()!!.bodyHeightPx!!, p.bodyHeightPx!!, 0.01f)
        assertEquals(measure()!!.shoulder, p.shoulder, 0.01f)
        assertEquals(cm(measure())!!.shoulderWidthCm, cm(p)!!.shoulderWidthCm, 0.001f)
    }
    @Test fun clippedHeadAndFeetRejectCm() {
        val head = mask(); for (y in 0..40) for (x in 95..104) head[y * 200 + x] = 1f
        val foot = mask(); for (y in 359..399) for (x in 75..85) foot[y * 200 + x] = 1f
        assertNull(cm(measure(head)))
        assertNull(cm(measure(foot)))
    }
    @Test fun isolatedNoiseDoesNotBecomeTheBodyTop() {
        val a = mask(); a[0] = 1f
        assertEquals(1600f, measure(a)!!.bodyHeightPx!!, 0.01f)
        assertNotNull(cm(measure(a)))
    }
    @Test fun secondSignificantSilhouetteRejectsCm() {
        val a = mask(); for (y in 30..140) for (x in 5..30) a[y * 200 + x] = 1f
        assertNull(cm(measure(a)))
    }
    @Test fun missingAndLowConfidenceFeetRejectCm() {
        assertNull(cm(measure(points = pose() - Joint.LEFT_FOOT)))
        val low = pose() + (Joint.LEFT_FOOT to pose().getValue(Joint.LEFT_FOOT).copy(confidence = 0.4f))
        assertNull(cm(measure(points = low)))
    }
    @Test fun maskAndLandmarkDisagreementRejectsCm() {
        val a = pose() + (Joint.LEFT_FOOT to GuidePoint(100f, 1775f, 0.99f))
        assertNull(cm(measure(points = a)))
    }
    @Test fun badMaskOrForegroundInterpretationRejectsCm() {
        assertNull(measure(FloatArray(200 * 400)))
        val original = mask()
        assertNull(measure(FloatArray(200 * 400) { 1f - original[it] }))
        val bad = mask(); bad[0] = Float.NaN
        assertNull(measure(bad))
        assertNull(SilhouetteMeasurer.measure(FloatArray(2), 200, 400, 1000, 2000, pose()))
    }
    @Test fun bentLegsRejectHeightCalibration() {
        // Knee still overlaps the other leg mask; rejection must come from posture, not coverage alone.
        val bent = pose() + (Joint.LEFT_KNEE to GuidePoint(550f, 1350f, 0.99f))
        assertNull(cm(measure(points = bent)))
    }
    @Test fun sidePoseAndAttachedArmsRejectCm() {
        val side = pose() + (Joint.LEFT_SHOULDER to GuidePoint(480f, 525f, 0.99f)) +
            (Joint.RIGHT_SHOULDER to GuidePoint(520f, 525f, 0.99f))
        assertNull(cm(measure(points = side)))
        val arm = pose() + (Joint.LEFT_WRIST to GuidePoint(410f, 1025f, 0.99f))
        assertNull(cm(measure(points = arm)))
    }
}


