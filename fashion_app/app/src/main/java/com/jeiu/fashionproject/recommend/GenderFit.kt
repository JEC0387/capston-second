package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 마이페이지에서 고른 성별에 맞는 옷만 추천하기 위한 필터.
 *
 * 남성으로 설정하면 원피스 · 치마(스커트) · 블라우스 · 힐처럼 여성 전용으로 보는 옷을
 * 코디 추천 · "이것만 사면" · 쇼핑 추천 상품 · 스타일 검색 결과에서 뺀다.
 * 옷장에 등록한 옷 자체는 지우지 않는다 (추천에만 쓰지 않음).
 * 여성이거나 성별을 고르지 않았으면 모두 허용한다.
 */
object GenderFit {

    private val WOMEN_SUBS = setOf("치마", "반팔 원피스", "긴팔 원피스", "민소매 원피스")
    private val WOMEN_KINDS = setOf("블라우스", "미니스커트", "롱스커트", "스커트", "힐", "원피스")
    private val WOMEN_WORDS = listOf(
        "원피스", "스커트", "치마", "블라우스", "드레스", "하이힐", "펌프스", "메리제인", "뷔스티에", "캐미솔", "브라탑"
    )

    /** 이름 · 분류로 본 여성 전용 옷인지 */
    fun isWomenOnly(category: String?, subCategory: String?, detailType: String?, name: String): Boolean =
        category == "원피스" || subCategory in WOMEN_SUBS || detailType in WOMEN_KINDS ||
            WOMEN_WORDS.any { it in name }

    fun isWomenOnly(item: WardrobeItem): Boolean =
        isWomenOnly(item.category, item.subCategory, item.detailType, item.name)

    /** 이 성별에게 추천해도 되는 옷인지 */
    fun allows(item: WardrobeItem, gender: Gender?): Boolean = gender != Gender.MALE || !isWomenOnly(item)

    /** 상품 · 검색 결과 제목처럼 이름만 있을 때 */
    fun allowsTitle(title: String, gender: Gender?): Boolean =
        gender != Gender.MALE || WOMEN_WORDS.none { it in title }

    fun filter(items: List<WardrobeItem>, gender: Gender?): List<WardrobeItem> =
        if (gender == Gender.MALE) items.filter { allows(it, gender) } else items
}
