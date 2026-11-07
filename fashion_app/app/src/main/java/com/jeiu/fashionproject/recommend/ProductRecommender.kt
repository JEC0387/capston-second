package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.ShopProduct
import com.jeiu.fashionproject.data.TasteScores
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates

/**
 * 쇼핑 추천 상품 순서 · 이유 — "AI추천" 고정 딱지 대신 실제로 계산한다.
 *
 * 상품마다 점수:
 *  ① 내 옷장 옷과 새로 만들 수 있는 좋은 코디 수 (코디 엔진 · 계절 대표 날씨, 색 정보가 있는 상품만)
 *  ② 온보딩 · 마이페이지에서 고른 선호 스타일에 맞는지
 *  ③ 스타일 취향 학습 점수 (좋아요 · 싫어요)
 *  ④ 좋아하는 색 · 자주 고르는 종류 (취향 카드 · 👍👎 · 착용 기록)
 * 성별에 맞지 않는 상품은 뺀다. 이유는 화면에 그대로 보여 준다.
 */
object ProductRecommender {

    data class Pick(val product: ShopProduct, val score: Double, val outfits: Int, val reasons: List<String>)

    private const val OUTFIT_WEIGHT = 1.0
    private const val MAX_OUTFITS = 8
    private const val STYLE_WEIGHT = 1.5
    private const val LEARNED_WEIGHT = 1.5
    private const val TASTE_WEIGHT = 0.8

    /** 색 정보가 없어도 이름 · 종류로 스타일을 볼 수 있게 만든 비교용 옷 (저장하지 않음) */
    private fun probe(p: ShopProduct): WardrobeItem = WardrobeItem(
        id = OutfitSources.candidateId(p), name = p.name, category = p.category ?: "",
        colorName = p.clothingColorName ?: "", colorValue = p.clothingColorValue ?: 0xFF888888,
        imageUri = "", subCategory = p.subCategory, detailType = p.detailType, isOwned = false,
        // Unknown catalog color must not become a gray/minimal style through placeholder pixels.
        style = ""
    )

    fun rank(
        products: List<ShopProduct>,
        wardrobe: List<WardrobeItem>,
        gender: Gender?,
        preferred: List<String>,
        learned: Map<String, Int>,
        taste: TasteScores,
        today: String = WearDates.today()
    ): List<Pick> {
        val owned = GenderFit.filter(wardrobe.filter { it.isOwned }, gender)
        val conditions = OutfitEngine.seasonConditions(today)
        val baseLooks = OutfitEngine.scoreAll(owned, conditions, null, today)
            .filter { it.score >= MissingItemFinder.GOOD_SCORE }.map { look(it) }.toSet()
        val likedColors = taste.top(taste.colors, 3)
        val likedKinds = taste.top(taste.kinds, 3)

        return products
            .filter { GenderFit.allows(it.category, it.subCategory, it.detailType, it.name, gender) }
            .map { p ->
                val candidate = OutfitSources.candidate(p, 1L)
                val item = candidate ?: probe(p)
                // ① 새로 생기는 좋은 코디 (상의 · 하의 짝 기준, 원래 있던 짝은 세지 않음)
                val outfits = candidate?.let { c ->
                    OutfitEngine.scoreAll(owned + c, conditions, null, today)
                        .filter { o -> o.score >= MissingItemFinder.GOOD_SCORE && o.items.any { it.id == c.id } }
                        .map { look(it) }.filter { it !in baseLooks }.distinct().size
                } ?: 0
                val styles = PreferredStyles.normalize(preferred).filter { PreferredStyles.matches(item, it) }
                val affinity = PreferredStyles.learnedAffinity(listOf(item), learned)
                val color = item.colorName.takeIf { it.isNotBlank() && it in likedColors }
                val kind = SituationRules.kindOf(item).takeIf { it in likedKinds }

                val score = OUTFIT_WEIGHT * minOf(outfits, MAX_OUTFITS) + STYLE_WEIGHT * styles.size.coerceAtMost(2) +
                    LEARNED_WEIGHT * affinity + TASTE_WEIGHT * (listOfNotNull(color, kind).size)
                val reasons = buildList {
                    if (outfits > 0) add("내 옷장 옷과 코디 ${outfits}가지")
                    styles.firstOrNull()?.let { add("선호하는 $it 스타일") }
                    if (affinity > 0.3 && styles.isEmpty()) PreferredStyles.canonicalScores(learned)
                        .filter { (style, score) -> score > 0 && PreferredStyles.matches(item, style) }
                        .maxByOrNull { it.value }?.key?.let { add("취향 학습에서 좋아한 $it") }
                    color?.let { add("좋아하는 $it") }
                    kind?.let { add("자주 고르는 $it") }
                }
                Pick(p, score, outfits, reasons.take(2))
            }
            .sortedByDescending { it.score }
    }

    private fun look(o: OutfitEngine.Outfit): String =
        o.items.filter { it.category in setOf("상의", "하의", "원피스") }.map { it.id }.sorted().joinToString("+")
}
