package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 가상 옷장 전역 저장소 (5주차 보고서: 원터치 가상 옷장 구축).
 *
 * AI 등록 화면에서 추가한 옷을 앱 어디서든 보여줄 수 있도록 StateFlow로 공유하고,
 * SharedPreferences에 JSON으로 영속화해 앱을 재시작해도 유지한다.
 */
data class WardrobeItem(
    val name: String,
    val category: String,      // 상의 / 하의 / 아우터 / 신발 / 액세서리
    val colorName: String,     // 자동 추출된 대표 색상명
    val colorValue: Long,      // 0xFFRRGGBB
    val imageUri: String,      // 사용자가 촬영/선택한 원본 이미지
    val aiTagged: Boolean = false   // AI 자동 태깅 여부
)

object WardrobeStore {
    private const val PREFS = "wardrobe_store"
    private const val KEY = "items"

    private val _items = MutableStateFlow<List<WardrobeItem>>(emptyList())
    val items: StateFlow<List<WardrobeItem>> = _items

    fun load(context: Context) {
        if (_items.value.isNotEmpty()) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            _items.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WardrobeItem(
                    name = o.getString("name"),
                    category = o.getString("category"),
                    colorName = o.getString("colorName"),
                    colorValue = o.getLong("colorValue"),
                    imageUri = o.getString("imageUri"),
                    aiTagged = o.optBoolean("aiTagged", false)
                )
            }
        }
    }

    fun add(context: Context, item: WardrobeItem) {
        _items.value = _items.value + item
        persist(context)
    }

    fun remove(context: Context, item: WardrobeItem) {
        _items.value = _items.value - item
        persist(context)
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        _items.value.forEach { item ->
            arr.put(
                JSONObject()
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("colorName", item.colorName)
                    .put("colorValue", item.colorValue)
                    .put("imageUri", item.imageUri)
                    .put("aiTagged", item.aiTagged)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
