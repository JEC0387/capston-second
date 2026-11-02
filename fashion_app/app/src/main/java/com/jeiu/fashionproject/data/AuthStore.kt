package com.jeiu.fashionproject.data

import android.content.Context
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
 * 로그인 상태 — 구글 계정 로그인 또는 로그인 없이(게스트).
 *
 * 구글 로그인 순서:
 *  1) Credential Manager 로 구글 계정을 고르면 구글 ID 토큰을 받는다 (비밀번호는 앱이 보지 않음)
 *  2) 우리 서버 /api/auth/google 이 토큰을 구글에 확인하고, 그 계정의 번호(accountKey)를 돌려준다
 *  3) 처음 로그인한 계정이면 지금 폰의 기록을 그대로 이 계정으로 이어 받고,
 *     다른 폰에서 쓰던 계정이면 이 폰을 그 계정 번호로 바꾸고 서버의 옷장 · 착용 · 취향 기록을 받아 온다
 * 로그아웃하면 이 폰의 기록을 지우고(계정 기록은 서버에 남음) 새 게스트로 다시 시작한다.
 * 같은 폰에서 여러 사람이 각자 계정으로 로그인해도 옷장 · 취향이 섞이지 않는다.
 */
object AuthStore {

    enum class Mode { NONE, GUEST, GOOGLE }

    data class Account(val email: String, val name: String)

    /** 서버 응답 (/api/auth/google) */
    data class LoginInfo(val accountKey: String, val email: String, val name: String, val isNewAccount: Boolean, val switched: Boolean)

    sealed class Result {
        /** @param restart true 면 받아 온 계정 기록을 불러오도록 앱을 다시 시작해야 함 */
        data class Success(val account: Account, val restart: Boolean, val isNewAccount: Boolean) : Result()
        object Cancelled : Result()
        data class Failed(val message: String) : Result()
    }

    private const val PREFS = "auth"
    /** 로그아웃해도 지우지 않는 저장소 — 서버 주소 설정 */
    private val KEEP_ON_SIGN_OUT = setOf("server_settings")

    private val _mode = MutableStateFlow(Mode.NONE)
    val mode: StateFlow<Mode> = _mode
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account
    @Volatile private var loaded = false

    /** 구글 로그인 설정(웹 클라이언트 ID)이 들어 있는지 */
    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = runCatching { Mode.valueOf(p.getString("mode", Mode.NONE.name)!!) }.getOrDefault(Mode.NONE)
        _account.value = p.getString("email", null)?.let { Account(it, p.getString("name", "") ?: "") }
        loaded = true
    }

    private fun save(context: Context, mode: Mode, account: Account?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name)
            .putString("email", account?.email)
            .putString("name", account?.name)
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
            j.optBoolean("isNewAccount"), j.optBoolean("switched"))
    }.getOrNull()?.takeIf { BackupRestore.normalize(it.accountKey) != null }

    /** 서버 오류 → 사용자에게 보여줄 말 */
    fun messageFor(code: Int, body: String?): String {
        val detail = body?.let { runCatching { JSONObject(it).optString("detail") }.getOrNull() }.orEmpty()
        return when (code) {
            -1 -> "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
            401 -> "구글 로그인 정보를 확인하지 못했어요. 다시 시도해 주세요."
            503 -> detail.ifBlank { "서버의 로그인 설정이 아직 준비되지 않았어요." }
            502 -> "구글 확인 서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요."
            else -> "로그인하지 못했어요. (오류 $code)"
        }
    }

    /**
     * 구글 계정으로 로그인. [activityContext]는 계정 선택 창을 띄울 화면(Activity)의 Context.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result {
        if (!isConfigured()) return Result.Failed("구글 로그인 설정(GOOGLE_WEB_CLIENT_ID)이 아직 없어요.")
        val appContext = activityContext.applicationContext

        // ① 구글 계정 고르기 → ID 토큰
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

        // ② 서버에 확인 → 계정 번호
        val (code, body) = BackendApi.postJsonWithCode("/api/auth/google", JSONObject().put("idToken", idToken).toString())
        if (code !in 200..299 || body == null) return Result.Failed(messageFor(code, body))
        val info = parseLogin(body) ?: return Result.Failed("서버 응답을 읽지 못했어요.")
        val account = Account(info.email, info.name)

        // ③ 다른 폰에서 쓰던 계정이면 그 기록을 받아 오기
        if (!info.switched) {
            save(appContext, Mode.GOOGLE, account)
            return Result.Success(account, restart = false, isNewAccount = info.isNewAccount)
        }
        return when (BackupRestore.restore(appContext, info.accountKey)) {
            is BackupRestore.Result.Done -> {
                save(appContext, Mode.GOOGLE, account)
                Result.Success(account, restart = true, isNewAccount = false)
            }
            BackupRestore.Result.NotFound, BackupRestore.Result.SameCode -> {
                // 아직 기록이 없는 계정 — 이 폰 기록을 비우고 그 계정 번호로 새로 시작
                clearLocalData(appContext)
                DbSync.adoptForRestore(info.accountKey, emptyMap(), emptyMap())
                save(appContext, Mode.GOOGLE, account)
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
