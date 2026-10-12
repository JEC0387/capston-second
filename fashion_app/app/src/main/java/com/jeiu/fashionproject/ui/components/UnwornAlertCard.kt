package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer

/**
 * "3개월 넘게 안 입은 옷" 알림 카드 (계획 3 — 5단계, 앱 안에서만 표시).
 * 카드를 누르면 목록 창이 올라오고, 옷마다 [정리하기(삭제)] / [계속 보관(90일 다시 세기)]를 고른다.
 * 해당하는 옷이 없으면 아무것도 그리지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnwornAlertCard(
    result: WearAnalyzer.UnwornResult,
    onDelete: (WardrobeItem) -> Unit,
    onKeep: (WardrobeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (result.unworn.isEmpty()) return
    var showList by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WardrobeItem?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SalmonLight)
            .clickable { showList = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = BrownDark, modifier = Modifier.size(20.dp))
        Text(
            "3개월 넘게 안 입은 옷이 ${result.unworn.size}벌 있어요",
            style = MaterialTheme.typography.bodyMedium, color = BrownDark, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "목록 보기", tint = BrownDark)
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = White) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "3개월 넘게 안 입은 옷 ${result.unworn.size}벌",
                    style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold
                )
                Text(
                    "정리하거나, 계속 둘 옷은 [계속 보관]을 눌러주세요. 보관한 옷은 90일 뒤에 다시 알려드려요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(result.unworn, key = { it.item.id }) { u ->
                        UnwornRow(u, onDelete = { confirmDelete = u.item }, onKeep = { onKeep(u.item) })
                    }
                    if (result.excludedBySeason.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CreamDark)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
                                Text(
                                    "지금 계절(${result.season})에 안 맞아 제외한 옷 ${result.excludedBySeason.size}벌: " +
                                        result.excludedBySeason.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall, color = MediumGray
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("옷 정리하기") },
            text = { Text("'${item.name}'을(를) 옷장에서 지울까요?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(item) }) { Text("삭제", color = HeartPink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("취소", color = MediumGray) }
            }
        )
    }
}

@Composable
private fun UnwornRow(u: WearAnalyzer.Unworn, onDelete: () -> Unit, onKeep: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ClothingThumb(
            item = u.item, cutoutPadding = 6.dp,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(u.item.name, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (u.lastWorn != null) "마지막 착용 ${WearDates.ago(u.lastWorn)}"
                else "등록 후 한 번도 안 입었어요 (${WearDates.ago(u.addedDate)} 등록)",
                style = MaterialTheme.typography.bodySmall, color = MediumGray
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("정리하기", style = MaterialTheme.typography.labelMedium, color = MediumGray) }
                Button(
                    onClick = onKeep,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CamelLight, contentColor = Brown),
                    modifier = Modifier.height(34.dp)
                ) { Text("계속 보관", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
