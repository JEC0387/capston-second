package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 선호 스타일 (온보딩 · 마이페이지에서 고르는 8가지) → 코디 추천에 반영.
 *
 * 옷마다 붙는 스타일 태그(StyleTagger: 미니멀 / 캐주얼 / 클래식 / 스트릿 / 빈티지)와는 별개로,
 * 옷의 세부 종류 · 이름 · 색을 보고 "이 옷이 그 스타일에 어울리는지"를 판단한다.
 * 한 옷이 여러 스타일에 맞을 수 있다 (예: 네이비 셔츠 → 댄디 · 포멀).
 * 코디의 옷 중 선호 스타일에 맞는 옷이 많을수록 점수를 더 준다 (최대 [MAX_SCORE]).
 */
object PreferredStyles {

    /** 고를 수 있는 선호 스타일 (서버 data.STYLE_OPTIONS 와 같아야 함) */
    val OPTIONS = listOf("캐주얼", "스트릿", "미니멀", "댄디", "아메카지", "유니크", "스포티", "포멀")

    /** 예전 버전에서 저장된 스타일 → 새 이름 (페미닌은 대응하는 스타일이 없어 뺀다) */
    private val LEGACY = mapOf("클래식" to "포멀", "빈티지" to "아메카지")

    /** 코디 옷이 모두 선호 스타일에 맞을 때 더하는 점수 (취향 스타일 점수 최댓값과 같은 크기) */
    const val MAX_SCORE = 1.5

    /** 저장된 값 정리 — 예전 이름은 새 이름으로 바꾸고, 목록에 없는 값과 중복은 뺀다. 고른 순서는 지킨다. */
    fun normalize(styles: Collection<String>): List<String> =
        styles.map { LEGACY[it] ?: it }.filter { it in OPTIONS }.distinct()

    // 색 묶음 (AddClothingViewModel 의 색 이름 기준)
    private val CALM = setOf("블랙", "화이트", "그레이", "베이지", "네이비", "브라운", "카키", "아이보리")
    private val EARTH = setOf("브라운", "베이지", "카키", "네이비", "블루", "그린", "아이보리")
    private val POINT = setOf("레드", "핑크", "퍼플", "옐로우", "오렌지", "그린")

    private fun WardrobeItem.nameHas(vararg words: String) = words.any { it in name }

    /** 이 옷이 [style] 스타일에 어울리는지 */
    fun matches(item: WardrobeItem, style: String): Boolean {
        val d = item.detailType
        val sub = item.subCategory
        val tag = StyleTagger.styleOf(item)
        val neutral = ColorHarmony.isNeutral(item)
        val calm = item.colorName in CALM || neutral
        return when (style) {
            "캐주얼" -> tag == "캐주얼" ||
                d in setOf("맨투맨", "청바지", "반팔티", "긴팔티", "면바지", "폴로", "스니커즈")
            "스트릿" -> tag == "스트릿" || d in setOf("후드티", "점퍼", "트레이닝팬츠") ||
                item.nameHas("카고", "오버핏", "그래픽", "와이드", "조거")
            "미니멀" -> tag == "미니멀" ||
                (neutral && d in setOf("니트", "반팔티", "긴팔티", "슬랙스", "셔츠", "코트", "로퍼"))
            "댄디" -> calm && (d in setOf("셔츠", "니트", "가디건", "슬랙스", "면바지", "자켓", "코트", "로퍼", "폴로") || sub == "구두")
            "아메카지" -> tag == "빈티지" || sub == "부츠" ||
                item.nameHas("데님", "체크", "코듀로이", "워크", "카고", "플란넬") ||
                (item.colorName in EARTH && d in setOf("청바지", "면바지", "셔츠", "자켓", "점퍼", "가디건"))
            "유니크" -> item.colorName in POINT ||
                item.nameHas("프린트", "그래픽", "스트라이프", "패턴", "레오파드", "체크", "플라워")
            "스포티" -> d in setOf("트레이닝팬츠", "후드티", "운동화", "스니커즈") || sub in setOf("운동화", "반바지") ||
                item.nameHas("트레이닝", "조거", "트랙", "져지", "스포츠", "러닝", "레깅스", "바람막이")
            "포멀" -> tag == "클래식" || sub == "구두" ||
                d in setOf("셔츠", "블라우스", "슬랙스", "자켓", "코트", "로퍼", "구두", "힐")
            else -> false
        }
    }

    /**
     * 코디 평가.
     * @return score: 선호 스타일에 맞는 옷의 비율 × [MAX_SCORE],
     *         style: 코디 옷의 절반 이상이 맞는 선호 스타일 (여럿이면 먼저 고른 스타일) — 추천 이유에 쓴다
     */
    data class Match(val score: Double, val style: String?)

    fun evaluate(items: List<WardrobeItem>, preferred: List<String>): Match {
        if (preferred.isEmpty() || items.isEmpty()) return Match(0.0, null)
        val coverage = items.count { item -> preferred.any { matches(item, it) } }.toDouble() / items.size
        val best = preferred.map { s -> s to items.count { matches(it, s) } }.maxByOrNull { it.second }
        return Match(MAX_SCORE * coverage, best?.takeIf { it.second * 2 >= items.size }?.first)
    }

    // ── 스타일 취향 학습(좋아요/싫어요) 점수 → 코디 반영 ────────────────────────

    /** 예전 이름으로 쌓인 점수(클래식 · 빈티지)를 새 이름으로 합치고, 8가지에 없는 스타일은 뺀다 */
    fun canonicalScores(scores: Map<String, Int>): Map<String, Int> {
        val out = OPTIONS.associateWith { 0 }.toMutableMap()
        scores.forEach { (k, v) -> val key = LEGACY[k] ?: k; if (key in out) out[key] = out.getValue(key) + v }
        return out
    }

    /**
     * 학습한 스타일 점수로 본 코디 취향도 (−1 ~ 1).
     * 옷마다 그 옷이 맞는 스타일들의 점수 평균(맞는 스타일이 없으면 0)을 구하고, 코디 옷들의 평균을 가장 큰 점수로 나눈다.
     */
    fun learnedAffinity(items: List<WardrobeItem>, scores: Map<String, Int>): Double {
        val learned = canonicalScores(scores).filterValues { it != 0 }
        val max = learned.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0
        if (max == 0 || items.isEmpty()) return 0.0
        val perItem = items.map { item ->
            val hits = learned.filterKeys { matches(item, it) }.values
            if (hits.isEmpty()) 0.0 else hits.average()
        }
        return (perItem.average() / max).coerceIn(-1.0, 1.0)
    }

    /** 넓은 스타일(캐주얼 · 미니멀)보다 뚜렷한 스타일을 먼저 — 대표 스타일을 고를 때 동점이면 이 순서 */
    private val SPECIFIC_FIRST = listOf("포멀", "댄디", "스포티", "아메카지", "유니크", "스트릿", "미니멀", "캐주얼")

    /** 코디를 대표하는 스타일 — 옷의 절반 이상이 맞는 스타일 중 가장 많이 맞는 것 (같으면 더 뚜렷한 스타일) */
    fun dominant(items: List<WardrobeItem>): String? {
        if (items.isEmpty()) return null
        return SPECIFIC_FIRST.map { s -> s to items.count { matches(it, s) } }
            .filter { it.second * 2 >= items.size && it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    /** 옷에 붙는 자동 스타일 태그(StyleTagger 5가지) → 8가지 중 가까운 이름 */
    fun fromTag(tag: String?): String? = tag?.let { LEGACY[it] ?: it }?.takeIf { it in OPTIONS }
}
