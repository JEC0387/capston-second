package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun MeasuredSizeCard(profile:BodyDimensionProfile) {
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<GarmentSizeRow>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded,retry) {
        if(expanded) {
            loading=true
            try { rows=SizeCharts.load() } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { rows=null } finally { loading=false }
        }
    }
    OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.testTag("measuredSizeOpen")) {
        Text(if(expanded) "실측 사이즈 비교 닫기" else "의류 실측으로 사이즈 비교")
    }
    if(expanded) when {
        loading -> Text("상품 실측표를 확인하고 있어요.")
        rows==null -> { Text("실측표를 불러오지 못했어요. 치수와 로컬 기록은 그대로예요.");TextButton(onClick={retry++}){Text("실측표 다시 확인")} }
        else -> MeasuredSizeComparison(profile,rows!!)
    }
}

@Composable
internal fun MeasuredSizeComparison(profile:BodyDimensionProfile,rows:List<GarmentSizeRow>) {
    val context=LocalContext.current
    var preference by remember { mutableStateOf(FitPreference.REGULAR) }
    var saveFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val result=remember(profile,rows,preference){MeasuredSizeRecommendation.suggest(profile,rows,preference)}
    Column(Modifier.fillMaxWidth().testTag("measuredSizeComparison"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("실측표와 몸 치수 비교",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            FitPreference.entries.forEach { fit ->
                FilterChip(preference==fit,{preference=fit},label={Text(fit.label.removeSuffix(" 참고"))},modifier=Modifier.weight(1f))
            }
        }
        Text("선호 핏은 시범 여유량 규칙이에요. 소재·신축성·패턴을 몰라 실제 착용감이나 구매 사이즈를 보장하지 않아요.")
        if(rows.isEmpty())Text("사용 가능한 실제 실측표가 없어요. 브랜드/상품 치수를 임의로 만들지 않아요.")
        if(result.missing.isNotEmpty())Text("추가 입력 필요: ${result.missing.joinToString { it.label }}. 사진 정면 너비를 둘레로 대신하지 않아요.")
        if(rows.isNotEmpty()&&result.suggestions.isEmpty())Text("출처·단위·필수 치수 또는 여유량 조건을 만족하는 표가 없어요. 원본 상품표와 추가 치수를 확인해주세요.")
        result.suggestions.forEach { suggestion ->
            val row=suggestion.row
            HorizontalDivider()
            Text("${row.brand} · ${row.category} · 참고 사이즈 ${row.sizeLabel}",style=MaterialTheme.typography.titleSmall)
            Text(if(row.productKey==null)"브랜드표 참고 · 해당 상품과의 일치는 미확인" else "상품표 식별값: ${row.productKey}")
            suggestion.evidence.forEach { evidence ->
                fun f(value:Float)=String.format(Locale.KOREA,"%.1f",value)
                Text("${evidence.dimension.label}: 몸 ${f(evidence.bodyCm)} cm (${evidence.origin.label}) → 옷 ${f(evidence.garmentCm)} cm · 여유 ${f(evidence.easeCm)} cm")
            }
            Text("표의 ${row.measureType} 단위를 사용했어요. 둘레 항목의 단면은 ×2, 어깨 너비는 그대로 비교해요.")
            if(row.lengthCm!=null)Text("의류 총장 ${row.lengthCm} cm는 신체 상체 길이와 같은 정의가 아니어서 적합 판정에 섞지 않았어요.")
            if(row.thighCm!=null)Text("허벅지 실측 ${row.thighCm} cm는 비교할 신체 치수 입력이 없어 판정에서 제외했어요.")
            Text("출처: ${row.sourceUrl}",style=MaterialTheme.typography.bodySmall)
            Text("실제 착용 후 평가 (기본은 이 화면에서만 확인)")
            Row(Modifier.fillMaxWidth()) {
                SizeSatisfaction.entries.forEach { rating ->
                    TextButton(modifier=Modifier.weight(1f),onClick={
                        val entry=SizeFeedbackStore.feedback(suggestion,rating,System.currentTimeMillis())
                        feedbackMessage=if(!saveFeedback)"${rating.label} · 이번 화면에서만 반영했어요."
                            else if(SizeFeedbackStore.save(context,entry))"${rating.label} · 이 기기에 평가를 저장했어요."
                            else "기기에 저장하지 못했어요. 다시 확인해주세요."
                    }){Text(rating.label)}
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("평가를 이 기기에 저장",modifier=Modifier.weight(1f))
            Switch(saveFeedback,{saveFeedback=it},modifier=Modifier.testTag("sizeFeedbackSave"))
        }
        Text("켜고 평가할 때 최근 100건을 저장해요. 신체 치수·사진·원본 URL을 기록하지 않고 서버·자동 백업으로 보내지 않아요.",style=MaterialTheme.typography.bodySmall)
        feedbackMessage?.let { Text(it,modifier=Modifier.testTag("sizeFeedbackMessage")) }
    }
}
