package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

/** Fresh test identities only. No token import, DataAuth mutation, restore, or DbSync enablement. */
internal class TestAccountConnection(
    private val read: () -> JSONObject,
    private val write: (JSONObject) -> Boolean,
    private val currentServer: () -> String,
    private val allowed: () -> Boolean,
    private val exchange: suspend (String, String, String?) -> SafeAccountHttp.Reply
) {
    private val lock = Mutex()
    @Volatile private var saved = JSONObject()
    @Volatile private var verified: OutfitServerSession? = null
    private val _state = MutableStateFlow("uninitialized")
    val state: StateFlow<String> = _state
    fun load() {
        try { saved = read(); _state.value = if (saved.has("token")) "verification_required" else if(saved.has("attempt")) "outcome_unknown" else "approval_required" }
        catch (_: Exception) { _state.value = "storage_unavailable" }
        verified = null
    }
    fun session(): OutfitServerSession? = verified?.takeIf { allowed() && it.base == currentServer() }
    val hasRecord: Boolean get() = saved.length() > 0 || _state.value == "storage_unavailable"

    suspend fun enroll(approved: Boolean, approvalReference: String, expectedServer: String): Boolean = lock.withLock {
        if (!allowed() || !approved || !Regex("[A-Za-z0-9._-]{3,80}").matches(approvalReference) ||
            expectedServer != currentServer() || !ServerOutfitVtonRepository.validBase(expectedServer) ||
            hasRecord || _state.value != "approval_required") return@withLock false
        // Journal BEFORE POST. A lost/cancelled reply cannot authorize another registration.
        saved = JSONObject().put("attempt", approvalReference).put("origin", expectedServer)
        if (!write(saved)) { _state.value = "storage_unavailable"; return@withLock false }
        _state.value = "connecting"
        try {
            val reply = exchange(expectedServer, "register", null)
            if (reply.code != 201) { _state.value = if(reply.code == 503) "registration_unavailable" else "outcome_unknown"; return@withLock false }
            val json = JSONObject(reply.body)
            val token = json.optString("accessToken")
            val recovery = json.optString("recoveryCode")
            if (!Regex("[A-Za-z0-9_-]{40,100}").matches(token) ||
                !Regex("[A-Za-z0-9_-]{40,100}").matches(recovery) || json.opt("dataEnabled") != true) {
                _state.value = "outcome_unknown"; return@withLock false
            }
            saved.put("token", token).put("recovery", recovery)
            if (!write(saved)) { _state.value = "storage_unavailable"; return@withLock false }
            _state.value = "verification_required"
            verifyLocked()
        } catch (cancelled: CancellationException) { _state.value = "outcome_unknown"; throw cancelled }
        catch (_: Exception) { _state.value = "outcome_unknown"; false }
    }
    suspend fun verify(): Boolean = lock.withLock { verifyLocked() }
    private suspend fun verifyLocked(): Boolean {
        verified = null
        if (!allowed() || _state.value == "storage_unavailable") return false
        val base = saved.optString("origin")
        val token = saved.optString("token")
        if (base != currentServer() || !ServerOutfitVtonRepository.validBase(base)) { _state.value = "server_mismatch"; return false }
        if (!Regex("[A-Za-z0-9_-]{40,100}").matches(token)) { _state.value = "outcome_unknown"; return false }
        _state.value = "checking"
        return try {
            val reply = exchange(base, "status", token)
            if (base != currentServer()) { _state.value = "server_mismatch"; return false }
            val json = if (reply.code == 200) JSONObject(reply.body) else JSONObject()
            if (json.opt("authenticated") != true || json.opt("dataEnabled") != true) {
                _state.value = if(reply.code == 401) "authentication_required" else "verification_failed"; false
            } else {
                verified = OutfitServerSession(base, token); _state.value = "ready"; true
            }
        } catch (cancelled: CancellationException) { _state.value = "verification_required"; throw cancelled }
        catch (_: Exception) { _state.value = "verification_failed"; false }
    }
}

object InstallTestAccount {
    @Volatile private var connection: TestAccountConnection? = null
    private val empty = MutableStateFlow("uninitialized")
    val state: StateFlow<String> get() = connection?.state ?: empty
    val hasRecord: Boolean get() = connection?.hasRecord == true
    fun sessionOrNull(): OutfitServerSession? = connection?.session()
    @Synchronized fun init(context: Context) {
        if (!BuildConfig.INSTALL_TEST_BUILD || connection != null) return
        val vault = CredentialVault(context.applicationContext, prefsName = "installtest_account")
        val client = SafeAccountHttp.client(25)
        connection = TestAccountConnection(vault::read, { runCatching { vault.write(it) }.getOrDefault(false) },
            { BackendApi.BASE_URL }, { BuildConfig.INSTALL_TEST_BUILD && BuildConfig.SERVER_OUTFIT_APPROVED }) { base, action, token ->
            val builder = Request.Builder().url("$base/api/auth/$action")
            if (action == "register") builder.post("{\"legacyPending\":false}".toRequestBody("application/json".toMediaType()))
            else builder.get().header("Authorization", "Bearer $token")
            SafeAccountHttp.send(client, builder.build())
        }.also { it.load() }
    }
    suspend fun enroll(approved: Boolean, reference: String, expectedServer: String) =
        connection?.enroll(approved, reference, expectedServer) == true
    suspend fun verify() = connection?.verify() == true
}
