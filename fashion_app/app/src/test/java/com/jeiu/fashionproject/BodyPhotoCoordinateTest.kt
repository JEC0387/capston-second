package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Looper
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import coil.size.Scale
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowNativeImageDecoder
import java.io.File
import java.nio.ByteBuffer

/** Production decode settings + native pixels; synthetic source plumbing only, not an Android FD test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 35], application = Application::class, shadows = [P18BufferSourceShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyPhotoCoordinateTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    // Display order TL, TR, BL, BR: hand-derived EXIF cases, not an Android transform oracle.
    private val expected = mapOf(
        1 to intArrayOf(0,1,2,3), 2 to intArrayOf(1,0,3,2),
        3 to intArrayOf(3,2,1,0), 4 to intArrayOf(2,3,0,1),
        5 to intArrayOf(0,2,1,3), 6 to intArrayOf(2,0,3,1),
        7 to intArrayOf(3,1,2,0), 8 to intArrayOf(1,3,0,2))

    private fun fixture(width: Int, height: Int, orientation: Int, order: IntArray = expected.getValue(1)): File {
        val file = File.createTempFile("p18-synthetic-", ".jpg", app.cacheDir)
        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            for (i in 0..3) {
                val x = (i % 2) * width / 2f; val y = (i / 2) * height / 2f
                canvas.drawRect(x,y,x+width/2f,y+height/2f,Paint().apply { color=colors[order[i]] })
            }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString()); saveAttributes()
            }
        } finally { bitmap.recycle() }
        return file
    }

    private fun <T> withDecoded(file: File, block: (Bitmap) -> T): T {
        val model = BodyAnalysisViewModel(app)
        val store = ViewModelStore().apply { put("p18",model) }
        try {
            // Exercise the existing private path without changing production visibility or bypassing analyze gates.
            val method = BodyAnalysisViewModel::class.java.getDeclaredMethod("loadBitmap",Context::class.java,Uri::class.java)
            method.isAccessible = true
            val bitmap = method.invoke(model,app,Uri.fromFile(file)) as Bitmap
            try { return block(bitmap) } finally { bitmap.recycle() }
        } finally { store.clear() }
    }

    private fun assertCorners(bitmap: Bitmap, order: IntArray) {
        // Near each corner and at each quadrant's centre: catches clipping/offset as well as mirror errors.
        for (fraction in listOf(.05f,.25f)) for (i in 0..3) {
            val x=((if(i%2==0) fraction else 1-fraction)*bitmap.width).toInt().coerceAtMost(bitmap.width-1)
            val y=((if(i/2==0) fraction else 1-fraction)*bitmap.height).toInt().coerceAtMost(bitmap.height-1)
            val actual=bitmap.getPixel(x,y);val want=colors[order[i]]
            for (shift in listOf(16,8,0)) assertTrue("corner=$i pixel=($x,$y) channel=$shift",
                kotlin.math.abs(((actual shr shift) and 255)-((want shr shift) and 255))<=12)
        }
    }

    @Test fun allExifOrientationsHaveIndependentDimensionsAndAsymmetricCorners() {
        for (orientation in 1..8) {
            val file=fixture(400,240,orientation)
            try { withDecoded(file) { bitmap ->
                assertEquals(if(orientation>=5) 240 else 400,bitmap.width)
                assertEquals(if(orientation>=5) 400 else 240,bitmap.height)
                assertCorners(bitmap,expected.getValue(orientation))
            } } finally { file.delete() }
        }
    }

    @Test fun physicallyRotatedPixelsWithNormalExifAreNotRotatedAgain() {
        val file=fixture(240,400,1,intArrayOf(2,0,3,1))
        try { withDecoded(file) { bitmap ->
            assertEquals(240,bitmap.width);assertEquals(400,bitmap.height)
            assertCorners(bitmap,intArrayOf(2,0,3,1))
        } } finally { file.delete() }
    }

    @Test fun sampledLandscapeRotationsPreserveAspectAndCornerPositions() {
        for (orientation in listOf(6,8)) {
            val file=fixture(3072,1536,orientation)
            try { withDecoded(file) { bitmap ->
                assertEquals(768,bitmap.width);assertEquals(1536,bitmap.height)
                assertCorners(bitmap,expected.getValue(orientation))
            } } finally { file.delete() }
        }
    }

    @Test fun coilFitDisplayAndAnalysisAgreeOnRotatedAndMirroredPhotoAxes() {
        val loader=ImageLoader(app)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try { for (orientation in listOf(2,5,6,7,8)) {
            val file=fixture(400,240,orientation)
            try {
                // API27 Robolectric on Windows creates file://C%3A%5C... (an authority, not a path).
                // Compare decode/EXIF using the identical encoded bytes; device URI fetching is a separate check.
                val pending=scope.async { loader.execute(ImageRequest.Builder(app).data(file.readBytes())
                    .size(400,260).scale(Scale.FIT).allowHardware(false).build()) }
                // Coil returns through Android's main dispatcher; service the paused test Looper.
                val deadline=System.nanoTime()+30_000_000_000L
                while(!pending.isCompleted && System.nanoTime()<deadline) {
                    shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
                }
                assertTrue("Coil decode must finish without an unbounded test wait",pending.isCompleted)
                val result=runBlocking { pending.await() }
                assertTrue("Coil must decode the synthetic file: ${(result as? ErrorResult)?.throwable?.stackTraceToString()}",result is SuccessResult)
                val displayed=(result as SuccessResult).drawable.toBitmap()
                withDecoded(file) { analysis ->
                    assertEquals(analysis.width.toFloat()/analysis.height,
                        displayed.width.toFloat()/displayed.height,.005f)
                    assertCorners(displayed,expected.getValue(orientation))
                }
            } finally { file.delete() }
        } } finally { scope.cancel(); loader.shutdown() }
    }
}

/**
 * Native Robolectric's AssetFileDescriptor binding throws "Only supported on Android".
 * Adapt ONLY source bytes: the fixture's JPEG/EXIF, production sampling, ImageDecoder native decode,
 * bitmap pixels and independent corner expectations are unchanged. Android URI/FD I/O remains unverified.
 */
@Implements(className="android.graphics.ImageDecoder",minSdk=28,callNativeMethodsByDefault=true)
class P18BufferSourceShadow : ShadowNativeImageDecoder() {
    companion object {
        @JvmStatic @Implementation(minSdk=28)
        fun createSource(resolver: ContentResolver, uri: Uri): ImageDecoder.Source {
            require(uri.scheme=="file" && File(requireNotNull(uri.path)).name.startsWith("p18-synthetic-"))
            val bytes=resolver.openInputStream(uri)!!.use { it.readBytes() }
            return ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        }
    }
}
