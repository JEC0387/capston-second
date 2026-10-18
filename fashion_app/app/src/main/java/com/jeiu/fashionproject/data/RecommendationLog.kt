package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.recommend.OutfitEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 화면에 보여 준 추천 코디 한 건.
 * @param context home(홈) / today(오늘) / forecast(5일 예보) / situation:INTERVIEW … (상황별)
 * @param scope 추천 범위 (OWNED 내 옷 / ALL 내 옷 + 관심 옷 / INTERESTED 관심 옷)
 * @param itemIds 코디 옷 id (정렬해 둠 — 같은 조합이면 같은 기록)
 */
data class RecommendationEntry(
    val date: String,
    val context: String,
    val scope: String,
    val rank: Int,
    val itemIds: List<String>,
    val shownAt: Long
) {
    val key: String get() = "$date|$context|${itemIds.joinToString("+")}"
}

/** 추천 성과 — 보여 준 코디 중 👍 · 실제로 입은 수 */
data class RecommendationSummary(
    val shown: Int = 0,
    val liked: Int = 0,
    val disliked: Int = 0,
    val worn: Int = 0,          // 추천한 날 ~ 2일 뒤 안에 코디 옷을 모두 입음
    val wornPartial: Int = 0    // 코디 옷의 절반 이상을 입음 (worn 포함)
) {
    fun percent(n: Int): Int = if (shown == 0) 0 else Math.round(n * 100f / shown)
}

/**
 * 추천 기록 (AI 추천 성능 측정용). 폰에 최근 [MAX_ENTRIES]개를 두고, 서버 DB(recommendation_logs)로 보낸다.
 * 서버는 오래된 기록도 지우지 않고 모아 두므로 /api/recommendations/stats 로 전체 성능을 볼 수 있다.
 * 같은 날 같은 화면의 같은 코디는 한 번만 센다.
 */
object RecommendationLog {
    private const val PREFS = "recommendation_log"
    private const val KEY = "entries"
    const val MAX_ENTRIES = 1000
    const val WORN_WINDOW_DAYS = 2
    private const val PER_SCREEN = 5   // 한 화면에서 위쪽 코디 몇 개까지 기록할지

    private val _entries = MutableStateFlow<List<RecommendationEntry>>(emptyList())
    val entries: StateFlow<List<RecommendationEntry>> = _entries
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _entries.value = raw?.let { runCatching { decode(it) }.getOrNull() } ?: emptyList()
    }

    /**
     * 추천 화면에 코디가 나왔을 때 부른다. 관심 옷(아직 안 산 옷)이 섞인 코디는 '입었는지'를 잴 수 없어 기록하지 않는다.
     */
    fun record(context: Context, date: String, screen: String, scope: String, outfits: List<OutfitEngine.Outfit>) {
        load(context)
        val known = _entries.value.map { it.key }.toHashSet()
        val now = System.currentTimeMillis()
        val added = outfits.take(PER_SCREEN).mapIndexedNotNull { rank, outfit ->
            if (outfit.items.isEmpty() || outfit.items.any { !it.isOwned || it.id.length > 64 }) return@mapIndexedNotNull null
            RecommendationEntry(date, screen, scope, rank, outfit.items.map { it.id }.distinct().sorted().take(8), now)
                .takeIf { known.add(it.key) }
        }
        if (added.isEmpty()) return
        _entries.value = (_entries.value + added).takeLast(MAX_ENTRIES)
        persist(context)
    }

    /** 백업 코드로 복원할 때 — 폰 기록은 비운다 (서버에는 남아 있음) */
    fun clearForRestore(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(_entries.value)).apply()
        DbSync.pushRecommendations(_entries.value)
    }

    fun encode(list: List<RecommendationEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("date", e.date).put("context", e.context).put("scope", e.scope)
                .put("rank", e.rank).put("itemIds", JSONArray(e.itemIds)).put("shownAt", e.shownAt))
        }
        return arr.toString()
    }

    fun decode(raw: String): List<RecommendationEntry> {
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("itemIds")
            RecommendationEntry(o.getString("date"), o.getString("context"), o.optString("scope", "OWNED"),
                o.optInt("rank"), List(ids.length()) { ids.getString(it) }, o.optLong("shownAt"))
        }
    }

    /**
     * 추천 성과 계산 (서버 /api/recommendations/stats 와 같은 규칙).
     * - 👍 / 👎: 같은 옷 조합에 마지막으로 누른 코디 평가
     * - 입음: 추천한 날 ~ 2일 뒤 착용 기록에 코디 옷이 모두 있음 / 절반 이상 있으면 '일부 입음'
     */
    fun summarize(entries: List<RecommendationEntry>, events: List<TasteEvent>, logs: List<WearLog>): RecommendationSummary {
        val ratings = HashMap<Set<String>, TasteEvent>()
        events.filter { it.source == TasteEvent.SOURCE_OUTFIT && it.itemIds.isNotEmpty() }.forEach { e ->
            val k = e.itemIds.toSet()
            val prev = ratings[k]
            if (prev == null || e.at >= prev.at) ratings[k] = e
        }
        val wear = logs.associate { it.date to it.itemIds.toSet() }
        var liked = 0; var disliked = 0; var worn = 0; var partial = 0; var shown = 0
        entries.forEach { r ->
            val items = r.itemIds.toSet()
            if (items.isEmpty()) return@forEach
            shown++
            ratings[items]?.let { if (it.liked) liked++ else disliked++ }
            val days = (0..WORN_WINDOW_DAYS).mapNotNull { d ->
                runCatching { WearDates.addDays(r.date, d) }.getOrNull()?.let { wear[it] }
            }
            val best = days.maxOfOrNull { got -> items.count { it in got } } ?: 0
            if (best == items.size) worn++
            if (best * 2 >= items.size && best > 0) partial++
        }
        return RecommendationSummary(shown, liked, disliked, worn, partial)
    }
}
