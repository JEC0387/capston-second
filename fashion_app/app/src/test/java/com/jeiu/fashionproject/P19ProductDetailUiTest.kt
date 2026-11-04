package com.jeiu.fashionproject

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.ui.screens.ProductDetailScreen
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Synthetic sandbox only: intents are recorded by Robolectric; all image fetches are intercepted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P19ProductDetailUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var images: ImageLoader

    @Before fun setup() {
        resetInterestMemory()
        // Plain Application intentionally does not initialize DbSync or authentication/network services.
        images = ImageLoader.Builder(context).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                    ErrorResult(null, chain.request, IllegalStateException("Synthetic UI: no image IO"))
            })
        }.build()
        Coil.setImageLoader(images)
    }

    @After fun cleanup() {
        images.shutdown()
        Coil.reset()
        resetInterestMemory()
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetInterestMemory() {
        ProductInterestStore::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(null, false)
        val ids = ProductInterestStore::class.java.getDeclaredField("_ids").apply { isAccessible = true }
            .get(null) as MutableStateFlow<List<String>>
        ids.value = emptyList()
    }

    @Test fun openingAThenBThenMissingKeepsVisibleDetailsAndAllActionsOnSelectedProduct() {
        val a = ShopCatalog.products[0]
        val b = ShopCatalog.products[3]
        val selected = mutableStateOf<String?>(a.id)
        compose.setContent {
            FashionProjectTheme {
                ProductDetailScreen(selected.value, onBack = {}, onProductClick = { selected.value = it })
            }
        }
        compose.runOnIdle { while (shadowOf(context).nextStartedActivity != null) { /* Test host startup only. */ } }
        listOf(a, b).forEach { product ->
            compose.runOnIdle { selected.value = product.id }
            compose.onNodeWithText(product.brand).assertIsDisplayed()
            compose.onNodeWithText(product.name).assertIsDisplayed()
            compose.onNodeWithContentDescription(product.name).assertIsDisplayed()
            compose.onNodeWithText(product.price).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithContentDescription("관심 표시")[0].performClick()
            compose.runOnIdle { assertTrue(product.id in ProductInterestStore.ids.value) }

            compose.onNodeWithContentDescription("공유").performClick()
            compose.runOnIdle {
                val chooser = shadowOf(context).nextStartedActivity
                assertNotNull(chooser)
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                @Suppress("DEPRECATION")
                val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                assertNotNull(share)
                assertEquals(Intent.ACTION_SEND, share!!.action)
                assertEquals("${product.brand}\n${product.name}\n${product.purchaseUrl}", share.getStringExtra(Intent.EXTRA_TEXT))
            }
            compose.onNodeWithText("판매처에서 확인").performClick()
            compose.runOnIdle {
                val purchase = shadowOf(context).nextStartedActivity
                assertNotNull(purchase)
                assertEquals(Intent.ACTION_VIEW, purchase.action)
                assertEquals(product.purchaseUrl, purchase.dataString)
            }
        }
        compose.runOnIdle {
            assertEquals(listOf(a.id, b.id), ProductInterestStore.ids.value)
            selected.value = "missing-catalog-id"
        }
        compose.onNodeWithText("상품을 찾을 수 없어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("공유").assertDoesNotExist()
        compose.onNodeWithText("판매처에서 확인").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("관심 표시").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("관심 해제").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(a.id, b.id), ProductInterestStore.ids.value)
            assertNull(shadowOf(context).nextStartedActivity)
        }
    }
}
