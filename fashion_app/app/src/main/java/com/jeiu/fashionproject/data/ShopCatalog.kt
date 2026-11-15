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
        // ── 2026 F/W 추가 상품 (무신사 상품 페이지에서 2026-11-15에 확인한 이름 · 가격 · 이미지 · 판매 링크).
        //    평점 · 후기 수는 확인한 값이 없어 0으로 둔다 (화면에 표시하지 않음). 가격은 판매처에서 바뀔 수 있다.
        ShopProduct("C 로고 버튼업 후디 집업 헤더 그레이", "커버낫", "39,000원", "109,000원", 0f, 0, 0xFFE4E4E4, null, "https://image.msscdn.net/images/goods_img/20220916/2798184/2798184_16794486965124_500.jpg", "https://www.musinsa.com/products/2798184").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "점퍼", clothingColorName = "그레이", clothingColorValue = 0xFF9E9E9E),
        ShopProduct("우먼 베이직 후드집업 아이보리", "커버낫 우먼", "69,000원", "99,000원", 0f, 0, 0xFFF3EEE4, null, "https://image.msscdn.net/images/goods_img/20260122/5924134/5924134_17749219102683_500.jpg", "https://www.musinsa.com/products/5924134").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "점퍼", clothingColorName = "아이보리", clothingColorValue = 0xFFFFFFF0),
        ShopProduct("릴렉스드 베이식 블레이저 [블랙]", "무신사 스탠다드", "89,890원", "99,900원", 0f, 0, 0xFFDADADA, null, "https://image.msscdn.net/images/goods_img/20200820/1558197/1558197_17791521338764_500.jpg", "https://www.musinsa.com/products/1558197").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "자켓", clothingColorName = "블랙", clothingColorValue = 0xFF000000),
        ShopProduct("우먼즈 울 블렌드 오버 숄더 피 코트 [네이비]", "무신사 스탠다드 우먼", "125,930원", "179,900원", 0f, 0, 0xFFD5DCE8, null, "https://image.msscdn.net/images/goods_img/20240620/4209428/4209428_17358863150832_500.jpg", "https://www.musinsa.com/products/4209428").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "코트", clothingColorName = "네이비", clothingColorValue = 0xFF000080),
        ShopProduct("우먼즈 오버사이즈 블레이저 [차콜 그레이]", "무신사 스탠다드 우먼", "84,920원", "99,900원", 0f, 0, 0xFFDCDCDC, null, "https://image.msscdn.net/images/goods_img/20210217/1797621/1797621_17537685852943_500.jpg", "https://www.musinsa.com/products/1797621").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "자켓", clothingColorName = "차콜", clothingColorValue = 0xFF36454F),
        ShopProduct("남성 1996 레트로 눕시 다운 자켓 LIGHT GRAY", "노스페이스", "419,000원", "419,000원", 0f, 0, 0xFFE6E8EA, null, "https://image.msscdn.net/images/goods_img/20261019/7553781/7553781_17942988298801_500.jpg", "https://www.musinsa.com/products/7553781").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "패딩", clothingColorName = "그레이", clothingColorValue = 0xFFD3D3D3),
        ShopProduct("여성 1996 레트로 눕시 다운 자켓 OFF WHITE", "노스페이스", "419,000원", "419,000원", 0f, 0, 0xFFF5F2EA, null, "https://image.msscdn.net/images/goods_img/20261028/7643110/7643110_17931683934732_500.jpg", "https://www.musinsa.com/products/7643110").copy(category = "아우터", subCategory = "긴팔 아우터", detailType = "패딩", clothingColorName = "화이트", clothingColorValue = 0xFFFAF9F6),
        ShopProduct("우먼즈 린넨 블렌드 리브드 브이 넥 시어 니트 [멜란지 라이트 그레이]", "무신사 스탠다드 우먼", "35,910원", "39,900원", 0f, 0, 0xFFE8E8E6, null, "https://image.msscdn.net/images/goods_img/20251029/5661404/5661404_17756386930506_500.jpg", "https://www.musinsa.com/products/5661404").copy(category = "상의", subCategory = "긴팔 상의", detailType = "니트", clothingColorName = "그레이", clothingColorValue = 0xFFC8C8C8),
        ShopProduct("우먼즈 나일론 루치드 롱 스커트 [애쉬 그레이]", "무신사 스탠다드 우먼", "44,880원", "59,900원", 0f, 0, 0xFFE2E2E0, null, "https://image.msscdn.net/images/goods_img/20251029/5661619/5661619_17732099705913_500.jpg", "https://www.musinsa.com/products/5661619").copy(category = "하의", subCategory = "치마", detailType = "롱스커트", clothingColorName = "그레이", clothingColorValue = 0xFFB2BEB5),
        ShopProduct("무브쉘 와이드 더블니 팬츠 (2color)", "무신사 스탠다드 스포츠", "109,900원", "109,900원", 0f, 0, 0xFFDDDAD3, null, "https://image.msscdn.net/images/goods_img/20251121/5761264/5761264_17721679877602_500.jpg", "https://www.musinsa.com/products/5761264").copy(category = "하의", subCategory = "긴바지", detailType = "트레이닝팬츠"),
        ShopProduct("삼바 OG - 아우라커피:화이트 / KI6956", "아디다스", "100,990원", "169,000원", 0f, 0, 0xFFEDE4DA, null, "https://image.msscdn.net/images/goods_img/20260312/6123606/6123606_17733675871310_500.jpg", "https://www.musinsa.com/products/6123606").copy(category = "신발", subCategory = "운동화", detailType = "스니커즈", clothingColorName = "브라운", clothingColorValue = 0xFF6F4E37),
    )
    // Existing detail screen is a separate demo product, not the cardigan sharing its link.
    val detailProduct = ShopProduct("아이보리 라이트 베이지 브이넥 니트 J19", "저스트메리드",
        "43,800원", "43,800원", 4.8f, 284, 0xFFEDE3D5, imageRes = R.drawable.casual_daily_look,
        id = "demo:detail-knit-j19", category = "상의", detailType = "니트")
    val allProducts get() = products + detailProduct
    fun interested(ids: List<String>) = ids.mapNotNull { id -> allProducts.find { it.id == id } }
}
