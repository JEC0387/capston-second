package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.ui.components.AccountAccessDialog
import com.jeiu.fashionproject.ui.screens.MyPageScreen
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Local UI contract only; no OAuth/server call or device data. Auth failures are tested separately. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w600dp-h1000dp")
class P23AccountAccessUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val photo by lazy { File(app.filesDir,"wardrobe/p23-synthetic.bin") }
    private val snapshots=linkedMapOf<String,Map<String,*>>()
    private lateinit var previousServer:String

    @Before fun preventExternalProfileLookup() {
        previousServer=BackendApi.BASE_URL
        // Private authorization requires HTTPS, so even a surviving synthetic token cannot
        // transmit data. openConnection itself does not connect; the auth guard returns first.
        BackendApi.BASE_URL="http://127.0.0.1:1"
    }
    @After fun restoreServer() {BackendApi.BASE_URL=previousServer}

    private fun seedRecords() {
        for (name in listOf("wardrobe_store","db_sync","avatar_visual_profile_v1","product_interests")) {
            val prefs=app.getSharedPreferences(name,0)
            prefs.edit().putString("p23_synthetic_sentinel","existing-$name").commit()
            snapshots[name]=prefs.all.toMap()
        }
        photo.parentFile!!.mkdirs();photo.writeBytes(byteArrayOf(1,3,5,7))
    }
    private fun assertRecordsUnchanged() {
        snapshots.forEach { (name,values) -> assertEquals(name,values,app.getSharedPreferences(name,0).all) }
        assertTrue(photo.isFile);assertArrayEquals(byteArrayOf(1,3,5,7),photo.readBytes())
    }

    @Test fun unconfiguredProvidersCannotStartAuthenticationAndGuestKeepsRecords() {
        seedRecords()
        var dismissed=0;var backup=0;var start=0
        compose.setContent {AccountAccessDialog({dismissed++},{backup++},{start++},false,"offline_or_unconfigured",3)}
        compose.onNodeWithText("소셜 로그인 미설정").assertExists()
        for (i in 0..3) compose.onNodeWithTag("accountProvider-$i").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("accountBackupNotice").performScrollTo()
            .assertTextContains("서버 연결 또는 인증 설정을 확인하지 못했어요.",substring=true)
        compose.onNodeWithText("전송 대기 3건 · 서버 저장 완료가 아니에요.").assertExists()
        compose.onNodeWithTag("accountContinueLocal").performClick()
        compose.runOnIdle {assertEquals(1,dismissed);assertEquals(0,backup);assertEquals(0,start);assertRecordsUnchanged()}
    }

    @Test fun closeAndExplicitNavigationHaveSeparateCallbacksWithoutDeletingData() {
        seedRecords()
        val open=mutableStateOf(true)
        var backup=0;var start=0
        compose.setContent {if(open.value)AccountAccessDialog({open.value=false},{backup++;open.value=false},
            {start++;open.value=false},false,"ready",0)}
        compose.onNodeWithTag("accountBackupNotice").performScrollTo()
            .assertTextContains("서버 저장·복원 완료를 의미하지는 않아요.",substring=true)
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle {assertEquals(0,backup);assertEquals(0,start);assertRecordsUnchanged();open.value=true}
        compose.onNodeWithTag("accountBackupRestore").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,backup);assertEquals(0,start);assertRecordsUnchanged();open.value=true}
        compose.onNodeWithTag("accountReturnToStart").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,backup);assertEquals(1,start);assertRecordsUnchanged()}
    }

    @Test fun pendingRestoreAndPreviewNeverClaimRemoteSuccess() {
        seedRecords()
        val preview=mutableStateOf(false)
        val auth=mutableStateOf("restore_pending")
        compose.setContent {AccountAccessDialog({}, {}, {},preview.value,auth.value,2)}
        compose.onNodeWithTag("accountBackupNotice").performScrollTo()
            .assertTextContains("복원 확인이 끝나지 않아 전송을 보류",substring=true)
        compose.runOnIdle {auth.value="secure_storage_unavailable"}
        compose.onNodeWithTag("accountBackupNotice").assertTextContains("기존 기록을 유지하고 전송을 중지",substring=true)
        compose.runOnIdle {preview.value=true;auth.value="ready"}
        compose.onNodeWithTag("accountBackupNotice").assertTextContains("개인 서버 동기화를 차단",substring=true)
        compose.runOnIdle {assertRecordsUnchanged()}
    }

    @Test fun myPageOpensAccountGuideInsteadOfPretendingToLogOut() {
        var starts=0
        compose.setContent {MyPageScreen(onStyleAnalysis={},onStyleSuggestion={},onBodyAnalysis={},onReturnToStart={starts++})}
        compose.onNodeWithText("style@fashion.ai").assertDoesNotExist()
        compose.onNodeWithText("로그아웃",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithText("계정 · 게스트 이용").performScrollTo().performClick()
        compose.onNodeWithText("소셜 로그인 미설정").assertExists()
        compose.onNodeWithTag("accountContinueLocal").performClick()
        compose.runOnIdle {assertEquals(0,starts)}
        compose.onNodeWithText("계정 · 게스트 이용").performScrollTo().performClick()
        compose.onNodeWithTag("accountReturnToStart").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,starts)}
    }
}
