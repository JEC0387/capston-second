package com.jeiu.fashionproject.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.BodyHistoryAnalyzer
import com.jeiu.fashionproject.ui.viewmodel.BodyMetric
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 체형 측정 기록 · 변화 그래프 (계획 1 — 7번).
 * 서버 DB(body_measurements)에 쌓인 기록을 불러와 항목별 꺾은선 그래프와 직전 기록 대비 변화를 보여준다.
 */
@Composable
fun BodyHistoryScreen(onBack: () -> Unit, onMeasure: () -> Unit) {
    // loaded == null 이면 서버 연결 실패 (불러오는 중에는 loading = true)
    var loaded by remember { mutableStateOf<List<BodyRecord>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var metric by remember { mutableStateOf(BodyMetric.WAIST_HIP) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        loading = true
        loaded = BodyRecords.load()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text(
                "체형 측정 기록",
                style = MaterialTheme.typography.titleLarge,
                color = CharcoalBlack,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { reloadKey++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "새로고침", tint = MediumGray)
            }
        }

        val records = loaded
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Camel)
            }
            records == null -> EmptyState(
                "서버에 연결되지 않아 기록을 불러올 수 없어요.\n서버를 켠 뒤 새로고침을 눌러 주세요.",
                "다시 불러오기"
            ) { reloadKey++ }
            records.isEmpty() -> EmptyState(
                "아직 측정 기록이 없어요.\n체형 분석을 하면 결과 숫자가 기록으로 쌓여요.\n(사진은 저장되지 않아요)",
                "체형 분석하러 가기",
                onMeasure
            )
            else -> HistoryContent(
                records, metric, onMetric = { metric = it },
                onDeleteSamples = {
                    scope.launch {
                        val ok = BodyRecords.deleteSamples()
                        Toast.makeText(
                            context,
                            if (ok) "샘플 기록을 지웠어요" else "서버에 연결되지 않아 지우지 못했어요",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) reloadKey++
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MediumGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
        ) { Text(button) }
    }
}

