package com.jeiu.fashionproject.ui.camera

import kotlin.math.abs

// Coordinates are pixels in PreviewView, after CameraX rotation/crop transformation.
data class GuidePoint(val x: Float, val y: Float, val confidence: Float)
enum class Joint { NOSE, LEFT_EYE, RIGHT_EYE, LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE, LEFT_FOOT, RIGHT_FOOT }
enum class GuideStatus(val message: String) {
    NO_PERSON("가이드 안에 한 명이 서주세요"),
    FULL_BODY("전신이 화면에 들어오게 해주세요"),
    TOO_CLOSE("조금 뒤로 이동해주세요"), TOO_FAR("조금 앞으로 이동해주세요"),
    CENTER("몸을 가이드 중앙에 맞춰주세요"), FRONT("정면을 바라봐주세요"),
    ARMS("팔을 몸에서 조금 떼주세요"), READY("촬영 준비 완료")
}

object CaptureGuide {
    const val LEFT = 0.12f
    const val RIGHT = 0.88f
    const val TOP = 0.06f
    const val BOTTOM = 0.94f

    fun evaluate(points: Map<Joint, GuidePoint>, width: Float, height: Float): GuideStatus {
        if (points.isEmpty()) return GuideStatus.NO_PERSON
        if (width <= 0 || height <= 0 || Joint.values().any {
                val p = points[it]
                p == null || !p.x.isFinite() || !p.y.isFinite() ||
                    !p.confidence.isFinite() || p.confidence < 0.65f
            }) return GuideStatus.FULL_BODY
        fun p(j: Joint) = points.getValue(j)
        val ls = p(Joint.LEFT_SHOULDER); val rs = p(Joint.RIGHT_SHOULDER)
        val lh = p(Joint.LEFT_HIP); val rh = p(Joint.RIGHT_HIP)
        val shoulder = abs(ls.x - rs.x)
        val torso = (lh.y + rh.y - ls.y - rs.y) / 2f
        if (torso <= 0f || shoulder <= 0f) return GuideStatus.FRONT
        // ML Kit has no crown landmark: reserve extra room above the nose.
        val headTop = p(Joint.NOSE).y - torso * 0.35f
        val feet = maxOf(p(Joint.LEFT_FOOT).y, p(Joint.RIGHT_FOOT).y)
        val span = (feet - headTop) / height
        if (span > 0.86f) return GuideStatus.TOO_CLOSE
        if (points.values.any { it.x < 0 || it.x > width || it.y < 0 || it.y > height } ||
            headTop < 0 || feet > height) return GuideStatus.FULL_BODY
        if (span < 0.60f) return GuideStatus.TOO_FAR
        if (headTop < height * TOP || feet > height * BOTTOM ||
            points.values.any { it.x < width * LEFT || it.x > width * RIGHT } ||
            abs((lh.x + rh.x) / 2f - width / 2f) > width * 0.10f) return GuideStatus.CENTER
        val core = PoseQualityRules.evaluate(points, width, height)
        if (core != GuideStatus.READY) return core
        return GuideStatus.READY
    }
}

/** Main-thread state machine shared by manual and automatic shutter paths. */
class AutoCaptureGate(private val holdMs: Long = 1500, private val maxGapMs: Long = 700) {
    private var readySince: Long? = null
    private var lastFrame: Long? = null
    var locked = false
        private set
    var progress = 0f
        private set

    fun reset() { readySince = null; lastFrame = null; progress = 0f }
    fun update(ready: Boolean, now: Long): Boolean {
        if (locked) return false
        val previous = lastFrame
        if (!ready || (previous != null && (now - previous > maxGapMs || now < previous))) reset()
        lastFrame = now
        if (!ready) return false
        val start = readySince ?: now.also { readySince = it }
        progress = ((now - start).toFloat() / holdMs).coerceIn(0f, 1f)
        return now - start >= holdMs
    }
    fun tryLock(): Boolean {
        if (locked) return false
        locked = true
        reset()
        return true
    }
    fun retry() { locked = false; reset() }
}
