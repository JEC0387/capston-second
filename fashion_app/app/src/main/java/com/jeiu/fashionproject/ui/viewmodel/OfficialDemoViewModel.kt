package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.BuildConfig
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

interface DemoJournal { fun read(): String?; fun write(value: String) }
class PrivateDemoJournal(context: Context) : DemoJournal {
    private val file = AtomicFile(File(context.noBackupFilesDir, "official-demo-job.json"))
    override fun read(): String? = try {
        // openRead restores a legacy .bak even when the base file disappeared during an interrupted write.
        file.openRead().bufferedReader().use { it.readText() }
    } catch (e: java.io.FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
        null
    }
    override fun write(value: String) {
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        catch (e: Exception) { file.failWrite(stream); throw e }
        file.finishWrite(stream)
        // Do not call failWrite after finishing: preserve the last durable record on verification failure.
        if (read() != value) throw java.io.IOException("Journal commit failed")
    }
}
data class DemoUiState(
    val phase: String = "idle", val working: Boolean = false, val ready: Boolean = false,
    val message: String = "공개 API 준비 확인 필요 · 자체 서버와 별개예요",
    val original: DemoImage? = null, val result: DemoImage? = null, val savedAt: Long = 0
)

/** Activity scoped UI + no-backup durable journal. No automatic POST on recreation or retry. */
class OfficialDemoViewModel(
    private val gateway: OfficialDemoGateway,
    private val journal: DemoJournal,
    private val enabled: Boolean = BuildConfig.DEBUG && BuildConfig.PREVIEW_BUILD,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private var record = JSONObject()
    var state by mutableStateOf(DemoUiState())
        private set
    private var operation: Job? = null
    private var submitting = false
    private var stopRequested = false
    val eventId: String? get() = record.optString("eventId").takeIf { it.isNotBlank() }
    val canResume get() = eventId != null && !state.working && state.phase !in setOf("failed", "expired", "quota", "storage")
    val canStart get() = enabled && state.ready && state.phase == "idle" && !state.working
    init {
        try {
            journal.read()?.let {
                record = JSONObject(it)
                if (record.optString("engine") != OfficialDemoContract.ENGINE) throw DemoFailure("storage")
                eventId?.let(OfficialDemoContract::eventId)
                state = state.copy(phase = record.optString("phase", "unknown"),
                    message = "이전 시험 기록이 있어요. 같은 작업 조회만 가능하며 자동 재생성하지 않아요.")
            }
        } catch (_: Exception) { state = state.copy(phase = "storage", message = OfficialDemoContract.message("storage")) }
    }
    private fun persist() {
        try { journal.write(record.toString()) } catch (_: Exception) { throw DemoFailure("storage") }
    }
    fun check() {
        if (!enabled || state.working || state.phase != "idle") return
        state = state.copy(working = true, ready = false, message = "공개 API 명세 확인 중…")
        operation = viewModelScope.launch {
            try {
                gateway.check()
                state = state.copy(ready = true, message = "공개 API 명세 확인 완료 · 무료 GPU 할당과 생성 성공은 보장되지 않아요")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { state = state.copy(message = OfficialDemoContract.message((e as? DemoFailure)?.kind ?: "unavailable")) }
            finally { state = state.copy(working = false) }
        }
    }
    fun start(exampleId: String, consent: Boolean) {
        if (!consent || !canStart || operation?.isActive == true) return
        try { OfficialDemoContract.exampleId(exampleId) } catch (_: Exception) {
            state = state.copy(message = "공식 예제 ID만 허용돼요. 사진·URL·파일 경로는 받지 않아요."); return
        }
        state = state.copy(working = true, phase = "preparing", result = null, savedAt = 0, message = "공식 예제와 현재 API 확인 중…")
        stopRequested = false
        operation = viewModelScope.launch {
            try {
                gateway.check()
                state = state.copy(original = gateway.example(exampleId))
                ensureActive()
                record = JSONObject().put("engine", OfficialDemoContract.ENGINE).put("exampleId", exampleId)
                    .put("attemptId", UUID.randomUUID().toString()).put("startedAt", now()).put("phase", "submitting")
                    .put("appInitiated", true).put("paidRequest", false)
                persist() // If this fails, POST must never start.
                submitting = true
                state = state.copy(phase = "submitting", message = "공식 예제 생성 요청 전송 중 · 자동 재전송 없음")
                val id = try { gateway.submit(exampleId) } finally { submitting = false }
                OfficialDemoContract.eventId(id)
                record.put("eventId", id).put("phase", "waiting")
                persist() // Keep ID before reading the stream, even when the user stopped waiting during POST.
                if (!stopRequested) receive(id) else state = state.copy(phase = "stopped")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { fail(e) }
            finally { state = state.copy(working = false) }
        }
    }
    fun resume() {
        if (!enabled || !canResume || operation?.isActive == true) return
        val id = eventId ?: return
        state = state.copy(working = true, message = "기존 작업 조회 중 · 새 생성 요청 없음")
        stopRequested = false
        operation = viewModelScope.launch {
            try {
                if (state.original == null) state = state.copy(original = gateway.example(OfficialDemoContract.EXAMPLE))
                receive(id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) { fail(e) }
            finally { state = state.copy(working = false) }
        }
    }
    private suspend fun receive(id: String) {
        state = state.copy(phase = "waiting", message = "공개 서비스 대기열·생성 대기 중")
        val completed = withTimeoutOrNull(310_000) {
            val url = record.optString("resultUrl").takeIf { it.isNotBlank() }
                ?: gateway.awaitResult(id) { progress -> withContext(Dispatchers.Main) { state = state.copy(message = progress) } }
            if (!OfficialDemoContract.resultUrl(url)) throw DemoFailure("unsafe_result")
            record.put("resultUrl", url); persist()
            val image = gateway.result(url)
            ensureActive()
            record.put("phase", "complete").put("receivedAt", now()).put("sha256", image.sha256)
                .put("width", image.width).put("height", image.height)
            persist()
            state = state.copy(phase = "complete", result = image,
                message = "공식 공개 데모의 새 요청 결과 수신 · 품질을 확인한 뒤 저장해주세요")
            true
        }
        if (completed != true) throw DemoFailure("timeout")
    }
    private fun fail(e: Exception) {
        var kind = (e as? DemoFailure)?.kind ?: if (e is java.io.InterruptedIOException) "timeout" else "network"
        if (record.optString("phase") == "submitting" && eventId == null &&
            kind !in setOf("quota", "unavailable", "expired", "storage")) kind = "unknown_submission"
        val phase = when (kind) { "quota", "expired", "storage" -> kind; "server_failed" -> "failed"; else -> "interrupted" }
        record.put("phase", phase).put("errorKind", kind)
        if (record.has("engine")) try { persist() } catch (_: Exception) { kind = "storage" }
        state = state.copy(phase = if (kind == "storage") "storage" else phase, message = OfficialDemoContract.message(kind))
    }
    fun stopWaiting() {
        stopRequested = true
        if (!submitting) operation?.cancel() // Never claim or send remote cancellation.
        state = state.copy(phase = "stopped", message = "앱에서 대기 종료 · 서버 생성 취소·삭제는 확인되지 않았어요. 작업 번호가 있으면 같은 작업만 조회할 수 있어요.")
    }
    fun prepareNextTest(acknowledged: Boolean) {
        if (!acknowledged || state.working || operation?.isActive == true || state.phase == "storage") return
        // Conservative developer-mode budget, independent of the provider's changing quota.
        if (record.has("startedAt") && now() - record.optLong("startedAt") < 24 * 60 * 60 * 1000L) {
            state = state.copy(message = "반복 호출 방지를 위해 이 기기의 새 시험은 마지막 전송 24시간 후 가능해요. 기존 작업 조회는 가능해요."); return
        }
        try {
            record = JSONObject().put("engine", OfficialDemoContract.ENGINE).put("phase", "idle")
            persist(); state = DemoUiState()
        } catch (_: Exception) { state = state.copy(phase = "storage", message = OfficialDemoContract.message("storage")) }
    }
    fun save(context: Context, target: Uri?) {
        val image = state.result ?: return
        if (target == null || state.working) return
        state = state.copy(working = true)
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target, "wt")?.use { it.write(image.png) }
                        ?: throw DemoFailure("storage")
                }
                val time = now()
                record.put("savedAt", time); persist()
                state = state.copy(savedAt = time, message = "공식 예제 결과 PNG 저장 완료 · 내 사진 피팅 결과가 아니에요")
            } catch (_: Exception) { state = state.copy(message = "저장 완료를 확인하지 못했어요. 저장 위치를 확인해주세요.") }
            finally { state = state.copy(working = false) }
        }
    }
}
