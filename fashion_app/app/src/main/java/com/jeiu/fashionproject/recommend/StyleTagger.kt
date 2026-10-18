package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 스타일 자동 태그 (계획 4 — 추천).
 *
 * 옷 이름·종류·색을 보고 미니멀 / 캐주얼 / 클래식 / 스트릿 / 빈티지 중 하나를 붙인다.
 * 대략적인 규칙이라 틀릴 수 있으므로, 옷 등록·수정 화면에서 사용자가 바꿀 수 있다. (WardrobeItem.style)
 */
object StyleTagger {

    /** 이 옷의 스타일 — 사용자가 고른 값이 있으면 그 값, 없으면 자동 규칙 */
    fun styleOf(item: WardrobeItem): String =
        item.style ?: auto(item.name, item.category, item.subCategory, item.detailType, item.colorValue)

    /**
     * 자동 규칙. 위에서부터 먼저 맞는 규칙을 쓴다.
     * 이름에 들어간 단어(체크, 카고, 데님…)가 종류보다 구체적이라 이름을 먼저 본다.
     */
    fun auto(name: String, category: String, subCategory: String?, detailType: String?, colorValue: Long): String {
        val neutral = ColorHarmony.isNeutralColor(colorValue)
        return when {
            // 1) 이름으로 판단
            listOf("체크", "코듀로이", "빈티지").any { it in name } -> "빈티지"
            listOf("카고", "후드", "트레이닝", "조거").any { it in name } -> "스트릿"
            listOf("데님", "맨투맨").any { it in name } -> "캐주얼"

            // 2) 무채색·뉴트럴 단색 기본템 → 미니멀
            neutral && detailType in setOf("니트", "반팔티", "긴팔티", "슬랙스") -> "미니멀"

            // 3) 세부 종류로 판단
            detailType in setOf("셔츠", "블라우스", "슬랙스", "로퍼", "구두", "힐", "코트", "자켓") -> "클래식"
            detailType in setOf("후드티", "트레이닝팬츠", "점퍼") -> "스트릿"
            detailType == "가디건" -> "빈티지"
            detailType in setOf("맨투맨", "청바지", "스니커즈", "운동화", "반팔티", "긴팔티", "면바지", "폴로", "니트") -> "캐주얼"

            // 4) 세부 종류가 없으면 분류로 대략 판단
            category == "원피스" -> if (neutral) "미니멀" else "클래식"
            subCategory == "치마" -> if (neutral) "미니멀" else "캐주얼"
            subCategory == "구두" -> "클래식"
            subCategory == "부츠" -> "빈티지"
            neutral -> "미니멀"
            else -> "캐주얼"
        }
    }
}
