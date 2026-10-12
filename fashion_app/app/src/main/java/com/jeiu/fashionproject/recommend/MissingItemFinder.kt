package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BasicItems
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates

/**
 * "이것만 사면 코디 완성" (계획 4 — 5단계).
 *
 * 원리: 후보 옷(BasicItems)을 옷장에 가상으로 하나씩 넣어 보고, 좋은 코디가 몇 개 늘어나는지 센다.
 *  - 좋은 코디 = 코디 엔진 점수(색 · 계절 날씨 · 스타일)가 GOOD_SCORE 이상인 조합
 *  - 코디 개수는 "상의(원피스) + 하의" 짝으로 센다. 신발 · 아우터까지 따로 세면 신발 한 켤레가
 *    기존 코디 전부와 곱해져서 늘 1등이 되기 때문이다.
 *    신발 · 아우터 후보는 계절에 맞는 신발 · 아우터가 없을 때(또는 새 짝을 만들어 줄 때)만 의미가 있다.
 *  - 날씨에 따라 매일 바뀌지 않도록 계절 대표 날씨로 계산한다 (OutfitEngine.seasonConditions)
 * 덤: 어떤 좋은 코디에도 못 들어가는 "외톨이 옷", 상황별(면접·운동 등)로 부족한 옷.
 */
object MissingItemFinder {

    const val GOOD_SCORE = 3.0      // 이 점수 이상이면 "좋은 코디"
    private const val TOP_N = 3
    private const val MAX_SITUATION_ITEMS = 3   // 상황별 부족 아이템은 최대 이만큼 묶어서 찾는다

    /** 추천 아이템 하나 — [gain]개의 좋은 코디가 새로 생기고, [examples]는 그 예시 */
    data class Suggestion(
        val item: WardrobeItem,
        val gain: Int,
        val examples: List<OutfitEngine.Outfit>,
        val shop: BasicItems.ShopCandidate?
    )

    /** 외톨이 옷 — [helper]가 있으면 [helperCount]개의 좋은 코디에 쓸 수 있다 */
    data class Orphan(val item: WardrobeItem, val helper: WardrobeItem?, val helperCount: Int)

    /** 상황별 부족 아이템 — [items]를 사면 [outfitCount]개 코디가 생긴다. 찾지 못하면 items가 비어 있음 */
    data class SituationNeed(val situation: Situation, val items: List<WardrobeItem>, val outfitCount: Int)

    data class Report(
        val goodCount: Int,                     // 지금 옷장의 좋은 코디 개수
        val suggestions: List<Suggestion>,      // TOP 3
        val orphans: List<Orphan>,
        val situationNeeds: List<SituationNeed>,
        val season: String
    )

    fun analyze(wardrobe: List<WardrobeItem>, today: String = WearDates.today()): Report {
        val conditions = OutfitEngine.seasonConditions(today)
        val good = goodOutfits(wardrobe, conditions, null, today)
        val baseLooks = good.map { lookKey(it) }.toSet()
        // 신발 · 아우터를 이미 갖고 있는지 (계절에 맞는 것)
        val owned = setOf("신발", "아우터").filter { cat -> wardrobe.any { it.category == cat && WeatherRules.allows(it, conditions) } }
        val candidates = candidatesFor(wardrobe, conditions)

        // ① 후보를 하나씩 넣어 보고, 새로 생기는 좋은 코디(상의+하의 짝 기준)를 센다
        val added: Map<WardrobeItem, List<OutfitEngine.Outfit>> = candidates.associateWith { c ->
            val withC = goodOutfits(wardrobe + c, conditions, null, today).filter { o -> o.items.any { it.id == c.id } }
            // 신발 · 아우터 후보: 이미 갖고 있으면 새로 생긴 짝만, 없으면 그 신발 · 아우터로 완성되는 코디 전부
            val fresh = if (c.category in owned) withC.filter { lookKey(it) !in baseLooks } else withC
            fresh.sortedByDescending { it.score }.distinctBy { lookKey(it) }
        }
        // 많이 늘어나는 순 — 같은 종류(예: 슬랙스 3개)가 TOP 3를 다 차지하지 않게 종류마다 하나씩
        val suggestions = added.filter { it.value.isNotEmpty() }
            .entries.sortedByDescending { it.value.size }
            .distinctBy { SituationRules.kindOf(it.key) }
            .take(TOP_N)
            .map { (c, outfits) -> Suggestion(c, outfits.size, outfits.take(3), BasicItems.shopOf(c)) }

        // ② 외톨이 옷 — 지금 계절에 입을 수 있는데 좋은 코디에 한 번도 못 들어간 옷 (좋은 코디가 하나라도 있을 때만)
        val usedIds = good.flatMap { o -> o.items.map { it.id } }.toSet()
        val orphans = if (good.isEmpty()) emptyList() else wardrobe
            .filter { it.category in setOf("상의", "하의", "원피스", "신발") && WeatherRules.allows(it, conditions) && it.id !in usedIds }
            .map { orphan ->
                // 이 옷과 함께 좋은 코디를 가장 많이 만드는 후보
                val best = added.entries
                    .map { (c, outfits) -> c to outfits.count { o -> o.items.any { it.id == orphan.id } } }
                    .maxByOrNull { it.second }
                if (best == null || best.second == 0) Orphan(orphan, null, 0) else Orphan(orphan, best.first, best.second)
            }

        // ③ 상황별 부족 아이템 — 그 상황 코디를 하나도 못 만들면(상황별 탭의 "옷이 부족해요"와 같은 기준),
        //    코디가 생길 때까지 후보를 하나씩 더해 본다
        val situationNeeds = Situation.entries.mapNotNull { s -> situationNeed(wardrobe, s, today) }

        return Report(baseLooks.size, suggestions, orphans, situationNeeds, conditions.season)
    }

