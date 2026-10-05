package com.jeiu.fashionproject.ui.viewmodel

import com.jeiu.fashionproject.ui.camera.GuidePoint
import com.jeiu.fashionproject.ui.camera.Joint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure Kotlin mask geometry. Confidence channel 0 is 'selfie' in the bundled single-channel model. */
object SilhouetteMeasurer {
    fun measure(
        confidence: FloatArray, maskWidth: Int, maskHeight: Int,
        imageWidth: Int, imageHeight: Int, points: Map<Joint, GuidePoint>
    ): BodyWidths? {
        if (maskWidth < 32 || maskHeight < 32 || maskWidth.toLong() * maskHeight != confidence.size.toLong() ||
            imageWidth <= 0 || imageHeight <= 0 || confidence.any { !it.isFinite() || it !in 0f..1f }) return null
        if (Joint.values().any { j -> points[j]?.let {
                it.x.isFinite() && it.y.isFinite() && it.confidence.isFinite() &&
                    it.x in 0f..imageWidth.toFloat() && it.y in 0f..imageHeight.toFloat()
            } != true }) return null
        fun p(j: Joint) = points.getValue(j)
        val sx = imageWidth.toFloat() / maskWidth
        val sy = imageHeight.toFloat() / maskHeight
        val ls = p(Joint.LEFT_SHOULDER); val rs = p(Joint.RIGHT_SHOULDER)
        val lh = p(Joint.LEFT_HIP); val rh = p(Joint.RIGHT_HIP)
        val shoulderY = (ls.y + rs.y) / 2f
        val hipY = (lh.y + rh.y) / 2f
        val torso = hipY - shoulderY
        if (torso <= 0f) return null
        val centerX = (ls.x + rs.x + lh.x + rh.x) / 4f
        val seedX = (centerX / sx).toInt().coerceIn(0, maskWidth - 1)
        val seedY = ((shoulderY + torso * 0.5f) / sy).toInt().coerceIn(0, maskHeight - 1)
        val foreground = BooleanArray(confidence.size) { confidence[it] >= 0.7f }
        val seed = seedY * maskWidth + seedX
        if (!foreground[seed]) return null
        val component = BooleanArray(confidence.size)
        val queue = IntArray(confidence.size)
        var read = 0; var count = 1
        queue[0] = seed; component[seed] = true
        var minX = seedX; var maxX = seedX; var minY = seedY; var maxY = seedY
        fun visit(index: Int) {
            if (foreground[index] && !component[index]) {
                component[index] = true; queue[count++] = index
            }
        }
        while (read < count) {
            val i = queue[read++]; val x = i % maskWidth; val y = i / maskWidth
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
            if (x > 0) visit(i - 1)
            if (x + 1 < maskWidth) visit(i + 1)
            if (y > 0) visit(i - maskWidth)
            if (y + 1 < maskHeight) visit(i + maskWidth)
        }
        // A central contiguous run excludes separate arm/hand runs, unlike row min/max.
        fun bandWidth(imageY: Float): Float? {
            val widths = listOf(-0.01f, -0.005f, 0f, 0.005f, 0.01f).mapNotNull { offset ->
                val y = ((imageY + imageHeight * offset) / sy).roundToInt()
                if (y !in 0 until maskHeight || !component[y * maskWidth + seedX]) null else {
                    var left = seedX; var right = seedX
                    while (left > 0 && component[y * maskWidth + left - 1]) left--
                    while (right + 1 < maskWidth && component[y * maskWidth + right + 1]) right++
                    (right - left + 1) * sx
                }
            }
            if (widths.size < 3 || widths.min() <= 0 || widths.max() / widths.min() > 1.35f) return null
            return widths.average().toFloat()
        }
        val shoulder = bandWidth(shoulderY) ?: return null
        val waist = bandWidth(shoulderY + torso * 0.6f) ?: return null
        val hip = bandWidth(hipY) ?: return null
        val top = minY * sy; val bottom = (maxY + 1) * sy
        val bodyHeight = bottom - top
        val shoulderSpan = abs(ls.x - rs.x)
        val hipSpan = abs(lh.x - rh.x)
        val nose = p(Joint.NOSE)
        val feetY = maxOf(p(Joint.LEFT_FOOT).y, p(Joint.RIGHT_FOOT).y)
        val ankleY = (p(Joint.LEFT_ANKLE).y + p(Joint.RIGHT_ANKLE).y) / 2f
        val marginX = max(2, (maskWidth * 0.01f).toInt())
        val marginY = max(2, (maskHeight * 0.01f).toInt())
        fun covered(point: GuidePoint): Boolean {
            val x = (point.x / sx).toInt(); val y = (point.y / sy).toInt()
            return (-2..2).any { dy -> (-2..2).any { dx ->
                val xx = x + dx; val yy = y + dy
                xx in 0 until maskWidth && yy in 0 until maskHeight && component[yy * maskWidth + xx]
            } }
        }
        val keyJoints = listOf(Joint.NOSE, Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER,
            Joint.LEFT_HIP, Joint.RIGHT_HIP, Joint.LEFT_KNEE, Joint.RIGHT_KNEE,
            Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE, Joint.LEFT_FOOT, Joint.RIGHT_FOOT)
        fun armClear(s: GuidePoint, h: GuidePoint, arm: GuidePoint): Boolean {
            if (arm.y < s.y || arm.y > hipY + torso * 0.6f) return false
            val t = ((arm.y - s.y) / (h.y - s.y).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val direction = if (s.x < centerX) -1f else 1f
            return (arm.x - (s.x + (h.x - s.x) * t)) * direction >= shoulderSpan * 0.12f
        }
        // Height calibration assumes straight standing legs, not a crouch with shortened projection.
        fun straightLeg(hipPoint: GuidePoint, knee: GuidePoint, ankle: GuidePoint): Boolean {
            val length = ankle.y - hipPoint.y
            if (length <= 0f) return false
            val t = (knee.y - hipPoint.y) / length
            val lineX = hipPoint.x + (ankle.x - hipPoint.x) * t
            return t in 0.25f..0.75f && abs(knee.x - lineX) <= torso * 0.12f &&
                abs(ankle.x - hipPoint.x) <= torso * 0.35f
        }
        val reliable = points.values.all { it.confidence in 0.75f..1f } &&
            count >= confidence.size * 0.03f && foreground.count { it } - count <= count * 0.05f &&
            minX >= marginX && maxX < maskWidth - marginX && minY >= marginY && maxY < maskHeight - marginY &&
            bodyHeight >= imageHeight * 0.50f && maxY - minY >= 80 &&
            keyJoints.all { covered(p(it)) } &&
            // The mask crown and soles must agree with pose; no nose-to-ankle height substitution.
            nose.y - top in bodyHeight * 0.025f..bodyHeight * 0.16f &&
            bottom - feetY in -sy * 2f..bodyHeight * 0.05f &&
            shoulderY > nose.y && hipY > shoulderY && ankleY > hipY &&
            listOf(Joint.LEFT_KNEE, Joint.RIGHT_KNEE).all { p(it).y in hipY..ankleY } &&
            straightLeg(lh, p(Joint.LEFT_KNEE), p(Joint.LEFT_ANKLE)) &&
            straightLeg(rh, p(Joint.RIGHT_KNEE), p(Joint.RIGHT_ANKLE)) &&
            shoulderSpan / torso >= 0.55f && hipSpan / torso >= 0.25f &&
            abs(ls.y - rs.y) <= shoulderSpan * 0.20f && abs(lh.y - rh.y) <= shoulderSpan * 0.20f &&
            abs(nose.x - (ls.x + rs.x) / 2f) <= shoulderSpan * 0.25f &&
            abs(p(Joint.LEFT_EYE).x - p(Joint.RIGHT_EYE).x) >= shoulderSpan * 0.12f &&
            abs((ls.x + rs.x - lh.x - rh.x) / 2f) <= torso * 0.15f &&
            shoulder in shoulderSpan * 0.8f..shoulderSpan * 1.5f &&
            armClear(ls, lh, p(Joint.LEFT_ELBOW)) && armClear(ls, lh, p(Joint.LEFT_WRIST)) &&
            armClear(rs, rh, p(Joint.RIGHT_ELBOW)) && armClear(rs, rh, p(Joint.RIGHT_WRIST))
        return BodyWidths(shoulder, waist, hip, bodyHeight, ankleY - hipY, torso, reliable)
    }
}


