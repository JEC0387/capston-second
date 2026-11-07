package com.jeiu.fashionproject.data

import kotlin.math.abs

enum class TryOnInputRole { PERSON_UPPER, PERSON_FULL, GARMENT }
data class PreparedTryOnImage(val data: String, val warnings: List<String> = emptyList())
data class TryOnPosePoint(val x: Float, val y: Float, val visibility: Float)

/** Conservative input checks, not a prediction of generation quality or identity preservation. */
object TryOnInputQuality {
    fun dimensions(width: Int, height: Int, role: TryOnInputRole): String? = when {
        width <= 0 || height <= 0 || width.toLong() * height > 20_000_000L -> "사진 크기를 확인해주세요. 2천만 화소 이하 사진을 사용해주세요."
        minOf(width, height) < if (role == TryOnInputRole.GARMENT) 128 else 256 -> "사진 해상도가 낮아요. 인물은 짧은 변 256px 이상, 의류는 128px 이상을 선택해주세요."
        width.toFloat() / height !in .25f..4f -> "지나치게 길거나 넓은 사진은 지원하지 않아요. 원래 비율의 사진을 선택해주세요."
        else -> null
    }

    /** Normalized landmarks: require visible legs for bottoms/dresses, allow upper-body tops. */
    fun pose(points: Map<String, TryOnPosePoint>, role: TryOnInputRole): String? {
        if (role == TryOnInputRole.GARMENT) return null
        val required = listOf("nose", "leftShoulder", "rightShoulder") +
            if (role == TryOnInputRole.PERSON_FULL) listOf("leftHip", "rightHip", "leftAnkle", "rightAnkle") else emptyList()
        if (required.any { key -> points[key]?.let { p ->
                p.visibility.isFinite() && p.visibility >= .65f && p.x.isFinite() && p.y.isFinite() &&
                    p.x in .02f.. .98f && p.y in .02f.. .98f } != true })
            return if (role == TryOnInputRole.PERSON_FULL) "하의·원피스에는 얼굴부터 발목까지 보이는 정면 전신 사진이 필요해요. 가려지거나 잘린 부분을 확인해주세요."
            else "얼굴과 양쪽 어깨가 보이는 정면 사진을 선택해주세요. 상의는 상반신 사진도 사용할 수 있어요."
        val left = points.getValue("leftShoulder"); val right = points.getValue("rightShoulder")
        if (abs(left.x - right.x) < .10f || abs(left.y - right.y) > abs(left.x - right.x) * .45f)
            return "몸을 옆으로 돌리거나 크게 기울이지 않은 정면 사진을 선택해주세요."
        return null
    }

    /** Edge diversity is only a background hint; do not crop, erase or infer garment geometry. */
    fun backgroundWarning(edgeColors: IntArray): String? {
        if (edgeColors.isEmpty()) return null
        fun channel(color: Int, shift: Int) = color shr shift and 255
        val variation = listOf(0, 8, 16).sumOf { shift ->
            val values = edgeColors.map { channel(it, shift) }
            (values.max() - values.min()).toDouble()
        } / 3
        return if (variation > 90) "의류 사진의 배경이 복잡할 수 있어요. 옷 전체가 보이는 단순한 배경의 원본을 권장해요. 배경을 임의로 지우거나 옷 비율을 바꾸지 않았어요." else null
    }
}
