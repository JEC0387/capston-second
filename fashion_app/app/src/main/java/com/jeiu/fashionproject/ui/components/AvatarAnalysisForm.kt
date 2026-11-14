package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import java.util.Locale

@Composable
internal fun AvatarAnalysisForm(
    saved: AvatarProfile?, manual: ManualBodyMeasurements?, owner: String, onAnalyzed: () -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val parts = AvatarAnalysisInput.coreParts + AvatarAnalysisInput.extraParts
    val initial = remember(owner) { saved }
    val original = remember(owner) {
        val avatarMeasurements = initial?.let(AvatarAnalysisInput::recorded).orEmpty()
        if (avatarMeasurements.isNotEmpty()) avatarMeasurements
        else if (manual != null && (saved == null || saved.heightCm == manual.heightCm)) mapOf(
            AvatarPart.CHEST to manual.chestCircumferenceCm, AvatarPart.WAIST to manual.waistCircumferenceCm,
            AvatarPart.HIP to manual.hipCircumferenceCm, AvatarPart.SHOULDER to manual.shoulderWidthCm,
            AvatarPart.LEG to manual.legLengthCm, AvatarPart.TORSO to manual.torsoLengthCm
        ).mapNotNull { (part, cm) -> cm?.let { part to it } }.toMap()
        else emptyMap()
    }
    var height by rememberSaveable(owner) { mutableStateOf((saved?.heightCm ?: manual?.heightCm)?.toString().orEmpty()) }
    var weight by rememberSaveable(owner) { mutableStateOf(saved?.weightKg?.toString().orEmpty()) }
    fun hasSeparateAdjustment(part: AvatarPart): Boolean {
        val value = initial?.centimeters?.values?.get(part) ?: return false
        // Renderer rounding of an exact tape value is not a separate ruler edit.
        return value.origin == AvatarValueOrigin.ADJUSTED &&
            original[part]?.let { AvatarCentimeters.snap(it) != value.cm } != false
    }
    var fields by rememberSaveable(owner) { mutableStateOf<List<String>>(ArrayList(parts.map { part ->
        val value = initial?.centimeters?.values?.get(part)
        if (hasSeparateAdjustment(part)) value?.cm.toString()
        else original[part]?.toString().orEmpty()
    })) }
    var kinds by rememberSaveable(owner) { mutableStateOf<List<String>>(ArrayList(parts.map { part ->
        if (hasSeparateAdjustment(part)) AvatarValueOrigin.ADJUSTED.name
        else if (part in original) AvatarValueOrigin.MANUAL.name else AvatarValueOrigin.AUTO.name
    })) }
    // A visual adjustment preserves real tape references until explicitly confirmed.
    var references by rememberSaveable(owner) { mutableStateOf<List<String>>(ArrayList(parts.map { original[it]?.toString().orEmpty() })) }
    var direct by rememberSaveable(owner) { mutableStateOf(false) }
    var selectedName by rememberSaveable(owner) { mutableStateOf(AvatarPart.WAIST.name) }
    val selected = AvatarPart.valueOf(selectedName)
    var help by remember { mutableStateOf(false) }
    var dirty by rememberSaveable(owner) {
        mutableStateOf(initial != null && original.isNotEmpty() && AvatarAnalysisInput.recorded(initial).isEmpty())
    }
    var savedSnapshot by rememberSaveable(owner) { mutableStateOf(initial?.let(AvatarProfileStore::encode)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val preferredTimestamp = rememberSaveable(owner) { System.currentTimeMillis() }
    val heightCm = height.toBodyNumber()?.takeIf { it in 100f..230f }
    val weightKg = weight.toBodyNumber()?.takeIf { it in 25f..300f }
    val measured = parts.zip(references).mapNotNull { (part, field) ->
        field.toBodyNumber()?.takeIf { AvatarAnalysisInput.valid(part, it, heightCm ?: Float.NaN) }?.let { part to it }
    }.toMap()
    val invalidParts = parts.zip(fields).filter { (part, field) ->
        field.isNotBlank() && field.toBodyNumber()?.let {
            AvatarAnalysisInput.valid(part, it, heightCm ?: Float.NaN)
        } != true
    }.map { it.first }
    val invalidReferences = parts.indices.any { references[it].isNotBlank() && parts[it] !in measured }
    val lengthsFit = heightCm?.let { AvatarAnalysisInput.lengthsFit(measured, it) } ?: true
    val previewInput = if (lengthsFit) measured else measured - AvatarPart.LEG - AvatarPart.TORSO
    val base = remember(heightCm, weightKg, previewInput, initial) {
        if (heightCm == null || weightKg == null) null
        else AvatarAnalysisInput.build(heightCm, weightKg, previewInput, initial, preferredTimestamp)
    }
    val preview = remember(base, fields, kinds) {
        base?.let { profile ->
            val model = checkNotNull(profile.centimeters)
            val adjusted = parts.indices.filter { kinds[it] == AvatarValueOrigin.ADJUSTED.name }
                .mapNotNull { i -> fields[i].toBodyNumber()?.let {
                    parts[i] to AvatarCm(AvatarCentimeters.snap(it), AvatarValueOrigin.ADJUSTED)
                } }.toMap()
            profile.copy(centimeters = model.copy(values = model.values + adjusted)).takeIf(AvatarProfile::valid)
        }
    }
    val shown = preview ?: base
    val isSaved = !dirty && savedSnapshot != null && saved?.let(AvatarProfileStore::encode) == savedSnapshot
    fun changed() { dirty = true; message = null }
    fun display(part: AvatarPart): String {
        val index = parts.indexOf(part)
        return fields[index].ifBlank {
            if (kinds[index] == AvatarValueOrigin.AUTO.name) shown?.centimeters?.get(part)?.let(::bodyNumber).orEmpty() else ""
        }
    }
    fun enter(part: AvatarPart, raw: String) {
        val index = parts.indexOf(part)
        fields = ArrayList(fields).apply { set(index, raw) }
        kinds = ArrayList(kinds).apply { set(index, AvatarValueOrigin.MANUAL.name) }
        if (raw.isBlank() || raw.toBodyNumber()?.let { AvatarAnalysisInput.valid(part, it, heightCm ?: Float.NaN) } == true)
            references = ArrayList(references).apply { set(index, raw) }
        changed()
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("치수 입력 도움말") },
        text = { Text("키·몸무게로 만든 모습은 추정이에요.\n줄자는 미리보기를 조절하며, 직접 잰 값으로 확인해야 분석에 사용해요.\n실측과 사진 분석 기록은 별도로 보존해요.") },
        confirmButton = { TextButton({ help = false }) { Text("확인") } })

    Text("1. 내 체형 만들기", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
    val heightField: @Composable (Modifier) -> Unit = { modifier ->
        BodyNumberField(height, { height = it; changed() }, "키 (cm)", "avatarHeight",
            height.isNotBlank() && heightCm == null, modifier)
    }
    val weightField: @Composable (Modifier) -> Unit = { modifier ->
        BodyNumberField(weight, { weight = it; changed() }, "몸무게 (kg)", "avatarWeight",
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
            if (shown == null) Text("키와 몸무게를 입력하면 아바타가 보여요.", color = FashionMuted)
            else {
                val parameters = remember(shown) { shown.parameters() }
                BodyAvatarViewport(parameters, shown.thigh,
                    Modifier.fillMaxWidth().height(224.dp).testTag("avatarAnalysisPreview"), fitModel = true)
                Text("화면을 밀어 아바타를 돌려보세요.", color = FashionMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("2. 내 치수 조절", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!direct, { focus.clearFocus(); direct = false }, label = { Text("줄자로 조절") },
            modifier = Modifier.weight(1f).testTag("avatarRulerTab"))
        FilterChip(direct, { direct = true }, label = { Text("직접 입력") },
            modifier = Modifier.weight(1f).testTag("avatarDirectTab"))
    }
    val visibleParts = AvatarAnalysisInput.coreParts + if (expanded) AvatarAnalysisInput.extraParts else emptyList()
    if (direct) {
        Text("직접 잰 값을 입력해 주세요.", color = FashionMuted, style = MaterialTheme.typography.bodySmall)
        val field: @Composable (AvatarPart, Modifier) -> Unit = { part, modifier ->
            BodyNumberField(display(part), { enter(part, it) }, part.inputLabel(), "avatarMeasure" + part.name,
                part in invalidParts, modifier,
                if (kinds[parts.indexOf(part)] == AvatarValueOrigin.MANUAL.name) "직접 잰 값" else "추정·조절값")
        }
        if (LocalDensity.current.fontScale >= 1.4f) visibleParts.forEach { field(it, Modifier.fillMaxWidth()) }
        else visibleParts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { field(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleParts.forEach { part -> FilterChip(selected == part, { selectedName = part.name },
                label = { Text(part.label) }, modifier = Modifier.testTag("analysisPart-" + part.name)) }
        }
        shown?.centimeters?.let { model ->
            val range = CmRulerMath.range(model, selected)
            val current = display(selected).toBodyNumber() ?: model[selected]
            Text(selected.inputLabel(), style = MaterialTheme.typography.titleMedium)
            if (current in range) CmRuler(current, range, selected.label, { value ->
                val index = parts.indexOf(selected)
                fields = ArrayList(fields).apply { set(index, bodyNumber(value)) }
                kinds = ArrayList(kinds).apply { set(index, AvatarValueOrigin.ADJUSTED.name) }
                changed()
            }, { direct = true }, showDirectInputAction = false)
            else Text("이 치수는 직접 입력에서 확인해 주세요.", color = FashionMuted)
            val index = parts.indexOf(selected)
            Text(if (kinds[index] == AvatarValueOrigin.MANUAL.name) "직접 잰 치수예요." else references[index].toBodyNumber()?.let { "미리보기 조절값 · 실측 " + bodyNumber(it) + " cm 유지" } ?: "미리보기 조절값이에요.",
                color = FashionMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("analysisValueOrigin"))
            if (kinds[index] != AvatarValueOrigin.MANUAL.name)
                TextButton({ enter(selected, display(selected)) },
                    Modifier.heightIn(min = 48.dp).testTag("analysisConfirmMeasured")) { Text("직접 잰 값으로 사용") }
        }
    }
    TextButton(onClick = {
        expanded = !expanded
        if (!expanded && selected in AvatarAnalysisInput.extraParts) selectedName = AvatarPart.WAIST.name
    }, modifier = Modifier.heightIn(min = 48.dp).testTag("avatarOptionalMeasures")) {
        Text(if (expanded) "추가 치수 접기" else "추가 치수")
    }
    TextButton({ help = true }, Modifier.heightIn(min = 48.dp).testTag("avatarAnalysisHelp")) { Text("ⓘ 도움말") }
    val missing = AvatarAnalysisInput.coreParts.filter { it != AvatarPart.SHOULDER && it !in measured }
    if (missing.isNotEmpty()) Text("분석하려면 가슴·허리·엉덩이 실측이 필요해요.",
        color = FashionMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("avatarAnalysisMissing"))
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("avatarAnalysisInputError")) }
    if (!isSaved) Button(
        onClick = {
            focus.clearFocus()
            message = when {
                invalidParts.isNotEmpty() || invalidReferences -> "입력한 치수의 범위를 확인해 주세요."
                !lengthsFit -> "키에 맞게 다리와 상체 길이를 다시 확인해 주세요."
                preview == null -> "키와 몸무게를 확인해 주세요."
                BodyMeasurementScope.current() != owner -> "계정 연결을 확인한 뒤 다시 시도해 주세요."
                !AvatarProfileStore.save(context, preview) -> "치수를 저장하지 못했어요. 다시 시도해 주세요."
                else -> {
                    savedSnapshot = AvatarProfileStore.encode(preview)
                    dirty = false
                    null
                }
            }
        },
        shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("avatarSave")
    ) { Text("이 체형으로 저장") }
    else {
        Text("체형을 저장했어요.", color = FashionMuted, modifier = Modifier.testTag("avatarSavedStatus"))
        Button(enabled = missing.isEmpty(), onClick = {
            if (BodyMeasurementScope.current() == owner && !dirty &&
                AvatarProfileStore.current.value?.let(AvatarProfileStore::encode) == savedSnapshot) onAnalyzed()
            else message = "저장한 체형을 다시 확인해 주세요."
        }, shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("avatarAnalyze")) {
            Text("내 체형 분석하기")
        }
    }
}

@Composable
private fun BodyNumberField(value: String, onChange: (String) -> Unit, label: String, tag: String,
    invalid: Boolean, modifier: Modifier, caption: String? = null) {
    OutlinedTextField(value, { onChange(it.take(8)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = invalid,
        supportingText = caption?.let { { Text(it) } },
        modifier = modifier.heightIn(min = 56.dp).testTag(tag))
}

private fun String.toBodyNumber(): Float? =
    trim().replace(',', '.').takeIf { it.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,2})?")) }?.toFloatOrNull()

private fun AvatarPart.inputLabel(): String = when (this) {
    AvatarPart.LEG -> "다리 길이 (cm)"
    AvatarPart.TORSO -> "상체 길이 (cm)"
    else -> "$label $unit (cm)"
}

private fun bodyNumber(value: Float) = String.format(Locale.KOREA, "%.1f", value)
