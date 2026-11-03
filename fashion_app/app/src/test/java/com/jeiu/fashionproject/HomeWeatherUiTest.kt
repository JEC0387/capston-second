package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.screens.HomeWeatherCard
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class HomeWeatherUiTest {
    @get:Rule val compose=createComposeRule()
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
