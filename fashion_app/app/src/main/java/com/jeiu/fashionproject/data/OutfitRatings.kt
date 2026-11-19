package com.jeiu.fashionproject.data

/** Reconstruct the visible votes from the current account's existing taste history. */
fun outfitRatings(events: List<TasteEvent>): Map<String, Boolean> =
    events.asSequence()
        .filter { it.source == TasteEvent.SOURCE_OUTFIT && it.itemIds.isNotEmpty() }
        .sortedBy { it.at }
        .associate { it.itemIds.joinToString("+") to it.liked }

/** 코디별 마지막 평가 — 옷 번호 묶음(순서 무관) → 좋아요/싫어요. 취소된 코디는 기록이 없어 빠진다 */
fun outfitVotes(events: List<TasteEvent>): Map<Set<String>, Boolean> =
    events.asSequence()
        .filter { it.source == TasteEvent.SOURCE_OUTFIT && it.itemIds.isNotEmpty() }
        .sortedBy { it.at }
        .associate { it.itemIds.toSet() to it.liked }

/** 같은 버튼을 다시 누르면 취소(null), 다른 버튼이면 그 평가로 */
fun nextVote(current: Boolean?, pressedLike: Boolean): Boolean? = if (current == pressedLike) null else pressedLike

/** 이 코디의 이전 평가 기록을 지우고 [event](없으면 취소)로 바꾼다 */
fun replaceOutfitVote(events: List<TasteEvent>, itemIds: Set<String>, event: TasteEvent?): List<TasteEvent> =
    events.filterNot { it.source == TasteEvent.SOURCE_OUTFIT && it.itemIds.toSet() == itemIds } + listOfNotNull(event)
