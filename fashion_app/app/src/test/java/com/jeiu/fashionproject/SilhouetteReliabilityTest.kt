package com.jeiu.fashionproject
import com.jeiu.fashionproject.ui.camera.*
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
class SilhouetteReliabilityTest {
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


    private fun shiftedMask(dx: Int=0, lean:Float=0f):FloatArray {
        val src=mask();val dst=FloatArray(src.size)
        for(y in 0 until 400) for(x in 0 until 200) {
            val xx=x+dx+((y-105)*lean).toInt()
            if(xx in 0 until 200)dst[y*200+xx]=src[y*200+x]
        }
        return dst
    }
    private fun shiftedPose(dx:Int=0, lean:Float=0f)=pose().mapValues { (_,p) ->
        p.copy(x=p.x+dx*5f+((p.y/5f-105)*lean)*5f)
    }
    @Test fun leaningTorsoUsesItsOwnCenterline() {
        val out=measure(shiftedMask(lean=.2f),shiftedPose(lean=.2f))!!
        assertTrue(out.fullBodyReliable)
        assertEquals(350f,out.shoulder,.01f)
        assertEquals(270f,out.waist,.01f)
        assertEquals(350f,out.hip,.01f)
    }
    @Test fun translationDoesNotChangeWidthsOrCm() {
        val a=cm(measure())!!;val b=cm(measure(shiftedMask(20),shiftedPose(20)))!!
        assertEquals(a.shoulderWidthCm,b.shoulderWidthCm,.001f)
        assertEquals(a.waistWidthCm,b.waistWidthCm,.001f)
        assertEquals(a.hipWidthCm,b.hipWidthCm,.001f)
    }
    @Test fun singleWideHipRowDoesNotBecomeTheHipWidth() {
        val a=mask();for(x in 40..160)a[190*200+x]=.99f
        assertEquals(350f,measure(a)!!.hip,.01f)
    }
    @Test fun smallCenterHolesDoNotInvalidateAllWaistRows() {
        val a=mask();a[155*200+100]=0f;a[160*200+100]=0f
        assertEquals(270f,measure(a)!!.waist,.01f)
    }
    @Test fun joinedSleevesAcrossEveryShoulderRowAreRejected() {
        val a=mask();for(y in 90 until 150)for(x in 40 until 160)a[y*200+x]=.99f
        val result=SilhouetteMeasurer.inspect(a,200,400,1000,2000,pose())
        assertNull(result.widths);assertEquals(SilhouetteIssue.ARM_CONTOUR,result.issue)
    }
    @Test fun unstableWaistBandsAreRejected() {
        val a=mask();for(y in 150 until 180){
            for(x in 55..145)a[y*200+x]=0f
            val half=if(y%2==0)10 else 45
            for(x in 100-half until 100+half)a[y*200+x]=.99f
        }
        assertNull(measure(a))
    }
    @Test fun lowerButUsableLandmarkLikelihoodDoesNotAutomaticallyFail() {
        val points=pose().mapValues { (_,p)->p.copy(confidence=.70f) }
        assertNotNull(cm(measure(points=points)))
    }
    @Test fun uncertainForegroundAndInvalidBackgroundAreRejected() {
        val a=mask().map { if(it>0f).55f else 0f }.toFloatArray()
        assertNull(measure(a))
        assertEquals(SilhouetteIssue.INVALID_MASK,
            SilhouetteMeasurer.inspect(FloatArray(1),200,400,1000,2000,pose()).issue)
    }
    @Test fun shoulderAndHipProjectionAreValidatedIndependently() {
        val collapsed=pose()+(Joint.LEFT_HIP to GuidePoint(490f,1000f,.99f))+
            (Joint.RIGHT_HIP to GuidePoint(510f,1000f,.99f))
        assertEquals(GuideStatus.FRONT,PoseQualityRules.evaluate(collapsed,1000f,2000f))
        assertNull(measure(points=collapsed))
    }
    @Test fun cameraAndGalleryShareFrontAndArmsChecks() {
        assertEquals(GuideStatus.READY,PoseQualityRules.evaluate(pose(),1000f,2000f))
        assertEquals(GuideStatus.READY,CaptureGuide.evaluate(pose(),1000f,2000f))
        val arm=pose()+(Joint.LEFT_WRIST to GuidePoint(410f,1025f,.99f))
        assertEquals(GuideStatus.ARMS,PoseQualityRules.evaluate(arm,1000f,2000f))
        assertEquals(GuideStatus.ARMS,CaptureGuide.evaluate(arm,1000f,2000f))
    }
    @Test fun narrowerShoulderJointProjectionDoesNotDefineBodyType() {
        val narrow=pose()+(Joint.LEFT_SHOULDER to GuidePoint(395f,525f,.99f))+
            (Joint.RIGHT_SHOULDER to GuidePoint(605f,525f,.99f))
        assertEquals(GuideStatus.READY,PoseQualityRules.evaluate(narrow,1000f,2000f))
        val a=BodyTypeClassifier.classify(measure());val b=BodyTypeClassifier.classify(measure(points=narrow))
        assertNotNull(a.type);assertEquals(a,b)
    }
    @Test fun repeatedAndMirroredSyntheticMasksRemainConsistent() {
        val original=mask();val mirror=FloatArray(original.size){i->original[(i/200)*200+(199-i%200)]}
        val points=pose().mapValues { (_,p)->p.copy(x=1000f-p.x) }
        val expected=measure()!!
        repeat(5){assertEquals(expected,measure())}
        assertEquals(expected.shoulder,measure(mirror,points)!!.shoulder,.01f)
        assertEquals(expected.hip,measure(mirror,points)!!.hip,.01f)
    }

}
