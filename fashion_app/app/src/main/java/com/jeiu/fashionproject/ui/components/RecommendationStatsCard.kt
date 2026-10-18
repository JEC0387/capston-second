package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.RecommendationSummary
import com.jeiu.fashionproject.ui.theme.*

/**
 * AI 추천 성과 (마이페이지) — 보여 준 추천 코디 중 👍 를 누른 비율과 실제로 입은 비율.
 * 같은 숫자를 서버 /api/recommendations/stats 에서도 볼 수 있다 (AI 성능 측정표용).
 */
@Composable
fun RecommendationStatsCard(summary: RecommendationSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CreamDark).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (summary.shown == 0) {
            Text("코디 추천을 보면 추천이 얼마나 맞았는지 알려 드릴게요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("추천 코디", "${summary.shown}개")
            Stat("👍 좋아요", "${summary.percent(summary.liked)}%")
            Stat("그대로 입음", "${summary.percent(summary.worn)}%")
            Stat("일부 입음", "${summary.percent(summary.wornPartial)}%")
        }
        Text("추천한 날부터 2일 안의 착용 기록과 코디 👍👎로 계산해요 (👎 ${summary.disliked}개)",
            style = MaterialTheme.typography.labelSmall, color = MediumGray)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Brown, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MediumGray)
    }
}
