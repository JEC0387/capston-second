package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer
import java.util.Calendar

/**
 * 착용 기록 달력 + 통계 화면 (계획 3 — 4단계).
 * 기록 있는 날에 점 표시 → 날짜를 누르면 그날 코디 → 아래에 이번 달 기록 수와 가장 많이 입은 옷 TOP 5.
 */
@Composable
fun WearCalendarScreen(
    onBack: () -> Unit,
    onRecord: (date: String) -> Unit     // 그날 기록 쓰기/수정 화면으로
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        WearLogStore.load(context)
    }
    val items by WardrobeStore.items.collectAsState()
    val logs by WearLogStore.logs.collectAsState()
    val itemsById = items.associateBy { it.id }

    val today = WearDates.today()
    var selectedDate by rememberSaveable { mutableStateOf(today) }
    // 보고 있는 달 (1일 기준)
    var year by rememberSaveable { mutableIntStateOf(WearDates.toCalendar(today).get(Calendar.YEAR)) }
    var month0 by rememberSaveable { mutableIntStateOf(WearDates.toCalendar(today).get(Calendar.MONTH)) }
    var confirmDelete by remember { mutableStateOf(false) }

    val loggedDates = logs.map { it.date }.toSet()
    val yearMonth = "%04d-%02d".format(year, month0 + 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("착용 기록", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        // ── 달력 ─────────────────────────────────────────────
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (month0 == 0) { month0 = 11; year-- } else month0--
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = Brown) }
                    Text(
                        "${year}년 ${month0 + 1}월",
                        style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (month0 == 11) { month0 = 0; year++ } else month0++
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = Brown) }
                }

                // 요일
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                        Text(
                            d, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (i) { 0 -> HeartPink; 6 -> StyleClassic; else -> MediumGray }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // 날짜 칸 (7칸 × 최대 6줄)
                val first = Calendar.getInstance().apply { clear(); set(year, month0, 1) }
                val offset = first.get(Calendar.DAY_OF_WEEK) - 1          // 1일 앞의 빈칸 수
                val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
                val cells = List(offset) { null } + (1..daysInMonth).map { it }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val date = WearDates.of(year, month0, day)
                                    DayCell(
                                        day = day,
                                        isSelected = date == selectedDate,
                                        isToday = date == today,
                                        hasLog = date in loggedDates,
                                        onClick = { selectedDate = date }
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // ── 고른 날의 코디 ────────────────────────────────────
        val log = logs.find { it.date == selectedDate }
        val worn = log?.itemIds?.mapNotNull { itemsById[it] }.orEmpty()
        Card(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(WearDates.label(selectedDate), style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                if (log == null) {
                    Text("이 날은 기록이 없어요", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
                    if (selectedDate <= today) {
                        Button(
                            onClick = { onRecord(selectedDate) },
                            colors = ButtonDefaults.buttonColors(containerColor = Camel),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("이 날 입은 옷 기록하기") }
                    }
                } else {
                    if (log.memo.isNotBlank()) {
                        Text("\"${log.memo}\"", style = MaterialTheme.typography.bodyMedium, color = Brown)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        worn.forEach { item ->
                            Column(modifier = Modifier.width(80.dp)) {
                                ClothingThumb(
                                    item = item, cutoutPadding = 6.dp,
                                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Text(
                                    item.name, style = MaterialTheme.typography.labelSmall, color = CharcoalBlack,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    if (worn.size < log.itemIds.size) {
                        Text("옷장에서 지운 옷 ${log.itemIds.size - worn.size}벌은 표시하지 않아요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onRecord(selectedDate) }, shape = RoundedCornerShape(12.dp)) {
                            Text("수정", color = Brown)
                        }
                        OutlinedButton(onClick = { confirmDelete = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("삭제", color = MediumGray)
                        }
                    }
                }
            }
        }

        // ── 통계 ─────────────────────────────────────────────
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${month0 + 1}월에 ${WearAnalyzer.daysLoggedIn(logs, yearMonth)}일 기록했어요",
                    style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = LightGray)
                Text("가장 많이 입은 옷 TOP 5", style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                val top = WearAnalyzer.topWorn(logs, items)
                if (top.isEmpty()) {
                    Text("아직 기록이 없어요. 옷장에서 [오늘 입은 옷]을 눌러 기록해 보세요.", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                }
                top.forEachIndexed { rank, (item, stat) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${rank + 1}", style = MaterialTheme.typography.titleMedium, color = Camel, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        ClothingThumb(item = item, cutoutPadding = 4.dp, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            stat.lastWorn?.let {
                                Text("마지막 착용 ${WearDates.ago(it)}", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                        }
                        Text("${stat.count}회", style = MaterialTheme.typography.titleSmall, color = Brown, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("기록 삭제") },
            text = { Text("${WearDates.label(selectedDate)} 기록을 지울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    WearLogStore.delete(context, selectedDate)
                    confirmDelete = false
                }) { Text("삭제", color = HeartPink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소", color = MediumGray) }
            }
        )
    }
}

@Composable
private fun DayCell(day: Int, isSelected: Boolean, isToday: Boolean, hasLog: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isSelected) Camel else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, Camel, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$day", style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) White else CharcoalBlack,
                fontWeight = if (hasLog) FontWeight.Bold else FontWeight.Normal
            )
        }
        // 기록 있는 날 점
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasLog) Camel else Color.Transparent)
        )
    }
}
