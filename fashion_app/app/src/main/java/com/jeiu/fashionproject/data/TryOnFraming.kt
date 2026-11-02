package com.jeiu.fashionproject.data

import kotlin.math.hypot

/**
 * AI 가상피팅 사진 다듬기 규칙 (안드로이드 없이 계산 — 단위 테스트용).
 *
 *  ① 자세 추정 결과(관절 좌표)로 사람 영역을 찾고, 전신이 2:3 세로 비율로 화면을 채우도록 자른다.
 *     FASHN 모델은 2:3 세로 사진에서 가장 자연스럽게 입히고, 사람이 작게 나온 사진은 옷 디테일이 뭉개진다.
 *  ⑤ 합성 때 얼굴은 원본을 그대로 쓰기 위한 얼굴 타원과 가장자리를 부드럽게 섞는 가중치.
 */
object TryOnFraming {

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** 얼굴 타원 (사진 픽셀 좌표) */
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {
        fun scaled(sx: Float, sy: Float) = Oval(cx * sx, cy * sy, rx * sx, ry * sy)
        fun shifted(dx: Float, dy: Float) = Oval(cx - dx, cy - dy, rx, ry)
    }

    data class P(val x: Float, val y: Float)

    /**
     * 관절 점들로 사람 상자 — 머리 꼭대기는 코에서 어깨까지 거리만큼 위, 옆으로는 팔 · 옷 여유 12%.
     * @param nose 코 (없으면 맨 위 관절에서 키의 10% 위)
     * @param shoulderMidY 양 어깨 가운데 높이 (없으면 null)
     */
    fun bodyBox(points: List<P>, nose: P?, shoulderMidY: Float?): Box? {
        if (points.size < 4) return null
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val tall = maxY - minY
        if (tall <= 0f) return null
        val top = if (nose != null && shoulderMidY != null && shoulderMidY > nose.y)
            minOf(minY, nose.y - (shoulderMidY - nose.y) * 1.1f) else minY - tall * 0.1f
        val side = (maxX - minX) * 0.12f + tall * 0.04f
        return Box(minX - side, top, maxX + side, maxY + tall * 0.04f)
    }

    /**
     * 사람을 2:3 세로 비율로 넉넉히(위아래 8%) 감싸는 자르기 상자.
     * 사진 밖으로 나가면 사진 안으로 밀어 넣고, 그래도 사람이 잘리거나 거의 사진 전체라면 null (자르지 않음).
     */
    fun crop(imageW: Int, imageH: Int, body: Box): Box? {
        var h = body.height * 1.16f
        var w = maxOf(h * 2f / 3f, body.width * 1.1f)
        h = w * 1.5f
        if (w > imageW) { w = imageW.toFloat(); h = minOf(imageH.toFloat(), w * 1.5f) }
        if (h > imageH) { h = imageH.toFloat(); w = minOf(imageW.toFloat(), h / 1.5f) }
        val cx = (body.left + body.right) / 2f
        val cy = (body.top + body.bottom) / 2f
        val l = (cx - w / 2f).coerceIn(0f, imageW - w)
        val t = (cy - h / 2f).coerceIn(0f, imageH - h)
        val box = Box(l, t, l + w, t + h)
        if (w * h > imageW * imageH * 0.9f) return null
        val inside = box.left <= maxOf(body.left, 0f) + 1f && box.right >= minOf(body.right, imageW.toFloat()) - 1f &&
            box.top <= maxOf(body.top, 0f) + 1f && box.bottom >= minOf(body.bottom, imageH.toFloat()) - 1f
        return if (inside) box else null
    }

    /**
     * 얼굴 타원 — 귀 사이(없으면 눈 사이 · 어깨 너비)로 크기를 정한다. 머리카락 · 목은 넣지 않는다.
     */
    fun faceOval(nose: P?, leftEar: P?, rightEar: P?, leftEye: P?, rightEye: P?, shoulderWidth: Float?): Oval? {
        val center = nose ?: if (leftEye != null && rightEye != null) P((leftEye.x + rightEye.x) / 2, (leftEye.y + rightEye.y) / 2) else return null
        val r = when {
            leftEar != null && rightEar != null -> hypot(leftEar.x - rightEar.x, leftEar.y - rightEar.y) * 0.5f
            leftEye != null && rightEye != null -> hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y) * 1.5f
            shoulderWidth != null -> shoulderWidth * 0.17f
            else -> return null
        }
        if (r < 4f) return null
        return Oval(center.x, center.y - r * 0.15f, r * 0.95f, r * 1.25f)
    }

    /** 원본 얼굴을 쓸 비율 (1 = 원본, 0 = AI 결과). 타원 안쪽 55%까지는 원본, 바깥으로 갈수록 부드럽게 AI 결과로 */
    fun faceWeight(x: Float, y: Float, o: Oval): Float {
        val dx = (x - o.cx) / o.rx; val dy = (y - o.cy) / o.ry
        val d = dx * dx + dy * dy
        if (d <= 0.55f) return 1f
        if (d >= 1f) return 0f
        val t = 1f - (d - 0.55f) / 0.45f
        return t * t * (3f - 2f * t)   // smoothstep
    }
}
