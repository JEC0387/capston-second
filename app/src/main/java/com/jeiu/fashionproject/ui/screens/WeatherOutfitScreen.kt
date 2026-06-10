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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

private data class OutfitItem(val label: String, val color: Color)
private data class OutfitSet(val title: String, val tag: String, val top: OutfitItem, val bottom: OutfitItem, val shoes: OutfitItem)

private val outfits = listOf(
    OutfitSet(
        "캐주얼 데일리룩", "날씨 최적",
        OutfitItem("베이지 니트", Color(0xFFEDE3D5)),
        OutfitItem("와이드 팬츠", Color(0xFFD5CABD)),
        OutfitItem("스니커즈", Color(0xFFF0EBE3))
    ),
    OutfitSet(
        "미니멀 오피스룩", "내 스타일",
        OutfitItem("화이트 블라우스", Color(0xFFF5F2EE)),
        OutfitItem("슬랙스", Color(0xFFCDC5BC)),
        OutfitItem("로퍼", Color(0xFFB8A898))
    ),
    OutfitSet(
        "라이트 스트릿", "트렌디",
        OutfitItem("그래픽 티셔츠", Color(0xFFE8DDD0)),
        OutfitItem("카고 팬츠", Color(0xFFCFCAC0)),
        OutfitItem("청키 스니커즈", Color(0xFFDDD8D0))
    )
)

@Composable
fun WeatherOutfitScreen(onBack: () -> Unit, onProductClick: () -> Unit) {
    var selectedIndex by remember { mutableIntStateOf(0) }

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
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Camel, modifier = Modifier.size(40.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("맑음 · 23°C · 습도 45%", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                        Text("가벼운 아우터를 걸치기 좋은 날씨예요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(outfit.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (outfit.tag == "날씨 최적") Camel else CamelLight)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(outfit.tag, style = MaterialTheme.typography.labelSmall, color = if (outfit.tag == "날씨 최적") White else Brown)
                            }
                        }

                        // 의류 조합
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(outfit.top, outfit.bottom, outfit.shoes).forEach { item ->
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
                                    )
                                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = MediumGray)
                                }
                            }
                        }

                        if (isSelected) {
                            Button(
                                onClick = onProductClick,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                            ) {
                                Text("이 코디 구매하기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
