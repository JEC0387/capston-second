package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.BasicItems
import com.jeiu.fashionproject.data.Gender
import androidx.compose.ui.text.style.TextOverflow
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.recommend.MissingItemFinder
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.recommend.josa
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.components.ShopSearchSheet
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "이것만 사면 코디 완성" 화면.
 *  ① 사면 좋은 코디가 가장 많이 늘어나는 옷 TOP 3 — 대분류가 겹치지 않게 다양하게, 기본템은 같은 종류 상품 사진을 "예시"로
 *  ② 어울리는 짝이 없는 옷
 *  ③ 상황별 코디 — 출근 · 등교 · 면접 · 하객 · 데이트 · 여행 · 주말 · 운동마다 코디 한 벌 + 상품 추천
 */
@Composable
fun MissingItemScreen(onBack: () -> Unit, gender: Gender? = null, onProductClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WardrobeStore.load(context) }
    val wardrobe by WardrobeStore.items.collectAsState()

    // 후보마다 조합을 다 맞춰 보느라 잠깐 걸릴 수 있어 화면이 멈추지 않게 따로 계산한다
    var report by remember { mutableStateOf<MissingItemFinder.Report?>(null) }
    LaunchedEffect(wardrobe, gender) {
        report = withContext(Dispatchers.Default) { MissingItemFinder.analyze(wardrobe, gender = gender) }
    }
    var searchKeyword by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }   // 예시를 펼친 후보 id
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("이것만 사면 코디 완성", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        val r = report
        if (r == null) {
            Column(
                Modifier.fillMaxWidth().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = Camel)
                Text("옷장을 분석하는 중…", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("지금 옷장으로 ${r.season} 코디 ${r.goodCount}개를 만들 수 있어요",
                    style = MaterialTheme.typography.bodyMedium, color = MediumGray)
            }

            // ① TOP 3
            item { SectionTitle("이것만 사면 코디가 늘어나요") }
            if (r.suggestions.isEmpty()) item {
                Hint(if (r.goodCount == 0) "한 개만 더해서 완성되는 코디가 없어요. 아래 상황별 코디를 확인해 주세요."
                    else "지금은 코디를 더 늘려 줄 아이템이 없어요.")
            }
            items(r.suggestions, key = { it.item.id }) { s ->
                SuggestionCard(
                    rank = r.suggestions.indexOf(s) + 1,
                    suggestion = s,
                    gender = gender,
                    expanded = expanded == s.item.id,
                    onToggle = { expanded = if (expanded == s.item.id) null else s.item.id },
                    onSearch = { searchKeyword = s.item.name },
                    onProductClick = onProductClick,
                    onOpenUrl = openUrl
                )
            }

            // ② 외톨이 옷
            if (r.orphans.isNotEmpty()) {
                item { SectionTitle("어울리는 짝이 없는 옷") }
                items(r.orphans.take(5), key = { "orphan_" + it.item.id }) { o -> OrphanRow(o) }
            }

            // ③ 상황별
            item { SectionTitle("상황별 코디 · 필요한 옷") }
            items(r.situationPlans, key = { "plan_" + it.situation.name }) { plan ->
                SituationPlanCard(plan, gender, onProductClick = onProductClick,
                    onSearch = { keyword -> searchKeyword = keyword })
            }
        }
    }

    searchKeyword?.let { keyword ->
        ShopSearchSheet(keyword = keyword, onDismiss = { searchKeyword = null })
    }
}

@Composable
private fun SuggestionCard(
    rank: Int,
    suggestion: MissingItemFinder.Suggestion,
    gender: Gender?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSearch: () -> Unit,
    onProductClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val item = suggestion.item
    val shop = suggestion.shop
    val example = remember(item.id, gender) { BasicItems.exampleOf(item, gender) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CandidateThumb(item, gender, Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${rank}위 · ${MissingItemFinder.labelOf(item)}",
                            style = MaterialTheme.typography.labelMedium, color = Camel, fontWeight = FontWeight.SemiBold)
                        if (shop != null) SmallChip("판매 상품")
                    }
                    Text(item.name, style = MaterialTheme.typography.titleSmall, color = CharcoalBlack,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("있으면 코디 ${suggestion.gain}개가 더 생겨요",
                        style = MaterialTheme.typography.bodySmall, color = Brown)
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "접기" else "예시 보기", tint = MediumGray
                )
            }

            if (expanded) {
                Text("새로 생기는 코디", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack)
                suggestion.examples.forEach { outfit -> OutfitMiniRow(outfit, gender) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val productId = shop?.productId ?: example?.productId
                when {
                    productId != null -> Button(onClick = { onProductClick(productId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel), shape = RoundedCornerShape(10.dp)
                    ) { Text(if (shop != null) "상품 보기" else "비슷한 상품") }
                    shop != null -> Button(onClick = { onOpenUrl(shop.url) },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel), shape = RoundedCornerShape(10.dp)
                    ) { Text("상품 보기") }
                }
                OutlinedButton(onClick = onSearch, shape = RoundedCornerShape(10.dp)) {
                    Text("쇼핑에서 찾기", color = Brown)
                }
            }
        }
    }
}

