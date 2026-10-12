package com.jeiu.fashionproject.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.jeiu.fashionproject.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.data.isRainy
import com.jeiu.fashionproject.data.isCloudy
import com.jeiu.fashionproject.recommend.ForecastAlerts
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.ui.components.NotEnoughClothesCard
import com.jeiu.fashionproject.ui.components.OutfitCard
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.ForecastUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel

private data class PopularItem(val name: String, val price: String, val bgColor: Color, val imageRes: Int, val purchaseUrl: String)

private val popularItems = listOf(
    PopularItem("워셔블 케이블 반팔 니트 - 16 COLOR", "43,000원", Color(0xFFF0E8DC), R.drawable.popular_knit_cable, "https://www.musinsa.com/products/3927583"),
    PopularItem("[크롭] 스트라이프 레이어드 헨리넥 반팔 티셔츠_차콜", "22,000원", Color(0xFFE8DDD0), R.drawable.popular_stripe_tshirt, "https://www.musinsa.com/products/6154823"),
    PopularItem("Side Big Cargo Pants_8Colors", "58,000원", Color(0xFFEDE5D8), R.drawable.popular_cargo_pants, "https://4910.kr/desktop/goods/39666985"),
    PopularItem("Kangaroo Pocket Bermuda Wide Sweat Shorts BLACK", "49,000원", Color(0xFFDDD5C8), R.drawable.popular_sweat_shorts, "https://4910.kr/desktop/goods/69121270"),
    PopularItem("[크롭] 배색 롤업 크롭 반팔 티셔츠_버건디", "22,000원", Color(0xFFE8E0D8), R.drawable.popular_crop_tshirt, "https://www.musinsa.com/products/6154816"),
    PopularItem("린넨 라이크 세미 와이드 밴딩 팬츠 - 10 COLOR", "39,000원", Color(0xFFEDE8E0), R.drawable.popular_linen_pants, "https://www.musinsa.com/products/1924274"),
)

