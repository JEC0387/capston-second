package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P36DragStateTest {
    private fun model()=ManualFittingViewModel().apply {
        selectPhoto(Uri.parse("content://synthetic/photo")); photoDisplaySize=Size(1000f,2000f); canvasSize=Size(400f,600f)
    }
    private fun garment(id:String, category:String="상의")=WornGarment(
        WardrobeItem(id,id,category,"synthetic",0,"content://synthetic/$id",addedAt=1),
        "content://synthetic/$id",1f,Offset(125f,150f),60f,1.2f,15f)

    @Test fun photoHitTestExcludesPortraitAndLandscapeLetterboxes() {
        val canvas=Rect(20f,100f,420f,700f)
        // 1000x2000 Fit => 300x600, horizontal root bounds [70,370).
        assertNull(photoDropPosition(Offset(60f,200f),canvas,Size(1000f,2000f)))
        assertEquals(Offset(105f,150f),photoDropPosition(Offset(125f,250f),canvas,Size(1000f,2000f)))
        assertNull(photoDropPosition(Offset(120f,200f),canvas,Size(2000f,1000f)))
        assertEquals(Offset(100f,300f),photoDropPosition(Offset(120f,400f),canvas,Size(2000f,1000f)))
        assertNull(photoDropPosition(Offset.Zero,canvas,null))
        assertNull(photoDropPosition(Offset(Float.NaN,200f),canvas,Size(100f,200f)))
        assertNull(photoDropPosition(Offset.Zero,Rect.Zero,Size(100f,200f)))
    }

    @Test fun consecutiveSameCategoryDropRejectsLateResultAndNeverTouchesAvatar() {
        val vm=model();val original=garment("original");val avatar=garment("avatar")
        vm.worn[FitSlot.TOP]=original;vm.avatarWorn[FitSlot.TOP]=avatar
        val a=vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f))!!
        val b=vm.beginPhotoDrop(FitSlot.TOP,Offset(120f,210f))!!
        assertFalse(vm.applyPhotoDrop(a,garment("late-A")))
        assertEquals(original,vm.worn[FitSlot.TOP])
        assertTrue(vm.applyPhotoDrop(b,garment("B")))
        assertEquals("B",vm.worn[FitSlot.TOP]?.item?.id)
        assertTrue(vm.undoPhotoDrop())
        assertEquals(original,vm.worn[FitSlot.TOP]);assertEquals(avatar,vm.avatarWorn[FitSlot.TOP])
        assertFalse(vm.applyPhotoDrop(b,garment("duplicate-B")))
    }

    @Test fun wrongCategoryChangedPhotoAndChangedModeCannotCommitDrop() {
        val vm=model();val ticket=vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f))!!
        assertFalse(vm.applyPhotoDrop(ticket,garment("pants","하의")))
        vm.avatarMode=true;assertFalse(vm.applyPhotoDrop(ticket,garment("top")))
        vm.avatarMode=false;vm.selectPhoto(Uri.parse("content://synthetic/new-photo"))
        assertFalse(vm.applyPhotoDrop(ticket,garment("old-photo-top")))
        assertTrue(vm.worn.isEmpty());assertNull(vm.photoDropUndo)
    }

    @Test fun cancellingPendingDecodeKeepsOriginalAndAllowsANewDrop() {
        val vm=model();val original=garment("original");vm.worn[FitSlot.TOP]=original
        val pending=vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f))!!
        vm.cancelPhotoDrops()
        assertFalse(vm.applyPhotoDrop(pending,garment("late")))
        assertEquals(original,vm.worn[FitSlot.TOP]);assertTrue(vm.pendingPhotoDrops.isEmpty())
        val next=vm.beginPhotoDrop(FitSlot.TOP,Offset(120f,210f))!!
        assertTrue(vm.applyPhotoDrop(next,garment("next")))
        assertTrue(vm.pendingPhotoDrops.isEmpty())
    }

    @Test fun aiModeExitCannotCommitPendingDecodeOrBeginAnotherDrop() {
        for (local in listOf(false, true)) {
            val vm=model(); val original=garment("original"); vm.worn[FitSlot.TOP]=original
            val pending=vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f))!!
            if (local) vm.showLocalAi=true else vm.showAi=true
            assertFalse(vm.applyPhotoDrop(pending,garment("late")))
            assertNull(vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f)))
            assertEquals(original,vm.worn[FitSlot.TOP]); assertNull(vm.photoDropUndo)
            // Screen exit effect invalidates the request even after returning to the manual screen.
            vm.cancelPhotoDrops(); vm.showLocalAi=false; vm.showAi=false
            assertFalse(vm.applyPhotoDrop(pending,garment("late-after-return")))
        }
    }

    @Test fun shoesSeedOnlyTheAffineReferenceWithCompletePlausibleFeet() {
        val left=FittingPoseDetector.SidePoints(ankle=Offset(420f,1750f),
            heel=Offset(420f,1780f),toe=Offset(400f,1840f))
        val right=FittingPoseDetector.SidePoints(ankle=Offset(580f,1750f),
            heel=Offset(580f,1780f),toe=Offset(600f,1840f))
        val body=FittingPoseDetector.BodyPoints(1000,2000,
            Offset(350f,500f) to Offset(650f,500f),null,
            left.ankle!! to right.ankle!!,left,right)
        val item=garment("shoes","신발").item
        val seed=photoDropPlacement(FitSlot.SHOES,item,1f,Size(400f,600f),body)!!
        // Fit scale .3, horizontal margin50, ankle separation48 => pair width96.
        // The reference is not the user's arbitrary drop point or a per-foot orientation claim.
        assertEquals(200f,seed.center.x,.01f);assertEquals(563.4f,seed.center.y,.01f)
        assertEquals(96f,seed.width,.01f);assertEquals(0f,seed.rotation,0f)
        for (invalid in listOf(body.copy(left=null),body.copy(left=left.copy(toe=null)),
            body.copy(left=left.copy(toe=left.heel)),
            body.copy(right=right.copy(toe=Offset(Float.NaN,1840f))),
            body.copy(right=right.copy(ankle=Offset(580f,900f))))) {
            assertNull(photoDropPlacement(FitSlot.SHOES,item,1f,Size(400f,600f),invalid))
        }
    }

    @Test fun undoOnlyRestoresReplacedSlotAndItsCoordinatesFollowResize() {
        val vm=model(); val old=garment("old")
        vm.worn[FitSlot.TOP]=old
        val ticket=vm.beginPhotoDrop(FitSlot.TOP,Offset(100f,200f))!!
        assertTrue(vm.applyPhotoDrop(ticket,garment("new")))
        val pants=garment("pants","하의");vm.worn[FitSlot.BOTTOM]=pants
        vm.resizeCanvas(Size(400f,800f))
        val pantsAfterResize=vm.worn[FitSlot.BOTTOM]
        assertTrue(vm.undoPhotoDrop())
        // Previous 400x600: photo scale .3,x padding50 => (125,150) maps to (100,200), width80.
        val restored=vm.worn.getValue(FitSlot.TOP)
        assertEquals(100f,restored.center.x,.01f);assertEquals(200f,restored.center.y,.01f)
        assertEquals(80f,restored.baseWidth,.01f);assertEquals(1.2f,restored.scale,0f);assertEquals(15f,restored.rotation,0f)
        assertEquals(pantsAfterResize,vm.worn[FitSlot.BOTTOM])
    }
}
