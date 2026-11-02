package com.jeiu.fashionproject

import android.app.Application
import android.graphics.Bitmap
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
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class LocalVtonFlowTest {
    @get:Rule val compose=createComposeRule()
    private val photo=Uri.parse("content://test-original/person.png")
    private val garment=TryOnGarment("top","상의 원본","상의","content://test-original/shirt.png")
    private val saved=SavedStateHandle(mapOf("engine" to "local"))
    private val fake=Fake()
    private val model=TryOnViewModel(saved).also {it.gateway=fake}
    private var visible by mutableStateOf(true)
    private var inputsAbsent by mutableStateOf(false)
    private val owner=object:ActivityResultRegistryOwner {
        override val activityResultRegistry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {}
        }
    }
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun connect() {
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
    @Test fun processRestorationQueriesOnlyAndDoesNotStoreCredentials() {
        fake.uncertain=true
        compose.runOnIdle {model.generate(app,photo,listOf(garment),true)}
        compose.waitUntil(5000) {model.state.pending && !model.state.watching}
        val restored=TryOnViewModel(saved).also {it.gateway=fake}
        compose.runOnIdle {fake.uncertain=false;restored.resume()}
        compose.waitUntil(5000) {!restored.state.pending}
        compose.runOnIdle {
            assertEquals(1,fake.posts)
            assertFalse(saved.keys().any {it.contains("token",true)})
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
    private class Fake:TryOnGateway {
        val images=mutableListOf<String>()
        var posts=0;var base="";var categories=listOf<String>()
        var gate:CompletableDeferred<Unit>?=null;var fail=false;var uncertain=false
        override suspend fun image(context:android.content.Context,source:String):String {images+=source;return "prepared-inline-image"}
        override suspend fun check(connection:TryOnConnection) {base=connection.base}
        override suspend fun submit(connection:TryOnConnection,id:String,person:String,garments:List<Pair<String,String>>) {
            posts++;base=connection.base;categories=garments.map {it.first};gate?.await()
            if(uncertain) throw IOException("synthetic lost response")
            if(fail) throw TryOnHttpFailure(503,"자체 AI 테스트 실패")
        }
        override suspend fun status(connection:TryOnConnection,id:String)=TryOnJob("completed",null,1,1,true)
        override suspend fun result(connection:TryOnConnection,id:String):ByteArray {
            val image=Bitmap.createBitmap(256,384,Bitmap.Config.ARGB_8888);val out=ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();return out.toByteArray()
        }
        override suspend fun cancel(connection:TryOnConnection,id:String) {}
    }
}
