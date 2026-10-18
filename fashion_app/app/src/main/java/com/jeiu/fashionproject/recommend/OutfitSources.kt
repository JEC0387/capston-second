package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.ShopProduct
import com.jeiu.fashionproject.data.WardrobeItem

/** Display and recommendation scopes are separate, screen/session state only. */
enum class ClothingScope(val label: String, val recommendationLabel: String, val description: String) {
    ALL("전체", "내 옷 + 관심 옷", "가지고 있는 옷과 사고 싶은 옷을 함께 조합해요."),
    OWNED("내 옷", "내 옷으로만", "지금 가지고 있는 옷으로 코디해요."),
    INTERESTED("관심 옷", "관심 옷으로만", "관심 표시한 상품들끼리 코디해요.")
}

data class ClothingEntry(val owned: WardrobeItem? = null, val product: ShopProduct? = null) {
    init { require((owned != null) xor (product != null)) }
    val isInterested get() = product != null
    val key get() = owned?.let { "owned:${it.id}" } ?: "interested:${product!!.id}"
    val category get() = owned?.category ?: product?.category.orEmpty()
}

/** Does not persist synthetic candidates or guess missing clothing information. */
object OutfitSources {
    val supportedCategories = setOf("상의", "하의", "아우터", "원피스", "신발")

    fun entries(owned: List<WardrobeItem>, interested: List<ShopProduct>,
                scope: ClothingScope, category: String = "전체"): List<ClothingEntry> {
        val own = if (scope == ClothingScope.INTERESTED) emptyList() else owned.map { ClothingEntry(owned = it) }
        val wish = if (scope == ClothingScope.OWNED) emptyList() else interested
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.category in supportedCategories }
            .distinctBy { it.id }.map { ClothingEntry(product = it) }
        // No productId in WardrobeItem: never deduplicate owned/product entries by name or image.
        return (own + wish).filter { category == "전체" || it.category == category }
    }

    fun candidate(product: ShopProduct, addedAt: Long): WardrobeItem? {
        if (product.id.isBlank() || product.name.isBlank() || product.category !in supportedCategories) return null
        val name = product.clothingColorName?.takeIf { it.isNotBlank() } ?: return null
        val color = product.clothingColorValue ?: return null
        if ((color ushr 24) != 0xFFL) return null
        val photo = if (product.imageRes != 0) "android.resource://com.jeiu.fashionproject/${product.imageRes}" else product.imageUrl
        if (photo.isBlank()) return null
        return WardrobeItem(id = candidateId(product), name = product.name, category = product.category!!,
            colorName = name, colorValue = color, imageUri = photo, subCategory = product.subCategory,
            detailType = product.detailType, addedAt = addedAt, isOwned = false)
        // season/style/fit/length stay null; existing engine fallback rules apply.
    }

    fun candidateId(product: ShopProduct) = "interested-product:${product.id}"

    fun candidates(owned: List<WardrobeItem>, interested: List<ShopProduct>,
                   scope: ClothingScope, addedAt: Long): List<WardrobeItem> =
        entries(owned, interested, scope).mapNotNull { it.owned ?: candidate(it.product!!, addedAt) }

    fun emptyMessage(scope: ClothingScope, category: String): String =
        if (category != "전체") "이 범위에 ${category} 아이템이 없어요. 다른 카테고리도 확인해보세요."
        else when (scope) {
            ClothingScope.OWNED -> "아직 등록한 옷이 없어요."
            ClothingScope.INTERESTED -> "아직 관심 표시한 옷이 없어요."
            ClothingScope.ALL -> "옷을 등록하거나 관심 상품을 추가해보세요."
        }

    fun insufficientMessage(scope: ClothingScope, reason: String): String = when (scope) {
        ClothingScope.OWNED -> reason
        ClothingScope.ALL -> "선택한 내 옷과 관심 옷으로는 아직 코디를 완성하기 어려워요.\n$reason"
        ClothingScope.INTERESTED -> "관심 옷만으로 완성할 수 있는 코디가 아직 없어요.\n상의·하의·신발 등 다른 아이템을 관심 목록에 추가해보세요.\n$reason"
    }
}
