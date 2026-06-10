package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

private data class BodyProportion(val label: String, val ratio: Float)

private val proportions = listOf(
    BodyProportion("어깨 너비", 0.82f),
    BodyProportion("허리 라인", 0.65f),
    BodyProportion("힙 너비", 0.80f),
    BodyProportion("다리 길이", 0.75f),
)

@Composable
fun BodyAnalysisScreen(onBack: () -> Unit, onStyleSuggestion: () -> Unit) {
    var analyzed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("AI 체형 분석", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!analyzed) {
                // 사진 업로드 영역
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, LightGray, RoundedCornerShape(20.dp))
                        .background(White)
                        .clickable { analyzed = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(CamelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Camel, modifier = Modifier.size(32.dp))
                        }
                        Text("전신 사진을 업로드하세요", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                        Text("정면에서 찍은 전신 사진이 가장 정확해요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }
                }

                // 촬영 팁
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CamelLight),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("촬영 팁", style = MaterialTheme.typography.titleMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                        listOf("정면을 향해 서주세요", "몸 전체가 나오도록 찍어주세요", "밝은 곳에서 촬영해주세요", "몸에 붙는 옷을 입으면 더 정확해요").forEach {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(Camel))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Brown)
                            }
                        }
                    }
                }

                Button(
                    onClick = { analyzed = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("사진 촬영 / 업로드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

            } else {
                // 분석 결과
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("분석 완료!", style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.Medium)
                                Text("모래시계형", style = MaterialTheme.typography.headlineMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Camel, modifier = Modifier.size(40.dp))
                        }

                        HorizontalDivider(color = LightGray)

                        // 체형 시각화 (간단한 실루엣)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CreamDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(Camel.copy(alpha = 0.5f)))
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(modifier = Modifier.size(width = 60.dp, height = 20.dp).clip(RoundedCornerShape(4.dp)).background(Camel.copy(alpha = 0.7f)))
                                Box(modifier = Modifier.size(width = 36.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).background(Camel.copy(alpha = 0.5f)))
                                Box(modifier = Modifier.size(width = 58.dp, height = 18.dp).clip(RoundedCornerShape(4.dp)).background(Camel.copy(alpha = 0.7f)))
                                Box(modifier = Modifier.size(width = 46.dp, height = 50.dp).clip(RoundedCornerShape(4.dp)).background(Camel.copy(alpha = 0.4f)))
                            }
                        }

                        // 비율 분석
                        Text("신체 비율 분석", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                        proportions.forEach { prop ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(prop.label, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
                                    Text("${(prop.ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(
                                    progress = { prop.ratio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Camel,
                                    trackColor = CamelLight
                                )
                            }
                        }
                    }
                }

                // 체형 특징 카드
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CamelLight),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("모래시계형 특징", style = MaterialTheme.typography.titleMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "어깨와 힙의 너비가 비슷해요",
                            "허리 라인이 뚜렷하게 들어가요",
                            "상하체 균형이 잘 맞아요"
                        ).forEach {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.padding(top = 6.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(Brown))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = BrownDark)
                            }
                        }
                    }
                }

                Button(
                    onClick = onStyleSuggestion,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Text("내 체형 맞춤 스타일 제안 보기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { analyzed = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Text("다시 분석하기", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
