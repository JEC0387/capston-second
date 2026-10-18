package com.jeiu.fashionproject.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BackupRestore
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 마이페이지 > 데이터 백업 · 복원.
 *  1) 내 백업 코드 보기 · 복사 — 폰을 바꿀 때 새 폰에 입력
 *  2) 다른 폰의 기록 가져오기 — 백업 코드를 넣으면 옷장(사진 포함) · 착용 기록 · 취향 · 관심 상품을 가져옴
 *  3) 서버 백업 끄기 — 서버에 저장된 내 기록을 모두 지우고 이후로 보내지 않음 (폰 기록은 그대로)
 */
@Composable
fun BackupRestoreDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pending by DbSync.pendingCount.collectAsState()
    var enabled by remember { mutableStateOf(DbSync.isEnabled) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    val code = DbSync.backupCode

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("데이터 백업 · 복원", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1) 백업 코드
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("내 백업 코드", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = CharcoalBlack)
                    if (enabled && code != null) {
                        SelectionContainer {
                            Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Brown,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CreamDark).padding(10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (pending == 0) "모든 기록이 서버에 저장됐어요" else "서버로 보낼 기록 ${pending}개가 남았어요 (서버가 켜지면 자동 전송)",
                                style = MaterialTheme.typography.labelSmall, color = MediumGray, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(code))
                                Toast.makeText(context, "백업 코드를 복사했어요", Toast.LENGTH_SHORT).show()
                            }) { Text("복사", color = Camel) }
                        }
                        Text("폰을 바꾸거나 앱을 다시 설치할 때 이 코드를 넣으면 옷장 · 착용 기록 · 취향 · 관심 상품 · 저장한 코디 · 코디 일정을 되살릴 수 있어요. " +
                            "코드를 아는 사람은 내 기록을 볼 수 있으니 다른 사람에게 알려 주지 마세요.",
                            style = MaterialTheme.typography.labelSmall, color = MediumGray)
                    } else {
                        Text("서버 백업이 꺼져 있어요. 아래에서 켜면 코드가 생겨요.", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                    }
                }
                HorizontalDivider(color = LightGray)

                // 2) 가져오기
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("다른 폰의 기록 가져오기", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = CharcoalBlack)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it.take(80) }, singleLine = true, enabled = !busy,
                        label = { Text("예전 폰의 백업 코드") }, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { confirmRestore = true },
                        enabled = !busy && BackupRestore.normalize(input) != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Camel),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "가져오는 중…" else "가져오기") }
                    Text("이 폰의 지금 기록은 가져온 기록으로 바뀌어요. 체형 동의 · 알림 설정은 이 폰에서 다시 정해 주세요.",
                        style = MaterialTheme.typography.labelSmall, color = MediumGray)
                }
                HorizontalDivider(color = LightGray)

                // 3) 서버 백업 켜기 / 끄기
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("서버에 백업하기", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = CharcoalBlack)
                        Text("끄면 서버에 저장된 내 기록을 모두 지우고 이후로 보내지 않아요 (폰 기록은 그대로)",
                            style = MaterialTheme.typography.labelSmall, color = MediumGray)
                    }
                    Switch(
                        checked = enabled, enabled = !busy,
                        onCheckedChange = { on ->
                            if (on) {
                                DbSync.enable(context.applicationContext)
                                enabled = true
                                message = "서버 백업을 켰어요. 폰의 기록을 다시 올릴게요."
                            } else confirmDisable = true
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Camel)
                    )
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brown) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("닫기", color = Camel) } }
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("기록을 가져올까요?") },
            text = { Text("이 폰의 옷장 · 착용 기록 · 취향 · 관심 상품 · 저장한 코디 · 코디 일정이 백업 코드의 기록으로 바뀌어요. 가져온 뒤 앱이 다시 시작돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    busy = true
                    message = null
                    scope.launch {
                        val result = runCatching { BackupRestore.restore(context.applicationContext, input) }
                            .getOrElse { BackupRestore.Result.Offline }
                        busy = false
                        when (result) {
                            is BackupRestore.Result.Done -> {
                                Toast.makeText(context, "옷 ${result.clothes}벌(사진 ${result.photos}장) · 착용 기록 ${result.wearLogs}일을 가져왔어요. 앱을 다시 시작할게요.",
                                    Toast.LENGTH_LONG).show()
                                BackupRestore.restartApp(context.applicationContext)
                            }
                            BackupRestore.Result.InvalidCode -> message = "백업 코드 형식이 맞지 않아요."
                            BackupRestore.Result.SameCode -> message = "이 폰의 백업 코드예요. 다른 폰의 코드를 넣어 주세요."
                            BackupRestore.Result.NotFound -> message = "이 코드로 저장된 기록이 없어요. 코드를 다시 확인해 주세요."
                            BackupRestore.Result.Offline -> message = "서버(DB)에 연결되지 않아요. 서버를 켠 뒤 다시 시도해 주세요."
                        }
                    }
                }) { Text("가져오기", color = Camel) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("취소", color = MediumGray) } }
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("서버 백업을 끌까요?") },
            text = { Text("서버 DB 에 저장된 내 옷장 · 사진 백업 · 착용 · 체형 숫자 · 취향 · 관심 상품 · 추천 기록 · 저장한 코디 · 코디 일정을 모두 지워요. 폰 안의 기록은 그대로예요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    busy = true
                    scope.launch {
                        val ok = DbSync.disableAndDeleteServerData()
                        busy = false
                        if (ok) { enabled = false; message = "서버의 내 기록을 지우고 백업을 껐어요." }
                        else message = "서버에 연결되지 않아 지우지 못했어요. 서버를 켠 뒤 다시 시도해 주세요."
                    }
                }) { Text("지우고 끄기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("취소", color = MediumGray) } }
        )
    }
}
