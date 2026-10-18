package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

class OutfitSourcesTest {
    private val today = "2026-07-01"
    private val at = WearDates.toCalendar(today).timeInMillis
    private fun owned(id: String, category: String) = WardrobeItem(id = id, name = id,
        category = category, colorName = "블랙", colorValue = 0xFF000000,
        imageUri = "file:///owned.png", addedAt = at)
    private fun product(id: String = "p", category: String? = "상의") = ShopProduct(
        name = "명시된 상품", brand = "브랜드", price = "1원", originalPrice = "1원", rating = 0f,
        reviewCount = 0, color = 0xFFFFFFFF, imageUrl = "https://example.com/item.png", id = id,
        category = category, clothingColorName = "블랙", clothingColorValue = 0xFF000000)
    private val owned = listOf(owned("top", "상의"), owned("bottom", "하의"), owned("shoes", "신발"))
    private val wished = listOf(product("top", "상의"), product("bottom", "하의"), product("shoes", "신발"))
    private fun candidates(scope: ClothingScope, own: List<WardrobeItem> = owned, wish: List<ShopProduct> = wished) =
        OutfitSources.candidates(own, wish, scope, at)
    private fun recommendation(items: List<WardrobeItem>, situation: Situation? = null, body: BodyProfile? = null,
                               weather: WeatherInfo = WeatherInfo("맑음", 25, 50, 800), taste: TasteScores = TasteScores()) =
        OutfitEngine.recommend(items, weather, emptyList(), emptyMap(), situation, today,
            taste = taste, bodyProfile = body)

