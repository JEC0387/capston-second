package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.*
import com.jeiu.fashionproject.ui.screens.*
import kotlinx.coroutines.*
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P63AvatarCaptureTest {
    @Test fun glRowsFlipExactlyOnceAndRgbaChannelsArePreserved() {
        val bytes = byteArrayOf(255.toByte(),0,0,255.toByte(), 0,255.toByte(),0,255.toByte(),
            0,0,255.toByte(),255.toByte(), 240.toByte(),230.toByte(),220.toByte(),255.toByte())
        val bitmap = AvatarFramePixels.bitmap(2,2,bytes)
        assertEquals(Color.BLUE,bitmap.getPixel(0,0)); assertEquals(Color.rgb(240,230,220),bitmap.getPixel(1,0))
        assertEquals(Color.RED,bitmap.getPixel(0,1)); assertEquals(Color.GREEN,bitmap.getPixel(1,1))
    }
    @Test fun blankTransparentMalformedAndOversizedFramesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AvatarFramePixels.bitmap(2,2,ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { AvatarFramePixels.bitmap(2,2,ByteArray(16) { if(it%4==3) 255.toByte() else 0 }) }
        assertThrows(IllegalArgumentException::class.java) { AvatarFramePixels.bitmap(2,2,ByteArray(15)) }
        assertThrows(IllegalArgumentException::class.java) { AvatarFramePixels.bitmap(4001,1000,byteArrayOf()) }
    }
    @Test fun missingSurfaceCannotProduceFakeSuccess() = runBlocking {
        try { AvatarFrameCapture().captureBitmap(); fail("unbound surface accepted") }
        catch (expected: IllegalStateException) { assertEquals("avatar_surface_unavailable",expected.message) }
    }
    @Test fun localThreeLayerExportPreservesFrameAndReopensIdenticalBytes() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val dir=File(app.cacheDir,"p63-${System.nanoTime()}").apply { mkdirs() }
        fun garment(name:String,category:String,color:Int,x:Float,y:Float,width:Float,aspect:Float):WornGarment {
            val file=File(dir,"$name.png")
            Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            val uri=Uri.fromFile(file).toString()
            return WornGarment(WardrobeItem(name,name,category,"synthetic",color.toLong(),uri),uri,aspect,Offset(x,y),width)
        }
        val top=garment("top","상의",Color.GREEN,50f,55f,30f,1f)
        val bottom=garment("bottom","하의",Color.BLUE,50f,105f,30f,2f)
        val shoes=garment("shoes","신발",Color.RED,50f,170f,30f,.5f)
        val snapshot=AvatarOutfitSnapshot(AvatarProfile(170f,65f),Size(100f,200f),listOf(top,bottom,shoes),1f,false)
        val frame=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY);setPixel(4,4,Color.BLACK) }
        val capture=AvatarFrameCapture().apply { reader={ frame } }
        // Coil returns to Android Main. Pump the paused Robolectric looper instead of
        // blocking it with runBlocking while waiting for a Main-thread decode callback.
        val operation=async(Dispatchers.Default) { AvatarOutfitExport.create(app,snapshot,capture) }
        val deadline=System.nanoTime()+20_000_000_000L
        while (!operation.isCompleted && System.nanoTime()<deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        if (!operation.isCompleted) { operation.cancel();fail("local image decode deadline") }
        val bytes=operation.await()
        assertTrue(frame.isRecycled)
        val png=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
        assertEquals(100,png.width);assertEquals(200,png.height)
        assertEquals(Color.GREEN,png.getPixel(50,55));assertEquals(Color.BLUE,png.getPixel(50,105));assertEquals(Color.RED,png.getPixel(50,170))
        assertEquals(Color.BLACK,png.getPixel(4,4));assertEquals(Color.LTGRAY,png.getPixel(95,195))
        val target=File(dir,"saved.png")
        LocalVtonRepository.save(app,bytes,Uri.fromFile(target),true)
        assertArrayEquals(bytes,LocalVtonRepository.openSavedImage(app,Uri.fromFile(target)))
        // Synthetic frame only: this evidence is not a real GLES/device screenshot.
        File("build/p63-synthetic").apply { mkdirs() }.resolve("synthetic-frame-three-layers.png").writeBytes(bytes)
    }
    @Test fun rotatingInspectionAndRemoteGarmentsAreRejectedBeforeCapture() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val garment=WornGarment(WardrobeItem("x","x","상의","synthetic",0,"https://invalid.test/x"),"https://invalid.test/x",1f,Offset(5f,5f),5f)
        val input=AvatarOutfitSnapshot(AvatarProfile(170f,65f),Size(20f,40f),listOf(garment),1f,false)
        var calls=0
        val capture=AvatarFrameCapture().apply { reader={calls++;error("must not capture")} }
        for (candidate in listOf(input,input.copy(inspecting=true),input.copy(layers=emptyList()))) {
            try { AvatarOutfitExport.create(app,candidate,capture);fail("invalid export accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0,calls)
    }
}
