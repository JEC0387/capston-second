package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.BasicItems
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.data.ShopProduct
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

    /**
     * 상황별 코디 계획 — [outfit]은 지금 옷장(또는 [need] 아이템을 더한 옷장)으로 만든 그 상황 코디,
     * [products]는 그 상황에 맞는 판매 상품 추천 (부족한 종류를 먼저).
     */
    data class SituationPlan(
        val situation: Situation,
        val outfit: OutfitEngine.Outfit?,
        val need: SituationNeed?,
        val products: List<ShopProduct>
    )

    data class Report(
        val goodCount: Int,                     // 지금 옷장의 좋은 코디 개수
        val suggestions: List<Suggestion>,      // TOP 3
        val orphans: List<Orphan>,
        val situationNeeds: List<SituationNeed>,
        val season: String,
        val situationPlans: List<SituationPlan> = emptyList()
    )

    fun analyze(allItems: List<WardrobeItem>, today: String = WearDates.today(), gender: Gender? = null): Report {
        // 성별에 맞지 않는 옷(남성: 원피스 · 치마 등)은 코디 계산과 구매 후보에서 뺀다
        val wardrobe = GenderFit.filter(allItems, gender)
        val conditions = OutfitEngine.seasonConditions(today)
        val good = goodOutfits(wardrobe, conditions, null, today)
        val baseLooks = good.map { lookKey(it) }.toSet()
        // 신발 · 아우터를 이미 갖고 있는지 (계절에 맞는 것)
        val owned = setOf("신발", "아우터").filter { cat -> wardrobe.any { it.category == cat && WeatherRules.allows(it, conditions) } }
        val candidates = candidatesFor(wardrobe, conditions, gender, BasicItems.allWithCatalog)

        // ① 후보를 하나씩 넣어 보고, 새로 생기는 좋은 코디(상의+하의 짝 기준)를 센다
        val added: Map<WardrobeItem, List<OutfitEngine.Outfit>> = candidates.associateWith { c ->
            val withC = goodOutfits(wardrobe + c, conditions, null, today).filter { o -> o.items.any { it.id == c.id } }
            // 신발 · 아우터 후보: 이미 갖고 있으면 새로 생긴 짝만, 없으면 그 신발 · 아우터로 완성되는 코디 전부
            val fresh = if (c.category in owned) withC.filter { lookKey(it) !in baseLooks } else withC
            fresh.sortedByDescending { it.score }.distinctBy { lookKey(it) }
        }
        // 많이 늘어나는 순(같으면 실제 판매 상품 먼저). 다양하게: 먼저 대분류(상의 · 하의 · 아우터 · 신발)마다 하나씩,
        // 모자라면 종류가 겹치지 않게 채운다.
        val suggestions = diverse(added.filter { it.value.isNotEmpty() }.entries
            .sortedWith(compareByDescending<Map.Entry<WardrobeItem, List<OutfitEngine.Outfit>>> { it.value.size }
                .thenBy { if (BasicItems.shopOf(it.key) != null) 0 else 1 })
            .map { it.key to it.value })
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
        val situationNeeds = Situation.entries.mapNotNull { s -> situationNeed(wardrobe, s, today, gender) }
        val plans = Situation.entries.map { s ->
            val need = situationNeeds.find { it.situation == s }
            plan(wardrobe, s, need, conditions, today, gender)
        }

        return Report(baseLooks.size, suggestions, orphans, situationNeeds, conditions.season, plans)
    }

    /** 대분류가 겹치지 않게 먼저 고르고, 모자라면 종류가 겹치지 않게 채운다 */
    internal fun <T> diverse(ranked: List<Pair<WardrobeItem, T>>, n: Int = TOP_N): List<Pair<WardrobeItem, T>> {
        val picked = mutableListOf<Pair<WardrobeItem, T>>()
        for (e in ranked) {
            if (picked.size >= n) break
            if (picked.none { it.first.category == e.first.category }) picked += e
        }
        for (e in ranked) {
            if (picked.size >= n) break
            if (e !in picked && picked.none { SituationRules.kindOf(it.first) == SituationRules.kindOf(e.first) }) picked += e
        }
        return picked.sortedBy { ranked.indexOf(it) }
    }

    /**
     * 상황 하나의 코디 + 상품 추천.
     * 코디: 지금 옷장으로 되면 가장 좋은 코디, 안 되면 부족한 아이템을 더해 만든 코디.
     * 상품: 그 상황 · 계절에 입을 수 있는 판매 상품 — 부족한 대분류 → 상황 선호 종류 순, 종류마다 하나씩 최대 4개.
     */
    private fun plan(
        wardrobe: List<WardrobeItem>, situation: Situation, need: SituationNeed?,
        conditions: WeatherRules.Conditions, today: String, gender: Gender?
    ): SituationPlan {
        val pool = if (need != null && need.items.isNotEmpty()) wardrobe + need.items else wardrobe
        val outfit = OutfitEngine.scoreAll(pool, conditions, situation, today).maxByOrNull { it.score }
        val rule = SituationRules.rule(situation)
        val owned = wardrobe.map { SituationRules.kindOf(it) to it.colorName }.toSet()
        val needCategories = need?.items?.map { it.category }?.toSet().orEmpty()
        val needKinds = need?.items?.map { SituationRules.kindOf(it) }?.toSet().orEmpty()
        val products = ShopCatalog.products
            .filter { GenderFit.allows(it.category, it.subCategory, it.detailType, it.name, gender) }
            .mapNotNull { p -> OutfitSources.candidate(p, 1L)?.let { p to it } }
            .filter { (_, item) ->
                SituationRules.allows(item, situation) && WeatherRules.allows(item, conditions) &&
                    (SituationRules.kindOf(item) to item.colorName) !in owned
            }
            .sortedWith(compareByDescending<Pair<ShopProduct, WardrobeItem>> { (_, item) ->
                (if (SituationRules.kindOf(item) in needKinds) 4 else 0) +
                    (if (item.category in needCategories) 2 else 0) +
                    (if (SituationRules.kindOf(item) in rule.preferred) 1 else 0)
            })
            .distinctBy { SituationRules.kindOf(it.second) }
            .take(4)
            .map { it.first }
        return SituationPlan(situation, outfit, need, products)
    }

    /**
     * 추천 카드 맨 위의 짧은 설명 — "기본템" 대신 옷에 맞게 (예: 겨울 패딩, 단정한 셔츠, 데일리 스니커즈)
     */
    fun labelOf(item: WardrobeItem): String {
        val kind = SituationRules.kindOf(item)
        val winter = item.season == "겨울"
        return when (kind) {
            "셔츠" -> if (item.subCategory == "반팔 상의") "여름 셔츠" else "단정한 셔츠"
            "블라우스" -> "오피스 블라우스"
            "니트" -> "포근한 니트"
            "긴팔티" -> "레이어드 긴팔티"
            "맨투맨" -> "캐주얼 맨투맨"
            "후드티" -> "편한 후드티"
            "반팔티" -> "여름 반팔티"
            "슬랙스" -> "깔끔한 슬랙스"
            "청바지" -> "데일리 청바지"
            "면바지" -> "캐주얼 면바지"
            "트레이닝팬츠" -> "편한 트레이닝팬츠"
            "롱스커트", "미니스커트" -> "포인트 스커트"
            "반바지" -> "여름 반바지"
            "가디건" -> "간절기 가디건"
            "코트" -> if (winter) "겨울 코트" else "간절기 코트"
            "자켓" -> "간절기 자켓"
            "패딩" -> "겨울 패딩"
            "점퍼" -> "데일리 점퍼"
            "스니커즈" -> "데일리 스니커즈"
            "운동화" -> "활동용 운동화"
            "로퍼", "구두" -> "단정한 구두"
            "힐" -> "포인트 힐"
            "부츠" -> "겨울 부츠"
            "샌들" -> "여름 샌들"
            else -> when (item.category) {
                "원피스" -> "원피스 한 벌 코디"
                "아우터" -> if (winter) "겨울 아우터" else "아우터"
                else -> kind
            }
        }
    }

    /**
     * 이 상황 코디를 하나도 못 만들 때, 무엇을 사면 되는지 (만들 수 있으면 null).
     * 코디가 생길 때까지 후보를 하나씩(최대 3개) 더해 본다. 상황별 탭의 "옷이 부족해요" 카드에서도 쓴다.
     */
    fun situationNeed(
        allItems: List<WardrobeItem>, situation: Situation, today: String = WearDates.today(), gender: Gender? = null
    ): SituationNeed? {
        val wardrobe = GenderFit.filter(allItems, gender)
        val conditions = OutfitEngine.seasonConditions(today)
        if (OutfitEngine.scoreAll(wardrobe, conditions, situation, today).isNotEmpty()) return null
        val chosen = mutableListOf<WardrobeItem>()
        var count = 0
        while (chosen.size < MAX_SITUATION_ITEMS && count == 0) {
            val pool = candidatesFor(wardrobe + chosen, conditions, gender).filter { SituationRules.allows(it, situation) }
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
    private fun candidatesFor(
        wardrobe: List<WardrobeItem>, conditions: WeatherRules.Conditions, gender: Gender? = null,
        pool: List<WardrobeItem> = BasicItems.all
    ): List<WardrobeItem> {
        val owned = wardrobe.map { SituationRules.kindOf(it) to it.colorName }.toSet()
        return pool.filter { c ->
            GenderFit.allows(c, gender) && WeatherRules.allows(c, conditions) &&
                (SituationRules.kindOf(c) to c.colorName) !in owned &&
                wardrobe.none { it.id == c.id }
        }
    }

    /** 상황에 맞는 옷이 몇 가지 대분류(상의·하의·신발 등)를 채우는지 — 코디가 아직 0개일 때 고르는 기준 */
    private fun coverage(items: List<WardrobeItem>, situation: Situation, conditions: WeatherRules.Conditions): Int =
        items.filter { SituationRules.allows(it, situation) && WeatherRules.allows(it, conditions) }
            .map { it.category }.distinct().size
}
