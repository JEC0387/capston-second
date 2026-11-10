package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.BuildConfig

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

/** One immutable token/destination pair is swapped atomically after secure persistence. */
internal data class ServerBoundToken(val token: String, val server: String) {
    fun forDestination(currentServer: String, url: URL): String? = token.takeIf {
        server == currentServer && url.protocol == "https" && url.toString().startsWith(server + "/")
    }
}

/** One private server account per installation. Old device IDs are never sent as credentials. */
object DataAuth {
    private val lock = Mutex()
    private lateinit var context: Context
    private lateinit var vault: CredentialVault
    @Volatile private var storageLocked = false
    @Volatile private var recovery: String? = null
    @Volatile private var requestedRecovery: String? = null
    @Volatile private var requestedRecoveryServer: String? = null
    @Volatile private var binding: ServerBoundToken? = null
    private val token: String? get() = binding?.token
    private val origin: String? get() = binding?.server
    @Volatile private var enabled = false
    @Volatile private var restorePending = false
    @Volatile private var legacyPending = false
    private val _state = MutableStateFlow("uninitialized")
    val state: StateFlow<String> = _state

    val accessToken: String? get() = binding?.let { it.token.takeIf { _ -> it.server == BackendApi.BASE_URL } }

    /** Capture token and its destination atomically, including concurrent settings changes. */
    internal fun authorizationFor(url: URL): String? = binding?.let { saved ->
        if (storageLocked) null else saved.forDestination(BackendApi.BASE_URL, url)
    }
    val recoveryCode: String? get() = recovery
    val needsLegacyDecision: Boolean get() = legacyPending && !enabled
    val canSync: Boolean get() = !storageLocked && accessToken != null && enabled && !restorePending &&
        (!::context.isInitialized || !RestoreJournal.hasPending(context))
    val isRestorePending: Boolean get() = restorePending
    val legacyDeviceId: String? get() = if (::context.isInitialized)
        context.getSharedPreferences("db_sync", Context.MODE_PRIVATE).getString("device_id", null) else null

    fun init(appContext: Context) = initialize(appContext, CredentialVault(appContext))

    internal fun initialize(appContext: Context, storage: CredentialVault) {
        if (::context.isInitialized) return
        context = appContext.applicationContext
        vault = storage
        val saved = runCatching { vault.read() }.getOrElse {
            storageLocked = true
            _state.value = "secure_storage_unavailable"
            return
        }
        val access = saved.optString("token").takeIf { it.isNotEmpty() }
        recovery = saved.optString("recovery").takeIf { it.isNotEmpty() }
        requestedRecovery = saved.optString("requested_recovery").takeIf { it.isNotEmpty() }
        requestedRecoveryServer = saved.optString("requested_recovery_server").takeIf { it.isNotEmpty() }
        val savedOrigin = saved.optString("origin")
        if (access != null && savedOrigin.isBlank()) {
            storageLocked = true
            _state.value = "secure_storage_unavailable"
            return
        }
        binding = access?.let { ServerBoundToken(it, savedOrigin) }
        enabled = saved.optBoolean("enabled", false)
        restorePending = saved.optBoolean("restore_pending", false)
        legacyPending = saved.optBoolean("legacy_pending", token == null && legacyDeviceId != null)
        _state.value = when {
            restorePending -> "restore_pending"
            token != null && origin != BackendApi.BASE_URL -> "server_mismatch"
            legacyPending && !enabled -> "legacy_decision_required"
            token != null && enabled -> "ready"
            else -> "waiting"
        }
    }

    @Synchronized private fun persist(access: String?, recovery: String?, dataEnabled: Boolean,
                        pending: Boolean, oldAccount: Boolean, server: String = origin ?: BackendApi.BASE_URL,
                        requestedRecovery: String? = this.requestedRecovery,
                        requestedRecoveryServer: String? = this.requestedRecoveryServer): Boolean {
        val saved = JSONObject().put("enabled", dataEnabled).put("restore_pending", pending)
            .put("legacy_pending", oldAccount).put("token", access).put("recovery", recovery)
            .put("origin", if (access == null) null else server).put("requested_recovery", requestedRecovery)
            .put("requested_recovery_server", if (requestedRecovery == null) null else requestedRecoveryServer)
        if (!runCatching { vault.write(saved) }.getOrDefault(false)) {
            storageLocked = true
            _state.value = "secure_storage_unavailable"
            return false
        }
        storageLocked = false
        this.recovery = recovery
        this.requestedRecovery = requestedRecovery
        this.requestedRecoveryServer = if (requestedRecovery == null) null else requestedRecoveryServer
        restorePending = pending
        legacyPending = oldAccount
        binding = access?.let { ServerBoundToken(it, server) }
        enabled = dataEnabled
        _state.value = when {
            pending -> "restore_pending"
            oldAccount && !dataEnabled -> "legacy_decision_required"
            access != null && dataEnabled -> "ready"
            else -> "waiting"
        }
        return true
    }

