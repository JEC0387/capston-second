package com.jeiu.fashionproject

import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.FitSlot
import com.jeiu.fashionproject.ui.screens.fitAvatar
import org.junit.Assert.*
import org.junit.Test

class P16GarmentBoundsTest {
    @Test fun minimumWidthMustNotExpandAnExtremelyTallGarmentPastTheViewport() {
        val canvas = Size(400f, 600f)
        val top = WardrobeItem(id = "synthetic-long", name = "synthetic", category = "상의",
            colorName = "test", colorValue = 0xff000000, imageUri = "")
        val placement = fitAvatar(FitSlot.TOP, top, 100f, canvas, AvatarProfile(170f, 65f))
        assertTrue("A minimum width must not undo the height constraint", placement.width * 100f <= canvas.height * .82f)
    }
}
