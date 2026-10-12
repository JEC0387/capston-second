package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.ui.theme.*

/**
 * 옷장 코디 카드 (계획 4 — 추천).
 * 코디에 들어간 옷 사진을 모아 보여주고, 추천 이유와 👍👎 버튼을 단다.
 * [wear]를 주면 [오늘 이거 입었어요] 버튼도 단다 (누르면 오늘 착용 기록으로 저장 → 취향 +1 학습).
 *
 * 사진 배치: 옷이 4벌(아우터·상의·하의·신발)이면 왼쪽에 아우터·상의, 오른쪽에 하의·신발.
 *           3벌이면 왼쪽에 상의를 크게, 오른쪽에 하의·신발.
 *
 * @param rated null이면 아직 평가 안 함, true/false면 👍/👎를 누른 상태 (다시 누를 수 없음)
 * @param modifier 카드 너비는 부르는 쪽에서 정한다 (홈: 가로로 넘기는 260dp, 코디 화면: 화면 너비)
 */
@Composable
fun OutfitCard(
    outfit: OutfitEngine.Outfit,
    rated: Boolean?,
    onRate: (like: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    photoHeight: Dp = 200.dp,
    wear: WearToday? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // 옷 사진 모음
        val left = outfit.items.take(maxOf(1, outfit.items.size - 2))
        val right = outfit.items.drop(left.size)
        Row(
            modifier = Modifier.fillMaxWidth().height(photoHeight).background(CreamDark),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(left, right).filter { it.isNotEmpty() }.forEach { column ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    column.forEach { item ->
                        ClothingThumb(item, Modifier.weight(1f).fillMaxWidth(), cutoutPadding = 8.dp)
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CamelLight)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(outfit.style, style = MaterialTheme.typography.labelMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                }
                Text("${outfit.style} 코디", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
            }
            Text(
                outfit.items.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall, color = MediumGray,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            // 추천 이유
            outfit.reasons.forEach { reason ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = Camel)
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack)
                }
            }
            // 오늘 이거 입었어요
            wear?.let { w ->
                val worn = w.isWorn(outfit)
                OutlinedButton(
                    onClick = { w.request(outfit) },
                    enabled = !worn,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (worn) Icons.Filled.CheckCircle else Icons.Filled.Checkroom,
                        contentDescription = null, tint = if (worn) MediumGray else Camel, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (worn) "오늘 입은 코디로 기록했어요" else "오늘 이거 입었어요", color = if (worn) MediumGray else Brown)
                }
            }
            // 👍 👎
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rated != null) {
                    Text(
                        if (rated) "좋아요를 취향에 반영했어요" else "다음엔 다른 스타일도 보여드릴게요",
                        style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { onRate(true) }, enabled = rated == null) {
                    Icon(
                        if (rated == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "좋아요", tint = if (rated == true) Camel else MediumGray
                    )
                }
                IconButton(onClick = { onRate(false) }, enabled = rated == null) {
                    Icon(
                        if (rated == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "별로예요", tint = if (rated == false) Brown else MediumGray
                    )
                }
            }
        }
    }
}

/**
 * [오늘 이거 입었어요] 처리 (계획 4 — 4단계).
 * 오늘 기록이 없으면 바로 저장하고, 다른 옷으로 이미 기록했으면 바꿀지 먼저 물어본다.
 * 저장은 WearLogStore.save를 쓰므로 새로 입은 옷만 취향 +1로 학습된다.
 * 화면에서 [rememberWearToday]로 만들고, 같은 화면에 [Dialog]를 한 번 그려 준다.
 */
class WearToday(private val context: Context, private val todayLog: WearLog?) {
    private var pending by mutableStateOf<OutfitEngine.Outfit?>(null)

    fun isWorn(outfit: OutfitEngine.Outfit): Boolean =
        todayLog?.itemIds?.toSet() == outfit.items.map { it.id }.toSet()

    fun request(outfit: OutfitEngine.Outfit) {
        if (todayLog == null || todayLog.itemIds.isEmpty()) save(outfit) else pending = outfit
    }

    private fun save(outfit: OutfitEngine.Outfit) {
        WearLogStore.save(context, WearLog(WearDates.today(), outfit.items.map { it.id }, todayLog?.memo.orEmpty()))
        pending = null
    }

    @Composable
    fun Dialog() {
        val outfit = pending ?: return
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("오늘 기록을 바꿀까요?") },
            text = { Text("오늘은 이미 다른 옷으로 기록했어요. 이 코디로 바꿔 기록할까요?\n(${outfit.items.joinToString(" · ") { it.name }})") },
            confirmButton = { TextButton(onClick = { save(outfit) }) { Text("바꾸기", color = Camel) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소", color = MediumGray) } }
        )
    }
}

/** 오늘 착용 기록이 바뀔 때마다 새로 만든다 */
@Composable
fun rememberWearToday(logs: List<WearLog>): WearToday {
    val context = LocalContext.current
    val todayLog = logs.find { it.date == WearDates.today() }
    return remember(todayLog) { WearToday(context, todayLog) }
}
