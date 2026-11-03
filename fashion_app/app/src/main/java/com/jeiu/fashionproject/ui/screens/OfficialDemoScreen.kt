package com.jeiu.fashionproject.ui.screens

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.*

@Composable
fun sharedOfficialDemoViewModel(): OfficialDemoViewModel {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().firstOrNull()
    return viewModel(viewModelStoreOwner = activity ?: checkNotNull(LocalViewModelStoreOwner.current),
        key = "official-public-demo", factory = remember { viewModelFactory {
            initializer { OfficialDemoViewModel(OfficialDemoRepository(), PrivateDemoJournal(context.applicationContext)) }
        } })
}

/** No photo, wardrobe, URI, URL or credential parameters and no image-picker launcher. */
@Composable
fun OfficialDemoScreen(onBack: () -> Unit, model: OfficialDemoViewModel = sharedOfficialDemoViewModel()) {
    val context = LocalContext.current
    val state = model.state
    var selected by rememberSaveable { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    // Entering with an existing job/result is a review of that trial, never a new generation.
    var previousTrial by remember { mutableStateOf(state.phase != "idle") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { model.save(context, it) }
    BackHandler(onBack = onBack)
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("공식 예제만 외부 서비스에서 실행") },
        text = { Text("Hugging Face의 FASHN 공개 Space에 공식 예제 ID에 해당하는 공개 사진 주소만 보내요. 내 사진·옷장·계정 토큰은 보내지 않아요. IP 등 접속 정보는 외부 서비스에 전달돼요. 무료 한도·대기열이 있고 보관·삭제·취소는 우리 자체 서버 정책과 달라요. 결과 품질·삭제 시점을 보장하지 않으며 유료 전환과 자동 재요청은 없어요. 예제·결과는 연구 시험용이며 무단 재배포하지 마세요.") },
        confirmButton = { TextButton(onClick = { consent = false; compare = false; previousTrial = false; model.start(OfficialDemoContract.EXAMPLE, true) },
            modifier = Modifier.testTag("officialDemoConsent")) { Text("동의하고 공식 예제 1회 생성") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("전송하지 않기") } })
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("새 시험 준비") },
        text = { Text("기존 결과가 이번 성공으로 표시되지 않도록 화면을 비워요. 서버 작업을 취소·삭제하는 기능은 아니에요. 마지막 전송 24시간 이후에만 새 시험을 허용해 반복 요청을 제한해요.") },
        confirmButton = { TextButton(onClick = { reset = false; compare = false; model.prepareNextTest(true) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("닫기") } })
    Column(Modifier.fillMaxSize().background(Cream)) {
        TextButton(onClick = onBack) { Text("자체 AI 화면으로 돌아가기") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("공식 공개 데모 연동 · 예제 전용", style = MaterialTheme.typography.titleLarge, color = Brown,
                modifier = Modifier.testTag("officialDemoTitle"))
            Text("개발 시험 모드 · 자체 서버 / 내 사진 AI 피팅과 별개")
            Text("fashn-ai/fashn-vton-1.5 · 공식 상의 예제만 사용 · 갤러리·카메라·사용자 옷장 입력 없음")
            Row {
                RadioButton(selected, onClick = { selected = true }, enabled = state.phase == "idle", modifier = Modifier.testTag("officialDemoExample"))
                Text("공식 예제 3 · 전신 인물 + 남색 상의")
            }
            Text("준비 확인 시 공개 서비스에 접속해요. 이용 조건: huggingface.co/terms-of-service · 개인정보: huggingface.co/privacy",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = model::check, enabled = state.phase == "idle" && !state.working,
                modifier = Modifier.testTag("officialDemoCheck")) { Text("공개 API 준비 확인") }
            Text(if (previousTrial && state.result != null && state.message.startsWith("공식 공개 데모의 새 요청 결과 수신"))
                "이전에 생성한 결과예요. 새 생성 요청 없이 비교·저장할 수 있어요."
                else state.message, modifier = Modifier.testTag("officialDemoStatus"))
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.working && state.result == null) TextButton(onClick = model::stopWaiting) { Text("앱에서 대기 종료 (서버 취소 아님)") }
            if (model.canResume) OutlinedButton(onClick = model::resume, modifier = Modifier.testTag("officialDemoResume")) { Text("같은 작업 결과 조회 · 재생성 없음") }
            if (state.result != null) {
                Text("공식 예제 생성 결과 · 의류 질감·신체 경계·핏 보존 미보장", modifier = Modifier.testTag("officialDemoSuccess"))
                Text("${state.result.width} × ${state.result.height} · SHA-256 ${state.result.sha256}")
                OutlinedButton(onClick = { compare = !compare }, modifier = Modifier.testTag("officialDemoCompare")) { Text("원본/결과 비교") }
            }
            val displayed = if (compare || state.result == null) state.original else state.result
            displayed?.let { AsyncImage(it.png, if (compare || state.result == null) "공식 예제 원본"
                else if (previousTrial) "이전에 생성한 결과" else "새 요청 생성 결과",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(320.dp).testTag("officialDemoImage")) }
            if (state.result != null) {
                OutlinedButton(onClick = { save.launch("official-example-" + System.currentTimeMillis() + ".png") },
                    enabled = !state.working, modifier = Modifier.testTag("officialDemoSave")) { Text("검수한 예제 결과 PNG 저장") }
            }
            if (state.savedAt != 0L) Text("PNG 저장 완료", modifier = Modifier.testTag("officialDemoSaved"))
            if (state.phase != "idle") TextButton(onClick = { reset = true }, enabled = !state.working) { Text("새 시험 준비 (24시간 제한)") }
            Text("대기 종료는 서버 취소·삭제가 아니에요. 재진입 시 같은 작업만 조회하며 응답 유실 시 POST를 반복하지 않아요.")
        }
        Button(onClick = { consent = true }, enabled = selected && model.canStart,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("officialDemoGenerate"),
            colors = ButtonDefaults.buttonColors(containerColor = Camel)) { Text("공식 예제로 생성 시험") }
    }
}
