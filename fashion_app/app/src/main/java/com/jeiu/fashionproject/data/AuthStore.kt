package com.jeiu.fashionproject.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

/**
 * Login chooser — Google · Kakao on top of DataAuth's Bearer account (Facebook · Apple stay off).
 *
 *  - Every call carries this installation's Bearer token (DataAuth.socialLogin); a device ID is never sent.
 *  - First login of an identity links it to the current account: nothing moves, records stay.
 *  - Identity already linked to another account: the server rotates that account's token, DataAuth stores
 *    it with restore pending, and the snapshot is installed through the existing restore path
 *    (BackupRestore.restoreClaimed → restart). The previous account's server rows are untouched.
 *  - Kakao opens the browser; the one-time code is bound to the starting account on the server.
 *  - No login, cancel or logout path deletes records. Logout only returns to this chooser.
 */
object AuthStore {
    enum class Mode { NONE, GUEST, SIGNED_IN }
    enum class Provider(val key: String, val label: String) {
        GOOGLE("google", "Google"), KAKAO("kakao", "카카오"), FACEBOOK("facebook", "Facebook"), APPLE("apple", "Apple");
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
    }
    /** Login buttons shown in the app */
    val ENABLED_PROVIDERS = listOf(Provider.KAKAO, Provider.GOOGLE)

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
    /** Marks a SIGNED_IN state written by the Bearer bridge (older SIGNED_IN values are not trusted). */
    private const val KEY_BEARER = "bearer_v2"
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
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString("mode", null)
        if (saved == Mode.SIGNED_IN.name && p.getBoolean(KEY_BEARER, false)) {
            _mode.value = Mode.SIGNED_IN
            _account.value = Provider.of(p.getString("provider", null))?.let {
                Account(p.getString("email", "") ?: "", p.getString("name", "") ?: "", it)
            }
            return
        }
        // A legacy SIGNED_IN preference is not proof of a valid Bearer session.
        _mode.value = if (saved in setOf(Mode.GUEST.name, Mode.SIGNED_IN.name, "GOOGLE")) Mode.GUEST else Mode.NONE
        _account.value = null
    }

    private fun saveLocalMode(context: Context, mode: Mode): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name).remove("email").remove("name").remove("provider").remove(KEY_BEARER).commit()
        if (saved) { _mode.value = mode; _account.value = null; _redirect.value = null }
        return saved
    }

    private fun saveSignedIn(context: Context, account: Account): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", Mode.SIGNED_IN.name).putString("email", account.email).putString("name", account.name)
            .putString("provider", account.provider.key).putBoolean(KEY_BEARER, true).commit()
        if (saved) { _mode.value = Mode.SIGNED_IN; _account.value = account }
        return saved
    }

    /** Changes only chooser state; no sync, restore or account adoption. */
    fun continueAsGuest(context: Context): Boolean = saveLocalMode(context, Mode.GUEST)
    /** Records, pending sync and DataAuth credentials remain intact. The return
     * value means local persistence, NOT successful server synchronization. */
    suspend fun signOut(context: Context): Boolean = saveLocalMode(context, Mode.NONE)

    fun isGoogleConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Server switches (google · kakao). Unknown or offline → everything off. Facebook · Apple are always off. */
    suspend fun serverProviders(): Map<String, Boolean> {
        val off = Provider.entries.associate { it.key to false }
        val body = BackendApi.getJson("/api/auth/providers") ?: return off
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return off
        return Provider.entries.associate { it.key to (it in ENABLED_PROVIDERS && json.optBoolean(it.key, false)) }
    }

    // ── Google ──────────────────────────────────────────────────────────

    suspend fun signInWithGoogle(activityContext: Context): Result {
        if (!isGoogleConfigured()) return Result.Failed(UNAVAILABLE)
        if (serverProviders()[Provider.GOOGLE.key] != true) return Result.Failed(UNAVAILABLE)
        val idToken = try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            else return Result.Failed("구글 계정 정보를 받지 못했어요. 기존 기록은 유지돼요.")
        } catch (_: GetCredentialCancellationException) {
            return Result.Cancelled
        } catch (_: NoCredentialException) {
            return Result.Failed("이 폰에 로그인할 구글 계정이 없어요. 휴대폰 설정에서 구글 계정을 추가해 주세요.")
        } catch (_: GetCredentialException) {
            return Result.Failed("구글 로그인 창을 열지 못했어요. 기존 기록은 유지돼요.")
        } catch (_: Exception) {
            return Result.Failed("구글 로그인 중 문제가 생겼어요. 기존 기록은 유지돼요.")
        }
        val reply = DataAuth.socialLogin("/api/auth/google", JSONObject().put("idToken", idToken))
            ?: return Result.Failed(messageFor(-1, null))
        return complete(activityContext.applicationContext, reply, Provider.GOOGLE)
    }

    // ── Kakao (browser) ─────────────────────────────────────────────────

    suspend fun startBrowserLogin(activityContext: Context, provider: Provider): Result {
        if (provider != Provider.KAKAO) return Result.Failed(UNAVAILABLE)
        if (serverProviders()[provider.key] != true) return Result.Failed(UNAVAILABLE)
        val reply = DataAuth.socialLogin("/api/auth/start", JSONObject().put("provider", provider.key))
            ?: return Result.Failed(messageFor(-1, null))
        if (reply.code != 200) return Result.Failed(messageFor(reply.code, reply.body))
        val url = runCatching { JSONObject(reply.body).getString("url") }.getOrNull()
            ?.takeIf { it.startsWith("https://kauth.kakao.com/") } ?: return Result.Failed(UNAVAILABLE)
        return try {
            activityContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            Result.OpenedBrowser
        } catch (_: ActivityNotFoundException) {
            Result.Failed("로그인 화면을 열 브라우저가 없어요.")
        }
    }

    fun handleRedirect(uri: Uri?) {
        if (uri != null && uri.toString().length <= 4096 && uri.scheme == REDIRECT_SCHEME &&
            uri.host == REDIRECT_HOST && uri.userInfo == null && uri.port == -1 &&
            uri.path.isNullOrEmpty() && uri.fragment == null) _redirect.value = uri
    }
    fun consumeRedirect(): Uri? = _redirect.value.also { _redirect.value = null }

    suspend fun finishBrowserLogin(context: Context, uri: Uri): Result {
        if (uri.toString().length > 4096 || uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST || uri.userInfo != null ||
            uri.port != -1 || !uri.path.isNullOrEmpty() || uri.fragment != null) return Result.Failed(UNAVAILABLE)
        if (uri.getQueryParameter("error") == "cancelled") return Result.Cancelled
        if (Provider.of(uri.getQueryParameter("provider")) != Provider.KAKAO) return Result.Failed(UNAVAILABLE)
        val code = uri.getQueryParameter("code")?.takeIf { Regex("[A-Za-z0-9_-]{20,64}").matches(it) }
            ?: return Result.Failed("로그인하지 못했어요. 다시 시도해 주세요. 기존 기록은 유지돼요.")
        val reply = DataAuth.socialLogin("/api/auth/exchange", JSONObject().put("code", code))
            ?: return Result.Failed(messageFor(-1, null))
        return complete(context.applicationContext, reply, Provider.KAKAO)
    }

    // ── Common ──────────────────────────────────────────────────────────

    /** Linked to this account → done. Switched to the identity's account → install its records (restore path). */
    private suspend fun complete(appContext: Context, reply: DataAuth.SocialReply, provider: Provider): Result {
        if (reply.code != 200) return Result.Failed(messageFor(reply.code, reply.body))
        val json = runCatching { JSONObject(reply.body) }.getOrNull() ?: return Result.Failed(messageFor(0, null))
        val account = Account(json.optString("email"), json.optString("name"), provider)
        val isNew = json.optBoolean("isNewAccount")
        if (!json.optBoolean("switched")) {
            saveSignedIn(appContext, account)
            return Result.Success(account, restart = false, isNewAccount = isNew)
        }
        // The token now belongs to the identity's account and sync is paused until restore completes.
        saveSignedIn(appContext, account)
        return when (BackupRestore.restoreClaimed(appContext)) {
            is BackupRestore.Result.Done, BackupRestore.Result.RestartRequired ->
                Result.Success(account, restart = true, isNewAccount = false)
            BackupRestore.Result.NotFound -> {
                // That account has no server records yet: keep this phone's records and resume sync.
                DataAuth.completeRestore()
                Result.Success(account, restart = false, isNewAccount = isNew)
            }
            else -> Result.Failed("로그인은 됐지만 계정 기록을 받아 오지 못했어요. 마이페이지 > 백업에서 '복원 이어하기'를 눌러 주세요.")
        }
    }

    fun parseLogin(body: String): LoginInfo? = runCatching {
        val j = JSONObject(body)
        val key = j.getString("accountKey")
        require(UUID.fromString(key).toString() == key)
        LoginInfo(key, j.optString("email"), j.optString("name"), j.optBoolean("isNewAccount"),
            j.optBoolean("switched"), Provider.of(j.optString("provider", "google")) ?: Provider.GOOGLE)
    }.getOrNull()

    /** Fixed messages only — remote text is never displayed (known error codes map to fixed sentences). */
    fun messageFor(code: Int, body: String?): String {
        val known = body?.let { runCatching { JSONObject(it).optJSONObject("detail")?.optString("code") }.getOrNull() }
        return when {
            known == "provider_already_linked" -> "이 기기 계정은 이미 다른 계정으로 로그인돼 있어요. 기존 기록은 유지돼요."
            known == "social_account_requires_review" -> "예전 방식으로 연결된 계정이라 운영자 확인이 필요해요. 기존 기록은 유지돼요."
            known == "legacy_decision_required" -> "마이페이지 > 백업에서 기존 기록 선택을 먼저 마쳐 주세요."
            known == "login_expired" -> "로그인 시간이 지났어요. 다시 로그인해 주세요."
            code == -1 -> "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
            code == 401 -> "로그인 정보를 확인하지 못했어요. 기존 기록은 유지돼요."
            code == 503 -> "서버의 로그인 설정이 아직 준비되지 않았어요."
            code == 502 -> "로그인 확인 서버에 연결하지 못했어요. 기존 기록은 유지돼요."
            else -> "로그인하지 못했어요. 기존 기록은 유지돼요."
        }
    }
}