@Composable
private fun HistoryContent(
    records: List<BodyRecord>,
    metric: BodyMetric,
    onMetric: (BodyMetric) -> Unit,
    onDeleteSamples: () -> Unit
) {
    val sampleCount = records.count { it.isSample }
    val newestFirst = remember(records) { BodyHistoryAnalyzer.chronological(records).reversed() }
    val changes = remember(records) { BodyHistoryAnalyzer.compareLatest(records) }
    val summary = remember(records) { BodyHistoryAnalyzer.summary(records) }
    val points = remember(records, metric) { BodyHistoryAnalyzer.series(records, metric) }
    val latest = newestFirst.first()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // 요약 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CamelLight),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("최근 체형: ${latest.bodyType}", style = MaterialTheme.typography.titleMedium, color = BrownDark, fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = BrownDark)
                Text(
                    "총 ${records.size}회 측정 · 마지막 ${dateLabel(latest.measuredAt, "M월 d일 HH:mm")}",
                    style = MaterialTheme.typography.bodySmall, color = Brown
                )
            }
        }

        // 시연용 샘플 기록 안내
        if (sampleCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "'샘플' 표시 기록 ${sampleCount}개는 그래프를 보여주기 위한 시연용 예시예요. (회색 점)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumGray,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDeleteSamples) { Text("샘플 지우기", color = Brown) }
            }
        }

        // 그래프 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("변화 그래프", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BodyMetric.values().forEach { m ->
                        FilterChip(
                            selected = m == metric,
                            onClick = { onMetric(m) },
                            label = { Text(m.shortLabel, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Camel,
                                selectedLabelColor = White
                            )
                        )
                    }
                }
                when {
                    points.isEmpty() -> Text(
                        if (metric.isRatio) "이 항목은 아직 기록이 없어요."
                        else "cm 값이 있는 기록이 없어요. 키를 입력하고 머리부터 발끝까지 보이게 찍으면 cm 가 기록돼요.",
                        style = MaterialTheme.typography.bodySmall, color = MediumGray
                    )
                    points.size == 1 -> Text(
                        "지금 값: ${metric.format(points[0].second)}\n기록이 2개 이상이면 꺾은선 그래프가 그려져요.",
                        style = MaterialTheme.typography.bodySmall, color = MediumGray
                    )
                    else -> LineChart(points, metric)
                }
                Text(
                    "※ 사진 각도 · 옷차림 · 자세에 따라 측정할 때마다 1~3% 정도 차이가 날 수 있어요. 너비 cm 는 정면 폭 추정값이에요(둘레 아님).",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray
                )
            }
        }

        // 직전 기록과 비교
        if (changes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("지난번과 비교", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    changes.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (c.significant) Camel else LightGray)
                            )
                            Text(
                                c.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (c.significant) CharcoalBlack else MediumGray
                            )
                        }
                    }
                }
            }
        }

        // 기록 목록
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("측정 기록", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                newestFirst.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = LightGray)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dateLabel(r.measuredAt, "yyyy.MM.dd HH:mm"), style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            Text(
                                listOfNotNull(
                                    r.shoulderToHip?.let { "어깨:엉덩이 ${BodyMetric.SHOULDER_HIP.format(it)}" },
                                    r.waistToHip?.let { "허리:엉덩이 ${BodyMetric.WAIST_HIP.format(it)}" },
                                    r.waistCm?.let { "허리 ${BodyMetric.WAIST_CM.format(it)}" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = CharcoalBlack
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (r.isSample) {
                                Text(
                                    "샘플",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MediumGray)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            Text(r.bodyType, style = MaterialTheme.typography.labelLarge, color = Brown, fontWeight = FontWeight.SemiBold)
                            Text("신뢰도 ${r.confidence}%", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** 날짜별 꺾은선 그래프 (외부 라이브러리 없이 Canvas 로 그림) */
@Composable
private fun LineChart(points: List<Pair<BodyRecord, Float>>, metric: BodyMetric) {
    val values = points.map { it.second }
    val rawMin = values.min()
    val rawMax = values.max()
    // 값이 거의 같아도 선이 납작하게 붙지 않도록 위아래 여유를 둔다
    val pad = maxOf((rawMax - rawMin) * 0.2f, if (metric.isRatio) 0.02f else 0.5f)
    val minY = rawMin - pad
    val maxY = rawMax + pad

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("최고 ${metric.format(rawMax)}", style = MaterialTheme.typography.labelSmall, color = MediumGray)
            Text("최저 ${metric.format(rawMin)}", style = MaterialTheme.typography.labelSmall, color = MediumGray)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            fun x(i: Int) = left + (right - left) * i / (points.size - 1).coerceAtLeast(1)
            fun y(v: Float) = bottom - (bottom - top) * (v - minY) / (maxY - minY)

            // 가로 보조선 3줄
            for (k in 0..2) {
                val gy = top + (bottom - top) * k / 2f
                drawLine(LightGray, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            points.forEachIndexed { i, (_, v) ->
                if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            drawPath(path, Camel, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { i, (_, v) ->
                val last = i == points.lastIndex
                val sample = points[i].first.isSample
                // 샘플 기록은 회색 점으로 구분
                val dot = when { sample -> MediumGray; last -> Brown; else -> Camel }
                drawCircle(dot, radius = (if (last) 6 else 4).dp.toPx(), center = Offset(x(i), y(v)))
                drawCircle(White, radius = 2.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateLabel(points.first().first.measuredAt, "M/d"), style = MaterialTheme.typography.labelSmall, color = MediumGray)
            Text(
                "${metric.label} · 최근 ${metric.format(values.last())}",
                style = MaterialTheme.typography.labelSmall, color = Brown, fontWeight = FontWeight.SemiBold
            )
            Text(dateLabel(points.last().first.measuredAt, "M/d"), style = MaterialTheme.typography.labelSmall, color = MediumGray)
        }
    }
}

private fun dateLabel(ms: Long, pattern: String): String =
    if (ms <= 0L) "-" else SimpleDateFormat(pattern, Locale.KOREA).format(Date(ms))
