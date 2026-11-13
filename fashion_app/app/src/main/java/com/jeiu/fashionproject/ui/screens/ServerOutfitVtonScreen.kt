package com.jeiu.fashionproject.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

/** User-facing AI try-on flow. Approval, authentication and explicit consent remain mandatory. */
@Composable
internal fun ServerOutfitVtonScreen(selection: DeviceVtonSelection?, onBack: () -> Unit,
    model: ServerOutfitVtonViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by DataAuth.state.collectAsState()
    val testAccountState by InstallTestAccount.state.collectAsState()
    val accountReady = if (BuildConfig.INSTALL_TEST_BUILD && InstallTestAccount.hasRecord)
        testAccountState == "ready" && InstallTestAccount.sessionOrNull() != null else DataAuth.canSync
    val state = model.state
    val inputsReady = model.inputReady(selection)
    val thirdParty = state.config?.providerInfo
    val externalCancellation = state.ticket?.let(ServerOutfitVtonRepository::isGenlook)
        ?: (thirdParty?.remoteCancellationSupported == false)
    var consentDialog by remember(selection, state.config, state.configBinding, accountState, testAccountState,
        ServerOutfitQaConnection.revision) { mutableStateOf(false) }
    var consentGiven by remember(selection, state.config, state.configBinding, accountState, testAccountState,
        ServerOutfitQaConnection.revision) { mutableStateOf(false) }
    var policyDetails by remember { mutableStateOf(false) }
    var linkingAccount by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var comparison by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var reviewPrevious by remember { mutableStateOf(false) }
    var reviewedPrevious by remember { mutableStateOf(false) }
    val visible = state.result?.takeIf { model.canDisplay() }
    val compareBytes = remember(comparison, visible) {
        if (!comparison) null else runCatching { visible?.let { it.original to it.png } }.getOrNull()
    }

    LaunchedEffect(model, accountReady) {
        model.initialize(context)
        if (accountReady) model.refreshConfig() // Read-only readiness request, never creates a paid job.
    }
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
                    message = "이미지를 저장했어요. 저장한 파일을 다시 열어 확인할 수 있어요."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "메모리가 부족해 저장하지 못했어요." }
                catch (_: Exception) { message = "저장하지 못했어요. 기존 파일은 변경하지 않았어요." }
                finally { saving = false }
            }
        } else if (uri != null) message = "저장할 결과를 찾지 못했어요."
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri != null && !reading) {
            reading = true; reopened = null
            scope.launch {
                try { reopened = LocalVtonRepository.openSavedImage(context, uri) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "메모리가 부족해 이미지를 열지 못했어요." }
                catch (_: Exception) { message = "선택한 이미지 파일을 열지 못했어요." }
                finally { reading = false }
            }
        }
    }

    val userError = userFacingTryOnError(state.failureCode ?: state.status?.error,
        previousRequest = state.ticket != null && state.status == null && state.previous)
    // A ready, account-bound config can only be obtained through gateway.session(); start()
    // revalidates the current account again before it opens or transmits any selected image.
    val canStart = selection?.hasCompleteOutfit() == true && inputsReady &&
        state.config?.ready == true && !state.busy && !saving && !linkingAccount && pendingSave == null &&
        model.startAllowedByPreviousJob()

    Surface(Modifier.fillMaxSize().testTag("serverOutfitVtonScreen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("serverOutfitBack")) { Text("뒤로") }
                Text("AI로 입어보기", style = MaterialTheme.typography.headlineSmall)
            }
            if (selection != null) {
                AsyncImage(selection.person, "선택한 전신 사진", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(300.dp).testTag("serverOutfitPersonPreview"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceGarmentRole.entries.forEach { role ->
                        val garment = selection.garments.singleOrNull { it.role == role }
                        val label = role.userLabel()
                        Card(Modifier.weight(1f).height(116.dp).testTag("serverOutfitCard_${role.name}")) {
                            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                if (garment != null) AsyncImage(garment.source, "$label 미리보기",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(72.dp).testTag("serverOutfitGarment_${role.name}"))
                                else Text("선택 필요", style = MaterialTheme.typography.labelSmall)
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                Text("피팅 화면에서 전신 사진과 상의·하의·신발을 선택해주세요.",
                    modifier = Modifier.testTag("serverOutfitMissingSelection"))
            }

            if (ServerOutfitQaConnection.supported && BuildConfig.DEBUG)
                OutfitQaConnectionPanel(enabled = !state.busy && !saving, onChanged = model::invalidateConnection)

            val readiness = when {
                selection?.hasCompleteOutfit() != true || !inputsReady -> "전신 사진과 상의·하의·신발을 모두 선택해주세요."
                !accountReady -> "계정 연결이 필요해요. 사진은 전송하지 않았어요."
                state.config?.ready == true -> "AI 피팅을 시작할 수 있어요."
                state.busy -> "AI 피팅을 준비하고 있어요."
                else -> "AI 피팅을 아직 사용할 수 없어요."
            }
            Text(readiness, modifier = Modifier.testTag("serverOutfitReady"))
            userError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("serverOutfitError")) }

            if (BuildConfig.DEBUG && BuildConfig.INSTALL_TEST_BUILD && !accountReady)
                Text("시험 계정 연결이 필요해요.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("installtestAccountHelp"))
            if (BuildConfig.DEBUG && BuildConfig.INSTALL_TEST_BUILD && (!DataAuth.canSync || InstallTestAccount.hasRecord))
                com.jeiu.fashionproject.ui.components.InstallTestAccountPanel(testAccountState, InstallTestAccount.hasRecord,
                    linkingAccount || state.busy || saving,
                    onEnroll = { consent, reference, server ->
                        if (!linkingAccount) {
                            linkingAccount = true
                            scope.launch {
                                try { InstallTestAccount.enroll(consent, reference, server); model.invalidateConnection() }
                                finally { linkingAccount = false }
                            }
                        }
                    }, onVerify = {
                        if (!linkingAccount) {
                            linkingAccount = true
                            scope.launch {
                                try { InstallTestAccount.verify(); model.invalidateConnection() }
                                finally { linkingAccount = false }
                            }
                        }
                    })

            if (selection?.hasCompleteOutfit() != true) {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("serverOutfitChangeSelection")) {
                    Text("사진과 옷 선택하기")
                }
            }
            if (BuildConfig.DEBUG && state.config?.ready != true && !state.busy) {
                TextButton(onClick = model::refreshConfig, modifier = Modifier.testTag("serverOutfitCheck")) { Text("다시 확인") }
            }
            Button(onClick = { consentDialog = true; consentGiven = false; policyDetails = false }, enabled = canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("serverOutfitGenerate")) {
                Text("AI로 입어보기")
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("착장 결과를 만들고 있어요.")
                if (state.phase == "preparing") TextButton(onClick = model::cancel,
                    modifier = Modifier.testTag("serverOutfitCancelPreparation")) { Text("취소") }
            }
            if (state.ticket != null) {
                TextButton(onClick = model::resume, enabled = !state.busy,
                    modifier = Modifier.testTag("serverOutfitResume")) { Text("기존 요청 확인") }
                TextButton(onClick = model::cancel, enabled = state.status?.status !in ServerOutfitVtonRepository.terminal,
                    modifier = Modifier.testTag("serverOutfitCancel")) { Text(if (externalCancellation) "대기 종료" else "요청 취소") }
            }
            if (model.canArchiveReviewedQaJob() && BuildConfig.DEBUG) {
                TextButton(onClick = { reviewedPrevious = false; reviewPrevious = true },
                    enabled = !saving && pendingSave == null, modifier = Modifier.testTag("serverOutfitReviewPrevious")) {
                    Text("이전 요청 기록 보관")
                }
            }
            if (visible != null) {
                Text(if (state.previous) "이전 요청 결과" else "생성 결과", modifier = Modifier.testTag("serverOutfitResultLabel"))
                AsyncImage(visible.png, "AI 가상피팅 결과 이미지", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(360.dp).testTag("serverOutfitResultImage"))
            }
            TextButton(onClick = { comparison = true; showOriginal = false }, enabled = visible != null,
                modifier = Modifier.testTag("serverOutfitCompare")) { Text("원본과 비교") }
            TextButton(onClick = {
                if (saving || pendingSave != null) return@TextButton
                try { pendingSave = visible?.png ?: return@TextButton; save.launch("fashionfit-tryon-${System.currentTimeMillis()}.png") }
                catch (_: OutOfMemoryError) { pendingSave = null; message = "메모리가 부족해 저장하지 못했어요." }
                catch (_: Exception) { pendingSave = null; message = "저장 화면을 열지 못했어요." }
            }, enabled = visible != null && !saving && pendingSave == null,
                modifier = Modifier.testTag("serverOutfitSave")) { Text(if (saving) "저장 중" else "저장하기") }
            if (visible != null) TextButton(onClick = {
                picking = true
                try { open.launch(arrayOf("image/png", "image/jpeg")) }
                catch (_: Exception) { picking = false; message = "저장된 이미지를 열지 못했어요." }
            }, enabled = !picking && !reading, modifier = Modifier.testTag("serverOutfitOpen")) { Text("저장 이미지 열기") }
            if (visible != null) TextButton(onClick = onBack, enabled = !saving && pendingSave == null,
                modifier = Modifier.testTag("serverOutfitChangeClothes")) { Text("다른 옷 입어보기") }
            message?.let { Text(it, modifier = Modifier.testTag("serverOutfitMessage")) }
        }
    }

    if (consentDialog) AlertDialog(onDismissRequest = { consentDialog = false },
        title = { Text("AI 피팅 동의") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("선택한 사진과 옷을 외부 AI 서비스로 보내 착장 이미지를 만들어요. 비용과 보관 정책이 적용됩니다.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consentGiven, { consentGiven = it }, modifier = Modifier.testTag("serverOutfitConsent"))
                Text("사진 전송과 처리 조건에 동의합니다.", modifier = Modifier.testTag("serverOutfitConsentText"))
            }
            TextButton(onClick = { policyDetails = !policyDetails }, modifier = Modifier.testTag("serverOutfitPolicyDetails")) {
                Text(if (policyDetails) "상세 정책 접기" else "상세 정책 보기")
            }
            if (policyDetails) {
                Text(buildString {
                    append("처리 제공자: ").append(thirdParty?.name ?: "승인된 외부 제공자").append('\n')
                    if (thirdParty != null) {
                        append("생성 방식: ").append(when (thirdParty.mode) {
                            "clothing-pair-then-shoes" -> "상의·하의 후 신발, 최대 2회"
                            "sequential-3" -> "상의·하의·신발 순차 처리, 최대 3회"
                            else -> "서버가 안내한 방식"
                        }).append('\n')
                        append("최대 사용량: ").append(thirdParty.maxCreditsPerJob).append(" 크레딧 · 자동 재요청 없음\n")
                        append("이미지 보관: 제공자 ").append(thirdParty.remoteRetentionDays).append("일, 앱 서버 ")
                            .append(state.config?.retentionSeconds ?: 0).append("초\n").append(thirdParty.notice)
                    } else append("서버가 안내하는 비용·보관 정책을 확인한 뒤 동의하세요.")
                    append("\n응답이 끊겨도 기존 요청만 조회하며 새 유료 요청을 자동 전송하지 않습니다.")
                }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("serverOutfitPolicyDetailsText"))
            }
        } },
        confirmButton = { TextButton(enabled = consentGiven && canStart, onClick = {
            consentDialog = false; comparison = false; reopened = null; model.start(context, selection, true)
        }, modifier = Modifier.testTag("serverOutfitConsentConfirm")) { Text("동의하고 생성") } },
        dismissButton = { TextButton(onClick = { consentDialog = false; consentGiven = false }) { Text("취소") } })

    if (reviewPrevious) AlertDialog(onDismissRequest = { reviewPrevious = false },
        title = { Text("이전 요청 기록 보관") },
        text = { Column {
            Text("서버에서 이전 요청이 끝났고 추가 과금이 없음을 별도로 확인한 경우에만 보관할 수 있어요. 확인하지 못했다면 취소해주세요.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reviewedPrevious, { reviewedPrevious = it }, modifier = Modifier.testTag("serverOutfitPreviousReviewed"))
                Text("종료와 결제 상태를 확인했어요.")
            }
        } },
        dismissButton = { TextButton(onClick = { reviewPrevious = false }) { Text("취소") } },
        confirmButton = { TextButton(enabled = reviewedPrevious && model.canArchiveReviewedQaJob(),
            onClick = { model.archiveReviewedQaJob(reviewedPrevious); consentGiven = false; reviewPrevious = false },
            modifier = Modifier.testTag("serverOutfitArchivePrevious")) { Text("보관") } })

    if (comparison && visible != null) AlertDialog(onDismissRequest = { comparison = false },
        title = { Text(if (state.previous) "이전 요청 결과" else "AI 피팅 결과") },
        text = { Column {
            if (compareBytes?.first == null) Text("요청 당시 원본이 없어 비교할 수 없어요.")
            else TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.testTag("serverOutfitToggle")) {
                Text(if (showOriginal) "결과 보기" else "원본 보기")
            }
            compareBytes?.let { AsyncImage(if (showOriginal && it.first != null) it.first else it.second,
                "원본과 AI 결과", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) }
        } }, confirmButton = { TextButton(onClick = { comparison = false }) { Text("닫기") } })

    reopened?.let { image -> AlertDialog(onDismissRequest = { reopened = null },
        title = { Text("저장 이미지") },
        text = { AsyncImage(image, "저장 이미지", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(280.dp)) },
        confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}

private fun DeviceGarmentRole.userLabel(): String = when (this) {
    DeviceGarmentRole.TOP -> "상의"
    DeviceGarmentRole.BOTTOM -> "하의"
    DeviceGarmentRole.SHOES -> "신발"
}

internal fun userFacingTryOnError(code: String?, previousRequest: Boolean): String? {
    if (previousRequest) return "이전 요청이 있어 결과 상태를 먼저 확인해주세요. 새 요청은 보내지 않았어요."
    if (code == null) return null
    return when (code) {
        "deployment_not_approved", "server_api_unavailable", "engine_unavailable", "genlook_disabled",
        "genlook_approval_required", "genlook_policy_unreviewed", "genlook_key_missing", "genlook_auth_failed" ->
            "AI 피팅을 아직 사용할 수 없어요."
        "authentication_required", "account_changed" -> "계정 연결을 확인해주세요."
        "https_required", "consent_or_transport_required" -> "보안 연결을 확인할 수 없어요."
        "image_read_failed" -> "선택한 이미지를 열 수 없어요. 파일이나 접근 권한을 확인해주세요."
        "image_processing_failed", "image_preparation_failed" -> "이미지를 변환할 수 없어요. 파일이 손상됐는지 확인해주세요."
        "image_size", "body_size" -> "이미지 크기가 지원 범위를 벗어났어요. 크기를 조정해 다시 선택해주세요."
        "image_unsupported" -> "정지 PNG, JPEG 또는 WebP 이미지를 선택해주세요."
        "image_memory" -> "이미지를 준비할 메모리가 부족해요. 더 작은 이미지를 선택해주세요."
        "genlook_budget_exhausted", "genlook_insufficient_credits", "usage_limit", "genlook_rate_limited" ->
            "AI 사용 한도에 도달했어요. 자동으로 다시 요청하지 않았어요."
        "submit_unconfirmed", "remote_execution_unconfirmed", "genlook_reconciliation_required" ->
            "요청 상태를 확인하지 못했어요. 기존 요청만 조회할 수 있어요."
        "result_download_failed", "result_png_invalid", "genlook_result_untrusted" ->
            "결과를 확인하지 못했어요. 기존 요청 상태를 확인해주세요."
        else -> "AI 피팅을 완료하지 못했어요. 선택한 사진과 옷은 유지돼요."
    }
}
