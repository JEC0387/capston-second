package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w500dp-h900dp")
// Real glyph metrics are required: LEGACY reports the entire Korean action as 13px wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P63AvatarExportUiTest {
    @get:Rule val compose=createComposeRule()
    private val garment=WornGarment(WardrobeItem("top","top","상의","synthetic",0,"file:///synthetic.png"),"file:///synthetic.png",1f,Offset(50f,50f),30f)
    private val input=AvatarOutfitSnapshot(AvatarProfile(170f,65f),Size(100f,200f),listOf(garment),1f,false)
    @Test fun saveAndReopenLabelsDoNotClipAt320dpWithDoubleFontSize() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                MaterialTheme { Box(Modifier.width(320.dp)) { AvatarOutfitExportPanel(input) { error("not clicked") } } }
            }
        }
        for (label in listOf("3D 착장 이미지 저장","저장 이미지 열기")) {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertTrue("Clipped action: $label " + layouts.joinToString { "size=${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, constraints=${it.layoutInput.constraints}" },
                layouts.none { it.hasVisualOverflow })
        }
    }
    @Test fun inspectionAndEmptyOutfitCannotExportButCanOpenSavedFile() {
        val state=mutableStateOf(input.copy(inspecting=true))
        compose.setContent { MaterialTheme { AvatarOutfitExportPanel(state.value) { error("must not render") } } }
        compose.onNodeWithTag("avatarPngCreate").assertIsNotEnabled()
        compose.onNodeWithTag("avatarPngOpen").assertIsEnabled()
        compose.runOnIdle {state.value=input.copy(layers=emptyList())}
        compose.onNodeWithTag("avatarPngCreate").assertIsNotEnabled()
    }
    @Test fun duplicateClickBlockedAndChangedPlacementCannotSaveOldResult() {
        val state=mutableStateOf(input)
        val result=CompletableDeferred<ByteArray>();var calls=0
        compose.setContent { MaterialTheme { AvatarOutfitExportPanel(state.value) { calls++;result.await() } } }
        compose.onNodeWithTag("avatarPngCreate").performClick()
        compose.onNodeWithTag("avatarPngCreate").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1,calls);state.value=input.copy(alpha=.5f);result.complete(byteArrayOf(1,2,3)) }
        compose.waitForIdle()
        compose.onNodeWithTag("avatarPngSave").assertDoesNotExist()
        compose.onNodeWithText("체형이나 배치가 바뀌어 이전 미리보기를 닫았어요. 현재 화면에서 다시 저장해주세요.").assertExists()
    }
    @Test fun cancelReleasesBusyStateWithoutChangingInput() {
        val result=CompletableDeferred<ByteArray>();var calls=0
        compose.setContent { MaterialTheme { AvatarOutfitExportPanel(input) { calls++;result.await() } } }
        compose.onNodeWithTag("avatarPngCreate").performClick()
        compose.onNodeWithTag("avatarPngCancel").performClick()
        compose.onNodeWithTag("avatarPngCreate").assertIsEnabled()
        compose.onNodeWithTag("avatarPngSave").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,calls);assertEquals(1f,input.alpha)}
    }
}
