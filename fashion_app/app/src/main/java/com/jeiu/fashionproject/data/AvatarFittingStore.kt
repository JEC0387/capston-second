package com.jeiu.fashionproject.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Normalized image-overlay placement; never stores a body photo or garment pixels. */
data class AvatarOverlay(
    val itemId: String, val centerX: Float, val centerY: Float,
    val width: Float, val scale: Float, val rotation: Float
) {
    fun valid(): Boolean = itemId.isNotBlank() && itemId.length <= 100 &&
        listOf(centerX, centerY, width, scale, rotation).all { it.isFinite() } &&
        centerX in -1f..2f && centerY in -1f..2f && width in .01f..2f &&
        scale in .4f..3f && rotation in -3600f..3600f
}

/** Corrupt data is reported and left untouched; save is explicit from the fitting screen. */
object AvatarFittingStore {
    private const val PREFS = "avatar_fitting_overlay_v1"
    private const val KEY = "placements"
    var issue: String? = null
        private set

    fun load(context: Context): List<AvatarOverlay> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw == null) { issue = null; return emptyList() }
        val entries = runCatching {
            val root = JSONObject(raw)
            require(root.getInt("schema") == 1)
            val array = root.getJSONArray("items")
            require(array.length() <= 20)
            (0 until array.length()).map { index ->
                val it = array.getJSONObject(index)
                AvatarOverlay(it.getString("id"), it.getDouble("x").toFloat(),
                    it.getDouble("y").toFloat(), it.getDouble("width").toFloat(),
                    it.getDouble("scale").toFloat(), it.getDouble("rotation").toFloat())
            }.also { require(it.all(AvatarOverlay::valid) && it.map(AvatarOverlay::itemId).distinct().size == it.size) }
        }.getOrNull()
        issue = if (entries == null) "저장된 아바타 옷 배치를 읽지 못했어요. 원본을 보존했어요." else null
        return entries.orEmpty()
    }

    fun save(context: Context, entries: List<AvatarOverlay>): Boolean {
        if (issue != null || entries.size > 20 || entries.any { !it.valid() } ||
            entries.map { it.itemId }.distinct().size != entries.size) return false
        val items = JSONArray()
        entries.forEach { e -> items.put(JSONObject().put("id", e.itemId).put("x", e.centerX)
            .put("y", e.centerY).put("width", e.width).put("scale", e.scale)
            .put("rotation", e.rotation)) }
        val payload = JSONObject().put("schema", 1).put("items", items).toString()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, payload).commit()
    }

    fun clear(context: Context): Boolean {
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
        if (ok) issue = null
        return ok
    }
}
