package com.jeiu.fashionproject.ui.viewmodel

import kotlin.math.sqrt

/**
 * 옷장 사진 규칙 — 안드로이드 Bitmap 없이 픽셀 배열만으로 계산해서 단위 테스트할 수 있게 따로 둔다.
 *
 *  1) [plainBackgroundMask] 단색 배경 지우기 — ML Kit 누끼가 실패했을 때의 예비 방법.
 *     사진 테두리 색이 고르면(바닥 · 벽 · 침대 시트처럼) 그 색을 배경으로 보고,
 *     테두리에서부터 비슷한 색으로 이어진 영역만 지운다 (옷 안쪽의 같은 색은 지우지 않음).
 *  2) [layout] 통일된 옷장 사진 — 모든 옷을 같은 크기 정사각형 캔버스의 같은 비율(긴 변 78%)로
 *     가운데에 놓는다. 옷마다 사진 크기 · 여백이 달라 생기는 이질감을 없앤다.
 */
object ClosetPhotoRules {

    /** 통일 캔버스 한 변 (px) */
    const val CANVAS = 900
    /** 옷의 긴 변이 캔버스에서 차지하는 비율 */
    const val FILL = 0.78f

    data class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

    /** 옷(w×h)을 캔버스 가운데에 긴 변이 [FILL]이 되게 놓는 자리. 그림자 자리를 위해 살짝 위로 */
    fun layout(w: Int, h: Int, canvas: Int = CANVAS, fill: Float = FILL): Placement {
        val scale = canvas * fill / maxOf(w, h)
        val sw = w * scale; val sh = h * scale
        return Placement((canvas - sw) / 2f, (canvas - sh) / 2f - canvas * 0.015f, sw, sh)
    }

    private fun dist(a: Int, r: Int, g: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - r
        val dg = ((a shr 8) and 0xFF) - g
        val db = (a and 0xFF) - b
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    /**
     * 단색 배경 마스크. true = 배경(지울 곳).
     * 테두리 색이 고르지 않거나, 지운 뒤 남는 옷 영역이 너무 작거나 크면 null (이 방법으로는 못 지움).
     */
    fun plainBackgroundMask(px: IntArray, w: Int, h: Int): BooleanArray? {
        if (w < 8 || h < 8 || px.size != w * h) return null
        // ① 테두리(2px) 평균 색과 흩어진 정도
        val border = ArrayList<Int>()
        for (x in 0 until w) for (y in listOf(0, 1, h - 2, h - 1)) border += px[y * w + x]
        for (y in 2 until h - 2) for (x in listOf(0, 1, w - 2, w - 1)) border += px[y * w + x]
        val r = border.sumOf { (it shr 16) and 0xFF } / border.size
        val g = border.sumOf { (it shr 8) and 0xFF } / border.size
        val b = border.sumOf { it and 0xFF } / border.size
        val spread = border.sumOf { dist(it, r, g, b) } / border.size
        if (spread > 28.0) return null                       // 무늬 · 여러 물건이 있는 배경
        val threshold = maxOf(26.0, spread * 3.0)

        // ② 테두리에서 시작해 배경색과 비슷한 픽셀로 이어진 곳만 채우기 (4방향)
        val bg = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun push(i: Int) {
            if (!bg[i] && dist(px[i], r, g, b) <= threshold) { bg[i] = true; queue[tail++] = i }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        val fg = bg.count { !it }.toDouble() / bg.size
        return if (fg in 0.04..0.92) bg else null
    }
}
