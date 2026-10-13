package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

/**
 * 옷이 부족해 코디를 만들 수 없을 때 보여주는 안내 카드 (계획 4 — 추천).
 * 고정 코디로 대신하지 않고, 무엇이 모자란지 알려주고 옷 등록으로 이어준다.
 */
@Composable
fun NotEnoughClothesCard(message: String, onAddClothing: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CreamDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Checkroom, contentDescription = null, tint = Camel, modifier = Modifier.size(32.dp))
            Text("아직 코디를 만들 옷이 부족해요", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MediumGray, textAlign = TextAlign.Center)
            Button(
                onClick = onAddClothing,
                colors = ButtonDefaults.buttonColors(containerColor = Camel),
                shape = RoundedCornerShape(12.dp)
            ) { Text("옷 등록하기") }
        }
    }
}
