package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.jeiu.fashionproject.ui.screens.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P12GarmentGeometryTest {
    @Test fun transparentPaddingMapsVisibleGarmentToTargetWithoutEditingSource() {
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        for(y in 40 until 160)for(x in 20 until 80)bitmap.setPixel(x,y,0xff123456.toInt())
        val region=checkNotNull(GarmentGeometry.bounds(bitmap))
        assertEquals(.6f,region.width,.001f);assertEquals(.6f,region.height,.001f)
        val target=Placement(Offset(100f,200f),120f,0f)
        val expanded=region.expand(target,2f)
        assertEquals(200f,expanded.width,.001f)
        assertEquals(target.center,expanded.center)
        assertEquals(0,bitmap.getPixel(0,0));bitmap.recycle()
    }
    @Test fun asymmetricPaddingKeepsContentAnchorAndOpaqueOrEmptyImagesAreNotCutouts() {
        val bitmap=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        assertNull(GarmentGeometry.bounds(bitmap))
        bitmap.eraseColor(0xff123456.toInt());assertNull(GarmentGeometry.bounds(bitmap))
        val region=GarmentRegion(.1f,.2f,.5f,.6f)
        val expanded=region.expand(Placement(Offset(100f,100f),100f,0f),1f)
        assertEquals(130f,expanded.center.x,.001f)
        assertEquals(100f,expanded.center.y,.001f)
        bitmap.recycle()
    }
}
