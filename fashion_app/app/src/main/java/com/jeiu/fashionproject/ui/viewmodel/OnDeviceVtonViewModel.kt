package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.coroutineContext

enum class DeviceVtonStatus { IDLE, PREPARING, RUNNING, SUCCEEDED, FAILED, CANCELLING, CANCELLED, TIMED_OUT, BLOCKED }

/** In-memory receipt only. No input URI, token, or result is persisted automatically. */
class DeviceVtonResult(val jobId: String, selection: DeviceVtonSelection, model: DeviceModelSpec,
    val inputDigest: String, original: ByteArray, png: ByteArray) {
    val selection = selection.copy()
    val model = model.snapshot()
    private val originalBytes = original.copyOf()
    private val pngBytes = png.copyOf()
    val original: ByteArray get() = originalBytes.copyOf()
    val png: ByteArray get() = pngBytes.copyOf()
    val sha256 = deviceSha256(pngBytes)
    override fun toString() = "DeviceVtonResult(jobId=$jobId, model=${model.id}, bytes=${pngBytes.size})"
}

data class DeviceVtonUiState(
    val status: DeviceVtonStatus = DeviceVtonStatus.IDLE,
    val pending: Boolean = false,
    val jobId: String? = null,
    val error: String? = null,
    val result: DeviceVtonResult? = null,
    val resultFromPreviousAttempt: Boolean = false
)

/** Single-flight Android boundary. The shipped engine is unavailable, never a synthetic fallback.
 * A future native adapter must remain joined while cancellation is pending; a timeout cannot
 * assert that native execution stopped. Going back/re-entering never submits another request. */
