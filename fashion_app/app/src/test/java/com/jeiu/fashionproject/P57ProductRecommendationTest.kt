package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

class P57ProductRecommendationTest {
    private fun product(id: String, name: String, category: String?, detail: String? = null) =
        ShopProduct(name, "synthetic", "0", "0", 0f, 0, 0xFF888888,
            id = id, category = category, detailType = detail)
    private fun rank(products: List<ShopProduct>, preferred: List<String> = emptyList()) =
        ProductRecommender.rank(products, emptyList(), null, preferred, emptyMap(), TasteScores(), "2026-10-15")

    @Test fun emptyWardrobeAndNoPreferenceCannotInventPersonalReasons() {
        val picks = rank(listOf(product("a", "셔츠", "상의", "셔츠"), product("b", "청바지", "하의", "청바지")))
        assertEquals(setOf("a", "b"), picks.map { it.product.id }.toSet())
        assertTrue(picks.all { it.outfits == 0 && it.reasons.isEmpty() && it.score == 0.0 })
    }

    @Test fun missingGenderKeepsDressAndDoesNotMutateCatalog() {
        val source = listOf(product("dress", "원피스", "원피스"), product("shirt", "셔츠", "상의", "셔츠"))
        assertEquals(setOf("dress", "shirt"), rank(source).map { it.product.id }.toSet())
        assertEquals(2, source.size)
    }

    @Test fun unknownCatalogColorDoesNotCreateMinimalPersonalization() {
        val unknown = product("unknown", "상품", null)
        assertTrue(rank(listOf(unknown), listOf("미니멀")).single().reasons.isEmpty())
    }

    @Test fun legacyPreferenceProjectsWithoutChangingInputOrProductIdentity() {
        val preferences = listOf("클래식", "페미닌")
        val selected = product("https://synthetic.invalid/p?id=a&color=navy", "셔츠", "상의", "셔츠")
        val pick = rank(listOf(selected), preferences).single()
        assertEquals(selected.id, pick.product.id)
        assertEquals(listOf("선호하는 포멀 스타일"), pick.reasons)
        assertEquals(listOf("클래식", "페미닌"), preferences)
    }
}
