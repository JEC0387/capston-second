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
import com.jeiu.fashionproject.data.ProductInterestStore
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.recommend.OutfitSources
import com.jeiu.fashionproject.recommend.ClothingScope
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.TastePreferenceStore
import com.jeiu.fashionproject.data.PrivacyConsent
import com.jeiu.fashionproject.data.BodyAnalysisSessionStore
import com.jeiu.fashionproject.data.RecommendationLog
import com.jeiu.fashionproject.data.SavedOutfitStore
import com.jeiu.fashionproject.ui.components.rememberOutfitBookActions
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.recommend.ForecastAlerts
import com.jeiu.fashionproject.recommend.BodyProfile
import com.jeiu.fashionproject.recommend.BodyProfileRepository
import com.jeiu.fashionproject.recommend.BodyProfileAnalyzer
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.recommend.Situation
import com.jeiu.fashionproject.ui.components.ForecastStrip
import com.jeiu.fashionproject.ui.components.NotEnoughClothesCard
import com.jeiu.fashionproject.recommend.MissingItemFinder
import com.jeiu.fashionproject.recommend.josa
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.components.rememberWearToday
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
    initialTab: Int = 0,
    initialScope: ClothingScope = ClothingScope.OWNED,
    onShop: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionMeasurements by BodyAnalysisSessionStore.measurements.collectAsState()
    var savedBodyProfile by remember { mutableStateOf<BodyProfile?>(null) }
    LaunchedEffect(sessionMeasurements) {
        savedBodyProfile = null
        if (BodyProfileAnalyzer.fromMeasurements(sessionMeasurements) == null)
            savedBodyProfile = BodyProfileRepository.loadLatest(PrivacyConsent.canSaveRecords(context))
    }
    val bodyProfile: BodyProfile? = BodyProfileAnalyzer.fromMeasurements(sessionMeasurements)
        ?: savedBodyProfile.takeIf { PrivacyConsent.canSaveRecords(context) }
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        ProductInterestStore.load(context)
        WearLogStore.load(context)
        StylePreferenceStore.load(context)
        TastePreferenceStore.load(context)
    }
    val weatherState by weatherViewModel.state.collectAsState()
    val forecastState by weatherViewModel.forecast.collectAsState()
    val wardrobe by WardrobeStore.items.collectAsState()
    val wearLogs by WearLogStore.logs.collectAsState()
    var scope by rememberSaveable { mutableStateOf(initialScope) }
    val likedIds by ProductInterestStore.ids.collectAsState()
    val interested = ShopCatalog.interested(likedIds)
    val candidateItems = remember(wardrobe, likedIds, scope) {
        OutfitSources.candidates(wardrobe, interested, scope, System.currentTimeMillis())
    }
    val interestedItemIds = interested.map { OutfitSources.candidateId(it) }.toSet()
    val excludedInterestCount = interested.count { OutfitSources.candidate(it, 1L) == null }
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
    val scores by StylePreferenceStore.scores.collectAsState()
    val taste by TastePreferenceStore.scores.collectAsState()
    // [오늘 이거 입었어요]를 눌러도 오늘 코디 카드가 바뀌지 않도록, 오늘 코디는 오늘 착용 기록을 빼고 계산한다 (황상윤)
    val pastLogs = remember(wearLogs, today) { wearLogs.filter { it.date != today } }
    val dayPlanLogs = if (forecastDays.getOrNull(forecastIndex)?.date == today) pastLogs else wearLogs
    val dayPlan = remember(candidateItems, scope, dayPlanLogs, forecastDays, forecastIndex, weatherInfo, bodyProfile) {
        if (forecastIndex < 0) null
        else ForecastAlerts.plan(forecastDays, forecastIndex, weatherInfo?.temp, candidateItems, dayPlanLogs, scores,
            taste = taste, bodyProfile = bodyProfile)
    }
    val recommendation: OutfitEngine.Result? = remember(candidateItems, scope, pastLogs, wearLogs, weatherInfo, tab, situation, travelDay, dayPlan, bodyProfile) {
        when (tab) {
            0 -> OutfitEngine.recommend(candidateItems, weatherInfo, pastLogs, scores, taste = taste, bodyProfile = bodyProfile)
            1 -> dayPlan?.result
            else -> {
                val day = if (situation == Situation.TRAVEL) travelDay else null
                if (day == null) OutfitEngine.recommend(candidateItems, weatherInfo, pastLogs, scores, situation,
                    taste = taste, bodyProfile = bodyProfile)
                else OutfitEngine.recommend(candidateItems, day.toWeatherInfo(), wearLogs, scores, situation,
                    today = day.date, taste = taste, bodyProfile = bodyProfile)
            }
        }
    }
    // 보고 있는 코디가 오늘 코디일 때만 [오늘 이거 입었어요]를 보여준다
    val recommendDate = when (tab) {
        0 -> today
        1 -> dayPlan?.day?.date
        else -> if (situation == Situation.TRAVEL) travelDay?.date ?: today else today
    }
    val wearToday = rememberWearToday(wearLogs)
    // 🔖 저장한 코디 · 📅 코디 일정
    val book = rememberOutfitBookActions()
    val savedOutfits by SavedOutfitStore.items.collectAsState()
    // 보여 준 추천 코디를 기록 → 👍 · 실제 착용과 비교해 추천 성능을 잰다 (서버 recommendation_logs)
    LaunchedEffect(recommendation, recommendDate) {
        val ok = recommendation as? OutfitEngine.Result.Ok ?: return@LaunchedEffect
        if (tab != 1 && weatherInfo == null) return@LaunchedEffect   // 날씨를 받기 전 임시 코디는 세지 않음
        val screen = when (tab) { 0 -> "today"; 1 -> "forecast"; else -> "situation:${situation.name}" }
        RecommendationLog.record(context, recommendDate ?: today, screen, scope.name, ok.outfits)
    }
    // 상황별 탭에서 내 옷이 부족할 때 "무엇을 사면 되는지" (계획 4 — 5단계, 황상윤)
    val situationNeed = remember(wardrobe, situation, tab, scope) {
        if (tab == 2 && scope == ClothingScope.OWNED) MissingItemFinder.situationNeed(wardrobe, situation) else null
    }
    var searchKeyword by remember { mutableStateOf<String?>(null) }
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

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ClothingScope.OWNED, ClothingScope.ALL, ClothingScope.INTERESTED).forEach { option ->
                FilterChip(selected = scope == option, onClick = { scope = option },
                    label = { Text(option.recommendationLabel) })
            }
        }
        Text(scope.description, color = MediumGray, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp))
        if (scope != ClothingScope.OWNED && excludedInterestCount > 0) {
            Text("분류·색상·사진 정보가 부족하거나 지원하지 않는 관심상품 ${excludedInterestCount}개는 추천에서 제외했어요.",
                color = MediumGray, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
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
                    if (scope == ClothingScope.OWNED) {
                        // 상황별 탭이면 무엇을 사면 되는지도 알려준다
                        val need = situationNeed?.takeIf { tab == 2 && it.items.isNotEmpty() }
                        NotEnoughClothesCard(
                            message = r.message,
                            onAddClothing = onAddClothing,
                            suggestion = need?.let {
                                "${josa(it.items.joinToString(" + ") { item -> item.name }, "이", "가")} 있으면 ${it.situation.label} 코디를 만들 수 있어요"
                            },
                            onSearch = need?.let { n -> { searchKeyword = n.items.first().name } }
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(OutfitSources.insufficientMessage(scope, r.message), color = MediumGray)
                            TextButton(onClick = onShop) { Text("쇼핑 둘러보기") }
                            if (scope == ClothingScope.ALL) TextButton(onClick = onAddClothing) { Text("옷 등록하기") }
                        }
                    }
                }
                is OutfitEngine.Result.Ok -> {
                    r.notice?.let { notice -> item { Text(notice, style = MaterialTheme.typography.bodySmall, color = Brown) } }
                    items(r.outfits, key = { it.id }) { outfit ->
                        OutfitCard(
                            outfit = outfit,
                            interestedItemIds = interestedItemIds,
                            rated = ratings[outfit.id],
                            onRate = { like ->
                                ratings = ratings + (outfit.id to like)
                                TastePreferenceStore.rateOutfit(context, outfit, like)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            photoHeight = 300.dp,
                            wear = wearToday.takeIf { recommendDate == today },
                            saved = book.isSaved(savedOutfits, outfit),
                            onToggleSave = { book.toggleSave(outfit) },
                            // 5일 예보 탭이면 그 날짜가 먼저 골라져 있다
                            onPlan = { book.requestPlan(outfit.items, recommendDate) }
                        )
                    }
                }
            }
        }
    }
    wearToday.Dialog()
    book.Dialog()
    searchKeyword?.let { keyword -> ShopSearchSheet(keyword = keyword, onDismiss = { searchKeyword = null }) }
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
