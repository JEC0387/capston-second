package com.jeiu.fashionproject.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

/** Team login chooser, separate from DataAuth's authenticated backup session.
 * The legacy OAuth response supplies a UUID, not a protocol-2 Bearer credential.
 * Until a verified identity bridge exists providers remain unavailable. No login,
 * cancel or logout path deletes records, adopts a different DbSync owner or claims
 * that an empty server account has restored data. Deletion stays separately explicit.
 */
@Suppress("UNUSED_PARAMETER")
object AuthStore {
    enum class Mode { NONE, GUEST, SIGNED_IN }
    enum class Provider(val key: String, val label: String) {
        GOOGLE("google", "Google"), KAKAO("kakao", "카카오"), FACEBOOK("facebook", "Facebook"), APPLE("apple", "Apple");
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
    }
    data class Account(val email: String, val name: String, val provider: Provider)
    /** Legacy response metadata only; never accepted as authentication. */
    data class LoginInfo(val accountKey: String, val email: String, val name: String,
                         val isNewAccount: Boolean, val switched: Boolean, val provider: Provider)
    sealed class Result {
        data class Success(val account: Account, val restart: Boolean, val isNewAccount: Boolean) : Result()
        object Cancelled : Result()
        object OpenedBrowser : Result()
        data class Failed(val message: String) : Result()
    }
    private const val PREFS = "auth"
    const val REDIRECT_SCHEME = "fashionai"
    const val REDIRECT_HOST = "login"
    const val UNAVAILABLE = "소셜 로그인과 안전한 계정 연결이 아직 설정되지 않았어요. 기존 기록은 유지하며 로컬 게스트로 이용할 수 있어요."
    private val _mode = MutableStateFlow(Mode.NONE)
    val mode: StateFlow<Mode> = _mode
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account
    private val _redirect = MutableStateFlow<Uri?>(null)
    val redirect: StateFlow<Uri?> = _redirect

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
        // A legacy SIGNED_IN preference is not proof of a valid Bearer session.
        _mode.value = if (saved in setOf(Mode.GUEST.name, Mode.SIGNED_IN.name, "GOOGLE")) Mode.GUEST else Mode.NONE
        _account.value = null
    }

    private fun saveLocalMode(context: Context, mode: Mode): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name).remove("email").remove("name").remove("provider").commit()
        if (saved) { _mode.value = mode; _account.value = null; _redirect.value = null }
        return saved
    }
    /** Changes only chooser state; no sync, restore or account adoption. */
    fun continueAsGuest(context: Context): Boolean = saveLocalMode(context, Mode.GUEST)
    /** Records, pending sync and DataAuth credentials remain intact. The return
     * value means local persistence, NOT successful server synchronization. */
    suspend fun signOut(context: Context): Boolean = saveLocalMode(context, Mode.NONE)

    fun isGoogleConfigured(): Boolean = false
    suspend fun serverProviders(): Map<String, Boolean> = Provider.entries.associate { it.key to false }
    suspend fun signInWithGoogle(activityContext: Context): Result = Result.Failed(UNAVAILABLE)
    suspend fun startBrowserLogin(activityContext: Context, provider: Provider): Result = Result.Failed(UNAVAILABLE)

    fun handleRedirect(uri: Uri?) {
        if (uri != null && uri.toString().length <= 4096 && uri.scheme == REDIRECT_SCHEME &&
            uri.host == REDIRECT_HOST && uri.userInfo == null && uri.port == -1 &&
            uri.path.isNullOrEmpty() && uri.fragment == null) _redirect.value = uri
    }
    fun consumeRedirect(): Uri? = _redirect.value.also { _redirect.value = null }
    suspend fun finishBrowserLogin(context: Context, uri: Uri): Result {
        if (uri.toString().length > 4096 || uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST || uri.userInfo != null ||
            uri.port != -1 || !uri.path.isNullOrEmpty() || uri.fragment != null) return Result.Failed(UNAVAILABLE)
        return if (uri.getQueryParameter("error") == "cancelled") Result.Cancelled else Result.Failed(UNAVAILABLE)
    }
    fun parseLogin(body: String): LoginInfo? = runCatching {
        val j = JSONObject(body)
        val key = j.getString("accountKey")
        require(UUID.fromString(key).toString() == key)
        LoginInfo(key, j.optString("email"), j.optString("name"), j.optBoolean("isNewAccount"),
            j.optBoolean("switched"), Provider.of(j.optString("provider", "google")) ?: Provider.GOOGLE)
    }.getOrNull()

    /** Never display arbitrary remote diagnostics or credentials. */
    fun messageFor(code: Int, body: String?): String = when (code) {
        -1 -> "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
        401 -> "로그인 정보를 확인하지 못했어요. 기존 기록은 유지돼요."
        503 -> "서버의 로그인 설정이 아직 준비되지 않았어요."
        502 -> "로그인 확인 서버에 연결하지 못했어요. 기존 기록은 유지돼요."
        else -> "로그인하지 못했어요. 기존 기록은 유지돼요."
    }
}
