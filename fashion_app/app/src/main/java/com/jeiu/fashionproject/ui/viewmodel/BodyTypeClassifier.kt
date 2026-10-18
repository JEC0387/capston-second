package com.jeiu.fashionproject.ui.viewmodel

import kotlin.math.abs

enum class BodyType(val label: String) {
    INVERTED_TRIANGLE("역삼각형"), TRIANGLE("삼각형"), HOURGLASS("모래시계형"), RECTANGLE("직사각형")
}
enum class ClassificationIssue(val message: String) {
    NO_SILHOUETTE("인물 외곽을 확인하지 못했어요. 배경과 구분되는 정면 전신 사진을 선택해주세요."),
    UNRELIABLE("전신 외곽이나 자세가 불안정해요. 머리·발끝을 모두 넣고 팔을 몸에서 조금 떼주세요."),
    INVALID_WIDTHS("어깨·허리·힙을 일관되게 측정하지 못했어요. 몸에 너무 헐렁하지 않은 옷으로 다시 촬영해주세요."),
    BOUNDARY("분류 경계에 가까워 체형 판정을 보류했어요. 자세와 촬영 조건을 맞춰 다시 촬영해주세요.")
}
data class BodyClassification(val type: BodyType? = null, val issue: ClassificationIssue? = null)

/** Four original heuristic thresholds, NOT a validated anthropometric classifier.
 * A ±0.01 ratio guard band expresses uncertainty; it does not equalize type frequencies. */
object BodyTypeClassifier {
    const val BOUNDARY_MARGIN = 0.01f
    fun classify(widths: BodyWidths?): BodyClassification {
        if (widths == null) return BodyClassification(issue = ClassificationIssue.NO_SILHOUETTE)
        if (!widths.fullBodyReliable) return BodyClassification(issue = ClassificationIssue.UNRELIABLE)
        val s = widths.shoulder; val w = widths.waist; val h = widths.hip
        val height = widths.bodyHeightPx
        if (height == null || !height.isFinite() || height < 200f ||
            listOf(s, w, h).any { !it.isFinite() || it <= 0f || it / height !in 0.08f..0.5f })
            return BodyClassification(issue = ClassificationIssue.INVALID_WIDTHS)
        val sh = s / h; val waist = w / ((s + h) / 2f)
        if (!sh.isFinite() || sh !in 0.5f..1.6f || !waist.isFinite() || waist !in 0.4f..1.35f)
            return BodyClassification(issue = ClassificationIssue.INVALID_WIDTHS)
        fun near(v: Float, boundary: Float) = abs(v - boundary) <= BOUNDARY_MARGIN + 0.000001f
        if (near(sh, 0.92f) || near(sh, 1.08f) || (sh in 0.92f..1.08f && near(waist, 0.80f)))
            return BodyClassification(issue = ClassificationIssue.BOUNDARY)
        return BodyClassification(type = when {
            sh >= 1.08f -> BodyType.INVERTED_TRIANGLE
            sh <= 0.92f -> BodyType.TRIANGLE
            waist <= 0.80f -> BodyType.HOURGLASS
            else -> BodyType.RECTANGLE
        })
    }
}
