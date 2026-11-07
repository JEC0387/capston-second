package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import java.util.Locale

@Composable
fun BodyDimensionsCard(profile: BodyDimensionProfile) {
    var showAvatar by remember { mutableStateOf(false) }
    if(showAvatar) BodyAvatarDialog(profile) { showAvatar=false }
    Card(Modifier.fillMaxWidth().testTag("bodyDimensionsCard")) {
        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("측정값 출처와 비율",style = MaterialTheme.typography.titleMedium)
            if (profile.values.isEmpty()) Text("치수가 없으면 3D 기본 비율 모델을 사용해요. 직접 입력 또는 사진 추정값은 출처를 구분해서 반영해요.")
            profile.values.forEach { (dimension,value) ->
                Text("${dimension.label}: ${String.format(Locale.KOREA,"%.1f",value.cm)} cm · ${value.origin.label}")
            }
            val height = profile[BodyDimension.HEIGHT]?.cm
            if (height != null) {
                listOf(BodyDimension.SHOULDER_WIDTH,BodyDimension.LEG_LENGTH,BodyDimension.TORSO_LENGTH).forEach { key ->
                    profile[key]?.let { value ->
                        val ratio = (value.cm / height).coerceIn(0f,1f)
                        Text("${key.label} / 키: ${String.format(Locale.KOREA,"%.1f",ratio*100)}% · ${value.origin.label}")
                        LinearProgressIndicator(progress = { ratio },modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            profile.comparisons.forEach { comparison ->
                Text("${comparison.dimension.label} 차이 (사진−직접 입력): ${String.format(Locale.KOREA,"%+.1f",comparison.differenceCm)} cm")
            }
            if (profile.comparisons.isNotEmpty()) Text("같은 정의의 치수만 비교했어요. 입력값과의 차이는 분석 정확도나 의료 평가가 아니에요.")
            profile.notes.forEach { Text(it,style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick={showAvatar=true},modifier=Modifier.testTag("openBodyAvatar")) { Text("3D 비율 아바타 보기") }
        }
    }
}
