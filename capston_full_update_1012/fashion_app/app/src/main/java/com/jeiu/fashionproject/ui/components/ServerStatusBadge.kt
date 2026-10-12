package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BackendApi
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.delay

private val Online = Color(0xFF4CAF50)

/**
 * 서버 연결 상태 표시 — "● 서버 연결됨 · DB 저장 중" / "● 오프라인 · 폰에만 저장".
 * 화면이 보이는 동안 15초마다 서버(GET /)를 확인한다. 누르면 바로 다시 확인.
 * 시연 중에 DB에 저장되고 있는지 한눈에 보기 위한 표시.
 */
@Composable
fun ServerStatusBadge(modifier: Modifier = Modifier) {
    // null = 확인 중
    var online by remember { mutableStateOf<Boolean?>(null) }
    var checkKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(checkKey) {
        while (true) {
            online = BackendApi.isServerUp()
            delay(15_000)
        }
    }

    val (dot, label) = when (online) {
        null -> MediumGray to "서버 확인 중…"
        true -> Online to "서버 연결됨 · DB 저장 중"
        false -> MediumGray to "오프라인 · 폰에만 저장 (서버 켜면 자동 동기화)"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (online == true) Online.copy(alpha = 0.10f) else CreamDark)
            .clickable { online = null; checkKey++ }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (online == true) Color(0xFF2E7D32) else MediumGray)
    }
}
