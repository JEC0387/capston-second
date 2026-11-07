package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.ui.components.ManualBodyEntryCard
import com.jeiu.fashionproject.ui.theme.*

/** A direct route to the existing source-aware measured-size flow. */
@Composable
fun BodySizeComparisonScreen(onBack: () -> Unit, onPhotoAnalysis: () -> Unit) {
    val photo by BodyAnalysisSessionStore.measurements.collectAsState()
    val manual by ManualBodyStore.current.collectAsState()
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("의류 사이즈 비교", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("내 치수와 의류 실측표", style = MaterialTheme.typography.titleMedium,
                        color = CharcoalBlack)
                    Text(if (manual != null || photo != null)
                        "아래의 의류 실측 비교에서 상품표와 내 치수의 출처를 함께 확인하세요."
                        else "비교에 사용할 치수가 없어요. 사진 분석을 하거나 직접 잰 치수를 입력하세요.",
                        style = MaterialTheme.typography.bodySmall, color = MediumGray,
                        modifier = Modifier.testTag("sizeComparisonGuidance"))
                    Text("사진의 정면 너비는 신체 둘레가 아니므로 둘레 실측표에 대신 넣지 않아요.",
                        style = MaterialTheme.typography.bodySmall, color = Brown)
                    OutlinedButton(onClick = onPhotoAnalysis, modifier = Modifier.testTag("sizeComparePhoto")) {
                        Text("사진으로 체형 분석하기")
                    }
                }
            }
            ManualBodyEntryCard(photo = photo, onUseHeight = {})
            Spacer(Modifier.height(16.dp))
        }
    }
}
