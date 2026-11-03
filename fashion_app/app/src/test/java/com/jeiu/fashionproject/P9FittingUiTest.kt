package com.jeiu.fashionproject

import android.app.Application
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P9FittingUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val body=AvatarCentimeters.generate(170f,65f,1)!!
    @Before @After fun clear() {
        app.getSharedPreferences("avatar_visual_profile_v1",0).edit().clear().commit()
        AvatarProfileStore.load(app);AvatarFittingStore.clear(app)
    }
    @Test fun explicitApplyOpensAvatarModeAndPhotoAiPathRemainsAvailable() {
        assertTrue(AvatarProfileStore.save(app,AvatarProfile(170f,65f,centimeters=body)))
        compose.setContent {VirtualFittingScreen(startAvatar=true,onBack={})}
        compose.onNodeWithTag("fittingAvatarViewport").assertExists()
        compose.onNodeWithTag("aiTryOnWand").assertDoesNotExist()
        compose.onNodeWithTag("fittingPhotoMode").performClick()
        compose.onNodeWithTag("aiTryOnWand").assertExists()
        compose.onNodeWithText("전신 사진 선택").assertIsDisplayed()
    }
    @Test fun changedCmShapeKeepsSavedManualOverlayUntilUserChoosesAutoFit() {
        WardrobeStore.load(app);val item=WardrobeStore.items.value.first()
        val profile=AvatarProfile(170f,65f,centimeters=body)
        AvatarProfileStore.save(app,profile)
        AvatarFittingStore.save(app,listOf(AvatarOverlay(item.id,.5f,.4f,.3f,1.15f,7f)))
        compose.setContent {VirtualFittingScreen(startAvatar=true,onBack={})}
        compose.waitUntil(5000) {runCatching {compose.onNodeWithTag("avatarAutoFit").assertIsEnabled()}.isSuccess}
        compose.runOnIdle {AvatarProfileStore.save(app,profile.copy(centimeters=body.adjusted(AvatarPart.WAIST,body[AvatarPart.WAIST]+.5f)))}
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        compose.runOnIdle {
            val saved=AvatarFittingStore.load(app).single()
            assertEquals(.3f,saved.width,.001f);assertEquals(1.15f,saved.scale,.001f);assertEquals(7f,saved.rotation,.001f)
        }
        compose.onNodeWithTag("avatarAutoFit").performClick();compose.onNodeWithTag("avatarSaveFitting").performClick()
        compose.runOnIdle {assertEquals(1f,AvatarFittingStore.load(app).single().scale,.001f)}
    }
    @Test fun avatarGarmentDragAndMoveControlsPersistAndMagicWandRestoresFrontPosition() {
        WardrobeStore.load(app)
        val item = WardrobeStore.items.value.first()
        assertTrue(AvatarProfileStore.save(app, AvatarProfile(170f, 65f, centimeters=body)))
        compose.setContent { VirtualFittingScreen(startAvatar=true, onBack={}) }
        compose.onNodeWithTag("fittingCanvas").assertIsDisplayed()
        compose.onNodeWithTag("manualWardrobe").performScrollTo()
        compose.onNodeWithTag("manualGarment-${item.id}").assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("fittingOverlay-${item.id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val before = AvatarFittingStore.load(app).single()

        // Foreground gestures work even with the native GL mannequin underneath.
        compose.onNodeWithTag("fittingGestureLayer").performTouchInput { swipeRight() }
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val dragged = AvatarFittingStore.load(app).single()
        assertTrue("A swipe must change the avatar garment X position", dragged.centerX > before.centerX)

        compose.onNodeWithTag("fittingGestureLayer").performTouchInput {
            down(0,center+androidx.compose.ui.geometry.Offset(-25f,0f))
            down(1,center+androidx.compose.ui.geometry.Offset(25f,0f))
            moveTo(0,center+androidx.compose.ui.geometry.Offset(-60f,-20f))
            moveTo(1,center+androidx.compose.ui.geometry.Offset(60f,20f))
            up(0);up(1)
        }
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val transformed=AvatarFittingStore.load(app).single()
        assertTrue(transformed.scale>dragged.scale)
        assertTrue(kotlin.math.abs(transformed.rotation-dragged.rotation)>1f)

        compose.onNodeWithTag("fittingMoveRight").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val nudged = AvatarFittingStore.load(app).single()
        compose.onNodeWithTag("fittingInspectModel").performScrollTo().performClick()
        compose.onNodeWithTag("fittingGestureLayer").assertDoesNotExist()
        compose.onNodeWithTag("fittingOverlay-${item.id}").assertDoesNotExist()
        compose.onNodeWithTag("fittingAvatarViewport").assertExists()
        compose.onNodeWithTag("fittingInspectModel").performScrollTo().performClick()
        compose.onNodeWithTag("fittingGestureLayer").assertExists()
        assertTrue("The position fallback button must move the same garment", nudged.centerX > dragged.centerX)

        compose.onNodeWithTag("avatarAutoFit").performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val restored = AvatarFittingStore.load(app).single()
        assertEquals("Magic wand should restore calculated front placement", before.centerX, restored.centerX, .002f)
        assertEquals(1f, restored.scale, .001f)
        compose.onNodeWithTag("avatarUndoFit").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val undone=AvatarFittingStore.load(app).single()
        assertEquals(nudged.centerX,undone.centerX,.002f)
        assertEquals(nudged.scale,undone.scale,.001f)
    }
    @Test fun topAndBottomCanBeSelectedAndMovedIndependently() {
        WardrobeStore.load(app)
        val top=WardrobeStore.items.value.first {it.category=="상의"}
        val bottom=WardrobeStore.items.value.first {it.category=="하의" && it.garmentUri!=null}
        AvatarProfileStore.save(app,AvatarProfile(170f,65f,centimeters=body))
        compose.setContent {VirtualFittingScreen(startAvatar=true,onBack={})}
        for(item in listOf(top,bottom)) {
            compose.onNodeWithTag("manualWardrobe").performScrollTo().performScrollToNode(hasTestTag("manualGarment-${item.id}"))
            compose.onNodeWithTag("manualGarment-${item.id}").performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("fittingOverlay-${item.id}").fetchSemanticsNodes().isNotEmpty()}
        }
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val before=AvatarFittingStore.load(app).associateBy {it.itemId}
        compose.onNodeWithTag("fittingMoveRight").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val after=AvatarFittingStore.load(app).associateBy {it.itemId}
        assertEquals(before.getValue(top.id).centerX,after.getValue(top.id).centerX,.002f)
        assertTrue(after.getValue(bottom.id).centerX>before.getValue(bottom.id).centerX)
        compose.onNodeWithTag("fittingOverlay-${top.id}").performTouchInput {click()}
        compose.onNodeWithTag("fittingMoveLeft").performScrollTo().performClick()
        compose.onNodeWithTag("avatarSaveFitting").performClick()
        val selectedTop=AvatarFittingStore.load(app).associateBy {it.itemId}
        assertTrue(selectedTop.getValue(top.id).centerX<after.getValue(top.id).centerX)
        assertEquals(after.getValue(bottom.id).centerX,selectedTop.getValue(bottom.id).centerX,.002f)
    }
}
