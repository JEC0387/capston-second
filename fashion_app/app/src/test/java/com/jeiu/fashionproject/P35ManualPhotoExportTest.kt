package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P35ManualPhotoExportTest {
    private fun image(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun garment(id: String, category: String, x: Float, y: Float, width: Float, aspect: Float = 1f) =
        WornGarment(WardrobeItem(id, id, category, "synthetic", Color.RED.toLong(), "file:///synthetic-$id.png"),
            "file:///synthetic-$id.png", aspect, Offset(x,y), width)
    private fun decode(bytes: ByteArray) = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))

    @Test fun threeGarmentsProduceRealPngAndSaveReopenWithoutNetwork() = runBlocking {
        val photo = image(100,200,Color.LTGRAY)
        val top = garment("top","상의",50f,60f,40f)
        val bottom = garment("bottom","하의",50f,120f,30f,2f)
        val shoes = garment("shoes","신발",50f,180f,30f,.5f)
        val png = ManualPhotoExport.render(photo,listOf(bottom to image(3,6,Color.BLUE), shoes to image(6,3,Color.BLACK), top to image(4,4,Color.GREEN)),Size(100f,200f),1f)
        val result = decode(png)
        assertEquals(100,result.width); assertEquals(200,result.height)
        assertEquals(Color.GREEN,result.getPixel(50,60)); assertEquals(Color.BLUE,result.getPixel(50,120))
        assertEquals(Color.BLACK,result.getPixel(50,180)); assertEquals(Color.LTGRAY,result.getPixel(5,5))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val target = File(app.cacheDir,"p35-synthetic-only-${System.nanoTime()}.png")
        try {
            LocalVtonRepository.save(app,png,Uri.fromFile(target),true)
            val reopened = decode(LocalVtonRepository.openSavedImage(app,Uri.fromFile(target)))
            assertEquals(Color.GREEN,reopened.getPixel(50,60)); assertEquals(Color.BLUE,reopened.getPixel(50,120))
            assertEquals(Color.BLACK,reopened.getPixel(50,180))
            val evidence = File("build/p35-synthetic").apply { mkdirs() }
            File(evidence,"manual-2d-synthetic.png").writeBytes(png)
            File(evidence,"original-synthetic.png").writeBytes(ManualPhotoExport.render(photo,emptyList(),Size(100f,200f),1f))
            File(evidence,"SHA256.txt").writeText(MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) })
        } finally { target.delete() }
    }

    @Test fun letterboxRotationAlphaAndUserScaleAreAppliedInCanvasCoordinates() {
        val photo = image(100,200,Color.WHITE)
        val layer = garment("top","상의",125f,150f,30f,2f).copy(scale=2f,rotation=90f)
        val png = ManualPhotoExport.render(photo,listOf(layer to image(2,4,Color.RED)),Size(400f,600f),.5f)
        val output = decode(png)
        assertEquals(Color.rgb(245,245,245),output.getPixel(20,300)) // x padding 50px
        assertEquals(Color.WHITE,output.getPixel(55,300))
        val mixed = output.getPixel(175,150) // rotation gives half width 60, half height 30
        assertEquals(255,Color.red(mixed)); assertTrue(Color.green(mixed) in 126..128)
        assertEquals(Color.WHITE,output.getPixel(125,190))
    }

    @Test fun opaqueLaterLayerWinsAndInvalidDimensionsFailClosed() {
        val p=image(10,10,Color.WHITE); val g=garment("top","상의",5f,5f,4f)
        val result=decode(ManualPhotoExport.render(p,listOf(g to image(2,2,Color.BLUE),g to image(2,2,Color.RED)),Size(10f,10f),1f))
        assertEquals(Color.RED,result.getPixel(5,5))
        for(size in listOf(Size.Zero,Size(Float.NaN,10f))) {
            assertThrows(IllegalArgumentException::class.java) { ManualPhotoExport.render(p,emptyList(),size,1f) }
        }
        assertThrows(IllegalArgumentException::class.java) { ManualPhotoExport.render(p,listOf(g.copy(rotation=Float.NaN) to p),Size(10f,10f),1f) }
    }

    @Test fun networkSourcesRejectedBeforeAnyDecoderIsCalled() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        assertFalse(ManualPhotoExport.localInput("https://example.invalid/private.png"))
        assertFalse(ManualPhotoExport.localInput("data:image/png;base64,abc"))
        val input=ManualPhotoSnapshot("https://example.invalid/private.png",1,Size(10f,10f),listOf(garment("top","상의",5f,5f,4f)),1f)
        try { ManualPhotoExport.create(app,input); fail("remote input accepted") } catch(_: IllegalArgumentException) { }
    }
}
