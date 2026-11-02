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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.BodyRecords
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.recommend.BodyStyleGuide
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.components.OutfitPhotoImage
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.components.outfitQuery
import com.jeiu.fashionproject.ui.components.rememberOutfitPhoto
import com.jeiu.fashionproject.ui.theme.*

/**
 * 체형 맞춤 스타일 제안.
 *  - 체형: 이번 분석 결과, 없으면 서버에 저장된 최근 측정 기록 (둘 다 없으면 체형 분석으로 안내)
 *  - 제안: 체형 4가지 × 성별 규칙 (BodyStyleGuide) — 예시 사진은 성별 + 제안 검색어로 찾은 실제 코디 사진
 *  - 내 옷장: 제안에 잘 맞는 옷 · 피하면 좋은 옷
 *  - 제안 카드를 누르면 쇼핑몰에서 그 스타일을 바로 검색
 */
@Composable
fun StyleSuggestionScreen(
    onBack: () -> Unit,
    onProductClick: () -> Unit,
    analysis: com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisResult? = null,
    gender: Gender? = null,
    onBodyAnalysis: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WardrobeStore.load(context) }
    val wardrobe by WardrobeStore.items.collectAsState()

    // 이번 분석이 없으면 저장된 최근 측정 기록의 체형
    var savedType by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(analysis == null) }
    LaunchedEffect(analysis) {
        if (analysis == null) {
            savedType = BodyRecords.load(limit = 5)?.firstOrNull { it.bodyType in BodyStyleGuide.BODY_TYPES }?.bodyType
            loading = false
        }
    }
    val bodyType = analysis?.bodyTypeKo ?: savedType
    val guide = remember(bodyType, gender) { BodyStyleGuide.guide(bodyType, gender) }
    val (good, avoid) = remember(guide, wardrobe, gender) {
        guide?.let { BodyStyleGuide.sort(wardrobe, it, gender) } ?: (emptyList<WardrobeItem>() to emptyList())
    }
    var shopKeyword by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("체형 맞춤 스타일 제안", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        if (guide == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (loading) CircularProgressIndicator(color = Camel)
                    else {
                        Text("아직 체형 분석 기록이 없어요.\n전신 사진으로 체형을 분석하면 체형에 맞는 스타일을 제안해 드려요.",
                            style = MaterialTheme.typography.bodyMedium, color = MediumGray, textAlign = TextAlign.Center)
                        Button(onClick = onBodyAnalysis, colors = ButtonDefaults.buttonColors(containerColor = Camel)) {
                            Text("체형 분석하러 가기")
                        }
                    }
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            // 체형 카드
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Camel), elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (analysis != null) "이번 사진의 추정 체형" else "최근 측정 기록의 체형",
                        style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.85f))
                    Text("${guide.bodyType}${gender?.let { " · ${it.label}" } ?: ""}",
                        style = MaterialTheme.typography.headlineSmall, color = White, fontWeight = FontWeight.Bold)
                    Text(guide.summary, style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.9f))
                }
            }
            if (gender == null) {
                Text("마이페이지에서 성별을 고르면 성별에 맞는 제안으로 바뀌어요.", style = MaterialTheme.typography.labelSmall, color = MediumGray)
            }

            // 제안 — 사진은 실제 코디 검색 결과, 누르면 쇼핑몰 검색
            Text("이렇게 입어 보세요", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            guide.tips.forEach { tip ->
                val query = outfitQuery(gender, tip.query)
                val photo = rememberOutfitPhoto(null, query)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { shopKeyword = query },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White), elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)).background(CamelLight)) {
                            if (photo.primary != null) OutfitPhotoImage(photo, tip.title, Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                        Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Camel, modifier = Modifier.size(16.dp))
                                Text(tip.title, style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                            }
                            Text(tip.desc, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            Text("눌러서 쇼핑몰에서 찾아보기 ›", style = MaterialTheme.typography.labelSmall, color = Camel)
                        }
                    }
                }
            }

            // 내 옷장에서
            Text("내 옷장에서 잘 맞는 옷", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            if (good.isEmpty()) {
                Text("옷장에 이 체형 제안에 맞는 옷이 아직 없어요. 위 제안을 눌러 찾아보세요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
            } else {
                ItemRow(good)
            }

            // 피하면 좋은 스타일 + 내 옷장 중 해당 옷
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("피하면 좋은 스타일", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    guide.avoid.forEach { style ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFD4756A), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Text(style, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                        }
                    }
                    if (avoid.isNotEmpty()) {
                        Text("내 옷장 중 다른 옷과 함께 입을 때 신경 쓰면 좋은 옷 ${avoid.size}벌",
                            style = MaterialTheme.typography.labelMedium, color = Brown)
                        ItemRow(avoid)
                    }
                }
            }
            Text("체형 분류 · 제안은 사진 속 어깨 · 허리 · 힙 비율로 추정한 일반적인 코디 원칙이며, 실제 착용 핏을 보장하지 않아요.",
                style = MaterialTheme.typography.labelSmall, color = MediumGray)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    shopKeyword?.let { ShopSearchSheet(keyword = it, onDismiss = { shopKeyword = null }) }
}

@Composable
private fun ItemRow(items: List<WardrobeItem>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.take(12), key = { it.id }) { item ->
            Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClothingThumb(item, Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)), cutoutPadding = 6.dp)
                Text(item.name, style = MaterialTheme.typography.labelSmall, color = CharcoalBlack, maxLines = 1)
            }
        }
    }
}
