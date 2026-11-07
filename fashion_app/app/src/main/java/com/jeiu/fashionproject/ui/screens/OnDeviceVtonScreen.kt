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
import com.jeiu.fashionproject.data.DeviceEngineAvailability
import com.jeiu.fashionproject.data.DeviceGarmentRole
import com.jeiu.fashionproject.data.DeviceVtonSelection
import com.jeiu.fashionproject.data.LocalVtonRepository
import com.jeiu.fashionproject.data.OnDeviceVtonImages
import com.jeiu.fashionproject.ui.viewmodel.OnDeviceVtonViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The document callback must retain this exact result, even if the wardrobe changes meanwhile. */
private class DeviceSaveTicket(val jobId: String, val inputDigest: String, png: ByteArray) {
    private val frozenPng = png.copyOf()
    val png: ByteArray get() = frozenPng.copyOf()
}

/** An independent local engine screen. No server credentials, network fallback, or manual export. */
@Composable
internal fun OnDeviceVtonScreen(
    selection: DeviceVtonSelection,
    onBack: () -> Unit,
    model: OnDeviceVtonViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = model.state
    val visible = state.result?.takeIf { model.resultMatches(selection) }
    val issue = model.issueFor(selection)
    var approved by remember(selection) { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<DeviceSaveTicket?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectingSaved by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var comparison by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    // Copy only while comparing; an allocation failure must not crash or show an empty success.
    val comparisonImages = remember(comparison, visible?.jobId) {
        if (!comparison) null else try { visible?.let { it.original to it.png } }
        catch (_: OutOfMemoryError) { null }
    }

    // Reentry observes the same controller job; it never submits an inference request.
    LaunchedEffect(selection) {
        model.selectionChanged(selection)
        comparison = false
        showOriginal = false
        message = null
        // A SAF picker already open still owns its original immutable ticket.
    }
    BackHandler(onBack = onBack)

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { target ->
        val ticket = pendingSave
        pendingSave = null
        if (target != null && ticket != null && !saving) {
            saving = true
            scope.launch {
                try {
                    OnDeviceVtonImages.saveExact(context, ticket.png, target)
                    message = "선택한 결과 PNG를 저장했어요. 저장 이미지 열기로 다시 확인해주세요."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: OutOfMemoryError) {
                    message = "메모리가 부족해 저장하지 못했어요. 선택한 문서가 완성되지 않았을 수 있어요."
                } catch (_: Exception) {
                    message = "저장하지 못했어요. 선택한 문서에 불완전한 파일이 남을 수 있어요. 저장 위치를 확인해주세요."
                } finally { saving = false }
            }
        } else if (target != null) {
            message = "저장할 결과 연결이 사라졌어요. 결과를 확인하고 다시 저장해주세요."
        }
    }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        selectingSaved = false
        if (source != null && !reading) {
            reading = true
            reopened = null
            scope.launch {
                try { reopened = LocalVtonRepository.openSavedImage(context, source) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: OutOfMemoryError) { message = "저장 이미지를 읽을 메모리가 부족해요. 잠시 후 다시 열어주세요." }
                catch (_: Exception) {
                    message = "이미지를 읽지 못했어요. PNG/JPG 형식·크기·접근 권한을 확인해주세요."
                } finally { reading = false }
            }
        }
    }

    Surface(Modifier.fillMaxSize().testTag("onDeviceVtonScreen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("deviceVtonBack")) { Text("피팅으로 돌아가기") }
            Text("기기 내 AI 가상피팅", style = MaterialTheme.typography.headlineSmall)
            Text(if (model.availability is DeviceEngineAvailability.Ready) "온디바이스 AI · 모델 연결됨" else "온디바이스 AI · 모델 미준비",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("deviceVtonReadiness"))
            Text("선택한 모델 사진과 상의·하의·신발을 한 요청으로 고정해 이 기기에서 처리해요. 서버 주소나 API 키를 입력하지 않아요.",
                style = MaterialTheme.typography.bodyMedium)
            Text("2D 수동 합성·외부 유료 AI·자체 GPU 서버와 별도 기능이에요. 생성형 모델이 없으면 기존 합성 사진으로 대체하지 않아요.",
                style = MaterialTheme.typography.bodySmall)
            Text("상의·하의·신발 3종이 모두 필요해요.", modifier = Modifier.testTag("deviceVtonAllRoles"))
            for (role in DeviceGarmentRole.entries) {
                val count = selection.garments.count { it.role == role }
                val label = when (role) { DeviceGarmentRole.TOP -> "상의"; DeviceGarmentRole.BOTTOM -> "하의"; DeviceGarmentRole.SHOES -> "신발" }
                Text("$label · ${when (count) { 0 -> "미선택"; 1 -> "선택됨"; else -> "중복 선택을 확인해주세요" }}",
                    modifier = Modifier.testTag("deviceVtonRole${role.name}"))
            }
            issue?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("deviceVtonBlocked")) }
            Row {
                Checkbox(checked = approved, onCheckedChange = { approved = it }, enabled = !state.pending,
                    modifier = Modifier.testTag("deviceVtonConsent"))
                Text("선택한 사진과 의류 3종의 기기 내 처리에 동의해요. 외부로 전송하지 않아요.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 10.dp))
            }
            Button(onClick = {
                message = null; comparison = false; reopened = null
                model.start(context, selection, approved)
            }, enabled = approved && issue == null && !state.pending && !saving && pendingSave == null,
                modifier = Modifier.fillMaxWidth().testTag("deviceVtonGenerate")) {
                Text(if (state.pending) "기기 내 생성 진행 중" else "선택한 3종으로 AI 생성")
            }
            if (state.pending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("화면 재진입만으로 새 요청을 만들지 않아요. 앱을 종료하면 진행 중 작업은 복구되지 않을 수 있어요.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.cancel() }, modifier = Modifier.testTag("deviceVtonCancel")) { Text("생성 취소") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("deviceVtonError")) }
            if (visible != null) {
                Text(if (state.resultFromPreviousAttempt) "이전에 생성한 결과 · 이번 시도 결과 아님" else "현재 요청의 결과 · 품질을 직접 확인해주세요",
                    modifier = Modifier.testTag("deviceVtonResultLabel"))
                Text("파일 검증만으로 자연스러운 착장 품질을 보장하지 않아요.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { comparison = true; showOriginal = false }, enabled = visible != null,
                modifier = Modifier.testTag("deviceVtonCompare")) { Text("원본·결과 비교") }
            TextButton(onClick = {
                val result = visible ?: return@TextButton
                if (saving || pendingSave != null) return@TextButton
                try {
                    pendingSave = DeviceSaveTicket(result.jobId, result.inputDigest, result.png)
                    savePicker.launch("on-device-fitting-${System.currentTimeMillis()}.png")
                }
                catch (_: OutOfMemoryError) { pendingSave = null; message = "저장할 결과를 준비할 메모리가 부족해요. 잠시 후 다시 시도해주세요." }
                catch (_: Exception) { pendingSave = null; message = "저장 화면을 열지 못했어요." }
            }, enabled = visible != null && !saving && pendingSave == null,
                modifier = Modifier.testTag("deviceVtonSave")) { Text(if (saving) "저장 중" else "결과 PNG 저장") }
            TextButton(onClick = {
                selectingSaved = true
                try { openPicker.launch(arrayOf("image/png", "image/jpeg")) }
                catch (_: Exception) { selectingSaved = false; message = "파일 선택 화면을 열지 못했어요." }
            }, enabled = !reading && !selectingSaved, modifier = Modifier.testTag("deviceVtonOpen")) { Text("저장 이미지 열기") }
            message?.let { Text(it, modifier = Modifier.testTag("deviceVtonMessage")) }
        }
    }
    if (comparison && visible != null) AlertDialog(onDismissRequest = { comparison = false },
        title = { Text("원본·기기 내 결과 비교") },
        text = { Column {
            Text(if (showOriginal) "원본 · 요청 당시 사진" else if (state.resultFromPreviousAttempt) "이전 요청 결과 · 새 생성 아님" else "현재 요청 결과 · 품질 검수 필요")
            TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.testTag("deviceVtonToggle")) {
                Text(if (showOriginal) "결과 보기" else "원본 보기")
            }
            if (comparisonImages == null) Text("비교 이미지를 준비할 메모리가 부족해요. 창을 닫고 잠시 후 다시 시도해주세요.")
            else AsyncImage(if (showOriginal) comparisonImages.first else comparisonImages.second, "요청 원본과 생성 결과 비교", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(280.dp))
        } }, confirmButton = { TextButton(onClick = { comparison = false }) { Text("비교 닫기") } })
    reopened?.let { bytes -> AlertDialog(onDismissRequest = { reopened = null },
        title = { Text("저장 이미지 · 새 생성 아님") },
        text = { Column {
            Text("직접 선택한 저장 파일이에요. 파일을 연 것만으로 AI 생성 여부를 확인할 수 없어요.", style = MaterialTheme.typography.bodySmall)
            AsyncImage(bytes, "선택한 저장 이미지", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp))
        } }, confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}
