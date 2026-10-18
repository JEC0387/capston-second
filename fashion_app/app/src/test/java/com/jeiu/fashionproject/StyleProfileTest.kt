package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.recommend.StyleProfile
import org.junit.Assert.*
import org.junit.Test

class StyleProfileTest {
    private fun item(id: String, style: String, color: String = "블랙") = WardrobeItem(
        id = id, name = id, category = "상의", colorName = color, colorValue = 0xFF000000,
        imageUri = "", style = style, addedAt = 0L
    )

    private val closet = listOf(
        item("a", "클래식"), item("b", "클래식"), item("c", "클래식"),
        item("d", "캐주얼", "베이지"), item("e", "미니멀")
    )

    @Test fun emptyClosetHasNoResult() {
        assertNull(StyleProfile.analyze(emptyList(), emptyList(), today = "2026-10-12"))
    }

    @Test fun withoutWearLogsUsesOwnershipOnly() {
        val r = StyleProfile.analyze(closet, emptyList(), today = "2026-10-12")!!
        assertEquals("클래식", r.top.style)
        assertEquals(60, r.top.percent)
        assertEquals(100, r.shares.sumOf { it.percent })
        assertEquals(0, r.totalWears)
        assertTrue(r.comment.contains("착용 기록을 남기면"))
    }

    @Test fun wearLogsShiftTheShare() {
        // 캐주얼 옷만 최근 4번 입음 → 보유 50% + 착용 50%
        val logs = listOf(
            WearLog("2026-10-10", listOf("d")), WearLog("2026-10-08", listOf("d")),
            WearLog("2026-10-05", listOf("d")), WearLog("2026-10-01", listOf("d"))
        )
        val r = StyleProfile.analyze(closet, logs, today = "2026-10-12")!!
        assertEquals("캐주얼", r.top.style)               // 0.5*0.2 + 0.5*1.0 = 60%
        assertEquals(60, r.top.percent)
        assertEquals(100, r.shares.sumOf { it.percent })
        assertEquals(4, r.totalWears)
        assertTrue(r.comment.contains("옷장에는 클래식 옷이 가장 많지만"))
    }

    @Test fun oldLogsAndDeletedItemsAreIgnored() {
        val logs = listOf(
            WearLog("2025-01-01", listOf("d")),          // 90일보다 오래됨
            WearLog("2026-10-11", listOf("deleted"))     // 지운 옷
        )
        val r = StyleProfile.analyze(closet, logs, today = "2026-10-12")!!
        assertEquals(0, r.totalWears)
        assertEquals("클래식", r.top.style)
    }

    @Test fun percentsAlwaysSumTo100() {
        val p = StyleProfile.toPercents(mapOf("a" to 1.0, "b" to 1.0, "c" to 1.0))
        assertEquals(100, p.values.sum())
        assertEquals(setOf(33, 34), p.values.toSet())
    }

    @Test fun keywordsComeFromTopStylesAndColor() {
        val r = StyleProfile.analyze(closet, emptyList(), today = "2026-10-12")!!
        assertTrue(r.keywords.contains("단정한"))     // 클래식
        assertTrue(r.keywords.contains("블랙 컬러"))  // 가장 많은 색
    }
}
