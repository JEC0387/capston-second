package com.jeiu.fashionproject.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.LocalVtonRepository
import kotlinx.coroutines.*

/** Local renderer export only. Does not call an AI provider or change saved placement. */
@Composable
internal fun AvatarOutfitExportPanel(input: AvatarOutfitSnapshot?, create: suspend (AvatarOutfitSnapshot) -> ByteArray) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(input)
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var output by remember { mutableStateOf<Pair<AvatarOutfitSnapshot, ByteArray>?>(null) }
    var preview by remember { mutableStateOf(false) }
    var reopened by remember { mutableStateOf<ByteArray?>(null) }
    var reading by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val visible = output?.takeIf { it.first == input }
    // A result may resume in the same frame as an edit, before rememberUpdatedState updates.
    // The render-time guard already hides it; discard it after composition as well.
    LaunchedEffect(input, output?.first) {
        if (output != null && output?.first != input) {
            output = null; preview = false
            message = "체형이나 배치가 바뀌어 이전 미리보기를 닫았어요. 현재 화면에서 다시 저장해주세요."
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bytes = pending; pending = null
        if (uri != null && bytes != null && !saving) {
            saving = true
            scope.launch {
                try { LocalVtonRepository.save(context, bytes, uri, true); message = "3D 착장 PNG를 저장했어요. 저장 이미지 열기로 확인해주세요." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "저장하지 못했어요. 저장 위치와 접근 권한을 확인해주세요." }
                finally { saving = false }
            }
        } else if (uri != null) message = "저장 연결이 사라졌어요. 현재 결과를 확인하고 다시 저장해주세요."
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selecting = false
        if (uri != null && !reading) {
            reading = true
            scope.launch {
                try { reopened = LocalVtonRepository.openSavedImage(context, uri) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "저장 이미지를 열지 못했어요. 파일과 접근 권한을 확인해주세요." }
                finally { reading = false }
            }
        }
    }
    val createAction: @Composable (Modifier) -> Unit = { actionModifier ->
        TextButton(enabled = input != null && input.layers.isNotEmpty() && !input.inspecting && !busy && !saving && pending == null,
            modifier = actionModifier.testTag("avatarPngCreate"), onClick = {
                val snapshot = input ?: return@TextButton
                if (busy) return@TextButton
                busy = true; output = null; message = null
                job = scope.launch {
                    try {
                        val bytes = withTimeout(20000) { create(snapshot) }
                        if (current == snapshot) { output = snapshot to bytes; preview = true }
                        else message = "체형이나 배치가 바뀌어 이전 미리보기를 닫았어요. 현재 화면에서 다시 저장해주세요."
                    } catch (_: TimeoutCancellationException) { message = "3D 화면 응답이 늦어 중단했어요. 모델이 보이면 다시 저장해주세요." }
                    catch (_: OutOfMemoryError) { message = "이미지를 만들 메모리가 부족해요. 기존 체형과 배치는 유지돼요." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "3D 화면을 저장하지 못했어요. 모델과 옷이 보인 뒤 다시 눌러주세요." }
                    finally { busy = false; job = null }
                }
            }) { Text(if (busy) "이미지 만드는 중" else "3D 착장 이미지 저장", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
    val secondaryAction: @Composable (Modifier) -> Unit = { actionModifier ->
        if (busy) TextButton(onClick = { job?.cancel() }, modifier = actionModifier.testTag("avatarPngCancel")) { Text("취소", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        else TextButton(enabled = !reading && !selecting, modifier = actionModifier.testTag("avatarPngOpen"), onClick = {
            selecting = true
            try { open.launch(arrayOf("image/png")) }
            catch (_: Exception) { selecting = false; message = "파일 선택 화면을 열지 못했어요." }
        }) { Text("저장 이미지 열기", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
    // Stable action area while rendering. Large text needs full-width buttons, not clipped columns.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (fontScale >= 1.5f || maxWidth < 260.dp) {
            Column {
                val action = Modifier.fillMaxWidth().height(56.dp * fontScale)
                createAction(action)
                secondaryAction(action)
            }
        } else Row(Modifier.height(72.dp * fontScale)) {
            createAction(Modifier.weight(1f))
            secondaryAction(Modifier.weight(1f))
        }
    }
    if (preview && visible != null) AlertDialog(onDismissRequest = { preview = false },
        title = { Text("3D 착장 이미지") },
        text = { Column { Text("3D 아바타와 배치한 옷을 저장해요. AI가 만든 착장 사진은 아니에요.")
            AsyncImage(visible.second, "3D 착장 저장 미리보기", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(280.dp)) } },
        confirmButton = { TextButton(enabled = !saving && pending == null, modifier = Modifier.testTag("avatarPngSave"), onClick = {
            pending = visible.second.copyOf()
            try { save.launch("FashionFit-3D-${System.currentTimeMillis()}.png"); preview = false }
            catch (_: Exception) { pending = null; message = "저장 화면을 열지 못했어요." }
        }) { Text("PNG 저장") } }, dismissButton = { TextButton(onClick = { preview = false }) { Text("취소") } })
    reopened?.let { bytes -> AlertDialog(onDismissRequest = { reopened = null }, title = { Text("저장한 이미지") },
        text = { AsyncImage(bytes, "다시 연 저장 이미지", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) },
        confirmButton = { TextButton(onClick = { reopened = null }) { Text("닫기") } }) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }) }
}
