package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.data.isCloudy
import com.jeiu.fashionproject.data.isRainy
import com.jeiu.fashionproject.data.WeatherOutfits
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel

@Composable
fun WeatherOutfitScreen(onBack: () -> Unit, onProductClick: () -> Unit, weatherViewModel: WeatherViewModel) {
    val context = LocalContext.current
    val weatherState by weatherViewModel.state.collectAsState()
    val outfits = WeatherOutfits.setsFor(
        (weatherState as? WeatherUiState.Success)?.info
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    var searchKeyword by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("오늘의 날씨 코디", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 날씨 정보 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CamelLight),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val weatherIcon = when (val s = weatherState) {
                        is WeatherUiState.Success -> if (s.info.isRainy() || s.info.isCloudy()) Icons.Filled.Cloud else Icons.Filled.WbSunny
                        else -> Icons.Filled.WbSunny
                    }
                    Icon(weatherIcon, contentDescription = null, tint = Camel, modifier = Modifier.size(40.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when (val s = weatherState) {
                                is WeatherUiState.Success -> "${s.info.condition} · ${s.info.temp}°C · 습도 ${s.info.humidity}%"
                                is WeatherUiState.Loading -> "날씨 불러오는 중..."
                                is WeatherUiState.Error -> s.message
                            },
                            style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (val s = weatherState) {
                                is WeatherUiState.Success -> s.info.advice()
                                else -> "오늘 날씨에 맞는 코디를 추천해드려요"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MediumGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("AI 추천 코디 ${outfits.size}가지", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("오늘 날씨와 내 스타일을 분석했어요", style = MaterialTheme.typography.bodySmall, color = MediumGray)

            Spacer(modifier = Modifier.height(16.dp))

            // 코디 카드들
            outfits.forEachIndexed { index, outfit ->
                val isSelected = index == selectedIndex
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { selectedIndex = index }
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, Camel, RoundedCornerShape(16.dp))
                            else Modifier
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 코디 이미지
                        if (outfit.imageSource != 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = outfit.imageSource,
                                    contentDescription = outfit.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(outfit.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (outfit.tag.contains("최적") || outfit.tag.contains("추천")) Camel else CamelLight)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(outfit.tag, style = MaterialTheme.typography.labelSmall, color = if (outfit.tag.contains("최적") || outfit.tag.contains("추천")) White else Brown)
                            }
                        }

                        // 의류 조합
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOfNotNull(outfit.top, outfit.bottom, outfit.shoes).forEach { item ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(item.color)
                                            .clickable {
                                                if (item.purchaseUrl.isNotEmpty()) {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.purchaseUrl)))
                                                } else {
                                                    searchKeyword = item.label
                                                }
                                            }
                                    ) {
                                        if (item.imageRes != 0) {
                                            AsyncImage(
                                                model = item.imageRes,
                                                contentDescription = item.label,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = MediumGray)
                                }
                            }
                        }

                        if (isSelected) {
                            Button(
                                onClick = { searchKeyword = outfit.title },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                            ) {
                                Text("이 코디 쇼핑하기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    searchKeyword?.let { keyword ->
        ShopSearchSheet(keyword = keyword, onDismiss = { searchKeyword = null })
    }
}
