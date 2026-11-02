package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 마이페이지에서 고른 성별에 맞는 옷을 추천하기 위한 규칙 — 남성은 남성에게, 여성은 여성에게 맞는 코디.
 *
 *  - 남성: 원피스 · 치마(스커트) · 블라우스 · 힐처럼 여성 전용으로 보는 옷은 추천하지 않는다.
 *  - 여성: 이름에 "남성 · 남자 · 맨즈 · 옴므"가 붙은 남성 전용 옷은 추천하지 않고,
 *          원피스 · 스커트 · 블라우스가 들어간 코디는 조금 더 위로 올린다 ([fitBonus]).
 *  - 성별을 고르지 않았으면 모두 허용한다.
 * 코디 추천 · "이것만 사면" · 쇼핑 추천 상품 · 스타일 검색 결과에 같이 쓴다. 옷장에 등록한 옷 자체는 지우지 않는다.
 */
object GenderFit {

    private val WOMEN_SUBS = setOf("치마", "반팔 원피스", "긴팔 원피스", "민소매 원피스")
    private val WOMEN_KINDS = setOf("블라우스", "미니스커트", "롱스커트", "스커트", "힐", "원피스")
    private val WOMEN_WORDS = listOf(
        "원피스", "스커트", "치마", "블라우스", "드레스", "하이힐", "펌프스", "메리제인", "뷔스티에", "캐미솔", "브라탑"
    )
    private val MEN_WORDS = listOf("남성", "남자", "맨즈", "옴므")
    private val MEN_EN = Regex("(?<![a-z])men'?s(?![a-z])")   // men's · mens (women's 는 아님)

    /** 여성 코디에서 이 옷이 들어가면 더하는 점수 (코디당 최대 [FEMALE_BONUS_MAX]) */
    const val FEMALE_BONUS = 0.3
    const val FEMALE_BONUS_MAX = 0.6

    /** 이름 · 분류로 본 여성 전용 옷인지 */
    fun isWomenOnly(category: String?, subCategory: String?, detailType: String?, name: String): Boolean =
        category == "원피스" || subCategory in WOMEN_SUBS || detailType in WOMEN_KINDS ||
            WOMEN_WORDS.any { it in name }

    fun isWomenOnly(item: WardrobeItem): Boolean =
        isWomenOnly(item.category, item.subCategory, item.detailType, item.name)

    /** 이름으로 본 남성 전용 옷인지 */
    fun isMenOnly(name: String): Boolean = name.lowercase().let { low -> MEN_WORDS.any { it in low } || MEN_EN.containsMatchIn(low) }

    /** 이 성별에게 추천해도 되는 옷(상품)인지 */
    fun allows(category: String?, subCategory: String?, detailType: String?, name: String, gender: Gender?): Boolean =
        when (gender) {
            Gender.MALE -> !isWomenOnly(category, subCategory, detailType, name)
            Gender.FEMALE -> !isMenOnly(name)
            null -> true
        }

    fun allows(item: WardrobeItem, gender: Gender?): Boolean =
        allows(item.category, item.subCategory, item.detailType, item.name, gender)

    /** 검색 결과 제목처럼 이름만 있을 때 */
    fun allowsTitle(title: String, gender: Gender?): Boolean = when (gender) {
        Gender.MALE -> WOMEN_WORDS.none { it in title }
        Gender.FEMALE -> !isMenOnly(title)
        null -> true
    }

    fun filter(items: List<WardrobeItem>, gender: Gender?): List<WardrobeItem> =
        if (gender == null) items else items.filter { allows(it, gender) }

    /** 성별에 더 잘 맞는 코디에 주는 점수 — 여성: 원피스 · 스커트 · 블라우스가 들어간 코디 */
    fun fitBonus(items: List<WardrobeItem>, gender: Gender?): Double =
        if (gender != Gender.FEMALE) 0.0
        else (items.count { isWomenOnly(it) } * FEMALE_BONUS).coerceAtMost(FEMALE_BONUS_MAX)
}