@Composable
fun HomeScreen(
    onOutfitRecommendClick: () -> Unit,
    onForecastClick: () -> Unit,
    onProductClick: () -> Unit,
    onAddClothing: () -> Unit,
    weatherViewModel: WeatherViewModel
) {
    val weatherState by weatherViewModel.state.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { weatherViewModel.fetchWeather() }

    LaunchedEffect(Unit) {
        StylePreferenceStore.load(context)
        WardrobeStore.load(context)
        WearLogStore.load(context)
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (coarseGranted) {
            weatherViewModel.fetchWeather()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // 오늘의 AI 코디 — 옷장 옷으로 코디 엔진이 만든다 (계획 4)
    val wardrobe by WardrobeStore.items.collectAsState()
    val wearLogs by WearLogStore.logs.collectAsState()
    val weatherInfo = (weatherState as? WeatherUiState.Success)?.info
    // 👍👎를 눌러도 카드가 바로 바뀌지 않도록 취향 점수는 계산할 때의 값만 쓴다
    val recommendation = remember(wardrobe, wearLogs, weatherInfo) {
        OutfitEngine.recommend(wardrobe, weatherInfo, wearLogs, StylePreferenceStore.scores.value)
    }
    var ratings by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showNotifDialog by remember { mutableStateOf(false) }

    // 내일 코디 (계획 4 — 3단계) — 예보에서 내일을 찾아 코디와 알림 문구를 만든다
    val forecastState by weatherViewModel.forecast.collectAsState()
    val forecastDays = (forecastState as? ForecastUiState.Success)?.days.orEmpty()
    val tomorrowPlan = remember(wardrobe, wearLogs, forecastDays, weatherInfo) {
        val index = forecastDays.indexOfFirst { it.date == WearDates.daysAgo(-1) }
        if (index < 0) null
        else ForecastAlerts.plan(forecastDays, index, weatherInfo?.temp, wardrobe, wearLogs, StylePreferenceStore.scores.value, count = 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("안녕하세요", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                Text(
                    "오늘의 스타일 찾기",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CharcoalBlack,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showNotifDialog = true }) {
                    // 내일 날씨 알림이 있으면 빨간 점
                    BadgedBox(badge = { if (tomorrowPlan?.alerts?.isNotEmpty() == true) Badge(containerColor = HeartPink) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "알림", tint = CharcoalBlack)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CamelLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text("나", style = MaterialTheme.typography.labelLarge, color = Brown)
                }
            }
        }

        // 날씨 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(onClick = onOutfitRecommendClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Camel),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val weatherIcon = when (val s = weatherState) {
                            is WeatherUiState.Success -> if (s.info.isRainy() || s.info.isCloudy()) Icons.Filled.Cloud else Icons.Filled.WbSunny
                            else -> Icons.Filled.WbSunny
                        }
                        Icon(weatherIcon, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                        Text(
                            when (val s = weatherState) {
                                is WeatherUiState.Success -> "${s.info.condition} · ${s.info.temp}°C · 습도 ${s.info.humidity}%"
                                is WeatherUiState.Loading -> "날씨 불러오는 중..."
                                is WeatherUiState.Error -> s.message
                            },
                            style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        when (val s = weatherState) {
                            is WeatherUiState.Success -> s.info.advice()
                            else -> "오늘 날씨에 맞는 코디를 추천해드려요"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.85f)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("AI 추천 보기", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 오늘의 AI 코디
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("오늘의 AI 코디", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOutfitRecommendClick) {
                Text("전체보기", style = MaterialTheme.typography.bodySmall, color = Camel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (val r = recommendation) {
            is OutfitEngine.Result.NotEnough -> NotEnoughClothesCard(
                message = r.message,
                onAddClothing = onAddClothing,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            is OutfitEngine.Result.Ok -> {
                r.notice?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = Brown,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(r.outfits, key = { it.id }) { outfit ->
                        OutfitCard(
                            outfit = outfit,
                            rated = ratings[outfit.id],
                            modifier = Modifier.width(260.dp),
                            onRate = { like ->
                                ratings = ratings + (outfit.id to like)
                                StylePreferenceStore.rate(context, outfit.style, like)
                            }
                        )
                    }
                }
            }
        }

        // 내일 코디
        tomorrowPlan?.let { plan ->
            Spacer(modifier = Modifier.height(28.dp))
            TomorrowOutfitSection(
                plan = plan,
                rated = ratings,
                onRate = { outfit, like ->
                    ratings = ratings + (outfit.id to like)
                    StylePreferenceStore.rate(context, outfit.style, like)
                },
                onMore = onForecastClick
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 인기 상품
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("인기 상품", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            TextButton(onClick = onProductClick) {
                Text("전체보기", style = MaterialTheme.typography.bodySmall, color = Camel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(popularItems) { item ->
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.purchaseUrl)))
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(item.bgColor)
                        ) {
                            AsyncImage(
                                model = item.imageRes,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Medium)
                            Text(item.price, style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text(if (tomorrowPlan?.alerts?.isNotEmpty() == true) "내일 날씨 알림" else "알림", fontWeight = FontWeight.Bold) },
            text = {
                val alerts = tomorrowPlan?.alerts.orEmpty()
                if (alerts.isEmpty()) Text("새로운 알림이 없습니다.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) { Text("확인", color = Camel) }
            }
        )
    }
}

/** 홈 "내일 코디" — 내일 날씨 요약 + 알림 문구 + 코디 1벌 (계획 4 — 3단계) */
@Composable
private fun TomorrowOutfitSection(
    plan: ForecastAlerts.DayPlan,
    rated: Map<String, Boolean>,
    onRate: (OutfitEngine.Outfit, Boolean) -> Unit,
    onMore: () -> Unit
) {
    val day = plan.day
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내일 코디", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text(
                    "${WearDates.label(day.date)} · ${day.condition} · ${day.minTemp}~${day.maxTemp}°C",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray
                )
            }
            TextButton(onClick = onMore) { Text("5일 예보", style = MaterialTheme.typography.bodySmall, color = Camel) }
        }
        val messages = plan.alerts.ifEmpty { listOf("내일은 오늘과 비슷한 날씨예요") }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CamelLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Brown) }
            }
        }
        when (val r = plan.result) {
            is OutfitEngine.Result.NotEnough -> Text(r.message, style = MaterialTheme.typography.bodySmall, color = MediumGray)
            is OutfitEngine.Result.Ok -> r.outfits.firstOrNull()?.let { outfit ->
                OutfitCard(
                    outfit = outfit,
                    rated = rated[outfit.id],
                    onRate = { like -> onRate(outfit, like) },
                    modifier = Modifier.fillMaxWidth(),
                    photoHeight = 220.dp
                )
            }
        }
    }
}
