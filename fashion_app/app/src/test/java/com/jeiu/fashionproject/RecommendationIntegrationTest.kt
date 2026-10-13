package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationIntegrationTest {
    private val day = "2026-07-01"
    private fun item(id: String, category: String, sub: String, kind: String, color: Long = 0xFF222222,
                     colorName: String = "블랙") = WardrobeItem(
        id = id, name = kind, category = category, colorName = colorName, colorValue = color,
        imageUri = "", subCategory = sub, detailType = kind, season = "사계절", style = "캐주얼",
        addedAt = WearDates.toCalendar(day).timeInMillis
    )
    private val top = item("top", "상의", "긴팔 상의", "셔츠", 0xFFCCCCCC, "화이트")
    private val bottom = item("bottom", "하의", "긴바지", "슬랙스")
    private val shoes = item("shoes", "신발", "운동화", "스니커즈")
    private val wardrobe = listOf(top, bottom, shoes)
    private val weather = WeatherInfo("맑음", 22, 50, 800)
    private fun recommend(taste: TasteScores = TasteScores(), logs: List<WearLog> = emptyList()) =
        (OutfitEngine.recommend(wardrobe, weather, logs, emptyMap(), today = day, taste = taste) as OutfitEngine.Result.Ok).outfits.single()

    @Test fun emptyTastePreservesExistingColorWeatherStyleScore() {
        // Existing main: neutral +2, brightness contrast +1, style unity +0.5, no history bonus.
        assertEquals(3.5, recommend().score, 0.00001)
        assertEquals("무채색 조합", recommend().pattern)
    }

    @Test fun recentWearPenaltyIsStillApplied() {
        val worn = listOf(WearLog("2026-06-30", wardrobe.map { it.id }))
        assertEquals(-4.5, recommend(logs = worn).score - recommend().score, 0.00001)
    }

    @Test fun tasteAddsBoundedColorKindAndPatternScoresWithConfidence() {
        val taste = TasteScores(mapOf("화이트" to 20), mapOf("셔츠" to 20), mapOf("무채색 조합" to 20), ratedCount = 10)
        assertEquals(2.5, recommend(taste).score - recommend().score, 0.00001)
        assertEquals(0.25, recommend(taste.copy(ratedCount = 1)).score - recommend().score, 0.00001)
        assertTrue(recommend(taste.copy(colors = mapOf("화이트" to -20), kinds = emptyMap(), patterns = emptyMap())).score < recommend().score)
    }

    @Test fun tasteNeverBypassesRainAndSituationFilters() {
        val strong = TasteScores(mapOf("블랙" to 1000), ratedCount = 100)
        val rainy = OutfitEngine.recommend(listOf(top, bottom, shoes.copy(subCategory = "샌들")),
            weather.copy(conditionId = 500), emptyList(), emptyMap(), today = day, taste = strong)
        assertTrue(rainy is OutfitEngine.Result.NotEnough)
        val interview = OutfitEngine.recommend(listOf(top.copy(detailType = "후드티"), bottom, shoes),
            weather, emptyList(), emptyMap(), Situation.INTERVIEW, today = day, taste = strong)
        assertTrue(interview is OutfitEngine.Result.NotEnough)
    }

    @Test fun missingItemAnalysisUsesVirtualItemsWithoutChangingWardrobe() {
        val owned = listOf(top.copy(subCategory = "반팔 상의", detailType = "반팔티"), shoes)
        val before = owned.toList()
        val report = MissingItemFinder.analyze(owned, day)
        assertEquals(before, owned)
        assertEquals(0, report.goodCount)
        assertTrue(report.suggestions.isNotEmpty())
        report.suggestions.forEach { suggestion ->
            assertTrue(suggestion.gain > 0)
            assertTrue(suggestion.examples.isNotEmpty())
            assertTrue(suggestion.examples.all { o -> o.items.any { it.id == suggestion.item.id } })
            assertFalse(owned.any { it.id == suggestion.item.id })
        }
    }

    @Test fun ownedKindAndColorAreNotSuggestedAgain() {
        val report = MissingItemFinder.analyze(wardrobe, day)
        val owned = wardrobe.map { SituationRules.kindOf(it) to it.colorName }.toSet()
        assertTrue(report.suggestions.none { (SituationRules.kindOf(it.item) to it.item.colorName) in owned })
    }

    @Test fun emptyWardrobeHasNoFictitiousCompleteOutfits() {
        val report = MissingItemFinder.analyze(emptyList(), day)
        assertEquals(0, report.goodCount)
        assertTrue(report.suggestions.isEmpty())
        assertTrue(report.situationNeeds.isNotEmpty())
    }

    @Test fun winterVirtualCountsRequireAnOuterLayer() {
        assertTrue(OutfitEngine.scoreAll(wardrobe, WeatherRules.Conditions(3, false, "겨울"), today = "2026-01-01").isEmpty())
    }

    @Test fun situationSuggestionsActuallyCompleteAllowedOutfits() {
        val need = MissingItemFinder.situationNeed(emptyList(), Situation.INTERVIEW, day)!!
        assertTrue(need.items.isNotEmpty())
        assertTrue(need.items.all { SituationRules.allows(it, Situation.INTERVIEW) })
        assertEquals(need.outfitCount, OutfitEngine.scoreAll(need.items, OutfitEngine.seasonConditions(day), Situation.INTERVIEW, day).size)
    }
}
