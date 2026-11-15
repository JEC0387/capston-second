package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.components.AvatarAnalysisForm
import com.jeiu.fashionproject.ui.components.AvatarBodyResultCard
import com.jeiu.fashionproject.data.AvatarProfileStore
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.data.BodyMeasurementScope
import com.jeiu.fashionproject.data.DataAuth
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.ui.theme.*

/** Start with a clear user choice rather than showing photo and avatar controls together. */
@Composable
fun BodyMethodSelectionScreen(
    onBack: () -> Unit,
    onPhotoAnalysis: () -> Unit,
    onAvatarSetup: () -> Unit,
    onSizeComparison: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val manual by ManualBodyStore.current.collectAsState()
    val photoType by com.jeiu.fashionproject.data.BodyTypeStore.current.collectAsState()
    LaunchedEffect(Unit) { ManualBodyStore.load(context); com.jeiu.fashionproject.data.BodyTypeStore.load(context) }
    val fused = com.jeiu.fashionproject.recommend.BodyShapeFusion.fuse(photoType?.type, manual)
    FashionFeatureTheme {
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("나의 체형 분석", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = CharcoalBlack)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("체형을 어떻게 확인할까요?", style = MaterialTheme.typography.titleLarge,
            color = CharcoalBlack)
        Text("사진으로 분석하거나 내 치수로 3D 체형을 만들 수 있어요.",
            style = MaterialTheme.typography.bodyMedium, color = FashionMuted)
        AccuracyTipCard(fused)
        BodyMethodCard(
            title = "전신 사진으로 분석",
            description = "정면 전신 사진에서 체형과 신체 비율을 확인해요.",
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrownDark) },
            onClick = onPhotoAnalysis,
            tag = "bodyMethodPhoto"
        )
        BodyMethodCard(
            title = "내 3D 체형 만들기",
            description = "내 치수로 아바타를 만들고 체형을 확인해요.",
            icon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = BrownDark) },
            onClick = onAvatarSetup,
            tag = "bodyMethodAvatar"
        )
        Text("더 살펴보기", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack,
            fontWeight = FontWeight.SemiBold)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = White)) {
            Column {
                BodyMenuRow("의류 사이즈 비교", "실측표와 내 치수를 비교해요", onSizeComparison,
                    "bodyMethodSize", { Icon(Icons.Filled.Straighten, contentDescription = null, tint = BrownDark) })
                HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
                BodyMenuRow("지난 분석 기록", "저장된 측정과 변화 보기", onHistory,
                    "bodyMethodHistory", { Icon(Icons.Filled.History, contentDescription = null, tint = BrownDark) })
            }
        }
        }
    }
}
}

/** 치수 직접 입력 + 사진 분석을 함께 하면 더 정확하다는 안내, 현재 반영 중인 체형 */
@Composable
private fun AccuracyTipCard(fused: com.jeiu.fashionproject.recommend.BodyShapeFusion.Result?) {
    Card(Modifier.fillMaxWidth().testTag("bodyAccuracyTip"), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = BrownDark)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("치수 입력 + 사진 분석을 함께 하면 더 정확해요",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = CharcoalBlack)
                Text("직접 잰 키 · 둘레로 사진 추정값을 보정하고, 두 결과를 비교해 체형을 정해요.",
                    style = MaterialTheme.typography.bodySmall, color = FashionMuted)
                if (fused != null) Text("지금 추천에 반영 중: ${fused.type} (${fused.source.label})",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = BrownDark)
            }
        }
    }
}

@Composable
private fun BodyMenuRow(title: String, description: String, onClick: () -> Unit,
    tag: String, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack)
            Text(description, style = MaterialTheme.typography.bodySmall, color = FashionMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray)
    }
}

@Composable
private fun BodyMethodCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick),
        shape = FashionCardShape,
        colors = CardDefaults.cardColors(containerColor = CreamDark)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = CharcoalBlack)
                Text(description, style = MaterialTheme.typography.bodySmall, color = FashionMuted)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray)
        }
    }
}

/** One form updates the saved avatar and opens its measured analysis. */
@Composable
fun BodyAvatarSetupScreen(onBack: () -> Unit, onPhotoAnalysis: () -> Unit) {
    val context = LocalContext.current
    val saved by AvatarProfileStore.current.collectAsState()
    val manual by ManualBodyStore.current.collectAsState()
    val mode by AuthStore.mode.collectAsState()
    val account by AuthStore.account.collectAsState()
    val connection by DataAuth.state.collectAsState()
    val ownershipRevision by BodyMeasurementScope.revision.collectAsState()
    val owner = remember(mode, account, connection, ownershipRevision) { BodyMeasurementScope.current() }
    var loaded by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(owner) {
        AvatarProfileStore.load(context)
        ManualBodyStore.load(context)
        loaded = true
    }
    var showAnalysis by rememberSaveable(owner) { mutableStateOf(false) }
    val showResult = owner != null && loaded && showAnalysis && saved != null
    FashionFeatureTheme {
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("내 3D 체형", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = CharcoalBlack)
        }
        key(owner, showResult) {
            if (loaded && owner != null && !showResult) {
                Box(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    AvatarAnalysisForm(saved, manual, owner, onAnalyzed = { showAnalysis = true },
                        onPhotoAnalysis = onPhotoAnalysis)
                }
            } else {
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        showResult -> AvatarBodyResultCard(checkNotNull(saved), onEdit = { showAnalysis = false })
                        !loaded -> Unit
                        else -> Text("계정 연결을 확인한 뒤 3D 치수를 열어주세요.")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
}
