package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 기존 추천의 보조 가산점.
 * 1) 길이: 상하체 길이 비율과 하의 길이 종류 (최대 MAX_BONUS)
 * 2) 체형: 분석한 체형(역삼각형 · 삼각형 · 모래시계형 · 직사각형)의 코디 원칙에 맞는 옷이면 가산, 피하면 좋은 옷이면 감산
 * 실제 착용 핏을 측정한 결과가 아니며 체형의 좋고 나쁨을 판단하지 않는다.
 */
object BodyAwareOutfitScorer {
    const val MAX_BONUS = 0.25
    const val SHAPE_BONUS = 0.3
    const val SHAPE_PENALTY = -0.2
    data class Adjustment(val score: Double, val reason: String? = null)

    fun evaluate(profile: BodyProfile?, items: List<WardrobeItem>): Adjustment {
        if (profile == null) return Adjustment(0.0)
        val length = lengthPart(profile, items)
        val shape = shapePart(profile, items)
        return Adjustment(length.score + shape.score, shape.reason ?: length.reason)
    }

    private fun lengthPart(profile: BodyProfile, items: List<WardrobeItem>): Adjustment {
        if (!profile.hasLengths) return Adjustment(0.0)
        val bottom = items.firstOrNull { it.category == "하의" } ?: return Adjustment(0.0)
        val shortLength = bottom.subCategory == "반바지" ||
            (bottom.subCategory == "치마" && bottom.detailType == "미니스커트")
        val longLength = bottom.subCategory == "긴바지" ||
            (bottom.subCategory == "치마" && bottom.detailType == "롱스커트")
        val favored = (profile.torsoToLegRatio >= 0.70 && shortLength) ||
            (profile.torsoToLegRatio <= 0.60 && longLength)
        return if (favored) Adjustment(MAX_BONUS, "체형 분석 반영: 상하체 비율에 맞는 하의 길이예요") else Adjustment(0.0)
    }

    private fun shapePart(profile: BodyProfile, items: List<WardrobeItem>): Adjustment {
        val guide = BodyStyleGuide.guide(profile.bodyType, profile.gender) ?: return Adjustment(0.0)
        val fits = items.map { it to BodyStyleGuide.fit(it, guide) }
        if (fits.any { it.second < 0 }) return Adjustment(SHAPE_PENALTY)
        val good = fits.firstOrNull { it.second > 0 }?.first ?: return Adjustment(0.0)
        return Adjustment(SHAPE_BONUS, "체형 분석 반영: ${guide.bodyType}에 잘 맞는 ${good.name}")
    }
}
