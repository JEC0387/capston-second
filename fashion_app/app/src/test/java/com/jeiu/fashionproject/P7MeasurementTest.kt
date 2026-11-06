package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.components.bodyImageRect
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class P7MeasurementTest {
    @Test fun combinedLengthsCannotExceedCalibrationHeight() {
        val invalid=BodyWidths(400f,280f,370f,1700f,1100f,650f,true)
        assertNull(BodyMeasurementCalculator.calculate(170f,invalid,1L))
        assertFalse(BodyMeasurementCalculator.isValid(BodyMeasurements(170f,40f,28f,37f,110f,65f,.1f,1700f,1L)))
    }
    @Test fun invalidGuideCoordinatesCannotBecomeAValidMeasurement() {
        val bad=BodyMeasurementGuides(1000,2000,List(3) {BodyGuideLine("test",Float.NaN,1f,.5f)})
        assertFalse(bad.valid())
        assertNull(BodyMeasurementCalculator.calculate(170f,BodyWidths(400f,280f,370f,1700f,800f,460f,true,bad),1L))
    }
    @Test fun fitImageMappingHandlesLetterboxPortraitLandscapeAndInvalidSizes() {
        val portrait=bodyImageRect(400f,260f,1000,2000)!!
        assertEquals(135f,portrait.left,.001f);assertEquals(265f,portrait.right,.001f)
        assertEquals(0f,portrait.top,.001f);assertEquals(260f,portrait.bottom,.001f)
        val landscape=bodyImageRect(400f,260f,2000,1000)!!
        assertEquals(0f,landscape.left,.001f);assertEquals(30f,landscape.top,.001f)
        assertNull(bodyImageRect(Float.NaN,20f,1000,2000));assertNull(bodyImageRect(400f,0f,1000,2000))
    }
}
