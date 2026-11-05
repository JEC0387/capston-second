package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w500dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P35ManualPhotoExportSaveUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun pickerPinsChosenBytesAndReentryOpensSavedFileWithoutGenerating() {
        val first=ManualPhotoSnapshot("file:///synthetic-red.png",1,Size(100f,200f),emptyList(),1f)
        val input=mutableStateOf<ManualPhotoSnapshot?>(first); val visible=mutableStateOf(true)
        var requestCode=0;var generations=0
        val registry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(code:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) { requestCode=code }
        }
        val owner=object:ActivityResultRegistryOwner { override val activityResultRegistry=registry }
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme {
            if(visible.value) ManualPhotoExportPanel(input.value) { snapshot ->
                generations++
                val image=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                val png=try { ManualPhotoExport.render(image,emptyList(),snapshot.canvas,1f) } finally { image.recycle() }
                ManualPhotoResult(snapshot,png,png)
            }
        } } }
        compose.onNodeWithTag("manualPngCreate").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("manualPngToggle").performClick()
        compose.onNodeWithText("원본 · 같은 캔버스 비율").assertExists()
        compose.onNodeWithText("비교 닫기").performClick()
        compose.onNodeWithTag("manualPngSave").performClick()
        compose.onNodeWithTag("manualPngSave").assertIsNotEnabled()
        compose.runOnIdle { input.value=first.copy(photo="file:///synthetic-blue.png",generation=2) }
        val app=ApplicationProvider.getApplicationContext<Application>()
        val target=File(app.cacheDir,"p35-snapshot-${System.nanoTime()}.png")
        try {
            compose.runOnIdle { registry.dispatchResult(requestCode,Uri.fromFile(target)) }
            compose.waitUntil(15000) { target.exists() && target.length()>0 && compose.onAllNodesWithText("2D 수동 합성 PNG를 저장했어요. 저장 이미지 열기로 다시 확인할 수 있어요.").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(Color.RED,BitmapFactory.decodeFile(target.path).getPixel(50,100))
            compose.onNodeWithTag("manualPngCompare").assertIsNotEnabled()
            compose.runOnIdle { visible.value=false }
            compose.runOnIdle { input.value=null; visible.value=true }
            compose.onNodeWithTag("manualPngOpen").performClick()
            compose.runOnIdle { registry.dispatchResult(requestCode,Uri.fromFile(target)) }
            compose.waitUntil(15000) {compose.onAllNodesWithText("선택한 저장 이미지 · 새 생성 아님").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("닫기").performClick()
            compose.onNodeWithTag("manualPngSave").assertIsNotEnabled()
            assertEquals(1,generations)
        } finally {target.delete()}
    }
}
