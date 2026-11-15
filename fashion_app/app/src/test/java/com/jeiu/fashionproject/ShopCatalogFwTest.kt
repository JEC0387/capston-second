package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.ClothingCategories
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.recommend.GenderFit
import com.jeiu.fashionproject.recommend.OutfitSources
import org.junit.Assert.*
import org.junit.Test

/** F/W catalogue additions: real seller links, no invented ratings, gender filtering by product name. */
class ShopCatalogFwTest {
    private val products = ShopCatalog.products

    @Test fun catalogueHasMoreThanTheOriginalEightWithUniqueSellerLinks() {
        assertTrue(products.size >= 19)
        assertEquals(products.size, products.map { it.id }.toSet().size)
        products.forEach {
            assertTrue(it.name, it.purchaseUrl.startsWith("https://"))
            assertTrue(it.name, it.imageRes != 0 || it.imageUrl.startsWith("https://"))
            assertTrue(it.name, it.category in OutfitSources.supportedCategories)
        }
    }

    @Test fun addedProductsUseTheAppTaxonomyAndCarryNoInventedRatings() {
        val added = products.drop(8)
        assertEquals(11, added.size)
        added.forEach { p ->
            assertEquals(p.name, 0f, p.rating); assertEquals(p.name, 0, p.reviewCount)
            assertTrue(p.name, p.subCategory in ClothingCategories.subCategories.getValue(p.category!!))
            assertTrue(p.name, p.detailType in ClothingCategories.detailTypes.getValue(p.subCategory!!))
            assertTrue(p.name, p.purchaseUrl.startsWith("https://www.musinsa.com/products/"))
        }
    }

    @Test fun womenAndMenLabelledProductsFollowTheUsersGender() {
        fun names(g: Gender) = products.filter { GenderFit.allows(it.category, it.subCategory, it.detailType, it.name, g) }.map { it.name }
        val men = names(Gender.MALE); val women = names(Gender.FEMALE)
        assertTrue(men.none { "우먼" in it || "여성" in it || "스커트" in it })
        assertTrue(men.any { it.startsWith("남성 1996") })
        assertTrue(women.none { it.startsWith("남성") })
        assertTrue(women.any { it.startsWith("여성 1996") } && women.any { "우먼즈" in it })
        // Unisex items stay visible for everyone.
        assertTrue(men.any { "삼바" in it } && women.any { "삼바" in it })
    }
}
