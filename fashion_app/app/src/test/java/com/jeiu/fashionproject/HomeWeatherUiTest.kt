package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.screens.HomeWeatherCard
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.data.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
// Legacy graphics assigns one pixel per character; wrapping/width assertions need actual font metrics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeWeatherUiTest {
    @get:Rule val compose=createComposeRule()

    @Test @Config(qualifiers="w384dp-h832dp")
    fun reportedCloudSentenceKeepsActionOnOneLineAndCardCompactAtS25LogicalWidth() {
        val reported=WeatherUiState.Success(WeatherInfo("약간의 구름이 낀 하늘",17,55,801))
        var state by mutableStateOf<WeatherUiState>(reported)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
                FashionProjectTheme {
                    Box(Modifier.width(384.dp).testTag("weatherFixture")) { HomeWeatherCard(state,{}) }
                }
            }
        }
        fun verifyReportedLayout() {
            compose.onNodeWithText("약간의 구름이 낀 하늘 · 17°C · 습도 55%",useUnmergedTree=true).assertIsDisplayed()
            compose.onNodeWithText("활동하기 좋은 날씨예요. 긴소매 셔츠나 얇은 니트를 추천해요",useUnmergedTree=true).assertIsDisplayed()
            val summary=compose.onNodeWithTag("homeWeatherSummary",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val action=compose.onNodeWithTag("homeWeatherAction",useUnmergedTree=true).assertIsDisplayed()
            val bounds=action.fetchSemanticsNode().boundsInRoot
            val layouts=mutableListOf<TextLayoutResult>()
            action.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertEquals("The reported action must not become a vertical character column",1,layouts.single().lineCount)
            assertTrue("Action text needs readable width, actual ${bounds.width}dp",bounds.width>=64f)
            assertTrue(summary.right<=bounds.left)
            assertTrue(bounds.right<=384f)
            val card=compose.onNodeWithTag("weatherFixture").fetchSemanticsNode().boundsInRoot
            assertTrue("This exact normal-font weather card must not grow into a tall column: ${card.height}dp",card.height<=180f)
        }
        verifyReportedLayout()
        // State replacement is tested without assuming when the user originally saw the symptom.
        compose.runOnIdle { state=WeatherUiState.Loading }
        compose.waitForIdle()
        compose.runOnIdle { state=reported }
        verifyReportedLayout()
    }

    @Test fun weatherAndActionDoNotOverlapAtNarrowWidthsAndSystemFontScales() {
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(320)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            Box(Modifier.width(width.dp)) {
                HomeWeatherCard(WeatherUiState.Error("날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해주세요."),{})
            }
        } }
        for (w in listOf(320,360)) for (s in listOf(1f,1.3f,1.5f)) {
            compose.runOnIdle { width=w;scale=s }
            val summary=compose.onNodeWithTag("homeWeatherSummary",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val action=compose.onNodeWithTag("homeWeatherAction",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(summary.right<=action.left)
            assertTrue(action.right<=w)
        }
    }
}
