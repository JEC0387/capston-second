package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.AvatarProfile
import com.jeiu.fashionproject.data.BodyDimension
import com.jeiu.fashionproject.recommend.AvatarBodyAnalysis
import com.jeiu.fashionproject.recommend.CircumferenceShape
import com.jeiu.fashionproject.recommend.AvatarAnalysisPath
import com.jeiu.fashionproject.recommend.AvatarModelAnalysis
import com.jeiu.fashionproject.recommend.ModelShape
import com.jeiu.fashionproject.ui.theme.*
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun AvatarBodyResultCard(profile: AvatarProfile, onEdit: () -> Unit) {
    val path = remember(profile) { AvatarModelAnalysis.path(profile) }
    if (path != AvatarAnalysisPath.MEASURED) {
        AvatarModelResultCard(profile, onEdit)
        return
    }
    val result = remember(profile) { AvatarBodyAnalysis.analyze(profile) }
    var details by rememberSaveable { mutableStateOf(false) }
    fun number(value: Float): String = String.format(Locale.KOREA, "%.1f", value)
    val topFit = when (result.shape) {
        CircumferenceShape.CHEST_DOMINANT -> "가슴과 어깨에 여유가 있는 상의부터 비교해 보세요."
        CircumferenceShape.DEFINED_WAIST -> "허리선이 있는 상의와 기본핏을 비교해 보세요."
        else -> "어깨선이 맞는 기본핏부터 비교해 보세요."
    }
    val bottomFit = when (result.shape) {
        CircumferenceShape.HIP_DOMINANT -> "엉덩이에 여유가 있는 스트레이트핏부터 비교해 보세요."
        CircumferenceShape.DEFINED_WAIST -> "허리와 엉덩이에 맞는 조절형 하의를 비교해 보세요."
        else -> "허리와 엉덩이 실측을 기준으로 기본핏부터 비교해 보세요."
    }

    Card(Modifier.fillMaxWidth().testTag("avatarBodyResult"), shape = FashionCardShape,
        colors = CardDefaults.cardColors(containerColor = CreamDark)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("3D 입력 기반 분석", style = MaterialTheme.typography.labelLarge, color = FashionMuted)
            Text("기기에 저장한 3D 치수 · 직접 잰 값 사용", style = MaterialTheme.typography.bodySmall, color = FashionMuted)
            Text(result.shape?.label ?: if (result.missing.isEmpty()) "체형 유형 판정 보류" else "체형 분석에 필요한 치수가 있어요",
                style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
            if (result.missing.isNotEmpty()) {
                Text("치수 수정에서 ${result.missing.joinToString { it.label }}를 입력해 주세요.")
            }
            result.chestHipDifference?.let { difference ->
                val evidence = when {
                    difference > 0 -> "가슴 둘레가 엉덩이보다 ${number(difference)} cm 커요."
                    difference < 0 -> "엉덩이 둘레가 가슴보다 ${number(abs(difference))} cm 커요."
                    else -> "가슴과 엉덩이 둘레가 같아요."
                }
                Text(evidence, modifier = Modifier.testTag("avatarBodyEvidence"))
            }
            result.waistDifferencePercent?.let { difference ->
                Text(if (difference == 0f) "허리 둘레가 가슴·엉덩이 평균과 같아요."
                    else "허리 둘레가 가슴·엉덩이 평균보다 ${number(abs(difference))}% ${if (difference > 0) "작아요." else "커요."}")
                if (result.shape == null) Text("기준에 가까워 치수 차이만 보여드려요.", color = FashionMuted)
            }
            Text("추천 핏", style = MaterialTheme.typography.titleMedium)
            val hasTop = result.measurements[BodyDimension.SHOULDER_WIDTH] != null ||
                result.measurements[BodyDimension.CHEST_CIRCUMFERENCE] != null
            val hasBottom = result.measurements[BodyDimension.WAIST_CIRCUMFERENCE] != null &&
                result.measurements[BodyDimension.HIP_CIRCUMFERENCE] != null
            Text("상의 · " + if (hasTop) topFit else "어깨 너비와 가슴 둘레를 입력하면 상품 실측표와 비교할 수 있어요.")
            Text("하의 · " + if (hasBottom) bottomFit else "허리와 엉덩이 둘레를 입력해 상품 실측을 비교해 보세요.")
            Text("사이즈 참고", style = MaterialTheme.typography.titleMedium)
            Text("상품 실측표와 비교해 주세요. 소재·신축성에 따라 달라요.", color = FashionMuted)
            OutlinedButton({ details = !details }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("avatarBodyDetails")) {
                Text(if (details) "상세 신체 치수 접기" else "상세 신체 치수")
            }
            if (details) {
                Text("키 ${number(profile.heightCm)} cm · 3D 설정에서 입력", color = FashionMuted)
                result.measurements.values.forEach { (dimension, value) ->
                    if (dimension != BodyDimension.HEIGHT) Text("${dimension.label} ${number(value.cm)} cm · 직접 잰 값")
                }
                if (result.measurements.values.isNotEmpty()) MeasuredSizeCard(result.measurements)
            }
            OutlinedButton(onEdit, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("avatarBodyEdit")) {
                Text("치수 수정하기")
            }

        }
    }
}

