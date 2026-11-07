package com.jeiu.fashionproject

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.navigation.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Uses Navigation's actual route matcher/argument decoding, not a second hand-written decoder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class P19ProductNavigationTest {
    private fun controller() = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = "shop") {
            composable("shop") {}
            composable(Screen.ProductDetail.route, arguments = listOf(
                navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )) {}
        }
    }

    @Test fun catalogueProductsKeepDifferentExactIdsInBackStack() {
        val nav = controller()
        val first = ShopCatalog.products[0].id
        val second = ShopCatalog.products[1].id
        nav.navigate(Screen.ProductDetail.route(first))
        val firstEntry = nav.currentBackStackEntry!!
        nav.navigate(Screen.ProductDetail.route(second))
        assertEquals(second, nav.currentBackStackEntry!!.arguments!!.getString("productId"))
        assertEquals(first, firstEntry.arguments!!.getString("productId"))
        assertNotSame(firstEntry, nav.currentBackStackEntry)
    }

    @Test fun urlReservedCharactersAndEncodedLiteralsSurviveExactlyOnce() {
        val nav = controller()
        listOf("https://example.test/a/b?q=한 글+자&next=%2F%25#section", "demo:detail-knit-j19", "bad%2Fid", "").forEach { id ->
            nav.navigate(Screen.ProductDetail.route(id))
            assertEquals(id, nav.currentBackStackEntry!!.arguments!!.getString("productId"))
        }
    }

    @Test fun missingArgumentStaysMissingInsteadOfSelectingAProduct() {
        val nav = controller()
        nav.navigate("product_detail")
        assertNull(nav.currentBackStackEntry!!.arguments?.getString("productId"))
    }
}
