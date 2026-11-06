package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.SampleWearHistory
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.viewmodel.ClosetPhotoRules
import com.jeiu.fashionproject.ui.viewmodel.ClosetPhotoUnifier
import com.jeiu.fashionproject.ui.viewmodel.AddClothingViewModel
import com.jeiu.fashionproject.ui.viewmodel.AddClothingUiState
import com.jeiu.fashionproject.ui.viewmodel.BackgroundRemover
import com.jeiu.fashionproject.ui.viewmodel.ImageMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P57ClosetPhotoSafetyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var first: WardrobeItem
    private lateinit var second: WardrobeItem
    private lateinit var original: File

    @Before fun seed() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        resetMemory()
        SampleWearHistory.markApplied(app)
        original = File(app.filesDir, "wardrobe/p57-original.png").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(2, 3, 5, 7))
        }
        first = WardrobeItem(id = "p57-first", name = "Synthetic first", category = "상의", colorName = "블랙",
            colorValue = 0xFF000000, imageUri = Uri.fromFile(original).toString(), addedAt = 123L,
            favoritedAt = 456L, brand = "synthetic brand", price = 789, isCutout = true)
        val secondOriginal = File(app.filesDir, "wardrobe/p57-second-original.png").apply { writeBytes(byteArrayOf(11, 12)) }
        second = first.copy(id = "p57-second", name = "Synthetic second", imageUri = Uri.fromFile(secondOriginal).toString())
        WardrobeStore.writeForRestore(app, listOf(first, second))
        WardrobeStore.load(app)
    }
    @After fun cleanup() { Dispatchers.resetMain(); resetMemory() }
    @Suppress("UNCHECKED_CAST")
    private fun resetMemory() {
        WardrobeStore::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(null, false)
        (WardrobeStore::class.java.getDeclaredField("_items").apply { isAccessible = true }.get(null)
            as MutableStateFlow<List<WardrobeItem>>).value = emptyList()
    }
    private fun prepared(item: WardrobeItem): WardrobeItem {
        val result = File(app.filesDir, "wardrobe/${item.id}_std.png").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        return item.copy(imageUri = Uri.fromFile(result).toString(), cutoutUri = Uri.fromFile(result).toString(),
            originalUri = item.originalUri ?: item.imageUri, isCutout = true)
    }
    private fun assertOriginal() { assertArrayEquals(byteArrayOf(2, 3, 5, 7), original.readBytes()) }

    @Test fun confirmedPhotoOnlyUpdateKeepsOriginalMetadataAndSurvivesReload() = runBlocking {
        val count = ClosetPhotoUnifier.unifyAll(app, listOf(first), transform = { _, item -> prepared(item) })
        assertEquals(1, count)
        val result = WardrobeStore.items.value.first()
        assertEquals(first.imageUri, result.originalUri)
        assertEquals(first, result.copy(imageUri = first.imageUri, originalUri = first.originalUri, cutoutUri = first.cutoutUri))
        assertOriginal()
        resetMemory(); WardrobeStore.load(app)
        assertEquals(result, WardrobeStore.items.value.first())
        assertOriginal()
    }

    @Test fun cancelOrProcessingFailureBeforeCommitPreservesAllRecords() = runBlocking {
        for (cancel in listOf(false, true)) {
            try {
                ClosetPhotoUnifier.unifyAll(app, listOf(first, second), transform = { _, item ->
                    if (item.id == second.id) {
                        if (cancel) throw CancellationException("synthetic cancellation")
                        else throw IllegalStateException("synthetic decode error")
                    }
                    prepared(item)
                })
                fail("Processing must not report success")
            } catch (_: CancellationException) { } catch (_: IllegalStateException) { }
            assertEquals(listOf(first, second), WardrobeStore.items.value)
            assertFalse(File(app.filesDir, "wardrobe/${first.id}_std.png").exists())
            assertOriginal()
        }
    }

    @Test fun concurrentEditAndDeleteCannotBeOverwrittenOrResurrected() = runBlocking {
        val edited = first.copy(name = "Edited while preparing", brand = "new brand")
        val count = ClosetPhotoUnifier.unifyAll(app, listOf(first, second), transform = { _, item ->
            val derivative = prepared(item)
            if (item.id == second.id) {
                WardrobeStore.update(app, edited)
                WardrobeStore.remove(app, second.id)
            }
            derivative
        })
        assertEquals(0, count)
        assertEquals(listOf(edited), WardrobeStore.items.value)
        assertOriginal()
    }

    @Test fun photoTidyRejectsMetadataChangesAndMissingOriginalReference() {
        val after = prepared(first)
        assertFalse(WardrobeStore.updatePhotoIfUnchanged(app, first, after.copy(name = "unapproved rename")))
        assertFalse(WardrobeStore.updatePhotoIfUnchanged(app, first, after.copy(originalUri = null)))
        assertEquals(listOf(first, second), WardrobeStore.items.value)
        assertOriginal()
    }

    @Test fun localMaskKeepsEnclosedSameColorDetailAndLayoutIsBounded() {
        val pixels = IntArray(20 * 20) { 0xFFFFFFFF.toInt() }
        for (y in 5..14) for (x in 5..14) pixels[y * 20 + x] = 0xFF000000.toInt()
        pixels[10 * 20 + 10] = 0xFFFFFFFF.toInt()
        val background = ClosetPhotoRules.plainBackgroundMask(pixels, 20, 20)!!
        assertTrue(background[0]); assertFalse(background[10 * 20 + 10]); assertFalse(background[5 * 20 + 5])
        val tall = ClosetPhotoRules.layout(100, 200)
        assertEquals(274.5f, tall.left, 0.001f)
        assertEquals(85.5f, tall.top, 0.001f)
        assertEquals(351f, tall.width, 0.001f)
        assertEquals(702f, tall.height, 0.001f)
        assertNull(ClosetPhotoRules.plainBackgroundMask(IntArray(4), 0, 0))
        try { ClosetPhotoRules.layout(0, 0); fail("Invalid input must be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun editCancelAndStaleEditCannotDeleteOrOverwriteStoredPhotos() {
        val model = AddClothingViewModel()
        model.startEdit(app, first)
        model.reset(app)
        assertEquals(listOf(first, second), WardrobeStore.items.value)
        assertOriginal()
        model.startEdit(app, first)
        val newer = first.copy(name = "newer name", price = 999)
        WardrobeStore.update(app, newer)
        model.saveEdit(app, "stale name", "상의", null, null, "사계절", null, ImageMode.CUTOUT)
        assertTrue(model.state.value is AddClothingUiState.Error)
        assertEquals(newer, WardrobeStore.items.value.first())
        assertOriginal()
    }

    @Test fun classifierMaskBaseMatchesUploadedGeometryNotTheNewSquareDerivative() {
        assertEquals("cropped.png", BackgroundRemover.Result("cropped.png", "original.jpg", "white-upload.jpg", null).maskBasePath)
        assertEquals("original.jpg", BackgroundRemover.Result(null, "original.jpg", "original.jpg", null).maskBasePath)
    }
}