class OnDeviceVtonViewModel(
    private val engine: DeviceVtonEngine = UnavailableDeviceVtonEngine,
    private val preparer: suspend (Context, String, DeviceVtonSelection, DeviceModelSpec) -> DevicePreparedJob =
        OnDeviceVtonImages::prepare,
    private val timeoutMillis: Long = 120_000,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    init { require(timeoutMillis > 0) }

    val availability: DeviceEngineAvailability get() = engine.availability
    var state by mutableStateOf(DeviceVtonUiState())
        private set
    private var currentSelection: DeviceVtonSelection? = null
    private var operation: Job? = null
    private var watchdog: Job? = null
    private var cancelled = false
    private var timedOut = false
    private var cleared = false
    private val roles = DeviceGarmentRole.entries.toSet()
    private val shaPattern = Regex("[a-f0-9]{64}")

    fun issueFor(selection: DeviceVtonSelection?): String? = issueFor(selection, availability)

    private fun issueFor(selection: DeviceVtonSelection?, available: DeviceEngineAvailability): String? {
        if (available is DeviceEngineAvailability.Unavailable) return available.reason
        val model = (available as DeviceEngineAvailability.Ready).model
        if (model.id.isBlank() || model.revision.isBlank() || !shaPattern.matches(model.sha256) ||
            model.maxSide !in 32..2048 || model.supportedRoles != roles)
            return "상의·하의·신발 전체를 지원하는 검증된 모델 설정이 필요해요."
        if (selection == null || selection.person.isBlank()) return "먼저 모델 사진을 선택해주세요."
        val garments = selection.garments
        if (garments.size != 3 || garments.map { it.role }.toSet() != roles)
            return "상의·하의·신발을 각각 한 벌씩 선택해주세요."
        if (garments.any { it.id.isBlank() || it.source.isBlank() } || garments.map { it.id }.distinct().size != 3)
            return "선택한 의류 정보를 확인하고 다시 선택해주세요."
        return null
    }

    fun resultMatches(selection: DeviceVtonSelection?): Boolean = selection != null &&
        currentSelection == selection && state.result?.selection == selection

    fun markResultAsPrevious() {
        if (state.result != null) state = state.copy(resultFromPreviousAttempt = true)
    }

    fun selectionChanged(selection: DeviceVtonSelection?) {
        if (currentSelection == selection) return
        currentSelection = selection?.copy()
        markResultAsPrevious()
        if (operation != null) cancel()
    }

    fun start(context: Context, selection: DeviceVtonSelection, approved: Boolean) {
        // Keep the lock even after cancel() until the engine and its structured children return.
        if (operation != null || cleared) return
        val frozen = selection.copy()
        currentSelection = frozen
        // Validate and prepare the same immutable readiness snapshot. A runtime
        // can unload or replace its model between two availability reads.
        val available = availability
        val issue = issueFor(frozen, available)
        if (issue != null || !approved) {
            state = state.copy(status = DeviceVtonStatus.BLOCKED, pending = false,
                error = issue ?: "기기 내 AI 처리 안내를 확인하고 동의해주세요.",
                resultFromPreviousAttempt = state.result != null)
            return
        }
        val model = (available as DeviceEngineAvailability.Ready).model.snapshot()
        val id = UUID.randomUUID().toString()
        cancelled = false
        timedOut = false
        state = state.copy(status = DeviceVtonStatus.PREPARING, pending = true, jobId = id,
            error = null, resultFromPreviousAttempt = state.result != null)
        val worker = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val job = withContext(workDispatcher) { preparer(context.applicationContext, id, frozen, model) }
                validatePrepared(job, id, frozen, model)
                coroutineContext.ensureActive()
                state = state.copy(status = DeviceVtonStatus.RUNNING)
                val output = withContext(workDispatcher) { engine.generate(job) }
                coroutineContext.ensureActive()
                val bytes = withContext(workDispatcher) { validateOutput(job, output) }
                coroutineContext.ensureActive()
                if (!cancelled && !cleared && currentSelection == frozen) {
                    state = state.copy(status = DeviceVtonStatus.SUCCEEDED, error = null,
                        result = DeviceVtonResult(id, frozen, model, job.inputDigest, job.person.bytes, bytes),
                        resultFromPreviousAttempt = false)
                }
            } catch (_: CancellationException) {
                if (!cleared) state = state.copy(status = if (timedOut) DeviceVtonStatus.TIMED_OUT else DeviceVtonStatus.CANCELLED,
                    error = if (timedOut) "처리 시간이 초과됐어요. 작업 종료를 확인한 뒤 직접 다시 시도해주세요." else "생성 대기를 취소했어요.")
            } catch (_: OutOfMemoryError) {
                if (!cleared) state = state.copy(status = DeviceVtonStatus.FAILED,
                    error = "기기 메모리가 부족해 생성을 완료하지 못했어요. 자동 재시도하지 않아요.")
            } catch (_: Exception) {
                // Never expose native exception text, input paths, or future adapter credentials.
                if (!cleared) state = state.copy(status = DeviceVtonStatus.FAILED,
                    error = "입력 또는 생성 결과를 검증하지 못했어요. 이전 결과를 새 생성으로 표시하지 않아요.")
            }
        }
        operation = worker
        // Also runs when cancelled before the LAZY coroutine executes its first instruction.
        worker.invokeOnCompletion {
            if (operation === worker) {
                watchdog?.cancel()
                watchdog = null
                operation = null
                if (!cleared) state = state.copy(pending = false,
                    status = when {
                        timedOut -> DeviceVtonStatus.TIMED_OUT
                        cancelled -> DeviceVtonStatus.CANCELLED
                        else -> state.status
                    }, error = when {
                        timedOut -> "처리 시간이 초과됐어요. 종료된 작업은 자동 재시도하지 않아요."
                        cancelled -> "생성 대기를 취소했어요."
                        else -> state.error
                    })
            }
        }
        watchdog = viewModelScope.launch {
            delay(timeoutMillis)
            if (operation != null && state.jobId == id) requestStop(timeout = true)
        }
        worker.start()
    }

    fun cancel() = requestStop(timeout = false)

    private fun requestStop(timeout: Boolean) {
        val active = operation ?: return
        cancelled = true
        timedOut = timedOut || timeout
        state = state.copy(status = if (timedOut) DeviceVtonStatus.TIMED_OUT else DeviceVtonStatus.CANCELLING,
            error = if (timedOut) "처리 시간이 초과됐어요. 실행 중인 작업이 종료될 때까지 기다려주세요."
                else "취소를 요청했어요. 실행 중인 작업이 종료될 때까지 기다려주세요.",
            resultFromPreviousAttempt = state.result != null)
        runCatching { state.jobId?.let(engine::requestCancellation) }
        active.cancel()
    }

    private fun validatePrepared(job: DevicePreparedJob, id: String, selection: DeviceVtonSelection, model: DeviceModelSpec) {
        if (job.id != id || job.selection != selection || job.model != model || job.garments.keys != roles ||
            !shaPattern.matches(job.inputDigest)) throw DeviceVtonFailure("입력 고정 정보가 일치하지 않아요.")
    }

    private fun validateOutput(job: DevicePreparedJob, output: DeviceEngineOutput): ByteArray {
        if (output.jobId != job.id || output.inputDigest != job.inputDigest || output.modelSha256 != job.model.sha256 ||
            output.categories != roles) throw DeviceVtonFailure("생성 결과의 작업 정보가 일치하지 않아요.")
        val bytes = output.png
        OnDeviceVtonImages.validateResultPng(bytes)
        val sha = deviceSha256(bytes)
        if (sha == job.person.sha256 || job.garments.values.any { it.sha256 == sha })
            throw DeviceVtonFailure("입력 이미지는 새 생성 결과가 아니에요.")
        return bytes
    }

    override fun onCleared() {
        cleared = true
        cancel()
        super.onCleared()
    }
}
