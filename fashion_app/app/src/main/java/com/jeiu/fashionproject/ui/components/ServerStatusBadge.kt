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
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.data.DataAuth
import com.jeiu.fashionproject.data.ServerConnection
import com.jeiu.fashionproject.data.ConnectionState
import com.jeiu.fashionproject.ui.theme.*

private val Online = Color(0xFF4CAF50)

/**
 * 서버 연결 상태 표시 — "● 서버 연결됨 · DB 저장 중" / "● 오프라인 · 폰에만 저장".
 * 앱 시작 시 연결을 확인하고 네트워크 복구/주기 재시도로 상태를 갱신한다.
 * 시연 중에 DB에 저장되고 있는지 한눈에 보기 위한 표시.
 */
@Composable
fun ServerStatusBadge(modifier: Modifier = Modifier) {
    val pending by DbSync.pendingCount.collectAsState()
    val authState by DataAuth.state.collectAsState()
    val connection by ServerConnection.state.collectAsState()
    val state = when {
        !DbSync.isEnabled -> ConnectionState.BACKUP_DISABLED
        authState == "restore_pending" -> ConnectionState.RESTORE_PENDING
        authState == "server_mismatch" -> ConnectionState.SERVER_CHANGED
        authState == "secure_storage_unavailable" -> ConnectionState.STORAGE_UNAVAILABLE
        authState == "legacy_decision_required" -> ConnectionState.LEGACY_REQUIRED
        authState == "reauth_required" -> ConnectionState.AUTH_REQUIRED
        else -> connection
    }
    val online = state == ConnectionState.READY
    val dot = if (online) Online else MediumGray
    val label = when (state) {
        ConnectionState.CHECKING -> "서버 연결 확인 중 · 폰에 저장"
        ConnectionState.READY -> "서버 연결됨 · 동기화 대기 ${pending}건"
        ConnectionState.BACKUP_DISABLED -> "서버 백업 꺼짐 · 폰에 저장"
        ConnectionState.OFFLINE -> "오프라인 · 폰에 저장 · 재연결 대기"
        ConnectionState.DATABASE_UNAVAILABLE, ConnectionState.UPDATE_REQUIRED -> "서버 점검 중 · 폰에 저장"
        ConnectionState.LEGACY_REQUIRED -> "기존 기록 이전 확인 필요 · 폰에 저장"
        ConnectionState.RESTORE_PENDING -> "복원 이어하기 필요 · 전송 중지"
        ConnectionState.STORAGE_UNAVAILABLE -> "보안 저장소 확인 필요 · 전송 중지"
        ConnectionState.SERVER_CHANGED -> "서버 변경 · 백업 인증 필요"
        ConnectionState.AUTH_REQUIRED -> "백업 인증 대기 · 폰에 저장"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (online) Online.copy(alpha = 0.10f) else CreamDark)
            .clickable { ServerConnection.retry(); DbSync.retry() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (online) Color(0xFF2E7D32) else MediumGray)
    }
}
