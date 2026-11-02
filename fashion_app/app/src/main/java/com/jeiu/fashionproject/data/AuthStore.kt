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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

/**
 * 로그인 상태 — 구글 · 카카오 · 페이스북 · Apple 계정 로그인 또는 로그인 없이(게스트).
 *
 *  - 구글: 폰의 구글 계정 선택 창(Credential Manager) → ID 토큰 → 서버 /api/auth/google 이 구글에 확인
 *  - 카카오 · 페이스북 · Apple: 서버가 만든 로그인 주소를 브라우저로 열기 → 각 회사 로그인 →
 *    서버가 사용자 확인 → fashionai://login?code=… 로 앱이 다시 열림 → /api/auth/exchange 로 계정 번호 받기
 *    (앱에 각 회사 SDK · 비밀 값을 넣지 않는다)
 * 어느 방법이든 서버는 그 계정의 번호(accountKey)를 돌려준다.
 *  - 처음 로그인한 계정: 지금 폰의 기록을 그대로 이 계정으로 이어 받는다
 *  - 다른 폰에서 쓰던 계정: 이 폰을 그 계정 번호로 바꾸고 서버의 옷장 · 착용 · 취향 기록을 받아 온다
 * 로그아웃하면 이 폰의 기록을 지우고(계정 기록은 서버에 남음) 새 게스트로 다시 시작한다.
 */
object AuthStore {

    enum class Mode { NONE, GUEST, SIGNED_IN }

    enum class Provider(val key: String, val label: String) {
        GOOGLE("google", "Google"), KAKAO("kakao", "카카오"), FACEBOOK("facebook", "Facebook"), APPLE("apple", "Apple");
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
    }

    data class Account(val email: String, val name: String, val provider: Provider)

    /** 서버 응답 (/api/auth/google · /api/auth/exchange) */
    data class LoginInfo(
        val accountKey: String, val email: String, val name: String,
        val isNewAccount: Boolean, val switched: Boolean, val provider: Provider
    )

    sealed class Result {
        /** @param restart true 면 받아 온 계정 기록을 불러오도록 앱을 다시 시작해야 함 */
        data class Success(val account: Account, val restart: Boolean, val isNewAccount: Boolean) : Result()
        object Cancelled : Result()
        /** 브라우저를 열었음 — 로그인을 마치면 앱으로 돌아온다 */
        object OpenedBrowser : Result()
        data class Failed(val message: String) : Result()
    }

    private const val PREFS = "auth"
    /** 로그아웃해도 지우지 않는 저장소 — 서버 주소 설정 */
    private val KEEP_ON_SIGN_OUT = setOf("server_settings")
    /** 로그인 뒤 앱으로 돌아오는 주소 (AndroidManifest 의 intent-filter 와 같아야 함) */
    const val REDIRECT_SCHEME = "fashionai"
    const val REDIRECT_HOST = "login"

    private val _mode = MutableStateFlow(Mode.NONE)
    val mode: StateFlow<Mode> = _mode
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account
    @Volatile private var loaded = false

    /** 브라우저 로그인 뒤 앱으로 돌아온 주소 (fashionai://login?code=… / ?error=…) — 로그인 화면이 처리한다 */
    private val _redirect = MutableStateFlow<Uri?>(null)
    val redirect: StateFlow<Uri?> = _redirect

