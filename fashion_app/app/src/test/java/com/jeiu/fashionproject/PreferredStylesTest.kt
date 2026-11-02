package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

/** 온보딩 · 마이페이지에서 고른 선호 스타일(8가지)이 코디 추천에 반영되는지 */
class PreferredStylesTest {
    private val day = "2026-10-15"
    private fun item(
        id: String, category: String, sub: String, detail: String,
        color: String = "블랙", value: Long = 0xFF222222, style: String? = "캐주얼"
    ) = WardrobeItem(
        id = id, name = id, category = category, subCategory = sub, detailType = detail,
        colorName = color, colorValue = value, imageUri = "", season = "사계절",
        style = style, addedAt = WearDates.toCalendar(day).timeInMillis
    )

    // 모두 같은 색 · 같은 스타일 태그 → 선호 스타일 말고는 점수 차이가 나지 않게
    private val shirt = item("shirt", "상의", "긴팔 상의", "셔츠")
    private val hoodie = item("hoodie", "상의", "긴팔 상의", "후드티")
    private val slacks = item("slacks", "하의", "긴바지", "슬랙스")
    private val training = item("training", "하의", "긴바지", "트레이닝팬츠")
    private val loafer = item("loafer", "신발", "구두", "로퍼")
    private val sneakers = item("sneakers", "신발", "운동화", "운동화")

    private fun outfits(items: List<WardrobeItem>, preferred: List<String>) =
        (OutfitEngine.recommend(items, null, emptyList(), emptyMap(), today = day, preferred = preferred)
            as OutfitEngine.Result.Ok).outfits

    @Test fun optionsAreTheEightStyles() {
        assertEquals(listOf("캐주얼", "스트릿", "미니멀", "댄디", "아메카지", "유니크", "스포티", "포멀"), PreferredStyles.OPTIONS)
        assertEquals(PreferredStyles.OPTIONS, ProfileStore.STYLE_OPTIONS)
    }

    @Test fun legacyNamesAreMappedAndUnknownDropped() {
        assertEquals(listOf("포멀", "아메카지", "댄디"), PreferredStyles.normalize(listOf("클래식", "빈티지", "페미닌", "댄디")))
        assertEquals(listOf("포멀"), PreferredStyles.normalize(listOf("포멀", "클래식")))
        assertEquals(emptyList<String>(), PreferredStyles.normalize(emptyList()))
    }

    @Test fun itemsMatchExpectedStyles() {
        val navyShirt = item("navy", "상의", "긴팔 상의", "셔츠", "네이비", 0xFF1F2A44, style = null)
        assertTrue(PreferredStyles.matches(navyShirt, "댄디"))
        assertTrue(PreferredStyles.matches(navyShirt, "포멀"))
        assertFalse(PreferredStyles.matches(navyShirt, "스포티"))
        assertTrue(PreferredStyles.matches(hoodie, "스포티"))
        assertTrue(PreferredStyles.matches(hoodie, "스트릿"))
        assertTrue(PreferredStyles.matches(sneakers, "스포티"))
        assertTrue(PreferredStyles.matches(loafer, "포멀"))
        val redTee = item("red", "상의", "반팔 상의", "반팔티", "레드", 0xFFD03030, style = null)
        assertTrue(PreferredStyles.matches(redTee, "유니크"))
        val boots = item("boots", "신발", "부츠", "부츠", "브라운", 0xFF6B4423, style = null)
        assertTrue(PreferredStyles.matches(boots, "아메카지"))
    }

    @Test fun noPreferenceKeepsScoresUnchanged() {
        val items = listOf(shirt, hoodie, slacks, training, loafer, sneakers)
        assertEquals(outfits(items, emptyList()), outfits(items, listOf("없는스타일")))
    }

    @Test fun fullyMatchingOutfitGetsMaxBonusAndReason() {
        val items = listOf(shirt, slacks, loafer)
        val base = outfits(items, emptyList()).single()
        val formal = outfits(items, listOf("포멀")).single()
        val sporty = outfits(items, listOf("스포티")).single()
        assertEquals(PreferredStyles.MAX_SCORE, formal.score - base.score, 0.00001)
        assertEquals(base.score, sporty.score, 0.00001)
        assertTrue(formal.reasons.contains("선호하는 포멀 스타일에 맞춘 코디예요"))
        assertFalse(sporty.reasons.any { it.startsWith("선호하는") })
    }

    @Test fun preferredStyleChangesTopOutfit() {
        val items = listOf(shirt, hoodie, slacks, training, loafer, sneakers)
        val formalTop = outfits(items, listOf("포멀")).first()
        val sportyTop = outfits(items, listOf("스포티")).first()
        assertEquals(setOf("shirt", "slacks", "loafer"), formalTop.items.map { it.id }.toSet())
        assertEquals(setOf("hoodie", "training", "sneakers"), sportyTop.items.map { it.id }.toSet())
    }
}
