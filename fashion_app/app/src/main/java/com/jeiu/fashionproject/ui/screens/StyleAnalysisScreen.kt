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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.recommend.StyleProfile
import com.jeiu.fashionproject.ui.theme.*

private val styleColors = mapOf(
    "미니멀" to StyleMinimal,
    "캐주얼" to StyleCasual,
    "클래식" to StyleClassic,
    "스트릿" to StyleStreet,
    "빈티지" to StyleVintage,
)

private fun colorOf(style: String): Color = styleColors[style] ?: MediumGray

/**
 * 스타일 분석 결과 — 옷장 옷의 스타일 태그 + 최근 착용 기록으로 계산 (StyleProfile).
 * 예전에는 "미니멀 35%" 같은 고정 숫자였다.
 */
@Composable
fun StyleAnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        WearLogStore.load(context)
    }
    val items by WardrobeStore.items.collectAsState()
    val logs by WearLogStore.logs.collectAsState()
    val result = remember(items, logs) { StyleProfile.analyze(items, logs) }

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

        if (result == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "옷장에 옷이 없어서 분석할 수 없어요.\n옷을 등록하면 내 스타일 분포를 보여드려요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumGray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
        val shown = result.shares.filter { it.percent > 0 }

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("나의 스타일 분포", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                        Text(
                            if (result.totalWears > 0)
                                "옷장 ${result.totalItems}벌 + 최근 ${result.windowDays}일 착용 ${result.totalWears}회 기준"
                            else "옷장 ${result.totalItems}벌 기준 (착용 기록 없음)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumGray
                        )
                    }

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

                            shown.forEach { style ->
                                val sweep = 360f * style.percent / 100f
                                drawArc(
                                    color = colorOf(style.style),
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
                            Text(result.top.style, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            Text("${result.top.percent}%", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 범례
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.chunked(2).forEach { row ->
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
                                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colorOf(style.style)))
                                        Text(style.style, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
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
                        result.keywords.take(3).forEach {
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
                        result.keywords.drop(3).forEach {
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
                        Text("스타일 코멘트", style = MaterialTheme.typography.titleMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        result.comment,
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
                    result.shares.forEach { style ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(style.style, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
                                Text("${style.percent}%", style = MaterialTheme.typography.bodySmall, color = colorOf(style.style), fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(
                                progress = { style.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = colorOf(style.style),
                                trackColor = CreamDark
                            )
                            Text(
                                "보유 ${style.owned}벌 · 최근 ${result.windowDays}일 착용 ${style.worn}회",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediumGray
                            )
                        }
                    }
                    Text(
                        "옷의 스타일은 옷 이름 · 종류 · 색으로 자동으로 붙고, 옷 정보 수정에서 바꿀 수 있어요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MediumGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}
