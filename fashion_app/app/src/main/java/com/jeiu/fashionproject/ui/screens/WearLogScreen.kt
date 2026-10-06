package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.ClothingCategories
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.ui.components.ClothingThumb
import com.jeiu.fashionproject.ui.theme.*
import java.util.Calendar
import java.util.TimeZone

/**
 * 오늘 입은 옷 기록 화면 (계획 3 — 4단계).
 * 날짜(기본 오늘) → 입은 옷 여러 벌 선택 → 한 줄 메모 → 저장.
 * 이미 기록이 있는 날짜를 고르면 그날 입은 옷이 선택된 상태로 열리고, 저장하면 그날 기록을 고친다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearLogScreen(
    initialDate: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WardrobeStore.load(context)
        WearLogStore.load(context)
    }
    val items by WardrobeStore.items.collectAsState()
    val logs by WearLogStore.logs.collectAsState()

    var date by remember { mutableStateOf(initialDate ?: WearDates.today()) }
    val selected = remember { mutableStateListOf<String>() }
    var memo by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("전체") }
    var showDatePicker by remember { mutableStateOf(false) }

    val existing = logs.find { it.date == date }
    // 날짜를 바꾸면 그날 기록을 불러온다 (없으면 빈 상태)
    LaunchedEffect(date) {
        val log = WearLogStore.logFor(date)
        val alive = WardrobeStore.items.value.map { it.id }.toSet()   // 지운 옷은 빼고
        selected.clear()
        log?.itemIds?.let { ids -> selected.addAll(ids.filter { it in alive }) }
        memo = log?.memo ?: ""
    }

    val shown = if (category == "전체") items else items.filter { it.category == category }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("입은 옷 기록", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
        }

        // 날짜
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White)
                .clickable { showDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Camel)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    WearDates.label(date) + if (date == WearDates.today()) " · 오늘" else "",
                    style = MaterialTheme.typography.titleSmall, color = CharcoalBlack, fontWeight = FontWeight.SemiBold
                )
                if (existing != null) {
                    Text("이미 기록한 날이에요 — 저장하면 수정돼요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
                }
            }
            Text("변경", style = MaterialTheme.typography.labelLarge, color = Camel)
        }

        // 카테고리 필터
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (listOf("전체") + ClothingCategories.categories).forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Camel, selectedLabelColor = White)
                )
            }
        }

        // 옷 선택 그리드
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(shown, key = { it.id }) { item ->
                val isSelected = item.id in selected
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            if (isSelected) BorderStroke(2.dp, Camel) else BorderStroke(1.dp, LightGray),
                            RoundedCornerShape(12.dp)
                        )
                        .background(White)
                        .clickable { if (isSelected) selected.remove(item.id) else selected.add(item.id) }
                ) {
                    ClothingThumb(
                        item = item,
                        cutoutPadding = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Camel),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "선택됨", tint = White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CharcoalBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 메모 + 저장
        Column(
            modifier = Modifier.fillMaxWidth().background(White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("한 줄 메모 (선택)") },
                placeholder = { Text("예: 발표날, 비 온 날") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Camel, focusedLabelColor = Camel)
            )
            val canDelete = selected.isEmpty() && existing != null
            Button(
                onClick = {
                    WearLogStore.save(context, WearLog(date, selected.toList(), memo.trim()))
                    onSaved()
                },
                enabled = selected.isNotEmpty() || canDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Camel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    when {
                        selected.isNotEmpty() -> "${selected.size}벌 기록하기"
                        canDelete -> "이 날 기록 지우기"
                        else -> "입은 옷을 골라주세요"
                    }
                )
            }
        }
    }

    if (showDatePicker) {
        // DatePicker는 UTC 자정 기준 밀리초를 쓴다
        val utc = TimeZone.getTimeZone("UTC")
        fun toUtcMillis(d: String): Long {
            val c = WearDates.toCalendar(d)
            return Calendar.getInstance(utc).apply {
                clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        val todayMillis = toUtcMillis(WearDates.today())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = toUtcMillis(date),
            selectableDates = object : SelectableDates {
                // 앞으로 올 날짜는 기록할 수 없다
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance(utc).apply { timeInMillis = ms }
                        date = WearDates.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                }) { Text("확인", color = Camel) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소", color = MediumGray) }
            }
        ) {
            DatePicker(
                state = pickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Camel,
                    todayDateBorderColor = Camel,
                    todayContentColor = Camel
                )
            )
        }
    }
}
