package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** Single source of truth for the existing shopping/detail hearts, now shared across screens. 바뀌면 서버 DB에도 저장. */
object ProductInterestStore {
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    val ids: StateFlow<List<String>> = _ids
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences("product_interests", Context.MODE_PRIVATE)
            .getString("ids", "[]") ?: "[]"
        _ids.value = runCatching {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }.distinct()
        }.getOrDefault(emptyList())
        // 서버 DB(product_interests)도 폰과 같게 맞춰 둔다 (서버가 꺼져 있으면 대기열에 남았다가 나중에)
        DbSync.pushProductInterests(_ids.value)
    }

    fun toggled(ids: List<String>, id: String): List<String> =
        if (id in ids) ids.filterNot { it == id } else ids + id

    fun toggle(context: Context, id: String) {
        load(context)
        _ids.value = toggled(_ids.value, id)
        context.getSharedPreferences("product_interests", Context.MODE_PRIVATE).edit()
            .putString("ids", JSONArray(_ids.value).toString()).apply()
        DbSync.pushProductInterests(_ids.value)
    }
}
