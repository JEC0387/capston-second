package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test

class OutfitRatingsTest {
    private fun event(at: Long, liked: Boolean, ids: List<String> = listOf("top", "pants", "shoes"), source: String = TasteEvent.SOURCE_OUTFIT) =
        TasteEvent(at, source, liked, emptyList(), emptyList(), itemIds = ids)

    @Test fun recreatedScreenRestoresBothVotesFromExistingHistory() {
        val history = listOf(event(1, true), event(2, false, listOf("top", "cargo", "shoes")))
        assertEquals(mapOf("top+pants+shoes" to true, "top+cargo+shoes" to false), outfitRatings(history.toList()))
        assertEquals(2, history.size)
    }
    @Test fun latestVoteWinsEvenWhenHistoryIsNotOrdered() {
        assertEquals(false, outfitRatings(listOf(event(3, false), event(1, true)))["top+pants+shoes"])
    }
    @Test fun wearingAndPreferenceCardsAreNotOutfitVotes() {
        assertTrue(outfitRatings(listOf(event(1, true, source = TasteEvent.SOURCE_WORN),
            event(2, true, source = TasteEvent.SOURCE_CARD), event(3, true, emptyList()))).isEmpty())
    }
    @Test fun replacingAccountHistoryDoesNotKeepPreviousVotes() {
        assertTrue(outfitRatings(emptyList()).isEmpty())
        assertEquals(mapOf("other" to false), outfitRatings(listOf(event(2, false, listOf("other")))))
    }
}
