package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.screens.*
import com.jeiu.fashionproject.ui.viewmodel.TryOnViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalVtonFlowTest {
    @get:Rule val compose=createComposeRule()
    private val photo=Uri.parse("content://test-original/person.png")
    private val garment=TryOnGarment("top","상의 원본","상의","content://test-original/shirt.png")
    private val saved=SavedStateHandle(mapOf("engine" to "local"))
    private val fake=Fake()
    private val model=TryOnViewModel(saved).also {it.gateway=fake}
    private var visible by mutableStateOf(true)
    private var inputsAbsent by mutableStateOf(false)
    private var registryCode=0
    private val registry=object:ActivityResultRegistry() {
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {registryCode=requestCode}
    }
    private val owner=object:ActivityResultRegistryOwner { override val activityResultRegistry=registry }
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun connect() {
        // Native Coil decoding needs real, reopenable fixture streams. These are
        // synthetic inputs, not real people, remote images or inference results.
        val input=Bitmap.createBitmap(32,64,Bitmap.Config.ARGB_8888)
        val bytes=try {
            input.eraseColor(Color.GRAY)
            ByteArrayOutputStream().apply {check(input.compress(Bitmap.CompressFormat.PNG,100,this))}.toByteArray()
        } finally {input.recycle()}
        listOf("person.png","shirt.png","pants.png","other-shirt.png","other.png").forEach {name ->
            shadowOf(app.contentResolver).registerInputStreamSupplier(Uri.parse("content://test-original/$name")) {
                ByteArrayInputStream(bytes)
            }
        }
        assertTrue(LocalVtonSession.configure("https://self-hosted.example","local-only-test-token-000000000000001"))
        assertTrue(TryOnSession.configure("https://paid.example","paid-only-test-token-0000000000000001"))
    }
    @After fun cleanup() {
        compose.runOnIdle {model.viewModelScope.cancel();LocalVtonSession.clear();TryOnSession.clear()}
    }
    private fun screen(garments:List<TryOnGarment> = listOf(garment)) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Box(Modifier.height(480.dp)) {
                    if(visible) LocalVtonScreen(if(inputsAbsent) null else photo,
                        if(inputsAbsent) emptyList() else garments,{},model)
                }
            }
        }
    }
    private fun approve() {
        compose.onNodeWithTag("localVtonGenerate").assertIsEnabled().performClick()
        compose.onNodeWithTag("localVtonConsentConfirm").performClick()
    }
    private fun done()=compose.waitUntil(5000) {!model.state.pending}
    @Test fun noLocalServerDoesNotUseConfiguredPaidConnection() {
        LocalVtonSession.clear()
        screen()
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("localVtonServerState").assertTextContains("미설정",substring=true)
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true);assertEquals(0,fake.posts);assertTrue(fake.images.isEmpty())}
        assertEquals("https://paid.example",TryOnSession.connection!!.base)
    }
    @Test fun declinedConsentDoesNotPrepareImagesOrPost() {
        screen();compose.onNodeWithTag("localVtonGenerate").performClick()
        compose.onNodeWithText("전송하지 않기").performClick()
        compose.runOnIdle {assertEquals(0,fake.posts);assertTrue(fake.images.isEmpty())}
    }
    @Test fun genuineEngineBoundarySupportsCompareAndBothSaveFormats() {
        screen();approve();done()
        compose.runOnIdle {
            assertEquals(1,fake.posts)
            assertEquals(listOf(photo.toString(),garment.image),fake.images)
            assertEquals("https://self-hosted.example",fake.base)
        }
        compose.onNodeWithTag("localVtonSuccess").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("자체 AI 원본/결과 비교").performScrollTo().performClick()
        compose.onNodeWithText("PNG 저장").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("JPG 저장").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("자체 AI 확대 비교").performScrollTo().performClick()
        compose.onNodeWithTag("tryOnZoomPreview").assertIsDisplayed()
    }
    @Test fun navigationAndDoubleClickKeepOneRequestAndOriginalSelection() {
        fake.gate=CompletableDeferred()
        screen();approve()
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true);visible=false}
        compose.runOnIdle {inputsAbsent=true;visible=true;model.resume()}
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.onNodeWithText("상의 · 상의 원본").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {fake.gate!!.complete(Unit)}
        done();compose.runOnIdle {assertEquals(1,fake.posts)}
    }
    @Test fun multipleGarmentsRequireAnExplicitSingleSelection() {
        screen(listOf(garment,TryOnGarment("bottom","하의 원본","하의","content://test-original/pants.png")))
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("localGarment-bottom").performScrollTo().performClick()
        approve();done()
        compose.runOnIdle {assertEquals(listOf("bottoms"),fake.categories)}
    }
    @Test fun failedNewGenerationDoesNotLabelOldResultAsNewSuccess() {
        screen();approve();done()
        compose.runOnIdle {fake.fail=true}
        approve();done()
        compose.runOnIdle {assertTrue(model.state.resultFromPreviousAttempt);assertNotNull(model.state.result)}
        compose.onNodeWithTag("localVtonSuccess").performScrollTo().assertTextContains("이전",substring=true)
    }
    @Test fun changingGarmentLabelsTheDisplayedOriginalRatherThanTheHiddenOldResult() {
        val other=TryOnGarment("other-top","다른 상의","상의","content://test-original/other-shirt.png")
        screen(listOf(garment,other))
        compose.onNodeWithTag("localGarment-top").performScrollTo().performClick()
        approve();done()
        compose.onNodeWithContentDescription("자체 AI 생성 결과").assertExists()
        val firstResult=requireNotNull(model.state.result).copyOf()
        val firstRequest=model.requestId
        compose.onNodeWithTag("localGarment-other-top").performScrollTo().performClick()
        compose.onNodeWithTag("localVtonSuccess").assertDoesNotExist()
        compose.onNodeWithText("PNG 저장").assertDoesNotExist()
        compose.onNodeWithContentDescription("자체 AI 생성 결과").assertDoesNotExist()
        compose.onNodeWithContentDescription("원본 사진").assertExists()
        compose.runOnIdle {assertEquals(1,fake.posts)}
        compose.runOnIdle {fake.resultColor=Color.BLUE}
        approve();done()
        compose.runOnIdle {
            assertEquals(2,fake.posts)
            assertNotEquals(firstRequest,model.requestId)
            assertEquals(listOf(other),model.state.resultGarments)
            assertEquals(other.image,fake.images.last())
            assertFalse(firstResult.contentEquals(requireNotNull(model.state.result)))
            assertFalse(model.resultMatches(photo,listOf(garment)))
            assertTrue(model.resultMatches(photo,listOf(other)))
            assertFalse(model.state.resultFromPreviousAttempt)
        }
    }
    @Test fun unreadyServerBlocksGenerationUntilExplicitSuccessfulReadinessCheck() {
        fake.notReady=true
        screen()
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.onNodeWithTag("localVtonServerState").assertTextContains("GPU 미준비",substring=true)
        compose.runOnIdle {fake.notReady=false;assertEquals(0,fake.posts);assertTrue(fake.images.isEmpty())}
        compose.onNodeWithText("자체 서버 준비 다시 확인").performScrollTo().performClick()
        compose.onNodeWithTag("localVtonGenerate").assertIsEnabled()
        compose.runOnIdle {assertEquals(0,fake.posts);assertTrue(fake.images.isEmpty())}
    }
    @Test fun processRestorationQueriesOnlyAndDoesNotStoreCredentials() {
        fake.uncertain=true
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true)}
        compose.waitUntil(5000) {model.state.pending && !model.state.watching}
        val restored=TryOnViewModel(SavedStateHandle(saved.keys().associateWith {saved.get<Any?>(it)})).also {it.gateway=fake}
        compose.runOnIdle {fake.uncertain=false;restored.resume()}
        compose.waitUntil(5000) {!restored.state.pending}
        compose.runOnIdle {
            assertEquals(1,fake.posts)
            assertFalse(saved.keys().any {it.contains("token",true)})
            assertEquals("Restored output must retain the submitted garment, not an empty/current draft", listOf(garment), restored.state.resultGarments)
            assertTrue(restored.resultMatches(photo, listOf(garment)))
            assertTrue("GET recovery is a previous request, not a new generation", restored.state.resultFromPreviousAttempt)
            restored.viewModelScope.cancel()
        }
    }
    @Test fun externalScreenProvidesExplicitLocalEntryWithoutPaidSubmission() {
        LocalVtonSession.clear()
        val external=TryOnViewModel(SavedStateHandle()).also {it.gateway=fake}
        compose.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            AiTryOnScreen(photo,listOf(garment),{},model=external)
        }}
        compose.onNodeWithTag("openLocalVton").performScrollTo().performClick()
        compose.onNodeWithTag("localVtonTitle").assertIsDisplayed()
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,fake.posts);external.viewModelScope.cancel()}
    }
    @Test fun fittingScreenCanChooseFromWardrobeWithoutFirstCreatingAStickerPlacement() {
        LocalVtonSession.clear() // No network request or generated image in this UI test.
        WardrobeStore.load(app)
        val item=WardrobeStore.items.value.first {TryOnPlan.categoryFor(it.category)!=null}
        compose.setContent {VirtualFittingScreen(onBack={})}
        compose.onNodeWithTag("openLocalVtonFromFitting").performScrollTo().performClick()
        compose.onNodeWithTag("localVtonTitle").assertIsDisplayed()
        compose.onNodeWithTag("localGarment-${item.id}").performScrollTo().performClick()
        compose.onNodeWithTag("localVtonGenerate").assertIsNotEnabled()
        compose.onNodeWithText("원본 전신 사진 선택").assertExists()
        compose.runOnIdle {assertEquals(0,fake.posts)}
    }
    @Test fun explicitLostJobRecoveryNeverPostsAutomatically() {
        fake.uncertain=true
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true)}
        compose.waitUntil(5000) {model.state.pending && !model.state.watching}
        compose.runOnIdle {
            model.stopLocalTracking()
            assertFalse(model.state.pending)
            assertNull(model.requestId)
            assertEquals(1,fake.posts)
        }
    }
    @Test fun cancellationDuringUploadWaitsForTheJobAndNeverPostsTwice() {
        fake.gate=CompletableDeferred()
        screen();approve()
        compose.waitUntil(5000) {fake.posts==1}
        compose.runOnIdle {model.cancel();assertEquals(0,fake.cancels)}
        compose.runOnIdle {fake.gate!!.complete(Unit)}
        done()
        compose.runOnIdle {
            assertEquals(1,fake.posts)
            assertEquals(1,fake.cancels)
            assertNull(model.state.result)
            assertNull(model.requestId)
        }
    }
    @Test fun resultExpiryBetweenStatusAndPngDownloadIsTerminal() {
        fake.expiredResult=true
        screen();approve();done()
        compose.runOnIdle {
            assertEquals(1,fake.posts)
            assertNull(model.state.result)
            assertNull(model.requestId)
            assertTrue(model.state.error.orEmpty().contains("만료"))
        }
    }
    @Test fun savePickerKeepsTheResultChosenBeforeAnotherRequestCompletes() {
        fake.resultColor=Color.RED
        screen();approve();done()
        fake.gate=CompletableDeferred()
        approve()
        compose.waitUntil(5000) {fake.posts==2}
        compose.onNodeWithText("PNG 저장").performScrollTo().performClick()
        val target=File(app.cacheDir,"p32-export-snapshot.png")
        compose.runOnIdle {fake.resultColor=Color.BLUE;fake.gate!!.complete(Unit)}
        done()
        compose.runOnIdle {registry.dispatchResult(registryCode,Uri.fromFile(target))}
        compose.waitUntil(5000) {compose.onAllNodesWithText("자체 AI 결과를 저장했어요.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("자체 AI 결과를 저장했어요.").performScrollTo().assertIsDisplayed()
        val decoded=BitmapFactory.decodeFile(target.path)
        assertNotNull(decoded)
        assertEquals("Save must export the selected red result A, not later blue result B",Color.RED,decoded.getPixel(100,100))
        decoded.recycle()
        assertEquals(2,fake.posts)
    }
    @Test fun explicitlyReopenedSavedImageIsNotANewGeneratedResult() {
        // Synthetic saved output in the test app sandbox; no real photo or model execution.
        val target=File(app.cacheDir,"p32-previous-synthetic.png")
        runBlocking {target.writeBytes(fake.result(LocalVtonSession.connection!!,"unused-synthetic"))}
        screen()
        compose.onNodeWithText("저장한 이미지 다시 열기").performScrollTo().performClick()
        compose.runOnIdle {registry.dispatchResult(registryCode,Uri.fromFile(target))}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("localSavedImage").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("이전에 저장한 이미지 · 새 AI 생성 결과가 아니에요").assertIsDisplayed()
        compose.onNodeWithTag("localVtonSuccess").assertDoesNotExist()
        compose.runOnIdle {assertEquals(0,fake.posts);assertNull(model.state.result)}
        compose.onNodeWithText("저장 이미지 닫기").performClick()
        compose.onNodeWithTag("localSavedImage").assertDoesNotExist()
    }
    @Test fun returningToCompletedResultClearlyLabelsItAsPreviousWithoutPosting() {
        screen();approve();done()
        compose.runOnIdle {visible=false}
        compose.runOnIdle {visible=true}
        compose.onNodeWithTag("localVtonSuccess").performScrollTo().assertTextContains("이전",substring=true)
        compose.runOnIdle {assertEquals(1,fake.posts)}
    }
    @Test fun legacyJobWithoutGarmentMetadataNeverMatchesAnEmptyOrNewSelection() {
        val legacy=TryOnViewModel(SavedStateHandle(mapOf("engine" to "local", "requestId" to java.util.UUID.randomUUID().toString(),
            "base" to LocalVtonSession.connection!!.base, "person" to photo.toString()))).also {it.gateway=fake}
        compose.runOnIdle {legacy.resume()}
        compose.waitUntil(5000) {!legacy.state.pending}
        compose.runOnIdle {
            assertFalse(legacy.resultMatches(photo,emptyList()))
            assertFalse(legacy.resultMatches(photo,listOf(garment)))
            assertEquals(0,fake.posts)
            legacy.viewModelScope.cancel()
        }
    }
    @Test fun restoredScreenKeepsResultAndSaveActionsWhenEntryHasNoGarments() = restoredScreen(emptyList())
    @Test fun restoredScreenSelectsSubmittedGarmentAmongSeveralThenHidesResultForAnother() =
        restoredScreen(listOf(garment,TryOnGarment("other","다른 옷","상의","content://test-original/other.png")))

    private fun restoredScreen(incoming: List<TryOnGarment>) {
        fake.uncertain=true
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true)}
        compose.waitUntil(5000) {model.state.pending && !model.state.watching}
        val restored=TryOnViewModel(SavedStateHandle(saved.keys().associateWith {saved.get<Any?>(it)})).also {it.gateway=fake}
        fake.uncertain=false
        try {
            compose.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Box(Modifier.height(480.dp)) {LocalVtonScreen(null,incoming,{},restored)}
            }}
            compose.onNodeWithText("기존 자체 AI 작업 조회").performScrollTo().performClick()
            compose.waitUntil(5000) {!restored.state.pending}
            compose.onNodeWithTag("localVtonSuccess").performScrollTo().assertTextContains("이전",substring=true)
            compose.onNodeWithText("PNG 저장").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("localGarment-top").performScrollTo().assertIsSelected()
            if(incoming.size>1) {
                compose.onNodeWithTag("localGarment-other").performScrollTo().performClick()
                compose.onNodeWithTag("localVtonSuccess").assertDoesNotExist()
                compose.onNodeWithText("PNG 저장").assertDoesNotExist()
            }
            compose.runOnIdle {assertEquals(1,fake.posts)}
        } finally {compose.runOnIdle {restored.viewModelScope.cancel()}}
    }
    private class Fake:TryOnGateway {
        val images=mutableListOf<String>()
        var posts=0;var cancels=0;var base="";var categories=listOf<String>();var notReady=false
        var gate:CompletableDeferred<Unit>?=null;var fail=false;var uncertain=false;var expiredResult=false
        var resultColor=Color.TRANSPARENT
        override suspend fun image(context:android.content.Context,source:String):String {images+=source;return "prepared-inline-image"}
        override suspend fun check(connection:TryOnConnection) {
            base=connection.base
            if(notReady) throw TryOnFailure("합성 시험: GPU 미준비")
        }
        override suspend fun submit(connection:TryOnConnection,id:String,person:String,garments:List<Pair<String,String>>) {
            posts++;base=connection.base;categories=garments.map {it.first};gate?.await()
            if(uncertain) throw IOException("synthetic lost response")
            if(fail) throw TryOnHttpFailure(503,"자체 AI 테스트 실패")
        }
        override suspend fun status(connection:TryOnConnection,id:String)=if(cancels>0)
            TryOnJob("cancelled","cancelled",1,1,false) else TryOnJob("completed",null,1,1,true)
        override suspend fun result(connection:TryOnConnection,id:String):ByteArray {
            if(expiredResult) throw TryOnHttpFailure(410,LocalVtonRepository.friendly("result_expired"))
            val image=Bitmap.createBitmap(256,384,Bitmap.Config.ARGB_8888);image.eraseColor(resultColor);val out=ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();return out.toByteArray()
        }
        override suspend fun cancel(connection:TryOnConnection,id:String) {cancels++}
    }
}
