package com.jeiu.fashionproject.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Wardrobe : Screen("wardrobe")
    /** 코디 추천 (오늘 / 5일 예보 / 상황별) — 계획 4. tab을 주면 그 탭으로 열림 (0: 오늘, 1: 5일 예보, 2: 상황별) */
    object OutfitRecommend : Screen("outfit_recommend?tab={tab}") {
        fun route(tab: Int = 0) = "outfit_recommend?tab=$tab"
    }
    object BodyAnalysis : Screen("body_analysis")
    object Shop : Screen("shop")
    object ProductDetail : Screen("product_detail")
    object MyPage : Screen("my_page")
    object StyleAnalysis : Screen("style_analysis")
    object StyleSuggestion : Screen("style_suggestion")
    object Camera : Screen("camera")
    /** AI 옷 등록 — edit(옷 번호)를 주면 그 옷의 정보 수정 화면으로 열림 */
    object AddClothing : Screen("add_clothing?edit={edit}") {
        fun route(editItemId: String? = null) = if (editItemId == null) "add_clothing" else "add_clothing?edit=$editItemId"
    }
    object StylePreference : Screen("style_preference")
    object VirtualFitting : Screen("virtual_fitting")
    /** 입은 옷 기록 — date("yyyy-MM-dd")를 주면 그 날짜로 열림, 없으면 오늘 */
    object WearLog : Screen("wear_log?date={date}") {
        fun route(date: String? = null) = if (date == null) "wear_log" else "wear_log?date=$date"
    }
    object WearCalendar : Screen("wear_calendar")
}
