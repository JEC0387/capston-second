package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

@Composable
fun MyPageScreen(
    onStyleAnalysis: () -> Unit,
    onStyleSuggestion: () -> Unit,
    onBodyAnalysis: () -> Unit,
    onStylePreference: () -> Unit = {},
    onVirtualFitting: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var showNotifDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 서버 프로필 (없으면 기본값 폴백)
    var profile by remember { mutableStateOf<com.jeiu.fashionproject.data.ServerProfile?>(null) }
    LaunchedEffect(Unit) {
        profile = com.jeiu.fashionproject.data.BackendApi.getProfile()
    }
    val nickname = profile?.nickname ?: "나의 스타일"
    val email = profile?.email ?: "style@fashion.ai"
    val wardrobeCount = profile?.wardrobeCount?.toString() ?: "12"
    val outfitCount = profile?.outfitCount?.toString() ?: "8"
    val favoriteCount = profile?.favoriteCount?.toString() ?: "24"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 프로필 헤더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CamelLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Camel, modifier = Modifier.size(44.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(nickname, style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }
            // 통계
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(Triple(wardrobeCount, "옷장", "벌"), Triple(outfitCount, "코디", "개"), Triple(favoriteCount, "즐겨찾기", "개")).forEach { (count, label, unit) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count, style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                        Text("$label $unit", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 내 스타일 정보
        Column(
            modifier = Modifier.background(White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("내 스타일 정보", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)

            // 체형 배지
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                    Text("체형", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CamelLight)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("모래시계형", style = MaterialTheme.typography.labelLarge, color = Brown, fontWeight = FontWeight.SemiBold)
                }
            }

            // 취향 태그
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                    Text("선호 스타일", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("미니멀", "캐주얼", "클래식").forEach { style ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(CreamDark)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(style, style = MaterialTheme.typography.labelLarge, color = Brown)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // AI 분석 메뉴
        Column(
            modifier = Modifier.background(White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("AI 분석", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MyMenuItem(icon = Icons.Filled.AutoAwesome, label = "스타일 분석 결과", onClick = onStyleAnalysis)
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Star, label = "체형 맞춤 스타일 제안", onClick = onStyleSuggestion)
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.CameraAlt, label = "체형 다시 분석하기", onClick = onBodyAnalysis)
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Favorite, label = "스타일 취향 학습", onClick = onStylePreference)
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Checkroom, label = "가상 피팅", onClick = onVirtualFitting)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 설정 메뉴
        Column(
            modifier = Modifier.background(White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("설정", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MyMenuItem(icon = Icons.Filled.Notifications, label = "알림 설정", onClick = { showNotifDialog = true })
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Lock, label = "개인정보 처리방침", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy?hl=ko")))
            })
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Info, label = "앱 버전 1.0.0", onClick = { showVersionDialog = true })
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.ExitToApp, label = "로그아웃", onClick = { showLogoutDialog = true })
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text("알림 설정", fontWeight = FontWeight.Bold) },
            text = { Text("현재 모든 알림이 활성화되어 있습니다.\n시스템 설정에서 알림을 관리할 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) { Text("확인", color = Camel) }
            }
        )
    }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text("앱 정보", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FashionAI")
                    Text("버전: 1.0.0", color = MediumGray)
                    Text("AI 기반 패션 스타일링 앱", color = MediumGray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) { Text("확인", color = Camel) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃", fontWeight = FontWeight.Bold) },
            text = { Text("로그아웃 하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("취소", color = MediumGray) }
            }
        )
    }
}

@Composable
private fun MyMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
    }
}
