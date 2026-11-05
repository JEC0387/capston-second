package com.jeiu.fashionproject.ui.screens

import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.components.TryOnComparisonDialog
import com.jeiu.fashionproject.ui.viewmodel.TryOnViewModel
import kotlinx.coroutines.*

/** Activity scope intentionally survives navigation away from either fitting entry point. */
@Composable
fun sharedTryOnViewModel(): TryOnViewModel {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<ComponentActivity>().firstOrNull()
    return viewModel(viewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current), key = "shared-ai-tryon")
}

/** Original photos only. No screenshots or manual placement coordinates are sent. */
@Composable
fun AiTryOnScreen(
    defaultPhoto: Uri?, garments: List<TryOnGarment>, onBack: () -> Unit,
    serviceConfigured: Boolean = true,
    model: TryOnViewModel = sharedTryOnViewModel(),
    extraContent: @Composable () -> Unit = {}
) {
    var showLocalMode by rememberSaveable { mutableStateOf(false) }
    if (showLocalMode) {
        LocalVtonScreen(defaultPhoto, garments, onBack = { showLocalMode = false })
        return
    }
    val context = LocalContext.current
    val configured = serviceConfigured && TryOnRepository.isConfigured()
    val scope = rememberCoroutineScope()
    val state = model.state
    LaunchedEffect(model) { model.markResultAsPrevious() }
    val selection = if ((state.pending || garments.isEmpty()) && model.selectedGarments.isNotEmpty()) model.selectedGarments else garments
    val plan = remember(selection) { TryOnPlan.build(selection) }
    var personText by rememberSaveable { mutableStateOf(defaultPhoto?.toString()) }
    val person = if (state.pending) model.selectedPerson ?: personText?.let(Uri::parse)
        else personText?.let(Uri::parse) ?: model.selectedPerson
    val currentResult = state.result?.takeIf { model.resultMatches(person, selection) }
    var zoomComparison by remember { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var review by remember { mutableStateOf<Pair<Uri, List<TryOnGarment>>?>(null) }
    var showConnection by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf(TryOnSession.connection?.base ?: BackendApi.BASE_URL.takeIf { it.startsWith("https://") }.orEmpty()) }
    var token by remember { mutableStateOf("") } // never saved, logged, or backed up
    var connectionError by remember { mutableStateOf<String?>(null) }
    var connectionCheck by remember { mutableStateOf<String?>(null) }
    var checkingConnection by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var exportSnapshot by remember { mutableStateOf<ByteArray?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    fun leave() { review = null; onBack() }
    BackHandler(onBack = ::leave)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo ->
        if (photo != null && !state.pending) { review = null; personText = photo.toString(); saveMessage = null }
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val source = exportSnapshot
        exportSnapshot = null
        if (target != null && source != null && !saving) {
            saving = true
            scope.launch {
                try {
                    TryOnRepository.saveResult(context, source, target)
                    saveMessage = "AI 결과 이미지를 저장했어요."
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { saveMessage = "이미지를 저장하지 못했어요. 다시 시도해주세요."
                } finally { saving = false }
            }
        } else if (target != null && source == null) {
            saveMessage = "저장할 결과 연결이 사라졌어요. 결과를 확인하고 저장을 다시 선택해주세요."
        }
    }
    if (showConnection) AlertDialog(
        onDismissRequest = { token = ""; showConnection = false },
        title = { Text("AI 서버 연결") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("운영자가 발급한 개인 접속 토큰을 입력해주세요. FASHN API 키를 입력하지 마세요. 토큰은 앱을 종료하면 지워져요.")
            OutlinedTextField(base, { base = it }, label = { Text("HTTPS 서버 주소") }, singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("개인 접속 토큰") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            connectionError?.let { Text(it) }
        } },
        confirmButton = { TextButton(onClick = {
            if (TryOnSession.configure(base, token)) {
                token = ""; showConnection = false; connectionError = null; connectionCheck = null
                if (model.state.pending) model.resume() // GET only, never generation
            } else connectionError = "HTTPS 주소와 32자 이상의 개인 접속 토큰을 확인해주세요."
        }) { Text("연결 설정") } },
        dismissButton = { TextButton(onClick = { token = ""; showConnection = false }) { Text("닫기") } }
    )
    review?.let { approved ->
        val approvedPlan = TryOnPlan.build(approved.second)
        AlertDialog(onDismissRequest = { review = null }, title = { Text("외부 AI 서비스에 사진을 전송할까요?") },
            text = { Text("선택한 원본 인물 사진과 의류 사진을 앱 서버를 거쳐 FASHN(api.fashn.ai)에 전송해요. 최대 ${approvedPlan.requestCount}회 유료 생성 요청이며 여러 벌은 중간 결과를 다시 전송해요. 현재 요금·크레딧 정책을 확인해주세요. 재생성은 추가 비용이 발생할 수 있어요. 수동 좌표와 얼굴·체형·세부 디자인·실제 핏은 보장하지 않아요. 서버의 사진·결과는 메모리에서만 잠시 처리하고 결과는 최대 30분 보관해요. FASHN 공식 정책상 base64 입력은 처리 후 삭제되며 정리 지연 시 하루까지 남을 수 있어요. base64 결과는 완료 후 최대 60분 제공되며 요청 이력은 자동 삭제되지 않아요. 실제 처리는 외부 서비스 정책을 따라요. 체형분석·기록 저장 동의와 별개이며 거절해도 수동 피팅을 사용할 수 있어요.", modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                review = null; showOriginal = false; saveMessage = null
                model.generate(context, approved.first, approved.second, approved = true)
            }, modifier = Modifier.testTag("aiTryOnConfirm")) { Text("확인하고 AI 생성") } },
            dismissButton = { TextButton(onClick = { review = null }) { Text("전송하지 않기") } })
    }
    if (zoomComparison && currentResult != null) TryOnComparisonDialog(state.resultPerson, currentResult,
        state.resultFromPreviousAttempt) { zoomComparison = false }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Brown) }
            Text("AI로 입어보기", style = MaterialTheme.typography.titleLarge, color = Brown, modifier = Modifier.padding(top = 10.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI 착장 사진 생성 · 외부 FASHN 서비스(유료)", color = Brown)
            Text("1. 전신 사진 선택  →  2. 옷장 의류 확인  →  3. AI 서버 연결  →  4. 생성 전 동의",
                style = MaterialTheme.typography.bodySmall, color = MediumGray)
            OutlinedButton(onClick = { showLocalMode = true }, modifier = Modifier.testTag("openLocalVton")) {
                Text("자체 AI 모델 사용하기 (별도 GPU 서버 필요)")
            }
            Text(if (configured) "외부 유료 AI 서버가 연결됐어요. 사진 전송 전 동의를 거친 뒤에만 생성해요."
                else "현재 외부 AI 서버가 연결되지 않았어요. 사진·옷을 선택해도 실제 착장 이미지를 생성할 수는 없어요.",
                color = Brown, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("aiTryOnGuide"))
            Text("관절 자동 정렬과 별도 기능이에요. 수동 드래그·회전 좌표는 AI에 전달되지 않아요.", color = MediumGray)
            OutlinedButton(enabled = !state.pending && !saving, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (person == null) "전신 사진 선택" else "전신 사진 바꾸기") }
            val original = if (currentResult != null) state.resultPerson?.let(Uri::parse) ?: person else person
            val shown: Any? = if (currentResult != null && !showOriginal) currentResult else original
            if (shown != null) AsyncImage(shown, if (currentResult != null && !showOriginal) "AI 생성 결과" else "원본 전신 사진",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(340.dp).testTag("aiTryOnPreview"))
            if (selection.isEmpty()) {
                Text("먼저 수동 배치 화면에서 입어볼 옷을 선택해주세요.", color = Brown)
                TextButton(onClick = ::leave) { Text("옷 선택으로 돌아가기") }
            }
            Text("상의는 정면 상반신, 하의·원피스는 발목까지 보이는 전신 사진을 사용해요. 의류 원본을 우선 사용하며 누끼만 있으면 흰 배경으로 처리해요.", color = MediumGray)
            selection.forEach { Text("${it.category} · ${it.name}", color = Brown) }
            if (plan.unsupported.isNotEmpty()) Text("신발·액세서리는 이번 AI 생성에서 제외돼요. 수동 배치는 계속 사용할 수 있어요.", color = MediumGray)
            plan.issue?.let { Text(it, color = Brown) }
            if (!configured) Text("AI 생성에는 별도 서버 주소와 운영자가 발급한 개인 토큰이 필요해요. 서버가 준비되지 않았다면 생성 버튼이 비활성화되는 것이 정상이에요. 유료 서비스는 사용자 동의 없이 실행하지 않아요.",
                color = Brown, modifier = Modifier.testTag("aiTryOnNotConfigured"))
            TextButton(enabled = !state.watching, onClick = { showConnection = true }) { Text("AI 서버 연결 설정") }
            if (configured) TextButton(enabled = !state.watching && !checkingConnection, onClick = {
                val connection = TryOnSession.connection ?: return@TextButton
                checkingConnection = true
                scope.launch {
                    try {
                        TryOnRepository.check(connection) // GET /config only: no images or paid generation.
                        connectionCheck = "AI 서버 인증·설정 확인 완료 · 아직 이미지를 생성하지 않았어요."
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: TryOnHttpFailure) {
                        connectionCheck = when (failure.code) {
                            401, 403 -> "AI 서버 인증 거절 · 운영자가 발급한 AI 전용 접속 토큰을 확인해주세요."
                            404 -> "서버에 AI 피팅 API가 없어요. 운영 서버 버전을 확인해주세요."
                            503 -> "서버의 AI 키·사용량 보호 설정이 준비되지 않았어요. 운영자 확인이 필요해요."
                            else -> "AI 서버 확인 실패 (HTTP ${failure.code}) · 생성 요청은 보내지 않았어요."
                        }
                    } catch (_: Exception) {
                        connectionCheck = "AI 서버에 연결하지 못했어요. HTTPS 주소와 인터넷을 확인해주세요. 생성 요청은 보내지 않았어요."
                    } finally { checkingConnection = false }
                }
            }) { Text(if (checkingConnection) "AI 서버 확인 중…" else "AI 서버 준비 확인 · 생성 없음") }
            connectionCheck?.let { Text(it, color = Brown, modifier = Modifier.testTag("aiConnectionCheck")) }
            if (state.watching) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Camel)
                Text(state.progress ?: "AI가 옷을 자연스럽게 입히는 중이에요", color = Brown)
            }
            if (state.pending) {
                Text("화면을 나가도 기존 작업을 이어서 확인해요. 이미 전송된 요청·요금은 취소되지 않을 수 있어요.", color = MediumGray)
                if (!state.watching) OutlinedButton(onClick = model::resume, enabled = configured) { Text("기존 작업 상태 확인") }
                TextButton(onClick = model::cancel, enabled = configured) { Text("생성 대기 중단") }
            }
            state.inputWarnings.forEach { Text(it, color = MediumGray, modifier = Modifier.testTag("aiTryOnInputHint")) }
            state.error?.let { Text(it, color = Brown, modifier = Modifier.testTag("aiTryOnError")) }
            if (state.result != null && currentResult == null) Text("이전 사진 또는 다른 옷의 AI 결과는 현재 선택에 표시하지 않아요. 다시 생성하려면 새 동의가 필요해요.", color = MediumGray)
            if (currentResult != null) {
                Text(if (state.resultFromPreviousAttempt) "이전 AI 생성 결과 · 이번 요청의 성공 결과가 아니에요" else "AI 생성 결과 · 얼굴·체형·실제 핏은 보장하지 않아요", color = Brown, modifier = Modifier.testTag("aiTryOnSuccess"))
                OutlinedButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "AI 결과 보기" else "원본과 비교") }
                OutlinedButton(onClick = { zoomComparison = true }, modifier = Modifier.testTag("aiTryOnZoom")) { Text("확대해서 비교") }
                OutlinedButton(enabled = !saving && exportSnapshot == null, onClick = {
                    exportSnapshot = currentResult.copyOf()
                    saveMessage = null
                    try { savePicker.launch("ai-fitting-${System.currentTimeMillis()}.jpg") }
                    catch (_: Exception) { exportSnapshot = null; saveMessage = "저장 위치 선택 화면을 열지 못했어요." }
                }) { Text(if (saving) "저장 중…" else "결과 이미지 저장") }
            }
            saveMessage?.let { Text(it, color = Brown) }
            extraContent()
            TextButton(onClick = ::leave) { Text("수동 피팅으로 돌아가기") }
        }
        Button(modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp).testTag("aiTryOnGenerate"),
            enabled = configured && person != null && plan.issue == null && !state.pending && !saving && exportSnapshot == null,
            colors = ButtonDefaults.buttonColors(containerColor = Camel), onClick = {
                person?.let { review = it to selection.toList() }
            }) {
            Icon(Icons.Filled.AutoFixHigh, null); Spacer(Modifier.width(6.dp))
            Text(if (state.pending) "AI 생성 중…" else if (currentResult != null || state.error != null) "다시 만들기 (${plan.requestCount}회 요청)" else "AI로 입어보기 (${plan.requestCount}회 요청)")
        }
    }
}
