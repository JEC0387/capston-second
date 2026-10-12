package com.jeiu.fashionproject

import com.jeiu.fashionproject.ui.camera.*
import org.junit.Assert.*
import org.junit.Test

class CaptureGuideTest {
    private fun body(): Map<Joint, GuidePoint> = mapOf(
        Joint.NOSE to (500f to 180f), Joint.LEFT_EYE to (480f to 165f), Joint.RIGHT_EYE to (520f to 165f),
        Joint.LEFT_SHOULDER to (400f to 270f), Joint.RIGHT_SHOULDER to (600f to 270f),
        Joint.LEFT_ELBOW to (350f to 380f), Joint.RIGHT_ELBOW to (650f to 380f),
        Joint.LEFT_WRIST to (320f to 490f), Joint.RIGHT_WRIST to (680f to 490f),
        Joint.LEFT_HIP to (430f to 520f), Joint.RIGHT_HIP to (570f to 520f),
        Joint.LEFT_KNEE to (435f to 680f), Joint.RIGHT_KNEE to (565f to 680f),
        Joint.LEFT_ANKLE to (440f to 830f), Joint.RIGHT_ANKLE to (560f to 830f),
        Joint.LEFT_FOOT to (425f to 870f), Joint.RIGHT_FOOT to (575f to 870f)
    ).mapValues { GuidePoint(it.value.first, it.value.second, 0.99f) }
    private fun status(points: Map<Joint, GuidePoint>) = CaptureGuide.evaluate(points, 1000f, 1000f)
    @Test fun standingFrontWithArmsApartIsReady() { assertEquals(GuideStatus.READY, status(body())) }
    @Test fun missingPersonAndFeetAreRejected() {
        assertEquals(GuideStatus.NO_PERSON, status(emptyMap()))
        assertEquals(GuideStatus.FULL_BODY, status(body() - Joint.LEFT_FOOT))
        assertEquals(GuideStatus.FULL_BODY, status(body() + (Joint.LEFT_ANKLE to GuidePoint(440f, 830f, 0.4f))))
    }
    @Test fun distanceIsRelativeToPreview() {
        fun scaled(scale: Float) = body().mapValues { (_, p) -> p.copy(x = 500 + (p.x - 500) * scale, y = 500 + (p.y - 500) * scale) }
        assertEquals(GuideStatus.TOO_FAR, status(scaled(0.7f)))
        assertEquals(GuideStatus.TOO_CLOSE, status(scaled(1.2f)))
    }
    @Test fun croppedAndOffCenterBodiesAreRejected() {
        assertEquals(GuideStatus.FULL_BODY, status(body().mapValues { (_, p) -> p.copy(x = p.x + 400) }))
        assertEquals(GuideStatus.CENTER, status(body().mapValues { (_, p) -> p.copy(x = p.x + 150) }))
    }
    @Test fun sideOnPoseAndAttachedArmsAreRejected() {
        val narrow = body().mapValues { (_, p) -> p.copy(x = 500 + (p.x - 500) * 0.4f) }
        assertEquals(GuideStatus.FRONT, status(narrow))
        assertEquals(GuideStatus.ARMS, status(body() + (Joint.LEFT_WRIST to GuidePoint(440f, 490f, 0.99f))))
    }
    @Test fun invalidNumbersNeverPass() {
        assertEquals(GuideStatus.FULL_BODY, status(body() + (Joint.NOSE to GuidePoint(Float.NaN, 180f, 1f))))
    }
    @Test fun singleFrameDoesNotTriggerAndContinuousHoldDoes() {
        val gate = AutoCaptureGate()
        assertFalse(gate.update(true, 0))
        assertFalse(gate.update(true, 500))
        assertFalse(gate.update(true, 1000))
        assertTrue(gate.update(true, 1500))
    }
    @Test fun brokenConditionAndStalledFramesRestartHold() {
        val gate = AutoCaptureGate()
        gate.update(true, 0); gate.update(true, 500); gate.update(false, 1000)
        assertFalse(gate.update(true, 1500))
        assertEquals(0f, gate.progress, 0f)
        assertFalse(gate.update(true, 2300))
        assertEquals(0f, gate.progress, 0f)
    }
    @Test fun manualAndAutoShareOneLockAndFailureCanRetry() {
        val gate = AutoCaptureGate()
        assertTrue(gate.tryLock())
        assertFalse(gate.tryLock())
        assertFalse(gate.update(true, 2000))
        gate.reset() // Lifecycle reset must not unlock a completed/in-flight capture.
        assertFalse(gate.tryLock())
        gate.retry()
        assertFalse(gate.update(true, 3000))
        assertTrue(gate.tryLock())
    }
}
