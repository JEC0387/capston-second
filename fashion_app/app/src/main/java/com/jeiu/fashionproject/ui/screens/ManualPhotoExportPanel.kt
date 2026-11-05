package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
    val current by rememberUpdatedState(input)
    var result by remember { mutableStateOf<ManualPhotoResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var comparison by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var reading by remember { mutableStateOf(false) }
    var selectingSaved by remember { mutableStateOf(false) }
    val visible = result?.takeIf { it.input == input }
    LaunchedEffect(input) {
        if (result?.input != input) { comparison = false; original = false }
        // Existing export bytes stay frozen while the document picker is open.
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { target ->
        val bytes = pendingSave; pendingSave = null
        if (target != null && bytes != null && !saving) {
            saving = true
            scope.launch {
                try { LocalVtonRepository.save(context, bytes, target, true); message = "2D 수동 합성 PNG를 저장했어요. 저장 이미지 열기로 다시 확인할 수 있어요." }
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
        Text("기기 내 2D 합성 · 생성형 AI 아님", style = MaterialTheme.typography.labelMedium)
        Text("현재 옷 위치·크기·회전·투명도를 PNG로 만들어요. 팔 가림·주름·조명은 새로 생성하지 않아요.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(enabled = input != null && !busy && !saving && pendingSave == null,
                modifier = Modifier.testTag("manualPngCreate"), onClick = {
                    val snapshot = input ?: return@TextButton
                    if (busy) return@TextButton
                    busy = true; result = null; message = null; reopened = null
                    job = scope.launch {
                        try {
                            val rendered = create(snapshot)
                            if (current == snapshot) { result = rendered; comparison = true; original = false }
                            else message = "합성 중 사진이나 배치가 바뀌었어요. 현재 배치로 다시 만들어주세요."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "합성하지 못했어요. 기기에서 열 수 있는 사진과 의류를 선택해주세요. 외부 이미지는 가져오지 않아요." }
                        finally { busy = false; job = null }
                    }
                }) { Text(if (busy) "합성 중" else "현재 배치 PNG 만들기") }
            if (busy) TextButton(onClick = { job?.cancel(); message = "합성을 취소했어요." }, modifier = Modifier.testTag("manualPngCancel")) { Text("취소") }
        }
        Row {
            TextButton(enabled = visible != null, onClick = { comparison = true; original = false }, modifier = Modifier.testTag("manualPngCompare")) { Text("원본·합성 비교") }
            TextButton(enabled = visible != null && !saving && pendingSave == null, modifier = Modifier.testTag("manualPngSave"), onClick = {
                val bytes = visible?.png ?: return@TextButton
                if (saving || pendingSave != null) return@TextButton
                pendingSave = bytes.copyOf()
                try { savePicker.launch("manual-2d-fitting-${System.currentTimeMillis()}.png") }
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
        title = { Text("2D 수동 합성 비교") },
        text = { Column {
            Text(if (original) "원본 · 같은 캔버스 비율" else "수동 합성 PNG · AI 생성 아님")
            TextButton(onClick = { original = !original }, modifier = Modifier.testTag("manualPngToggle")) { Text(if (original) "합성 보기" else "원본 보기") }
            AsyncImage(if (original) visible.original else visible.png, "수동 합성 비교", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(280.dp))
        } }, confirmButton = { TextButton(onClick = { comparison = false }) { Text("비교 닫기") } })
    reopened?.let { bytes -> AlertDialog(onDismissRequest = { reopened = null }, title = { Text("선택한 저장 이미지 · 새 생성 아님") },
        text = { AsyncImage(bytes, "저장 이미지", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) },
        confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
}
