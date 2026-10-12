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
 * "이것만 사면 코디 완성" 화면 (계획 4 — 5단계).
 *  ① 사면 좋은 코디가 가장 많이 늘어나는 옷 TOP 3 (누르면 새로 생기는 코디 예시)
 *  ② 어울리는 짝이 없는 옷 (외톨이 옷)
 *  ③ 상황별(면접 · 데이트 · 여행 · 운동)로 필요한 옷
 * 후보 옷은 사진이 없어서 색 칸으로, 쇼핑 상품은 상품 사진으로 보여준다.
 */
@Composable
fun MissingItemScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WardrobeStore.load(context) }
    val wardrobe by WardrobeStore.items.collectAsState()

    // 후보마다 조합을 다 맞춰 보느라 잠깐 걸릴 수 있어 화면이 멈추지 않게 따로 계산한다
    var report by remember { mutableStateOf<MissingItemFinder.Report?>(null) }
    LaunchedEffect(wardrobe) {
        report = withContext(Dispatchers.Default) { MissingItemFinder.analyze(wardrobe) }
    }
    var searchKeyword by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }   // 예시를 펼친 후보 id

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
                Text(
                    "지금 옷장으로 ${r.season} 코디 ${r.goodCount}개를 만들 수 있어요",
                    style = MaterialTheme.typography.bodyMedium, color = MediumGray
                )
            }

            // ① TOP 3
            item { SectionTitle("이것만 사면 코디가 늘어나요") }
            if (r.suggestions.isEmpty()) item { Hint("지금 옷장으로 충분해요! 새로 사지 않아도 다양한 코디를 만들 수 있어요") }
            items(r.suggestions, key = { it.item.id }) { s ->
                SuggestionCard(
                    rank = r.suggestions.indexOf(s) + 1,
                    suggestion = s,
                    expanded = expanded == s.item.id,
                    onToggle = { expanded = if (expanded == s.item.id) null else s.item.id },
                    onSearch = { searchKeyword = s.item.name },
                    onOpenShop = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                )
            }

            // ② 외톨이 옷
            if (r.orphans.isNotEmpty()) {
                item { SectionTitle("어울리는 짝이 없는 옷") }
                items(r.orphans, key = { "orphan_" + it.item.id }) { o -> OrphanRow(o) }
            }

            // ③ 상황별
            item { SectionTitle("상황별로 필요한 옷") }
            if (r.situationNeeds.isEmpty()) item { Hint("면접 · 데이트 · 여행 · 운동 코디를 모두 만들 수 있어요") }
            items(r.situationNeeds, key = { "need_" + it.situation.name }) { n ->
                SituationNeedRow(n, onSearch = { keyword -> searchKeyword = keyword })
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onSearch: () -> Unit,
    onOpenShop: (String) -> Unit
) {
    val item = suggestion.item
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
                CandidateThumb(item, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f)) {
                    Text("${rank}위 · ${suggestion.shop?.let { "쇼핑 상품" } ?: "기본템"}", style = MaterialTheme.typography.labelMedium, color = Camel)
                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${josa(item.name, "이", "가")} 있으면 코디 ${suggestion.gain}개가 더 생겨요",
                        style = MaterialTheme.typography.bodySmall, color = Brown
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "접기" else "예시 보기", tint = MediumGray
                )
            }

            if (expanded) {
                Text("새로 생기는 코디 예시", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack)
                suggestion.examples.forEach { outfit -> OutfitMiniRow(outfit) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestion.shop?.let { shop ->
                    Button(
                        onClick = { onOpenShop(shop.url) },
                        colors = ButtonDefaults.buttonColors(containerColor = Camel),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("상품 보기") }
                }
                OutlinedButton(onClick = onSearch, shape = RoundedCornerShape(10.dp)) {
                    Text("쇼핑에서 찾기", color = Brown)
                }
            }
        }
    }
}

/** 코디 예시 한 줄 — 옷 사진(후보는 색 칸)을 나란히 + 이름 */
@Composable
private fun OutfitMiniRow(outfit: OutfitEngine.Outfit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            outfit.items.forEach { CandidateThumb(it, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), cutoutPadding = 4.dp) }
        }
        Text(outfit.items.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MediumGray)
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
            Text(
                "${josa(orphan.item.name, "은", "는")} 어울리는 짝이 없어요",
                style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
            )
            Text(
                orphan.helper?.let { "${josa(it.name, "이", "가")} 있으면 코디 ${orphan.helperCount}개에 쓸 수 있어요" }
                    ?: "후보 옷 중에는 잘 어울리는 짝을 찾지 못했어요",
                style = MaterialTheme.typography.bodySmall, color = Brown
            )
        }
    }
}

@Composable
private fun SituationNeedRow(need: MissingItemFinder.SituationNeed, onSearch: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(CamelLight).padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text(need.situation.label, style = MaterialTheme.typography.labelLarge, color = Brown, fontWeight = FontWeight.SemiBold) }
        Column(Modifier.weight(1f)) {
            if (need.items.isEmpty()) {
                Text("후보 옷으로는 코디를 만들기 어려워요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
            } else {
                Text(
                    "${josa(need.items.joinToString(" + ") { it.name }, "이", "가")} 있으면 코디를 만들 수 있어요",
                    style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    need.items.forEach { CandidateThumb(it, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), cutoutPadding = 2.dp) }
                }
            }
        }
        need.items.firstOrNull()?.let { first ->
            TextButton(onClick = { onSearch(first.name) }) { Text("찾기", color = Camel) }
        }
    }
}

/** 옷 사진 칸 — 내 옷은 사진, 쇼핑 상품은 상품 사진, 기본템은 색 칸 */
@Composable
private fun CandidateThumb(item: WardrobeItem, modifier: Modifier, cutoutPadding: Dp = 8.dp) {
    val shop = BasicItems.shopOf(item)
    when {
        shop != null -> AsyncImage(
            model = shop.image, contentDescription = item.name,
            contentScale = ContentScale.Crop, modifier = modifier.background(Color(item.colorValue))
        )
        item.imageUri.isEmpty() -> Box(
            modifier.background(Color(item.colorValue)), contentAlignment = Alignment.Center
        ) {
            // 밝은 색 칸에는 어두운 글씨, 어두운 칸에는 밝은 글씨
            val light = (item.colorValue shr 16 and 0xFF) + (item.colorValue shr 8 and 0xFF) + (item.colorValue and 0xFF) > 384
            Text("NEW", style = MaterialTheme.typography.labelSmall, color = if (light) CharcoalBlack else White)
        }
        else -> ClothingThumb(item, modifier, cutoutPadding = cutoutPadding)
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
