package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.ui.screens.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real long-press/move/up events. Fixtures are generated pixels in the JVM app sandbox only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w600dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P36DragUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val owner=object:ViewModelStoreOwner {override val viewModelStore=ViewModelStore()}
    private lateinit var vm:ManualFittingViewModel
    private lateinit var images:ImageLoader
    private val files=mutableListOf<File>()
    private fun resetFixtureStoreCache() {
        // JVM singleton survives test methods; the next isolated app startup must read its own
        // generated files, not the previous method's now-deleted synthetic image URIs.
        WardrobeStore::class.java.getDeclaredField("loaded").apply { isAccessible=true }.setBoolean(null,false)
    }

    @Before fun fixture() {
        vm=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=ManualFittingViewModel() as T
        })[ManualFittingViewModel::class.java]
        val body=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888).apply {density=Bitmap.DENSITY_NONE;eraseColor(Color.LTGRAY)}
        images=ImageLoader.Builder(app).components {add(object:Interceptor {
            override suspend fun intercept(chain:Interceptor.Chain):ImageResult = SuccessResult(BitmapDrawable(app.resources,body),chain.request,DataSource.MEMORY)
        })}.build();Coil.setImageLoader(images)
        val fixtures=listOf("top-a" to "상의","top-b" to "상의", "pants-a" to "하의",
            "pants-b" to "하의", "shoes-a" to "신발", "shoes-b" to "신발")
        val items=fixtures.mapIndexed { i,(id,category) ->
            val file=File(app.cacheDir,"p36-$id-${System.nanoTime()}.png");files.add(file)
            val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(if(i==0)Color.RED else Color.BLUE)}
            file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
            WardrobeItem(id,id,category,"synthetic",0,Uri.fromFile(file).toString(),addedAt=1)
        }
        resetFixtureStoreCache()
        WardrobeStore.writeForRestore(app,items);WardrobeStore.load(app)
        assertEquals("UI must receive this test's generated files, not a prior singleton cache",
            items.associate {it.id to it.imageUri},WardrobeStore.items.value.associate {it.id to it.imageUri})
        for (item in items) assertNotNull("Generated fixture must decode through the real garment loader: ${item.id}",
            runBlocking {GarmentGeometry.load(app,item)})
        vm.selectPhoto(Uri.parse("content://synthetic/p36-body"))
        compose.setContent {CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {VirtualFittingScreen(onBack={})}}
        compose.waitUntil(10000) {vm.photoDisplaySize!=null && vm.canvasSize.height>0}
    }
    @After fun release() {
        owner.viewModelStore.clear();images.shutdown();Coil.reset()
        resetFixtureStoreCache();files.forEach {it.delete()}
    }

    private fun drag(id:String,outside:Boolean=false,cancelled:Boolean=false) {
        val before=compose.onNodeWithTag("fittingCanvas").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("manualWardrobe").performScrollTo()
            .performScrollToNode(hasTestTag("manualGarment-$id"))
        val card=compose.onNodeWithTag("manualGarment-$id").performScrollTo()
        val cardBounds=card.fetchSemanticsNode().boundsInRoot
        val canvas=compose.onNodeWithTag("fittingCanvas").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Scrolling wardrobe must not move the drop canvas",before,canvas)
        assertTrue("Photo drop target must remain nonzero",canvas.width>0f && canvas.height>0f)
        val target=if(outside) Offset(2f,2f) else canvas.center
        // Flush DOWN before advancing the Compose clock: queued event timestamps alone do not
        // guarantee the coroutine long-press timeout has elapsed in Robolectric.
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(750)
        compose.onNodeWithTag("wardrobeDragGhost").assertExists()
        compose.onNodeWithTag("photoDropTarget").assertIsDisplayed()
        card.performTouchInput {
            advanceEventTime(750)
            moveTo(target-cardBounds.topLeft,100)
        }
        compose.onNodeWithTag("wardrobeDragGhost").assertExists()
        if (!outside) {
            val ghostCenter=compose.onNodeWithTag("wardrobeDragGhost").fetchSemanticsNode().boundsInRoot.center
            assertEquals("Ghost must follow the finger into the photo: x",target.x,ghostCenter.x,1.5f)
            assertEquals("Ghost must follow the finger into the photo: y",target.y,ghostCenter.y,1.5f)
        }
        card.performTouchInput { if(cancelled) cancel() else up() }
        compose.waitForIdle()
        compose.onNodeWithTag("wardrobeDragGhost").assertDoesNotExist()
        println("P36 synthetic drop id=$id outside=$outside cancel=$cancelled requests=${vm.selectionRequests} pending=${vm.pendingPhotoDrops.keys} slots=${vm.worn.keys}")
        compose.onAllNodesWithTag("fittingInputNotice").fetchSemanticsNodes().forEach {println("P36 synthetic notice: ${it.config}")}
    }

    @Test fun wardrobeCardDropsReplaceSameSlotAndUndoRestoresPreviousGarment() {
        drag("top-a")
        // The decode resumes on Main. Drain the UI queue before inspecting Compose state;
        // polling it only from the test thread can leave that completion queued in Robolectric.
        compose.waitUntil(10000) {compose.runOnIdle {vm.worn[FitSlot.TOP]?.item?.id=="top-a"}}
        compose.onNodeWithTag("fittingInputNotice").assertTextContains("수동 배치",substring=true)
        drag("top-b")
        compose.waitUntil(10000) {compose.runOnIdle {vm.worn[FitSlot.TOP]?.item?.id=="top-b"}}
        assertEquals(1,vm.worn.size)
        compose.onNodeWithTag("photoDropUndo").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("top-a",vm.worn[FitSlot.TOP]?.item?.id);assertTrue(vm.avatarWorn.isEmpty())}
        // Undo changes the notice, whose effect scrolls the controls back to their beginning.
        // Settle that effect before scrolling to and tapping the wardrobe again.
        compose.waitForIdle()
        // Original click selection still toggles the chosen garment.
        compose.onNodeWithTag("manualWardrobe").performScrollTo()
            .performScrollToNode(hasTestTag("manualGarment-top-a"))
        val selectedCard=compose.onNodeWithTag("manualGarment-top-a").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        selectedCard.performTouchInput {click()}
        compose.runOnIdle {
            assertTrue("Tap must remove the selected garment; request=${vm.selectionRequests}, worn=${vm.worn.values.map {it.item.id}}",vm.worn.isEmpty())
        }
    }

    @Test fun outsideDropAndGestureCancellationDoNotRemoveOrAddClothes() {
        drag("top-a",outside=true)
        compose.runOnIdle {assertTrue(vm.worn.isEmpty())}
        drag("top-b",cancelled=true)
        compose.runOnIdle {assertTrue(vm.worn.isEmpty());assertNull(vm.photoDropUndo)}
        compose.onNodeWithTag("wardrobeDragGhost").assertDoesNotExist()
    }

    @Test @Config(qualifiers="w384dp-h800dp")
    fun narrowScreenDropsAllCategoriesAndReplacingOnePreservesTheOthers() {
        for ((id,slot) in listOf("top-a" to FitSlot.TOP,"pants-a" to FitSlot.BOTTOM,"shoes-a" to FitSlot.SHOES)) {
            drag(id)
            compose.waitUntil(10000) {vm.worn[slot]?.item?.id==id}
        }
        val top=vm.worn.getValue(FitSlot.TOP); val shoes=vm.worn.getValue(FitSlot.SHOES)
        drag("pants-b")
        compose.waitUntil(10000) {vm.worn[FitSlot.BOTTOM]?.item?.id=="pants-b"}
        compose.runOnIdle {assertEquals(top,vm.worn[FitSlot.TOP]);assertEquals(shoes,vm.worn[FitSlot.SHOES])}
        val pants=vm.worn.getValue(FitSlot.BOTTOM)
        drag("shoes-b")
        compose.waitUntil(10000) {vm.worn[FitSlot.SHOES]?.item?.id=="shoes-b"}
        compose.runOnIdle {assertEquals(top,vm.worn[FitSlot.TOP]);assertEquals(pants,vm.worn[FitSlot.BOTTOM]);assertEquals(3,vm.worn.size)}
        compose.onNodeWithTag("photoDropUndo").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(shoes,vm.worn[FitSlot.SHOES]);assertEquals(top,vm.worn[FitSlot.TOP]);assertEquals(pants,vm.worn[FitSlot.BOTTOM]);assertTrue(vm.avatarWorn.isEmpty())}
    }
    @Test fun pinnedSlidersOnlyScaleSelectedGarmentAndKeepOpacityAcrossSelection() {
        drag("top-a"); compose.waitUntil(10000) {vm.worn[FitSlot.TOP]!=null}
        drag("pants-a"); compose.waitUntil(10000) {vm.worn[FitSlot.BOTTOM]!=null}
        val top=vm.worn.getValue(FitSlot.TOP)
        val pants=vm.worn.getValue(FitSlot.BOTTOM)
        compose.onNodeWithTag("manualScale").assertIsDisplayed()
        compose.onNodeWithTag("manualScaleUp").assertIsDisplayed().performClick()
        compose.onNodeWithTag("manualAlpha").assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.55f) }
        compose.runOnIdle {
            assertEquals(top,vm.worn[FitSlot.TOP])
            assertEquals(pants.copy(scale=1.1f),vm.worn[FitSlot.BOTTOM])
            assertEquals(.55f,vm.overlayAlpha,.001f)
            vm.activeSlot=FitSlot.TOP
        }
        compose.onNodeWithTag("manualScaleDown").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(top.copy(scale=.9f),vm.worn[FitSlot.TOP])
            assertEquals(pants.copy(scale=1.1f),vm.worn[FitSlot.BOTTOM])
            assertEquals(.55f,vm.overlayAlpha,.001f)
        }
    }

}
