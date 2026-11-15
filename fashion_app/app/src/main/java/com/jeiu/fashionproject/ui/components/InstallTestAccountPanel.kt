package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jeiu.fashionproject.data.BackendApi

/** User-confirmed fresh registration only; no field ever accepts a token or another account. */
@Composable
internal fun InstallTestAccountPanel(state: String, hasRecord: Boolean, busy: Boolean,
    onEnroll: (Boolean, String, String) -> Unit, onVerify: () -> Unit) {
    val base = BackendApi.BASE_URL
    var expanded by remember { mutableStateOf(false) }
    var approved by remember(base) { mutableStateOf(false) }
    var reference by remember(base) { mutableStateOf("") }
    var confirm by remember(base) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("testAccountExpand")) {
        Text(if (expanded) "시험 계정 접기" else "관리자 승인 시험 계정 연결")
    }
    if (expanded) {
        Text("연결 서버: $base")
        Text("기존 로그인·옷장 백업과 분리된 빈 시험 계정이에요. 관리자 승인 후에만 만들 수 있어요. 사진과 기존 기록은 보내지 않아요.")
        Text(when(state) {
            "ready" -> "시험 계정 인증 확인됨. AI 설정을 다시 확인해주세요."
            "connecting", "checking" -> "확인 중… 최대 25초씩 기다려요."
            "verification_required" -> "저장된 시험 계정의 인증을 다시 확인해주세요."
            "registration_unavailable" -> "서버 등록이 허용되지 않았거나 준비되지 않았어요. 관리자에게 확인해주세요."
            "outcome_unknown" -> "계정 생성 결과를 확정할 수 없어요. 재등록하지 말고 관리자에게 승인 번호로 확인해주세요."
            "server_mismatch" -> "계정을 발급한 서버와 현재 주소가 달라요. 원래 서버 주소를 확인해주세요."
            "storage_unavailable" -> "안전한 인증정보 저장을 확인하지 못했어요. 기존 데이터는 유지돼요."
            "authentication_required", "verification_failed" -> "시험 계정 인증을 확인하지 못했어요. 관리자에게 확인하거나 연결을 다시 확인해주세요."
            else -> "관리자 승인과 서버의 등록 허용이 필요해요."
        }, modifier = Modifier.testTag("testAccountStatus"))
        if (!hasRecord) {
            OutlinedTextField(reference, { reference = it.take(80) }, label = { Text("관리자 승인 번호 (비밀정보 제외)") },
                enabled = !busy, singleLine = true, modifier = Modifier.testTag("testAccountReference"))
            Row {
                Checkbox(approved, { approved = it }, enabled = !busy, modifier = Modifier.testTag("testAccountApproval"))
                Text("관리자가 이 서버의 독립 시험 계정 생성을 승인했어요.")
            }
            OutlinedButton(onClick = { confirm = true }, enabled = !busy && approved && Regex("[A-Za-z0-9._-]{3,80}").matches(reference),
                modifier = Modifier.testTag("testAccountCreate")) { Text("새 시험 계정 만들기") }
        } else {
            OutlinedButton(onClick = onVerify, enabled = !busy && state !in setOf("outcome_unknown", "registration_unavailable", "storage_unavailable"),
                modifier = Modifier.testTag("testAccountVerify")) { Text("시험 계정 인증 확인") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("새 시험 계정을 만들까요?") },
        text = { Text("$base\n승인 번호: $reference\n서버에 빈 계정 1개를 만들어요. Genlook 생성·결제·기존 기록 전송은 하지 않아요. 응답이 끊기면 자동 재등록하지 않아요.") },
        confirmButton = { TextButton(onClick = { confirm = false; onEnroll(approved, reference, base) },
            modifier = Modifier.testTag("testAccountConfirm")) { Text("승인된 계정 생성") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
}
