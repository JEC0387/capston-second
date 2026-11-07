package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.screens.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** JVM-only synthetic navigation/placement checks; no photos, account or server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w600dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P48ServerEntryTest {
    @get:Rule val compose = createComposeRule()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @After fun clearTestViewModels() { owner.viewModelStore.clear() }
    private fun garment(id: String) = WornGarment(
        WardrobeItem(id, id, "상의", "합성 시험", 0, "content://synthetic/$id"),
        "content://synthetic/$id", 1.5f, Offset(100f, 200f), 80f, 1.2f, 15f)

    @Test fun explicitServerEntryRejectsLateDropsAndKeepsPhotoAvatarAndOnDeviceState() {
        val vm = ManualFittingViewModel()
        val person = Uri.parse("content://synthetic/person")
        vm.selectPhoto(person)
        vm.acceptPhotoDisplaySize(vm.photoGeneration, person, Size(1000f, 2000f))
        vm.resizeCanvas(Size(400f, 800f))
        val photo = garment("photo"); val avatar = garment("avatar")
        vm.worn[FitSlot.TOP] = photo; vm.avatarWorn[FitSlot.TOP] = avatar
        val drop = vm.beginPhotoDrop(FitSlot.TOP, Offset(120f, 210f))!!
        vm.selectionRequests[FitSlot.BOTTOM] = 7L
        val selected = onDeviceFittingSelection(person.toString(), vm.photoGeneration,
            vm.worn.values.toList(), vm.canvasSize)!!
        vm.openServerOutfit(selected)
        assertTrue(vm.showServerOutfitAi)
        assertFalse(vm.showOnDeviceAi)
        assertNull(vm.deviceSelection)
        assertEquals(selected, vm.serverOutfitSelection)
        assertNotEquals(7L, vm.selectionRequests[FitSlot.BOTTOM])
        assertNull(vm.beginPhotoDrop(FitSlot.TOP, Offset.Zero))
        assertFalse(vm.applyPhotoDrop(drop, garment("late")))
        assertSame(photo, vm.worn[FitSlot.TOP]); assertSame(avatar, vm.avatarWorn[FitSlot.TOP])
        vm.showServerOutfitAi = false
        assertNotNull(vm.beginPhotoDrop(FitSlot.TOP, Offset(100f, 200f)))
        vm.openOnDevice(selected)
        assertTrue(vm.showOnDeviceAi)
        assertEquals(selected, vm.deviceSelection)
    }

    @Test fun defaultBuildCanOpenExplicitBlockedServerScreenAndReturnWithoutAnInput() {
        // The ordinary Debug CI also runs this class. Approval defaults off in
        // every build; Preview/QA additionally force it off in Gradle.
        assertFalse(BuildConfig.SERVER_OUTFIT_APPROVED)
        compose.setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            MaterialTheme { VirtualFittingScreen(onBack = {}) }
        } }
        compose.onNodeWithTag("openServerOutfitFromFitting").performScrollTo().performClick()
        compose.onNodeWithTag("serverOutfitVtonScreen").assertExists()
        compose.onNodeWithTag("serverOutfitBack").performScrollTo().performClick()
        compose.onNodeWithTag("openServerOutfitFromFitting").performScrollTo().assertExists()
    }
}
