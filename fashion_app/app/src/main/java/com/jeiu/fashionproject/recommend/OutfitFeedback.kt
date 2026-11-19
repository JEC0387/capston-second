package com.jeiu.fashionproject.recommend

/**
 * 코디 카드 👍👎 기록을 다음 추천에 직접 반영한다 (색 · 종류 · 스타일 취향 점수와 별도).
 *  - 좋아요한 바로 그 코디: 가산 + "전에 좋아요한 코디예요"
 *  - 싫어요한 바로 그 코디: 크게 감점 (다른 코디가 있으면 뒤로 밀린다)
 *  - 같은 상의 · 하의(또는 원피스) 짝이 들어간 코디에 좋아요가 더 많으면 가산, 싫어요가 더 많으면 감점
 * 평가를 취소하면 기록이 사라져 반영도 사라진다.
 */
object OutfitFeedback {
    const val EXACT_LIKE = 1.0
    const val EXACT_DISLIKE = -3.0
    const val PAIR_LIKE = 0.6
    const val PAIR_DISLIKE = -1.0

    fun adjust(outfit: OutfitEngine.Outfit, votes: Map<Set<String>, Boolean>): OutfitEngine.Outfit {
        if (votes.isEmpty()) return outfit
        val ids = outfit.items.map { it.id }.toSet()
        votes[ids]?.let { liked ->
            return if (liked) outfit.copy(score = outfit.score + EXACT_LIKE, reasons = listOf("전에 좋아요한 코디예요") + outfit.reasons)
            else outfit.copy(score = outfit.score + EXACT_DISLIKE)
        }
        val core = outfit.items.filter { it.category in setOf("상의", "하의", "원피스") }
        if (core.isEmpty()) return outfit
        val coreIds = core.map { it.id }.toSet()
        val related = votes.filterKeys { it.containsAll(coreIds) }.values
        val net = related.count { it } - related.count { !it }
        return when {
            net > 0 -> outfit.copy(score = outfit.score + PAIR_LIKE,
                reasons = listOf("좋아요한 코디의 ${core.joinToString(" + ") { OutfitStory.shortName(it) }} 조합이에요") + outfit.reasons)
            net < 0 -> outfit.copy(score = outfit.score + PAIR_DISLIKE)
            else -> outfit
        }
    }
}
