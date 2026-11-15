package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

/** 코디 👍👎: 다시 누르면 취소, 다른 버튼이면 바꾸기, 남은 평가를 다음 추천에 반영 */
class OutfitVoteToggleTest {
    private fun event(at: Long, liked: Boolean, ids: List<String> = listOf("top", "pants", "shoes")) =
        TasteEvent(at, TasteEvent.SOURCE_OUTFIT, liked, listOf("블랙"), listOf("셔츠"), itemIds = ids)

    @Test fun pressingTheSameButtonCancelsAndTheOtherSwitches() {
        assertEquals(true, nextVote(null, true))
        assertNull(nextVote(true, true))
        assertEquals(false, nextVote(true, false))
        assertNull(nextVote(false, false))
        assertEquals(true, nextVote(false, true))
    }

    @Test fun replacingAVoteRemovesTheOldEventSoScoresRevert() {
        val liked = listOf(event(1, true))
        val switched = replaceOutfitVote(liked, setOf("shoes", "pants", "top"), event(2, false))
        assertEquals(listOf(false), switched.map { it.liked })
        assertEquals(mapOf(setOf("top", "pants", "shoes") to false), outfitVotes(switched))
        val cancelled = replaceOutfitVote(switched, setOf("top", "pants", "shoes"), null)
        assertTrue(outfitVotes(cancelled).isEmpty())
        assertTrue(outfitRatings(cancelled).isEmpty())
        // 다른 코디 · 착용 기록은 그대로
        val other = event(3, true, listOf("a", "b", "c"))
        val worn = TasteEvent(4, TasteEvent.SOURCE_WORN, true, listOf("블랙"), listOf("셔츠"), itemIds = listOf("top"))
        assertEquals(2, replaceOutfitVote(liked + other + worn, setOf("top", "pants", "shoes"), null).size)
    }

    @Test fun styleScoreCorrectionsSumExactlyWithPlusTwoMinusOneSteps() {
        for ((from, to) in listOf(true to null, true to false, false to null, false to true, null to true, null to false)) {
            fun v(b: Boolean?) = when (b) { true -> 2; false -> -1; null -> 0 }
            val delta = v(to) - v(from)
            val (likes, dislikes) = StylePreferenceStore.serverSteps(delta)
            assertEquals("$from→$to", delta, likes * 2 - dislikes)
            assertTrue(likes >= 0 && dislikes >= 0)
        }
    }

    private fun item(id: String, category: String, sub: String, kind: String, colorName: String, color: Long) = WardrobeItem(
        id = id, name = "$colorName $kind", category = category, colorName = colorName, colorValue = color,
        imageUri = "", subCategory = sub, detailType = kind, season = "사계절"
    )
    private val wardrobe = listOf(
        item("t1", "상의", "긴팔 상의", "셔츠", "화이트", 0xFFF5F5F5), item("t2", "상의", "긴팔 상의", "니트", "그레이", 0xFF808080),
        item("b1", "하의", "긴바지", "슬랙스", "블랙", 0xFF000000), item("b2", "하의", "긴바지", "청바지", "블루", 0xFF6A88B0),
        item("s1", "신발", "운동화", "스니커즈", "화이트", 0xFFF5F5F5)
    )
    private fun recommend(votes: Map<Set<String>, Boolean>) = (OutfitEngine.recommend(wardrobe, WeatherInfo("맑음", 18, 50, 800),
        emptyList(), emptyMap(), today = "2026-10-01", taste = TasteScores(outfitVotes = votes)) as OutfitEngine.Result.Ok).outfits

    @Test fun dislikedOutfitDropsAndLikedOutfitRisesWithReason() {
        val base = recommend(emptyMap())
        fun ids(o: OutfitEngine.Outfit) = o.items.map { it.id }.toSet()
        val first = ids(base.first())
        val afterDislike = recommend(mapOf(first to false))
        afterDislike.find { ids(it) == first }?.let { o ->
            assertEquals(base.first().score + OutfitFeedback.EXACT_DISLIKE, o.score, 1e-9)
            assertTrue(afterDislike.indexOf(o) >= 0)
        }
        val last = ids(base.last())
        val afterLike = recommend(mapOf(last to true))
        val liked = afterLike.first { ids(it) == last }
        assertEquals(base.last().score + OutfitFeedback.EXACT_LIKE, liked.score, 1e-9)
        assertTrue(afterLike.indexOf(liked) <= base.size - 1)
        assertEquals("전에 좋아요한 코디예요", liked.reasons.first())
        // 취소(기록 없음)하면 원래대로
        assertEquals(base.map { it.id }, recommend(emptyMap()).map { it.id })
    }
}
