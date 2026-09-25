package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

private data class StyleData(val name: String, val percent: Int, val color: Color)

private val styleBreakdown = listOf(
    StyleData("미니멀", 35, StyleMinimal),
    StyleData("캐주얼", 25, StyleCasual),
    StyleData("클래식", 20, StyleClassic),
    StyleData("스트릿", 12, StyleStreet),
    StyleData("빈티지", 8, StyleVintage),
)

private val keywords = listOf("깔끔한", "심플한", "모던한", "자연스러운", "세련된", "편안한")

@Composable
fun StyleAnalysisScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("스타일 분석 결과", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 도넛 차트 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("나의 스타일 분포", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)

                    // 도넛 차트
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(200.dp)) {
                            var startAngle = -90f
                            val strokeWidth = 44.dp.toPx()
                            val inset = strokeWidth / 2f
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(inset, inset)

                            styleBreakdown.forEach { style ->
                                val sweep = 360f * style.percent / 100f
                                drawArc(
                                    color = style.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep - 2f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("미니멀", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            Text("35%", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 범례
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        styleBreakdown.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                row.forEach { style ->
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(style.color))
                                        Text(style.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
                                        Text("${style.percent}%", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 스타일 키워드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("나를 표현하는 키워드", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        keywords.take(3).forEach {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(CamelLight)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("# $it", style = MaterialTheme.typography.bodySmall, color = Brown, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        keywords.drop(3).forEach {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(CamelLight)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("# $it", style = MaterialTheme.typography.bodySmall, color = Brown, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // AI 코멘트
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CamelLight),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✨", style = MaterialTheme.typography.titleMedium)
                        Text("AI 스타일 코멘트", style = MaterialTheme.typography.titleMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "깔끔하고 심플한 미니멀 스타일을 선호하시네요. 불필요한 장식 없이 기본에 충실한 코디를 잘 소화하실 수 있어요. 베이직 아이템을 활용한 다양한 코디 조합이 잘 어울릴 거예요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrownDark,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }
            }

            // 스타일별 비율 바
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("스타일 상세 분포", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    styleBreakdown.forEach { style ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(style.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
                                Text("${style.percent}%", style = MaterialTheme.typography.bodySmall, color = style.color, fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(
                                progress = { style.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = style.color,
                                trackColor = CreamDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
