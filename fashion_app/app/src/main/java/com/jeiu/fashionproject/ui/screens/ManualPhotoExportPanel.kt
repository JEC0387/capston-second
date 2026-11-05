package com.jeiu.fashionproject.ui.screens

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
import com.jeiu.fashionproject.data.LocalVtonRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Local PNG utilities are shared with VTON; no VTON request or token is used here. */
@Composable
internal fun ManualPhotoExportPanel(input: ManualPhotoSnapshot?,
    create: suspend (ManualPhotoSnapshot) -> ManualPhotoResult = { error("renderer_required") }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var automatic by remember { mutableStateOf(false) }
    val effectiveInput = input?.copy(automatic = automatic)
    val current by rememberUpdatedState(effectiveInput)
    var result by remember { mutableStateOf<ManualPhotoResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var pendingAutomatic by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var comparison by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var reading by remember { mutableStateOf(false) }
    var selectingSaved by remember { mutableStateOf(false) }
    val visible = result?.takeIf { it.input == effectiveInput }
    LaunchedEffect(effectiveInput) {
        if (result?.input != effectiveInput) { comparison = false; original = false }
        // Existing export bytes stay frozen while the document picker is open.
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { target ->
        val bytes = pendingSave; pendingSave = null
        val savedAutomatic = pendingAutomatic
        if (target != null && bytes != null && !saving) {
            saving = true
            scope.launch {
                try { LocalVtonRepository.save(context, bytes, target, true); message =
                    if (savedAutomatic) "기기 내 자동 착장 합성 PNG를 저장했어요. 실제 착용이나 AI 생성을 의미하지 않아요."
                    else "2D 수동 합성 PNG를 저장했어요. 저장 이미지 열기로 다시 확인할 수 있어요." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "저장하지 못했어요. 선택한 문서에 불완전한 파일이 남을 수 있어요. 저장 위치를 확인해주세요." }
                finally { saving = false }
            }
        } else if (target != null) message = "저장 연결이 사라졌어요. 결과를 확인하고 다시 저장해주세요."
    }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        selectingSaved = false
        if (source != null && !reading) {
            reading = true; reopened = null
            scope.launch {
                try { reopened = LocalVtonRepository.openSavedImage(context, source) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "이미지를 읽지 못했어요. PNG/JPG 형식·크기·접근 권한을 확인해주세요." }
                finally { reading = false }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("manualPhotoExport")) {
        Text(if (automatic) "기기 내 자동 착장 합성 · 생성형 AI 아님" else "기기 내 2D 합성 · 생성형 AI 아님", style = MaterialTheme.typography.labelMedium)
        Row {
            Checkbox(checked = automatic, onCheckedChange = { automatic = it; message = null },
                enabled = !busy && !saving && pendingSave == null && (input?.body != null || automatic),
                modifier = Modifier.testTag("localDrapeMode"))
            Text("관절·의류 윤곽 기반 변형 (시험)", style = MaterialTheme.typography.bodySmall)
        }
        Text(if (automatic) "안전하게 확인된 윤곽과 관절만 적용해요. 기존 옷 제거·머리카락 가림·새 주름 생성은 지원하지 않아요. 결과를 직접 확인해주세요."
            else "현재 옷 위치·크기·회전·투명도를 PNG로 만들어요. 팔 가림·주름·조명은 새로 생성하지 않아요.", style = MaterialTheme.typography.bodySmall)
        if (input != null && input.body == null) Text("신체 위치를 찾지 못해 자동 변형은 사용할 수 없어요. 수동 배치와 PNG 저장은 가능해요.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(enabled = effectiveInput != null && (!automatic || effectiveInput.body != null) && !busy && !saving && pendingSave == null,
                modifier = Modifier.testTag("manualPngCreate"), onClick = {
                    val snapshot = effectiveInput ?: return@TextButton
                    if (busy) return@TextButton
                    busy = true; result = null; message = null; reopened = null
                    job = scope.launch {
                        try {
                            val rendered = create(snapshot)
                            if (current == snapshot) { result = rendered; comparison = true; original = false }
                            else message = "합성 중 사진이나 배치가 바뀌었어요. 현재 배치로 다시 만들어주세요."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (blocked: LocalDrapeFailure) { message = blocked.message }
                        catch (_: Exception) { message = "합성하지 못했어요. 기기에서 열 수 있는 사진과 의류를 선택해주세요. 외부 이미지는 가져오지 않아요." }
                        finally { busy = false; job = null }
                    }
                }) { Text(if (busy) "합성 중" else if (automatic) "자동 착장 PNG 만들기" else "현재 배치 PNG 만들기") }
            if (busy) TextButton(onClick = { job?.cancel(); message = "합성을 취소했어요." }, modifier = Modifier.testTag("manualPngCancel")) { Text("취소") }
        }
        Row {
            TextButton(enabled = visible != null, onClick = { comparison = true; original = false }, modifier = Modifier.testTag("manualPngCompare")) { Text("원본·합성 비교") }
            TextButton(enabled = visible != null && !saving && pendingSave == null, modifier = Modifier.testTag("manualPngSave"), onClick = {
                val bytes = visible?.png ?: return@TextButton
                if (saving || pendingSave != null) return@TextButton
                pendingSave = bytes.copyOf()
                pendingAutomatic = visible.input.automatic
                try { savePicker.launch("${if (pendingAutomatic) "local-drape" else "manual-2d-fitting"}-${System.currentTimeMillis()}.png") }
                catch (_: Exception) { pendingSave = null; message = "저장 화면을 열지 못했어요." }
            }) { Text(if (saving) "저장 중" else "PNG 저장") }
        }
        TextButton(enabled = !reading && !selectingSaved, modifier = Modifier.testTag("manualPngOpen"), onClick = {
            selectingSaved = true
            try { openPicker.launch(arrayOf("image/png", "image/jpeg")) }
            catch (_: Exception) { selectingSaved = false; message = "파일 선택 화면을 열지 못했어요." }
        }) { Text("저장 이미지 열기") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("manualPngMessage")) }
    }
    if (comparison && visible != null) AlertDialog(onDismissRequest = { comparison = false },
        title = { Text(if (visible.input.automatic) "자동 착장 합성 검수" else "2D 수동 합성 비교") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text(if (original) "원본 · 같은 캔버스 비율" else if (visible.input.automatic) "기기 내 합성 PNG · 실제 착용/AI 생성 아님" else "수동 합성 PNG · AI 생성 아님")
            visible.reports.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { original = !original }, modifier = Modifier.testTag("manualPngToggle")) { Text(if (original) "합성 보기" else "원본 보기") }
            AsyncImage(if (original) visible.original else visible.png, "원본과 기기 내 합성 비교", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(280.dp))
        } }, confirmButton = { TextButton(onClick = { comparison = false }) { Text("비교 닫기") } })
    reopened?.let { bytes -> AlertDialog(onDismissRequest = { reopened = null }, title = { Text("선택한 저장 이미지 · 새 생성 아님") },
        text = { AsyncImage(bytes, "저장 이미지", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) },
        confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}
