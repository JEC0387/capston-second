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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.ShopCatalog
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.recommend.BodyStyleSuggestions
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisUiState
import com.jeiu.fashionproject.ui.theme.*

@Composable
fun StyleSuggestionScreen(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    analysisState: BodyAnalysisUiState = BodyAnalysisUiState.Idle,
    gender: Gender? = null,
    wardrobe: List<WardrobeItem> = emptyList()
) {
    val suggestion = BodyStyleSuggestions.create(analysisState, gender, wardrobe)
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

            Text(suggestion.explanation, style = MaterialTheme.typography.bodyMedium, color = Brown)
            Text(suggestion.genderNotice,
                style = MaterialTheme.typography.bodySmall, color = MediumGray)
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
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (suggestion.personalized) "이번 사진의 추정 분류" else "개인화 보류", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.8f))
                        Text(suggestion.bodyType?.label ?: "일반 스타일 예시", style = MaterialTheme.typography.headlineSmall, color = White, fontWeight = FontWeight.Bold)
                        Text("실측 핏을 보장하지 않는 스타일 참고", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.85f))
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
            Text(if (suggestion.personalized) "체형 추정에 따른 스타일 제안" else "일반 스타일 예시", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                suggestion.tips.forEach { style ->
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
                                    .background(CreamDark)
                            )
                            Column(
                                modifier = Modifier.padding(14.dp).weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Camel, modifier = Modifier.size(16.dp))
                                    Text(style.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                                }
                                Text(style.description, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                        }
                    }
                }
            }

            // Rules are suggestions, not prescriptions about the user's body.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("제안 참고사항", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    suggestion.considerations.forEach { style ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFD4756A), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Text(style, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                        }
                    }
                }
            }

            Text("등록된 옷장과 비교", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
            if (suggestion.wardrobeReasons.isEmpty()) {
                Text("확인된 체형·의류 정보로 연결할 옷장 후보가 없어요. 위 설명을 보유 의류에 대한 개인화 추천으로 보지 마세요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
            } else {
                suggestion.wardrobeReasons.forEach { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = Brown)
                }
            }

            // Browsing catalog entries is separate from personalized wardrobe evidence.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("카탈로그 둘러보기", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("체형별 적합성을 검증하지 않은 카탈로그 상품이에요. 표시 가격은 실시간 정보가 아니며 판매처에서 확인하세요.", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                LazyRow(
                    modifier = Modifier.testTag("styleCatalog"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(ShopCatalog.products, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onProductClick(item.id) },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(item.color))
                            ) {
                                AsyncImage(model = item.imageRes.takeIf { it != 0 } ?: item.imageUrl,
                                    contentDescription = item.name, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize())
                            }
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
