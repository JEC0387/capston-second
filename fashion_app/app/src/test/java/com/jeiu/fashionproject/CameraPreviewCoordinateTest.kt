package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** Library boundary used by CameraPreviewScreen; not a real sensor/ML Kit accuracy test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
class CameraPreviewCoordinateTest {
    private fun source(rotation: Int): ImageProxy {
        val info=Proxy.newProxyInstance(ImageInfo::class.java.classLoader,arrayOf(ImageInfo::class.java)) { _,method,_ ->
            when(method.name) { "getRotationDegrees" -> rotation; else -> error("Unexpected ImageInfo call: ${method.name}") }
        } as ImageInfo
        return Proxy.newProxyInstance(ImageProxy::class.java.classLoader,arrayOf(ImageProxy::class.java)) { _,method,_ ->
            when(method.name) {
                "getWidth" -> 1200; "getHeight" -> 800; "getCropRect" -> Rect(100,50,1100,650)
                "getImageInfo" -> info; else -> error("Unexpected ImageProxy call: ${method.name}")
            }
        } as ImageProxy
    }

    @Test fun fullRotatedImageCoordinatesMapAsymmetricCropEdgesWithoutMirrorOrClamp() {
        for (rotation in listOf(90,270)) {
            // Same flags as CameraPreviewScreen: ML Kit coordinates are full rotated image coordinates.
            val factory=ImageProxyTransformFactory().apply { isUsingRotationDegrees=true;isUsingCropRect=false }
            val src=factory.getOutputTransform(source(rotation))
            // Independent normalized sensor viewport -> 300x500 preview matrix.
            // 90 CW: (-1,-1)->(300,0); 270 CW: (-1,-1)->(0,500).
            val targetMatrix=Matrix().apply { setValues(if(rotation==90)
                floatArrayOf(0f,-150f,150f,250f,0f,250f,0f,0f,1f)
                else floatArrayOf(0f,150f,150f,-250f,0f,250f,0f,0f,1f)) }
            val target=OutputTransform(targetMatrix,Size(1000,600))
            // Raw crop [100,50..1100,650] becomes x=[150,750] at 90, [50,650] at 270.
            val x=if(rotation==90) 150f else 50f
            val points=floatArrayOf(x,100f,x+600f,1100f,x+300f,600f,0f,0f,800f,1200f)
            CoordinateTransform(src,target).mapPoints(points)
            val expected=if(rotation==90)
                floatArrayOf(0f,0f,300f,500f,150f,250f,-75f,-50f,325f,550f)
                else floatArrayOf(0f,0f,300f,500f,150f,250f,-25f,-50f,375f,550f)
            assertArrayEquals("rotation=$rotation; outside crop must stay outside",expected,points,.01f)
        }
    }
}
