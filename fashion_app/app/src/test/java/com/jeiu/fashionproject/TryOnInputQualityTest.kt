package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test

class TryOnInputQualityTest {
    private fun upper() = mapOf("nose" to TryOnPosePoint(.5f, .15f, .9f),
        "leftShoulder" to TryOnPosePoint(.3f, .3f, .9f), "rightShoulder" to TryOnPosePoint(.7f, .3f, .9f))
    @Test fun upperBodyCanWearTopsButCannotMasqueradeAsFullBody() {
        assertNull(TryOnInputQuality.pose(upper(), TryOnInputRole.PERSON_UPPER))
        assertNotNull(TryOnInputQuality.pose(upper(), TryOnInputRole.PERSON_FULL))
        val full = upper() + mapOf("leftHip" to TryOnPosePoint(.4f,.55f,.9f), "rightHip" to TryOnPosePoint(.6f,.55f,.9f),
            "leftAnkle" to TryOnPosePoint(.4f,.9f,.9f), "rightAnkle" to TryOnPosePoint(.6f,.9f,.9f))
        assertNull(TryOnInputQuality.pose(full, TryOnInputRole.PERSON_FULL))
        assertNotNull(TryOnInputQuality.pose(full + ("leftAnkle" to TryOnPosePoint(.4f,1.1f,.9f)), TryOnInputRole.PERSON_FULL))
    }
    @Test fun occludedTiltedAndNonFiniteLandmarksDoNotReachGeneration() {
        for (point in listOf(TryOnPosePoint(.5f,.3f,.2f), TryOnPosePoint(Float.NaN,.3f,.9f), TryOnPosePoint(.7f,.8f,.9f)))
            assertNotNull(TryOnInputQuality.pose(upper() + ("rightShoulder" to point), TryOnInputRole.PERSON_UPPER))
    }
    @Test fun sizesRejectOversizedOrTinyImagesWithoutChangingAspectRatio() {
        assertNotNull(TryOnInputQuality.dimensions(6000,6000,TryOnInputRole.PERSON_FULL))
        assertNotNull(TryOnInputQuality.dimensions(127,300,TryOnInputRole.GARMENT))
        assertNotNull(TryOnInputQuality.dimensions(300,2000,TryOnInputRole.PERSON_FULL))
        assertNull(TryOnInputQuality.dimensions(864,1296,TryOnInputRole.PERSON_FULL))
        assertNull(TryOnInputQuality.dimensions(1600,1200,TryOnInputRole.GARMENT))
    }
    @Test fun busyBackgroundIsAHintNotAnAutomaticCutout() {
        assertNull(TryOnInputQuality.backgroundWarning(IntArray(64) { -1 }))
        assertNotNull(TryOnInputQuality.backgroundWarning(IntArray(64) { if (it%2==0) 0xff000000.toInt() else -1 }))
    }
}
