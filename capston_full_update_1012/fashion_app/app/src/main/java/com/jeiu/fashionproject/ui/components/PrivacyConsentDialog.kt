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
    "전신 사진은 폰 안에서만 분석하고 서버로 보내지 않아요.",
    "촬영한 사진은 갤러리에 저장하지 않아요. 결과를 보여주는 동안만 앱 임시 폴더에 두고, 다음 분석 때 지워요.",
    "서버에는 체형 · 비율 · 키 · cm 추정값 같은 숫자만 저장해요. (측정 기록 · 변화 그래프용, '측정 기록 저장'을 켰을 때만)",
    "얼굴 인식이나 신원 확인에는 쓰지 않아요.",
    "마이페이지 > 설정에서 언제든 측정 기록을 삭제하거나 저장을 끌 수 있어요.",
)

/**
 * 체형 촬영 · 사진 선택 전에 한 번 띄우는 동의 화면.
 * [동의하고 시작]을 눌러야 촬영이 시작된다. '측정 기록 저장'은 따로 고를 수 있다.
 */
@Composable
fun BodyPhotoConsentDialog(onAgree: (saveRecords: Boolean) -> Unit, onDismiss: () -> Unit) {
    var saveRecords by remember { mutableStateOf(true) }
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
                        Text("숫자 결과를 서버에 남겨 변화 그래프로 보여줘요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
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
                PrivacyPoint("옷 사진은 폰 안 앱 폴더에 보관하고, 서버에는 옷 정보와 사진 주소만 저장해요.")
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
