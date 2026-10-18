package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/** 코디 상황 (계획 4 — 2단계). 상황을 늘리려면 여기에 하나 추가하고 SituationRules.rule에 규칙을 적는다. */
enum class Situation(val label: String) {
    INTERVIEW("면접"),
    DATE("데이트"),
    TRAVEL("여행"),
    EXERCISE("운동")
}

/**
 * 상황별 규칙표 (계획 4 — 추천).
 *
 * 옷 종류는 가장 구체적인 이름으로 비교한다: 세부 종류(셔츠·스니커즈…)가 있으면 그것, 없으면 소분류(반바지·운동화…).
 *  예) 소분류 "운동화" + 세부 종류 "스니커즈" → "스니커즈"로 본다 (운동용 운동화와 구분)
 */
object SituationRules {

    /**
     * @param preferred 점수를 더 주는 종류
     * @param excluded 빼는 종류
     * @param excludedWords 이름에 이 단어가 있으면 뺀다 (체크·스트라이프 같은 무늬)
     * @param allowedOnly null이 아니면 이 종류만 허용 (운동)
     * @param requiredByCategory 대분류별로 이 종류만 허용 (면접 상의는 셔츠·블라우스만)
     * @param neutralOnly 무채색·뉴트럴 옷만 허용
     * @param pointColorBonus 뉴트럴 + 포인트 색 1개 코디에 더 줄 점수
     * @param tasteMultiplier 내 취향 점수를 몇 배로 반영할지
     * @param layeredBonus 아우터를 겹쳐 입으면 더 줄 점수
     * @param needHints 부족 안내에 쓸 이름 — 대분류 → "셔츠나 블라우스"
     * @param reason 코디 카드 맨 위에 붙일 한 줄
     */
    data class Rule(
        val preferred: Set<String> = emptySet(),
        val excluded: Set<String> = emptySet(),
        val excludedWords: Set<String> = emptySet(),
        val allowedOnly: Set<String>? = null,
        val requiredByCategory: Map<String, Set<String>> = emptyMap(),
        val neutralOnly: Boolean = false,
        val pointColorBonus: Double = 0.0,
        val tasteMultiplier: Double = 1.0,
        val layeredBonus: Double = 0.0,
        val needHints: Map<String, String> = emptyMap(),
        val reason: String
    )

    private const val PREFERRED_SCORE = 1.0
    private const val PREFERRED_MAX = 2.0   // 선호 옷 가산점은 코디당 최대 이만큼

    fun rule(situation: Situation): Rule = when (situation) {
        Situation.INTERVIEW -> Rule(
            preferred = setOf("셔츠", "블라우스", "슬랙스", "자켓", "코트", "로퍼", "구두"),
            excluded = setOf(
                "후드티", "맨투맨", "반팔티", "청바지", "트레이닝팬츠", "반바지", "미니스커트",
                "운동화", "스니커즈", "샌들", "점퍼", "패딩"
            ),
            excludedWords = setOf("체크", "스트라이프", "카고", "데님", "프린트", "그래픽"),
            requiredByCategory = mapOf("상의" to setOf("셔츠", "블라우스")),
            neutralOnly = true,
            needHints = mapOf("상의" to "무늬 없는 셔츠나 블라우스", "하의" to "슬랙스", "신발" to "로퍼나 구두"),
            reason = "면접에 맞게 단정한 무채색으로 맞췄어요"
        )
        Situation.DATE -> Rule(
            preferred = setOf("반팔 원피스", "긴팔 원피스", "민소매 원피스", "니트", "셔츠", "블라우스"),
            excluded = setOf("트레이닝팬츠", "운동화"),
            pointColorBonus = 1.0,
            tasteMultiplier = 2.0,
            reason = "데이트에 어울리는 옷 위주로 골랐어요"
        )
        Situation.TRAVEL -> Rule(
            preferred = setOf("청바지", "면바지", "반팔티", "긴팔티", "맨투맨", "후드티", "니트", "운동화", "스니커즈"),
            excluded = setOf("구두", "힐"),
            layeredBonus = 1.0,
            needHints = mapOf("신발" to "운동화나 스니커즈"),
            reason = "오래 걸어도 편한 옷 위주로 골랐어요"
        )
        Situation.EXERCISE -> Rule(
            // 추운 날을 위해 긴팔티·후드티·점퍼도 허용
            allowedOnly = setOf("반팔티", "민소매", "긴팔티", "후드티", "트레이닝팬츠", "반바지", "운동화", "점퍼"),
            needHints = mapOf("상의" to "반팔티나 긴팔티", "하의" to "트레이닝팬츠나 반바지", "신발" to "운동화"),
            reason = "움직이기 편한 운동복으로 골랐어요"
        )
    }

    /** 옷의 가장 구체적인 종류 이름 */
    fun kindOf(item: WardrobeItem): String = item.detailType ?: item.subCategory ?: item.category

    /** 이 상황에 입을 수 있는 옷인지 (상황이 없으면 항상 true) */
    fun allows(item: WardrobeItem, situation: Situation?): Boolean {
        if (situation == null) return true
        val rule = rule(situation)
        val kind = kindOf(item)
        if (rule.allowedOnly != null && kind !in rule.allowedOnly) return false
        if (kind in rule.excluded) return false
        if (rule.excludedWords.any { it in item.name }) return false
        rule.requiredByCategory[item.category]?.let { if (kind !in it) return false }
        if (rule.neutralOnly && !ColorHarmony.isNeutral(item)) return false
        return true
    }

    /** 상황에 맞는 선호 옷 가산점 + 데이트 포인트 색·여행 레이어드 점수 */
    fun score(all: List<WardrobeItem>, outer: WardrobeItem?, situation: Situation?): Double {
        if (situation == null) return 0.0
        val rule = rule(situation)
        var score = (all.count { kindOf(it) in rule.preferred } * PREFERRED_SCORE).coerceAtMost(PREFERRED_MAX)
        if (all.count { !ColorHarmony.isNeutral(it) } == 1) score += rule.pointColorBonus
        if (outer != null) score += rule.layeredBonus
        return score
    }

    /** 대분류(상의·하의·신발)가 모자랄 때 안내에 쓸 이름 */
    fun hintFor(category: String, situation: Situation?): String =
        situation?.let { rule(it).needHints[category] } ?: category
}
