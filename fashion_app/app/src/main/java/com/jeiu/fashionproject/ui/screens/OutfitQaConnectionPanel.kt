package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import com.jeiu.fashionproject.data.ServerOutfitQaConnection

/** QA-only manual configuration; configuration itself never connects or sends photos. */
@Composable
internal fun OutfitQaConnectionPanel(enabled: Boolean, onChanged: () -> Unit) {
    if (!ServerOutfitQaConnection.supported) return
    var expanded by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var acknowledged by remember(base, token) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { expanded = !expanded }, enabled = enabled,
        modifier = Modifier.testTag("outfitQaConnectionExpand")) { Text("QA 전용 서버 연결") }
    if (expanded) {
        Text("승인된 격리 HTTPS 서버와 그 서버가 발급한 사용자 토큰만 입력하세요. Genlook API 키나 Preview 계정은 사용하지 않아요. 연결 설정만으로 사진을 전송하지 않아요.")
        OutlinedTextField(base, { base = it }, enabled = enabled, singleLine = true,
            label = { Text("승인된 QA 서버 HTTPS 주소") }, modifier = Modifier.fillMaxWidth().testTag("outfitQaBase"))
        OutlinedTextField(token, { token = it }, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(), label = { Text("QA 서버 사용자 토큰") },
            modifier = Modifier.fillMaxWidth().testTag("outfitQaToken"))
        Row {
            Checkbox(acknowledged, { acknowledged = it }, enabled = enabled,
                modifier = Modifier.testTag("outfitQaApproved"))
            Text("이 주소가 사용 승인을 받은 격리 서버임을 확인했어요. 실제 생성에는 별도 크레딧·시험 입력 승인과 아래 전송 동의가 필요해요.")
        }
        Button(enabled = enabled && acknowledged, onClick = {
            if (ServerOutfitQaConnection.configure(base, token, acknowledged)) {
                token = ""; acknowledged = false
                message = "QA 연결을 메모리에 설정했어요. 서버 준비 상태를 확인해주세요. 앱 종료 후 같은 주소와 토큰을 다시 입력해야 이전 작업을 조회할 수 있어요."
                onChanged()
            } else message = "HTTPS 격리 주소와 QA 서버 사용자 토큰을 확인해주세요. 운영 주소·공급자 API 키는 사용할 수 없어요."
        }, modifier = Modifier.testTag("outfitQaConfigure")) { Text("QA 연결 설정") }
        TextButton(enabled = enabled, onClick = {
            ServerOutfitQaConnection.clear(); token = ""; acknowledged = false
            message = "메모리의 QA 연결만 해제했어요. 기존 작업 기록과 파일은 유지했어요."
            onChanged()
        }, modifier = Modifier.testTag("outfitQaDisconnect")) { Text("QA 연결 해제") }
        message?.let { Text(it, modifier = Modifier.testTag("outfitQaConnectionMessage")) }
    }
}
