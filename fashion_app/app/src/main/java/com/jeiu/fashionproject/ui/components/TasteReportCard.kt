package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.TasteScores
import com.jeiu.fashionproject.recommend.josa
import com.jeiu.fashionproject.ui.theme.*

/**
 * 내 취향 리포트 (계획 4 — 4단계, 마이페이지).
 * 👍👎 · 취향 카드 · 착용 기록으로 쌓인 점수에서 좋아하는 색 · 자주 고르는 종류 · 스타일 · 색 조합 방식을 보여준다.
 *
 * @param styleScores 스타일 점수 (StylePreferenceStore — 서버와 동기화)
 * @param styleRatedCount 스타일 평가 횟수
 */
@Composable
fun TasteReportCard(
    styleScores: Map<String, Int>,
    styleRatedCount: Int,
    taste: TasteScores,
    modifier: Modifier = Modifier
) {
    val colors = taste.top(taste.colors, 2)
    val kinds = taste.top(taste.kinds, 2)
    val style = taste.top(styleScores, 1).firstOrNull()
    val pattern = taste.top(taste.patterns, 1).firstOrNull()

    val lines = buildList {
        if (colors.isNotEmpty()) add("🎨" to "${josa(colors.joinToString(" · "), "을", "를")} 좋아해요")
        if (kinds.isNotEmpty()) add("👕" to "${josa(kinds.joinToString(" · "), "을", "를")} 자주 골라요")
        val mood = listOfNotNull(style?.let { "$it 스타일" }, pattern?.let { if (it.endsWith("조합")) it else "$it 조합" })
        if (mood.isNotEmpty()) add("✨" to mood.joinToString(" · "))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CreamDark)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (lines.isEmpty()) {
            Text(
                "코디에 👍👎를 누르거나 입은 옷을 기록하면 취향을 알려 드릴게요",
                style = MaterialTheme.typography.bodySmall, color = MediumGray
            )
        } else {
            lines.forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(icon, style = MaterialTheme.typography.bodyMedium)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Brown, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "평가 ${maxOf(taste.ratedCount, styleRatedCount)}회 · 착용 기록 ${taste.wornCount}회 기준",
                style = MaterialTheme.typography.labelSmall, color = MediumGray
            )
        }
    }
}
