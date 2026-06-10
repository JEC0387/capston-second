package com.jeiu.fashionproject.ui.screens

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.ui.theme.*

private data class OutfitCard(val title: String, val desc: String, val bgColor: Color, val imageUrl: String)
private data class PopularItem(val name: String, val price: String, val bgColor: Color, val imageUrl: String)

private val outfitCards = listOf(
    OutfitCard("캐주얼 데일리룩", "베이지 니트 + 와이드 팬츠", Color(0xFFEDE3D5), "https://loremflickr.com/400/500/fashion,casual?lock=10"),
    OutfitCard("미니멀 오피스룩", "화이트 블라우스 + 슬랙스", Color(0xFFE8E0D8), "https://loremflickr.com/400/500/fashion,office?lock=11"),
    OutfitCard("스트릿 캐주얼", "후드집업 + 카고 팬츠", Color(0xFFDDD5C8), "https://loremflickr.com/400/500/streetwear,fashion?lock=12"),
)

private val popularItems = listOf(
    PopularItem("오버사이즈 니트", "43,000원", Color(0xFFF0E8DC), "https://loremflickr.com/300/380/knit,sweater?lock=20"),
    PopularItem("린넨 와이드 팬츠", "58,000원", Color(0xFFE8DDD0), "https://loremflickr.com/300/380/pants,fashion?lock=21"),
    PopularItem("베이직 코튼 티", "22,000원", Color(0xFFEDE5D8), "https://loremflickr.com/300/380/tshirt,clothing?lock=22"),
    PopularItem("데님 자켓", "89,000원", Color(0xFFDDD5C8), "https://loremflickr.com/300/380/denim,jacket?lock=23"),
)

@Composable
fun HomeScreen(onWeatherOutfitClick: () -> Unit, onProductClick: () -> Unit) {
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
                IconButton(onClick = {}) {
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
                        Icon(Icons.Filled.WbSunny, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                        Text("맑음 · 23°C", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "오늘 날씨에 맞는 코디를 추천해드려요",
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
            items(outfitCards) { card ->
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable(onClick = onWeatherOutfitClick),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card.bgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            AsyncImage(
                                model = card.imageUrl,
                                contentDescription = card.title,
                                contentScale = ContentScale.Crop,
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
                        .clickable(onClick = onProductClick),
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
                                model = item.imageUrl,
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
}
