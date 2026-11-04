package com.jeiu.fashionproject.ui.screens

import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.jeiu.fashionproject.BuildConfig
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.components.TryOnComparisonDialog
import com.jeiu.fashionproject.ui.theme.Brown
import com.jeiu.fashionproject.ui.theme.Camel
import com.jeiu.fashionproject.ui.theme.Cream
import com.jeiu.fashionproject.ui.viewmodel.TryOnViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun sharedLocalVtonViewModel(): TryOnViewModel {
    val context=LocalContext.current
    val activity=generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<ComponentActivity>().firstOrNull()
    return viewModel(viewModelStoreOwner=activity ?: checkNotNull(LocalViewModelStoreOwner.current),
        key="shared-local-vton",factory=remember { viewModelFactory {
            initializer { TryOnViewModel(createSavedStateHandle().also { it["engine"]="local" }) }
        } })
}

/** Own server + credentials + activity-scoped request ID. No paid fallback. */
@Composable
fun LocalVtonScreen(defaultPhoto: Uri?,garments: List<TryOnGarment>,onBack: () -> Unit,
    model: TryOnViewModel=sharedLocalVtonViewModel()) {
    check(model.localMode) { "A separate local engine ViewModel is required" }
    var showOfficialDemo by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val state=model.state
    var chosenPhoto by rememberSaveable { mutableStateOf(defaultPhoto?.toString()) }
    var chosenId by rememberSaveable { mutableStateOf(garments.singleOrNull()?.id) }
    LaunchedEffect(garments) {
        if (garments.none { it.id == chosenId }) chosenId = garments.singleOrNull()?.id
    }
    val selected=if(state.pending) model.selectedGarments else garments.filter { it.id==chosenId }
    val person=if(state.pending) model.selectedPerson else chosenPhoto?.let(Uri::parse) ?: model.selectedPerson
    val issue=LocalVtonRepository.selectionIssue(selected)
    val currentResult=state.result?.takeIf { model.resultMatches(person,selected) }
    var ready by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var connect by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf(LocalVtonSession.connection?.base.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var allowLoopback by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    var stopTracking by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    var originalVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val connection=LocalVtonSession.connection

    suspend fun checkServer() {
        val snapshot=LocalVtonSession.connection
        ready=false
        if(snapshot==null) { status="자체 AI 서버 미설정 · 유료 서비스로 자동 전환하지 않아요.";return }
        checking=true
        try {
            model.gateway.check(snapshot)
            if(LocalVtonSession.connection==snapshot) {
                ready=true;status="자체 AI 서버 사전 준비 확인 · 첫 요청에서 모델을 로딩해요."
            }
        } catch(cancelled: CancellationException) { throw cancelled
        } catch(error: Exception) {
            if(LocalVtonSession.connection==snapshot) status=if(error is TryOnFailure || error is TryOnHttpFailure)
                error.message else "자체 AI 서버에 연결하지 못했어요. 주소·인증·GPU 준비를 확인해주세요."
        } finally { checking=false }
    }
    LaunchedEffect(connection) { checkServer() }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null && !state.pending) chosenPhoto=uri.toString()
    }
    fun save(target: Uri?,png: Boolean) {
        val bytes=currentResult ?: return
        if(target==null || saving) return
        saving=true
        scope.launch {
            try { LocalVtonRepository.save(context,bytes,target,png);saveMessage="자체 AI 결과를 저장했어요."
            } catch(cancelled: CancellationException) { throw cancelled
            } catch(_: Exception) { saveMessage="결과를 저장하지 못했어요. 저장 위치를 확인해주세요."
            } finally { saving=false }
        }
    }
    val pngPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { save(it,true) }
    val jpgPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { save(it,false) }

    // Keep the private-server screen's input draft remembered while visiting the separate public mode.
    if (BuildConfig.DEBUG && BuildConfig.PREVIEW_BUILD && showOfficialDemo) {
        OfficialDemoScreen(onBack = { showOfficialDemo = false })
        return
    }

    if(connect) AlertDialog(onDismissRequest={token="";connect=false},title={Text("자체 AI 서버 연결")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("별도 개발 GPU 서버 주소와 자체 서버 전용 개인 토큰을 입력해요. FASHN API 키나 기존 백업 복구 코드를 입력하지 마세요. 토큰은 메모리에만 보관해요.")
            OutlinedTextField(base,{base=it},label={Text("자체 서버 HTTPS 주소")},singleLine=true)
            OutlinedTextField(token,{token=it},label={Text("자체 서버 개인 토큰")},singleLine=true,
                visualTransformation=PasswordVisualTransformation())
            if(BuildConfig.DEBUG) Row {
                Checkbox(allowLoopback,{allowLoopback=it})
                Text("개발용 루프백 HTTP만 허용 (USB reverse/에뮬레이터). 외부 접속에는 HTTPS 필요")
            }
        }},
        confirmButton={TextButton(onClick={
            if(LocalVtonSession.configure(base,token,allowLoopback)) { token="";connect=false }
            else status="HTTPS 주소와 32~256자 개인 토큰을 확인해주세요. 개발 HTTP는 루프백 주소만 허용돼요."
        }) { Text("설정 확인") }},
        dismissButton={TextButton(onClick={token="";connect=false}) { Text("닫기") }})
    if(review) AlertDialog(onDismissRequest={review=false},title={Text("자체 AI 서버로 사진을 전송할까요?")},
        text={Text("선택한 원본 전신 사진과 의류 한 벌을 직접 지정한 자체 AI 서버에 전송해요. 유료 FASHN API로 보내지 않아요. 연구·평가용 모델이며 얼굴·손·무늬·실제 핏 보존을 보장하지 않아요. 원본 임시 파일은 작업 종료/취소 시 제거하고 결과는 최대 5분 보관해요. 생성이 실패해도 가짜 결과나 자동 재요청을 만들지 않아요.")},
        confirmButton={TextButton(onClick={
            review=false;originalVisible=false
            model.generate(context,person,selected,approved=true)
        },modifier=Modifier.testTag("localVtonConsentConfirm")) {Text("동의하고 자체 AI 생성")}},
        dismissButton={TextButton(onClick={review=false}) {Text("전송하지 않기")}})
    if(compare && currentResult!=null) TryOnComparisonDialog(state.resultPerson,currentResult,
        state.resultFromPreviousAttempt) {compare=false}
    if(stopTracking) AlertDialog(onDismissRequest={stopTracking=false},title={Text("기기에서 작업 추적을 종료할까요?")},
        text={Text("서버 작업이 취소됐다는 뜻은 아니에요. 먼저 서버 상태를 확인하고, 새 생성은 별도 동의 후 시작해요. 자동 재요청하지 않아요.")},
        confirmButton={TextButton(onClick={stopTracking=false;model.stopLocalTracking()}) {Text("추적만 종료")}},
        dismissButton={TextButton(onClick={stopTracking=false}) {Text("계속 확인")}})

    Column(Modifier.fillMaxSize().background(Cream)) {
        TextButton(onClick=onBack) {Text("외부 AI/수동 모드로 돌아가기")}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("자체 AI 착장 이미지 생성",style=MaterialTheme.typography.titleLarge,color=Brown,
                modifier=Modifier.testTag("localVtonTitle"))
            Text("FASHN VTON v1.5 · 별도 개발 서버 · 유료 API 자동 호출 없음")
            if (BuildConfig.DEBUG && BuildConfig.PREVIEW_BUILD) OutlinedButton(
                onClick = { showOfficialDemo = true }, modifier = Modifier.testTag("officialDemoEntry")) {
                Text("공식 공개 데모 연동 · 예제 전용")
            }
            Text("전신 사진 → 옷장에서 한 벌 → 서버 준비 확인 → 전송 동의 → 생성·비교·저장",
                style=MaterialTheme.typography.bodySmall,color=Brown)
            Text("마법봉의 2D 위치 맞춤과 다른 모델 생성 경로예요. 결과가 없으면 생성 성공으로 표시하지 않아요.")
            Text(status ?: "자체 AI 서버 미설정",color=Brown,modifier=Modifier.testTag("localVtonServerState"))
            TextButton(onClick={connect=true},enabled=!state.pending) {Text("자체 AI 서버 설정")}
            OutlinedButton(onClick={scope.launch {checkServer()}},enabled=!checking && !state.watching) {
                Text(if(checking) "서버 준비 확인 중…" else "자체 서버 준비 다시 확인")
            }
            OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                enabled=!state.pending) {Text("원본 전신 사진 선택")}
            Text("얼굴·양쪽 어깨·발목까지 보이는 정면 사진과 의류 전체가 보이는 사진을 사용해요.")
            Text("옷장에서 의류 한 벌 선택",style=MaterialTheme.typography.titleMedium,color=Brown)
            (if(state.pending) model.selectedGarments else garments).forEach { garment ->
                Row {
                    RadioButton(selected=chosenId==garment.id,onClick={chosenId=garment.id},
                        enabled=!state.pending && TryOnPlan.categoryFor(garment.category)!=null,
                        modifier=Modifier.testTag("localGarment-"+garment.id))
                    Text(garment.category+" · "+garment.name)
                }
            }
            issue?.let {Text(it)}
            Text("의류 사진 유형")
            listOf("flat-lay" to "바닥/상품 단독 사진","model" to "사람이 착용한 의류 사진").forEach { (value,label) ->
                Row {
                    RadioButton(LocalVtonSession.garmentPhotoType==value,
                        onClick={LocalVtonSession.selectPhotoType(value)},enabled=!state.pending)
                    Text(label)
                }
            }
            if(state.watching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress ?: "자체 모델 생성 준비 중")
            }
            if(state.pending) {
                Text("화면을 나갔다 돌아와도 같은 작업만 조회해요. 새 생성 요청을 자동으로 보내지 않아요.")
                if(!state.watching) {
                    OutlinedButton(onClick=model::resume,enabled=connection!=null) {Text("기존 자체 AI 작업 조회")}
                    TextButton(onClick={stopTracking=true}) {Text("자체 작업 상태 추적 종료")}
                }
                TextButton(onClick=model::cancel,enabled=connection!=null) {Text("자체 AI 작업 취소")}
            }
            state.inputWarnings.forEach {Text(it)}
            state.error?.let {Text(it,modifier=Modifier.testTag("localVtonError"))}
            val original=if(currentResult!=null) state.resultPerson ?: person?.toString() else person?.toString()
            val shown: Any?=if(currentResult!=null && !originalVisible) currentResult else original
            if(shown!=null) AsyncImage(shown,if(state.result!=null && !originalVisible) "자체 AI 생성 결과" else "원본 사진",
                contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(320.dp))
            if(state.result!=null && currentResult==null) Text("이전 사진·옷의 자체 AI 결과는 현재 선택에 표시하지 않아요. 새 생성에는 별도 동의가 필요해요.")
            if(currentResult!=null) {
                Text(if(state.resultFromPreviousAttempt) "이전 자체 AI 결과 · 이번 요청은 실패/진행 중일 수 있어요"
                    else "자체 AI 생성 결과 · 실제 착용·정체성 보존 미보장",modifier=Modifier.testTag("localVtonSuccess"))
                OutlinedButton(onClick={originalVisible=!originalVisible}) {Text("자체 AI 원본/결과 비교")}
                OutlinedButton(onClick={compare=true}) {Text("자체 AI 확대 비교")}
                OutlinedButton(onClick={pngPicker.launch("local-vton-"+System.currentTimeMillis()+".png")},enabled=!saving) {Text("PNG 저장")}
                OutlinedButton(onClick={jpgPicker.launch("local-vton-"+System.currentTimeMillis()+".jpg")},enabled=!saving) {Text("JPG 저장")}
            }
            saveMessage?.let {Text(it)}
            Text("Human Parser는 NVIDIA SegFormer 연구·평가용 비상업 라이선스를 따라요. 3D 아바타 스크린샷을 전신 사진 대신 보내지 않아요.")
        }
        Button(onClick={review=true},enabled=ready && person!=null && issue==null && !state.pending && !saving,
            colors=ButtonDefaults.buttonColors(containerColor=Camel),
            modifier=Modifier.fillMaxWidth().padding(16.dp).testTag("localVtonGenerate")) {Text("자체 AI 생성 시작 · 의류 한 벌")}
    }
}
