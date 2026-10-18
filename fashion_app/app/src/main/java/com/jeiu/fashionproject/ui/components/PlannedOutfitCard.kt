package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.ui.theme.*

/** 옷 사진을 가로로 나열 (저장한 코디 · 코디 일정 · 홈 '오늘 계획한 코디') */
@Composable
fun OutfitItemsRow(items: List<WardrobeItem>, size: Int = 72) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(Modifier.width(size.dp)) {
                ClothingThumb(item = item, cutoutPadding = 6.dp, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)))
                Text(item.name, style = MaterialTheme.typography.labelSmall, color = CharcoalBlack, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 계획한 코디 옷으로 OutfitCard · [오늘 이거 입었어요]가 쓰는 코디 값을 만든다 */
fun plannedOutfit(items: List<WardrobeItem>, style: String = "계획한"): OutfitEngine.Outfit =
    OutfitEngine.Outfit(items = items, score = 0.0, reasons = emptyList(), style = style)

/**
 * 홈 '오늘 계획한 코디' — 코디 일정에 오늘 정해 둔 코디가 있을 때만 보인다.
 * @param missing 옷장에서 지운 옷 수
 */
@Composable
fun PlannedOutfitCard(
    title: String,
    items: List<WardrobeItem>,
    missing: Int,
    wear: WearToday?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = Camel, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onOpen) { Text("일정 보기", color = Camel, style = MaterialTheme.typography.bodySmall) }
            }
            OutfitItemsRow(items)
            if (missing > 0) {
                Text("옷장에서 지운 옷 ${missing}벌은 빼고 보여 드려요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
            }
            if (wear != null && items.isNotEmpty()) {
                val outfit = plannedOutfit(items)
                val worn = wear.isWorn(outfit)
                OutlinedButton(onClick = { wear.request(outfit) }, enabled = !worn, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(if (worn) Icons.Filled.CheckCircle else Icons.Filled.Checkroom, contentDescription = null,
                        tint = if (worn) MediumGray else Camel, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (worn) "오늘 입은 코디로 기록했어요" else "오늘 이거 입었어요", color = if (worn) MediumGray else Brown)
                }
            }
        }
    }
}
