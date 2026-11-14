package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.theme.*

@Composable
internal fun AvatarAnalysisForm(
    saved: AvatarProfile?, manual: ManualBodyMeasurements?, owner: String, onAnalyzed: () -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val parts = AvatarAnalysisInput.coreParts + AvatarAnalysisInput.extraParts
    val original = remember(saved, manual) {
        val avatarMeasurements = saved?.let(AvatarAnalysisInput::recorded).orEmpty()
        if (avatarMeasurements.isNotEmpty()) avatarMeasurements
        else if (manual != null && (saved == null || saved.heightCm == manual.heightCm)) mapOf(
            AvatarPart.CHEST to manual.chestCircumferenceCm, AvatarPart.WAIST to manual.waistCircumferenceCm,
            AvatarPart.HIP to manual.hipCircumferenceCm, AvatarPart.SHOULDER to manual.shoulderWidthCm,
            AvatarPart.LEG to manual.legLengthCm, AvatarPart.TORSO to manual.torsoLengthCm
        ).mapNotNull { (part, cm) -> cm?.let { part to it } }.toMap()
        else emptyMap()
    }
    var height by rememberSaveable(saved, manual) { mutableStateOf((saved?.heightCm ?: manual?.heightCm)?.toString().orEmpty()) }
    var weight by rememberSaveable(saved) { mutableStateOf(saved?.weightKg?.toString().orEmpty()) }
    var fields by rememberSaveable(original) { mutableStateOf(ArrayList(parts.map { original[it]?.toString().orEmpty() })) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val preferredTimestamp = remember { System.currentTimeMillis() }
    val heightCm = height.toBodyNumber()?.takeIf { it in 100f..230f }
    val weightKg = weight.toBodyNumber()?.takeIf { it in 25f..300f }
    val measured = parts.zip(fields).mapNotNull { (part, field) ->
        field.toBodyNumber()?.takeIf { AvatarAnalysisInput.valid(part, it, heightCm ?: Float.NaN) }?.let { part to it }
    }.toMap()
    val invalidParts = parts.zip(fields).filter { (part, field) -> field.isNotBlank() && part !in measured }.map { it.first }
    val lengthsFit = heightCm?.let { AvatarAnalysisInput.lengthsFit(measured, it) } ?: true
    val previewInput = if (lengthsFit) measured else measured - AvatarPart.LEG - AvatarPart.TORSO
    val preview = remember(heightCm, weightKg, previewInput, saved) {
        if (heightCm == null || weightKg == null) null
        else AvatarAnalysisInput.build(heightCm, weightKg, previewInput, saved, preferredTimestamp)
    }
    val modelApproximation = preview?.centimeters?.let { model ->
        previewInput.any { (part, cm) -> model[part] != AvatarCentimeters.snap(cm) }
    } == true

    Text("1. 내 체형 만들기", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
    val heightField: @Composable (Modifier) -> Unit = { modifier ->
        BodyNumberField(height, { height = it; message = null }, "키 (cm)", "avatarHeight",
            height.isNotBlank() && heightCm == null, modifier)
    }
    val weightField: @Composable (Modifier) -> Unit = { modifier ->
        BodyNumberField(weight, { weight = it; message = null }, "몸무게 (kg)", "avatarWeight",
            weight.isNotBlank() && weightKg == null, modifier)
    }
    if (LocalDensity.current.fontScale >= 1.4f) {
        heightField(Modifier.fillMaxWidth())
        weightField(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        heightField(Modifier.weight(1f))
        weightField(Modifier.weight(1f))
    }
    if (height.isNotBlank() && heightCm == null || weight.isNotBlank() && weightKg == null) {
        Text("키는 100~230 cm, 몸무게는 25~300 kg로 입력해 주세요.", color = MaterialTheme.colorScheme.error)
    }
    Card(Modifier.fillMaxWidth(), shape = FashionCardShape, colors = CardDefaults.cardColors(containerColor = CreamDark)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("3D 미리보기", style = MaterialTheme.typography.titleMedium)
            if (preview == null) Text("키와 몸무게를 입력하면 아바타가 보여요.", color = FashionMuted)
            else {
                val parameters = remember(preview) { preview.parameters() }
                BodyAvatarViewport(parameters, preview.thigh,
                    Modifier.fillMaxWidth().height(192.dp).testTag("avatarAnalysisPreview"), fitModel = true)
                Text(when {
                    measured.isEmpty() -> "키와 몸무게로 만든 미리보기예요."
                    modelApproximation -> "일부 치수는 미리보기와 다를 수 있어요. 입력값은 분석에 그대로 사용해요."
                    else -> "화면을 밀어 아바타를 돌려볼 수 있어요."
                }, color = FashionMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("2. 신체 치수 입력", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
    Text("직접 잰 값만 입력해 주세요.", color = FashionMuted)
    val field: @Composable (AvatarPart, Modifier) -> Unit = { part, modifier ->
        val index = parts.indexOf(part)
        BodyNumberField(fields[index], {
            fields = ArrayList(fields).apply { set(index, it) }
            message = null
        }, part.inputLabel(), "avatarMeasure${part.name}", part in invalidParts, modifier)
    }
    if (LocalDensity.current.fontScale >= 1.4f) {
        AvatarAnalysisInput.coreParts.forEach { field(it, Modifier.fillMaxWidth()) }
    } else {
        AvatarAnalysisInput.coreParts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { field(it, Modifier.weight(1f)) }
            }
        }
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).testTag("avatarOptionalMeasures")) {
        Text(if (expanded) "추가 치수 접기" else "추가 치수 입력 (선택)")
    }
    if (expanded) AvatarAnalysisInput.extraParts.forEach { field(it, Modifier.fillMaxWidth()) }
    val missing = AvatarAnalysisInput.coreParts.filter { it != AvatarPart.SHOULDER && it !in measured }
    if (missing.isNotEmpty()) Text("체형 분석에 필요한 치수 · ${missing.joinToString { "${it.label} ${it.unit}" }}",
        color = FashionMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("avatarAnalysisMissing"))
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("avatarAnalysisInputError")) }
    Button(
        onClick = {
            focus.clearFocus()
            message = when {
                invalidParts.isNotEmpty() -> "입력 범위를 확인해 주세요 · ${invalidParts.joinToString { it.label }}"
                !lengthsFit -> "키에 맞게 다리와 상체 길이를 다시 확인해 주세요."
                missing.isNotEmpty() -> "가슴·허리·엉덩이 둘레를 입력해 주세요."
                preview == null -> "키와 몸무게를 확인해 주세요."
                BodyMeasurementScope.current() != owner -> "계정 연결을 확인한 뒤 다시 시도해 주세요."
                !AvatarProfileStore.save(context, preview) -> "치수를 저장하지 못했어요. 다시 시도해 주세요."
                else -> {
                    onAnalyzed()
                    null
                }
            }
        },
        shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("avatarAnalyze")
    ) { Text("내 체형 분석하기") }
}

@Composable
private fun BodyNumberField(value: String, onChange: (String) -> Unit, label: String, tag: String,
    invalid: Boolean, modifier: Modifier) {
    OutlinedTextField(value, { onChange(it.take(8)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = invalid,
        modifier = modifier.heightIn(min = 56.dp).testTag(tag))
}

private fun String.toBodyNumber(): Float? =
    trim().replace(',', '.').takeIf { it.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,2})?")) }?.toFloatOrNull()

private fun AvatarPart.inputLabel(): String = when (this) {
    AvatarPart.LEG -> "다리 길이 (cm)"
    AvatarPart.TORSO -> "상체 길이 (cm)"
    else -> "$label $unit (cm)"
}
