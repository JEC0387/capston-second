package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.recommend.ForecastAlerts
import com.jeiu.fashionproject.ui.theme.*

/**
 * 5일 날씨 띠 (계획 4 — 3단계).
 * 날짜마다 요일 · 날씨 아이콘 · 최저~최고 기온 · 비 올 확률을 보여주고, 누르면 그날을 고른다.
 */
@Composable
fun ForecastStrip(
    days: List<DayForecast>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, day ->
            val isSelected = i == selected
            val textColor = if (isSelected) White else CharcoalBlack
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Camel else White)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    ForecastAlerts.shortLabel(day.date),
                    style = MaterialTheme.typography.labelLarge, color = textColor, fontWeight = FontWeight.SemiBold
                )
                Icon(
                    weatherIcon(day), contentDescription = day.condition,
                    tint = if (isSelected) White else Camel, modifier = Modifier.size(20.dp)
                )
                Text("${day.minTemp}~${day.maxTemp}°", style = MaterialTheme.typography.bodySmall, color = textColor)
                Text(
                    if (day.rainChance >= 30) "☔${day.rainChance}%" else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) White else MediumGray
                )
            }
        }
    }
}

/** 대표 날씨 → 아이콘 (비 올 확률이 높으면 우산) */
fun weatherIcon(day: DayForecast): ImageVector = when {
    day.conditionId in 200..299 -> Icons.Filled.Thunderstorm
    day.conditionId in 600..699 -> Icons.Filled.AcUnit
    day.conditionId in 300..599 || day.rainChance >= DayForecast.RAINY_CHANCE -> Icons.Filled.Umbrella
    day.conditionId in 801..899 || day.conditionId in 700..799 -> Icons.Filled.Cloud
    else -> Icons.Filled.WbSunny
}