    @Test fun allFilterShowsBothSources() {
        assertEquals(6, OutfitSources.entries(owned, wished, ClothingScope.ALL).size)
    }
    @Test fun ownedFilterShowsOnlyOwned() {
        assertEquals(owned, OutfitSources.entries(owned, wished, ClothingScope.OWNED).map { it.owned })
    }
    @Test fun interestFilterShowsOnlyProducts() {
        assertEquals(wished, OutfitSources.entries(owned, wished, ClothingScope.INTERESTED).map { it.product })
    }
    @Test fun allFilterHasExplicitProvenanceAndSeparateKeys() {
        val entries = OutfitSources.entries(owned, wished, ClothingScope.ALL)
        assertEquals(3, entries.count { it.isInterested })
        assertEquals(6, entries.map { it.key }.distinct().size)
    }
    @Test fun categoryAndSourceAreIndependent() {
        val entries = OutfitSources.entries(owned, wished, ClothingScope.INTERESTED, "상의")
        assertEquals(listOf(wished.first()), entries.map { it.product })
        assertEquals(2, OutfitSources.entries(owned, wished, ClothingScope.ALL, "상의").size)
    }
    @Test fun unlikingRemovesProductFromBothListAndCandidates() {
        val id = ShopCatalog.products.first().id
        val liked = ProductInterestStore.toggled(emptyList(), id)
        assertEquals(1, ShopCatalog.interested(liked).size)
        val unliked = ProductInterestStore.toggled(liked, id)
        assertTrue(OutfitSources.entries(emptyList(), ShopCatalog.interested(unliked), ClothingScope.ALL).isEmpty())
        assertTrue(candidates(ClothingScope.INTERESTED, emptyList(), ShopCatalog.interested(unliked)).isEmpty())
    }
    @Test fun ownedRecommendationExcludesInterests() { assertEquals(owned, candidates(ClothingScope.OWNED)) }
    @Test fun combinedRecommendationUsesBothSources() {
        val items = candidates(ClothingScope.ALL)
        assertTrue(items.containsAll(owned))
        assertTrue(wished.all { p -> items.any { it.id == OutfitSources.candidateId(p) } })
    }
    @Test fun interestedRecommendationExcludesOwned() {
        assertEquals(wished.map { OutfitSources.candidateId(it) }, candidates(ClothingScope.INTERESTED).map { it.id })
    }
    @Test fun noInterestsKeepsCombinedOwnedCandidates() {
        assertEquals(owned, candidates(ClothingScope.ALL, wish = emptyList()))
        assertTrue(candidates(ClothingScope.INTERESTED, wish = emptyList()).isEmpty())
    }
    @Test fun noOwnedStillUsesInterests() { assertEquals(3, candidates(ClothingScope.ALL, own = emptyList()).size) }
    @Test fun bothEmptyProduceNotEnough() {
        val items = candidates(ClothingScope.ALL, emptyList(), emptyList())
        assertTrue(items.isEmpty()); assertTrue(recommendation(items) is OutfitEngine.Result.NotEnough)
    }
    @Test fun onlyInterestedTopCannotProduceEmptyOrFakeOutfit() {
        assertTrue(recommendation(candidates(ClothingScope.INTERESTED, wish = listOf(wished.first()))) is OutfitEngine.Result.NotEnough)
        assertTrue(OutfitSources.insufficientMessage(ClothingScope.INTERESTED, "하의·신발 필요").contains("관심 옷만으로"))
    }
    @Test fun combinedCandidatesCompleteMissingOwnedCategories() {
        val items = candidates(ClothingScope.ALL, listOf(owned.first()), wished.drop(1))
        val result = recommendation(items) as OutfitEngine.Result.Ok
        assertTrue(result.outfits.isNotEmpty())
        assertTrue(result.outfits.all { it.items.contains(owned.first()) })
    }
    @Test fun missingColorDoesNotUseCardBackgroundColor() {
        val p = product().copy(clothingColorName = null, clothingColorValue = null)
        assertEquals(1, OutfitSources.entries(emptyList(), listOf(p), ClothingScope.INTERESTED).size)
        assertNull(OutfitSources.candidate(p, at))
    }
    @Test fun missingColorNameOrValueExcluded() {
        assertNull(OutfitSources.candidate(product().copy(clothingColorName = null), at))
        assertNull(OutfitSources.candidate(product().copy(clothingColorValue = null), at))
    }
    @Test fun unknownCategoryIsNotGuessedFromName() {
        val p = product(category = null).copy(name = "상의처럼 보이는 상품")
        assertNull(OutfitSources.candidate(p, at))
        assertTrue(OutfitSources.entries(emptyList(), listOf(p), ClothingScope.INTERESTED).isEmpty())
    }
    @Test fun bagsAndAccessoriesAreNotConvertedToTops() {
        for (category in listOf("가방", "액세서리", "기타")) {
            assertNull(OutfitSources.candidate(product(category = category), at))
        }
    }
    @Test fun shoesAreSupportedByExistingEngine() {
        assertEquals("신발", OutfitSources.candidate(product(category = "신발"), at)!!.category)
    }
    @Test fun missingIdNameOrImageExcluded() {
        assertNull(OutfitSources.candidate(product(id = ""), at))
        assertNull(OutfitSources.candidate(product().copy(name = ""), at))
        assertNull(OutfitSources.candidate(product().copy(imageUrl = ""), at))
    }
    @Test fun unspecifiedSeasonStyleAndLengthStayUnknown() {
        val c = OutfitSources.candidate(product(), at)!!
        assertNull(c.season); assertNull(c.style); assertNull(c.subCategory); assertNull(c.detailType)
    }
    @Test fun stableProductIdsDeduplicateInterestsButNotOwnedNamesOrImages() {
        assertEquals(1, candidates(ClothingScope.INTERESTED, wish = listOf(product(), product())).size)
        val p = product().copy(name = owned.first().name, imageUrl = owned.first().imageUri)
        assertEquals(2, OutfitSources.entries(listOf(owned.first()), listOf(p), ClothingScope.ALL).size)
    }
    @Test fun realCatalogOnlyExplicitColorVariantsAreCandidates() {
        val ready = ShopCatalog.allProducts.mapNotNull { OutfitSources.candidate(it, at) }
        assertEquals(3, ready.size)
        assertEquals(setOf("상의", "하의", "신발"), ready.map { it.category }.toSet())
        assertTrue(recommendation(ready) is OutfitEngine.Result.Ok)
    }
    @Test fun interestedSandalsStillExcludedInRainAndInterview() {
        val items = candidates(ClothingScope.INTERESTED, wish = wished.map {
            if (it.category == "신발") it.copy(subCategory = "샌들") else it
        })
        assertTrue(recommendation(items, weather = WeatherInfo("비", 25, 80, 500)) is OutfitEngine.Result.NotEnough)
        assertTrue(recommendation(items, situation = Situation.INTERVIEW) is OutfitEngine.Result.NotEnough)
    }
    @Test fun interestedOutfitRetainsBodyBonusAndTasteScoring() {
        val items = candidates(ClothingScope.INTERESTED, wish = wished.map {
            if (it.category == "하의") it.copy(subCategory = "반바지") else it
        })
        fun first(body: BodyProfile? = null, taste: TasteScores = TasteScores()) =
            (recommendation(items, body = body, taste = taste) as OutfitEngine.Result.Ok).outfits.first()
        val base = first()
        val profile = BodyProfileAnalyzer.fromLengths(170f, 72f, 56f)!!
        assertEquals(BodyAwareOutfitScorer.MAX_BONUS, first(body = profile).score - base.score, 0.00001)
        val taste = TasteScores(colors = mapOf("블랙" to 10), ratedCount = 10)
        assertTrue(first(taste = taste).score > base.score)
    }
    @Test fun unownedCandidatesNeverReceiveWearHistoryBonusesOrPenalties() {
        val items = candidates(ClothingScope.INTERESTED)
        assertTrue(items.none { it.isOwned })
        val old = items.map { it.copy(addedAt = WearDates.toCalendar("2026-01-01").timeInMillis) }
        val logs = listOf(WearLog(today, items.map { it.id }))
        val baseline = (recommendation(items) as OutfitEngine.Result.Ok).outfits.single()
        val withHistory = (OutfitEngine.recommend(old, WeatherInfo("맑음", 25, 50, 800), logs,
            emptyMap(), today = today) as OutfitEngine.Result.Ok).outfits.single()
        assertEquals(baseline.score, withHistory.score, 0.00001)
    }
    @Test fun ownedCandidatesPreserveExistingWearHistoryPenalty() {
        val items = candidates(ClothingScope.OWNED)
        assertTrue(items.all { it.isOwned })
        val baseline = (recommendation(items) as OutfitEngine.Result.Ok).outfits.single()
        val logs = listOf(WearLog(today, items.map { it.id }))
        val withHistory = (OutfitEngine.recommend(items, WeatherInfo("맑음", 25, 50, 800), logs,
            emptyMap(), today = today) as OutfitEngine.Result.Ok).outfits.single()
        assertEquals(-4.5, withHistory.score - baseline.score, 0.00001)
    }
    @Test fun eachEmptyScopeHasHonestGuidance() {
        assertTrue(OutfitSources.emptyMessage(ClothingScope.OWNED, "전체").contains("등록"))
        assertTrue(OutfitSources.emptyMessage(ClothingScope.INTERESTED, "전체").contains("관심"))
        assertTrue(OutfitSources.emptyMessage(ClothingScope.ALL, "전체").contains("관심"))
        assertTrue(OutfitSources.emptyMessage(ClothingScope.INTERESTED, "하의").contains("하의"))
    }
}