    /**
     * 이 상황 코디를 하나도 못 만들 때, 무엇을 사면 되는지 (만들 수 있으면 null).
     * 코디가 생길 때까지 후보를 하나씩(최대 3개) 더해 본다. 상황별 탭의 "옷이 부족해요" 카드에서도 쓴다.
     */
    fun situationNeed(wardrobe: List<WardrobeItem>, situation: Situation, today: String = WearDates.today()): SituationNeed? {
        val conditions = OutfitEngine.seasonConditions(today)
        if (OutfitEngine.scoreAll(wardrobe, conditions, situation, today).isNotEmpty()) return null
        val chosen = mutableListOf<WardrobeItem>()
        var count = 0
        while (chosen.size < MAX_SITUATION_ITEMS && count == 0) {
            val pool = candidatesFor(wardrobe + chosen, conditions).filter { SituationRules.allows(it, situation) }
            val (best, bestCount) = pool
                .map { c -> c to OutfitEngine.scoreAll(wardrobe + chosen + c, conditions, situation, today).size }
                // 아직 0개면 상의·하의·신발 중 더 많이 채우는 후보를 먼저
                .maxByOrNull { it.second + coverage(wardrobe + chosen + it.first, situation, conditions) * 0.001 }
                ?: break
            chosen += best
            count = bestCount
        }
        return SituationNeed(situation, if (count > 0) chosen else emptyList(), count)
    }

    /** 코디를 셀 때 쓰는 열쇠 — 상의(원피스) + 하의 짝 (신발 · 아우터 제외) */
    private fun lookKey(o: OutfitEngine.Outfit): String =
        o.items.filter { it.category in setOf("상의", "하의", "원피스") }.joinToString("+") { it.id }

    private fun goodOutfits(
        items: List<WardrobeItem>, conditions: WeatherRules.Conditions, situation: Situation?, today: String
    ): List<OutfitEngine.Outfit> =
        OutfitEngine.scoreAll(items, conditions, situation, today).filter { it.score >= GOOD_SCORE }

    /**
     * 지금 계절에 입을 수 있는 후보 중, 옷장에 이미 비슷한 옷(같은 종류 · 같은 색)이 없는 것.
     * (예: 블랙 슬랙스가 있으면 블랙 슬랙스는 후보에서 뺀다)
     */
    private fun candidatesFor(wardrobe: List<WardrobeItem>, conditions: WeatherRules.Conditions): List<WardrobeItem> {
        val owned = wardrobe.map { SituationRules.kindOf(it) to it.colorName }.toSet()
        return BasicItems.all.filter { c ->
            WeatherRules.allows(c, conditions) &&
                (SituationRules.kindOf(c) to c.colorName) !in owned &&
                wardrobe.none { it.id == c.id }
        }
    }

    /** 상황에 맞는 옷이 몇 가지 대분류(상의·하의·신발 등)를 채우는지 — 코디가 아직 0개일 때 고르는 기준 */
    private fun coverage(items: List<WardrobeItem>, situation: Situation, conditions: WeatherRules.Conditions): Int =
        items.filter { SituationRules.allows(it, situation) && WeatherRules.allows(it, conditions) }
            .map { it.category }.distinct().size
}
