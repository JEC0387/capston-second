package com.jeiu.fashionproject.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    CHECKING, OFFLINE, DATABASE_UNAVAILABLE, UPDATE_REQUIRED, BACKUP_DISABLED,
    AUTH_REQUIRED, LEGACY_REQUIRED, RESTORE_PENDING, STORAGE_UNAVAILABLE, SERVER_CHANGED, READY
}

/** One attempt has no writes to local stores, queues, settings, or database configuration. */
internal class ConnectionAttempt(
    private val probe: suspend () -> ServerSettings.Check,
    private val authenticate: suspend () -> Boolean,
    private val authState: () -> String,
    private val backupEnabled: () -> Boolean,
    private val sync: () -> Unit
) {
    suspend fun run(): ConnectionState {
        if (!backupEnabled()) return ConnectionState.BACKUP_DISABLED
        when (authState()) {
            "restore_pending" -> return ConnectionState.RESTORE_PENDING
            "secure_storage_unavailable" -> return ConnectionState.STORAGE_UNAVAILABLE
            "server_mismatch" -> return ConnectionState.SERVER_CHANGED
        }
        val health = probe()
        if (!health.reachable) return ConnectionState.OFFLINE
        if (!health.dbReady) return ConnectionState.DATABASE_UNAVAILABLE
        if (!health.syncReady || health.authProtocol != 2) return ConnectionState.UPDATE_REQUIRED
        if (!authenticate()) return when (authState()) {
            "legacy_decision_required" -> ConnectionState.LEGACY_REQUIRED
            "restore_pending" -> ConnectionState.RESTORE_PENDING
            "secure_storage_unavailable" -> ConnectionState.STORAGE_UNAVAILABLE
            "server_mismatch" -> ConnectionState.SERVER_CHANGED
            else -> ConnectionState.AUTH_REQUIRED
        }
        sync()
        return ConnectionState.READY
    }
}

/** Start with the bundled Railway address, reconnect without blocking any screen. */
object ServerConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val _state = MutableStateFlow(ConnectionState.CHECKING)
    val state: StateFlow<ConnectionState> = _state
    private var started = false

    internal fun retryDelay(failures: Int): Long = (5_000L shl failures.coerceIn(0, 6)).coerceAtMost(300_000L)

    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        // Periodic retries remain available on devices without a network callback.
        runCatching {
            val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = retry()
                override fun onLost(network: Network) = retry()
            })
        }
        scope.launch {
            var failures = 0
            while (isActive) {
                val server = BackendApi.BASE_URL
                val attempt = ConnectionAttempt(
                    probe = { ServerSettings.check(server) },
                    authenticate = { server == BackendApi.BASE_URL && DataAuth.validateSession() },
                    authState = { DataAuth.state.value }, backupEnabled = { DbSync.isEnabled }, sync = { DbSync.retry() })
                val result = try { attempt.run() } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { ConnectionState.OFFLINE }
                if (server != BackendApi.BASE_URL) continue
                _state.value = result
                val wait = if (result == ConnectionState.READY) {
                    failures = 0; 60_000L
                } else retryDelay(failures++)
                withTimeoutOrNull(wait) { wakeup.receive() }?.let { failures = 0 }
            }
        }
    }

    fun retry() { wakeup.trySend(Unit) }
}