/** 코디 예시 한 줄 — 옷 사진(기본템은 예시 사진)을 나란히 + 이름 */
@Composable
private fun OutfitMiniRow(outfit: OutfitEngine.Outfit, gender: Gender?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            outfit.items.forEach { CandidateThumb(it, gender, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), cutoutPadding = 4.dp) }
        }
        Text(outfit.items.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall,
            color = MediumGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OrphanRow(orphan: MissingItemFinder.Orphan) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClothingThumb(orphan.item, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), cutoutPadding = 4.dp)
        Column(Modifier.weight(1f)) {
            Text(orphan.item.name, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                orphan.helper?.let { "${josa(it.name, "이", "가")} 있으면 코디 ${orphan.helperCount}개" }
                    ?: "어울리는 짝을 찾지 못했어요",
                style = MaterialTheme.typography.bodySmall, color = Brown, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 상황 하나 — 코디 한 벌 + (부족하면) 필요한 옷 + 추천 상품 */
@Composable
private fun SituationPlanCard(
    plan: MissingItemFinder.SituationPlan, gender: Gender?,
    onProductClick: (String) -> Unit, onSearch: (String) -> Unit
) {
    val need = plan.need
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(CamelLight).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(plan.situation.label, style = MaterialTheme.typography.labelLarge, color = Brown, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        need == null -> "내 옷장으로 완성"
                        need.items.isNotEmpty() -> "${need.items.size}개만 더하면 완성"
                        else -> "옷이 더 필요해요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (need == null) BrownDark else MediumGray, fontWeight = FontWeight.SemiBold
                )
            }
            plan.outfit?.let { OutfitMiniRow(it, gender) }
            if (need != null && need.items.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("필요: " + need.items.joinToString(" · ") { it.name }, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onSearch(need.items.first().name) }) { Text("찾기", color = Camel) }
                }
            }
        }
    }
}

/** 옷 사진 칸 — 내 옷은 사진, 판매 상품은 상품 사진, 기본템은 같은 종류의 예시 사진(없으면 색 칸) */
@Composable
private fun CandidateThumb(item: WardrobeItem, gender: Gender?, modifier: Modifier, cutoutPadding: Dp = 8.dp) {
    val shop = BasicItems.shopOf(item)
    val example = if (shop == null && item.imageUri.isEmpty()) remember(item.id, gender) { BasicItems.exampleOf(item, gender) } else null
    when {
        shop != null -> AsyncImage(
            model = shop.image, contentDescription = item.name,
            contentScale = ContentScale.Crop, modifier = modifier.background(Color(item.colorValue))
        )
        example != null -> Box(modifier.background(Color(item.colorValue))) {
            AsyncImage(model = example.image, contentDescription = "${item.name} 예시",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text("예시", style = MaterialTheme.typography.labelSmall, color = White,
                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp))
        }
        item.imageUri.isEmpty() -> Box(
            modifier.background(Color(item.colorValue)), contentAlignment = Alignment.Center
        ) {
            // 밝은 색 칸에는 어두운 글씨, 어두운 칸에는 밝은 글씨
            val light = (item.colorValue shr 16 and 0xFF) + (item.colorValue shr 8 and 0xFF) + (item.colorValue and 0xFF) > 384
            Text(SituationLabel.short(item), style = MaterialTheme.typography.labelSmall, color = if (light) CharcoalBlack else White)
        }
        else -> ClothingThumb(item, modifier, cutoutPadding = cutoutPadding)
    }
}

private object SituationLabel {
    fun short(item: WardrobeItem): String = (item.detailType ?: item.subCategory ?: item.category).take(4)
}

@Composable
private fun SmallChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(CreamDark).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Brown)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MediumGray)
}
