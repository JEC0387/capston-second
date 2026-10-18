package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test

class TasteLearningTest {
    private fun event(source: String = TasteEvent.SOURCE_OUTFIT, liked: Boolean = true, date: String? = null) =
        TasteEvent(1, source, liked, listOf("블랙", "블랙"), listOf("셔츠", "셔츠"), "무채색 조합", date = date)

    @Test fun explicitLikeAndDislikeLearnDistinctColorKindAndPattern() {
        val liked = TasteScores().plus(event())
        assertEquals(2, liked.colors["블랙"])
        assertEquals(2, liked.kinds["셔츠"])
        assertEquals(2, liked.patterns["무채색 조합"])
        val next = liked.plus(event(liked = false))
        assertEquals(1, next.colors["블랙"])
        assertEquals(2, next.ratedCount)
        assertEquals(0, next.wornCount)
    }

    @Test fun wearIsWeakerThanExplicitLike() {
        val scores = TasteScores().plus(event(TasteEvent.SOURCE_WORN))
        assertEquals(1, scores.colors["블랙"])
        assertEquals(0, scores.ratedCount)
        assertEquals(1, scores.wornCount)
    }

    @Test fun repeatedSaveReplacesDayAndDeleteRemovesOnlyThatDay() {
        val rating = event()
        val oldDay = event(TasteEvent.SOURCE_WORN, date = "2026-10-12")
        val today = event(TasteEvent.SOURCE_WORN, date = "2026-10-13")
        val first = replaceWornEvents(listOf(rating, oldDay), "2026-10-13", today)
        val second = replaceWornEvents(first, "2026-10-13", today.copy(at = 2))
        assertEquals(3, second.size)
        assertEquals(2, second.fold(TasteScores()) { a, e -> a.plus(e) }.wornCount)
        val deleted = replaceWornEvents(second, "2026-10-13", null)
        assertEquals(listOf(rating, oldDay), deleted)
    }

    @Test fun changedDayDoesNotKeepOldColorPreference() {
        val day = event(TasteEvent.SOURCE_WORN, date = "2026-10-13")
        val changed = day.copy(colors = listOf("화이트"))
        val scores = replaceWornEvents(listOf(day), "2026-10-13", changed)
            .fold(TasteScores()) { a, e -> a.plus(e) }
        assertNull(scores.colors["블랙"])
        assertEquals(1, scores.colors["화이트"])
        assertEquals(1, scores.wornCount)
    }

    @Test fun reportDoesNotCallNegativeScoresPreferences() {
        assertEquals(listOf("화이트"), TasteScores().top(mapOf("블랙" to -1, "화이트" to 2, "그레이" to 0), 3))
    }
}
