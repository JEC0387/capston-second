package com.jeiu.fashionproject.data

import android.content.Context
import org.json.JSONArray

/**
 * 내 프로필 중 폰에 저장하는 값 — 지금은 온보딩에서 고른 선호 스타일.
 * 폰에 먼저 저장하고(서버가 꺼져 있어도 유지), 서버 DB(users.preferred_styles)에도 보낸다.
 */
object ProfileStore {
    fun hasSelection(context: Context): Boolean = context.getSharedPreferences("profile_store", Context.MODE_PRIVATE).contains("preferred_styles")

    private const val PREFS = "profile_store"
    private const val KEY_STYLES = "preferred_styles"

    /** 온보딩에서 고를 수 있는 스타일 (서버 STYLE_OPTIONS 와 같아야 함) */
    val STYLE_OPTIONS = listOf("캐주얼", "미니멀", "스트릿", "페미닌", "빈티지", "스포티", "클래식", "아메카지")

    fun preferredStyles(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STYLES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }.filter { it in STYLE_OPTIONS }
        }.getOrDefault(emptyList())
    }

    /** 선호 스타일 저장 (폰 + 서버). 고른 순서를 그대로 지킨다. */
    fun savePreferredStyles(context: Context, styles: List<String>) {
        val valid = styles.filter { it in STYLE_OPTIONS }.distinct()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STYLES, JSONArray(valid).toString()).apply()
        DbSync.pushPreferredStyles(valid)
    }
}
