package com.jeiu.fashionproject.data

import android.content.Context
import com.jeiu.fashionproject.recommend.PreferredStyles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 인터랙티브 취향 학습 시스템 (5주차 보고서: Style Preference Learning).
 *
 * 사용자가 코디 카드에 좋아요/싫어요 평가를 할 때마다 스타일별 점수를 갱신.
 * 백엔드 서버가 켜져 있으면 서버와 동기화하고, 없으면 SharedPreferences로 로컬 동작.
 * 스타일은 선호 스타일과 같은 8가지(PreferredStyles.OPTIONS)이고, 점수는 OutfitEngine 코디 점수에 반영된다.
 * 예전 버전의 5가지 중 클래식 → 포멀, 빈티지 → 아메카지로 합쳐 읽는다.
 */
object StylePreferenceStore {
    private const val PREFS = "style_preference"
    private val ioScope = CoroutineScope(Dispatchers.IO)

    val styles = PreferredStyles.OPTIONS
    private val legacyKeys = listOf("클래식", "빈티지")
    private const val CANONICAL_SCORES = "canonical_scores_v1"

    private val _scores = MutableStateFlow(styles.associateWith { 0 })
    val scores: StateFlow<Map<String, Int>> = _scores

    private val _ratedCount = MutableStateFlow(0)
    val ratedCount: StateFlow<Int> = _ratedCount

    fun load(context: Context) {
        // 1) 로컬값 먼저 표시 (즉시 반응)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _scores.value = readLocalScores(context)
        _ratedCount.value = p.getInt("rated_count", 0)

        // 2) 서버가 켜져 있으면 서버값으로 덮어쓰기
        ioScope.launch {
            val server = BackendApi.getStylePreference() ?: return@launch
            if (server.ratedCount < _ratedCount.value) return@launch
            _scores.value = PreferredStyles.canonicalScores(server.scores)
            _ratedCount.value = server.ratedCount
            persist(context)
        }
    }

    /** like=true 면 +2점, false 면 -1점 (선호 신호를 비선호보다 크게 반영) */
    fun rate(context: Context, rawStyle: String, like: Boolean) {
        val style = PreferredStyles.normalize(listOf(rawStyle)).firstOrNull() ?: return
        // 로컬 즉시 반영
        val delta = if (like) 2 else -1
        _scores.value = _scores.value.mapValues { (k, v) -> if (k == style) v + delta else v }
        _ratedCount.value += 1
        persist(context)

        // 서버에도 평가 전송 → 응답으로 동기화 (실패해도 로컬값 유지)
        ioScope.launch {
            val server = BackendApi.rateStylePreference(style, like) ?: return@launch
            if (server.ratedCount < _ratedCount.value) return@launch
            _scores.value = PreferredStyles.canonicalScores(server.scores)
            _ratedCount.value = server.ratedCount
            persist(context)
        }
    }

    private fun persist(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        _scores.value.forEach { (k, v) -> p.putInt("score_$k", v) }
        // Retain legacy/unknown keys for backup compatibility; the marker prevents double counting.
        p.putBoolean(CANONICAL_SCORES, true)
        p.putInt("rated_count", _ratedCount.value).apply()
    }

    /** 백업 코드로 복원할 때 — 서버에서 받은 스타일 점수로 바꿔 쓴다 (앱을 다시 시작해 불러옴) */
    fun writeForRestore(context: Context, scores: Map<String, Int>, ratedCount: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        val merged = PreferredStyles.canonicalScores(scores)
        styles.forEach { p.putInt("score_$it", merged[it] ?: 0) }
        // Explicit restore owns this store. Retain source score keys as well as its canonical projection.
        scores.filterKeys { it !in styles }.forEach { (key, value) -> p.putInt("score_$key", value) }
        p.putBoolean(CANONICAL_SCORES, true).putInt("rated_count", ratedCount).commit()
    }

    internal fun readLocalScores(context: Context): Map<String, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = if (p.getBoolean(CANONICAL_SCORES, false)) styles else styles + legacyKeys
        return PreferredStyles.canonicalScores(keys.associateWith { p.getInt("score_$it", 0) })
    }

    /** 현재 점수 기준 선호 스타일 순위 */
    fun ranking(): List<Pair<String, Int>> =
        _scores.value.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun topStyle(): String = ranking().firstOrNull()?.first ?: styles.first()

    /** 점수가 0보다 큰 스타일이 하나라도 있는지 (아직 좋아요가 없으면 "가장 선호" 문구를 띄우지 않는다) */
    fun hasPositive(): Boolean = _scores.value.values.any { it > 0 }
}