    private data class Reply(val code: Int, val body: String, val server: String)

    /** Credentials are transmitted only to HTTPS endpoints; redirects are never followed. */
    private suspend fun call(path: String, body: JSONObject, bearer: String? = null, method: String = "POST", server: String = BackendApi.BASE_URL): Reply? = withContext(Dispatchers.IO) {
        if (BuildConfig.PREVIEW_BUILD) return@withContext null // No production registration, recovery or ownership changes.
        runCatching {
            val base = server
            val uri = URI(base)
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.rawQuery != null || uri.rawFragment != null) return@runCatching null
            val destination = URL(base + path)
            if (bearer != null && (base != BackendApi.BASE_URL || binding?.forDestination(base, destination) != bearer))
                return@runCatching null
            val connection = (destination.openConnection() as HttpURLConnection).apply {
                requestMethod = method; doOutput = method == "POST"; instanceFollowRedirects = false
                connectTimeout = 5_000; readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                if (method == "POST") {
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText().take(16_384) }.orEmpty()
                Reply(code, text, base)
            } finally { connection.disconnect() }
        }.getOrNull()
    }

    /** Install-test builds require explicit provisioning; background checks never create accounts. */
    suspend fun ensureReady(): Boolean = lock.withLock {
        if (!::context.isInitialized || storageLocked) return@withLock false
        if (token != null) {
            if (origin != BackendApi.BASE_URL) _state.value = "server_mismatch"
            return@withLock canSync
        }
        if (BuildConfig.INSTALL_TEST_BUILD) {
            _state.value = "registration_required"
            return@withLock false
        }
        val response = call("/api/auth/register", JSONObject().put("legacyPending", legacyPending))
        if (response?.code != 201 || response.server != BackendApi.BASE_URL) { _state.value = "offline_or_unconfigured"; return@withLock false }
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return@withLock false
        val access = json.optString("accessToken")
        val recovery = json.optString("recoveryCode")
        if (access.length < 40 || recovery.length < 40) return@withLock false
        if (!persist(access, recovery, json.optBoolean("dataEnabled"), false, legacyPending, response.server)) return@withLock false
        canSync
    }

    /** Startup/reconnect handshake; a cached token alone does not establish server access. */
    suspend fun validateSession(): Boolean {
        if (!ensureReady()) return false
        return lock.withLock {
            val access = accessToken ?: return@withLock false
            val reply = call("/api/auth/status", JSONObject(), access, "GET")
            if (reply?.server != BackendApi.BASE_URL) return@withLock false
            if (reply.code == 401) { markUnauthorized(access); return@withLock false }
            if (reply.code != 200) return@withLock false
            val dataEnabled = runCatching { JSONObject(reply.body).getBoolean("dataEnabled") }.getOrDefault(false)
            if (!dataEnabled) return@withLock false
            canSync
        }
    }

    /** Existing installations explicitly choose to upload local data into a fresh, empty account. */
    suspend fun startFresh(): Boolean = DbSync.exclusiveAccountChange {
        if (::context.isInitialized && RestoreJournal.hasPending(context)) return@exclusiveAccountChange false
        ensureReady()
        lock.withLock {
            val access = accessToken ?: return@withLock false
            val reply = call("/api/auth/start-fresh", JSONObject(), access)
            if (reply?.code != 200 || reply.server != BackendApi.BASE_URL) return@withLock false
            persist(access, recoveryCode, true, false, false, requestedRecovery = null)
        }
    }

    /** A recovery code rotates the server access token and pauses sync until restore completes. */
    suspend fun recover(code: String): Boolean = lock.withLock {
        if (!::context.isInitialized || !Regex("[A-Za-z0-9_-]{40,100}").matches(code)) return@withLock false
        // Persist the intended account/server before rotation, including lost responses/process death.
        val destination = BackendApi.BASE_URL
        if (!persist(token, recoveryCode, enabled, true, legacyPending, requestedRecovery = code,
                requestedRecoveryServer = destination)) return@withLock false
        val reply = call("/api/auth/recover", JSONObject().put("recoveryCode", code), server = destination)
        if (reply?.code != 200 || reply.server != BackendApi.BASE_URL) return@withLock false
        val json = runCatching { JSONObject(reply.body) }.getOrNull() ?: return@withLock false
        val access = json.optString("accessToken")
        if (access.length < 40) return@withLock false
        persist(access, code, json.optBoolean("dataEnabled"), true, false, reply.server, requestedRecovery = null)
    }

    /** Only an operator-issued ticket can attach old server data; sync stays paused for restore. */
    suspend fun claimLegacy(ticket: String): Boolean = DbSync.exclusiveAccountChange {
        ensureReady()
        lock.withLock {
            val access = accessToken ?: return@withLock false
            if (!legacyPending || !Regex("[A-Za-z0-9_-]{40,100}").matches(ticket)) return@withLock false
            if (!persist(access, recoveryCode, false, true, true, requestedRecovery = null)) return@withLock false
            val reply = call("/api/auth/claim-legacy", JSONObject().put("ticket", ticket), access)
            if (reply?.code != 200 || reply.server != BackendApi.BASE_URL) return@withLock false
            persist(access, recoveryCode, true, true, false)
        }
    }

    /** Server answer to a social login call (Google · Kakao). The body never contains this device's token. */
    data class SocialReply(val code: Int, val body: String)

    /**
     * Google · Kakao login with this installation's Bearer account (never a device ID).
     * When the identity belongs to another account the server rotates that account's token and returns it:
     * it is stored with restore pending, so sync stays paused until that account's snapshot is installed
     * (BackupRestore.restoreClaimed → app restart, the same path as recovery).
     * @return null when offline, unauthenticated or the token could not be stored safely
     */
    suspend fun socialLogin(path: String, body: JSONObject): SocialReply? = DbSync.exclusiveAccountChange {
        if (!::context.isInitialized || RestoreJournal.hasPending(context)) return@exclusiveAccountChange null
        ensureReady()
        lock.withLock {
            val access = accessToken ?: return@withLock null
            val reply = call(path, body, access) ?: return@withLock null
            if (reply.server != BackendApi.BASE_URL) return@withLock null
            if (reply.code == 401) {
                // A rejected/expired social identity is independent of this existing
                // authenticated account. Only the server's explicit Bearer rejection
                // disables synchronization; arbitrary remote diagnostics stay private.
                val failureCode = runCatching {
                    val detail = JSONObject(reply.body).opt("detail")
                    when (detail) {
                        is String -> detail
                        is JSONObject -> detail.optString("code")
                        else -> null
                    }
                }.getOrNull()
                when (failureCode) {
                    "authentication_required" -> markUnauthorized(access)
                    "invalid_identity", "login_expired" -> Unit
                    else -> {
                        // Some HttpURLConnection implementations omit a streaming POST's
                        // 401 body. Do not replay that POST or guess which credential failed.
                        // One read-only status probe uses the original HTTPS origin/token.
                        val status = call("/api/auth/status", JSONObject(), access, method = "GET", server = reply.server)
                        if (status?.code == 401 && status.server == reply.server && reply.server == BackendApi.BASE_URL)
                            markUnauthorized(access)
                    }
                }
            }
            if (reply.code == 200) {
                val json = runCatching { JSONObject(reply.body) }.getOrNull() ?: return@withLock null
                if (json.optBoolean("switched")) {
                    val rotated = json.optString("accessToken")
                    if (!Regex("[A-Za-z0-9_-]{40,100}").matches(rotated)) return@withLock null
                    // The other account's recovery code is not known here; Google/Kakao login is its recovery.
                    if (!persist(rotated, null, true, true, false, reply.server, requestedRecovery = null)) return@withLock null
                }
                // Secrets stay inside DataAuth; callers only see account metadata.
                json.remove("accessToken"); json.remove("recoveryCode")
                return@withLock SocialReply(reply.code, json.toString())
            }
            SocialReply(reply.code, reply.body)
        }
    }

    suspend fun resumePendingAuthentication(): Boolean {
        val code = requestedRecovery
        if (code != null && requestedRecoveryServer != BackendApi.BASE_URL) {
            _state.value = "server_mismatch"
            return false
        }
        return if (code != null) recover(code) else reconcileClaim()
    }

    /** Reconcile a successful server claim whose response was lost before local persistence. */
    suspend fun reconcileClaim(): Boolean = lock.withLock {
        if (!restorePending || !legacyPending) return@withLock true
        val access = accessToken ?: return@withLock false
        val reply = call("/api/auth/status", JSONObject(), access, "GET")
        if (reply?.code != 200 || reply.server != BackendApi.BASE_URL) return@withLock false
        val ready = runCatching { JSONObject(reply.body).getBoolean("dataEnabled") }.getOrDefault(false)
        ready && persist(access, recoveryCode, true, true, false)
    }

    fun completeRestore(): Boolean {
        val access = accessToken
        return ::context.isInitialized && enabled && access != null &&
            persist(access, recoveryCode, enabled, false, false, requestedRecovery = null)
    }

    @Synchronized fun markUnauthorized(sentToken: String) {
        if (!::context.isInitialized || accessToken != sentToken) return
        enabled = false
        persist(token, recoveryCode, false, restorePending, legacyPending)
        _state.value = "reauth_required"
    }

    fun onServerChanged() {
        if (token != null && origin != BackendApi.BASE_URL) _state.value = "server_mismatch"
        else if (canSync) _state.value = "ready"
    }

    fun clearAfterServerDelete() {
        if (::context.isInitialized) persist(null, null, false, false, false, requestedRecovery = null)
    }
}
