package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P22PhotoResizeTest {
    private fun garment() = WornGarment(
        WardrobeItem(id="synthetic", name="synthetic", category="상의", colorName="test", colorValue=0,
            imageUri="synthetic://garment", addedAt=1L), "synthetic://garment", 1.5f, Offset(100f,200f),80f,1.25f,17f)

    @Test fun portraitHeightChangeKeepsPhotoPosition() {
        // 1000x2000 photo: old scale .4 and no margin; new scale .3, left margin 50.
        // (100/.4*.3 + 50, 200/.4*.3) = (125,150), width 80/.4*.3 = 60.
        val state = ManualFittingViewModel()
        state.photoDisplaySize = Size(1000f,2000f)
        state.canvasSize = Size(400f,800f)
        state.worn[FitSlot.TOP] = garment()
        state.resizeCanvas(Size(400f,600f))
        val g = state.worn.getValue(FitSlot.TOP)
        assertEquals(125f,g.center.x,.001f)
        assertEquals(150f,g.center.y,.001f)
        assertEquals(60f,g.baseWidth,.001f)
        assertEquals(1.25f,g.scale,0f)
        assertEquals(17f,g.rotation,0f)
    }

    @Test fun widthChangesAndLandscapeLetterboxingHaveIndependentExpectedCoordinates() {
        val p = Placement(Offset(100f,200f),80f,17f)
        fun check(image: Size, from: Size, to: Size, input: Placement, x: Float, y: Float, width: Float) {
            val result = checkNotNull(resizePhotoPlacement(input,image,from,to))
            assertEquals(x,result.center.x,.001f); assertEquals(y,result.center.y,.001f)
            assertEquals(width,result.width,.001f); assertEquals(17f,result.rotation,0f)
        }
        check(Size(1000f,2000f),Size(400f,800f),Size(600f,800f),p,200f,200f,80f)
        check(Size(1000f,2000f),Size(400f,800f),Size(200f,800f),p,50f,300f,40f)
        val landscape = p.copy(center=Offset(200f,100f))
        check(Size(2000f,1000f),Size(800f,400f),Size(600f,400f),landscape,150f,125f,60f)
        check(Size(2000f,1000f),Size(800f,400f),Size(800f,600f),landscape,200f,200f,80f)
        // Switch from left/right margins to top/bottom margins.
        check(Size(1000f,2000f),Size(400f,600f),Size(200f,800f),
            p.copy(center=Offset(125f,150f),width=60f),50f,300f,40f)
    }

    @Test fun roundTripKeepsUserTransformSelectionAndSeparateAvatarContract() {
        val state = ManualFittingViewModel()
        state.canvasSize=Size(400f,800f); state.photoDisplaySize=Size(1000f,2000f)
        val original=garment()
        state.worn[FitSlot.TOP]=original; state.avatarWorn[FitSlot.TOP]=original
        state.activeSlot=FitSlot.TOP;state.overlayAlpha=.7f
        for (mode in listOf(false,true)) {
            state.avatarMode=mode
            state.resizeCanvas(Size(400f,600f))
            assertEquals(Offset(125f,150f),state.worn.getValue(FitSlot.TOP).center)
            assertEquals(Offset(100f,150f),state.avatarWorn.getValue(FitSlot.TOP).center)
            assertEquals(80f,state.avatarWorn.getValue(FitSlot.TOP).baseWidth,0f)
            state.resizeCanvas(Size(400f,800f))
            val photo=state.worn.getValue(FitSlot.TOP)
            assertEquals(original.center.x,photo.center.x,.001f)
            assertEquals(original.center.y,photo.center.y,.001f)
            assertEquals(original.baseWidth,photo.baseWidth,.001f)
            assertEquals(original.scale,photo.scale,0f);assertEquals(original.rotation,photo.rotation,0f)
            assertSame(original.item,photo.item);assertEquals(original.uri,photo.uri)
            assertEquals(original,state.avatarWorn.getValue(FitSlot.TOP))
            assertEquals(FitSlot.TOP,state.activeSlot);assertEquals(.7f,state.overlayAlpha,0f)
        }
    }

    @Test fun unknownDimensionsAndZeroLayoutPreservePlacementWithoutDelayedJump() {
        val state=ManualFittingViewModel()
        val uri=Uri.parse("content://synthetic/photo")
        val generation=state.selectPhoto(uri)
        state.canvasSize=Size(400f,800f);state.worn[FitSlot.TOP]=garment()
        state.resizeCanvas(Size(400f,600f))
        assertEquals(garment(),state.worn[FitSlot.TOP])
        state.resizeCanvas(Size.Zero)
        assertEquals(Size(400f,600f),state.canvasSize)
        // A user drag while decoding is in flight must not be reinterpreted retroactively.
        val dragged=garment().copy(center=Offset(200f,300f),scale=1.7f,rotation=-31f)
        state.worn[FitSlot.TOP]=dragged
        state.acceptPhotoDisplaySize(generation,uri,Size.Zero)
        assertNull(state.photoDisplaySize)
        state.acceptPhotoDisplaySize(generation,uri,Size(1000f,2000f))
        assertEquals(dragged,state.worn[FitSlot.TOP]);assertNull(state.body)
        state.resizeCanvas(Size(600f,600f))
        assertEquals(Offset(300f,300f),state.worn.getValue(FitSlot.TOP).center)
        assertEquals(1.7f,state.worn.getValue(FitSlot.TOP).scale,0f)
        assertEquals(-31f,state.worn.getValue(FitSlot.TOP).rotation,0f)
    }

    @Test fun staleOrDuplicateDisplayResponsesNeverChangeCurrentPhotoGeometry() {
        val state=ManualFittingViewModel()
        val a=Uri.parse("content://synthetic/a");val b=Uri.parse("content://synthetic/b")
        val old=state.selectPhoto(a);val current=state.selectPhoto(b)
        state.worn[FitSlot.TOP]=garment()
        state.acceptPhotoDisplaySize(old,a,Size(2000f,1000f));assertNull(state.photoDisplaySize)
        state.acceptPhotoDisplaySize(current,a,Size(2000f,1000f));assertNull(state.photoDisplaySize)
        state.acceptPhotoDisplaySize(current,b,Size(1000f,2000f))
        state.acceptPhotoDisplaySize(current,b,Size(2000f,1000f))
        assertEquals(Size(1000f,2000f),state.photoDisplaySize)
        state.selectPhoto(b) // Same URI, different request.
        state.acceptPhotoDisplaySize(current,b,Size(1000f,2000f));assertNull(state.photoDisplaySize)
        assertEquals(garment(),state.worn[FitSlot.TOP])
    }

    @Test fun invalidImageAndCanvasGeometryIsNeverApplied() {
        val p=Placement(Offset(100f,200f),80f,17f);val good=Size(400f,800f)
        assertNull(resizePhotoPlacement(p,null,good,good))
        for (bad in listOf(Size.Zero,Size(-1f,10f),Size(Float.NaN,10f),Size(10f,Float.POSITIVE_INFINITY))) {
            assertNull(resizePhotoPlacement(p,bad,good,good))
            assertNull(resizePhotoPlacement(p,good,bad,good))
            assertNull(resizePhotoPlacement(p,good,good,bad))
        }
    }
}
