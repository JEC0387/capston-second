package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 저장한 코디 (룩북) — 추천 코디 카드의 🔖 를 누르면 저장된다.
 * 옷 조합(옷 id 집합)이 같으면 같은 코디로 본다. 옷장에서 옷을 지워도 기록은 남고, 화면에서는 남은 옷만 보여 준다.
 */
data class SavedOutfit(
    val id: String,                 // 옷 id 를 정렬해 + 로 이은 값 (같은 조합 = 같은 코디)
    val itemIds: List<String>,
    val style: String,
    val pattern: String? = null,
    val note: String = "",
    val savedAt: Long
) {
    companion object {
        fun idOf(itemIds: Collection<String>): String = itemIds.distinct().sorted().joinToString("+")
    }
}

/**
 * 코디 일정 — 날짜에 입을 코디를 미리 정해 둔다 (하루에 하나).
 * 홈의 '오늘 계획한 코디', 착용 달력, 내일 날씨 알림에 함께 보여 준다.
 */
data class OutfitPlan(
    val date: String,               // yyyy-MM-dd
    val itemIds: List<String>,
    val note: String = "",
    val createdAt: Long
)

object SavedOutfitStore {
    private const val PREFS = "saved_outfits"
    private const val KEY = "items"
    const val MAX = 200

    private val _items = MutableStateFlow<List<SavedOutfit>>(emptyList())
    /** 최근에 저장한 것부터 */
    val items: StateFlow<List<SavedOutfit>> = _items
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _items.value = raw?.let { runCatching { decode(it) }.getOrNull() } ?: emptyList()
        DbSync.pushSavedOutfits(_items.value)
    }

    fun isSaved(itemIds: Collection<String>): Boolean = _items.value.any { it.id == SavedOutfit.idOf(itemIds) }

    /** 저장 / 저장 취소. 저장했으면 true */
    fun toggle(context: Context, itemIds: List<String>, style: String, pattern: String?): Boolean {
        load(context)
        val id = SavedOutfit.idOf(itemIds)
        val saved = _items.value.none { it.id == id }
        _items.value = toggled(_items.value, SavedOutfit(id, itemIds.distinct(), style, pattern, "", System.currentTimeMillis()))
        persist(context)
        return saved
    }

    fun setNote(context: Context, id: String, note: String) {
        load(context)
        _items.value = _items.value.map { if (it.id == id) it.copy(note = note.take(100)) else it }
        persist(context)
    }

    fun remove(context: Context, id: String) {
        load(context)
        _items.value = _items.value.filterNot { it.id == id }
        persist(context)
    }

    /** 같은 조합이 있으면 빼고, 없으면 맨 앞에 넣는다 (최대 [MAX]개) */
    fun toggled(list: List<SavedOutfit>, outfit: SavedOutfit): List<SavedOutfit> =
        if (list.any { it.id == outfit.id }) list.filterNot { it.id == outfit.id }
        else (listOf(outfit) + list).take(MAX)

    fun writeForRestore(context: Context, list: List<SavedOutfit>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(list)).commit()
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(_items.value)).apply()
        DbSync.pushSavedOutfits(_items.value)
    }

    fun encode(list: List<SavedOutfit>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("itemIds", JSONArray(it.itemIds)).put("style", it.style)
                .put("pattern", it.pattern ?: JSONObject.NULL).put("note", it.note).put("savedAt", it.savedAt))
        }
        return arr.toString()
    }

    fun decode(raw: String): List<SavedOutfit> {
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("itemIds").let { a -> List(a.length()) { a.getString(it) } }
            SavedOutfit(o.optString("id").ifBlank { SavedOutfit.idOf(ids) }, ids, o.optString("style", ""),
                if (o.isNull("pattern")) null else o.optString("pattern"), o.optString("note", ""), o.optLong("savedAt"))
        }
    }
}

object OutfitPlanStore {
    private const val PREFS = "outfit_plans"
    private const val KEY = "plans"
    /** 지난 일정은 60일까지만 폰에 둔다 (서버에도 같은 범위로 맞춰짐) */
    const val KEEP_PAST_DAYS = 60

    private val _plans = MutableStateFlow<List<OutfitPlan>>(emptyList())
    /** 날짜 순 */
    val plans: StateFlow<List<OutfitPlan>> = _plans
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val all = raw?.let { runCatching { decode(it) }.getOrNull() } ?: emptyList()
        _plans.value = pruned(all, WearDates.today())
        if (_plans.value.size != all.size) persist(context) else DbSync.pushOutfitPlans(_plans.value)
    }

    fun planFor(date: String): OutfitPlan? = _plans.value.find { it.date == date }

    /** 그 날짜의 일정을 정한다 (이미 있으면 바꾼다) */
    fun set(context: Context, date: String, itemIds: List<String>, note: String = "") {
        load(context)
        _plans.value = upserted(_plans.value, OutfitPlan(date, itemIds.distinct(), note.take(100), System.currentTimeMillis()))
        persist(context)
    }

    fun remove(context: Context, date: String) {
        load(context)
        _plans.value = _plans.value.filterNot { it.date == date }
        persist(context)
    }

    fun upserted(list: List<OutfitPlan>, plan: OutfitPlan): List<OutfitPlan> =
        (list.filterNot { it.date == plan.date } + plan).sortedBy { it.date }

    fun pruned(list: List<OutfitPlan>, today: String): List<OutfitPlan> {
        val cutoff = WearDates.addDays(today, -KEEP_PAST_DAYS)
        return list.filter { it.date >= cutoff }.sortedBy { it.date }
    }

    /** 일정대로 입었는지 — 그날 착용 기록에 일정의 옷이 모두 있으면 true, 기록이 없으면 null */
    fun followed(plan: OutfitPlan, log: WearLog?): Boolean? =
        if (log == null) null else plan.itemIds.all { it in log.itemIds }

    fun writeForRestore(context: Context, list: List<OutfitPlan>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(list)).commit()
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(_plans.value)).apply()
        DbSync.pushOutfitPlans(_plans.value)
    }

    fun encode(list: List<OutfitPlan>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("date", it.date).put("itemIds", JSONArray(it.itemIds)).put("note", it.note)
                .put("createdAt", it.createdAt))
        }
        return arr.toString()
    }

    fun decode(raw: String): List<OutfitPlan> {
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("itemIds").let { a -> List(a.length()) { a.getString(it) } }
            OutfitPlan(o.getString("date"), ids, o.optString("note", ""), o.optLong("createdAt"))
        }.sortedBy { it.date }
    }
}
