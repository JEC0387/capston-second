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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onAvatarSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("나의 체형 분석", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = CharcoalBlack)
        }
        Text("어떤 방법으로 체형을 확인할까요?", style = MaterialTheme.typography.titleMedium,
            color = CharcoalBlack)
        Text("사진 분석 또는 3D 체형 설정 중 원하는 방법을 선택하세요.",
            style = MaterialTheme.typography.bodyMedium, color = MediumGray)
        BodyMethodCard(
            title = "사진으로 체형 분석",
            description = "지금 촬영하거나 갤러리의 전신 사진을 선택해 신체 비율을 분석해요.",
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Camel) },
            onClick = onPhotoAnalysis,
            tag = "bodyMethodPhoto"
        )
        BodyMethodCard(
            title = "3D 아바타로 체형 설정",
            description = "기본 3D 모델을 확인하고, 신체 치수를 직접 입력해 비율을 조절해요.",
            icon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = Camel) },
            onClick = onAvatarSetup,
            tag = "bodyMethodAvatar"
        )
        Text("입력한 체형은 나중에 가상피팅에서도 활용할 수 있도록 연결할 예정이에요.",
            style = MaterialTheme.typography.bodySmall, color = MediumGray)
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
fun BodyAvatarSetupScreen(onBack: () -> Unit, onPhotoAnalysis: () -> Unit) {
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
            Text("3D 기본 모델을 확인하고, 알고 있는 신체 치수를 입력해 보세요.",
                style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
            AvatarSetupCard()
            ManualBodyEntryCard(photo = null, onUseHeight = {})
            OutlinedButton(onClick = onPhotoAnalysis, modifier = Modifier.fillMaxWidth()) {
                Text("사진으로 체형 분석하기")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
