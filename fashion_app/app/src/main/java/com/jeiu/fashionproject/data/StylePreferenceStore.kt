package com.jeiu.fashionproject.data

import android.content.Context
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
 */
object StylePreferenceStore {
    private const val PREFS = "style_preference"
    private val ioScope = CoroutineScope(Dispatchers.IO)

    val styles = listOf("미니멀", "캐주얼", "클래식", "스트릿", "빈티지")

    private val _scores = MutableStateFlow(styles.associateWith { 0 })
    val scores: StateFlow<Map<String, Int>> = _scores

    private val _ratedCount = MutableStateFlow(0)
    val ratedCount: StateFlow<Int> = _ratedCount

    fun load(context: Context) {
        // 1) 로컬값 먼저 표시 (즉시 반응)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _scores.value = styles.associateWith { p.getInt("score_$it", 0) }
        _ratedCount.value = p.getInt("rated_count", 0)

        // 2) 서버가 켜져 있으면 서버값으로 덮어쓰기
        ioScope.launch {
            val server = BackendApi.getStylePreference() ?: return@launch
            _scores.value = styles.associateWith { server.scores[it] ?: 0 }
            _ratedCount.value = server.ratedCount
            persist(context)
        }
    }

    /** like=true 면 +2점, false 면 -1점 (선호 신호를 비선호보다 크게 반영) */
    fun rate(context: Context, style: String, like: Boolean) {
        // 로컬 즉시 반영
        val delta = if (like) 2 else -1
        _scores.value = _scores.value.mapValues { (k, v) -> if (k == style) v + delta else v }
        _ratedCount.value += 1
        persist(context)

        // 서버에도 평가 전송 → 응답으로 동기화 (실패해도 로컬값 유지)
        ioScope.launch {
            val server = BackendApi.rateStylePreference(style, like) ?: return@launch
            _scores.value = styles.associateWith { server.scores[it] ?: 0 }
            _ratedCount.value = server.ratedCount
            persist(context)
        }
    }

    private fun persist(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        _scores.value.forEach { (k, v) -> p.putInt("score_$k", v) }
        p.putInt("rated_count", _ratedCount.value).apply()
    }

    /** 현재 점수 기준 선호 스타일 순위 */
    fun ranking(): List<Pair<String, Int>> =
        _scores.value.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun topStyle(): String = ranking().firstOrNull()?.first ?: styles.first()
}
