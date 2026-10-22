package com.jeiu.fashionproject.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeiu.fashionproject.MainActivity
import com.jeiu.fashionproject.ui.screens.AddClothingScreen
import com.jeiu.fashionproject.ui.screens.BodyHistoryScreen
import com.jeiu.fashionproject.ui.screens.MissingItemScreen
import com.jeiu.fashionproject.ui.screens.MusinsaClosetScreen
import com.jeiu.fashionproject.ui.screens.OutfitBookScreen
import com.jeiu.fashionproject.ui.screens.OutfitRecommendScreen
import com.jeiu.fashionproject.ui.screens.StyleAnalysisScreen
import com.jeiu.fashionproject.ui.screens.StylePreferenceScreen
import com.jeiu.fashionproject.ui.screens.VirtualFittingScreen
import com.jeiu.fashionproject.ui.screens.WearCalendarScreen
import com.jeiu.fashionproject.ui.screens.WearLogScreen
import com.jeiu.fashionproject.ui.screens.WeatherOutfitScreen
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 발표 · 제출용 앱 화면 캡처 (실제 기능 검증용 테스트가 아님).
 * .github/workflows/app-screenshots.yml 이 에뮬레이터에서 이 클래스만 실행하고 PNG 를 모은다.
 * 사진은 앱 외부 파일 폴더(Android/data/<패키지>/files/shots)에 저장된다.
 */
internal object Shots {
    fun take(rule: ComposeTestRule, name: String, waitMs: Long = 3000) {
        rule.waitForIdle()
        Thread.sleep(waitMs)
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        // 시스템 대화상자(응답 없음 등)가 떠 있으면 닫는다
        runCatching { inst.uiAutomation.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS").close() }
        Thread.sleep(600)
        val bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun scroll(rule: ComposeTestRule) {
        runCatching { rule.onRoot().performTouchInput { swipeUp(durationMillis = 400) } }
    }
}

/** 실제 앱 실행 흐름: 온보딩 → 하단 탭 5개 */
@RunWith(AndroidJUnit4::class)
class A_MainTabsTour {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) {
        rule.onAllNodes(hasText(label) and isSelectable()).onFirst().performClick()
    }

    @Test fun tour() {
        Shots.take(rule, "00_onboarding", 2000)
        listOf("캐주얼", "미니멀").forEach { s -> runCatching { rule.onAllNodesWithText(s).onFirst().performClick() } }
        rule.onAllNodesWithText("시작하기").onFirst().performClick()
        Shots.take(rule, "01_home", 12000)
        Shots.scroll(rule); Shots.take(rule, "02_home_more", 2500)
        tab("옷장"); Shots.take(rule, "03_wardrobe", 3000)
        Shots.scroll(rule); Shots.take(rule, "04_wardrobe_more", 2000)
        tab("AI분석"); Shots.take(rule, "05_body_analysis", 2500)
        tab("쇼핑"); Shots.take(rule, "06_shop", 7000)
        tab("마이"); Shots.take(rule, "07_mypage", 4000)
        Shots.scroll(rule); Shots.take(rule, "08_mypage_more", 2000)
    }
}

/** 하위 화면은 화면 함수를 직접 띄워서 캡처한다 (데이터는 앱 저장소의 샘플 옷 · 착용 기록 사용). */
@RunWith(AndroidJUnit4::class)
class B_SubScreens {
    @get:Rule val rule = createComposeRule()

    @Test fun outfitToday() {
        rule.setContent { FashionProjectTheme { OutfitRecommendScreen(onBack = {}, onAddClothing = {}, weatherViewModel = viewModel(), initialTab = 0) } }
        Shots.take(rule, "10_outfit_today", 12000)
        Shots.scroll(rule); Shots.take(rule, "11_outfit_today_more", 2500)
    }

    @Test fun outfitForecast() {
        rule.setContent { FashionProjectTheme { OutfitRecommendScreen(onBack = {}, onAddClothing = {}, weatherViewModel = viewModel(), initialTab = 1) } }
        Shots.take(rule, "12_outfit_forecast", 15000)
    }

    @Test fun outfitSituation() {
        rule.setContent { FashionProjectTheme { OutfitRecommendScreen(onBack = {}, onAddClothing = {}, weatherViewModel = viewModel(), initialTab = 2) } }
        Shots.take(rule, "13_outfit_situation", 6000)
    }

    @Test fun addClothing() {
        rule.setContent { FashionProjectTheme { AddClothingScreen(onBack = {}, onDone = {}) } }
        Shots.take(rule, "14_add_clothing")
    }

    @Test fun wearLog() {
        rule.setContent { FashionProjectTheme { WearLogScreen(initialDate = null, onBack = {}, onSaved = {}) } }
        Shots.take(rule, "15_wear_log")
    }

    @Test fun wearCalendar() {
        rule.setContent { FashionProjectTheme { WearCalendarScreen(onBack = {}, onRecord = {}) } }
        Shots.take(rule, "16_wear_calendar")
        Shots.scroll(rule); Shots.take(rule, "17_wear_calendar_stats", 2000)
    }

    @Test fun outfitBook() {
        rule.setContent { FashionProjectTheme { OutfitBookScreen(onBack = {}) } }
        Shots.take(rule, "18_outfit_book")
    }

    @Test fun missingItem() {
        rule.setContent { FashionProjectTheme { MissingItemScreen(onBack = {}) } }
        Shots.take(rule, "19_missing_item", 4000)
    }

    @Test fun styleAnalysis() {
        rule.setContent { FashionProjectTheme { StyleAnalysisScreen(onBack = {}) } }
        Shots.take(rule, "20_style_analysis")
    }

    @Test fun stylePreference() {
        rule.setContent { FashionProjectTheme { StylePreferenceScreen(onBack = {}) } }
        Shots.take(rule, "21_style_preference")
    }

    @Test fun bodyHistory() {
        rule.setContent { FashionProjectTheme { BodyHistoryScreen(onBack = {}, onMeasure = {}) } }
        Shots.take(rule, "22_body_history", 4000)
    }

    @Test fun weatherOutfit() {
        rule.setContent { FashionProjectTheme { WeatherOutfitScreen(onBack = {}, onProductClick = {}, weatherViewModel = viewModel()) } }
        Shots.take(rule, "23_weather_outfit", 15000)
    }

    @Test fun musinsaCloset() {
        rule.setContent { FashionProjectTheme { MusinsaClosetScreen(onBack = {}, weatherViewModel = viewModel()) } }
        Shots.take(rule, "24_musinsa_closet", 7000)
    }

    @Test fun virtualFitting() {
        rule.setContent { FashionProjectTheme { VirtualFittingScreen(onBack = {}) } }
        Shots.take(rule, "25_virtual_fitting")
    }
}
