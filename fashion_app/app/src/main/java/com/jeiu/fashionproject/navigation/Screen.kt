package com.jeiu.fashionproject.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Wardrobe : Screen("wardrobe")
    object WeatherOutfit : Screen("weather_outfit")
    object BodyAnalysis : Screen("body_analysis")
    object Shop : Screen("shop")
    object ProductDetail : Screen("product_detail")
    object MyPage : Screen("my_page")
    object StyleAnalysis : Screen("style_analysis")
    object StyleSuggestion : Screen("style_suggestion")
    object Camera : Screen("camera")
    object AddClothing : Screen("add_clothing")
    object StylePreference : Screen("style_preference")
    object VirtualFitting : Screen("virtual_fitting")
}
