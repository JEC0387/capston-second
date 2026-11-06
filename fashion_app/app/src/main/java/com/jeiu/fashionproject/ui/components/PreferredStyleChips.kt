package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.recommend.PreferredStyles
import com.jeiu.fashionproject.ui.theme.Camel
import com.jeiu.fashionproject.ui.theme.CharcoalBlack
import com.jeiu.fashionproject.ui.theme.CreamDark
import com.jeiu.fashionproject.ui.theme.White

/**
 * 선호 스타일 8개 선택 칩 — 온보딩 첫 화면과 마이페이지 [변경]에서 같이 쓴다. 여러 개 고를 수 있다.
 * @param perRow 한 줄에 놓을 칩 수 (폭이 좁은 대화상자에서는 3)
 */
@Composable
fun PreferredStyleChips(selected: List<String>, perRow: Int = 4, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreferredStyles.OPTIONS.chunked(perRow).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { style ->
                    FilterChip(
                        selected = style in PreferredStyles.normalize(selected),
                        onClick = { onToggle(style) },
                        label = { Text(style) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Camel,
                            selectedLabelColor = White,
                            containerColor = CreamDark,
                            labelColor = CharcoalBlack
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}
