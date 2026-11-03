package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.VirtualFittingScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** JVM-only isolated storage; never install/run this fixture against a user's app data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w600dp-h900dp")
class P16FittingUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before @After fun isolatedFixture() {
        app.getSharedPreferences("avatar_visual_profile_v1", 0).edit().clear().commit()
        AvatarProfileStore.load(app)
        AvatarFittingStore.clear(app)
    }

    @Test fun alreadyAlignedWandDoesNotReplaceLastRealUndoPoint() {
        WardrobeStore.load(app)
        val item = WardrobeStore.items.value.first { it.category == "상의" && it.garmentUri != null }
        assertTrue(AvatarProfileStore.save(app, AvatarProfile(170f, 65f,
            centimeters = AvatarCentimeters.generate(170f, 65f, 1)!!)))
        val manual = AvatarOverlay(item.id, .44f, .35f, .29f, 1.35f, 17f)
        assertTrue(AvatarFittingStore.save(app, listOf(manual)))
        compose.setContent { VirtualFittingScreen(startAvatar = true, onBack = {}) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("fittingOverlay-${item.id}").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val aligned = AvatarFittingStore.load(app).single()
        assertEquals(1f, aligned.scale, .001f)
        assertEquals(0f, aligned.rotation, .001f)
        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("이미 자동 정렬된 위치예요.", substring = true)
        compose.onNodeWithTag("avatarUndoFit").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()

        val restored = AvatarFittingStore.load(app).single()
        assertEquals(manual.centerX, restored.centerX, .002f)
        assertEquals(manual.centerY, restored.centerY, .002f)
        assertEquals(manual.width, restored.width, .002f)
        assertEquals(manual.scale, restored.scale, .001f)
        assertEquals(manual.rotation, restored.rotation, .001f)
    }

    private fun profile() = AvatarProfile(170f, 65f,
        centimeters = AvatarCentimeters.generate(170f, 65f, 1)!!)

    private fun imageItem(id: String, tall: Boolean): WardrobeItem {
        val bitmap = Bitmap.createBitmap(if (tall) 40 else 80, if (tall) 4000 else 80, Bitmap.Config.ARGB_8888)
        if (tall) {
            for (y in 40 until 3960) for (x in 8 until 32) bitmap.setPixel(x, y, 0xff43576b.toInt())
        } else bitmap.eraseColor(0xff43576b.toInt())
        val file = File(app.cacheDir, "$id.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return WardrobeItem(id = id, name = id, category = "상의", colorName = "synthetic", colorValue = 0xff43576b,
            imageUri = Uri.fromFile(file).toString())
    }

    @Test fun opaqueAutoFitExplainsFailureAndKeepsManualPlacement() {
        WardrobeStore.load(app)
        val item = imageItem("p16-opaque", false)
        WardrobeStore.add(app, item)
        AvatarProfileStore.save(app, profile())
        val manual = AvatarOverlay(item.id, .4f, .4f, .3f, 1.2f, 13f)
        AvatarFittingStore.save(app, listOf(manual))
        compose.setContent { VirtualFittingScreen(startAvatar = true, onBack = {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("fittingOverlay-${item.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("투명 의류 영역을 확인하지 못했어요.", substring = true)
        compose.onNodeWithTag("avatarUndoFit").assertDoesNotExist()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val saved = AvatarFittingStore.load(app).single()
        assertEquals(manual.centerX, saved.centerX, .002f)
        assertEquals(manual.centerY, saved.centerY, .002f)
        assertEquals(manual.width, saved.width, .002f)
        assertEquals(manual.scale, saved.scale, .001f)
        assertEquals(manual.rotation, saved.rotation, .001f)
    }

    @Test fun unsupportedTallReplacementKeepsTheExistingGarment() {
        WardrobeStore.load(app)
        val current = WardrobeStore.items.value.first { it.category == "상의" && it.garmentUri != null }
        val long = imageItem("p16-long", true)
        WardrobeStore.add(app, long)
        AvatarProfileStore.save(app, profile())
        val manual = AvatarOverlay(current.id, .4f, .4f, .3f, 1.2f, 13f)
        AvatarFittingStore.save(app, listOf(manual))
        compose.setContent { VirtualFittingScreen(startAvatar = true, onBack = {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("fittingOverlay-${current.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("manualWardrobe").performScrollTo().performScrollToNode(hasTestTag("manualGarment-${long.id}"))
        compose.onNodeWithTag("manualGarment-${long.id}").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("fittingInputNotice")
                .assertTextContains("의류 비율이나 투명 여백 때문에 자동 배치할 수 없어요.", substring = true) }.isSuccess
        }
        compose.onNodeWithTag("fittingOverlay-${current.id}").assertExists()
        compose.onNodeWithTag("fittingOverlay-${long.id}").assertDoesNotExist()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        assertEquals(current.id, AvatarFittingStore.load(app).single().itemId)
    }

    @Test fun tappingEachGarmentExposesItsSelectionWithoutASecondGestureHandler() {
        WardrobeStore.load(app)
        val top = WardrobeStore.items.value.first { it.category == "상의" && it.garmentUri != null }
        val bottom = WardrobeStore.items.value.first { it.category == "하의" && it.garmentUri != null }
        AvatarProfileStore.save(app, profile())
        AvatarFittingStore.save(app, listOf(AvatarOverlay(top.id, .5f, .25f, .2f, 1f, 0f),
            AvatarOverlay(bottom.id, .5f, .7f, .2f, 1f, 0f)))
        compose.setContent { VirtualFittingScreen(startAvatar = true, onBack = {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("fittingOverlay-${bottom.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fittingOverlay-${top.id}").performTouchInput { click() }
        compose.onNodeWithTag("fittingOverlay-${top.id}").assertIsSelected()
        compose.onNodeWithTag("fittingOverlay-${bottom.id}").assertIsNotSelected()
        compose.onNodeWithTag("fittingOverlay-${bottom.id}").performTouchInput { click() }
        compose.onNodeWithTag("fittingOverlay-${bottom.id}").assertIsSelected()
        compose.onNodeWithTag("fittingOverlay-${top.id}").assertIsNotSelected()
    }
}