    /** 구글 로그인 설정(웹 클라이언트 ID)이 앱에 들어 있는지 */
    fun isGoogleConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = when (p.getString("mode", null)) {
            Mode.GUEST.name -> Mode.GUEST
            Mode.SIGNED_IN.name, "GOOGLE" -> Mode.SIGNED_IN
            else -> Mode.NONE
        }
        _account.value = p.getString("email", null)?.let {
            Account(it, p.getString("name", "") ?: "", Provider.of(p.getString("provider", null)) ?: Provider.GOOGLE)
        }
        loaded = true
    }

    private fun save(context: Context, mode: Mode, account: Account?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name)
            .putString("email", account?.email)
            .putString("name", account?.name)
            .putString("provider", account?.provider?.key)
            .commit()
        _mode.value = mode
        _account.value = account
        loaded = true
    }

    /** 로그인 없이 시작 — 기록은 이 폰(과 기기 번호 백업)에만 저장된다 */
    fun continueAsGuest(context: Context) = save(context, Mode.GUEST, null)

    /** 서버 응답 읽기 (단위 테스트용으로 따로) */
    fun parseLogin(body: String): LoginInfo? = runCatching {
        val j = JSONObject(body)
        LoginInfo(j.getString("accountKey"), j.optString("email"), j.optString("name"),
            j.optBoolean("isNewAccount"), j.optBoolean("switched"),
            Provider.of(j.optString("provider", "google")) ?: Provider.GOOGLE)
    }.getOrNull()?.takeIf { BackupRestore.normalize(it.accountKey) != null }

    /** 서버 오류 → 사용자에게 보여줄 말 */
    fun messageFor(code: Int, body: String?): String {
        val detail = body?.let { runCatching { JSONObject(it).optString("detail") }.getOrNull() }.orEmpty()
        return when (code) {
            -1 -> "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
            401 -> detail.ifBlank { "로그인 정보를 확인하지 못했어요. 다시 시도해 주세요." }
            503 -> detail.ifBlank { "서버의 로그인 설정이 아직 준비되지 않았어요." }
            502 -> "로그인 확인 서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요."
            else -> "로그인하지 못했어요. (오류 $code)"
        }
    }

    /** 서버에 설정된 로그인 방법 (google · kakao · facebook · apple → 켜짐). 서버에 연결되지 않으면 null */
    suspend fun serverProviders(): Map<String, Boolean>? {
        val body = BackendApi.getJson("/api/auth/providers") ?: return null
        return runCatching {
            val j = JSONObject(body)
            Provider.entries.associate { it.key to j.optBoolean(it.key) }
        }.getOrNull()
    }

    // ── 구글 ────────────────────────────────────────────────────────────

    /** 구글 계정으로 로그인. [activityContext]는 계정 선택 창을 띄울 화면(Activity)의 Context. */
    suspend fun signInWithGoogle(activityContext: Context): Result {
        if (!isGoogleConfigured()) return Result.Failed("구글 로그인 설정(GOOGLE_WEB_CLIENT_ID)이 아직 없어요.")

        val idToken = try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else return Result.Failed("구글 계정 정보를 받지 못했어요.")
        } catch (_: GetCredentialCancellationException) {
            return Result.Cancelled
        } catch (_: NoCredentialException) {
            return Result.Failed("이 폰에 로그인할 구글 계정이 없어요. 휴대폰 설정에서 구글 계정을 추가해 주세요.")
        } catch (e: GetCredentialException) {
            return Result.Failed("구글 로그인 창을 열지 못했어요. (${e.type.substringAfterLast('.')})")
        } catch (_: Exception) {
            return Result.Failed("구글 로그인 중 문제가 생겼어요. 다시 시도해 주세요.")
        }

        val (code, body) = BackendApi.postJsonWithCode("/api/auth/google", JSONObject().put("idToken", idToken).toString())
        if (code !in 200..299 || body == null) return Result.Failed(messageFor(code, body))
        val info = parseLogin(body) ?: return Result.Failed("서버 응답을 읽지 못했어요.")
        return complete(activityContext.applicationContext, info)
    }

    // ── 카카오 · 페이스북 · Apple (브라우저) ─────────────────────────────

    /** 서버에서 로그인 주소를 받아 브라우저로 연다. 로그인을 마치면 [handleRedirect]로 돌아온다 */
    suspend fun startBrowserLogin(activityContext: Context, provider: Provider): Result {
        val (code, body) = BackendApi.postJsonWithCode("/api/auth/start", JSONObject().put("provider", provider.key).toString())
        if (code !in 200..299 || body == null) return Result.Failed(messageFor(code, body))
        val url = runCatching { JSONObject(body).getString("url") }.getOrNull()
            ?.takeIf { it.startsWith("https://") } ?: return Result.Failed("로그인 주소를 받지 못했어요.")
        return try {
            activityContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            Result.OpenedBrowser
        } catch (_: ActivityNotFoundException) {
            Result.Failed("로그인 화면을 열 브라우저가 없어요.")
        }
    }

    /** MainActivity 가 fashionai://login 으로 열렸을 때 */
    fun handleRedirect(uri: Uri?) {
        if (uri?.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST) _redirect.value = uri
    }

    fun consumeRedirect(): Uri? = _redirect.value.also { _redirect.value = null }

    /** 브라우저 로그인 마무리 — 1회용 코드를 계정 번호로 바꾼다 */
    suspend fun finishBrowserLogin(context: Context, uri: Uri): Result {
        val loginCode = uri.getQueryParameter("code")
        if (loginCode.isNullOrBlank()) {
            return if (uri.getQueryParameter("error") == "cancelled") Result.Cancelled
            else Result.Failed("로그인하지 못했어요. 다시 시도해 주세요.")
        }
        val (code, body) = BackendApi.postJsonWithCode("/api/auth/exchange", JSONObject().put("code", loginCode).toString())
        if (code !in 200..299 || body == null) return Result.Failed(messageFor(code, body))
        val info = parseLogin(body) ?: return Result.Failed("서버 응답을 읽지 못했어요.")
        return complete(context.applicationContext, info)
    }

    // ── 공통: 계정 기록 맞추기 ───────────────────────────────────────────

    /** 처음 로그인한 계정이면 이 폰 기록을 그대로, 다른 폰에서 쓰던 계정이면 그 기록을 받아 온다 */
    private suspend fun complete(appContext: Context, info: LoginInfo): Result {
        val account = Account(info.email, info.name, info.provider)
        if (!info.switched) {
            save(appContext, Mode.SIGNED_IN, account)
            return Result.Success(account, restart = false, isNewAccount = info.isNewAccount)
        }
        return when (BackupRestore.restore(appContext, info.accountKey)) {
            is BackupRestore.Result.Done -> {
                save(appContext, Mode.SIGNED_IN, account)
                Result.Success(account, restart = true, isNewAccount = false)
            }
            BackupRestore.Result.NotFound, BackupRestore.Result.SameCode -> {
                // 아직 기록이 없는 계정 — 이 폰 기록을 비우고 그 계정 번호로 새로 시작
                clearLocalData(appContext)
                DbSync.adoptForRestore(info.accountKey, emptyMap(), emptyMap())
                save(appContext, Mode.SIGNED_IN, account)
                Result.Success(account, restart = true, isNewAccount = info.isNewAccount)
            }
            else -> Result.Failed("계정 기록을 받아 오지 못했어요. 서버 연결을 확인한 뒤 다시 시도해 주세요.")
        }
    }

    /**
     * 로그아웃 — 보내지 못한 기록을 먼저 서버로 보내고, 이 폰의 기록을 지운 뒤 로그인 화면으로 다시 시작한다.
     * @return 보내지 못한 기록이 남아 있었으면 false (그래도 로그아웃은 진행)
     */
    suspend fun signOut(context: Context): Boolean {
        val appContext = context.applicationContext
        val synced = withTimeoutOrNull(8_000L) { DbSync.flushNow() } ?: false
        DbSync.resetForSignOut()
        clearLocalData(appContext)
        save(appContext, Mode.NONE, null)
        return synced
    }

    /** 이 폰의 사용자 기록(옷장 · 착용 · 취향 · 설정 · 옷 사진)을 지운다. 서버 주소 설정만 남긴다. */
    private suspend fun clearLocalData(context: Context) = withContext(Dispatchers.IO) {
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter { it !in KEEP_ON_SIGN_OUT && it != PREFS && it != "db_sync" }
            ?.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.filesDir, "wardrobe").deleteRecursively()
        File(context.cacheDir, "tryon").deleteRecursively()
    }
}
