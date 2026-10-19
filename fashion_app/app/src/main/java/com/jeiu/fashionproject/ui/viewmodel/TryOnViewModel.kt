package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.*
import java.util.UUID

data class TryOnUiState(
    val pending: Boolean = false,
    val watching: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val result: ByteArray? = null,
    val resultPerson: String? = null
)

/** Navigation/recomposition never owns a paid operation. Recovery performs GET only. */
class TryOnViewModel(private val saved: SavedStateHandle) : ViewModel() {
    var gateway: TryOnGateway = TryOnRepository
    var selectedGarments by mutableStateOf<List<TryOnGarment>>(emptyList())
        private set
    var selectedPerson by mutableStateOf<Uri?>(saved.get<String>("person")?.let(Uri::parse))
        private set
    var state by mutableStateOf(TryOnUiState(pending = saved.get<String>("requestId") != null,
        error = if (saved.get<String>("requestId") != null) "기존 작업의 상태를 확인해주세요. 새 생성 요청은 전송하지 않았어요." else null))
        private set
    private var operation: Job? = null
    private var connection: TryOnConnection? = null
    private var submitted = false
    val requestId: String? get() = saved["requestId"]

    fun generate(context: Context, person: Uri?, garments: List<TryOnGarment>, approved: Boolean) {
        val plan = TryOnPlan.build(garments)
        if (!approved || state.pending || operation?.isActive == true) return
        if (person == null || plan.issue != null) {
            state = state.copy(error = if (person == null) "먼저 전신 사진을 선택해주세요." else plan.issue)
            return
        }
        val config = TryOnSession.connection ?: run {
            state = state.copy(error = "AI 서버와 개인 접속 토큰을 먼저 연결해주세요.")
            return
        }
        val appContext = context.applicationContext
        selectedGarments = garments.toList()
        selectedPerson = person
        connection = config
        submitted = false
        saved.remove<String>("requestId")
        // Synchronous guard before launching, shared by both entry points.
        state = state.copy(pending = true, watching = true, progress = "사진 준비 중…", error = null)
        operation = viewModelScope.launch {
            var accepted = false
            try {
                gateway.check(config) // no images or paid request when server is disabled
                val input = gateway.image(appContext, person.toString())
                val prepared = plan.garments.map { garment ->
                    TryOnPlan.categoryFor(garment.category)!! to gateway.image(appContext, garment.image)
                }
                ensureActive()
                val id = UUID.randomUUID().toString()
                saved["requestId"] = id
                saved["base"] = config.base
                saved["person"] = person.toString() // URI metadata only; no images or credentials
                submitted = true // outcome may be unknown once HTTP transmission starts
                gateway.submit(config, id, input, prepared) // exactly one POST; no retry interceptor
                accepted = true
                watch(config, id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                val rejected = !accepted && failure is TryOnHttpFailure && failure.code in setOf(400, 401, 403, 413, 422, 429, 503)
                val uncertain = submitted && !rejected
                if (!uncertain) saved.remove<String>("requestId")
                state = state.copy(pending = uncertain, watching = false, progress = null,
                    error = failure.message?.takeIf { failure is TryOnFailure || failure is TryOnHttpFailure }
                        ?: if (uncertain) "응답을 받지 못했어요. 기존 작업 상태만 다시 확인해주세요. 생성 요청을 반복하지 않았어요."
                        else "사진 준비 또는 서버 연결에 실패했어요. 입력 사진과 연결 설정을 확인해주세요.")
            }
        }
    }

    fun resume() {
        if (operation?.isActive == true) return
        val id = requestId ?: return
        val config = connection ?: TryOnSession.connection ?: return
        if (saved.get<String>("base") != config.base) {
            state = state.copy(error = "기존 요청을 보낸 HTTPS 서버에 다시 연결해주세요.")
            return
        }
        connection = config
        state = state.copy(pending = true, watching = true, progress = "기존 AI 작업 상태 확인 중…", error = null)
        operation = viewModelScope.launch {
            try { watch(config, id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                state = state.copy(pending = true, watching = false, progress = null,
                    error = "기존 작업의 상태를 확인하지 못했어요. 같은 서버·접속 토큰으로 다시 확인해주세요. 새 생성 요청은 보내지 않아요.")
            }
        }
    }

    private suspend fun watch(config: TryOnConnection, id: String) {
        // Bound client polling. Server has an independent per-garment deadline.
        withTimeoutOrNull(430_000) {
            while (true) {
                val job = gateway.status(config, id)
                when (job.status) {
                    "completed" -> {
                        val bytes = if (job.resultAvailable) gateway.result(config, id) else null
                        state = state.copy(pending = false, watching = false, progress = null,
                            error = if (bytes == null) TryOnRepository.friendly("result_expired") else null,
                            result = bytes ?: state.result, resultPerson = if (bytes != null) saved["person"] else state.resultPerson)
                        return@withTimeoutOrNull true
                    }
                    "failed", "timeout", "cancelled", "interrupted" -> {
                        state = state.copy(pending = false, watching = false, progress = null, error = TryOnRepository.friendly(job.error))
                        return@withTimeoutOrNull true
                    }
                    else -> state = state.copy(progress = "AI가 옷을 자연스럽게 입히는 중이에요 (${job.step.coerceAtLeast(1)}/${job.count})")
                }
                delay(3_000)
            }
        } ?: run {
            state = state.copy(pending = true, watching = false, progress = null,
                error = "대기 시간이 길어졌어요. 기존 작업 상태를 다시 확인해주세요. 새 생성 요청은 전송하지 않아요.")
        }
    }

    fun cancel() {
        if (!state.pending) return
        if (!submitted && requestId == null) {
            operation?.cancel()
            state = state.copy(pending = false, watching = false, progress = null, error = "전송 전에 취소했어요.")
            return
        }
        val id = requestId ?: return
        val config = connection ?: TryOnSession.connection ?: return
        // Do not cancel an in-flight POST; resolve its outcome with the same ID.
        viewModelScope.launch {
            try {
                gateway.cancel(config, id)
                state = state.copy(error = "대기 중단을 요청했어요. 이미 전송된 FASHN 작업·요금은 취소되지 않을 수 있어요.")
                if (!state.watching) resume()
            } catch (_: Exception) {
                state = state.copy(error = "중단 여부를 확인하지 못했어요. 기존 작업 상태를 확인해주세요.")
            }
        }
    }
}
