package com.jeiu.fashionproject

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNativeImageDecoder
import java.io.File
import java.nio.ByteBuffer

/** P36 fitting decoder only. Synthetic JPEG pixels/EXIF, not sensor or Android FD validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[27,35], application=Application::class, shadows=[P36PoseBufferSourceShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P36PoseDecodeTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val colors=intArrayOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)
    private val expected=mapOf(1 to intArrayOf(0,1,2,3),2 to intArrayOf(1,0,3,2),
        3 to intArrayOf(3,2,1,0),4 to intArrayOf(2,3,0,1),5 to intArrayOf(0,2,1,3),
        6 to intArrayOf(2,0,3,1),7 to intArrayOf(3,1,2,0),8 to intArrayOf(1,3,0,2))
    private fun fixture(width:Int,height:Int,orientation:Int):File {
        val path=File.createTempFile("p36-pose-synthetic-",".jpg",app.cacheDir)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap)
            for(i in 0..3) {
                val x=i%2*width/2f;val y=i/2*height/2f
                canvas.drawRect(x,y,x+width/2f,y+height/2f,Paint().apply {color=colors[i]})
            }
            path.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it))}
            ExifInterface(path.path).apply {setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString());saveAttributes()}
        } finally {bitmap.recycle()}
        return path
    }
    private fun corners(bitmap:Bitmap,order:IntArray) {
        for(i in 0..3) {
            val x=((if(i%2==0) .15f else .85f)*bitmap.width).toInt()
            val y=((if(i/2==0) .15f else .85f)*bitmap.height).toInt()
            val actual=bitmap.getPixel(x,y)
            for(shift in listOf(16,8,0)) assertTrue("synthetic EXIF corner $i channel $shift",
                kotlin.math.abs(((actual shr shift) and 255)-((colors[order[i]] shr shift) and 255))<=12)
        }
    }
    @Test fun allExifRotationsAndReflectionsPreserveExpectedImageCoordinates() {
        for(orientation in 1..8) {
            val path=fixture(400,240,orientation)
            try {
                val bitmap=FittingPoseDetector.decode(app,Uri.fromFile(path))
                try {
                    assertEquals(if(orientation>=5)240 else 400,bitmap.width)
                    assertEquals(if(orientation>=5)400 else 240,bitmap.height)
                    assertEquals(Bitmap.Config.ARGB_8888,bitmap.config)
                    corners(bitmap,expected.getValue(orientation))
                } finally {bitmap.recycle()}
            } finally {path.delete()}
        }
    }
    @Test fun largePhotoIsBoundedWithoutLosingRotationOrAspect() {
        val path=fixture(3000,1500,6)
        try {
            val bitmap=FittingPoseDetector.decode(app,Uri.fromFile(path))
            try {
                assertTrue(maxOf(bitmap.width,bitmap.height)<=1280)
                assertEquals(if(Build.VERSION.SDK_INT>=28)640 else 375,bitmap.width)
                assertEquals(if(Build.VERSION.SDK_INT>=28)1280 else 750,bitmap.height)
                corners(bitmap,expected.getValue(6))
            } finally {bitmap.recycle()}
        } finally {path.delete()}
    }
}

/** Mirrors P18's documented test-only source adapter. No production decode settings are replaced. */
@Implements(className="android.graphics.ImageDecoder",minSdk=28,callNativeMethodsByDefault=true)
class P36PoseBufferSourceShadow:ShadowNativeImageDecoder() {
    companion object {
        @JvmStatic @Implementation(minSdk=28)
        fun createSource(resolver:ContentResolver,uri:Uri):ImageDecoder.Source {
            require(uri.scheme=="file" && File(requireNotNull(uri.path)).name.startsWith("p36-pose-synthetic-"))
            val bytes=resolver.openInputStream(uri)!!.use {it.readBytes()}
            return ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        }
    }
}
