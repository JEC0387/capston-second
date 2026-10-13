package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.recommend.OutfitEngine
import com.jeiu.fashionproject.recommend.SituationRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 취향 점수 (계획 4 — 4단계). 색 · 종류 · 색 조합 방식별 점수와 학습 횟수.
 * 안드로이드 기능을 쓰지 않는 단순한 값이라 코디 엔진·PC 테스트에서 그대로 쓸 수 있다.
 */
data class TasteScores(
    val colors: Map<String, Int> = emptyMap(),    // "베이지" → 4
    val kinds: Map<String, Int> = emptyMap(),     // "슬랙스" → 3
    val patterns: Map<String, Int> = emptyMap(),  // "톤온톤" → 2
    val ratedCount: Int = 0,                      // 👍👎 · 취향 카드 평가 횟수
    val wornCount: Int = 0                        // 착용 기록으로 배운 횟수
) {
    /** 점수가 0보다 큰 것 중 높은 순 */
    fun top(scores: Map<String, Int>, n: Int): List<String> =
        scores.filter { it.value > 0 }.entries.sortedByDescending { it.value }.take(n).map { it.key }

    val isEmpty: Boolean get() = ratedCount == 0 && wornCount == 0
}

/**
 * 취향 평가 기록 한 건 (DB 연동 대비).
 * 점수 합계만 남기면 나중에 규칙(+2/−1 등)을 바꾸거나 DB로 옮길 때 다시 계산할 수 없어서, 평가 하나하나를 남긴다.
 * (팀 DB 설계의 style_ratings와 같은 생각 — "합계가 아니라 기록")
 *
 * @param source "outfit"(코디 카드 👍👎) / "card"(취향 카드 ❤️✕) / "worn"(착용 기록)
 * @param liked 👍 · ❤️ · 착용이면 true, 👎 · ✕면 false
 * @param itemIds 평가한 옷장 옷 번호 (취향 카드는 옷장 옷이 아니라서 비어 있음)
 * @param date 착용 기록이면 입은 날짜("yyyy-MM-dd")
 */
data class TasteEvent(
    val at: Long,
    val source: String,
    val liked: Boolean,
    val colors: List<String>,
    val kinds: List<String>,
    val pattern: String? = null,
    val style: String? = null,
    val itemIds: List<String> = emptyList(),
    val date: String? = null
) {
    /** 점수 규칙: 착용 +1, 좋아요 +2, 싫어요 −1 */
    val delta: Int get() = when {
        source == SOURCE_WORN -> 1
        liked -> 2
        else -> -1
    }

    companion object {
        const val SOURCE_OUTFIT = "outfit"
        const val SOURCE_CARD = "card"
        const val SOURCE_WORN = "worn"
    }
}

/**
 * 취향 학습 확장 저장소 (계획 4 — 4단계).
 *
 * 스타일 점수는 기존 StylePreferenceStore(서버 동기화)가 맡고,
 * 여기서는 색 · 종류 · 색 조합 방식을 폰 안(SharedPreferences)에만 저장한다.
 *  - 코디 👍 +2 / 👎 −1 (취향 카드 10장도 같음)
 *  - "오늘 입은 옷" 기록 = 약한 좋아요 +1
 * 한 코디에 같은 색·종류가 여러 번 나와도 한 번만 센다.
 *
 * 저장은 평가 기록([events])으로 하고, 점수([scores])는 기록을 모두 더해 계산한다.
 * 기록 방식으로 바뀌기 전에 쌓인 점수는 "예전 합계"(base)로 그대로 더한다.
 */
object TastePreferenceStore {
    private const val PREFS = "taste_preference"
    private const val KEY_LEGACY = "scores"   // 예전 버전: 합계만 저장
    private const val KEY_BASE = "base"       // 기록 방식 이전에 쌓인 합계
    private const val KEY_EVENTS = "events"

    private val _scores = MutableStateFlow(TasteScores())
    val scores: StateFlow<TasteScores> = _scores

    /** 평가 기록 전부 (오래된 순) — 나중에 DB로 올릴 때 쓴다 */
    private val _events = MutableStateFlow<List<TasteEvent>>(emptyList())
    val events: StateFlow<List<TasteEvent>> = _events

