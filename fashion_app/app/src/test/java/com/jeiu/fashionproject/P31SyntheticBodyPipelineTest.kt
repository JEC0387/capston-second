package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.BodyProfileAnalyzer
import com.jeiu.fashionproject.ui.camera.GuidePoint
import com.jeiu.fashionproject.ui.camera.Joint
import com.jeiu.fashionproject.ui.components.bodyImageRect
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic mask-to-numbers/guides/session and history-parser contracts only.
 * No detector, camera, URI/EXIF decoder, database write, network or real-person accuracy claim.
 * P18 already covers decoder EXIF cases; this exercises the normalized landscape downstream path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P31SyntheticBodyPipelineTest {
    private val timestamp = 1700000000000L

    // The existing P30 approved synthetic silhouette, expressed in mask pixels.
    private fun mask(): FloatArray {
        val pixels = FloatArray(200 * 400)
        fun rectangle(l: Int, t: Int, r: Int, b: Int) {
            for (y in t until b) for (x in l until r) pixels[y * 200 + x] = .99f
        }
        rectangle(85, 40, 115, 80); rectangle(93, 80, 107, 90)
        rectangle(65, 90, 135, 150); rectangle(73, 150, 127, 180); rectangle(65, 180, 135, 215)
        rectangle(70, 215, 95, 350); rectangle(105, 215, 130, 350)
        rectangle(65, 350, 97, 360); rectangle(103, 350, 135, 360)
        rectangle(45, 110, 65, 120); rectangle(135, 110, 155, 120)
        rectangle(40, 115, 55, 210); rectangle(145, 115, 160, 210)
        return pixels
    }

    private fun pose(offsetX: Float = 0f) = mapOf(
        Joint.NOSE to (100f to 65f), Joint.LEFT_EYE to (94f to 60f), Joint.RIGHT_EYE to (106f to 60f),
        Joint.LEFT_SHOULDER to (70f to 105f), Joint.RIGHT_SHOULDER to (130f to 105f),
        Joint.LEFT_ELBOW to (50f to 150f), Joint.RIGHT_ELBOW to (150f to 150f),
        Joint.LEFT_WRIST to (45f to 205f), Joint.RIGHT_WRIST to (155f to 205f),
        Joint.LEFT_HIP to (80f to 200f), Joint.RIGHT_HIP to (120f to 200f),
        Joint.LEFT_KNEE to (82f to 270f), Joint.RIGHT_KNEE to (118f to 270f),
        Joint.LEFT_ANKLE to (82f to 340f), Joint.RIGHT_ANKLE to (118f to 340f),
        Joint.LEFT_FOOT to (80f to 355f), Joint.RIGHT_FOOT to (120f to 355f)
    ).mapValues { (_, p) -> GuidePoint(p.first * 5f + offsetX, p.second * 5f, .99f) }

    private fun measurement(): BodyMeasurements = requireNotNull(BodyMeasurementCalculator.calculate(
        170f, SilhouetteMeasurer.measure(mask(), 200, 400, 1000, 2000, pose()), timestamp))

    @After fun clearSyntheticSession() = BodyAnalysisSessionStore.clear()

    @Test fun normalizedLandscapeKeepsCalibrationAndFitsGuidesWithVerticalLetterbox() {
        val original = mask()
        // 150 empty pixels on each side: same body in a 2500x2000 landscape analysis image.
        val landscape = FloatArray(500 * 400)
        for (y in 0 until 400) for (x in 0 until 200) landscape[y * 500 + x + 150] = original[y * 200 + x]
        val inspected = SilhouetteMeasurer.inspect(landscape, 500, 400, 2500, 2000, pose(750f))
        assertNull(inspected.issue)
        val widths = requireNotNull(inspected.widths)
        assertEquals(1600f, widths.bodyHeightPx!!, .01f) // (360-40)*5
        assertEquals(350f, widths.shoulder, .01f)       // (135-65)*5
        assertEquals(270f, widths.waist, .01f)          // (127-73)*5
        val numbers = requireNotNull(BodyMeasurementCalculator.calculate(170f, widths, timestamp))
        assertEquals(.10625f, numbers.cmPerPixel, .000001f) // 170/1600
        assertEquals(37.1875f, numbers.shoulderWidthCm, .0001f)
        assertEquals(28.6875f, numbers.waistWidthCm, .0001f)
        assertEquals(37.1875f, numbers.hipWidthCm, .0001f)
        assertEquals(BodyType.HOURGLASS, BodyTypeClassifier.classify(widths).type)
        val guides = requireNotNull(numbers.guides)
        assertEquals(2500, guides.imageWidth); assertEquals(2000, guides.imageHeight)
        val shoulder = guides.lines.first { it.label == "어깨" }
        assertEquals(.43f, shoulder.left, .0001f)  // (150+65)/500
        assertEquals(.57f, shoulder.right, .0001f) // (150+135)/500
        val fitted = requireNotNull(bodyImageRect(400f, 600f, guides.imageWidth, guides.imageHeight))
        assertEquals(0f, fitted.left, .001f); assertEquals(140f, fitted.top, .001f)
        assertEquals(400f, fitted.right, .001f); assertEquals(460f, fitted.bottom, .001f)
        assertEquals(172f, fitted.left + fitted.width * shoulder.left, .001f)
        assertEquals(228f, fitted.left + fitted.width * shoulder.right, .001f)
    }

    @Test fun rejectedSyntheticForegroundCannotReplaceValidSessionOrInvokeSave() {
        val lastValid = measurement()
        BodyAnalysisSessionStore.update(lastValid)
        var saveCalls = 0
        val head = mask().apply { for (y in 0..40) for (x in 95..104) this[y * 200 + x] = .99f }
        val feet = mask().apply { for (y in 359..399) for (x in 75..85) this[y * 200 + x] = .99f }
        val separated = mask().apply { for (y in 30..140) for (x in 5..30) this[y * 200 + x] = .99f }
        val connected = separated.copyOf().apply { for (x in 30..45) this[115 * 200 + x] = .99f }
        for ((label, input) in listOf("head clipped" to head, "feet clipped" to feet,
            "extra region" to separated, "joined extra region" to connected,
            "empty mask" to FloatArray(200 * 400))) {
            val inspected = SilhouetteMeasurer.inspect(input, 200, 400, 1000, 2000, pose())
            assertNotNull(label, inspected.issue); assertNull(label, inspected.widths)
            assertNull(label, BodyTypeClassifier.classify(inspected.widths).type)
            val invalid = BodyMeasurementCalculator.calculate(170f, inspected.widths, timestamp + 1)
            assertNull(label, invalid)
            BodyAnalysisCompletion.complete(invalid, true) { saveCalls++ }
            assertEquals(label, lastValid, BodyAnalysisSessionStore.measurements.value)
        }
        assertEquals(0, saveCalls)
    }

    @Test fun numericHistoryReadContractKeepsFrontalWidthsDistinctFromCircumferences() {
        val numbers = measurement()
        val result = BodyAnalysisResult("모래시계형", 1f, 27f / 35f, 1f, .4375f,
            emptyList(), emptyList(), 99, 1f, 27f / 35f, numbers)
        assertTrue(BodyAnalysisPersistence.mayStore(result))
        // Independent API response fixture: parser/read-model contract, NOT a DB round trip.
        val records = BodyRecords.parse("""{"measurements":[{"id":31,"bodyType":"모래시계형",
            "confidence":99,"shoulderToHip":1,"waistToHip":0.7714285714,"legRatio":0.4375,
            "heightCm":170,"shoulderCm":37.1875,"waistCm":28.6875,"hipCm":37.1875,
            "legCm":74.375,"torsoCm":50.46875,"measuredAt":1700000000000,"isSample":false}]}""")
        assertEquals(1, records.size)
        assertEquals(28.6875f, BodyMetric.WAIST_CM.read(records.single())!!, .0001f)
        assertEquals(.4375, requireNotNull(BodyProfileAnalyzer.latestValid(records)).legToHeightRatio, .000001)
        assertEquals(19.0 / 28.0, BodyProfileAnalyzer.latestValid(records)!!.torsoToLegRatio, .000001)
        assertEquals(BodyProfileAnalyzer.fromMeasurements(numbers), BodyProfileAnalyzer.latestValid(records))
        val entered = ManualBodyMeasurements(170f, null, null, 80f, 100f, null, null, timestamp + 2)
        val dimensions = BodyDimensions.resolve(entered, numbers)
        assertEquals(28.6875f, dimensions[BodyDimension.WAIST_FRONT_WIDTH]!!.cm, .0001f)
        assertEquals(MeasurementOrigin.PHOTO_ESTIMATE, dimensions[BodyDimension.WAIST_FRONT_WIDTH]!!.origin)
        assertEquals(80f, dimensions[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm, .0001f)
        assertEquals(MeasurementOrigin.USER_ENTERED, dimensions[BodyDimension.WAIST_CIRCUMFERENCE]!!.origin)
        assertNull(dimensions[BodyDimension.CHEST_CIRCUMFERENCE])
        assertNull(MeasurementEvaluation.summarize(dimensions.comparisons)) // no paired real measurements
    }
}
