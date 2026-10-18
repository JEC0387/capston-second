package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 서버(FastAPI) 주소 설정 — 마이페이지 > 서버 주소.
 *
 * 에뮬레이터는 PC 를 10.0.2.2 로 부르지만, 실제 폰은 PC 의 와이파이 IP(예: 192.168.0.10)로 불러야 한다.
 * 폰과 PC 가 같은 와이파이에 있고, 서버를 `--host 0.0.0.0` 으로 켜야 폰에서 접속된다.
 * 바꾼 주소는 폰에 저장돼 앱을 다시 켜도 유지된다.
 */
object ServerSettings {
    private const val PREFS = "server_settings"
    private const val KEY = "base_url"
    const val DEFAULT_PORT = 8000
    private const val EMULATOR = "http://10.0.2.2:8000"

    /** 앱에 들어 있는 기본 주소 (local.properties SERVER_URL, 없으면 에뮬레이터 주소) */
    val defaultUrl: String get() = normalize(BuildConfig.SERVER_URL) ?: EMULATOR

    /** 앱 시작 때 한 번 — 저장된 주소(없으면 기본 주소)를 BackendApi 에 넣는다 */
    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        BackendApi.BASE_URL = saved?.let { normalize(it) } ?: defaultUrl
    }

    val current: String get() = BackendApi.BASE_URL
    val isDefault: Boolean get() = current == defaultUrl

    fun save(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, url).apply()
        BackendApi.BASE_URL = url
        DbSync.retry()   // 새 주소로 밀린 기록을 바로 보내 본다
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        BackendApi.BASE_URL = defaultUrl
        DbSync.retry()
    }

    /**
     * 입력한 주소를 "http://호스트:포트" 로 다듬는다. 틀리면 null.
     *  "192.168.0.10" → "http://192.168.0.10:8000",  "http://my-pc.local:9000/" → "http://my-pc.local:9000"
     */
    fun normalize(input: String): String? {
        var text = input.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "http://$text"
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return null
        val port = when {
            uri.port in 1..65535 -> uri.port
            uri.port == -1 && scheme == "https" -> 443
            uri.port == -1 -> DEFAULT_PORT
            else -> return null
        }
        return "$scheme://$host:$port"
    }

    /** 연결 확인 결과 */
    data class Check(val reachable: Boolean, val dbReady: Boolean = false, val dbStatus: String? = null, val missing: List<String> = emptyList()) {
        val message: String get() = when {
            !reachable -> "서버에 연결되지 않아요. 같은 와이파이인지, 서버를 --host 0.0.0.0 으로 켰는지, PC 방화벽이 8000번을 막지 않는지 확인해 주세요."
            dbReady && missing.isEmpty() -> "연결됐어요. 서버와 DB 모두 준비됐어요."
            dbReady -> "연결됐어요. DB 에 빠진 표가 있어요(${missing.joinToString()}) — 서버 PC 에서 python apply_migrations.py 를 실행해 주세요."
            dbStatus == "not_configured" -> "서버는 연결됐지만 DB 설정(backend/.env 의 DATABASE_URL)이 없어요. AI 기능만 쓸 수 있어요."
            else -> "서버는 연결됐지만 DB 에 연결되지 않아요. 인터넷 · Supabase 주소를 확인해 주세요."
        }
    }

    /** 이 주소로 서버 · DB 가 응답하는지 확인 (저장하지 않음) */
    suspend fun check(url: String): Check = withContext(Dispatchers.IO) {
        val root = get("$url/") ?: return@withContext Check(reachable = false)
        // 다른 프로그램이 같은 주소에서 응답하는 경우를 걸러 낸다 (우리 서버는 / 에서 service 이름을 돌려줌)
        if (!root.contains("FashionProject")) return@withContext Check(reachable = false)
        val health = get("$url/api/health/database")?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@withContext Check(reachable = true, dbStatus = "unknown")
        val missing = health.optJSONArray("missing")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        Check(true, health.optBoolean("ready"), health.optString("status"), missing)
    }

    private fun get(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 3_000; readTimeout = 5_000; instanceFollowRedirects = false
        }
        try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
