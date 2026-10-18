package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.OutfitPlan
import com.jeiu.fashionproject.data.OutfitPlanStore
import com.jeiu.fashionproject.data.SavedOutfit
import com.jeiu.fashionproject.data.SavedOutfitStore
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.ui.components.OutfitItemsRow
import com.jeiu.fashionproject.ui.components.plannedOutfit
import com.jeiu.fashionproject.ui.components.rememberOutfitBookActions
import com.jeiu.fashionproject.ui.components.rememberWearToday
import com.jeiu.fashionproject.ui.theme.*

/** 저장한 코디가 착용 기록에 몇 번 나왔는지 — 그날 입은 옷에 코디 옷이 모두 있으면 1번 */
fun timesWorn(itemIds: List<String>, logs: List<WearLog>): Pair<Int, String?> {
    val hits = logs.filter { log -> itemIds.isNotEmpty() && itemIds.all { it in log.itemIds } }
    return hits.size to hits.maxOfOrNull { it.date }
}

/**
 * 저장한 코디 (룩북) · 코디 일정 화면.
 *  - 저장한 코디: 추천 카드에서 🔖 로 저장한 코디. 오늘 입기 · 날짜 정하기 · 메모 · 삭제
 *  - 코디 일정: 날짜에 정해 둔 코디. 지난 일정은 '일정대로 입었는지'를 착용 기록과 비교해 보여 준다
 */
@Composable
fun OutfitBookScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        WearLogStore.load(context)
    }
    val items by WardrobeStore.items.collectAsState()
    val logs by WearLogStore.logs.collectAsState()
    val saved by SavedOutfitStore.items.collectAsState()
    val plans by OutfitPlanStore.plans.collectAsState()
    val byId = items.associateBy { it.id }
    val wearToday = rememberWearToday(logs)
    val book = rememberOutfitBookActions()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var noteTarget by remember { mutableStateOf<SavedOutfit?>(null) }
    val today = WearDates.today()

    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack) }
            Text("저장한 코디 · 코디 일정", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }
        TabRow(selectedTabIndex = tab, containerColor = Cream, contentColor = Brown) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("저장한 코디 ${saved.size}") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("코디 일정 ${plans.count { it.date >= today }}") })
        }

        if (tab == 0) {
            if (saved.isEmpty()) {
                Empty("코디 추천 카드의 🔖 를 누르면 여기에 모여요")
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(saved, key = { it.id }) { s ->
                    val outfitItems = s.itemIds.mapNotNull { byId[it] }
                    val (count, last) = timesWorn(s.itemIds, logs)
                    BookCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (s.style.isBlank()) "저장한 코디" else "${s.style} 코디", style = MaterialTheme.typography.titleSmall,
                                color = CharcoalBlack, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(WearDates.label(WearDates.fromMillis(s.savedAt)).substringBefore(" ("),
                                style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                        OutfitItemsRow(outfitItems)
                        if (outfitItems.size < s.itemIds.size) {
                            Text("옷장에서 지운 옷 ${s.itemIds.size - outfitItems.size}벌은 빼고 보여 드려요",
                                style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                        if (s.note.isNotBlank()) Text("\"${s.note}\"", style = MaterialTheme.typography.bodySmall, color = Brown)
                        Text(if (count == 0) "아직 입은 기록이 없어요" else "${count}번 입었어요 · 마지막 ${WearDates.ago(last!!)}",
                            style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val outfit = plannedOutfit(outfitItems, s.style)
                            val worn = outfitItems.isNotEmpty() && wearToday.isWorn(outfit)
                            OutlinedButton(onClick = { wearToday.request(outfit) }, enabled = outfitItems.isNotEmpty() && !worn,
                                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(if (worn) Icons.Filled.CheckCircle else Icons.Filled.Checkroom, contentDescription = null,
                                    tint = if (worn) MediumGray else Camel, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (worn) "오늘 입음" else "오늘 입기", color = Brown, style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(onClick = { book.requestPlan(outfitItems) }, enabled = outfitItems.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("날짜 정하기", color = Brown, style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(onClick = { noteTarget = s }) { Text("메모", color = MediumGray) }
                            TextButton(onClick = { SavedOutfitStore.remove(context, s.id) }) { Text("삭제", color = MediumGray) }
                        }
                    }
                }
            }
        } else {
            val upcoming = plans.filter { it.date >= today }
            val past = plans.filter { it.date < today }.sortedByDescending { it.date }
            if (plans.isEmpty()) {
                Empty("코디 카드의 📅 를 누르거나 저장한 코디에서 [날짜 정하기]를 누르면 여기에 모여요")
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (upcoming.isNotEmpty()) item { Section("다가오는 일정") }
                items(upcoming, key = { "u" + it.date }) { plan -> PlanCard(plan, byId, logs, today, onRemove = { OutfitPlanStore.remove(context, plan.date) }) }
                if (past.isNotEmpty()) {
                    item {
                        val withLog = past.mapNotNull { OutfitPlanStore.followed(it, logs.find { l -> l.date == it.date }) }
                        Section("지난 일정 · 일정대로 입은 날 ${withLog.count { it }} / 기록한 날 ${withLog.size}")
                    }
                    items(past, key = { "p" + it.date }) { plan -> PlanCard(plan, byId, logs, today, onRemove = { OutfitPlanStore.remove(context, plan.date) }) }
                }
            }
        }
    }

    wearToday.Dialog()
    book.Dialog()

    noteTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.note) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("코디 메모") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(100) }, singleLine = true,
                    placeholder = { Text("예: 금요일 회식용") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { SavedOutfitStore.setNote(context, target.id, text.trim()); noteTarget = null }) { Text("저장", color = Camel) } },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("취소", color = MediumGray) } }
        )
    }
}

@Composable
private fun PlanCard(plan: OutfitPlan, byId: Map<String, WardrobeItem>, logs: List<WearLog>, today: String, onRemove: () -> Unit) {
    val outfitItems = plan.itemIds.mapNotNull { byId[it] }
    BookCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = Camel, modifier = Modifier.size(18.dp))
            val whenText = when (WearDates.daysBetween(today, plan.date)) { 0 -> " · 오늘"; 1 -> " · 내일"; else -> "" }
            Text(WearDates.label(plan.date) + whenText, style = MaterialTheme.typography.titleSmall, color = CharcoalBlack,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("삭제", color = MediumGray) }
        }
        OutfitItemsRow(outfitItems, size = 64)
        if (outfitItems.size < plan.itemIds.size) {
            Text("옷장에서 지운 옷 ${plan.itemIds.size - outfitItems.size}벌은 빼고 보여 드려요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
        }
        if (plan.date < today) {
            val followed = OutfitPlanStore.followed(plan, logs.find { it.date == plan.date })
            Text(when (followed) { true -> "✓ 일정대로 입었어요"; false -> "다른 옷을 입었어요"; null -> "이 날 착용 기록이 없어요" },
                style = MaterialTheme.typography.labelSmall, color = if (followed == true) Camel else MediumGray)
        }
    }
}

@Composable
private fun BookCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Brown, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MediumGray)
    }
}
