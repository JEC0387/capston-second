package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.core.net.toUri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.theme.*
import com.jeiu.fashionproject.ui.viewmodel.PhotoTransferConsent
import kotlinx.coroutines.*

/** Separate from the manual canvas: the API receives the original inputs, never drag coordinates. */
@Composable
fun AiTryOnScreen(defaultPhoto: Uri?, garments: List<TryOnGarment>, onBack: () -> Unit, serviceConfigured: Boolean = TryOnRepository.hasKey()) {
    val context = LocalContext.current
    val configured = serviceConfigured && TryOnRepository.hasKey()
    val scope = rememberCoroutineScope()
    val plan = remember(garments) { TryOnPlan.build(garments) }
    val consent = remember { PhotoTransferConsent() }
    var person by remember { mutableStateOf(defaultPhoto) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableStateOf<Job?>(null) }
    fun leave() { consent.cancel(); generation?.cancel(); onBack() }
    BackHandler(onBack = ::leave)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo ->
        if (photo != null && progress == null) {
            consent.cancel(); person = photo; result = null; error = null; saveMessage = null
        }
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val source = result
        if (target != null && source != null && !saving) {
            saving = true; saveMessage = null
            scope.launch {
                try {
                    TryOnRepository.saveResult(context, source, target)
                    saveMessage = "AI 결과 이미지를 저장했어요."
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { saveMessage = "이미지를 저장하지 못했어요. 다시 시도해주세요."
                } finally { saving = false }
            }
        }
    }
    fun start() {
        val photo = person ?: return
        if (progress != null || plan.issue != null || !configured || !consent.consume(photo.toString())) return
        // Set before launching to prevent a second paid request from a rapid tap.
        progress = "사진 준비 중…"; error = null; result = null; saveMessage = null; showOriginal = false
        generation = scope.launch {
            try {
                result = withTimeout(180_000L * plan.requestCount) {
                    val input = TryOnRepository.uriToDataUri(context, photo)
                    TryOnBatch.run(plan, input, generate = { current, garment ->
                        val image = if (TryOnBatch.isResultUrl(garment.image)) garment.image
                            else TryOnRepository.uriToDataUri(context, garment.image.toUri())
                        TryOnRepository.tryOn(current, image, TryOnPlan.categoryFor(garment.category)!!)
                    }, progress = { index, total -> progress = "AI가 옷을 입혀주고 있어요… ($index/$total)" })
                }
            } catch (_: TimeoutCancellationException) {
                error = "AI 피팅이 오래 걸려 대기를 종료했어요. 서버 작업·차감 여부를 확인한 뒤 재시도해주세요."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: TryOnFailure) { error = failure.message
            } catch (_: Exception) { error = "사진 또는 네트워크를 확인한 뒤 다시 시도해주세요."
            } finally { progress = null }
        }
    }
    if (showConsent) {
        AlertDialog(
            onDismissRequest = { consent.cancel(); showConsent = false },
            title = { Text("외부 AI 서비스에 사진을 전송할까요?") },
            text = { Text("FASHN(api.fashn.ai)에 전신 사진과 선택한 의류 이미지를 전송해요. ${plan.requestCount}벌을 순서대로 생성하므로 최대 ${plan.requestCount}회 생성 요청하며 중간 결과를 다시 전송해요. 재시도는 추가 요청이에요. 요금·크레딧은 현재 서비스 정책을 따르며, 얼굴·체형·의상 세부와 실제 제품 핏을 보장하지 않아요. 드래그 위치는 AI에 전달하지 않아요. 체형분석·기록 저장 동의와는 별개예요.") },
            confirmButton = { TextButton(onClick = {
                showConsent = false
                person?.let { if (consent.confirm(it.toString())) start() }
            }) { Text("확인하고 AI 생성") } },
            dismissButton = { TextButton(onClick = { consent.cancel(); showConsent = false }) { Text("전송하지 않기") } }
        )
    }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Brown) }
            Text("AI로 입어보기", style = MaterialTheme.typography.titleLarge, color = Brown, modifier = Modifier.padding(top = 10.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (configured) "원하는 옷을 선택하고 마법봉을 누르면 AI가 자연스럽게 입혀줘요 ✨"
                else "AI 서비스 연결 전에는 수동 배치로 옷의 위치와 크기를 조절할 수 있어요.",
                color = Brown, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("aiTryOnGuide"))
            Text("수동 배치와 별도 기능이에요. 드래그 위치는 AI 결과에 반영되지 않으며 결과는 실제 제품의 핏을 보장하지 않아요.", color = MediumGray, style = MaterialTheme.typography.labelSmall)
            OutlinedButton(enabled = progress == null && !saving, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (person == null) "전신 사진 선택" else "전신 사진 바꾸기") }
            if (person != null) AsyncImage(
                model = if (showOriginal || result == null) person else result,
                contentDescription = if (showOriginal || result == null) "원본 전신 사진" else "AI 생성 결과",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(340.dp))
            if (garments.isEmpty()) {
                Text("먼저 수동 배치 화면에서 입어볼 옷을 선택해주세요.", color = Brown)
                TextButton(onClick = ::leave) { Text("옷 선택으로 돌아가기") }
            }
            Text("정면 전신 사진과 옷이 잘 보이는 상품 사진을 사용해주세요. 배경이 있는 상품은 원본을 전송하며 AI 처리 가능 여부는 서비스 응답으로 확인해요.",
                color = MediumGray, style = MaterialTheme.typography.labelSmall)
            garments.forEach { Text("${it.category} · ${it.name}", color = Brown, style = MaterialTheme.typography.bodySmall) }
            if (plan.unsupported.isNotEmpty()) Text("신발·액세서리는 이번 AI 생성에서 제외돼요. 수동 배치는 계속 사용할 수 있어요.", color = MediumGray)
            plan.issue?.let { Text(it, color = Brown) }
            when {
                !configured -> Text("AI 피팅 서비스가 아직 연결되지 않았어요. 수동 배치는 계속 사용할 수 있어요.", color = Brown, modifier = Modifier.testTag("aiTryOnNotConfigured"))
                progress != null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Camel)
                    Text(progress!!, color = Brown)
                    Text("의류마다 수십 초 이상 걸릴 수 있어요. 화면을 나가도 이미 전송된 서버 작업이나 요금이 취소되는 것은 아니에요.", color = MediumGray)
                }
                error != null -> {
                    Text("AI 피팅을 완료하지 못했어요.", color = Brown)
                    Text(error!!, color = MediumGray)
                }
                result != null -> {
                    Text("AI 피팅이 완료됐어요! ✨", color = Brown)
                    OutlinedButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "AI 결과 보기" else "원본과 비교") }
                    OutlinedButton(enabled = !saving, onClick = { savePicker.launch("ai-fitting-${System.currentTimeMillis()}.jpg") }) { Text(if (saving) "저장 중…" else "결과 이미지 저장") }
                }
                else -> Text("전신 사진과 입어볼 옷을 선택해주세요.", color = MediumGray)
            }
            saveMessage?.let { Text(it, color = Brown) }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp).testTag("aiTryOnGenerate"),
            enabled = configured && person != null && plan.issue == null && progress == null && !saving,
            colors = ButtonDefaults.buttonColors(containerColor = Camel),
            onClick = {
                person?.let { consent.request(it.toString()); showConsent = true }
            }
        ) {
            Icon(Icons.Filled.AutoFixHigh, null); Spacer(Modifier.width(6.dp))
            Text(if (progress != null) "AI 생성 중…" else if (result != null || error != null) "다시 AI로 입어보기 (${plan.requestCount}회 요청)" else "AI로 입어보기 (${plan.requestCount}회 요청)")
        }
    }
}
