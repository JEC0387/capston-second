package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.ManualBodyMeasurements

@Composable
fun ManualBodySummary(value: ManualBodyMeasurements, saved: Boolean) {
    Card(Modifier.fillMaxWidth().testTag("analysisManualSummary")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (saved) "저장된 실측값" else "이번 실행의 실측값", style = MaterialTheme.typography.titleSmall)
            listOf("키" to value.heightCm, "어깨 너비" to value.shoulderWidthCm,
                "가슴 둘레" to value.chestCircumferenceCm, "허리 둘레" to value.waistCircumferenceCm,
                "엉덩이 둘레" to value.hipCircumferenceCm, "다리 길이" to value.legLengthCm,
                "상체 길이" to value.torsoLengthCm).forEach { (label, cm) ->
                if (cm != null) Text("$label: $cm cm")
            }
            Text("사이즈 비교와 3D 초안에 사용해요. 사진의 정면 너비·체형 판정은 별도로 유지해요.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
