package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.recommend.ForecastAlerts
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.recommend.Situation
import com.jeiu.fashionproject.ui.components.ForecastStrip
import com.jeiu.fashionproject.ui.components.NotEnoughClothesCard
import com.jeiu.fashionproject.ui.components.OutfitCard
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.ForecastUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel

/**
 * 코디 추천 화면 (계획 4 — 추천).
 * 홈의 [AI 추천 보기]·[전체보기]·[5일 예보]로 들어온다.
 *  - 오늘: 오늘 날씨에 맞는 옷장 코디
 *  - 5일 예보: 날씨 띠에서 날짜를 고르면 그날 알림 문구와 코디
 *  - 상황별: 면접·데이트·여행·운동 (여행은 날짜를 골라 그날 예보로) — 맞는 옷이 없으면 부족한 옷 안내
 */
@Composable
fun OutfitRecommendScreen(
    onBack: () -> Unit,
    onAddClothing: () -> Unit,
    weatherViewModel: WeatherViewModel,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        WearLogStore.load(context)
    }
    val weatherState by weatherViewModel.state.collectAsState()
    val forecastState by weatherViewModel.forecast.collectAsState()
    val wardrobe by WardrobeStore.items.collectAsState()
    val wearLogs by WearLogStore.logs.collectAsState()
    val weatherInfo = (weatherState as? WeatherUiState.Success)?.info
    val forecastDays = (forecastState as? ForecastUiState.Success)?.days.orEmpty()
    val today = WearDates.today()

    val tabs = listOf("오늘", "5일 예보", "상황별")
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var situation by rememberSaveable { mutableStateOf(Situation.INTERVIEW) }
    // 5일 예보 탭에서 고른 날 (-1이면 아직 안 고름 → 내일)
    var forecastPick by rememberSaveable { mutableIntStateOf(-1) }
    val forecastIndex = when {
        forecastDays.isEmpty() -> -1
        forecastPick in forecastDays.indices -> forecastPick
        else -> forecastDays.indexOfFirst { it.date == WearDates.daysAgo(-1) }.coerceAtLeast(0)
    }
    // 여행 날짜: 0번은 오늘(지금 날씨), 그 뒤는 예보의 날짜들
    val travelDays: List<DayForecast?> = listOf<DayForecast?>(null) + forecastDays.filter { it.date != today }
    var travelPick by rememberSaveable { mutableIntStateOf(0) }
    val travelDay = travelDays.getOrNull(travelPick)

    // 👍👎를 눌러도 카드가 바로 바뀌지 않도록 취향 점수는 계산할 때의 값만 쓴다
    val scores = StylePreferenceStore.scores.value
    val dayPlan = remember(wardrobe, wearLogs, forecastDays, forecastIndex, weatherInfo) {
        if (forecastIndex < 0) null
        else ForecastAlerts.plan(forecastDays, forecastIndex, weatherInfo?.temp, wardrobe, wearLogs, scores)
    }
    val recommendation: OutfitEngine.Result? = remember(wardrobe, wearLogs, weatherInfo, tab, situation, travelDay, dayPlan) {
        when (tab) {
            0 -> OutfitEngine.recommend(wardrobe, weatherInfo, wearLogs, scores)
            1 -> dayPlan?.result
            else -> {
                val day = if (situation == Situation.TRAVEL) travelDay else null
                if (day == null) OutfitEngine.recommend(wardrobe, weatherInfo, wearLogs, scores, situation)
                else OutfitEngine.recommend(wardrobe, day.toWeatherInfo(), wearLogs, scores, situation, today = day.date)
            }
        }
    }
    var ratings by remember { mutableStateOf(mapOf<String, Boolean>()) }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 상단 바
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("코디 추천", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        // 오늘 날씨 한 줄
        Text(
            when (val s = weatherState) {
                is WeatherUiState.Success -> "${s.info.condition} · ${s.info.temp}°C — ${s.info.advice()}"
                is WeatherUiState.Loading -> "날씨 불러오는 중… (계절 기준으로 먼저 추천해요)"
                is WeatherUiState.Error -> "${s.message} (계절 기준으로 추천해요)"
            },
            style = MaterialTheme.typography.bodySmall, color = MediumGray,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = tab,
            containerColor = Cream,
            contentColor = Camel,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Camel)
            }
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = Camel,
                    unselectedContentColor = MediumGray
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 5일 예보: 날씨 띠 + 그날 알림
            if (tab == 1) {
                when (val f = forecastState) {
                    is ForecastUiState.Loading -> item { Hint("예보 불러오는 중…") }
                    is ForecastUiState.Error -> item { Hint(f.message) }
                    is ForecastUiState.Success -> {
                        item { ForecastStrip(f.days, forecastIndex, onSelect = { forecastPick = it }) }
                        dayPlan?.let { plan ->
                            item { DayAlerts(plan, today) }
                        }
                    }
                }
            }

            // 상황별: 상황 칩 (+ 여행이면 날짜 칩)
            if (tab == 2) {
                item {
                    ChipRow {
                        Situation.entries.forEach { s ->
                            FilterChip(
                                selected = situation == s,
                                onClick = { situation = s },
                                label = { Text(s.label) },
                                colors = chipColors()
                            )
                        }
                    }
                }
                if (situation == Situation.TRAVEL) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("여행 날짜", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            ChipRow {
                                travelDays.forEachIndexed { i, day ->
                                    FilterChip(
                                        selected = travelPick == i,
                                        onClick = { travelPick = i },
                                        label = {
                                            Text(
                                                if (day == null) "오늘"
                                                else "${ForecastAlerts.shortLabel(day.date, today)} ${day.minTemp}~${day.maxTemp}°"
                                            )
                                        },
                                        colors = chipColors()
                                    )
                                }
                            }
                            if (forecastDays.isEmpty()) Hint("예보를 불러오면 다른 날짜도 고를 수 있어요")
                        }
                    }
                }
            }

            // 코디
            when (val r = recommendation) {
                null -> Unit
                is OutfitEngine.Result.NotEnough -> item {
                    NotEnoughClothesCard(message = r.message, onAddClothing = onAddClothing)
                }
                is OutfitEngine.Result.Ok -> {
                    r.notice?.let { notice -> item { Text(notice, style = MaterialTheme.typography.bodySmall, color = Brown) } }
                    items(r.outfits, key = { it.id }) { outfit ->
                        OutfitCard(
                            outfit = outfit,
                            rated = ratings[outfit.id],
                            onRate = { like ->
                                ratings = ratings + (outfit.id to like)
                                StylePreferenceStore.rate(context, outfit.style, like)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            photoHeight = 300.dp
                        )
                    }
                }
            }
        }
    }
}

/** 고른 날의 날씨 요약 + 알림 문구 */
@Composable
private fun DayAlerts(plan: ForecastAlerts.DayPlan, today: String) {
    val day = plan.day
    val label = ForecastAlerts.dayLabel(day.date, today)
    val messages = plan.alerts.ifEmpty {
        if (label == "내일") listOf("내일은 오늘과 비슷한 날씨예요") else emptyList()
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CamelLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${WearDates.label(day.date)} · ${day.condition} · 낮 ${day.dayTemp}°C (${day.minTemp}~${day.maxTemp}°C)",
                style = MaterialTheme.typography.labelLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
            )
            messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Brown) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MediumGray)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = Camel, selectedLabelColor = White)
