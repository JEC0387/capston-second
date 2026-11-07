package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.ProductDetailSelection
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import org.junit.Assert.*
import org.junit.Test

class P19ProductDetailSelectionTest {
    @Test fun twoSelectionsKeepTheirOwnDetailsAndActionTargets() {
        val first = ShopCatalog.products[0]
        val second = ShopCatalog.products[1]
        val a = requireNotNull(ProductDetailSelection.resolve(first.id))
        val b = requireNotNull(ProductDetailSelection.resolve(second.id))

        assertEquals(first, a.product)
        assertEquals(second, b.product)
        assertNotEquals(a.interestId, b.interestId)
        assertEquals(first.purchaseUrl, a.purchaseUrl)
        assertEquals(second.purchaseUrl, b.purchaseUrl)
        assertEquals("${first.brand}\n${first.name}\n${first.purchaseUrl}", a.shareText)
        assertEquals("${second.brand}\n${second.name}\n${second.purchaseUrl}", b.shareText)
        assertEquals(first.imageRes, a.image)
        assertEquals(second.imageUrl, b.image)
        // Opening B and then A is an exact lookup, without a last-selected global fallback.
        assertEquals(a, ProductDetailSelection.resolve(first.id))
    }

    @Test fun likesUseTheSelectedIdWithoutChangingTheOtherProduct() {
        val a = requireNotNull(ProductDetailSelection.resolve(ShopCatalog.products[0].id))
        val b = requireNotNull(ProductDetailSelection.resolve(ShopCatalog.products[1].id))
        val likedA = ProductInterestStore.toggled(emptyList(), a.interestId)
        val likedBoth = ProductInterestStore.toggled(likedA, b.interestId)
        assertEquals(listOf(a.product.id), likedA)
        assertEquals(listOf(a.product.id, b.product.id), likedBoth)
        assertEquals(listOf(b.product.id), ProductInterestStore.toggled(likedBoth, a.interestId))
    }

    @Test fun missingAndInvalidIdsNeverFallBackToDemoOrLastProduct() {
        assertNotNull(ProductDetailSelection.resolve(ShopCatalog.products.first().id))
        listOf(null, "", " ", "unknown", "https://invalid.example/product", "%", "../product",
            ShopCatalog.products.first().id + "?different=true").forEach {
            assertNull("Unexpected match for $it", ProductDetailSelection.resolve(it))
        }
    }

    @Test fun demoWithoutMerchantLinkCannotBuyAnUnrelatedCatalogProduct() {
        val selection = requireNotNull(ProductDetailSelection.resolve(ShopCatalog.detailProduct.id))
        assertNull(selection.purchaseUrl)
        assertEquals("${ShopCatalog.detailProduct.brand}\n${ShopCatalog.detailProduct.name}", selection.shareText)
        assertFalse(selection.shareText.contains("https://"))
    }

    @Test fun everyCatalogSelectionHasItsOwnIdentityAndActions() {
        ShopCatalog.products.forEach { product ->
            val selection = requireNotNull(ProductDetailSelection.resolve(product.id))
            assertEquals(product.id, selection.interestId)
            assertEquals(product.price, selection.product.price)
            assertEquals(product.purchaseUrl, selection.purchaseUrl)
            assertTrue(selection.shareText.contains(product.name))
            assertTrue(selection.shareText.endsWith(product.purchaseUrl))
        }
    }
}
