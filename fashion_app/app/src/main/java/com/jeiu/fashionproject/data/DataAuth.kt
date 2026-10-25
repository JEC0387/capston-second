package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** One private server account per installation. Old device IDs are never sent as credentials. */
object DataAuth {
    private const val PREFS = "data_auth"
    private val lock = Mutex()
    private lateinit var context: Context
    @Volatile private var token: String? = null
    @Volatile private var origin: String? = null
    @Volatile private var enabled = false
    @Volatile private var restorePending = false
    @Volatile private var legacyPending = false
    private val _state = MutableStateFlow("uninitialized")
    val state: StateFlow<String> = _state

    val accessToken: String? get() = token?.takeIf { origin == BackendApi.BASE_URL }
    val recoveryCode: String? get() = if (::context.isInitialized)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recovery", null) else null
    val needsLegacyDecision: Boolean get() = legacyPending && !enabled
    val canSync: Boolean get() = accessToken != null && enabled && !restorePending
    val isRestorePending: Boolean get() = restorePending
    val legacyDeviceId: String? get() = if (::context.isInitialized)
        context.getSharedPreferences("db_sync", Context.MODE_PRIVATE).getString("device_id", null) else null

    fun init(appContext: Context) {
        if (::context.isInitialized) return
        context = appContext.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        token = prefs.getString("token", null)
        origin = prefs.getString("origin", null)
        enabled = prefs.getBoolean("enabled", false)
        restorePending = prefs.getBoolean("restore_pending", false)
        legacyPending = prefs.getBoolean("legacy_pending", token == null && legacyDeviceId != null)
        _state.value = when {
            restorePending -> "restore_pending"
            token != null && origin != BackendApi.BASE_URL -> "server_mismatch"
            legacyPending && !enabled -> "legacy_decision_required"
            token != null && enabled -> "ready"
            else -> "waiting"
        }
    }

    private fun persist(access: String?, recovery: String?, dataEnabled: Boolean,
                        pending: Boolean, oldAccount: Boolean): Boolean {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", dataEnabled).putBoolean("restore_pending", pending)
            .putBoolean("legacy_pending", oldAccount)
        if (access == null) edit.remove("token") else edit.putString("token", access)
        if (access == null) edit.remove("origin") else edit.putString("origin", BackendApi.BASE_URL)
        if (recovery == null) edit.remove("recovery") else edit.putString("recovery", recovery)
        if (!edit.commit()) return false
        restorePending = pending
        legacyPending = oldAccount
        token = access
        origin = if (access == null) null else BackendApi.BASE_URL
        enabled = dataEnabled
        _state.value = when {
            pending -> "restore_pending"
            oldAccount && !dataEnabled -> "legacy_decision_required"
            access != null && dataEnabled -> "ready"
            else -> "waiting"
        }
        return true
    }

    private data class Reply(val code: Int, val body: String)

    /** Credentials are transmitted only to HTTPS endpoints; redirects are never followed. */
    private suspend fun call(path: String, body: JSONObject, bearer: String? = null): Reply? = withContext(Dispatchers.IO) {
        runCatching {
            val base = BackendApi.BASE_URL
            val uri = URI(base)
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.rawQuery != null || uri.rawFragment != null) return@runCatching null
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
                connectTimeout = 5_000; readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText().take(16_384) }.orEmpty()
                Reply(code, text)
            } finally { connection.disconnect() }
        }.getOrNull()
    }

    /** New installs register automatically when sync first needs the server. */
    suspend fun ensureReady(): Boolean = lock.withLock {
        if (!::context.isInitialized) return@withLock false
        if (token != null) {
            if (origin != BackendApi.BASE_URL) _state.value = "server_mismatch"
            return@withLock canSync
        }
        val response = call("/api/auth/register", JSONObject().put("legacyPending", legacyPending))
        if (response?.code != 201) { _state.value = "offline_or_unconfigured"; return@withLock false }
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return@withLock false
        val access = json.optString("accessToken")
        val recovery = json.optString("recoveryCode")
        if (access.length < 40 || recovery.length < 40) return@withLock false
        if (!persist(access, recovery, json.optBoolean("dataEnabled"), false, legacyPending)) return@withLock false
        canSync
    }

    /** Existing installations explicitly choose to upload local data into a fresh, empty account. */
    suspend fun startFresh(): Boolean {
        ensureReady()
        return lock.withLock {
            val access = accessToken ?: return@withLock false
            val reply = call("/api/auth/start-fresh", JSONObject(), access)
            if (reply?.code != 200) return@withLock false
            persist(access, recoveryCode, true, false, false)
        }
    }

    /** A recovery code rotates the server access token and pauses sync until restore completes. */
    suspend fun recover(code: String): Boolean = lock.withLock {
        if (!::context.isInitialized || !Regex("[A-Za-z0-9_-]{40,100}").matches(code)) return@withLock false
        val reply = call("/api/auth/recover", JSONObject().put("recoveryCode", code))
        if (reply?.code != 200) return@withLock false
        val json = runCatching { JSONObject(reply.body) }.getOrNull() ?: return@withLock false
        val access = json.optString("accessToken")
        if (access.length < 40) return@withLock false
        persist(access, code, json.optBoolean("dataEnabled"), true, false)
    }

    /** Only an operator-issued ticket can attach old server data; sync stays paused for restore. */
    suspend fun claimLegacy(ticket: String): Boolean {
        ensureReady()
        return lock.withLock {
            val access = accessToken ?: return@withLock false
            if (!legacyPending || !Regex("[A-Za-z0-9_-]{40,100}").matches(ticket)) return@withLock false
            val reply = call("/api/auth/claim-legacy", JSONObject().put("ticket", ticket), access)
            if (reply?.code != 200) return@withLock false
            persist(access, recoveryCode, true, true, false)
        }
    }

    fun completeRestore() {
        val access = accessToken
        if (::context.isInitialized && restorePending && access != null)
            persist(access, recoveryCode, enabled, false, false)
    }

    fun markUnauthorized() {
        if (!::context.isInitialized || accessToken == null) return
        enabled = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        _state.value = "reauth_required"
    }

    fun onServerChanged() {
        if (token != null && origin != BackendApi.BASE_URL) _state.value = "server_mismatch"
        else if (canSync) _state.value = "ready"
    }

    fun clearAfterServerDelete() {
        if (::context.isInitialized) persist(null, null, false, false, false)
    }
}
