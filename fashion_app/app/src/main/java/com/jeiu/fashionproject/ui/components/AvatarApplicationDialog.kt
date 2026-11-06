package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import java.util.Locale

/** Preview and approval never mutate storage; caller owns the explicit save step. */
@Composable
fun AvatarApplicationDialog(previous: AvatarProfile?,candidate: AvatarProfile,onDismiss:()->Unit,onApprove:()->Unit,commitOnApprove:Boolean=false) {
    var preview by remember { mutableStateOf(false) }
    var before by remember { mutableStateOf(false) }
    val initial=previous?.parameters() ?: AvatarParameters()
    val after=candidate.parameters()
    if(preview) BodyAvatarDialog(BodyDimensions.resolve(null,null),
        configured=if(before)initial else after,thighScale=if(before)previous?.thigh ?: 1f else candidate.thigh) {preview=false}
    AlertDialog(onDismissRequest=onDismiss,title={Text("3D 반영 전후 비교")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${previous?.source?.label ?: "기본 모델"} → ${candidate.source.label}")
            Text("아래 너비는 렌더링 형상 수치이며 측정된 둘레가 아니에요. 깊이는 기본 타원 비율 추정이에요.")
            listOf("키" to (initial.heightCm to after.heightCm),"어깨 표시 너비" to (initial.shoulderCm to after.shoulderCm),
                "가슴 표시 너비" to (initial.chestWidthCm to after.chestWidthCm),"허리 표시 너비" to (initial.waistWidthCm to after.waistWidthCm),
                "힙 표시 너비" to (initial.hipWidthCm to after.hipWidthCm),"다리 표시 길이" to (initial.legCm to after.legCm),
                "상체 표시 길이" to (initial.torsoCm to after.torsoCm)).forEach { (label,pair) ->
                Text("$label: ${String.format(Locale.KOREA,"%.1f → %.1f cm",pair.first,pair.second)}")
            }
            candidate.applied.forEach { (key,value) -> Text("원본 ${key.label}: ${value.cm} cm (${value.origin.label})") }
            candidate.centimeters?.let {cm->AvatarPart.entries.forEach {part->
                Text("${part.label} ${part.unit}: ${previous?.centimeters?.get(part)?.toString() ?: "이전 cm 없음"} → ${cm[part]} cm · ${cm.values.getValue(part).origin.label}")
            }}
            after.sources.forEach {Text(it)}
            after.notes.forEach {Text(it)}
            Row {
                TextButton(onClick={before=true;preview=true}) {Text("이전 3D 보기")}
                TextButton(onClick={before=false;preview=true}) {Text("반영 후 3D 보기")}
            }
            Text(if(commitOnApprove)"확인하면 이번 cm 프로필을 이 기기에 저장해요. 취소하면 기존 저장값을 유지해요."
                else "승인은 미리보기만 바꿔요. 저장 버튼을 누르기 전에는 기존 프로필을 보존해요.")
        }
    },confirmButton={TextButton(onClick=onApprove,modifier=Modifier.testTag("avatarApplyApprove")) {Text(if(commitOnApprove)"확인하고 저장" else "확인하고 미리보기에 반영")}},
        dismissButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("avatarApplyCancel")) {Text("취소 · 기존 설정 유지")}})
}
