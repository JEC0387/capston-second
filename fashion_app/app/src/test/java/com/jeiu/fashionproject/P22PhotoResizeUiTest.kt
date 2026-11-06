package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.jeiu.fashionproject.data.AvatarFittingStore
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real screen/onSizeChanged/Coil success callback, synthetic pixels only; no image IO or pose service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w600dp-h1200dp")
class P22PhotoResizeUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var images:ImageLoader
    private val owner=object:ViewModelStoreOwner {override val viewModelStore=ViewModelStore()}
    private lateinit var state:ManualFittingViewModel

    @Before fun fixture() {
        AvatarFittingStore.clear(app) // Robolectric sandbox only, never a device.
        state=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = ManualFittingViewModel() as T
        })[ManualFittingViewModel::class.java]
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888).apply {
            density=Bitmap.DENSITY_NONE;eraseColor(0xff58768a.toInt())
        }
        images=ImageLoader.Builder(app).components {
            add(object:Interceptor {
                override suspend fun intercept(chain:Interceptor.Chain):ImageResult =
                    SuccessResult(BitmapDrawable(app.resources,bitmap),chain.request,DataSource.MEMORY)
            })
        }.build()
        Coil.setImageLoader(images)
    }
    @After fun release() {owner.viewModelStore.clear();images.shutdown();Coil.reset()}

    @Test fun displayedPhotoWithoutPoseDrivesActualScreenResizeAndRoundTrip() {
        val height=mutableStateOf(1000.dp)
        state.selectPhoto(Uri.parse("content://synthetic/already-oriented-photo"))
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                // Override the test host's tight root constraints: height() alone is clamped
                // and never produces an onSizeChanged event in that host.
                Box(Modifier.fillMaxWidth().requiredHeight(height.value)) {VirtualFittingScreen(onBack={})}
            }
        }
        compose.waitUntil(10_000) {state.photoDisplaySize!=null && state.canvasSize.height>0}
        assertNull("Display dimensions must not depend on successful pose detection",state.body)
        assertEquals(2f,state.photoDisplaySize!!.height/state.photoDisplaySize!!.width,.0001f)
        var original=Size.Zero
        // For a 1:2 portrait fitted by height, a photo point at (25%,25%) has
        // canvas coordinates (canvasWidth/2-canvasHeight/8, canvasHeight/4).
        val item=WardrobeItem(id="p22-ui",name="synthetic garment",category="상의",colorName="test",colorValue=0,
            imageUri="synthetic://garment")
        compose.runOnIdle {
            state.worn[FitSlot.TOP]=WornGarment(item,item.imageUri,1.5f,
                Offset(100f,200f),80f,1.4f,23f)
            state.activeSlot=FitSlot.TOP
        }
        compose.onNodeWithTag("fittingOverlay-p22-ui").assertExists().assertIsSelected()
        // Selecting clothes exposes controls and changes available canvas height. Establish
        // the reference only after that real layout settles, not from the empty outfit.
        compose.runOnIdle {
            original=state.canvasSize
            assertTrue(original.height<original.width*2)
            state.worn[FitSlot.TOP]=state.worn.getValue(FitSlot.TOP).copy(
                center=Offset(original.width/2-original.height/8,original.height/4),baseWidth=original.height/10)
        }
        compose.runOnIdle {height.value=800.dp}
        compose.waitForIdle()
        println("P22 synthetic layout before=$original after=${state.canvasSize} node=${compose.onNodeWithTag("fittingCanvas").fetchSemanticsNode().size}")
        compose.waitUntil(5_000) {state.canvasSize!=original}
        compose.runOnIdle {
            val next=state.canvasSize;val g=state.worn.getValue(FitSlot.TOP)
            assertTrue(next.height<original.height);assertEquals(original.width,next.width,0f)
            assertEquals(next.width/2-next.height/8,g.center.x,.01f)
            assertEquals(next.height/4,g.center.y,.01f);assertEquals(next.height/10,g.baseWidth,.01f)
            assertEquals(1.4f,g.scale,0f);assertEquals(23f,g.rotation,0f);assertEquals(FitSlot.TOP,state.activeSlot)
            assertEquals(item,g.item)
        }
        compose.onNodeWithTag("fittingOverlay-p22-ui").assertIsSelected()
        compose.runOnIdle {height.value=1000.dp}
        compose.waitForIdle()
        compose.waitUntil(5_000) {state.canvasSize==original}
        compose.runOnIdle {
            val g=state.worn.getValue(FitSlot.TOP)
            assertEquals(original.width/2-original.height/8,g.center.x,.01f)
            assertEquals(original.height/4,g.center.y,.01f);assertEquals(original.height/10,g.baseWidth,.01f)
            assertEquals(1.4f,g.scale,0f);assertEquals(23f,g.rotation,0f)
        }
        val beforeReselect=state.worn.getValue(FitSlot.TOP)
        compose.runOnIdle {state.selectPhoto(Uri.parse("content://synthetic/already-oriented-photo"))}
        compose.waitForIdle()
        compose.waitUntil(5_000) {state.photoDisplaySize!=null}
        compose.runOnIdle {assertEquals(beforeReselect,state.worn.getValue(FitSlot.TOP))}
    }
}
