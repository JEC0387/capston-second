package com.jeiu.fashionproject.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.TastePreferenceStore
import com.jeiu.fashionproject.ui.components.TasteReportCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.PrivacyConsent
import com.jeiu.fashionproject.data.ProfileStore
import com.jeiu.fashionproject.notify.WeatherAlertScheduler
import com.jeiu.fashionproject.ui.components.PrivacyPolicyDialog
import com.jeiu.fashionproject.ui.components.ServerStatusBadge
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MyPageScreen(
    bodyTypeKo: String? = null,
    gender: Gender? = null,
    onGenderSelected: (Gender) -> Unit = {},
    onStyleAnalysis: () -> Unit,
    onStyleSuggestion: () -> Unit,
    onBodyAnalysis: () -> Unit,
    onStylePreference: () -> Unit = {},
    onVirtualFitting: () -> Unit = {},
    onBodyHistory: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNotifDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var saveRecords by remember { mutableStateOf(PrivacyConsent.canSaveRecords(context)) }
    var weatherAlertOn by remember { mutableStateOf(WeatherAlertScheduler.isEnabled(context)) }

    // 알림 권한 (안드로이드 13 이상은 허용을 받아야 알림을 보낼 수 있음)
    var pendingNotifAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingNotifAction
        pendingNotifAction = null
        if (granted) action?.invoke()
        else Toast.makeText(context, "알림 권한이 없어서 알림을 보낼 수 없어요. 휴대폰 설정 > 앱 > 알림에서 허용해 주세요.", Toast.LENGTH_LONG).show()
    }
    fun withNotifPermission(action: () -> Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pendingNotifAction = action
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, "휴대폰 설정에서 이 앱의 알림이 꺼져 있어요. 설정 > 앱 > 알림에서 켜 주세요.", Toast.LENGTH_LONG).show()
        } else action()
    }
    val styleScores by StylePreferenceStore.scores.collectAsState()
    val styleRatedCount by StylePreferenceStore.ratedCount.collectAsState()
    val taste by TastePreferenceStore.scores.collectAsState()

    // 서버 프로필 (없으면 기본값 폴백)
    var profile by remember { mutableStateOf<com.jeiu.fashionproject.data.ServerProfile?>(null) }
    LaunchedEffect(Unit) {
        StylePreferenceStore.load(context)
        TastePreferenceStore.load(context)
        profile = com.jeiu.fashionproject.data.BackendApi.getProfile()
    }
    val nickname = profile?.nickname ?: "나의 스타일"
    val email = profile?.email ?: "style@fashion.ai"
    val wardrobeCount = profile?.wardrobeCount?.toString() ?: "12"
    val outfitCount = profile?.outfitCount?.toString() ?: "8"
    val favoriteCount = profile?.favoriteCount?.toString() ?: "24"
    var showGenderDialog by remember { mutableStateOf(false) }
    val shownBodyType = bodyTypeKo ?: profile?.bodyType

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
                ServerStatusBadge()
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

            // 성별 배지 — 상품/코디 추천 필터링에 쓰인다
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGenderDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Wc, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                    Text("성별", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (gender != null) CamelLight else CreamDark)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            gender?.label ?: "선택 안함",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (gender != null) Brown else MediumGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(18.dp))
                }
            }

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
                        .background(if (shownBodyType != null) CamelLight else CreamDark)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // 이번 실행에서 분석한 결과가 있으면 그것, 없으면 서버에 저장된 최근 결과 (둘 다 없으면 안내)
                    Text(
                        shownBodyType ?: "분석 전",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (shownBodyType != null) Brown else MediumGray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text("내 취향 리포트", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack)
            TasteReportCard(styleScores, styleRatedCount, taste)

            // 취향 태그
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                    Text("선호 스타일", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                }
                // 폰에 저장된 선택 우선, 없으면 서버 프로필
                val styles = if (ProfileStore.hasSelection(context)) ProfileStore.preferredStyles(context) else profile?.preferredStyles.orEmpty()
                if (styles.isEmpty()) {
                    Text("아직 고르지 않았어요 (첫 화면에서 선택)", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
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
            MyMenuItem(icon = Icons.Filled.ShowChart, label = "체형 측정 기록 · 변화 그래프", onClick = onBodyHistory)
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
            MyMenuItem(
                icon = Icons.Filled.Notifications,
                label = if (weatherAlertOn) "알림 설정 (내일 날씨 알림 켜짐)" else "알림 설정",
                onClick = { showNotifDialog = true }
            )
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Lock, label = "개인정보 처리 안내", onClick = { showPrivacyDialog = true })
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.DeleteOutline, label = "현재 세션 체형 데이터 삭제", onClick = {
                BodyAnalysisSessionStore.clear()
                Toast.makeText(context, "현재 세션의 측정값을 삭제했어요", Toast.LENGTH_SHORT).show()
            })
            HorizontalDivider(color = LightGray)
            // 체형 분석 결과 숫자를 서버에 기록으로 남길지 (동의 화면에서 고른 값, 여기서 바꿀 수 있음)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                    Column {
                        Text("체형 측정 기록 저장", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                        Text(
                            if (PrivacyConsent.hasAgreed(context)) "분석 결과 숫자만 서버에 저장 (사진 X)"
                            else "체형 분석을 처음 할 때 동의 후 켤 수 있어요",
                            style = MaterialTheme.typography.labelSmall, color = MediumGray
                        )
                    }
                }
                Switch(
                    checked = saveRecords,
                    enabled = PrivacyConsent.hasAgreed(context),
                    onCheckedChange = {
                        PrivacyConsent.setSaveRecords(context, it)
                        saveRecords = PrivacyConsent.canSaveRecords(context)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Camel)
                )
            }
            HorizontalDivider(color = LightGray)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !deleting) { showDeleteDialog = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    if (deleting) "측정 기록 삭제 중…" else "측정 기록 삭제",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.Info, label = "앱 버전 1.0.0", onClick = { showVersionDialog = true })
            HorizontalDivider(color = LightGray)
            MyMenuItem(icon = Icons.Filled.ExitToApp, label = "로그아웃", onClick = { showLogoutDialog = true })
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showGenderDialog) {
        AlertDialog(
            onDismissRequest = { showGenderDialog = false },
            title = { Text("성별 선택", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "선택한 성별에 맞춰 추천 상품과 코디가 달라져요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Gender.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onGenderSelected(option)
                                    showGenderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = gender == option,
                                onClick = {
                                    onGenderSelected(option)
                                    showGenderDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Camel)
                            )
                            Text(option.label, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGenderDialog = false }) { Text("닫기", color = Camel) }
            }
        )
    }

    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text("알림 설정", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("내일 날씨 알림", style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            Text(
                                "매일 저녁 8시쯤 내일 날씨 변화와 추천 코디를 알려줘요",
                                style = MaterialTheme.typography.labelSmall, color = MediumGray
                            )
                        }
                        Switch(
                            checked = weatherAlertOn,
                            onCheckedChange = { on ->
                                if (on) withNotifPermission {
                                    WeatherAlertScheduler.setEnabled(context, true)
                                    weatherAlertOn = true
                                    Toast.makeText(context, "매일 저녁 8시쯤 내일 날씨를 알려드릴게요", Toast.LENGTH_SHORT).show()
                                } else {
                                    WeatherAlertScheduler.setEnabled(context, false)
                                    weatherAlertOn = false
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Camel)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            withNotifPermission {
                                WeatherAlertScheduler.sendTestNow(context)
                                Toast.makeText(context, "잠시 후 테스트 알림이 와요 (예보를 받는 데 몇 초 걸려요)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel)
                    ) { Text("지금 테스트 알림 보내기") }
                    Text(
                        "예보를 받으려면 인터넷과 날씨 API 키가 필요해요. 위치는 앱에서 마지막으로 받은 위치를 써요.",
                        style = MaterialTheme.typography.labelSmall, color = MediumGray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) { Text("닫기", color = Camel) }
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

    if (showPrivacyDialog) {
        val agreedAt = PrivacyConsent.agreedAt(context)
        PrivacyPolicyDialog(
            agreedAtText = if (agreedAt > 0L)
                java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA).format(java.util.Date(agreedAt))
            else null,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("측정 기록 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text("서버에 저장된 체형 측정 기록(체형 · 비율 · cm)을 모두 지울까요?\n지운 기록은 되돌릴 수 없어요. 옷장 · 착용 기록은 지워지지 않아요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleting = true
                    scope.launch {
                        val ok = BodyRecords.deleteAll()
                        deleting = false
                        if (ok) profile = profile?.copy(bodyType = null)
                        Toast.makeText(
                            context,
                            if (ok) "측정 기록을 모두 삭제했어요" else "서버에 연결되지 않아 삭제하지 못했어요. 서버를 켠 뒤 다시 시도해 주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소", color = MediumGray) }
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