    private var base = TasteScores()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 예전 합계는 base로 옮긴다 (한 번만)
        val legacy = prefs.getString(KEY_LEGACY, null)
        if (legacy != null && !prefs.contains(KEY_BASE)) {
            prefs.edit().putString(KEY_BASE, legacy).remove(KEY_LEGACY).apply()
        }
        val baseRaw = prefs.getString(KEY_BASE, null) ?: legacy
        base = baseRaw?.let { runCatching { scoresFromJson(JSONObject(it)) }.getOrNull() } ?: TasteScores()
        _events.value = prefs.getString(KEY_EVENTS, null)?.let { raw ->
            runCatching { JSONArray(raw).let { arr -> List(arr.length()) { eventFromJson(arr.getJSONObject(it)) } } }.getOrNull()
        } ?: emptyList()
        _scores.value = _events.value.fold(base) { acc, e -> acc.plus(e) }
    }

    /** 코디 카드 👍👎 — 스타일(기존 저장소)과 색·종류·조합 방식을 함께 학습한다 */
    fun rateOutfit(context: Context, outfit: OutfitEngine.Outfit, like: Boolean) {
        StylePreferenceStore.rate(context, outfit.style, like)
        record(
            context,
            TasteEvent(
                at = System.currentTimeMillis(), source = TasteEvent.SOURCE_OUTFIT, liked = like,
                colors = outfit.items.map { it.colorName }, kinds = outfit.items.map { SituationRules.kindOf(it) },
                pattern = outfit.pattern, style = outfit.style, itemIds = outfit.items.map { it.id }
            )
        )
    }

    /** 취향 카드 ❤️ / ✕ — 카드에 붙은 색·종류 정보로 학습 (스타일 점수는 화면에서 따로 반영, 여기엔 기록만 남김) */
    fun rateCard(context: Context, colors: List<String>, kinds: List<String>, pattern: String?, like: Boolean, style: String? = null) {
        record(context, TasteEvent(System.currentTimeMillis(), TasteEvent.SOURCE_CARD, like, colors, kinds, pattern, style))
    }

    /** 착용 기록 — 입은 옷의 색·종류에 약한 좋아요(+1) */
    fun learnWorn(context: Context, items: List<WardrobeItem>, date: String? = null) {
        if (items.isEmpty()) return
        record(
            context,
            TasteEvent(
                at = System.currentTimeMillis(), source = TasteEvent.SOURCE_WORN, liked = true,
                colors = items.map { it.colorName }, kinds = items.map { SituationRules.kindOf(it) },
                itemIds = items.map { it.id }, date = date
            )
        )
    }

    private fun record(context: Context, event: TasteEvent) {
        load(context)
        _events.value = _events.value + event
        _scores.value = _scores.value.plus(event)
        persist(context)
    }

    /** 기록 하나를 점수에 더한다. 한 기록 안에서 같은 색·종류는 한 번만 센다 */
    private fun TasteScores.plus(e: TasteEvent): TasteScores = copy(
        colors = colors.plusEach(e.colors.distinct(), e.delta),
        kinds = kinds.plusEach(e.kinds.distinct(), e.delta),
        patterns = if (e.pattern == null) patterns else patterns.plusEach(listOf(e.pattern), e.delta),
        ratedCount = ratedCount + if (e.source == TasteEvent.SOURCE_WORN) 0 else 1,
        wornCount = wornCount + if (e.source == TasteEvent.SOURCE_WORN) 1 else 0
    )

    private fun Map<String, Int>.plusEach(keys: List<String>, delta: Int): Map<String, Int> =
        toMutableMap().apply { keys.forEach { this[it] = (this[it] ?: 0) + delta } }

    private fun JSONObject?.toIntMap(): Map<String, Int> =
        if (this == null) emptyMap() else keys().asSequence().associateWith { getInt(it) }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private fun scoresFromJson(o: JSONObject) = TasteScores(
        colors = o.optJSONObject("colors").toIntMap(),
        kinds = o.optJSONObject("kinds").toIntMap(),
        patterns = o.optJSONObject("patterns").toIntMap(),
        ratedCount = o.optInt("ratedCount"),
        wornCount = o.optInt("wornCount")
    )

    private fun eventFromJson(o: JSONObject) = TasteEvent(
        at = o.getLong("at"),
        source = o.getString("source"),
        liked = o.getBoolean("liked"),
        colors = o.optJSONArray("colors").toStringList(),
        kinds = o.optJSONArray("kinds").toStringList(),
        pattern = o.optStringOrNull("pattern"),
        style = o.optStringOrNull("style"),
        itemIds = o.optJSONArray("itemIds").toStringList(),
        date = o.optStringOrNull("date")
    )

    private fun persist(context: Context) {
        val arr = JSONArray()
        _events.value.forEach { e ->
            arr.put(
                JSONObject()
                    .put("at", e.at)
                    .put("source", e.source)
                    .put("liked", e.liked)
                    .put("colors", JSONArray(e.colors))
                    .put("kinds", JSONArray(e.kinds))
                    .put("pattern", e.pattern ?: JSONObject.NULL)
                    .put("style", e.style ?: JSONObject.NULL)
                    .put("itemIds", JSONArray(e.itemIds))
                    .put("date", e.date ?: JSONObject.NULL)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, arr.toString()).apply()
    }
}
