package com.jeiu.fashionproject.data

/** Reconstruct the visible votes from the current account's existing taste history. */
fun outfitRatings(events: List<TasteEvent>): Map<String, Boolean> =
    events.asSequence()
        .filter { it.source == TasteEvent.SOURCE_OUTFIT && it.itemIds.isNotEmpty() }
        .sortedBy { it.at }
        .associate { it.itemIds.joinToString("+") to it.liked }
