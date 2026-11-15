package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.screens.ShopScreen
import com.jeiu.fashionproject.ui.viewmodel.ShopViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShopCatalogueDisclosureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingKeepsTheShortNoticeAndOpeningDetailsKeepsTheGridPosition() {
        val viewModel = ShopViewModel { _, _ -> emptyList() }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.size(360.dp, 620.dp)) {
                    ShopScreen(onProductClick = {}, viewModel = viewModel)
                }
            }
        }
        compose.onNodeWithTag("shopCatalogueDetails").assertIsDisplayed()
        val grid = compose.onNodeWithTag("shopCatalogueGrid")
        grid.performScrollToIndex(5)
        compose.onNodeWithTag("shopCatalogueDetails").assertDoesNotExist()
        compose.onNodeWithText("가격·재고는 실시간이 아니에요.", substring = true).assertIsDisplayed()
        val visibleProducts = hasTestTagPrefix("shopProduct-")
        val before = compose.onAllNodes(visibleProducts).fetchSemanticsNodes().map { it.positionInRoot }
        org.junit.Assert.assertTrue(before.isNotEmpty())
        compose.onNodeWithTag("shopCatalogueDisclosure").performClick()
        compose.onNodeWithTag("shopCatalogueDetails").assertIsDisplayed()
        org.junit.Assert.assertEquals(before, compose.onAllNodes(visibleProducts).fetchSemanticsNodes().map { it.positionInRoot })
        compose.onNodeWithTag("shopCatalogueDisclosure").performClick()
        grid.performScrollToIndex(0)
        compose.onNodeWithTag("shopCatalogueDetails").assertIsDisplayed()
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith(prefix)
    }
}
