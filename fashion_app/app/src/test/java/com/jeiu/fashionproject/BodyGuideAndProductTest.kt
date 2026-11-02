package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

/** 체형별 스타일 제안 · 쇼핑 추천 상품 점수 — 고정 예시 · 가짜 평점 대신 실제 계산 */
class BodyGuideAndProductTest {
    private val day = "2026-10-15"
    private fun item(id: String, category: String, sub: String, detail: String, color: String = "블랙",
                     value: Long = 0xFF222222, name: String = id) = WardrobeItem(
        id = id, name = name, category = category, subCategory = sub, detailType = detail,
        colorName = color, colorValue = value, imageUri = "", season = "사계절", style = null,
        addedAt = WearDates.toCalendar(day).timeInMillis
    )

    @Test fun everyBodyTypeHasGuideForBothGenders() {
        BodyStyleGuide.BODY_TYPES.forEach { type ->
            listOf(Gender.MALE, Gender.FEMALE, null).forEach { g ->
                val guide = BodyStyleGuide.guide(type, g)!!
                assertEquals(type, guide.bodyType)
                assertEquals(3, guide.tips.size)
                assertTrue(guide.avoid.isNotEmpty())
            }
        }
        assertNull(BodyStyleGuide.guide("알 수 없음", Gender.MALE))
        assertNull(BodyStyleGuide.guide(null, null))
        // 남성 제안에는 원피스 · 스커트가 나오지 않는다
        BodyStyleGuide.BODY_TYPES.forEach { type ->
            val text = BodyStyleGuide.guide(type, Gender.MALE)!!.tips.joinToString { it.title + it.query }
            assertFalse(text, listOf("원피스", "스커트", "블라우스").any { it in text })
        }
    }

    @Test fun wardrobeIsSortedByGuide() {
        val guide = BodyStyleGuide.guide("삼각형", Gender.MALE)!!
        val jacket = item("jacket", "아우터", "긴팔 아우터", "자켓")
        val whitePants = item("white", "하의", "긴바지", "면바지", "화이트", 0xFFF5F5F5)
        val skinny = item("skinny", "하의", "긴바지", "청바지", name = "스키니 진")
        val blackSlacks = item("slacks", "하의", "긴바지", "슬랙스")
        val (good, avoid) = BodyStyleGuide.sort(listOf(jacket, whitePants, skinny, blackSlacks), guide, Gender.MALE)
        assertEquals(setOf("jacket", "slacks"), good.map { it.id }.toSet())
        assertEquals(setOf("white", "skinny"), avoid.map { it.id }.toSet())
    }

    @Test fun productsAreRankedByRealSignalsNotFixedTags() {
        val wardrobe = listOf(
            item("tee", "상의", "반팔 상의", "반팔티", "화이트", 0xFFF5F5F5),
            item("shoes", "신발", "운동화", "운동화", "화이트", 0xFFF5F5F5)
        )
        // 하의가 없는 옷장 → 하의 상품이 코디를 새로 만든다 (여름 기준: 반팔 · 겉옷 없음)
        val summer = "2026-07-15"
        val pants = ShopProduct("블랙 슬랙스", "브랜드", "1원", "1원", 0f, 0, 0xFF000000, id = "p-pants",
            purchaseUrl = "https://example.com/p", imageUrl = "https://example.com/p.jpg")
            .copy(category = "하의", subCategory = "긴바지", detailType = "슬랙스", clothingColorName = "그레이", clothingColorValue = 0xFF555555)
        val shirt = ShopProduct("그레이 셔츠", "브랜드", "1원", "1원", 0f, 0, 0xFF000000, id = "p-shirt",
            purchaseUrl = "https://example.com/s", imageUrl = "https://example.com/s.jpg")
            .copy(category = "상의", subCategory = "긴팔 상의", detailType = "셔츠", clothingColorName = "그레이", clothingColorValue = 0xFF888888)
        val dress = ShopProduct("플라워 원피스", "브랜드", "1원", "1원", 0f, 0, 0xFF000000, id = "p-dress")
            .copy(category = "원피스", subCategory = "긴팔 원피스")

        val picks = ProductRecommender.rank(listOf(shirt, pants, dress), wardrobe, Gender.MALE, emptyList(), emptyMap(), TasteScores(), summer)
        assertEquals(listOf("p-pants", "p-shirt"), picks.map { it.product.id })      // 남성에게 원피스 제외
        assertTrue(picks.first().outfits > 0)
        assertTrue(picks.first().reasons.first().startsWith("내 옷장 옷과 코디"))

        // 선호 스타일(포멀)을 고르면 셔츠에도 이유가 붙는다
        val formal = ProductRecommender.rank(listOf(shirt), wardrobe, Gender.MALE, listOf("포멀"), emptyMap(), TasteScores(), summer)
        assertTrue(formal.single().reasons.contains("선호하는 포멀 스타일"))
    }
}
