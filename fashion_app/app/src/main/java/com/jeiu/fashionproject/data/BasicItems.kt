package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.R

/**
 * "이것만 사면 코디 완성"의 후보 옷 (계획 4 — 5단계).
 *
 * 옷장에 가상으로 하나씩 넣어 보고, 좋은 코디가 가장 많이 늘어나는 옷을 추천한다.
 *  - 기본템: 어디에나 잘 어울리는 무난한 옷 약 30개 (사진 없음 → 화면에서는 색 칸으로 표시)
 *  - 쇼핑 상품: 처음 8개 상품 + 쇼핑 화면의 색 정보가 있는 실제 판매 상품 (추천되면 [상품 보기])
 *  - 기본템은 사진이 없어서, 같은 종류의 판매 상품 · 샘플 옷 사진을 "예시"로 보여 준다 (exampleOf)
 * 계절은 규칙(ClothingCategories.season) 대신 옷마다 직접 적었다. (트렌치코트는 봄·가을 등)
 */
object BasicItems {

    /** 쇼핑 상품 후보 — [image]는 앱 안 사진(R.drawable) 또는 사진 주소 */
    data class ShopCandidate(val item: WardrobeItem, val url: String, val image: Any, val productId: String? = null)

    private fun basic(
        id: String, name: String, category: String, sub: String, detail: String?,
        colorName: String, color: Long, season: String
    ) = WardrobeItem(
        id = "basic_$id", name = name, category = category, colorName = colorName, colorValue = color,
        imageUri = "", subCategory = sub, detailType = detail, season = season
    )

    val basics: List<WardrobeItem> = listOf(
        // 상의
        basic("01", "화이트 셔츠", "상의", "긴팔 상의", "셔츠", "화이트", 0xFFF4F4F2, "사계절"),
        basic("02", "스카이블루 셔츠", "상의", "긴팔 상의", "셔츠", "블루", 0xFFA9C4E0, "사계절"),
        basic("03", "화이트 블라우스", "상의", "긴팔 상의", "블라우스", "화이트", 0xFFF2EFEA, "사계절"),
        basic("04", "네이비 니트", "상의", "긴팔 상의", "니트", "네이비", 0xFF2B3550, "사계절"),
        basic("05", "그레이 니트", "상의", "긴팔 상의", "니트", "그레이", 0xFF9A9A9C, "사계절"),
        basic("06", "블랙 긴팔티", "상의", "긴팔 상의", "긴팔티", "블랙", 0xFF222222, "사계절"),
        basic("07", "그레이 맨투맨", "상의", "긴팔 상의", "맨투맨", "그레이", 0xFF8E8E90, "봄·가을"),
        basic("08", "그레이 후드티", "상의", "긴팔 상의", "후드티", "그레이", 0xFF7D7D80, "봄·가을"),
        basic("09", "화이트 반팔티", "상의", "반팔 상의", "반팔티", "화이트", 0xFFF2F2F2, "여름"),
        basic("10", "블랙 반팔티", "상의", "반팔 상의", "반팔티", "블랙", 0xFF202020, "여름"),
        // 하의
        basic("11", "베이지 슬랙스", "하의", "긴바지", "슬랙스", "베이지", 0xFFC8B79E, "사계절"),
        basic("12", "그레이 슬랙스", "하의", "긴바지", "슬랙스", "그레이", 0xFF8A8B8D, "사계절"),
        basic("13", "블랙 슬랙스", "하의", "긴바지", "슬랙스", "블랙", 0xFF1E1E1E, "사계절"),
        basic("14", "연청 청바지", "하의", "긴바지", "청바지", "블루", 0xFF8FA6BF, "사계절"),
        basic("15", "진청 청바지", "하의", "긴바지", "청바지", "네이비", 0xFF34465E, "사계절"),
        basic("16", "카키 면바지", "하의", "긴바지", "면바지", "그린", 0xFF7A7458, "사계절"),
        basic("17", "블랙 롱스커트", "하의", "치마", "롱스커트", "블랙", 0xFF1E1E1E, "사계절"),
        basic("18", "그레이 트레이닝팬츠", "하의", "긴바지", "트레이닝팬츠", "그레이", 0xFF8A8A8A, "사계절"),
        basic("19", "블랙 반바지", "하의", "반바지", null, "블랙", 0xFF222222, "여름"),
        // 원피스
        basic("20", "블랙 원피스", "원피스", "긴팔 원피스", null, "블랙", 0xFF1E1E1E, "사계절"),
        // 아우터
        basic("21", "네이비 가디건", "아우터", "긴팔 아우터", "가디건", "네이비", 0xFF283350, "봄·가을"),
        basic("22", "베이지 트렌치코트", "아우터", "긴팔 아우터", "코트", "베이지", 0xFFC9B48F, "봄·가을"),
        basic("23", "네이비 블레이저", "아우터", "긴팔 아우터", "자켓", "네이비", 0xFF232C45, "봄·가을"),
        basic("24", "데님 자켓", "아우터", "긴팔 아우터", "자켓", "블루", 0xFF6F86A3, "봄·가을"),
        basic("25", "블랙 코트", "아우터", "긴팔 아우터", "코트", "블랙", 0xFF1C1C1C, "겨울"),
        basic("26", "블랙 패딩", "아우터", "긴팔 아우터", "패딩", "블랙", 0xFF1A1A1A, "겨울"),
        // 신발
        basic("27", "화이트 스니커즈", "신발", "운동화", "스니커즈", "화이트", 0xFFF0F0F0, "사계절"),
        basic("28", "러닝화", "신발", "운동화", "운동화", "그레이", 0xFF9A9A9A, "사계절"),
        basic("29", "블랙 로퍼", "신발", "구두", "로퍼", "블랙", 0xFF1E1E1E, "사계절"),
        basic("30", "브라운 로퍼", "신발", "구두", "로퍼", "브라운", 0xFF5C3A22, "사계절"),
        basic("31", "블랙 부츠", "신발", "부츠", null, "블랙", 0xFF1E1E1E, "겨울"),
    )

