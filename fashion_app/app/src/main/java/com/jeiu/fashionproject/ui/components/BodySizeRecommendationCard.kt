package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.SizeCharts
import com.jeiu.fashionproject.recommend.GarmentSizeRow
import com.jeiu.fashionproject.recommend.SizeRecommendationState
import com.jeiu.fashionproject.ui.theme.CharcoalBlack
import com.jeiu.fashionproject.ui.theme.MediumGray
import com.jeiu.fashionproject.ui.theme.White
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import java.util.Locale

@Composable
fun BodySizeRecommendationCard(measurements: BodyMeasurements) {
    var loaded by remember(measurements.measuredAt) { mutableStateOf(false) }
    var rows by remember(measurements.measuredAt) { mutableStateOf<List<GarmentSizeRow>?>(null) }
    LaunchedEffect(measurements.measuredAt) {
        rows = SizeCharts.load()
        loaded = true
    }
    val display = if (loaded) SizeRecommendationState.from(measurements, rows) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("체형 기반 사이즈 추천", style = MaterialTheme.typography.titleMedium,
                color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
            when {
                display == null -> Text("브랜드 사이즈표를 불러오는 중이에요.", color = MediumGray)
                display == SizeRecommendationState.Unavailable -> Text("사이즈표 정보를 불러오지 못했습니다.", color = MediumGray)
                display == SizeRecommendationState.NoCharts -> Text("추천에 사용할 상품 실측 정보가 없습니다.", color = MediumGray)
                display == SizeRecommendationState.MissingBody -> Text("측정값이 없어 사이즈를 추천할 수 없어요.", color = MediumGray)
                display == SizeRecommendationState.NoMatch -> Text("비교 가능한 상의·아우터 어깨 단면 치수가 없어 추천할 수 없어요.", color = MediumGray)
                display is SizeRecommendationState.Ready -> display.suggestions.forEach { suggestion ->
                    Text("${suggestion.brand} · ${suggestion.category}", color = CharcoalBlack)
                    Text("추천 사이즈: ${suggestion.sizeLabel} (어깨 기준)",
                        color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                    Text("사진 기반 추정 어깨 너비: ${oneDecimal(measurements.shoulderWidthCm)} cm",
                        color = CharcoalBlack, style = MaterialTheme.typography.bodySmall)
                    Text("${suggestion.sizeLabel} 의류 어깨 단면: ${oneDecimal(suggestion.garmentShoulderCm)} cm",
                        color = CharcoalBlack, style = MaterialTheme.typography.bodySmall)
                    Text("어깨 여유량: +${oneDecimal(suggestion.shoulderEaseCm)} cm",
                        color = CharcoalBlack, style = MaterialTheme.typography.bodySmall)
                    Text("예상 어깨 핏: ${suggestion.expectedShoulderFit}",
                        color = CharcoalBlack, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("정면 사진과 입력 키로 얻은 추정값이에요. 어깨 단면이 추정 어깨 너비보다 2 cm 이상 큰 사이즈 중 가장 작은 것을 고르는 시범 기준이에요. 가슴·허리·엉덩이 둘레와 전체 착용 핏은 판단할 수 없어요. 상품별 패턴과 소재에 따라 실제 핏이 달라지므로 구매 전 해당 상품의 실측표를 확인해 주세요.",
                color = MediumGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun oneDecimal(value: Float): String = String.format(Locale.KOREA, "%.1f", value)
