package com.jeiu.fashionproject.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.ServerSettings
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 마이페이지 > 서버 주소 — 실제 폰에서 PC 서버에 연결할 때 PC 의 와이파이 IP 를 넣는다.
 * [연결 확인]으로 서버 · DB 가 응답하는지 먼저 보고 [저장]한다.
 */
@Composable
fun ServerAddressDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(ServerSettings.current.removePrefix("http://")) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ServerSettings.Check?>(null) }
    val normalized = ServerSettings.normalize(input)

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text("서버 주소", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("에뮬레이터는 기본 주소(10.0.2.2)를 그대로 쓰면 돼요.\n실제 폰은 PC 의 와이파이 IP 를 넣어 주세요 " +
                    "(PC 에서 ipconfig → IPv4 주소, 예: 192.168.0.10).",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(100); result = null }, singleLine = true,
                    label = { Text("PC 주소 (포트를 안 쓰면 8000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = input.isNotBlank() && normalized == null,
                    supportingText = { Text(normalized ?: "주소 형식이 맞지 않아요 (예: 192.168.0.10 또는 192.168.0.10:8000)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val url = normalized ?: return@OutlinedButton
                            checking = true
                            scope.launch { result = ServerSettings.check(url); checking = false }
                        },
                        enabled = normalized != null && !checking
                    ) { Text(if (checking) "확인 중…" else "연결 확인", color = Brown) }
                    TextButton(onClick = {
                        ServerSettings.reset(context)
                        input = ServerSettings.current.removePrefix("http://")
                        result = null
                        Toast.makeText(context, "기본 주소로 되돌렸어요", Toast.LENGTH_SHORT).show()
                    }, enabled = !checking) { Text("기본값", color = MediumGray) }
                }
                result?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = if (it.reachable && it.dbReady) Camel else HeartPink)
                }
                Text("서버는 PC 에서 python -m uvicorn main:app --host 0.0.0.0 --port 8000 으로 켜고, 폰과 PC 는 같은 와이파이에 있어야 해요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = normalized ?: return@TextButton
                ServerSettings.save(context, url)
                Toast.makeText(context, "서버 주소를 $url 로 바꿨어요", Toast.LENGTH_SHORT).show()
                onDismiss()
            }, enabled = normalized != null && !checking) { Text("저장", color = Camel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("취소", color = MediumGray) } }
    )
}
