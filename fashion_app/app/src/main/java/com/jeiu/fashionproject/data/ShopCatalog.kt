package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.R

/** Existing catalog. Card background color is NOT a garment color.
 * Categories/kinds use explicit product-title labels only. Unknown variants remain null.
 * Color RGB values represent the named navy/black labels, not measured fabric colors. */
data class ShopProduct(
    val name: String,
    val brand: String,
    val price: String,
    val originalPrice: String,
    val rating: Float,
    val reviewCount: Int,
    val color: Long,
    val tag: String? = null,
    val imageUrl: String = "",
    val purchaseUrl: String = "",
    val imageRes: Int = 0,
    val id: String = purchaseUrl,
    val category: String? = null,
    val subCategory: String? = null,
    val detailType: String? = null,
    val clothingColorName: String? = null,
    val clothingColorValue: Long? = null
)

object ShopCatalog {
    val products = listOf(
        ShopProduct("CKS 스탠다드 슬림핏 반팔 셔츠 네이비", "CHICKS", "39,000원", "39,000원", 4.7f, 184, 0xFFD5E0EC, "AI추천", "", "https://www.musinsa.com/products/6405693", R.drawable.shop_cks_shirt).copy(category = "상의", subCategory = "반팔 상의", detailType = "셔츠", clothingColorName = "네이비", clothingColorValue = 0xFF000080),
        ShopProduct("사이드 핀턱 빈티지 워싱 벌룬핏 데님 팬츠", "미스터제이슨", "8,522원", "22,000원", 4.5f, 231, 0xFFA8B8C8, null, "https://d3ha2047wt6x28.cloudfront.net/hEmVfMXLFDQ/pr:GOODS_DETAIL/czM6Ly9hYmx5LWltYWdlLWxlZ2FjeS9kYXRhL2dvb2RzLzIwMjYwMjI2XzE3NzIwNjgyMzQ2NTQ5NjRtLmpwZw", "https://4910.kr/desktop/goods/51479882").copy(category = "하의", detailType = "청바지"),
        ShopProduct("[made]썸머크루즈가디건", "어텀", "26,560원", "35,000원", 4.6f, 98, 0xFFEDE3D5, "AI추천", "https://cf.product-image.s.zigzag.kr/original/c/17/026/333/170263336-3421640085957955899.jpeg?width=400&height=400&quality=80&format=webp", "https://zigzag.kr/catalog/products/170263336").copy(category = "아우터", detailType = "가디건"),
        ShopProduct("아치스 조리샌들_블랙", "ARCHIES", "35,000원", "35,000원", 4.8f, 342, 0xFF3A3A3A, "베스트", "", "https://www.musinsa.com/products/3142059", R.drawable.shop_archies_sandal).copy(category = "신발", subCategory = "샌들", clothingColorName = "블랙", clothingColorValue = 0xFF000000),
        ShopProduct("백 사틴 오피서 팬츠 [블랙]", "무신사스탠다드", "59,900원", "59,900원", 4.8f, 512, 0xFF4A4A4A, null, "", "https://www.musinsa.com/products/3304653", R.drawable.shop_officer_pants).copy(category = "하의", clothingColorName = "블랙", clothingColorValue = 0xFF000000),
        ShopProduct("[MADE]러블린셔링블라우스 - 4color", "Ni:some", "32,200원", "42,900원", 4.9f, 284, 0xFFF5EEE8, "AI추천", "https://cf.product-image.s.zigzag.kr/original/d/2026/4/27/5875_202604271053470543_53810.jpeg?width=400&height=400&quality=80&format=webp", "https://zigzag.kr/catalog/products/161164288").copy(category = "상의", detailType = "블라우스"),
        ShopProduct("바이오워싱 크롭 데님 반팔 셔츠", "DIMITRI BLACK", "49,000원", "49,000원", 4.4f, 156, 0xFFA8C0D0, null, "", "https://www.musinsa.com/products/6317950", R.drawable.shop_denim_shirt).copy(category = "상의", subCategory = "반팔 상의", detailType = "셔츠"),
        ShopProduct("8부 원턱 버뮤다 스웨트 팬츠 3color", "NANDE", "49,000원", "49,000원", 4.5f, 203, 0xFFD5D0C8, null, "", "https://www.musinsa.com/products/6068184", R.drawable.shop_sweat_pants).copy(category = "하의"),
    )
    // Existing detail screen is a separate demo product, not the cardigan sharing its link.
    val detailProduct = ShopProduct("아이보리 라이트 베이지 브이넥 니트 J19", "저스트메리드",
        "43,800원", "43,800원", 4.8f, 284, 0xFFEDE3D5, imageRes = R.drawable.casual_daily_look,
        id = "demo:detail-knit-j19", category = "상의", detailType = "니트")
    val allProducts get() = products + detailProduct
    fun interested(ids: List<String>) = ids.mapNotNull { id -> allProducts.find { it.id == id } }
}
