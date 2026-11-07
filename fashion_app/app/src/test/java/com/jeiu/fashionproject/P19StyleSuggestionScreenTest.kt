package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.ui.screens.StyleSuggestionScreen
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P19StyleSuggestionScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var images: ImageLoader

    @Before fun interceptImages() {
        images = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                    ErrorResult(null, chain.request, IllegalStateException("Synthetic UI: no image IO"))
            })
        }.build()
        Coil.setImageLoader(images)
    }

    @After fun releaseImages() {
        images.shutdown()
        Coil.reset()
    }

    @Test fun changingAnalysisReplacesCardsAndCatalogClickReturnsActualId() {
        val cm = BodyMeasurements(170f, 40f, 28f, 40f, 72f, 56f, .5f, 340f, 1L)
        val result = BodyAnalysisResult("모래시계형", 1f, .7f, 1f, 72f/170f,
            emptyList(), emptyList(), 90, 1f, .7f, cm)
        val state = mutableStateOf<BodyAnalysisUiState>(BodyAnalysisUiState.Success(result, Uri.EMPTY))
        var selected: String? = null
        compose.setContent { StyleSuggestionScreen(onBack = {}, onProductClick = { selected = it },
            analysisState = state.value) }
        compose.onNodeWithText("허리선이 보이는 조합").assertExists()
        compose.onNodeWithText("성별 미선택 · 성별을 추정하지 않는 중립적인 스타일 안내예요.").assertExists()
        compose.runOnIdle { state.value = BodyAnalysisUiState.Partial("부분 분석 테스트", Uri.EMPTY, cm) }
        compose.onNodeWithText("허리선이 보이는 조합").assertDoesNotExist()
        compose.onNodeWithText("편안한 기본 조합").assertExists()
        compose.onNodeWithText("부분 분석에서는 체형별 제안을 보류해요. 부분 분석 테스트").assertExists()
        compose.onNodeWithTag("styleCatalog").performScrollTo()
        compose.onNodeWithText(ShopCatalog.products.first().name).performClick()
        compose.runOnIdle { assertEquals(ShopCatalog.products.first().id, selected) }
    }
}
