package com.jeiu.fashionproject.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.jeiu.fashionproject.ui.viewmodel.ServerOutfitUiState
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** User-facing AI try-on flow. Approval, authentication and explicit consent remain mandatory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerOutfitVtonScreen(selection: DeviceVtonSelection?, onBack: () -> Unit,
    model: ServerOutfitVtonViewModel,
    wardrobe: List<WardrobeItem> = emptyList(),
    onGarmentSelected: ((WardrobeItem) -> Unit)? = null,
    onPhotoSelected: (() -> Unit)? = null,
    selectionUpdating: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by DataAuth.state.collectAsState()
    val testAccountState by InstallTestAccount.state.collectAsState()
    val accountReady = if (BuildConfig.INSTALL_TEST_BUILD && InstallTestAccount.hasRecord)
        testAccountState == "ready" && InstallTestAccount.sessionOrNull() != null else DataAuth.canSync
    val state = model.state
    val inputsReady = model.inputReady(selection)
    val thirdParty = state.config?.providerInfo
    var consentDialog by remember(selection, state.config, state.configBinding, accountState, testAccountState,
        ServerOutfitQaConnection.revision) { mutableStateOf(false) }
    var consentGiven by remember(selection, state.config, state.configBinding, accountState, testAccountState,
        ServerOutfitQaConnection.revision) { mutableStateOf(false) }
    var policyDetails by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var linkingAccount by remember { mutableStateOf(false) }
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var savedUri by rememberSaveable(state.ticket?.id) { mutableStateOf<Uri?>(null) }
    var message by rememberSaveable(state.ticket?.id) { mutableStateOf<String?>(null) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var reviewPrevious by remember { mutableStateOf(false) }
    var reviewedPrevious by remember { mutableStateOf(false) }
    val visible = state.result?.takeIf { model.canDisplay() }
    val original = remember(visible) { visible?.original }
    val resultPng = remember(visible) { visible?.png }
    var showOriginal by rememberSaveable(state.ticket?.id) { mutableStateOf(false) }
    var zoomPhoto by remember(visible) { mutableStateOf(false) }
    var imageFailed by remember(visible, showOriginal) { mutableStateOf(false) }
    var imageAttempt by remember(visible, showOriginal) { mutableIntStateOf(0) }
    var imageDisplayed by remember(visible) { mutableStateOf(false) }
    var garmentPicker by remember { mutableStateOf<DeviceGarmentRole?>(null) }

    LaunchedEffect(model, accountReady) {
        model.initialize(context)
        if (accountReady) model.onAuthorizedScreenEntry()
    }
    LaunchedEffect(selection) { model.selectionChanged(selection) }
    BackHandler(onBack = onBack)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val request = pendingSave
        val bytes = model.state.result?.takeIf { request != null && model.state.ticket?.id == request &&
            model.canDisplay() }?.png
        pendingSave = null
        if (uri != null && bytes != null && !saving) {
            saving = true
            scope.launch {
                try {
                    OnDeviceVtonImages.saveExact(context, bytes, uri)
                    savedUri = uri
                    message = "사진을 저장했어요."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "메모리가 부족해 저장하지 못했어요." }
                catch (_: Exception) { message = "사진을 저장하지 못했어요. 다시 저장해 주세요." }
                finally { saving = false }
            }
        } else if (uri != null) message = "저장할 결과를 찾지 못했어요."
    }
    fun openSavedPhoto() {
        val uri = savedUri ?: return
        if (reading) return
        reading = true
        scope.launch {
            try { reopened = LocalVtonRepository.openSavedImage(context, uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: OutOfMemoryError) { message = "메모리가 부족해 사진을 열지 못했어요." }
            catch (_: Exception) { message = "저장한 사진을 열지 못했어요." }
            finally { reading = false }
        }
    }

    val userError = userFacingTryOnError(state.failureCode ?: state.status?.error,
        previousRequest = state.ticket != null && state.status == null && state.previous)
    // A ready, account-bound config can only be obtained through gateway.session(); start()
    // revalidates the current account again before it opens or transmits any selected image.
    val canStart = !selectionUpdating && selection?.hasCompleteOutfit() == true && inputsReady &&
        state.config?.ready == true && !state.busy && !saving && !linkingAccount && pendingSave == null &&
        model.startAllowedByPreviousJob()

    val awaitingJob = state.ticket != null && !model.startAllowedByPreviousJob()
    val hasResult = visible != null && resultPng != null && !(state.busy && state.phase == "preparing")
    val completedWithoutImage = state.status?.status == "completed" && state.status?.resultAvailable == true
    val generating = state.busy && (state.phase == "preparing" ||
        state.ticket != null && (awaitingJob || completedWithoutImage)) && !hasResult
    val recovering = awaitingJob || completedWithoutImage && !hasResult && state.failureStage in
        setOf("result_download", "result_png", "job_status")
    val accepted = state.ticket != null && state.status?.status in setOf("queued", "running", "completed", "cancelling")
    val cancellable = state.ticket?.let { !ServerOutfitVtonRepository.isGenlook(it) } == true &&
        thirdParty?.remoteCancellationSupported != false && state.status?.status in setOf("queued", "running")
    val title = when {
        hasResult -> "피팅 결과"
        generating || recovering -> "AI 가상피팅"
        else -> "AI로 입어보기"
    }

    FashionFeatureTheme {
    Surface(Modifier.fillMaxSize().testTag("serverOutfitVtonScreen"), color = Cream) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val largeText = LocalDensity.current.fontScale >= 1.4f
            val scrollActions = largeText || maxHeight < 560.dp
            val imageHeight = (maxHeight * if (hasResult) .58f else .37f).coerceIn(160.dp, 640.dp)
            val displayingOriginal = showOriginal && original != null
            val displayedPhoto = if (displayingOriginal) original else resultPng
            val actions: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        hasResult -> {
                            Button(onClick = {
                                if (saving || pendingSave != null) return@Button
                                pendingSave = state.ticket?.id ?: return@Button
                                try { save.launch("fashionfit-tryon-${System.currentTimeMillis()}.png") }
                                catch (_: Exception) { pendingSave = null; message = "저장 화면을 열지 못했어요." }
                            }, enabled = !saving && pendingSave == null,
                                shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .testTag("serverOutfitSave")) { Text(if (saving) "저장 중" else "사진 저장하기") }
                            OutlinedButton(onClick = onBack, enabled = !saving && pendingSave == null,
                                shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("serverOutfitChangeClothes")) { Text("다른 옷 입어보기") }
                        }
                        generating || recovering -> {
                            if (!state.busy && recovering) Button(onClick = model::resume,
                                shape = FashionActionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .testTag("serverOutfitResume")) { Text("이전 피팅 결과 확인") }
                            if (state.phase == "preparing" && generating) OutlinedButton(onClick = model::cancel,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("serverOutfitCancelPreparation")) { Text("준비 취소") }
                            if (state.ticket != null && state.phase != "preparing") {
                                val browseModifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("serverOutfitBrowse")
                                if (state.busy) Button(onClick = onBack, modifier = browseModifier, shape = FashionActionShape) {
                                    Text("다른 화면 둘러보기")
                                } else OutlinedButton(onClick = onBack, modifier = browseModifier, shape = FashionActionShape) {
                                    Text("다른 화면 둘러보기")
                                }
                                if (cancellable) TextButton(onClick = { confirmCancel = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("serverOutfitCancel")) {
                                    Text("생성 취소")
                                }
                            }
                        }
                        else -> {
                            Button(onClick = { consentDialog = true; consentGiven = false; policyDetails = false },
                                enabled = canStart, shape = FashionActionShape,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("serverOutfitGenerate")) {
                                Text("AI로 입어보기")
                            }
                            if (selection == null && onPhotoSelected == null)
                                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("serverOutfitChangeSelection")) { Text("사진과 옷 선택하기") }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("serverOutfitBack")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                key(hasResult, generating || recovering) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            hasResult -> {
                                if (imageDisplayed) OutfitCompletedHeader(state.previous)
                                else if (!imageFailed) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (original != null) Row(Modifier.fillMaxWidth().testTag("serverOutfitToggle"),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(false to "✨ AI 결과", true to "원본 사진").forEach { (isOriginal, label) ->
                                        val selected = displayingOriginal == isOriginal
                                        Surface(shape = FashionActionShape, color = if (selected) CamelLight else Cream,
                                            border = BorderStroke(if (selected) 2.dp else 1.dp, FashionBorder),
                                            modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                                                onClick = { showOriginal = isOriginal })
                                                .heightIn(min = 48.dp).testTag(if (isOriginal) "serverOutfitShowOriginal" else "serverOutfitShowResult")) {
                                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically) {
                                                if (selected) Icon(Icons.Filled.Check, null, Modifier.padding(end = 4.dp).size(18.dp))
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                                if (imageFailed) Card(
                                    modifier = Modifier.fillMaxWidth(), shape = FashionCardShape,
                                    colors = CardDefaults.cardColors(containerColor = CreamDark)
                                ) {
                                    Column(Modifier.padding(20.dp)) {
                                        Text("사진을 화면에 불러오지 못했어요.")
                                        TextButton(onClick = { imageAttempt++; imageFailed = false },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("serverOutfitReloadImage")) {
                                            Text("사진 다시 보기")
                                        }
                                    }
                                } else Surface(onClick = { zoomPhoto = true }, shape = FashionCardShape, color = CreamDark,
                                    modifier = Modifier.fillMaxWidth().height(imageHeight).testTag("serverOutfitPhotoZoom")) {
                                    key(imageAttempt) {
                                        AsyncImage(displayedPhoto, if (displayingOriginal) "원본 사진, 눌러서 확대" else "AI 착장 사진, 눌러서 확대",
                                            onSuccess = { imageDisplayed = true },
                                            onError = { imageFailed = true; imageDisplayed = false }, contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                                .testTag(if (displayingOriginal) "serverOutfitOriginalImage" else "serverOutfitResultImage"))
                                    }
                                }
                            }
                            generating || recovering -> {
                                OutfitProgressCard(state, accepted)
                                val garments = model.requestedGarmentsForCurrentAccount()
                                if (garments.isNotEmpty()) OutfitRequestGarments(garments)
                            }
                            else -> {
                                Surface(shape = FashionCardShape, color = CreamDark,
                                    modifier = Modifier.fillMaxWidth().height(imageHeight)) {
                                    if (selection == null) Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("전신 사진을 선택해주세요.", color = FashionMuted,
                                            modifier = Modifier.testTag("serverOutfitMissingSelection"))
                                    } else AsyncImage(selection.person, "선택한 전신 사진", contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().testTag("serverOutfitPersonPreview"))
                                }
                                if (onPhotoSelected != null) OutlinedButton(onClick = onPhotoSelected,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(if (selection == null) "전신 사진 선택" else "사진 바꾸기")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DeviceGarmentRole.entries.forEach { role ->
                                        val garment = selection?.garments?.singleOrNull { it.role == role }
                                        val label = role.userLabel()
                                        Card(onClick = { garmentPicker = role }, enabled = onGarmentSelected != null,
                                            modifier = Modifier.weight(1f).testTag("serverOutfitCard_${role.name}"),
                                            shape = FashionActionShape, border = BorderStroke(if (garment == null) 1.dp else 2.dp, FashionBorder),
                                            colors = CardDefaults.cardColors(containerColor = CreamDark, disabledContainerColor = CreamDark,
                                                disabledContentColor = CharcoalBlack)) {
                                            Column(Modifier.fillMaxWidth().padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (garment != null) AsyncImage(garment.source, "$label 미리보기",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxWidth().height(64.dp).testTag("serverOutfitGarment_${role.name}"))
                                                Text(label, style = MaterialTheme.typography.labelLarge)
                                                Text(if (garment == null) "선택" else "선택됨", color = FashionMuted,
                                                    style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                                val readiness = when {
                                    selectionUpdating -> "선택한 옷을 불러오고 있어요."
                                    selection?.hasCompleteOutfit() != true || !inputsReady -> "전신 사진과 상의·하의·신발을 모두 선택해주세요."
                                    !accountReady && state.config?.ready != true -> "계정 연결을 확인해주세요."
                                    state.busy -> "AI 피팅을 준비하고 있어요."
                                    state.config?.ready != true -> "AI 피팅을 아직 사용할 수 없어요."
                                    else -> null
                                }
                                readiness?.let { Text(it, color = FashionMuted, modifier = Modifier.testTag("serverOutfitReady")) }
                            }
                        }
                        if (BuildConfig.DEBUG && !hasResult && !generating) {
                            if (ServerOutfitQaConnection.supported)
                                OutfitQaConnectionPanel(enabled = !state.busy && !saving, onChanged = model::invalidateConnection)
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
                            if (state.config?.ready != true && !state.busy)
                                TextButton(onClick = model::refreshConfig, modifier = Modifier.testTag("serverOutfitCheck")) { Text("다시 확인") }
                            if (model.canArchiveReviewedQaJob()) TextButton(onClick = {
                                reviewedPrevious = false; reviewPrevious = true
                            }, modifier = Modifier.testTag("serverOutfitReviewPrevious")) { Text("이전 요청 기록 보관") }
                        }
                        userError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("serverOutfitError")) }
                        message?.let {
                            Text(it, color = FashionMuted, modifier = Modifier.testTag("serverOutfitMessage"))
                        }
                        if (hasResult && savedUri != null) TextButton(onClick = { openSavedPhoto() },
                            enabled = !reading, modifier = Modifier.heightIn(min = 48.dp).testTag("serverOutfitOpen")) {
                            Text("저장한 사진 보기")
                        }
                        if (scrollActions) actions()
                    }
                }
                if (!scrollActions) Surface(color = Cream, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { actions() }
                }
            }
        }
    }

    garmentPicker?.let { role ->
        ModalBottomSheet(onDismissRequest = { garmentPicker = null }, containerColor = Cream) {
            Text("${role.userLabel()} 선택", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val candidates = wardrobe.filter { it.category == role.userLabel() }
            if (candidates.isEmpty()) Text("옷장에 등록된 옷이 없어요.", Modifier.padding(24.dp), color = FashionMuted)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.id }) { item ->
                    val selected = selection?.garments?.any { it.role == role && it.id == item.id } == true
                    Surface(onClick = {
                        if (!selected) onGarmentSelected?.invoke(item)
                        garmentPicker = null
                    }, shape = FashionActionShape, color = if (selected) CamelLight else CreamDark,
                        border = if (selected) BorderStroke(2.dp, FashionBorder) else null,
                        modifier = Modifier.fillMaxWidth().testTag("serverOutfitPick-${item.id}")) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(item.imageUri, null, Modifier.size(52.dp), contentScale = ContentScale.Fit)
                            Text(item.name, Modifier.weight(1f))
                            if (selected) Icon(Icons.Filled.Check, "선택됨")
                        }
                    }
                }
            }
        }
    }

    if (consentDialog) AlertDialog(onDismissRequest = { consentDialog = false },
        title = { Text("AI 피팅 동의") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            consentDialog = false; reopened = null; model.start(context, selection, true)
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

    if (confirmCancel && cancellable) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("생성 취소를 요청할까요?") },
        text = { Text("서버에서 취소를 확인하면 작업이 종료돼요.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; model.cancel() }) { Text("취소 요청") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("계속 기다리기") } }
    )

    if (zoomPhoto && resultPng != null) OutfitPhotoZoom(
        if (showOriginal) original ?: resultPng else resultPng, "피팅 사진 확대", onClose = { zoomPhoto = false })

    reopened?.let { image -> AlertDialog(onDismissRequest = { reopened = null },
        title = { Text("저장 이미지") },
        text = { AsyncImage(image, "저장 이미지", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(280.dp)) },
        confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}
}

@Composable
private fun OutfitProgressCard(state: ServerOutfitUiState, accepted: Boolean) {
    val status = state.status?.status
    val preparing = state.phase == "preparing"
    val recovering = !state.busy
    val message = when {
        recovering && state.failureCode in setOf("connection_failed", "status_request_failed", "job_status_unavailable",
            "result_download_failed", "network_or_transport", "network_unavailable") -> "연결이 잠시 끊겼어요. 기존 요청을 다시 확인해 주세요."
        recovering && state.phase == "waiting" -> "아직 결과를 확인하지 못했어요. 같은 요청을 다시 확인할 수 있어요."
        recovering -> "같은 요청의 상태와 결과를 다시 확인할 수 있어요."
        preparing -> "사진과 옷을 준비하고 있어요."
        status == null && state.phase == "submitting" -> "요청이 접수됐는지 확인하고 있어요."
        status == null -> "이전에 보낸 요청을 확인하고 있어요."
        status == "queued" -> "서버에서 생성할 차례를 기다리고 있어요."
        status == "completed" -> "완성된 사진을 불러오고 있어요."
        status == "cancelling" -> "서버에 보낸 취소 요청을 확인하고 있어요."
        state.status?.providerProgress?.phase == "uploading" -> "동의한 사진과 옷을 보내고 있어요."
        state.status?.providerProgress?.phase in setOf("validating_result", "finalizing") -> "완성된 착장 사진을 확인하고 있어요."
        else -> "AI 착장 이미지 생성 중"
    }
    val pulse = if (state.busy) {
        val shimmer = rememberInfiniteTransition(label = "fitting")
        shimmer.animateFloat(.65f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "sparkle").value
    } else 1f
    Card(Modifier.fillMaxWidth(), shape = FashionCardShape, colors = CardDefaults.cardColors(containerColor = CreamDark)) {
        Column(Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(28.dp).alpha(pulse), tint = BrownDark)
                Surface(shape = FashionActionShape, color = Cream) {
                    Text(when {
                        recovering -> "상태 확인"
                        preparing -> "준비 중"
                        status == "running" -> "생성 중"
                        status == "queued" -> "대기 중"
                        else -> "확인 중"
                    }, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge, color = BrownDark)
                }
            }
            Text(if (recovering) "이전 요청을 확인해 주세요" else "새로운 스타일을 만들고 있어요",
                style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, modifier = Modifier.testTag("serverOutfitProgress"))
            if (!recovering && (preparing || status in setOf("queued", "running")))
                Text("선택한 옷을 자연스럽게 입혀드릴게요.", color = FashionMuted)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (accepted && status in setOf("queued", "running")) Text("사진과 옷 준비 완료", color = BrownDark)
            Text(message, color = FashionMuted)
            if (accepted && !recovering && status in setOf("queued", "running")) {
                Text("다른 화면을 이용해도 진행 상태를 다시 확인할 수 있어요.", color = FashionMuted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OutfitRequestGarments(garments: List<DeviceGarmentInput>) {
    Text("선택한 옷", color = CharcoalBlack, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        garments.sortedBy { it.role.ordinal }.forEach { garment ->
            Card(Modifier.weight(1f), shape = FashionActionShape,
                colors = CardDefaults.cardColors(containerColor = CreamDark)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    var unavailable by remember(garment.source) { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
                        if (unavailable) Icon(Icons.Filled.Checkroom, null, tint = BrownDark)
                        else AsyncImage(garment.source, "${garment.role.userLabel()} 미리보기",
                            Modifier.fillMaxSize(), onError = { unavailable = true }, contentScale = ContentScale.Fit)
                    }
                    Text(garment.role.userLabel(), style = MaterialTheme.typography.labelLarge, color = CharcoalBlack,
                        modifier = Modifier.testTag("serverOutfitRequested_${garment.role.name}"))
                }
            }
        }
    }
}

@Composable
private fun OutfitCompletedHeader(previous: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = FashionCardShape, colors = CardDefaults.cardColors(containerColor = CreamDark)) {
        Row(Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Check, null, tint = BrownDark)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("새로운 착장이 완성됐어요!", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack,
                    modifier = Modifier.testTag("serverOutfitCompleted"))
                Text(if (previous) "이전에 요청한 착장이에요." else "AI가 만들어준 스타일을 확인해 보세요.",
                    style = MaterialTheme.typography.bodySmall, color = FashionMuted,
                    modifier = if (previous) Modifier.testTag("serverOutfitResultLabel") else Modifier)
            }
        }
    }
}

@Composable
private fun OutfitPhotoZoom(bytes: ByteArray, title: String, onClose: () -> Unit) {
    var scale by remember(bytes) { mutableFloatStateOf(1f) }
    var offset by remember(bytes) { mutableStateOf(Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("닫기") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(FashionCardShape).background(CreamDark).testTag("serverOutfitZoomImage")
                    .pointerInput(bytes) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            val limit = (scale - 1f) / 2f
                            val moved = offset + pan
                            offset = Offset(moved.x.coerceIn(-size.width * limit, size.width * limit),
                                moved.y.coerceIn(-size.height * limit, size.height * limit))
                        }
                    }) {
                    AsyncImage(bytes, title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    })
                }
            }
        }
    }
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
