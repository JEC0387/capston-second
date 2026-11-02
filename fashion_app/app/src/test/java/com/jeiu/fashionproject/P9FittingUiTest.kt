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
}
