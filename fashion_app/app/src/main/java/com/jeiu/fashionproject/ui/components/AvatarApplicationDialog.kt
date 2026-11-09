package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import java.util.Locale

/** Display-only comparison. Approval is the only caller-owned mutation. */
@Composable
fun AvatarApplicationDialog(previous: AvatarProfile?,candidate: AvatarProfile,onDismiss:()->Unit,onApprove:()->Unit,commitOnApprove:Boolean=false) {
    var preview by remember { mutableStateOf(false) }
    var before by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val initial=previous?.parameters() ?: AvatarParameters()
    val after=candidate.parameters()
    fun number(value:Float?) = value?.let { String.format(Locale.KOREA,"%.1f",it) } ?: "없음"
    fun comparison(label:String,old:Float?,value:Float) = "$label: ${number(old)} → ${number(value)} cm"
    if(preview) BodyAvatarDialog(BodyDimensions.resolve(null,null),
        configured=if(before)initial else after,thighScale=if(before)previous?.thigh ?: 1f else candidate.thigh) {preview=false}
    // Constrain only the scrolling content, keeping both action buttons outside it.
    val contentHeight=(LocalConfiguration.current.screenHeightDp.dp * .42f).coerceAtMost(360.dp)
    AlertDialog(onDismissRequest=onDismiss,title={Text("3D 변경 확인")},text={
        Column(Modifier.heightIn(max=contentHeight).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(comparison("키",previous?.heightCm,candidate.heightCm))
            val parts=listOf(AvatarPart.SHOULDER,AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP)
            val oldWidths=listOf(initial.shoulderCm,initial.chestWidthCm,initial.waistWidthCm,initial.hipWidthCm)
            val widths=listOf(after.shoulderCm,after.chestWidthCm,after.waistWidthCm,after.hipWidthCm)
            parts.forEachIndexed { index,part ->
                val cm=candidate.centimeters
                // Never compare a legacy front width with a circumference.
                val useCm=cm!=null && (previous==null || previous.centimeters!=null)
                val label=if(useCm) "${part.label} ${part.unit}" else "${part.label} 표시 너비"
                Text(comparison(label,if(previous==null)null else if(useCm)previous.centimeters!![part] else oldWidths[index],
                    if(useCm)cm!![part] else widths[index]))
            }
            Text("3D 표시용 치수예요. 사진 너비는 실측 둘레와 달라요.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={details=!details},modifier=Modifier.testTag("avatarComparisonDetails")) { Text(if(details)"상세 접기" else "상세 보기") }
            if(details) {
                Text(comparison("다리 표시 길이",previous?.let {initial.legCm},after.legCm))
                Text(comparison("상체 표시 길이",previous?.let {initial.torsoCm},after.torsoCm))
                candidate.applied.forEach { (key,value) -> Text("원본 ${key.label}: ${number(value.cm)} cm (${value.origin.label})") }
                candidate.centimeters?.let {cm->AvatarPart.entries.forEach {part->
                    Text("${part.label} ${part.unit}: ${number(previous?.centimeters?.get(part))} → ${number(cm[part])} cm · ${cm.values.getValue(part).origin.label}")
                }}
                after.sources.forEach {Text(it)}
                after.notes.forEach {Text(it)}
                Row {
                    TextButton(onClick={before=true;preview=true}, enabled=previous!=null) {Text("이전 3D")}
                    TextButton(onClick={before=false;preview=true}) {Text("변경 후 3D")}
                }
            }
            Text(if(commitOnApprove)"적용하면 이 기기에 저장해요. 원본 실측값은 유지해요." else "미리보기에만 반영해요. 저장값은 유지해요.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=onApprove,modifier=Modifier.testTag("avatarApplyApprove")) {Text(if(commitOnApprove)"적용·저장" else "반영")}},
        dismissButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("avatarApplyCancel")) {Text("취소")}})
}
