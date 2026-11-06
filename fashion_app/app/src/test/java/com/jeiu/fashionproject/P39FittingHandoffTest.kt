package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.jeiu.fashionproject.data.DeviceGarmentRole
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P39FittingHandoffTest {
    private fun garment(id: String, category: String) = WornGarment(
        WardrobeItem(id, id, category, "시험", 0xffaaaaaa, "content://synthetic/$id-thumb",
            originalUri = "content://synthetic/$id-original"),
        "content://synthetic/$id-cutout", 1.5f, Offset(100f, 200f), 80f, 1.2f, 15f)

    @Test fun allThreeOriginalsAndPlacementsFreezeWithoutLosingShoes() {
        val worn = mutableListOf(garment("shoe", "신발"), garment("top", "상의"), garment("bottom", "하의"))
        val snapshot = onDeviceFittingSelection("content://synthetic/person", 7, worn, Size(400f, 800f))!!
        assertEquals(listOf("top", "bottom", "shoe"), snapshot.garments.map { it.id })
        assertEquals(DeviceGarmentRole.entries.toList(), snapshot.garments.map { it.role })
        assertEquals("content://synthetic/shoe-original", snapshot.garments.last().source)
        assertEquals("400.0,800.0,100.0,200.0,80.0,1.2,15.0,1.5", snapshot.garments.first().placementKey)
        worn[1] = garment("top-B", "상의").copy(scale = 2f, rotation = -30f)
        assertEquals("top", snapshot.garments.first().id)
        assertNotEquals(snapshot, onDeviceFittingSelection("content://synthetic/person", 7, worn, Size(400f, 800f)))
        assertNotEquals(snapshot, snapshot.copy(revision = 8))
    }

    @Test fun unsupportedCategoriesAreNotSilentlyDroppedOrRelabelledAsTops() {
        for (category in listOf("원피스", "아우터", "액세서리")) {
            assertNull(onDeviceFittingSelection("content://synthetic/person", 1,
                listOf(garment("top", "상의"), garment("extra", category)), Size(400f, 800f)))
        }
        assertNull(onDeviceFittingSelection(null, 1, emptyList(), Size.Zero))
        val incomplete = onDeviceFittingSelection("content://synthetic/person", 1, listOf(garment("top", "상의")), Size.Zero)!!
        assertEquals(1, incomplete.garments.size) // Screen/controller must block, never fabricate missing clothing.
    }

    @Test fun openingOnDeviceModeRejectsLatePhotoDropWithoutChangingAvatarOrPhotoClothes() {
        val manual = ManualFittingViewModel()
        val person = Uri.parse("content://synthetic/person")
        manual.selectPhoto(person)
        manual.acceptPhotoDisplaySize(manual.photoGeneration, person, Size(1000f, 2000f))
        manual.resizeCanvas(Size(400f, 800f))
        val photo = garment("photo", "상의")
        val avatar = garment("avatar", "상의")
        manual.worn[FitSlot.TOP] = photo
        manual.avatarWorn[FitSlot.TOP] = avatar
        val ticket = manual.beginPhotoDrop(FitSlot.TOP, Offset(110f, 210f))!!
        val pendingCardRequest = 20L
        manual.selectionRequests[FitSlot.BOTTOM] = pendingCardRequest
        val selected = onDeviceFittingSelection(person.toString(), manual.photoGeneration, manual.worn.values.toList(), manual.canvasSize)!!
        manual.openOnDevice(selected)
        assertNotEquals(pendingCardRequest, manual.selectionRequests[FitSlot.BOTTOM])
        assertEquals(selected, manual.deviceSelection)
        assertNull(manual.beginPhotoDrop(FitSlot.TOP, Offset(120f, 220f)))
        assertFalse(manual.applyPhotoDrop(ticket, garment("late", "상의")))
        assertSame(photo, manual.worn[FitSlot.TOP])
        assertSame(avatar, manual.avatarWorn[FitSlot.TOP])
    }
}
