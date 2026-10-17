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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.produceState
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
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.NaverImageSearchRepository
import com.jeiu.fashionproject.data.ShopImageResult
import com.jeiu.fashionproject.data.WeatherOutfits
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.data.isRainy
import com.jeiu.fashionproject.data.isCloudy
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.components.outfitQuery
import com.jeiu.fashionproject.ui.components.OutfitPhoto
import com.jeiu.fashionproject.ui.components.OutfitPhotoImage
import com.jeiu.fashionproject.ui.components.rememberOutfitPhoto
import com.jeiu.fashionproject.ui.theme.*
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
    onWeatherOutfitClick: () -> Unit,
    onProductClick: () -> Unit,
    onShopClick: (presetQuery: String?) -> Unit,
    weatherViewModel: WeatherViewModel,
    gender: Gender? = null
) {
    val weatherState by weatherViewModel.state.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> weatherViewModel.fetchWeather() } // 거부해도 기본 위치(서울) 날씨로 진행

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) weatherViewModel.fetchWeather()
        else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val outfitCards = WeatherOutfits.cardsFor(
        (weatherState as? WeatherUiState.Success)?.info,
        gender
    )
    var showNotifDialog by remember { mutableStateOf(false) }
    var shopKeyword by remember { mutableStateOf<String?>(null) }

    // 인기 상품: 20°C 이상이면 앱에 넣어둔 여름 상품(구매 링크 있음),
    // 그보다 쌀쌀하면 계절 키워드로 네이버 이미지 검색 결과를 보여준다.
    val weatherInfo = (weatherState as? WeatherUiState.Success)?.info
    val seasonKeyword: String? = weatherInfo?.let {
        when {
            it.temp < 5 -> "한겨울"
            it.temp < 12 -> "겨울"
            it.temp < 20 -> "가을"
            else -> null // 여름 상품 목록 사용
        }
    }
    // 20대 대상 앱이라 검색어에 "20대"를 넣어 연령대가 다른 코디가 섞이지 않게 한다
    val seasonShopQuery = seasonKeyword?.let { "20대 $it 코디" }          // 쇼핑 탭 검색창에 들어갈 말
    val seasonQuery = seasonShopQuery?.let { outfitQuery(gender, it) }   // 실제 검색어(성별 포함)
    val seasonItems by produceState<List<ShopImageResult>>(emptyList(), seasonQuery) {
        value = if (seasonQuery == null) emptyList()
        else runCatching { NaverImageSearchRepository.search(seasonQuery, display = 10) }.getOrDefault(emptyList())
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
                    Icon(Icons.Filled.Notifications, contentDescription = "알림", tint = CharcoalBlack)
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
                .clickable(onClick = onWeatherOutfitClick),
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
                                is WeatherUiState.Success -> "${s.info.condition} · ${s.info.temp}°C"
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
            TextButton(onClick = onWeatherOutfitClick) {
                Text("전체보기", style = MaterialTheme.typography.bodySmall, color = Camel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(outfitCards) { index, card ->
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable {
                            when {
                                card.purchaseUrl.isNotEmpty() -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.purchaseUrl)))
                                index == 0 -> onProductClick()
                                else -> onWeatherOutfitClick()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card.bgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            OutfitPhotoImage(
                                photo = rememberOutfitPhoto(card.imageSource, outfitQuery(gender, card.desc, suffix = "코디")),
                                contentDescription = card.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            Text(card.desc, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                        }
                    }
                }
            }
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
            Text(
                if (seasonKeyword != null) "요즘 날씨 인기 스타일" else "인기 상품",
                style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold
            )
            // 전체보기 → 쇼핑 탭으로 이동 (전에는 상품 하나짜리 상세 화면으로 가서 '전체'가 아니었음)
            TextButton(onClick = { onShopClick(seasonShopQuery) }) {
                Text("전체보기", style = MaterialTheme.typography.bodySmall, color = Camel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (seasonKeyword != null) {
                items(seasonItems) { result ->
                    Card(
                        modifier = Modifier
                            .width(140.dp)
                            .clickable { shopKeyword = outfitQuery(gender, seasonKeyword, "코디") },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(CamelLight)
                            ) {
                                OutfitPhotoImage(
                                    photo = OutfitPhoto(result.imageUrl, result.thumbnailUrl),
                                    contentDescription = result.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                result.title,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = CharcoalBlack,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2
                            )
                        }
                    }
                }
            } else items(popularItems) { item ->
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

    shopKeyword?.let { keyword ->
        ShopSearchSheet(keyword = keyword, onDismiss = { shopKeyword = null })
    }

    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text("알림", fontWeight = FontWeight.Bold) },
            text = { Text("새로운 알림이 없습니다.") },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) { Text("확인", color = Camel) }
            }
        )
    }
}
