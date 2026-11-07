package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.SampleWardrobe
import com.jeiu.fashionproject.data.SampleWearHistory
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.screens.WardrobeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Uses only the Robolectric sandbox, synthetic photos and bundled resources; no server or device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WardrobeSampleRestoreUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setup() {
        resetMemory()
        SampleWearHistory.markApplied(context)
    }

    @After fun cleanup() { resetMemory() }

    // Simulate process restart without modifying production code or persisted wardrobe data.
    @Suppress("UNCHECKED_CAST")
    private fun resetMemory() {
        WardrobeStore::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(null, false)
        val state = WardrobeStore::class.java.getDeclaredField("_items").apply { isAccessible = true }
            .get(null) as MutableStateFlow<List<WardrobeItem>>
        state.value = emptyList()
    }

    @Test fun restoreBeforeFirstLoadPreservesSavedUserClothingAndItsPhoto() {
        val photo = File(context.filesDir, "wardrobe/personal.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis() - 7_200_000L)
        }
        val personal = WardrobeItem(id = "user-owned", name = "내 옷", category = "상의",
            colorName = "블랙", colorValue = 0xFF000000, imageUri = Uri.fromFile(photo).toString(),
            addedAt = 100L, favoritedAt = 200L, keptAt = 300L, brand = "내 브랜드", price = 12345)
        WardrobeStore.writeForRestore(context, listOf(personal))
        val before = context.getSharedPreferences("wardrobe_store", 0).getString("items", null)
        assertFalse(WardrobeStore.addDemoSamplesIfEmpty(context))
        assertEquals(listOf(personal), WardrobeStore.items.value)
        assertEquals(before, context.getSharedPreferences("wardrobe_store", 0).getString("items", null))
        assertArrayEquals(byteArrayOf(1, 2, 3), photo.readBytes())
    }

    @Test fun deletedSamplesStayAbsentUntilRequestedAndRestoreDoesNotDuplicateAfterRestart() {
        WardrobeStore.writeForRestore(context, emptyList())
        WardrobeStore.load(context)
        assertTrue(WardrobeStore.items.value.isEmpty())
        assertTrue(WardrobeStore.addDemoSamplesIfEmpty(context))
        val restored = WardrobeStore.items.value
        assertEquals(12, restored.size)
        assertFalse(WardrobeStore.addDemoSamplesIfEmpty(context))
        resetMemory()
        WardrobeStore.load(context)
        assertEquals(restored, WardrobeStore.items.value)
        assertFalse(WardrobeStore.addDemoSamplesIfEmpty(context))
    }

    @Test fun unreadableSavedWardrobeIsNotTreatedAsAnEmptyWardrobe() {
        val photo = File(context.filesDir, "wardrobe/recoverable.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(System.currentTimeMillis() - 7_200_000L)
        }
        val prefs = context.getSharedPreferences("wardrobe_store", 0)
        val unreadable = "[{truncated-user-data"
        prefs.edit().putString("items", unreadable).putBoolean("samples_seeded", false).commit()
        assertFalse(WardrobeStore.addDemoSamplesIfEmpty(context))
        assertEquals(unreadable, prefs.getString("items", null))
        assertFalse(prefs.getBoolean("samples_seeded", false))
        assertArrayEquals(byteArrayOf(4, 5, 6), photo.readBytes())
        assertTrue(WardrobeStore.items.value.isEmpty())
    }

    @Test fun everyBundledPhotoVersionResolvesAndDecodesForTheBuiltPackage() {
        SampleWardrobe.items().flatMap { it.photoUris() }.toSet().forEach { photo ->
            assertEquals(BuildConfig.APPLICATION_ID, Uri.parse(photo).authority)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(photo)).use { input ->
                assertNotNull("Bundled image stream: $photo", input)
                BitmapFactory.decodeStream(input, null, bounds)
            }
            assertTrue("Bundled image dimensions: $photo", bounds.outWidth > 0 && bounds.outHeight > 0)
        }
    }

    @Test fun emptyWardrobeButtonRestoresSamplesAndDisappears() {
        WardrobeStore.writeForRestore(context, emptyList())
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                    input: I, options: ActivityOptionsCompat?) = error("Sample restore must not open a photo picker")
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                WardrobeScreen(onOutfitRecommend = {}, onShop = {}, onProductDetail = {})
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("샘플 옷 12벌 추가"))
        compose.onNodeWithText("샘플 옷 12벌 추가").performClick()
        compose.runOnIdle { assertEquals(12, WardrobeStore.items.value.size) }
        compose.onNodeWithText("샘플 옷 12벌 추가").assertDoesNotExist()
    }
}
