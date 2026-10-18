package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer

/**
 * 기온·비·계절 규칙 (계획 4 — 추천).
 *
 * 온도 구간은 WeatherRepository의 advice() 문구와 맞췄다.
 *  23도 이상: 반팔·반바지 가능, 아우터 없음
 *  20~22도: 긴팔 또는 얇은 아우터(가디건·자켓)
 *  12~19도: 아우터 필요 (자켓·가디건·점퍼)
 *  5~11도: 코트·니트 우선
 *  5도 미만: 패딩·코트 우선
 *  비·눈: 밝은 하의·흰 신발 감점, 샌들 제외
 */
object WeatherRules {

    /**
     * 오늘 조건.
     * @param temp 기온(도). 날씨를 못 받았으면 null → 계절 규칙만 쓴다
     * @param season 지금 계절 (봄/여름/가을/겨울)
     */
    data class Conditions(val temp: Int?, val rainy: Boolean, val season: String)

    enum class OuterNeed { NONE, OPTIONAL, REQUIRED }

    fun outerNeed(c: Conditions): OuterNeed = when {
        c.temp == null -> when (c.season) {
            "여름" -> OuterNeed.NONE
            "겨울" -> OuterNeed.REQUIRED
            else -> OuterNeed.OPTIONAL
        }
        c.temp >= 23 -> OuterNeed.NONE
        c.temp >= 20 -> OuterNeed.OPTIONAL
        else -> OuterNeed.REQUIRED
    }

    private val shortSleeves = setOf("반팔 상의", "반팔 아우터", "반팔 원피스")
    private val sleeveless = setOf("민소매", "민소매 원피스")

    /** 아우터 종류 — 세부 종류가 없으면 반팔 아우터 / 자켓으로 본다 */
    private fun outerKind(item: WardrobeItem): String =
        item.detailType ?: if (item.subCategory == "반팔 아우터") "반팔 아우터" else "자켓"

    /** 오늘 입을 수 있는 옷인지 (false면 코디 후보에서 뺀다) */
    fun allows(item: WardrobeItem, c: Conditions): Boolean {
        if (!seasonOk(item, c)) return false
        if (c.rainy && item.subCategory == "샌들") return false
        val t = c.temp ?: return true
        return when {
            item.category == "아우터" -> when (outerKind(item)) {
                "패딩" -> t <= 11
                "코트" -> t <= 16
                "가디건" -> t in 12..22
                "반팔 아우터" -> t >= 20
                else -> t in 5..22          // 자켓·점퍼
            }
            item.subCategory in sleeveless -> t >= 23
            item.subCategory in shortSleeves -> t >= 17
            item.subCategory == "반바지" || item.subCategory == "샌들" -> t >= 20
            item.detailType == "니트" -> t <= 24
            item.subCategory == "부츠" -> t <= 19
            else -> true
        }
    }

    /**
     * 계절 태그가 지금 계절과 맞는지.
     * 계절이 안 맞아도 오늘 기온이 그 옷에 맞으면 허용한다. (예: 9월인데 25도면 여름 옷도 가능)
     */
    private fun seasonOk(item: WardrobeItem, c: Conditions): Boolean {
        if (WearAnalyzer.inSeason(item.season, c.season)) return true
        val t = c.temp ?: return false
        return when (item.season) {
            "여름" -> t >= 23
            "겨울" -> t <= 11
            "봄·가을" -> t in 12..22
            else -> true
        }
    }

    /** 옷 하나의 날씨 점수 (입을 수는 있지만 더 잘 맞는/덜 맞는 옷) */
    fun itemScore(item: WardrobeItem, c: Conditions): Double {
        var score = 0.0
        val t = c.temp
        if (t != null) {
            if (item.category == "아우터") {
                val best = when (outerKind(item)) {
                    "패딩" -> t < 5
                    "코트" -> t <= 11
                    "가디건" -> t in 17..22
                    "반팔 아우터" -> t >= 20
                    else -> t in 12..19      // 자켓·점퍼
                }
                if (best) score += 1.5
            }
            if (item.subCategory in shortSleeves && t < 23) score -= 1.0   // 17~22도 반팔은 조금 서늘
            if (item.subCategory in shortSleeves && t >= 23) score += 0.5
            if (item.subCategory == "긴팔 상의" && t >= 25) score -= 1.0
            if (item.detailType == "니트" && t <= 11) score += 1.0
            if (item.detailType == "미니스커트" && t <= 11) score -= 1.5
        }
        if (c.rainy) {
            if (item.category == "하의" && isBright(item)) score -= 1.5
            if (item.category == "신발" && item.colorName == "화이트") score -= 1.0
        }
        return score
    }

    fun isBright(item: WardrobeItem): Boolean =
        item.colorName == "화이트" || item.colorName == "베이지" || ColorHarmony.hsv(item.colorValue).v > 0.75f

    /** 코디 전체에 붙일 날씨 이유 한 줄 (없으면 null) */
    fun reason(main: WardrobeItem, bottom: WardrobeItem?, outer: WardrobeItem?, c: Conditions): String? {
        val t = c.temp
        return when {
            c.rainy && bottom != null && !isBright(bottom) -> "비 소식이 있어 어두운 하의를 골랐어요"
            t == null -> null
            outer != null && t < 23 -> "${t}도 날씨라 ${josa(outer.name, "을", "를")} 더했어요"
            main.detailType == "니트" && t <= 11 -> "쌀쌀해서 따뜻한 니트를 골랐어요"
            t >= 23 -> "${t}도 날씨라 가볍게 입을 수 있게 골랐어요"
            else -> null
        }
    }
}
