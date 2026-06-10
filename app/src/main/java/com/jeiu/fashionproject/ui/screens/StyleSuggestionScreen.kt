package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

private data class StyleCard(val title: String, val desc: String, val color: Color)
private data class RecommendItem(val name: String, val price: String, val color: Color)

private val recommendedStyles = listOf(
    StyleCard("허리 강조 원피스", "모래시계형에 잘 맞는 허리 라인 원피스", Color(0xFFEDE3D5)),
    StyleCard("피트 앤 플레어", "상하체 균형을 살려주는 핏", Color(0xFFE8E0D8)),
    StyleCard("크롭 탑 + 미디스커트", "허리 라인을 자연스럽게 강조", Color(0xFFDDD5C8)),
    StyleCard("랩 스타일", "체형을 돋보이게 하는 클래식 스타일", Color(0xFFE0D8D0)),
)

private val avoidStyles = listOf(
    "오버사이즈 티셔츠 + 와이드 팬츠 조합",
    "허리 라인이 없는 박시한 원피스",
    "너무 길고 풍성한 레이어드 스타일",
)

private val recommendItems = listOf(
    RecommendItem("랩 미디 원피스", "78,000원", Color(0xFFEDE3D5)),
    RecommendItem("피트 블라우스", "45,000원", Color(0xFFE8DDD0)),
    RecommendItem("허리 벨트 팬츠", "62,000원", Color(0xFFD5CABD)),
    RecommendItem("크롭 니트", "38,000원", Color(0xFFEDE8E0)),
)

@Composable
fun StyleSuggestionScreen(onBack: () -> Unit, onProductClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("체형별 스타일 제안", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 체형 배지
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Camel),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("내 체형", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.8f))
                        Text("모래시계형", style = MaterialTheme.typography.headlineSmall, color = White, fontWeight = FontWeight.Bold)
                        Text("어깨·힙 균형 · 허리 라인 뚜렷", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.85f))
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(24.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(White.copy(alpha = 0.7f)))
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(modifier = Modifier.size(14.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(White.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(modifier = Modifier.size(22.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(White.copy(alpha = 0.7f)))
                        }
                    }
                }
            }

            // 추천 스타일
            Text("추천 스타일", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recommendedStyles.forEach { style ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(modifier = Modifier.padding(0.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                    .background(style.color)
                            )
                            Column(
                                modifier = Modifier.padding(14.dp).weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Camel, modifier = Modifier.size(16.dp))
                                    Text(style.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                                }
                                Text(style.desc, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                        }
                    }
                }
            }

            // 피해야 할 스타일
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("피하면 좋은 스타일", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    avoidStyles.forEach { style ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFD4756A), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Text(style, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                        }
                    }
                }
            }

            // 추천 아이템
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("지금 바로 담아보세요", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(recommendItems) { item ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable(onClick = onProductClick),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(item.color)
                            )
                            Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Medium)
                            Text(item.price, style = MaterialTheme.typography.bodySmall, color = Camel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
