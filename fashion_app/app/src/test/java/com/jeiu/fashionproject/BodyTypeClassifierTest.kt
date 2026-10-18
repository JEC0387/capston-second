package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.viewmodel.*
import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class BodyTypeClassifierTest {
    private fun widths(s: Float = 400f, w: Float = 300f, h: Float = 400f) =
        BodyWidths(s, w, h, 1600f, 700f, 450f, true)
    private fun type(s: Float, w: Float, h: Float) = BodyTypeClassifier.classify(widths(s,w,h)).type
    @After fun clean() = BodyAnalysisSessionStore.clear()
    @Test fun fourOriginalTypesRemainReachable() {
        assertEquals(BodyType.INVERTED_TRIANGLE, type(460f, 300f, 400f))
        assertEquals(BodyType.TRIANGLE, type(340f, 300f, 400f))
        assertEquals(BodyType.HOURGLASS, type(400f, 280f, 400f))
        assertEquals(BodyType.RECTANGLE, type(400f, 360f, 400f))
    }
    @Test fun missingSegmentationNeverUsesJointDistances() {
        assertEquals(ClassificationIssue.NO_SILHOUETTE, BodyTypeClassifier.classify(null).issue)
    }
    @Test fun unreliableMaskCannotClassifyEvenPlausibleWidths() {
        assertNull(BodyTypeClassifier.classify(widths().copy(fullBodyReliable=false)).type)
    }
    @Test fun invalidNumbersAndMissingWaistAreRejected() {
        for (bad in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            assertNull(BodyTypeClassifier.classify(widths(s=bad)).type)
            assertNull(BodyTypeClassifier.classify(widths(w=bad)).type)
            assertNull(BodyTypeClassifier.classify(widths(h=bad)).type)
        }
    }
    @Test fun extremeRatiosAndInvalidHeightAreRejectedWithoutClamping() {
        assertNull(BodyTypeClassifier.classify(widths(700f,300f,200f)).type)
        assertNull(BodyTypeClassifier.classify(widths().copy(bodyHeightPx=Float.NaN)).type)
        assertNull(BodyTypeClassifier.classify(widths().copy(bodyHeightPx=null)).type)
    }
    @Test fun bothShoulderHipBoundaryBandsAreUncertain() {
        for (boundary in listOf(.92f,1.08f)) for (delta in listOf(-.009f,0f,.009f)) {
            assertEquals(ClassificationIssue.BOUNDARY,
                BodyTypeClassifier.classify(widths(s=400f*(boundary+delta))).issue)
        }
    }
    @Test fun waistBoundaryIsUncertainAndNotForcedToHourglass() {
        for (waist in listOf(316.4f,320f,323.6f))
            assertEquals(ClassificationIssue.BOUNDARY,BodyTypeClassifier.classify(widths(w=waist)).issue)
    }
    @Test fun smallPerturbationsInsideGuardBandRemainUncertain() {
        val results=(-4..4).map { BodyTypeClassifier.classify(widths(s=432f+it*.2f)) }
        assertTrue(results.all { it.type==null && it.issue==ClassificationIssue.BOUNDARY })
    }
    @Test fun scaleDoesNotChangeClassification() {
        val original=widths()
        val smaller=original.copy(shoulder=200f,waist=150f,hip=200f,bodyHeightPx=800f)
        assertEquals(BodyTypeClassifier.classify(original),BodyTypeClassifier.classify(smaller))
    }
    @Test fun missingOrInvalidMeasurementsNeverAttemptPersistence() {
        var saved=0
        BodyAnalysisCompletion.complete(null,true) { saved++ }
        val invalid=BodyMeasurements(170f,Float.NaN,30f,40f,70f,50f,.5f,340f,1L)
        BodyAnalysisCompletion.complete(invalid,true) { saved++ }
        assertEquals(0,saved)
        assertNull(BodyAnalysisSessionStore.measurements.value)
    }
}
