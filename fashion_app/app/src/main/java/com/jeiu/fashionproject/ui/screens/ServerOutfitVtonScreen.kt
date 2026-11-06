package com.jeiu.fashionproject.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.BuildConfig
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitVtonViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Explicit server mode. Nothing here enables deployment, changes auth, or auto-uploads inputs. */
@Composable
internal fun ServerOutfitVtonScreen(selection: DeviceVtonSelection?, onBack: () -> Unit,
    model: ServerOutfitVtonViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by DataAuth.state.collectAsState()
    val state = model.state
    val inputsReady = model.inputReady(selection)
    var approved by remember(selection, state.config, state.configBinding, accountState, ServerOutfitQaConnection.revision) { mutableStateOf(false) }
    val thirdParty = state.config?.providerInfo
    val externalCancellation = state.ticket?.let(ServerOutfitVtonRepository::isGenlook)
        ?: (thirdParty?.remoteCancellationSupported == false)
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var comparison by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val visible = state.result?.takeIf { model.canDisplay() }
    val compareBytes = remember(comparison, visible) {
        if (!comparison) null else runCatching { visible?.let { it.original to it.png } }.getOrNull()
    }

    LaunchedEffect(model) { model.initialize(context) }
    LaunchedEffect(selection) { model.selectionChanged(selection); comparison = false; message = null }
    BackHandler(onBack = onBack)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bytes = pendingSave
        pendingSave = null
        if (uri != null && bytes != null && !saving) {
            saving = true
            scope.launch {
                try {
                    OnDeviceVtonImages.saveExact(context, bytes, uri)
                    message = "선택한 서버 결과 PNG를 저장했어요. 저장 이미지 열기로 파일을 확인해주세요."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "메모리 부족으로 저장을 완료하지 못했어요. 선택한 파일이 일부만 저장됐을 수 있어요." }
                catch (_: Exception) { message = "저장하지 못했어요. 기존 파일을 삭제하지 않았으며 선택한 파일의 완성 여부를 확인해주세요." }
                finally { saving = false }
            }
        } else if (uri != null) message = "저장할 결과 연결이 사라졌어요. 결과를 다시 확인해주세요."
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri != null && !reading) {
            reading = true; reopened = null
            scope.launch {
                try { reopened = LocalVtonRepository.openSavedImage(context, uri) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "저장 파일을 읽을 메모리가 부족해요." }
                catch (_: Exception) { message = "저장 파일의 형식·크기·접근 권한을 확인해주세요." }
                finally { reading = false }
            }
        }
    }

    Surface(Modifier.fillMaxSize().testTag("serverOutfitVtonScreen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("serverOutfitBack")) { Text("피팅으로 돌아가기") }
            Text("서버 AI · 3종 착장", style = MaterialTheme.typography.headlineSmall)
            Text("온디바이스 AI와 별도 모드예요. 운영 배포·모델 사용 권한·개인정보 처리 정책이 승인된 서버에서만 사용해요.")
            Text(when {
                ServerOutfitQaConnection.supported -> "격리 QA에서는 승인된 개발 서버와 별도 인증을 직접 설정한 경우에만 연결해요. 운영 서버·공개 연구 데모·유료 AI로 자동 전환하지 않아요."
                BuildConfig.PREVIEW_BUILD -> "현재 Preview에서는 운영 서버로 사진을 전송하지 않아요. 공개 연구 데모나 유료 AI로 자동 전환하지 않아요."
                else -> "승인된 서버 설정과 사용자 동의가 필요해요. 공개 연구 데모나 다른 유료 AI로 자동 전환하지 않아요."
            },
                style = MaterialTheme.typography.bodySmall)
            OutfitQaConnectionPanel(enabled = !state.busy && !saving, onChanged = model::invalidateConnection)
            DeviceGarmentRole.entries.forEach { role ->
                val count = selection?.garments?.count { it.role == role } ?: 0
                val label = when (role) { DeviceGarmentRole.TOP -> "상의"; DeviceGarmentRole.BOTTOM -> "하의"; DeviceGarmentRole.SHOES -> "신발" }
                Text("$label · ${if (count == 1) "선택됨" else "한 벌을 선택해주세요"}")
            }
            if (selection == null) Text("먼저 피팅 화면에서 모델 사진과 상의·하의·신발을 선택해주세요.")
            else if (state.config?.ready == true && !inputsReady)
                Text("기기에서 읽을 모델 사진과 서로 다른 상의·하의·신발을 한 벌씩 선택해주세요.")
            TextButton(onClick = model::refreshConfig, enabled = !state.busy && !saving,
                modifier = Modifier.testTag("serverOutfitCheck")) { Text("서버 준비 상태 확인") }
            Text(if (state.config?.ready == true) "서버 설정 준비됨 · 실제 생성 품질 미검증" else "서버 미준비 · 사진 전송 전 확인 필요",
                modifier = Modifier.testTag("serverOutfitReady"))
            state.config?.takeIf { it.ready }?.let {
                val host = state.configHost ?: "승인된 HTTPS 서버 없음"
                Text("사진 처리 서버: $host", style = MaterialTheme.typography.bodySmall)
                Text("처리 정책 ${it.policy} · 서버 결과 보관 ${it.retentionSeconds}초. 법정 기준이 아니라 승인된 서버 설정이에요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            thirdParty?.let {
                Text("외부 처리: Genlook · 스마트폰이 아닌 제3자 서버에서 실행", modifier = Modifier.testTag("serverOutfitProvider"))
                Text(when (it.mode) {
                    "clothing-pair-then-shoes" -> "상의·하의 함께 → 신발, 2회 생성이에요. 앞 단계 의류·얼굴·자세 보존과 전체 착장 품질은 실제 결과 검수가 필요해요."
                    "sequential-3" -> "상의 → 하의 → 신발, 3회 순차 생성이에요. 앞 단계 의류·얼굴·자세 보존과 전체 착장 품질은 실제 결과 검수가 필요해요."
                    else -> "생성 방식이 확인되지 않았어요. 서버 처리 조건을 다시 확인해주세요."
                }, modifier = Modifier.testTag("serverOutfitGenerationMode"))
                Text("요청당 최대 ${it.maxCreditsPerJob}크레딧을 예약해요. 실제 청구액·환불 확정이 아니며 자동 충전하지 않아요.",
                    modifier = Modifier.testTag("serverOutfitCost"))
                Text("Genlook 이미지 보관 설정 ${it.remoteRetentionDays}일 · 앱 서버 결과 보관 시간과 별도예요. 생성 기록·동의·백업 보관 및 삭제 제한은 아래 정책을 확인해주세요.",
                    modifier = Modifier.testTag("serverOutfitExternalRetention"))
                Text(it.notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("serverOutfitProviderNotice"))
                Text("공개된 원격 생성 취소 기능이 없어 대기 종료는 외부 작업 취소·환불·삭제 완료를 보장하지 않아요.",
                    modifier = Modifier.testTag("serverOutfitCancellationLimits"))
            }
            Row {
                Checkbox(approved, onCheckedChange = { approved = it }, enabled = !state.busy && state.config?.ready == true,
                    modifier = Modifier.testTag("serverOutfitConsent"))
                Text(if (thirdParty != null) "선택한 인물·상의·하의·신발 4장의 이미지와 중간 생성 결과를 승인된 앱 서버를 통해 Genlook으로 전송하고 표시된 비용·보관 정책으로 처리하는 데 동의해요."
                    else "선택한 인물·의류 3종을 표시된 정책의 서버로 전송하는 데 동의해요.",
                    Modifier.weight(1f).padding(top = 10.dp).testTag("serverOutfitConsentText"))
            }
            Button(onClick = { comparison = false; reopened = null; model.start(context, selection, approved) },
                enabled = inputsReady && approved && !state.busy && !saving && pendingSave == null && model.startAllowedByPreviousJob(),
                modifier = Modifier.fillMaxWidth().testTag("serverOutfitGenerate")) { Text("선택한 3종으로 서버 AI 생성") }
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("서버 작업: ${state.phase}. 화면 복귀로 새 생성 요청을 보내지 않아요.")
            }
            state.status?.takeIf { it.status == "running" && state.phase in setOf("running", "waiting") }?.providerProgress?.let { progress ->
                val stage = when (progress.mode) {
                    "clothing-pair-then-shoes" -> if (progress.stageIndex == 1) "상의·하의 함께" else "신발"
                    "sequential-3" -> when (progress.stageIndex) { 1 -> "상의"; 2 -> "하의"; else -> "신발" }
                    else -> "처리 단계 확인 필요"
                }
                val phase = when (progress.phase) {
                    "preparing" -> "입력 준비 중"
                    "uploading" -> "입력 전송 중"
                    "submitting" -> "생성 요청 접수 중"
                    "waiting_for_generation" -> "생성 결과 대기 중"
                    "validating_result" -> "반환 이미지 확인 중"
                    "finalizing" -> "최종 결과 정리 중"
                    else -> "진행 상태 확인 필요"
                }
                Text("${progress.stageIndex}/${progress.stageCount}단계 · $stage · $phase",
                    modifier = Modifier.testTag("serverOutfitStage"))
                Text("서버가 확인한 처리 단계예요. 추론 완료율이나 착장 품질 합격을 뜻하지 않아요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.busy && state.phase == "preparing") {
                TextButton(onClick = model::cancel, modifier = Modifier.testTag("serverOutfitCancelPreparation")) {
                    Text("사진 준비 취소")
                }
            }
            if (state.ticket != null) {
                TextButton(onClick = model::resume, enabled = !state.busy,
                    modifier = Modifier.testTag("serverOutfitResume")) { Text("기존 작업 상태 조회") }
                TextButton(onClick = model::cancel,
                    enabled = state.status?.status !in ServerOutfitVtonRepository.terminal,
                    modifier = Modifier.testTag("serverOutfitCancel")) { Text(if (externalCancellation) "대기 종료·후속 생성 중단" else "서버 생성 취소 요청") }
                Text("앱을 종료해도 작업 번호는 남아요. 다시 조회하며 응답 유실 시 자동 재전송하지 않아요. 미저장 원본 비교 이미지는 앱 종료 후 복구되지 않아요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("serverOutfitError")) }
            if (visible != null) Text(if (state.previous) "이전 서버 작업 결과 · 새 생성 아님" else "현재 서버 요청 결과 · 품질 검수 필요",
                modifier = Modifier.testTag("serverOutfitResultLabel"))
            if (visible != null) state.status?.source?.let {
                val modeLabel = when (it.mode) {
                    "clothing-pair-then-shoes" -> "상의·하의 함께 → 신발, 2회 생성"
                    "sequential-3" -> "3회 순차 생성"
                    else -> "생성 방식 미확인"
                }
                Text("출처: Genlook Try-On API · $modeLabel · 내부 모델 버전 미공개. 파일 검증과 착장 품질 합격은 달라요.",
                    modifier = Modifier.testTag("serverOutfitResultSource"))
                Text("이 작업 예약 상한 ${it.creditsReserved}크레딧 · 실제 청구액은 공급자 사용 내역으로 확인해주세요.")
            }
            TextButton(onClick = { comparison = true; showOriginal = false }, enabled = visible != null,
                modifier = Modifier.testTag("serverOutfitCompare")) { Text("원본·서버 결과 비교") }
            TextButton(onClick = {
                if (saving || pendingSave != null) return@TextButton
                try {
                    pendingSave = visible?.png ?: return@TextButton
                    save.launch("server-outfit-${System.currentTimeMillis()}.png")
                } catch (_: OutOfMemoryError) { pendingSave = null; message = "저장할 메모리가 부족해요." }
                catch (_: Exception) { pendingSave = null; message = "저장 선택기를 열지 못했어요." }
            }, enabled = visible != null && !saving && pendingSave == null,
                modifier = Modifier.testTag("serverOutfitSave")) { Text(if (saving) "저장 중" else "결과 PNG 저장") }
            TextButton(onClick = {
                picking = true
                try { open.launch(arrayOf("image/png", "image/jpeg")) }
                catch (_: Exception) { picking = false; message = "저장 파일 선택기를 열지 못했어요." }
            }, enabled = !picking && !reading, modifier = Modifier.testTag("serverOutfitOpen")) { Text("저장 이미지 열기") }
            message?.let { Text(it, modifier = Modifier.testTag("serverOutfitMessage")) }
        }
    }
    if (comparison && visible != null) AlertDialog(onDismissRequest = { comparison = false },
        title = { Text(if (state.previous) "이전 서버 작업 결과" else "현재 서버 요청 결과") },
        text = { Column {
            if (compareBytes?.first == null) Text("이 세션에 원본 비교 이미지가 없어요. 결과 수신을 새 생성이나 원본 보존 검증으로 표시하지 않아요.")
            else TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.testTag("serverOutfitToggle")) {
                Text(if (showOriginal) "서버 결과 보기" else "요청 당시 원본 보기")
            }
            compareBytes?.let { AsyncImage(if (showOriginal && it.first != null) it.first else it.second,
                "서버 결과와 요청 원본", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) }
            Text("파일 무결성 검사는 착장 품질·얼굴·상품 세부 보존을 보장하지 않아요.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { comparison = false }) { Text("비교 닫기") } })
    reopened?.let { image -> AlertDialog(onDismissRequest = { reopened = null },
        title = { Text("저장 이미지 · 새 생성 아님") },
        text = { Column {
            Text("직접 선택한 저장 파일이에요. 재열기만으로 AI 생성 출처를 확인하지 않아요.")
            AsyncImage(image, "저장 이미지", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp))
        } }, confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}