    val shopItems: List<ShopCandidate> = listOf(
        ShopCandidate(basic("s1", "CKS 반팔 셔츠 네이비", "상의", "반팔 상의", "셔츠", "네이비", 0xFF27324A, "여름"),
            "https://www.musinsa.com/products/6405693", R.drawable.shop_cks_shirt),
        ShopCandidate(basic("s2", "핀턱 워싱 데님 팬츠", "하의", "긴바지", "청바지", "블루", 0xFF8CA0B4, "사계절"),
            "https://4910.kr/desktop/goods/51479882",
            "https://d3ha2047wt6x28.cloudfront.net/hEmVfMXLFDQ/pr:GOODS_DETAIL/czM6Ly9hYmx5LWltYWdlLWxlZ2FjeS9kYXRhL2dvb2RzLzIwMjYwMjI2XzE3NzIwNjgyMzQ2NTQ5NjRtLmpwZw"),
        ShopCandidate(basic("s3", "썸머크루즈 가디건", "아우터", "긴팔 아우터", "가디건", "베이지", 0xFFEDE3D5, "봄·가을"),
            "https://zigzag.kr/catalog/products/170263336",
            "https://cf.product-image.s.zigzag.kr/original/c/17/026/333/170263336-3421640085957955899.jpeg?width=400&height=400&quality=80&format=webp"),
        ShopCandidate(basic("s4", "아치스 조리샌들 블랙", "신발", "샌들", null, "블랙", 0xFF3A3A3A, "여름"),
            "https://www.musinsa.com/products/3142059", R.drawable.shop_archies_sandal),
        ShopCandidate(basic("s5", "백 사틴 오피서 팬츠 블랙", "하의", "긴바지", "슬랙스", "블랙", 0xFF2A2A2A, "사계절"),
            "https://www.musinsa.com/products/3304653", R.drawable.shop_officer_pants),
        ShopCandidate(basic("s6", "러블린 셔링 블라우스", "상의", "긴팔 상의", "블라우스", "화이트", 0xFFF5EEE8, "사계절"),
            "https://zigzag.kr/catalog/products/161164288",
            "https://cf.product-image.s.zigzag.kr/original/d/2026/4/27/5875_202604271053470543_53810.jpeg?width=400&height=400&quality=80&format=webp"),
        ShopCandidate(basic("s7", "크롭 데님 반팔 셔츠", "상의", "반팔 상의", "셔츠", "블루", 0xFF7F9AB0, "여름"),
            "https://www.musinsa.com/products/6317950", R.drawable.shop_denim_shirt),
        ShopCandidate(basic("s8", "버뮤다 스웨트 팬츠", "하의", "반바지", null, "그레이", 0xFFBDB8B0, "여름"),
            "https://www.musinsa.com/products/6068184", R.drawable.shop_sweat_pants),
    )

    /** 쇼핑 화면 판매 상품 중 색 정보가 있는 것 (처음 8개와 겹치는 상품은 뺀다) */
    val catalogItems: List<ShopCandidate> by lazy {
        val known = shopItems.map { it.url }.toSet()
        ShopCatalog.products.filter { it.purchaseUrl !in known }.mapNotNull { p ->
            com.jeiu.fashionproject.recommend.OutfitSources.candidate(p, 1L)?.let { item ->
                ShopCandidate(item, p.purchaseUrl, if (p.imageRes != 0) p.imageRes else p.imageUrl, p.id)
            }
        }
    }

    /** 후보 전체 (기본템 + 쇼핑 상품 8개) — 상황별 부족 아이템 계산용 (화면에서 바로 계산하므로 가볍게) */
    val all: List<WardrobeItem> get() = basics + shopItems.map { it.item }

    /** TOP 3 계산용 후보 — 기본템 + 모든 판매 상품 */
    val allWithCatalog: List<WardrobeItem> get() = all + catalogItems.map { it.item }

    fun shopOf(item: WardrobeItem): ShopCandidate? =
        shopItems.find { it.item.id == item.id } ?: catalogItems.find { it.item.id == item.id }

    /** 예시 사진 — [productId]가 있으면 그 판매 상품 사진 */
    data class Example(val image: Any, val productId: String?)

    /**
     * 사진이 없는 기본템의 예시 사진: 같은 종류 · 같은 색 판매 상품 → 같은 종류 판매 상품 → 같은 종류 샘플 옷.
     * 판매 상품은 성별에 맞는 것만. 찾지 못하면 null (화면에서 색 칸으로 표시).
     */
    fun exampleOf(item: WardrobeItem, gender: Gender? = null): Example? {
        if (item.imageUri.isNotEmpty() || shopOf(item) != null) return null
        val kind = item.detailType ?: item.subCategory
        val products = ShopCatalog.products.filter {
            it.category == item.category && (it.detailType ?: it.subCategory) == kind &&
                com.jeiu.fashionproject.recommend.GenderFit.allows(it.category, it.subCategory, it.detailType, it.name, gender)
        }
        (products.firstOrNull { it.clothingColorName == item.colorName } ?: products.firstOrNull())?.let {
            return Example(if (it.imageRes != 0) it.imageRes else it.imageUrl, it.id)
        }
        return SampleWardrobe.items().firstOrNull {
            it.category == item.category && (it.detailType ?: it.subCategory) == kind
        }?.let { Example(it.imageUri, null) }
    }
}
