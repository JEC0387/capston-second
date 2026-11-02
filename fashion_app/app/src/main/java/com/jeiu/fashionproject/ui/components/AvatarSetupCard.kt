package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.BodyMeasurements

@Composable
fun AvatarSetupCard(photo: BodyMeasurements? = null, autoImportPhoto: Boolean = false) {
    val context = LocalContext.current
    var storesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AvatarProfileStore.load(context);ManualBodyStore.load(context);storesLoaded=true }
    val saved by AvatarProfileStore.current.collectAsState()
    val issue by AvatarProfileStore.issue.collectAsState()
    val sessionPhoto by BodyAnalysisSessionStore.measurements.collectAsState()
    val manual by ManualBodyStore.current.collectAsState()
    val manualIssue by ManualBodyStore.issue.collectAsState()
    var imported by remember { mutableStateOf<AvatarProfile?>(null) }
    var proposal by remember { mutableStateOf<AvatarProfile?>(null) }
    var initialImportHandled by remember { mutableStateOf(false) }
    var height by remember { mutableStateOf("170") }
    var weight by remember { mutableStateOf("65") }
    var shoulder by remember { mutableFloatStateOf(1f) }
    var waist by remember { mutableFloatStateOf(1f) }
    var hip by remember { mutableFloatStateOf(1f) }
    var thigh by remember { mutableFloatStateOf(1f) }
    var leg by remember { mutableFloatStateOf(1f) }
    var chest by remember { mutableFloatStateOf(1f) }
    var torso by remember { mutableFloatStateOf(1f) }
    var showAvatar by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) { saved?.let {
        height=it.heightCm.toString();weight=it.weightKg.toString()
        shoulder=it.shoulder;waist=it.waist;hip=it.hip;thigh=it.thigh;leg=it.leg
        chest=it.chest;torso=it.torso;imported=it
    } }
    val draft = AvatarProfile(height.toFloatOrNull() ?: Float.NaN, weight.toFloatOrNull() ?: Float.NaN,
        shoulder, waist, hip, thigh, leg,chest,torso,imported?.source ?: AvatarSource.BMI,imported?.applied.orEmpty())
    LaunchedEffect(autoImportPhoto, storesLoaded, photo, sessionPhoto, saved) {
        if (autoImportPhoto && storesLoaded && !initialImportHandled) {
            AvatarMeasurementImport.photo(photo ?: sessionPhoto, saved)?.let {
                proposal = it
                initialImportHandled = true
            }
        }
    }
    proposal?.let { proposed -> AvatarApplicationDialog(saved,proposed,onDismiss={proposal=null},onApprove={
        imported=proposed;height=proposed.heightCm.toString();weight=proposed.weightKg.toString()
        shoulder=proposed.shoulder;waist=proposed.waist;hip=proposed.hip;thigh=proposed.thigh;leg=proposed.leg
        chest=proposed.chest;torso=proposed.torso;proposal=null
        message="선택한 출처를 미리보기에 반영했어요. 기존 저장값은 ‘3D 설정 저장’을 누를 때만 바뀌어요."
    }) }
    if (showAvatar && draft.valid()) BodyAvatarDialog(BodyDimensions.resolve(null,null),
        onDismiss={showAvatar=false}, configured=draft.parameters(), thighScale=thigh)
    if (askClear) AlertDialog(onDismissRequest={askClear=false},title={Text("3D 설정 초기화")},
        text={Text("저장된 3D 표시 프로필과 보존해 둔 구버전 3D 복사본만 지워요. 직접 입력한 원본 치수와 사진 분석 기록은 유지해요.")},
        confirmButton={TextButton(onClick={
            if (AvatarProfileStore.clear(context)) {
                height="170";weight="65";shoulder=1f;waist=1f;hip=1f;thigh=1f;leg=1f;chest=1f;torso=1f;imported=null
                message="3D 설정을 초기화했어요."
            } else message="3D 설정을 지우지 못했어요."
            askClear=false
        }) { Text("초기화") }},
        dismissButton={TextButton(onClick={askClear=false}) { Text("취소") }})
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("내 3D 모델",style=MaterialTheme.typography.titleMedium)
            Text(if (saved != null) "저장된 3D 설정: ${saved!!.source.label}" else "아직 저장한 3D 설정이 없어요.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={showAvatar=true},enabled=draft.valid(),
                modifier=Modifier.fillMaxWidth().testTag("avatarShow")) { Text("3D 모델 보기") }
            Text("체형 생성 방법",style=MaterialTheme.typography.titleMedium)
            Text("3D 형상 출처 선택 · 현재 ${draft.source.label}",modifier=Modifier.testTag("avatarSource"))
            OutlinedButton(onClick={proposal=AvatarProfile(height.toFloatOrNull() ?: Float.NaN,weight.toFloatOrNull() ?: Float.NaN).takeIf {it.valid()}},
                modifier=Modifier.fillMaxWidth().testTag("avatarImportBmi")) {
                Text("A · 키·몸무게 추정 사용")
            }
            Text("입력값으로 초기 비율을 추정해요.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={proposal=AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)},
                enabled=AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)!=null,
                modifier=Modifier.fillMaxWidth().testTag("avatarImportPhoto")) {
                Text("B · 이 사진 치수로 3D 모델 만들기")
            }
            Text("이번 세션의 유효한 사진 측정값을 비교한 뒤 반영해요.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={proposal=AvatarMeasurementImport.manual(manual,saved)},
                enabled=AvatarMeasurementImport.manual(manual,saved)!=null,
                modifier=Modifier.fillMaxWidth().testTag("avatarImportManual")) {
                Text("C · 직접 입력한 치수 사용")
            }
            Text("직접 잰 값의 출처를 확인하고 가져와요.",style=MaterialTheme.typography.bodySmall)
            if(AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)==null) Text("현재 세션의 유효한 사진 측정값이 없어요. 사진 분석 후 다시 선택해주세요.")
            if(manual==null) Text("직접 입력한 신체 치수가 없어요. 숫자 입력에서 필요한 항목을 추가해주세요.")
            manualIssue?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text("키·몸무게는 시각적 초기 비율에만 사용해요. 실제 신체 치수·체지방·의류 핏을 측정하지 않아요.",
                style=MaterialTheme.typography.bodySmall)
            issue?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            OutlinedTextField(height,{height=it.take(8)},label={Text("키 (cm)")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                readOnly=draft.source!=AvatarSource.BMI,modifier=Modifier.fillMaxWidth().testTag("avatarHeight"))
            OutlinedTextField(weight,{weight=it.take(8)},label={Text(if(draft.source==AvatarSource.BMI) "몸무게 (kg)" else "몸무게 (이 모드의 형상 계산에 사용하지 않음)")},singleLine=true,
                readOnly=draft.source!=AvatarSource.BMI,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                modifier=Modifier.fillMaxWidth().testTag("avatarWeight"))
            if (!draft.valid()) Text("키 100~230cm, 몸무게 25~300kg을 확인해주세요.",color=MaterialTheme.colorScheme.error)
            @Composable fun adjustment(label:String,value:Float,onChange:(Float)->Unit,tag:String) {
                Text("$label 표시 비율: ${(value*100).toInt()}%")
                Slider(value,onChange,valueRange=.8f..1.2f,modifier=Modifier.testTag(tag))
            }
            OutlinedButton(onClick={controlsExpanded=!controlsExpanded},
                modifier=Modifier.fillMaxWidth().testTag("avatarAdjustToggle")) {
                Text(if(controlsExpanded) "세부 체형 조절 접기" else "세부 체형 조절")
            }
            if (controlsExpanded) {
            adjustment("어깨",shoulder,{shoulder=it},"avatarShoulder")
            adjustment("가슴",chest,{chest=it},"avatarChest")
            adjustment("허리",waist,{waist=it},"avatarWaist")
            adjustment("골반·엉덩이",hip,{hip=it},"avatarHip")
            adjustment("허벅지",thigh,{thigh=it},"avatarThigh")
            adjustment("다리 길이",leg,{leg=it},"avatarLeg")
            adjustment("상체 길이",torso,{torso=it},"avatarTorso")
            imported?.applied?.forEach { (key,value) -> Text("${key.label}: ${value.cm} cm · ${value.origin.label}") }
            if(draft.source!=AvatarSource.BMI) Text("빠진 가슴/부위 치수와 앞뒤 깊이는 기본 비율 추정이에요. 둘레→타원 형상은 시각화 가정이며 실제 깊이 측정이 아니에요.")
            Text("조절값은 3D 메시 너비·관절 높이에 반영돼요. 사진 분석·직접 잰 치수에는 쓰지 않아요.",
                style=MaterialTheme.typography.bodySmall)
            }
            Text("저장하면 선택한 치수의 숫자·출처 복사본도 이 기기에 남아요. 원본 분석 기록과 별도이며 서버로 전송하지 않아요.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={message=if(AvatarProfileStore.save(context,draft)) "이 기기에 3D 설정을 저장했어요." else "3D 설정을 저장하지 못했어요. 입력값이나 기존 저장값을 확인해주세요."},
                enabled=draft.valid(),modifier=Modifier.fillMaxWidth().testTag("avatarSave")) { Text("3D 설정 저장") }
            if (saved != null || issue != null) TextButton(onClick={askClear=true}) { Text("3D 설정 초기화") }
            message?.let { Text(it) }
        }
    }
}
