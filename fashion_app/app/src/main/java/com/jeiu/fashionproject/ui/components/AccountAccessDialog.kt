package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** An honest entry point until a provider can use the existing authenticated backup contract.
 * No login exchange, storage mutation, queue flush, or account adoption happens in this dialog.
 */
@Composable
fun AccountAccessDialog(
    onDismiss: () -> Unit,
    onBackupRestore: () -> Unit,
    onReturnToStart: () -> Unit,
    preview: Boolean,
    authState: String,
    pendingCount: Int
) {
    val backupNotice = when {
        preview -> "Preview·QA에서는 개인 서버 동기화를 차단해요. 기존 기록은 이 기기에서 사용할 수 있어요."
        authState == "restore_pending" -> "복원 확인이 끝나지 않아 전송을 보류하고 있어요. 백업·복원에서 진행 상태를 확인해주세요."
        authState == "secure_storage_unavailable" -> "백업 인증 저장소를 확인하지 못했어요. 기존 기록을 유지하고 전송을 중지해요."
        authState == "server_mismatch" -> "서버가 바뀌어 백업 인증 확인이 필요해요. 기존 기록을 새 계정에 자동 연결하지 않아요."
        authState == "legacy_decision_required" -> "기존 기록의 소유권과 이전 방법을 먼저 확인해야 해요. 기록은 그대로 유지해요."
        authState == "ready" -> "이 기기의 백업 인증이 있어요. 이것만으로 서버 저장·복원 완료를 의미하지는 않아요."
        authState == "offline_or_unconfigured" -> "서버 연결 또는 인증 설정을 확인하지 못했어요. 기존 기록으로 계속 사용할 수 있어요."
        else -> "서버 백업 인증은 소셜 로그인과 별개예요. 연결·인증이 완료되지 않아도 기존 기기 기록은 계속 사용할 수 있어요."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("계정 · 게스트 이용") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("소셜 로그인 미설정", style = MaterialTheme.typography.titleMedium)
                Text("이 빌드에는 소셜 로그인을 연결하지 않았어요. 로그인 없이 기존 기기 기록으로 계속 이용할 수 있어요.")
                listOf("Google", "카카오", "Facebook", "Apple").forEachIndexed { index, provider ->
                    OutlinedButton(onClick = {}, enabled = false,
                        modifier = Modifier.fillMaxWidth().testTag("accountProvider-$index")) {
                        Text("$provider · 미설정")
                    }
                }
                Text(backupNotice, modifier = Modifier.testTag("accountBackupNotice"))
                Text("전송 대기 ${pendingCount.coerceAtLeast(0)}건 · 서버 저장 완료가 아니에요.",
                    style = MaterialTheme.typography.bodySmall)
                Text("닫기·취소·시작 화면 이동은 로그아웃이나 기기 데이터 삭제가 아니에요. 옷장·사진·체형 기록을 지우지 않아요.")
                OutlinedButton(onClick = onBackupRestore,
                    modifier = Modifier.fillMaxWidth().testTag("accountBackupRestore")) {
                    Text("데이터 백업 · 복원 안내")
                }
                TextButton(onClick = onReturnToStart,
                    modifier = Modifier.fillMaxWidth().testTag("accountReturnToStart")) {
                    Text("기록을 유지하고 시작 화면으로")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("accountContinueLocal")) {
                Text("기존 기록으로 계속하기")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
