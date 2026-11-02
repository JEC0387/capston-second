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
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.components.ManualBodyEntryCard
import com.jeiu.fashionproject.ui.components.AvatarSetupCard
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
        Text("사진으로 분석하거나 내 3D 체형을 직접 만들 수 있어요.",
            style = MaterialTheme.typography.bodyMedium, color = MediumGray)
        BodyMethodCard(
            title = "사진으로 체형 분석",
            description = "지금 촬영하거나 갤러리의 전신 사진을 선택해 신체 비율을 분석해요.",
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Camel) },
            onClick = onPhotoAnalysis,
            tag = "bodyMethodPhoto"
        )
        BodyMethodCard(
            title = "3D 내 체형 만들기",
            description = "키·몸무게, 사진 결과 또는 직접 입력한 치수로 모델을 조절해요.",
            icon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = Camel) },
            onClick = onAvatarSetup,
            tag = "bodyMethodAvatar"
        )
        Text("더 살펴보기", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack,
            fontWeight = FontWeight.SemiBold)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = White)) {
            Column {
                BodyMenuRow("의류 사이즈 비교", "실측표와 내 치수를 비교해요", onSizeComparison,
                    "bodyMethodSize", { Icon(Icons.Filled.Straighten, contentDescription = null, tint = Camel) })
                HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
                BodyMenuRow("지난 분석 기록", "저장된 측정과 변화 보기", onHistory,
                    "bodyMethodHistory", { Icon(Icons.Filled.History, contentDescription = null, tint = Camel) })
            }
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
            Text(description, style = MaterialTheme.typography.bodySmall, color = MediumGray)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = White)
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
                Text(description, style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MediumGray)
        }
    }
}

/** Phase-one avatar entry reuses existing manual measurements and 3D preview. */
@Composable
fun BodyAvatarSetupScreen(onBack: () -> Unit, onPhotoAnalysis: () -> Unit,onFitting:(()->Unit)?=null) {
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("3D 아바타 체형 설정", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = CharcoalBlack)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("3D 모델을 확인하고 체형 생성 방법을 선택해 보세요.",
                style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
            AvatarSetupCard(onApplyToFitting=onFitting)
            OutlinedButton(onClick = { manualExpanded = !manualExpanded },
                modifier = Modifier.fillMaxWidth().testTag("avatarManualExpand")) {
                Text(if (manualExpanded) "직접 입력한 치수 접기" else "신체 치수 직접 입력")
            }
            if (manualExpanded) ManualBodyEntryCard(photo = null, onUseHeight = {})
            OutlinedButton(onClick = onPhotoAnalysis, modifier = Modifier.fillMaxWidth()) {
                Text("사진으로 체형 분석하기")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