@Composable
private fun AvatarModelResultCard(profile: AvatarProfile, onEdit: () -> Unit) {
    val result = remember(profile) { AvatarModelAnalysis.analyze(profile) }
    var details by rememberSaveable { mutableStateOf(false) }
    fun percent(ratio: Float) = String.format(Locale.KOREA, "%.1f", ratio * 100f)
    Card(Modifier.fillMaxWidth().testTag("avatarBodyResult"), shape = FashionCardShape,
        colors = CardDefaults.cardColors(containerColor = CreamDark)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("3D 모델 기반 추정", style = MaterialTheme.typography.labelLarge,
                color = FashionMuted, modifier = Modifier.testTag("avatarModelSource"))
            if (result == null) {
                Text("저장한 체형을 다시 확인해 주세요.")
            } else {
                val dimension = if (result.usesModelCircumferences) "둘레" else "너비"
                Text(result.shape.label, style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
                Text("저장한 모델의 비율을 살펴본 결과예요. 실제 신체 측정은 아니에요.", color = FashionMuted)
                Text("모델의 가슴 ${dimension}는 엉덩이 ${dimension}의 ${percent(result.chestToHip)}%예요.",
                    modifier = Modifier.testTag("avatarModelEvidence"))
                Text("모델의 허리 ${dimension}는 가슴·엉덩이 평균의 ${percent(result.waistToTorso)}%예요.",
                    modifier = Modifier.testTag("avatarModelWaistEvidence"))
                Text("추천 핏", style = MaterialTheme.typography.titleMedium)
                Text("상의 · " + when (result.shape) {
                    ModelShape.UPPER_EMPHASIS -> "가슴과 어깨가 편안한 기본핏을 비교해 보세요."
                    ModelShape.DEFINED_WAIST -> "허리선이 있는 핏과 기본핏을 비교해 보세요."
                    else -> "어깨선이 자연스러운 기본핏을 비교해 보세요."
                })
                Text("하의 · " + when (result.shape) {
                    ModelShape.LOWER_EMPHASIS -> "엉덩이에 여유가 있는 스트레이트핏을 비교해 보세요."
                    else -> "허리 조절이 가능한 스트레이트핏을 비교해 보세요."
                })
                Text("사이즈는 직접 잰 치수와 상품 실측표로 확인해 주세요.", color = FashionMuted)
                OutlinedButton({ details = !details }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("avatarModelDetails")) { Text(if (details) "모델 비율 접기" else "모델 비율 보기") }
                if (details) {
                    Text("모델의 어깨 너비 / 엉덩이 너비 · ${percent(result.shoulderToHip)}%",
                        modifier = Modifier.testTag("avatarModelShoulderEvidence"))
                    Text("모델의 다리 길이 / 키 · ${percent(result.legToHeight)}%")
                    result.thighToHip?.let { Text("모델의 허벅지 둘레 / 엉덩이 둘레 · ${percent(it)}%") }
                }
            }
            OutlinedButton(onEdit, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("avatarBodyEdit")) {
                Text("치수 수정하기")
            }
        }
    }
}
