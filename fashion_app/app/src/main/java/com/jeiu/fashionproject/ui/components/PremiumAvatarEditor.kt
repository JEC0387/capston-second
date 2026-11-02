package com.jeiu.fashionproject.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.theme.*
import org.json.JSONObject
import java.util.Locale

/** All edits are an unsaved draft. Comparison/approval and persistence remain separate actions. */
@Composable
fun PremiumAvatarEditor(initial:AvatarProfile,previous:AvatarProfile?,onDismiss:()->Unit,
    onSave:(AvatarProfile)->Boolean,onApplyToFitting:(()->Unit)?=null) {
    val initialBody=checkNotNull(initial.centimeters)
    val initialJson=remember(initialBody){initialBody.toJson().toString()}
    var json by rememberSaveable(initialJson) {mutableStateOf(initialJson)}
    val parsed=remember(json){runCatching {AvatarCentimeters.fromJson(JSONObject(json))}.getOrNull()}
    val body=parsed ?: initialBody
    var selectedName by rememberSaveable {mutableStateOf(AvatarPart.WAIST.name)}
    val selected=AvatarPart.valueOf(selectedName)
    val range=CmRulerMath.range(body,selected)
    var yaw by rememberSaveable {mutableFloatStateOf(0f)}
    var cameraReset by remember {mutableIntStateOf(0)}
    var input by rememberSaveable {mutableStateOf(false)}
    var text by rememberSaveable {mutableStateOf("")}
    var measured by rememberSaveable {mutableStateOf(false)}
    var confirmSave by remember {mutableStateOf(false)}
    var applyAfterSave by remember {mutableStateOf(false)}
    var askReset by remember {mutableStateOf(false)}
    var askLeave by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var details by rememberSaveable {mutableStateOf(false)}
    var lastSaved by rememberSaveable {mutableStateOf<String?>(null)}
    fun update(next:AvatarCentimeters) {json=next.toJson().toString();message=null}
    fun adjust(value:Float) {
        val next=body.adjusted(selected,value)
        if(next!=null)update(next) else message="이 표시 모델에서 지원하지 않는 조합이에요. 원본과 현재 값은 유지했어요."
    }
    fun leave() {if(json!=(lastSaved ?: initialJson))askLeave=true else onDismiss()}
    fun openInput() {text=String.format(Locale.KOREA,"%.1f",body[selected]);measured=false;input=true}
    val candidate=initial.copy(centimeters=body)
    BackHandler(onBack=::leave)
    if(input) AlertDialog(onDismissRequest={input=false},title={Text("${selected.label} ${selected.unit} 직접 입력")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text,{if(it.length<=8)text=it},label={Text("cm · 0.5 단위")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.testTag("rulerInputField"))
            Text("표시 범위 ${range.start}~${range.endInclusive} cm. 눈금과 같은 0.5cm 단위로 입력해요.")
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(measured,{measured=it});Text("줄자로 직접 잰 값이에요 (선택)")}
            if(CmRulerMath.parse(text,range)==null)Text("유효한 범위의 0.5cm 단위 숫자를 입력해주세요.",color=Brown)
        }
    },confirmButton={TextButton(enabled=CmRulerMath.parse(text,range)!=null,onClick={
        val value=checkNotNull(CmRulerMath.parse(text,range));val next=body.adjusted(selected,value)
        if(next!=null) {
            update(if(measured)next.copy(values=next.values+(selected to AvatarCm(value,AvatarValueOrigin.MANUAL)),
                references=next.references+(selected to AvatarCm(value,AvatarValueOrigin.MANUAL))) else next)
            input=false
        }
    },modifier=Modifier.testTag("rulerInputConfirm")) {Text("cm 반영")}},dismissButton={TextButton(onClick={input=false}) {Text("취소")}})
    if(confirmSave) AvatarApplicationDialog(previous,candidate,onDismiss={confirmSave=false},commitOnApprove=true,onApprove={
        confirmSave=false
        val stamped=candidate.copy(centimeters=body.copy(updatedAt=maxOf(System.currentTimeMillis(),body.createdAt)))
        if(onSave(stamped)) {
            lastSaved=json;message="cm 치수와 출처를 이 기기에 저장했어요."
            if(applyAfterSave) {onDismiss();onApplyToFitting?.invoke()}
        } else message="저장하지 못했어요. 기존 자료는 보존했고, 손상 안내와 입력 범위를 확인해주세요."
    })
    if(askReset) AlertDialog(onDismissRequest={askReset=false},title={Text("자동 초기 추정값으로 복원할까요?")},
        text={Text("지금 미리보기의 7개 부위만 초기 추정값으로 돌려요. 원본 치수와 저장된 프로필은 별도 저장 전까지 유지해요.")},
        confirmButton={TextButton(onClick={update(body.resetAll());askReset=false}) {Text("초기 추정 복원")}},
        dismissButton={TextButton(onClick={askReset=false}) {Text("취소")}})
    if(askLeave) AlertDialog(onDismissRequest={askLeave=false},title={Text("저장하지 않은 조절값이 있어요")},
        text={Text("닫으면 이번 조절 초안만 버려요. 기존 저장 프로필·사진·옷장은 그대로예요.")},
        confirmButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("cmDiscardDraft")) {Text("저장하지 않고 닫기")}},
        dismissButton={TextButton(onClick={askLeave=false}) {Text("계속 조절")}})

    Dialog(onDismissRequest=::leave,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Cream).systemBarsPadding().testTag("premiumAvatarEditor")) {
            val compact=maxHeight<540.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=::leave) {Icon(Icons.AutoMirrored.Filled.ArrowBack,contentDescription="3D 조절 닫기")}
                    Text("3D 체형 조절",style=MaterialTheme.typography.titleLarge,color=Brown)
                }
                Column(Modifier.weight(if(compact).45f else .58f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                    BodyAvatarViewport(body.parameters(),1f,Modifier.weight(1f).fillMaxWidth().testTag("cmAvatarViewport"),
                        resetKey=cameraReset,cameraYaw=yaw,onRotation={yaw=it},fitModel=true)
                    Row(horizontalArrangement=Arrangement.Center) {
                        listOf(0f to "정면",90f to "측면",180f to "후면").forEach {(angle,label)->
                            TextButton(onClick={yaw=angle},modifier=Modifier.testTag("cmCamera-${angle.toInt()}")) {Text(label)}
                        }
                        TextButton(onClick={yaw=0f;cameraReset++}) {Text("전체 보기")}
                    }
                }
                Column(Modifier.weight(if(compact).55f else .42f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        AvatarPart.entries.forEach {part->FilterChip(selected==part,{selectedName=part.name},label={Text(part.label)},
                            modifier=Modifier.testTag("cmPart-${part.name}"))}
                    }
                    Text("${selected.label} ${selected.unit}",style=MaterialTheme.typography.titleMedium,color=Brown,
                        modifier=Modifier.testTag("cmPartLabel"))
                    Text(body.values.getValue(selected).origin.label,style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.testTag("cmValueOrigin"))
                    CmRuler(body[selected],range,"${selected.label} ${selected.unit}",::adjust,::openInput)
                    Text("초기 추정 ${body.initial.getValue(selected)} cm · 실제 측정값에 맞게 조절해주세요.",style=MaterialTheme.typography.bodySmall)
                    body.references[selected]?.let {Text("불러온 원본 ${it.cm} cm · ${it.origin.label}",style=MaterialTheme.typography.bodySmall)}
                    message?.let {Text(it,modifier=Modifier.testTag("cmEditorMessage"))}
                    if(parsed==null)Text("초안 형식을 복원하지 못해 초기 미리보기를 사용해요. 저장된 원본은 유지했어요.")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={askReset=true}) {Text("초기 추정값 복원")}
                        TextButton(onClick={json=initialJson;message=null}) {Text("불러온 설정 복원")}
                    }
                    Button(onClick={applyAfterSave=false;confirmSave=true},modifier=Modifier.fillMaxWidth().testTag("cmEditorSave")) {Text("비교 후 3D 설정 저장")}
                    if(onApplyToFitting!=null)OutlinedButton(onClick={applyAfterSave=true;confirmSave=true},
                        modifier=Modifier.fillMaxWidth().testTag("cmApplyFitting")) {Text("저장 후 가상피팅에 적용")}
                    TextButton(onClick={details=!details}) {Text(if(details)"출처·형상 가정 접기" else "출처·형상 가정 보기")}
                    if(details) {
                        Text("키 ${body.heightCm} cm · 몸무게 ${body.weightKg} kg. 자동 초기값은 검증된 평균이 아닌 임시 형상이에요.")
                        Text("깊이는 타원 가정이며 실제 신체 스캔·의복 물리·정확한 착용 핏이 아니에요.")
                        Text("가슴은 기본 깊이/너비 0.72를 쓰고 어깨 폭을 넘으면 깊이를 늘려 둘레를 맞춰요(최대 1.6). 허리 0.70, 힙 0.76, 허벅지 0.92도 표시 가정이에요.")
                        if(selected in setOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH)) {
                            val radii=body.radii(selected)
                            Text(String.format(Locale.KOREA,"표시용 타원: 너비 %.1f / 깊이 %.1f / 계산 둘레 %.1f cm. 실제 피부 표면·메시 수치 근사와는 구분해요.",
                                radii.first*2,radii.second*2,AvatarCentimeters.circumference(radii.first,radii.second)))
                        } else if(selected==AvatarPart.SHOULDER)Text("어깨는 모델의 좌우 어깨 기준점 간 너비예요. 팔을 포함한 최대 외곽 폭과 달라요.")
                        Text("모델 표시 범위는 의학적 신체 정상 범위가 아니에요. 지원하지 않는 조합의 원본 치수는 그대로 보존해요.")
                        body.photoFrontWidths.forEach {(key,value)->Text("사진 ${if(key=="waist")"허리" else "힙"} 정면 너비 $value cm · 둘레로 변환하지 않음")}
                        body.references.forEach {(part,value)->if(body[part]!=value.cm)Text("${part.label} 원본 ${value.cm} cm는 표시 단위/범위 차이로 현재 ${body[part]} cm와 달라요.")}
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
