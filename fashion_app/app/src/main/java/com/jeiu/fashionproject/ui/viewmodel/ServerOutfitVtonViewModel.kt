package com.jeiu.fashionproject.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.coroutineContext

class OutfitServerImage(bytes: ByteArray, original: ByteArray?) {
    private val frozen = bytes.copyOf()
    private val originalCopy = original?.copyOf()
    val png get() = frozen.copyOf()
    val original get() = originalCopy?.copyOf()
}

data class ServerOutfitUiState(val phase: String = "idle", val busy: Boolean = false,
    val config: OutfitServerConfig? = null, val configBinding: String? = null, val configHost: String? = null,
    val ticket: OutfitServerTicket? = null,
    val status: OutfitServerStatus? = null, val result: OutfitServerImage? = null,
    val previous: Boolean = false, val error: String? = null)

/** Durable metadata precedes POST. Recovery ONLY queries the existing request; never re-POSTs. */
class ServerOutfitVtonViewModel(
    private val gateway: OutfitServerGateway = ServerOutfitVtonRepository,
    private val storeFactory: (Context) -> OutfitTicketStore = { PrivateOutfitTicketStore(it) },
    private val preparer: suspend (Context, String, DeviceVtonSelection, DeviceModelSpec) -> DevicePreparedJob = OnDeviceVtonImages::prepare,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pollMillis: Long = 1500,
    private val localWaitMillis: Long = 120_000
) : ViewModel() {
    var state by mutableStateOf(ServerOutfitUiState())
        private set
    private var store: OutfitTicketStore? = null
    private var initialized = false
    private var locked = false
    private var operation: Job? = null
    private var selection: DeviceVtonSelection? = null
    private var original: ByteArray? = null
    private var cancelWanted = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        try {
            store = storeFactory(context.applicationContext)
            val ticket = store!!.read()
            state = state.copy(ticket = ticket, phase = if (ticket == null) "idle" else "recovered",
                previous = ticket != null, error = if (ticket != null) "이전 서버 작업 번호를 복구했어요. 상태 조회만 하며 새 생성 요청은 보내지 않아요." else null)
        } catch (_: Exception) {
            locked = true
            state = state.copy(phase = "blocked", error = "작업 기록을 읽지 못해 새 요청을 차단했어요. 기존 파일은 보존했어요.")
        }
    }

    fun selectionChanged(value: DeviceVtonSelection?) {
        if (selection == value) return
        selection = value?.copy()
        // In-flight request bytes remain unchanged. A later response is explicitly previous.
        state = state.copy(result = null, previous = state.ticket != null)
    }

    private fun sessionFor(ticket: OutfitServerTicket? = state.ticket): OutfitServerSession {
        val session = gateway.session()
        if (ticket != null && (session.accountKey != ticket.accountKey || session.base != ticket.base))
            throw OutfitServerFailure("account_changed")
        return session
    }

    fun canDisplay(): Boolean = state.result != null && runCatching { sessionFor(); true }.getOrDefault(false)

    /** Reuse the preparer's metadata/URI checks without opening any image. */
    fun inputReady(selected: DeviceVtonSelection?): Boolean {
        val config = state.config?.takeIf { it.ready } ?: return false
        if (selected == null) return false
        return runCatching {
            OnDeviceVtonImages.validateSelection("server-input-readiness", selected,
                DeviceModelSpec(config.modelId, config.revision, config.modelSha, DeviceGarmentRole.entries.toSet()))
        }.isSuccess
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        if (operation != null || locked) return
        state = state.copy(busy = true, error = null)
        val task = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: OutfitServerFailure) {
                state = state.copy(phase = "blocked", result = null,
                    error = ServerOutfitVtonRepository.friendly(failure.code))
            } catch (_: OutOfMemoryError) {
                state = state.copy(phase = "failed", result = null, error = "메모리가 부족해 중단했어요. 기존 작업 번호는 유지하며 자동 재전송하지 않아요.")
            } catch (_: Exception) {
                state = state.copy(phase = "unknown", result = null,
                    error = "통신 또는 파일 처리가 중단됐어요. 생성 여부가 불명확하므로 상태 조회만 다시 하세요. 자동 재전송하지 않아요.")
            }
        }
        operation = task
        task.invokeOnCompletion {
            if (operation === task) { operation = null; state = state.copy(busy = false) }
        }
        task.start()
    }

    fun refreshConfig() = launchExclusive {
        val current = gateway.session()
        val config = gateway.config(current)
        sessionFor(null).also { if (it.accountKey != current.accountKey || it.base != current.base) throw OutfitServerFailure("account_changed") }
        state = state.copy(config = config, configBinding = current.accountKey,
            configHost = java.net.URI(current.base).host, phase = if (config.ready) "ready" else "blocked",
            error = if (config.ready) null else ServerOutfitVtonRepository.friendly(config.reason))
    }

    fun start(context: Context, selected: DeviceVtonSelection?, approved: Boolean) {
        initialize(context)
        if (operation != null || locked) return
        val config = state.config
        if (!approved || selected == null || config?.ready != true || !inputReady(selected)) {
            state = state.copy(phase = "blocked", error = "모델 사진·상의·하의·신발, 서버 준비 상태와 외부 전송 동의를 먼저 확인해주세요.")
            return
        }
        // A lost response, process restart or cancellation never authorizes a second POST.
        if (state.ticket != null && state.status?.status !in ServerOutfitVtonRepository.terminal) {
            state = state.copy(error = "이전 작업의 종료 상태를 먼저 조회해주세요. 새 요청을 중복 전송하지 않아요.")
            return
        }
        val frozen = selected.copy()
        selection = frozen
        cancelWanted = false
        launchExclusive {
            val session = gateway.session()
            // Consent/config obtained for account/server A must never authorize B.
            // Reject before opening any selected image. UI consent also resets on this binding.
            if (state.configBinding != session.accountKey) {
                state = state.copy(config = null, configBinding = null, configHost = null)
                throw OutfitServerFailure("account_changed")
            }
            val id = UUID.randomUUID().toString()
            state = state.copy(phase = "preparing", result = null, previous = false)
            val job = preparer(context.applicationContext, id, frozen,
                DeviceModelSpec(config.modelId, config.revision, config.modelSha, DeviceGarmentRole.entries.toSet()))
            coroutineContext.ensureActive()
            if (cancelWanted) { state = state.copy(phase = "cancelled", error = "전송 전에 취소했어요."); return@launchExclusive }
            if (job.id != id || job.selection != frozen || job.model.id != config.modelId ||
                job.model.revision != config.revision || job.model.sha256 != config.modelSha ||
                job.model.supportedRoles != DeviceGarmentRole.entries.toSet())
                throw OutfitServerFailure("input_changed")
            val body = ServerOutfitVtonRepository.requestBody(job, config)
            val created = clock()
            val ticket = OutfitServerTicket(id, deviceSha256(body), config.revision, session.base,
                session.accountKey, ServerOutfitVtonRepository.selectionSha(frozen), created,
                created + config.retentionSeconds * 1000, config.modelSha)
            sessionFor(null).also { if (it.accountKey != session.accountKey || it.base != session.base) throw OutfitServerFailure("account_changed") }
            store!!.write(ticket) // Synchronous atomic metadata commit, before first byte of POST.
            original = job.person.bytes
            state = state.copy(ticket = ticket, status = null, phase = "submitting")
            val first = gateway.submit(session, ticket, body)
            sessionFor(ticket)
            applyStatus(first)
            poll(session, ticket, first)
        }
    }

    /** Explicit resume button is safe after process death: only GET, no stored image inputs. */
    fun resume() = launchExclusive {
        val ticket = state.ticket ?: return@launchExclusive
        val session = sessionFor(ticket)
        val first = gateway.status(session, ticket)
        sessionFor(ticket)
        applyStatus(first)
        poll(session, ticket, first)
    }

    fun cancel() {
        cancelWanted = true
        if (state.ticket == null) return // Local preparation checks this before writing the ticket/POST.
        if (operation != null) {
            state = state.copy(error = "취소를 요청했어요. 현재 통신이 끝난 뒤 서버에 전달하며 실제 종료 확인까지 새 생성을 막아요.")
            return
        }
        launchExclusive {
            val ticket = state.ticket ?: return@launchExclusive
            val session = sessionFor(ticket)
            val status = gateway.cancel(session, ticket)
            sessionFor(ticket); applyStatus(status)
            poll(session, ticket, status)
        }
    }

    private fun applyStatus(status: OutfitServerStatus) {
        state = state.copy(status = status, phase = status.status,
            error = status.error?.let(ServerOutfitVtonRepository::friendly))
    }

    private suspend fun poll(session: OutfitServerSession, ticket: OutfitServerTicket, initial: OutfitServerStatus) {
        var status = initial
        val began = clock()
        var cancellationSent = false
        while (true) {
            coroutineContext.ensureActive(); sessionFor(ticket)
            if (cancelWanted && !cancellationSent && status.status !in ServerOutfitVtonRepository.terminal) {
                status = gateway.cancel(session, ticket); cancellationSent = true
                sessionFor(ticket); applyStatus(status)
            }
            if (status.status in ServerOutfitVtonRepository.terminal) {
                if (status.status == "completed" && status.resultAvailable && !cancelWanted) {
                    if (clock() >= status.expiresAt) throw OutfitServerFailure("result_expired")
                    val bytes = gateway.result(session, ticket, status)
                    coroutineContext.ensureActive(); sessionFor(ticket)
                    OnDeviceVtonImages.validateResultPng(bytes)
                    if (deviceSha256(bytes) != status.resultSha || original?.let(::deviceSha256) == status.resultSha)
                        throw OutfitServerFailure("result_binding_mismatch")
                    if (!cancelWanted) {
                        val previous = state.previous || selection?.let(ServerOutfitVtonRepository::selectionSha) != ticket.selectionSha
                        state = state.copy(result = OutfitServerImage(bytes, original), previous = previous)
                    }
                }
                return
            }
            if (clock() - began >= localWaitMillis) {
                state = state.copy(phase = "waiting", error = "앱의 대기 시간이 끝났어요. 서버 생성 취소를 뜻하지 않아요. 같은 작업 상태를 다시 조회해주세요.")
                return
            }
            delay(pollMillis)
            status = gateway.status(session, ticket); sessionFor(ticket); applyStatus(status)
        }
    }
}
