package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.ui.components.OutfitCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 홈 "오늘의 AI 코디"처럼 나란히 놓으면 글 길이가 달라도 카드 높이가 같다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UniformOutfitCardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun item(id: String, category: String, name: String) =
        WardrobeItem(id = id, name = name, category = category, colorName = "블랙", colorValue = 0xFF222222, imageUri = "")

    @Test fun sideBySideCardsShareTheTallestHeight() {
        val short = OutfitEngine.Outfit(listOf(item("a", "상의", "셔츠"), item("b", "하의", "슬랙스"), item("c", "신발", "로퍼")),
            1.0, listOf("짧은 이유"), "클래식", title = "단정한 셔츠 룩")
        val long = OutfitEngine.Outfit(
            listOf(item("d", "아우터", "아주 긴 이름의 울 블렌드 오버사이즈 블레이저"), item("e", "상의", "니트"),
                item("f", "하의", "청바지"), item("g", "신발", "스니커즈")),
            1.0, List(4) { "옷과 날씨를 함께 보고 고른 꽤 긴 추천 이유 문장이 여기에 들어가요 $it" }, "캐주얼", title = "편안한 블레이저 룩")
        compose.setContent {
            Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max)) {
                OutfitCard(short, rated = null, onRate = {}, modifier = Modifier.width(260.dp).fillMaxHeight().testTag("short"),
                    saved = false, onPlan = {}, uniform = true)
                OutfitCard(long, rated = true, onRate = {}, modifier = Modifier.width(260.dp).fillMaxHeight().testTag("long"),
                    saved = false, onPlan = {}, uniform = true)
            }
        }
        val a = compose.onNodeWithTag("short").getBoundsInRoot()
        val b = compose.onNodeWithTag("long").getBoundsInRoot()
        assertEquals((b.bottom - b.top).value, (a.bottom - a.top).value, 0.5f)
    }
}
