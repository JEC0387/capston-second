package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.recommend.PreferredStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * 내 프로필 중 폰에 저장하는 값 — 온보딩 · 마이페이지에서 고른 선호 스타일.
 * 폰에 먼저 저장하고(서버가 꺼져 있어도 유지), 서버 DB(users.preferred_styles)에도 보낸다.
 * [styles]를 구독하면 마이페이지에서 바꾼 값이 홈 · 코디 추천에 바로 반영된다.
 */
object ProfileStore {
    fun hasSelection(context: Context): Boolean = context.getSharedPreferences("profile_store", Context.MODE_PRIVATE).contains("preferred_styles")

    private const val PREFS = "profile_store"
    private const val KEY_STYLES = "preferred_styles"

    /** 고를 수 있는 선호 스타일 (서버 STYLE_OPTIONS 와 같아야 함) */
    val STYLE_OPTIONS = PreferredStyles.OPTIONS

    private val _styles = MutableStateFlow<List<String>>(emptyList())
    /** 지금 선호 스타일 (고른 순서) — [load] 또는 저장 뒤부터 값이 들어 있다 */
    val styles: StateFlow<List<String>> = _styles
    @Volatile private var loaded = false

    /** 폰에 저장된 선호 스타일을 [styles]에 불러온다 (여러 번 불러도 처음 한 번만 읽음) */
    fun load(context: Context) {
        if (loaded) return
        _styles.value = preferredStyles(context)
        loaded = true
    }

    /** 예전 버전의 스타일 이름(클래식 · 빈티지)은 새 이름(포멀 · 아메카지)으로 바꿔 읽는다 */
    fun preferredStyles(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STYLES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            PreferredStyles.normalize(List(arr.length()) { arr.getString(it) })
        }.getOrDefault(emptyList())
    }

    /** 백업 코드로 복원할 때 — 서버의 선호 스타일을 폰에만 써 둔다 */
    fun writeForRestore(context: Context, styles: List<String>) {
        val valid = PreferredStyles.normalize(styles)
        if (valid.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STYLES, JSONArray(valid).toString()).commit()
        _styles.value = valid; loaded = true
    }

    /** 선호 스타일 저장 (폰 + 서버). 고른 순서를 그대로 지킨다. */
    fun savePreferredStyles(context: Context, styles: List<String>) {
        val valid = PreferredStyles.normalize(styles)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STYLES, JSONArray(valid).toString()).apply()
        _styles.value = valid; loaded = true
        DbSync.pushPreferredStyles(valid)
    }
}
