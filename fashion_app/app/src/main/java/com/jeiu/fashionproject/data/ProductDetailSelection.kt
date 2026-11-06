package com.jeiu.fashionproject.data

import java.net.URI

/** One exact catalog selection supplies every detail action; no fallback or URL-as-input lookup. */
data class ProductDetailSelection private constructor(val product: ShopProduct) {
    val interestId: String get() = product.id
    val image: Any get() = if (product.imageRes != 0) product.imageRes else product.imageUrl
    val purchaseUrl: String? get() = product.purchaseUrl.takeIf { url ->
        runCatching {
            val uri = URI(url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }.getOrDefault(false)
    }
    val shareText: String get() = listOfNotNull(product.brand, product.name, purchaseUrl).joinToString("\n")

    companion object {
        fun resolve(id: String?): ProductDetailSelection? =
            id?.takeIf { it.isNotBlank() }?.let { requested ->
                ShopCatalog.allProducts.singleOrNull { it.id == requested }?.let(::ProductDetailSelection)
            }
    }
}
