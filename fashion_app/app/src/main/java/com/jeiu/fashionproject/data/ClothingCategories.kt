package com.jeiu.fashionproject.data

/**
 * 옷 분류 사전 (계획 3 — 옷장).
 *
 *  대분류(category) → 소분류(subCategory) → 세부 종류(detailType)
 *  예) 상의 → 긴팔 상의 → 니트
 *
 * AI(DeepFashion2)는 소분류까지만 알 수 있고, 셔츠·니트처럼 소재/디자인 차이는 구분하지 못하므로
 * 세부 종류는 사용자가 칩으로 고른다. 계절은 규칙으로 자동 지정하고 사용자가 고칠 수 있다.
 */
object ClothingCategories {

    val categories = listOf("상의", "하의", "아우터", "원피스", "신발", "액세서리")

    val subCategories: Map<String, List<String>> = mapOf(
        "상의" to listOf("반팔 상의", "긴팔 상의", "민소매"),
        "하의" to listOf("반바지", "긴바지", "치마"),
        "아우터" to listOf("반팔 아우터", "긴팔 아우터"),
        "원피스" to listOf("반팔 원피스", "긴팔 원피스", "민소매 원피스"),
        "신발" to listOf("운동화", "구두", "샌들", "부츠"),
        "액세서리" to listOf("가방", "모자", "기타"),
    )

    val detailTypes: Map<String, List<String>> = mapOf(
        "반팔 상의" to listOf("반팔티", "셔츠", "블라우스", "폴로"),
        "긴팔 상의" to listOf("긴팔티", "셔츠", "블라우스", "니트", "맨투맨", "후드티"),
        "긴팔 아우터" to listOf("자켓", "코트", "패딩", "가디건", "점퍼"),
        "긴바지" to listOf("청바지", "슬랙스", "면바지", "트레이닝팬츠"),
        "치마" to listOf("미니스커트", "롱스커트"),
        "가방" to listOf("핸드백", "백팩", "캐리어"),
        // 신발 세부 종류 (계획 4 — 상황별 코디에서 운동화·구두 등을 구분할 때 사용)
        "운동화" to listOf("운동화", "스니커즈"),
        "구두" to listOf("로퍼", "구두", "힐"),
    )

    val seasons = listOf("봄·가을", "여름", "겨울", "사계절")

    /** AI가 알려준 종류 → 앱 분류. detailType은 가방처럼 AI가 세부 종류까지 아는 경우에만 채운다. */
    data class Mapping(val nameKo: String, val category: String, val subCategory: String, val detailType: String? = null)

    fun fromModelLabel(label: String): Mapping? = when (label) {
        // DeepFashion2 13종류
        "short_sleeved_shirt" -> Mapping("반팔 상의", "상의", "반팔 상의")
        "long_sleeved_shirt" -> Mapping("긴팔 상의", "상의", "긴팔 상의")
        "vest", "sling" -> Mapping("민소매", "상의", "민소매")
        "short_sleeved_outwear" -> Mapping("반팔 아우터", "아우터", "반팔 아우터")
        "long_sleeved_outwear" -> Mapping("긴팔 아우터", "아우터", "긴팔 아우터")
        "shorts" -> Mapping("반바지", "하의", "반바지")
        "trousers" -> Mapping("긴바지", "하의", "긴바지")
        "skirt" -> Mapping("치마", "하의", "치마")
        "short_sleeved_dress" -> Mapping("반팔 원피스", "원피스", "반팔 원피스")
        "long_sleeved_dress" -> Mapping("긴팔 원피스", "원피스", "긴팔 원피스")
        "vest_dress", "sling_dress" -> Mapping("민소매 원피스", "원피스", "민소매 원피스")
        // COCO 가방류
        "handbag" -> Mapping("핸드백", "액세서리", "가방", "핸드백")
        "backpack" -> Mapping("백팩", "액세서리", "가방", "백팩")
        "suitcase" -> Mapping("캐리어", "액세서리", "가방", "캐리어")
        else -> null
    }

    /** 계절 규칙 — 세부 종류를 먼저 보고, 없으면 소분류로 판단. */
    fun season(subCategory: String?, detailType: String?): String = when {
        detailType in setOf("패딩", "코트") -> "겨울"
        detailType in setOf("자켓", "가디건", "점퍼", "맨투맨", "후드티") -> "봄·가을"
        subCategory in setOf("반팔 상의", "민소매", "반바지", "반팔 아우터", "반팔 원피스", "민소매 원피스", "샌들") -> "여름"
        else -> "사계절"
    }
}
