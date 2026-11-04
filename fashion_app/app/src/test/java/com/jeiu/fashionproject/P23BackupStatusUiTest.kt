package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jeiu.fashionproject.ui.components.BackupQueueStatusText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class P23BackupStatusUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun drainingQueueDoesNotAnnounceVerifiedServerBackup() {
        val pending=mutableStateOf(3)
        compose.setContent {BackupQueueStatusText(pending.value)}
        compose.onNodeWithText("서버로 보낼 기록 3개가 남았어요. 연결·인증이 준비되면 재시도해요.").assertIsDisplayed()
        compose.runOnIdle {pending.value=0}
        compose.onNodeWithText("현재 전송 대기 항목이 없어요. 서버 백업 완료나 복원 가능 여부는 아직 확인하지 않았어요.").assertIsDisplayed()
        compose.onNodeWithText("모든 기록이 서버에 저장됐어요").assertDoesNotExist()
    }
}
