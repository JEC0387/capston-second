package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.pose.PoseLandmark
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic landmark/Task contracts only; no ML Kit inference, user image or device invocation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P36PoseContractTest {
    private fun sample(x: Float, y: Float, confidence: Float = .9f) =
        FittingPoseDetector.LandmarkSample(Offset(x,y), confidence)
    private fun torso() = mutableMapOf(
        PoseLandmark.LEFT_SHOULDER to sample(80f,40f), PoseLandmark.RIGHT_SHOULDER to sample(20f,40f),
        PoseLandmark.LEFT_HIP to sample(75f,100f), PoseLandmark.RIGHT_HIP to sample(25f,100f))

    @Test fun anatomicalSideSurvivesCrossedFeetAndLegacyPairsStaySorted() {
        val landmarks = torso().apply {
            put(PoseLandmark.LEFT_KNEE, sample(60f,130f)); put(PoseLandmark.RIGHT_KNEE, sample(40f,130f))
            put(PoseLandmark.LEFT_ANKLE, sample(35f,170f)); put(PoseLandmark.RIGHT_ANKLE, sample(65f,170f))
            put(PoseLandmark.LEFT_HEEL, sample(33f,173f)); put(PoseLandmark.RIGHT_HEEL, sample(67f,173f))
            put(PoseLandmark.LEFT_FOOT_INDEX, sample(25f,180f)); put(PoseLandmark.RIGHT_FOOT_INDEX, sample(75f,180f))
        }
        val body = requireNotNull(FittingPoseDetector.fromSamples(100,200,landmarks))
        assertEquals(Offset(80f,40f),body.left!!.shoulder)
        assertEquals(Offset(35f,170f),body.left!!.ankle)
        assertEquals(Offset(25f,180f),body.left!!.toe)
        assertEquals(Offset(65f,170f),body.right!!.ankle)
        assertEquals(Offset(75f,180f),body.right!!.toe)
        assertEquals(Offset(20f,40f) to Offset(80f,40f), body.shoulders)
        assertEquals(Offset(35f,170f) to Offset(65f,170f), body.ankles)
    }

    @Test fun missingOrUncertainToeIsNeverFabricatedFromAnkle() {
        val landmarks=torso().apply {
            put(PoseLandmark.LEFT_ANKLE,sample(75f,175f));put(PoseLandmark.RIGHT_ANKLE,sample(25f,175f))
            put(PoseLandmark.RIGHT_FOOT_INDEX,sample(25f,185f,.49f))
        }
        val body=requireNotNull(FittingPoseDetector.fromSamples(100,200,landmarks))
        assertNotNull(body.ankles)
        assertNull(body.left!!.toe); assertNull(body.right!!.toe)
        assertNull(body.left!!.heel); assertNull(body.right!!.heel)
        assertNull(body.faceCenter); assertNull(body.faceWidth)
    }

    @Test fun validationRejectsNonFiniteOutOfFrameAndInvalidConfidence() {
        for (bad in listOf(sample(Float.NaN,20f), sample(20f,Float.POSITIVE_INFINITY),
            sample(-.1f,20f),sample(100f,20f),sample(20f,200f), sample(20f,20f,Float.NaN),
            sample(20f,20f,.499f),sample(20f,20f,1.01f))) {
            assertNull(FittingPoseDetector.validPoint(bad,100,200))
        }
        assertEquals(Offset(0f,0f),FittingPoseDetector.validPoint(sample(0f,0f,.5f),100,200))
        assertNull(FittingPoseDetector.validPoint(sample(20f,20f),0,200))
        assertNull(FittingPoseDetector.fromSamples(0,200,torso()))
    }

    @Test fun collapsedPairAndFaceWidthAreNotPromotedToUsableGeometry() {
        val points=torso().apply {
            put(PoseLandmark.LEFT_HIP,sample(25f,100f))
            put(PoseLandmark.NOSE,sample(50f,15f))
            put(PoseLandmark.LEFT_EAR,sample(1f,15f));put(PoseLandmark.RIGHT_EAR,sample(99f,15f))
        }
        val body=requireNotNull(FittingPoseDetector.fromSamples(100,200,points))
        assertNull(body.hips); assertNull(body.faceWidth)
        assertEquals(Offset(50f,15f),body.faceCenter)
        points[PoseLandmark.LEFT_EAR]=sample(40f,15f); points[PoseLandmark.RIGHT_EAR]=sample(60f,15f)
        assertEquals(20f,requireNotNull(FittingPoseDetector.fromSamples(100,200,points)).faceWidth!!,0f)
    }

    @Test fun horizontalReflectionDoesNotSwapAnatomicalLabels() {
        val points=torso().apply { put(PoseLandmark.LEFT_WRIST,sample(90f,95f));put(PoseLandmark.RIGHT_WRIST,sample(10f,95f)) }
        val reflected=points.mapValues {(_,value)->value.copy(position=Offset(99f-value.position.x,value.position.y))}
        val body=requireNotNull(FittingPoseDetector.fromSamples(100,200,reflected))
        assertEquals(Offset(9f,95f),body.left!!.wrist)
        assertEquals(Offset(89f,95f),body.right!!.wrist)
        assertEquals(Offset(19f,40f) to Offset(79f,40f),body.shoulders)
    }

    @Test fun cancellationDoesNotRecycleInputBeforeNativeCompletionOrPublishLateResult() = runBlocking {
        val native=TaskCompletionSource<String>();val input=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        var releases=0;var published=false
        val job=launch(start=CoroutineStart.UNDISPATCHED) {
            FittingPoseDetector.awaitOwnedNative({native.task},{releases++;input.recycle()})
            published=true
        }
        job.cancel();yield()
        assertFalse(input.isRecycled);assertEquals(0,releases);assertFalse(job.isCompleted)
        native.setResult("synthetic-native-completion")
        job.join()
        assertTrue(input.isRecycled);assertEquals(1,releases);assertFalse(published)
    }

    @Test fun nativeFailureAndSynchronousStartFailureReleaseExactlyOnce() = runBlocking {
        val native=TaskCompletionSource<String>();var releases=0;var result:String?="initial"
        val job=launch(start=CoroutineStart.UNDISPATCHED) {
            result=FittingPoseDetector.awaitOwnedNative({native.task},{releases++})
        }
        native.setException(IllegalArgumentException("synthetic failure"));job.join()
        assertNull(result);assertEquals(1,releases)
        assertNull(FittingPoseDetector.awaitOwnedNative<String>({error("synthetic start failure")},{releases++}))
        assertEquals(2,releases)
    }
}
