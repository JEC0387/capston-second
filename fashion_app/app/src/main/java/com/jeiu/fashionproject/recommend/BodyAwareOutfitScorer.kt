package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/** 기존 추천의 보조 가산점. 실측 핏 정보가 없으므로 명시된 하의 길이 분류만 참고한다. */
object BodyAwareOutfitScorer {
    const val MAX_BONUS = 0.25
    data class Adjustment(val score: Double, val reason: String? = null)

    fun evaluate(profile: BodyProfile?, items: List<WardrobeItem>): Adjustment {
        if (profile == null || !profile.torsoToLegRatio.isFinite() ||
            !profile.legToHeightRatio.isFinite() || profile.torsoToLegRatio <= 0.0
        ) return Adjustment(0.0)
        val bottom = items.firstOrNull { it.category == "하의" } ?: return Adjustment(0.0)
        val shortLength = bottom.subCategory == "반바지" ||
            (bottom.subCategory == "치마" && bottom.detailType == "미니스커트")
        val longLength = bottom.subCategory == "긴바지" ||
            (bottom.subCategory == "치마" && bottom.detailType == "롱스커트")
        // 시연용 약한 우선순위 규칙이며 체형의 좋고 나쁨이나 실제 착용 핏을 판단하지 않는다.
        val favored = (profile.torsoToLegRatio >= 0.70 && shortLength) ||
            (profile.torsoToLegRatio <= 0.60 && longLength)
        return if (favored) Adjustment(
            MAX_BONUS,
            "체형 분석 반영: 사진에서 추정한 상하체 길이 비율과 하의 길이 종류를 함께 고려했어요"
        ) else Adjustment(0.0)
    }
}
