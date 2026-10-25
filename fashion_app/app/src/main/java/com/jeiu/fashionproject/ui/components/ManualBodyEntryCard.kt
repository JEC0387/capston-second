package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import com.jeiu.fashionproject.data.BodyDimensions
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements
import java.util.Locale

/** User-entered measurements stay distinct from the photo classifier and server records. */
@Composable
fun ManualBodyEntryCard(photo: BodyMeasurements?, onUseHeight: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ManualBodyStore.load(context) }
    val current by ManualBodyStore.current.collectAsState()
    val saved by ManualBodyStore.saved.collectAsState()
    val storeIssue by ManualBodyStore.issue.collectAsState()
    var defaultAvatar by remember { mutableStateOf(false) }
    if (defaultAvatar) BodyAvatarDialog(BodyDimensions.resolve(null,null)) { defaultAvatar=false }
    var editing by remember { mutableStateOf(false) }
    var saveOnDevice by remember(saved) { mutableStateOf(saved) }
    val fields = remember(current) {
        mutableStateListOf(
            current?.heightCm?.toString().orEmpty(), current?.shoulderWidthCm?.toString().orEmpty(),
            current?.chestCircumferenceCm?.toString().orEmpty(),
            current?.waistCircumferenceCm?.toString().orEmpty(),
            current?.hipCircumferenceCm?.toString().orEmpty(),
            current?.legLengthCm?.toString().orEmpty(),
            current?.torsoLengthCm?.toString().orEmpty()
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    val names = listOf("키", "어깨 너비", "가슴 둘레", "허리 둘레", "엉덩이 둘레", "다리 길이 (고관절~발목)", "상체 길이 (어깨~고관절)")
    Card(Modifier.fillMaxWidth().testTag("manualBodyCard")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("신체 치수 직접 입력", style = MaterialTheme.typography.titleMedium)
            storeIssue?.let { Text(it,style = MaterialTheme.typography.bodySmall) }
            Text("줄자로 잰 둘레와 사진에서 추정한 정면 너비는 다른 값이에요. 입력값으로 사진 체형 판정을 바꾸지 않아요.",
                style = MaterialTheme.typography.bodySmall)
            current?.let { value ->
                Text(if (saved) "기기에 저장된 입력값" else "이번 앱 실행 중 입력값", style = MaterialTheme.typography.labelMedium)
                listOf(value.heightCm, value.shoulderWidthCm, value.chestCircumferenceCm,
                    value.waistCircumferenceCm, value.hipCircumferenceCm, value.legLengthCm,
                    value.torsoLengthCm).forEachIndexed { index, number ->
                    if (number != null) Text("${names[index]}: ${String.format(Locale.KOREA, "%.1f", number)} cm")
                }
                if (photo != null && value.shoulderWidthCm != null) {
                    Text("어깨 너비 비교: 직접 입력 ${String.format(Locale.KOREA, "%.1f", value.shoulderWidthCm)} cm · 사진 추정 ${String.format(Locale.KOREA, "%.1f", photo.shoulderWidthCm)} cm",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { editing = !editing }, modifier = Modifier.testTag("manualBodyEdit")) {
                Text(if (editing) "입력 닫기" else if (current == null) "치수 입력하기" else "치수 수정하기")
            }
                if (current == null && photo == null) TextButton(onClick={defaultAvatar=true}) { Text("3D 기본 모델") }
            }
            if (editing) {
                names.forEachIndexed { index, label ->
                    OutlinedTextField(
                        value = fields[index],
                        onValueChange = { if (it.matches(Regex("[0-9]{0,3}(\\.[0-9]?)?"))) fields[index] = it },
                        label = { Text("$label (cm)${if (index == 0) " · 필수" else ""}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("manualBodyField$index")
                    )
                }
                Text("키와 치수 한 개 이상을 입력하세요. 다리·상체 길이를 모두 입력하면 기존 코디 비율 참고에 사용할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이 기기에 입력값 저장", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = saveOnDevice, onCheckedChange = { saveOnDevice = it },
                        modifier = Modifier.testTag("manualBodySaveToggle"))
                }
                Text("저장을 끄면 앱 종료 후 복원되지 않아요. 서버로 전송하지 않으며 자동 백업에서도 제외해요.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val numbers = fields.map { if (it.isBlank()) null else it.toFloatOrNull() }
                    val value = numbers[0]?.let { height -> ManualBodyMeasurements(
                        height, numbers[1], numbers[2], numbers[3], numbers[4],
                        numbers[5], numbers[6], System.currentTimeMillis()) }
                    if (value == null || !value.isValid()) message = "입력 범위를 확인해주세요. 키 100~230 cm, 어깨 15~75 cm, 둘레 40~200 cm이며 다리·상체 길이는 키와 비례해야 해요."
                    else if (!ManualBodyStore.update(context, value, saveOnDevice)) message = "기기에 입력값을 저장하지 못했어요. 다시 시도해주세요."
                    else {
                        onUseHeight(fields[0])
                        editing = false
                        message = if (saveOnDevice) "입력값을 이 기기에 저장했어요." else "입력값을 이번 앱 실행에 적용했어요."
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("manualBodyApply")) { Text("입력값 적용") }
            }
            if (current != null) TextButton(onClick = {
                ManualBodyStore.clear(context)
                message = "직접 입력한 치수를 삭제했어요."
            }, modifier = Modifier.testTag("manualBodyClear")) { Text("직접 입력값 삭제") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (current != null || photo != null) BodyDimensionsCard(BodyDimensions.resolve(current,photo))
        }
    }
}
