package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.BodySizeRecommendation
import com.jeiu.fashionproject.recommend.GarmentSizeRow
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import org.junit.Assert.*
import org.junit.Test

class BodyDimensionsTest {
    private val manual = ManualBodyMeasurements(170f,40f,90f,75f,95f,88f,52f,2L)
    private val photo = BodyMeasurements(170f,38f,30f,37f,85f,50f,.5f,340f,1L)
    @Test fun manualAndPhotoRemainDistinctAndCircumferencesAreNeverInvented() {
        val profile = BodyDimensions.resolve(manual,photo)
        assertEquals(40f,profile[BodyDimension.SHOULDER_WIDTH]!!.cm)
        assertEquals(MeasurementOrigin.USER_ENTERED,profile[BodyDimension.SHOULDER_WIDTH]!!.origin)
        assertEquals(75f,profile[BodyDimension.WAIST_CIRCUMFERENCE]!!.cm)
        assertEquals(30f,profile[BodyDimension.WAIST_FRONT_WIDTH]!!.cm)
        assertEquals(MeasurementOrigin.PHOTO_ESTIMATE,profile[BodyDimension.WAIST_FRONT_WIDTH]!!.origin)
        assertNull(BodyDimensions.resolve(null,photo)[BodyDimension.WAIST_CIRCUMFERENCE])
        assertEquals(3,profile.comparisons.size)
    }
    @Test fun incompatibleHeightCannotSilentlyMixMeasurements() {
        val profile=BodyDimensions.resolve(manual.copy(heightCm=180f),photo)
        assertNull(profile[BodyDimension.WAIST_FRONT_WIDTH])
        assertTrue(profile.comparisons.isEmpty())
        assertTrue(profile.notes.any { it.contains("키가 달라") })
        assertEquals(180f,profile[BodyDimension.HEIGHT]!!.cm)
    }
    @Test fun invalidMeasurementsAreRejectedRatherThanClamped() {
        assertTrue(BodyDimensions.resolve(manual.copy(heightCm=Float.NaN),photo.copy(cmPerPixel=-1f)).values.isEmpty())
        assertFalse(manual.copy(heightCm=100f,shoulderWidthCm=75f,legLengthCm=null,torsoLengthCm=null).isValid())
        assertFalse(manual.copy(legLengthCm=110f,torsoLengthCm=67f).isValid())
    }
    @Test fun pairedDifferenceIsMeasuredWithoutInventingAnAccuracyScore() {
        val pairs=BodyDimensions.resolve(manual,photo).comparisons
        val report=MeasurementEvaluation.summarize(pairs)!!
        assertEquals(3,report.count)
        assertEquals(7.0/3,report.meanAbsoluteDifferenceCm,.0001)
        assertNull(MeasurementEvaluation.summarize(emptyList()))
        assertNull(MeasurementEvaluation.summarize(listOf(DimensionComparison(BodyDimension.SHOULDER_WIDTH,Float.NaN,40f))))
    }
    @Test fun sizeSuggestionUsesTheChosenManualShoulderAndKeepsMissingChartsEmpty() {
        val rows=listOf(GarmentSizeRow("known","상의","S",40f,"단면"),GarmentSizeRow("known","상의","M",43f,"단면"))
        assertEquals("S",BodySizeRecommendation.suggest(BodyDimensions.resolve(null,photo),rows).single().sizeLabel)
        assertEquals("M",BodySizeRecommendation.suggest(BodyDimensions.resolve(manual,photo),rows).single().sizeLabel)
        assertTrue(BodySizeRecommendation.suggest(BodyDimensions.resolve(manual,null),emptyList()).isEmpty())
    }

    @Test fun avatarSizeAndOutfitUseTheSameChosenMeasurements() {
        val dimensions=BodyDimensions.resolve(manual,photo)
        val avatar=AvatarParameters.from(dimensions)
        val outfit=com.jeiu.fashionproject.recommend.BodyProfileRepository.current(manual,photo)!!
        assertEquals(dimensions[BodyDimension.LEG_LENGTH]!!.cm,avatar.legCm)
        assertEquals(manual.legLengthCm!!.toDouble()/manual.heightCm,outfit.legToHeightRatio,.00001)
        assertEquals(manual.torsoLengthCm!!.toDouble()/manual.legLengthCm,outfit.torsoToLegRatio,.00001)
    }
    @Test fun missingOrMismatchedCurrentLengthsCannotBorrowOlderPhotoRatios() {
        val incomplete=manual.copy(heightCm=180f,legLengthCm=null,torsoLengthCm=null)
        assertNull(com.jeiu.fashionproject.recommend.BodyProfileRepository.current(incomplete,photo))
        assertNull(com.jeiu.fashionproject.recommend.BodyProfileRepository.current(null,null))
    }
}
