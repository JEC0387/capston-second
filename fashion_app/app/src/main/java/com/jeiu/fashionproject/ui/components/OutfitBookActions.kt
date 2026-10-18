package com.jeiu.fashionproject.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.OutfitPlanStore
import com.jeiu.fashionproject.data.SavedOutfit
import com.jeiu.fashionproject.data.SavedOutfitStore
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.ui.theme.*

/**
 * 코디 카드의 🔖 저장 · 📅 일정 버튼 처리 (홈 · 코디 추천 · 저장한 코디 화면에서 같이 쓴다).
 * 화면에서 [rememberOutfitBookActions] 로 만들고, 같은 화면에 [Dialog] 를 한 번 그려 준다.
 */
class OutfitBookActions(private val context: Context) {
    private var planItems by mutableStateOf<List<WardrobeItem>?>(null)
    private var planDate by mutableStateOf(WearDates.today())

    fun isSaved(saved: List<SavedOutfit>, outfit: OutfitEngine.Outfit): Boolean =
        saved.any { it.id == SavedOutfit.idOf(outfit.items.map { i -> i.id }) }

    fun toggleSave(outfit: OutfitEngine.Outfit) {
        val nowSaved = SavedOutfitStore.toggle(context, outfit.items.map { it.id }, outfit.style, outfit.pattern)
        Toast.makeText(context, if (nowSaved) "코디를 저장했어요 (마이 > 저장한 코디)" else "저장을 취소했어요", Toast.LENGTH_SHORT).show()
    }

    /** 날짜 고르기 창을 연다 — [date] 를 주면 그 날짜가 먼저 골라져 있다 (5일 예보 탭) */
    fun requestPlan(items: List<WardrobeItem>, date: String? = null) {
        planDate = date?.takeIf { it >= WearDates.today() } ?: WearDates.today()
        planItems = items
    }

    @Composable
    fun Dialog() {
        val items = planItems ?: return
        val days = remember { (0..13).map { WearDates.daysAgo(-it) } }
        val existing = OutfitPlanStore.planFor(planDate)
        AlertDialog(
            onDismissRequest = { planItems = null },
            title = { Text("언제 입을까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(items.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEachIndexed { i, d ->
                            val label = when (i) { 0 -> "오늘"; 1 -> "내일"; else -> WearDates.label(d).substringBefore(" (") }
                            FilterChip(selected = planDate == d, onClick = { planDate = d }, label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CamelLight, selectedLabelColor = Brown))
                        }
                    }
                    Text(WearDates.label(planDate), style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack)
                    if (existing != null) {
                        Text("이 날 이미 정한 코디가 있어요. 이 코디로 바꿔요.", style = MaterialTheme.typography.labelSmall, color = HeartPink)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OutfitPlanStore.set(context, planDate, items.map { it.id })
                    Toast.makeText(context, "${WearDates.label(planDate)} 코디로 정했어요", Toast.LENGTH_SHORT).show()
                    planItems = null
                }) { Text("정하기", color = Camel) }
            },
            dismissButton = { TextButton(onClick = { planItems = null }) { Text("취소", color = MediumGray) } }
        )
    }
}

@Composable
fun rememberOutfitBookActions(): OutfitBookActions {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SavedOutfitStore.load(context)
        OutfitPlanStore.load(context)
    }
    return remember(context) { OutfitBookActions(context) }
}
