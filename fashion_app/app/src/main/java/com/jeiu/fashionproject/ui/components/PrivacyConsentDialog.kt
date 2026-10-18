package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

/** 체형 사진 개인정보 안내 항목 — 동의 화면과 마이페이지 "개인정보 처리 안내"에서 같이 쓴다 */
val BODY_PRIVACY_POINTS = listOf(
    "체형분석은 사진을 기기 안에서 처리해요. 별도 기능인 가상피팅은 전송 직전 확인을 받은 뒤 FASHN 외부 서비스로 사진을 보내요.",
    "촬영 사진은 앱 임시 폴더에 두고 결과 화면에서 사용해요. 다시 분석하거나 화면이 정리될 때 삭제해요. 갤러리에서 고른 원본은 변경하지 않아요.",
    "저장을 선택하면 전송 대기 숫자 기록을 폰에 보관하고 연결 복구 시 재시도해요.",
    "체형 기록에는 원본 사진을 저장하지 않아요. 저장을 선택한 경우에만 체형 · 비율 · 키 · cm 추정값을 서버에 기록해요.",
    "저장하지 않아도 이번 분석 결과로 사이즈와 코디 추천을 사용할 수 있어요.",
    "얼굴 인식이나 신원 확인에는 쓰지 않아요.",
    "마이페이지 > 설정에서 현재 세션의 측정값을 지우거나 저장을 끌 수 있고, 서버 기록 전체를 삭제할 수 있어요.",
)

/**
 * 체형 촬영 · 사진 선택 전에 한 번 띄우는 동의 화면.
 * [동의하고 시작]을 눌러야 촬영이 시작된다. '측정 기록 저장'은 따로 고를 수 있다.
 */
@Composable
fun BodyPhotoConsentDialog(onAgree: (saveRecords: Boolean) -> Unit, onDismiss: () -> Unit) {
    var saveRecords by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Camel) },
        title = { Text("체형 사진 이용 안내", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BODY_PRIVACY_POINTS.forEach { PrivacyPoint(it) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { saveRecords = !saveRecords },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveRecords,
                        onCheckedChange = { saveRecords = it },
                        colors = CheckboxDefaults.colors(checkedColor = Camel)
                    )
                    Column {
                        Text("측정 기록 저장 (선택)", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                        Text("선택하면 숫자 결과를 서버에 남겨 다음 추천과 변화 그래프에 사용해요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAgree(saveRecords) }) {
                Text("동의하고 시작", color = Camel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = MediumGray) }
        }
    )
}

/** 마이페이지 > 개인정보 처리 안내 */
@Composable
fun PrivacyPolicyDialog(agreedAtText: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개인정보 처리 안내", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BODY_PRIVACY_POINTS.forEach { PrivacyPoint(it) }
                PrivacyPoint("옷 사진은 폰 안 앱 폴더에 보관하고, AI 옷 분류 시 옷 사진을 서버로 전송하며, DB 동기화에는 옷 정보와 사진 주소를 저장해요.")
                PrivacyPoint("로그인 대신 앱이 처음 실행될 때 만든 기기 번호로 내 데이터를 구분해요.")
                Text(
                    agreedAtText?.let { "체형 사진 이용 동의: $it" } ?: "체형 사진 이용: 아직 동의하지 않음",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인", color = Camel) }
        }
    )
}

@Composable
private fun PrivacyPoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodySmall, color = Camel)
        Text(text, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
    }
}
