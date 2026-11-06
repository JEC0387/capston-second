package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.AvatarFittingStore
import com.jeiu.fashionproject.data.AvatarOverlay
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.FitSlot
import com.jeiu.fashionproject.ui.screens.fitAvatar
import com.jeiu.fashionproject.ui.screens.fit
import com.jeiu.fashionproject.ui.screens.photoAutoPlacement
import com.jeiu.fashionproject.ui.viewmodel.FittingPoseDetector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AvatarFittingTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("avatar_fitting_overlay_v1", 0)
    private fun garment(category: String) = WardrobeItem(id = category, name = category,
        category = category, colorName = "", colorValue = 0, imageUri = "")

    @Before @After fun reset() { prefs.edit().clear().commit(); AvatarFittingStore.load(app) }

    @Test fun placementsUseAvatarShouldersHipsLegsAndGarmentCategory() {
        val canvas = Size(400f, 600f)
        val profile = AvatarProfile(170f, 65f)
        val shirt = fitAvatar(FitSlot.TOP, garment("상의"), 1.2f, canvas, profile)
        val coat = fitAvatar(FitSlot.OUTER, garment("아우터"), 1.2f, canvas, profile)
        val pants = fitAvatar(FitSlot.BOTTOM, garment("하의"), 1.4f, canvas, profile)
        val shoes = fitAvatar(FitSlot.SHOES, garment("신발"), .6f, canvas, profile)
        assertTrue(coat.width > shirt.width)
        assertTrue(pants.center.y > shirt.center.y)
        assertTrue(shoes.center.y > pants.center.y)
        val dress = fitAvatar(FitSlot.TOP, garment("원피스"), 2f, canvas, profile)
        assertTrue(dress.center.y + dress.width > shirt.center.y + shirt.width * .6f)
        assertTrue(fitAvatar(FitSlot.TOP, garment("상의"), 1.2f, canvas,
            profile.copy(shoulder = 1.2f)).width > shirt.width)
        assertTrue(fitAvatar(FitSlot.BOTTOM, garment("하의"), 1.4f, canvas,
            profile.copy(leg = 1.2f)).center.y < pants.center.y)
    }

    @Test fun photoAutoFitUsesPoseAnchorsAndDressExtendsBelowTop() {
        val body = FittingPoseDetector.BodyPoints(400, 800,
            Offset(130f, 230f) to Offset(270f, 230f),
            Offset(150f, 430f) to Offset(250f, 430f),
            Offset(165f, 740f) to Offset(235f, 740f))
        val canvas = Size(400f, 800f)
        val shirt = fit(FitSlot.TOP, garment("상의"), 1.2f, canvas, body)
        val dress = fit(FitSlot.TOP, garment("원피스"), 2f, canvas, body)
        val outer = fit(FitSlot.OUTER, garment("아우터"), 1.2f, canvas, body)
        val pants = fit(FitSlot.BOTTOM, garment("하의"), 1.4f, canvas, body)
        val shoes = fit(FitSlot.SHOES, garment("신발"), .6f, canvas, body)
        assertTrue(outer.width > shirt.width)
        assertTrue(dress.center.y + dress.width > shirt.center.y + shirt.width * .6f)
        assertTrue(pants.center.y > shirt.center.y)
        assertTrue(shoes.center.y > pants.center.y)
    }

    @Test fun photoAutoFitRequiresGarmentLandmarksAndChangesRealPlacement() {
        val canvas=Size(400f,800f)
        val pose=FittingPoseDetector.BodyPoints(400,800,
            Offset(100f,200f) to Offset(240f,220f),null,null)
        assertNull(photoAutoPlacement(FitSlot.TOP,garment("상의"),1.2f,canvas,null))
        assertNull(photoAutoPlacement(FitSlot.BOTTOM,garment("하의"),1.2f,canvas,pose))
        assertNull(photoAutoPlacement(FitSlot.TOP,garment("원피스"),2f,canvas,pose))
        assertNull(photoAutoPlacement(FitSlot.ACCESSORY,garment("액세서리"),1f,canvas,pose))
        val placement=checkNotNull(photoAutoPlacement(FitSlot.TOP,garment("상의"),1.2f,canvas,pose))
        assertNotEquals(Offset(200f,280f),placement.center)
        assertTrue(placement.rotation>0f)
        assertTrue(placement.width>200f)
        val twice=checkNotNull(photoAutoPlacement(FitSlot.TOP,garment("상의"),1.2f,Size(800f,1600f),pose))
        assertEquals(placement.center*2f,twice.center)
        assertEquals(placement.width*2f,twice.width,.001f)
    }

    @Test fun explicitOverlaySaveRestoresNormalizedPlacementWithoutPhotoOrUri() {
        val input = listOf(AvatarOverlay("shirt-1", .51f, .37f, .31f, 1.2f, 8f))
        assertTrue(AvatarFittingStore.save(app, input))
        assertEquals(input, AvatarFittingStore.load(app))
        val raw = prefs.getString("placements", "")!!
        assertFalse(raw.contains("content://"))
        assertFalse(raw.contains("file://"))
    }

    @Test fun corruptOverlayIsPreservedAndCannotBeSilentlyOverwritten() {
        prefs.edit().putString("placements", "{bad").commit()
        assertTrue(AvatarFittingStore.load(app).isEmpty())
        assertNotNull(AvatarFittingStore.issue)
        assertFalse(AvatarFittingStore.save(app, emptyList()))
        assertEquals("{bad", prefs.getString("placements", null))
        assertFalse(AvatarOverlay("bad", Float.NaN, .5f, .3f, 1f, 0f).valid())
        assertTrue(AvatarFittingStore.clear(app))
        assertNull(prefs.getString("placements", null))
    }
}
