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
fun AvatarSetupCard(photo: BodyMeasurements? = null, autoImportPhoto: Boolean = false,onApplyToFitting:(()->Unit)?=null) {
    val context = LocalContext.current
    var storesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AvatarProfileStore.load(context);ManualBodyStore.load(context);storesLoaded=true }
    val saved by AvatarProfileStore.current.collectAsState()
    val issue by AvatarProfileStore.issue.collectAsState()
    val sessionPhoto by BodyAnalysisSessionStore.measurements.collectAsState()
    val manual by ManualBodyStore.current.collectAsState()
    val manualIssue by ManualBodyStore.issue.collectAsState()
    var importedJson by rememberSaveable { mutableStateOf<String?>(null) }
    val imported = remember(importedJson) { importedJson?.let(AvatarProfileStore::decode) }
    var proposal by remember { mutableStateOf<AvatarProfile?>(null) }
    var initialImportHandled by rememberSaveable { mutableStateOf(false) }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    var height by rememberSaveable { mutableStateOf("170") }
    var weight by rememberSaveable { mutableStateOf("65") }
    var cmEditingRaw by rememberSaveable {mutableStateOf<String?>(null)}
    var shoulder by rememberSaveable { mutableFloatStateOf(1f) }
    var waist by rememberSaveable { mutableFloatStateOf(1f) }
    var hip by rememberSaveable { mutableFloatStateOf(1f) }
    var thigh by rememberSaveable { mutableFloatStateOf(1f) }
    var leg by rememberSaveable { mutableFloatStateOf(1f) }
    var chest by rememberSaveable { mutableFloatStateOf(1f) }
    var torso by rememberSaveable { mutableFloatStateOf(1f) }
    var showAvatar by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    fun adopt(profile: AvatarProfile) {
        height=profile.heightCm.toString();weight=profile.weightKg.toString()
        shoulder=profile.shoulder;waist=profile.waist;hip=profile.hip;thigh=profile.thigh;leg=profile.leg
        chest=profile.chest;torso=profile.torso;importedJson=AvatarProfileStore.encode(profile)
    }
    // Store hydration is not an instruction to replace an active, restored or newly typed draft.
    LaunchedEffect(storesLoaded,saved) { if(storesLoaded && !draftEdited) saved?.let(::adopt) }
    val draft = AvatarProfile(height.toFloatOrNull() ?: Float.NaN, weight.toFloatOrNull() ?: Float.NaN,
        shoulder, waist, hip, thigh, leg,chest,torso,imported?.source ?: AvatarSource.BMI,imported?.applied.orEmpty(),imported?.centimeters)
    cmEditingRaw?.let {raw->AvatarProfileStore.decode(raw)?.let {base->
        PremiumAvatarEditor(base,saved,onDismiss={cmEditingRaw=null},onSave={profile->
            AvatarProfileStore.save(context,profile).also {ok->if(ok) {adopt(profile);draftEdited=false}}
        },onApplyToFitting=onApplyToFitting)
    }}
    LaunchedEffect(autoImportPhoto, storesLoaded, photo, sessionPhoto, saved) {
        if (autoImportPhoto && storesLoaded && !initialImportHandled) {
            AvatarMeasurementImport.photo(photo ?: sessionPhoto, saved)?.let {
                proposal = it
                initialImportHandled = true
            }
        }
    }
    proposal?.let { proposed -> AvatarApplicationDialog(saved,proposed,onDismiss={proposal=null},onApprove={
        adopt(proposed);draftEdited=true;proposal=null
        message="선택한 출처를 미리보기에 반영했어요. 기존 저장값은 ‘3D 설정 저장’을 누를 때만 바뀌어요."
    }) }
    if (showAvatar && draft.valid()) BodyAvatarDialog(BodyDimensions.resolve(null,null),
        onDismiss={showAvatar=false}, configured=draft.parameters(), thighScale=thigh)
    if (askClear) AlertDialog(onDismissRequest={askClear=false},title={Text("3D 설정 초기화")},
        text={Text("저장된 3D 표시 프로필과 보존해 둔 구버전 3D 복사본만 지워요. 직접 입력한 원본 치수와 사진 분석 기록은 유지해요.")},
        confirmButton={TextButton(onClick={
            if (AvatarProfileStore.clear(context)) {
                height="170";weight="65";shoulder=1f;waist=1f;hip=1f;thigh=1f;leg=1f;chest=1f;torso=1f;importedJson=null;draftEdited=true
                message="3D 설정을 초기화했어요."
            } else message="3D 설정을 지우지 못했어요."
            askClear=false
        }) { Text("초기화") }},
        dismissButton={TextButton(onClick={askClear=false}) { Text("취소") }})
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("내 3D 모델",style=MaterialTheme.typography.titleMedium)
            Text(if(saved?.centimeters!=null)"저장된 cm 아바타 · 부위별 출처 구분" else if (saved != null) "저장된 3D 설정: ${saved!!.source.label}" else "아직 저장한 3D 설정이 없어요.",
                style=MaterialTheme.typography.bodySmall)
            Text("1단계 · 키와 몸무게",style=MaterialTheme.typography.titleMedium)
            if(draft.source!=AvatarSource.BMI || draft.centimeters!=null) {
                TextButton(onClick={
                    importedJson=null;shoulder=1f;chest=1f;waist=1f;hip=1f;thigh=1f;leg=1f;torso=1f;draftEdited=true
                    controlsExpanded=false
                    message="기존 저장 프로필은 그대로예요. 새 키·몸무게를 입력하고 3D를 만들 수 있어요."
                },modifier=Modifier.testTag("avatarStartNew")) { Text("새 키·몸무게로 시작") }
            }
            Text("키·몸무게로 만든 추정 형상이에요. 실제 치수는 다음 화면에서 조절해요.",
                style=MaterialTheme.typography.bodySmall)
            issue?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            OutlinedTextField(height,{height=it.take(8);draftEdited=true},label={Text("키 (cm)")},singleLine=true,enabled=storesLoaded,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                readOnly=draft.source!=AvatarSource.BMI || draft.centimeters!=null,modifier=Modifier.fillMaxWidth().testTag("avatarHeight"))
            OutlinedTextField(weight,{weight=it.take(8);draftEdited=true},label={Text("몸무게 (kg)")},singleLine=true,enabled=storesLoaded,
                readOnly=draft.centimeters!=null,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                modifier=Modifier.fillMaxWidth().testTag("avatarWeight"))
            if (!draft.valid()) Text("키 100~230cm, 몸무게 25~300kg을 확인해주세요.",color=MaterialTheme.colorScheme.error)
            Text("2단계 · 내 3D 체형",style=MaterialTheme.typography.titleMedium)
            Button(enabled=draft.valid()&&storesLoaded,onClick={
                AvatarCentimeters.fromLegacy(draft,System.currentTimeMillis())?.let {cm->
                    cmEditingRaw=AvatarProfileStore.encode(draft.copy(centimeters=cm))
                }
            },modifier=Modifier.fillMaxWidth().testTag("openCmAvatar")) {Text(if(draft.centimeters!=null)"저장된 체형 조절" else "3D 생성 · 부위별 조절")}
            OutlinedButton(onClick={advancedExpanded=!advancedExpanded},
                modifier=Modifier.fillMaxWidth().testTag("avatarAdvancedToggle")) {
                Text(if(advancedExpanded) "고급 설정 접기" else "고급 설정 · 사진 치수 및 실측값 불러오기")
            }
            if(advancedExpanded) {
            OutlinedButton(onClick={showAvatar=true},enabled=draft.valid(),
                modifier=Modifier.fillMaxWidth().testTag("avatarShow")) { Text("현재 설정 3D 미리보기") }
            Text("체형 생성 방법",style=MaterialTheme.typography.titleMedium)
            Text("3D 형상 출처 선택 · 현재 ${draft.source.label}",modifier=Modifier.testTag("avatarSource"))
            OutlinedButton(onClick={proposal=AvatarProfile(height.toFloatOrNull() ?: Float.NaN,weight.toFloatOrNull() ?: Float.NaN).takeIf {it.valid()}},
                modifier=Modifier.fillMaxWidth().testTag("avatarImportBmi")) {
                Text("키·몸무게 추정 사용")
            }
            Text("입력값으로 초기 비율을 추정해요.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={proposal=AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)},
                enabled=AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)!=null,
                modifier=Modifier.fillMaxWidth().testTag("avatarImportPhoto")) {
                Text("이 사진 치수로 3D 모델 만들기")
            }
            Text("이번 세션의 유효한 사진 측정값을 비교한 뒤 반영해요.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={proposal=AvatarMeasurementImport.manual(manual,saved)},
                enabled=AvatarMeasurementImport.manual(manual,saved)!=null,
                modifier=Modifier.fillMaxWidth().testTag("avatarImportManual")) {
                Text("직접 입력한 치수 사용")
            }
            Text("직접 잰 값의 출처를 확인하고 가져와요.",style=MaterialTheme.typography.bodySmall)
            if(AvatarMeasurementImport.photo(photo ?: sessionPhoto,saved)==null) Text("현재 세션의 유효한 사진 측정값이 없어요. 사진 분석 후 다시 선택해주세요.")
            if(manual==null) Text("직접 입력한 신체 치수가 없어요. 숫자 입력에서 필요한 항목을 추가해주세요.")
            manualIssue?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            @Composable fun adjustment(label:String,value:Float,onChange:(Float)->Unit,tag:String) {
                Text("$label 표시 비율: ${(value*100).toInt()}%")
                Slider(value,{draftEdited=true;onChange(it)},valueRange=.8f..1.2f,modifier=Modifier.testTag(tag))
            }
            OutlinedButton(onClick={controlsExpanded=!controlsExpanded},
                modifier=Modifier.fillMaxWidth().testTag("avatarAdjustToggle")) {
                Text(if(controlsExpanded) "세부 체형 조절 접기" else "세부 체형 조절")
            }
            if (controlsExpanded) {
            if(draft.centimeters==null) {
            adjustment("어깨",shoulder,{shoulder=it},"avatarShoulder")
            adjustment("가슴",chest,{chest=it},"avatarChest")
            adjustment("허리",waist,{waist=it},"avatarWaist")
            adjustment("골반·엉덩이",hip,{hip=it},"avatarHip")
            adjustment("허벅지",thigh,{thigh=it},"avatarThigh")
            adjustment("다리 길이",leg,{leg=it},"avatarLeg")
            adjustment("상체 길이",torso,{torso=it},"avatarTorso")
            } else Text("저장된 cm 프로필은 위 줄자 화면에서 조절해요. 다른 출처를 선택하면 변경 전 비교를 거쳐 새 초안을 만들어요.")
            imported?.applied?.forEach { (key,value) -> Text("${key.label}: ${value.cm} cm · ${value.origin.label}") }
            if(draft.source!=AvatarSource.BMI) Text("빠진 가슴/부위 치수와 앞뒤 깊이는 기본 비율 추정이에요. 둘레→타원 형상은 시각화 가정이며 실제 깊이 측정이 아니에요.")
            Text("조절값은 3D 메시 너비·관절 높이에 반영돼요. 사진 분석·직접 잰 치수에는 쓰지 않아요.",
                style=MaterialTheme.typography.bodySmall)
            }
            Text("저장하면 선택한 치수의 숫자·출처 복사본도 이 기기에 남아요. 원본 분석 기록과 별도이며 서버로 전송하지 않아요.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={message=if(AvatarProfileStore.save(context,draft)) {
                adopt(draft);draftEdited=false;"이 기기에 3D 설정을 저장했어요."
            } else "3D 설정을 저장하지 못했어요. 입력값이나 기존 저장값을 확인해주세요."},
                enabled=draft.valid(),modifier=Modifier.fillMaxWidth().testTag("avatarSave")) { Text("3D 설정 저장") }
            if (saved != null || issue != null) TextButton(onClick={askClear=true}) { Text("3D 설정 초기화") }
            }
            message?.let { Text(it) }
        }
    }
}
