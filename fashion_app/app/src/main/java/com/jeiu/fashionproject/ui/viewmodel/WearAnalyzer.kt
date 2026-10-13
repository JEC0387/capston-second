package com.jeiu.fashionproject.ui.viewmodel

import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import java.util.Calendar

/**
 * 착용 기록 계산기 — 기록 목록만 보고 계산한다(저장은 하지 않음).
 * 착용 횟수·마지막 착용일·TOP 5, 그리고 "3개월 넘게 안 입은 옷"(계절 제외 규칙 포함).
 */
object WearAnalyzer {

    data class WearStat(val count: Int, val lastWorn: String?)   // lastWorn: "yyyy-MM-dd" 또는 null(안 입음)

    /** 옷 id → 착용 횟수 · 마지막 착용일 */
    fun stats(logs: List<WearLog>): Map<String, WearStat> {
        val result = mutableMapOf<String, WearStat>()
        logs.forEach { log ->
            log.itemIds.forEach { id ->
                val prev = result[id]
                result[id] = WearStat(
                    count = (prev?.count ?: 0) + 1,
                    lastWorn = listOfNotNull(prev?.lastWorn, log.date).max()
                )
            }
        }
        return result
    }

    fun statOf(logs: List<WearLog>, itemId: String): WearStat = stats(logs)[itemId] ?: WearStat(0, null)

    /** 가장 많이 입은 옷 TOP N (옷장에 남아 있는 옷만, 횟수가 같으면 최근에 입은 옷 먼저) */
    fun topWorn(logs: List<WearLog>, items: List<WardrobeItem>, n: Int = 5): List<Pair<WardrobeItem, WearStat>> {
        val stats = stats(logs)
        return items.mapNotNull { item -> stats[item.id]?.let { item to it } }
            .sortedWith(compareByDescending<Pair<WardrobeItem, WearStat>> { it.second.count }
                .thenByDescending { it.second.lastWorn })
            .take(n)
    }

    /** 해당 월("yyyy-MM")에 기록한 날 수 */
    fun daysLoggedIn(logs: List<WearLog>, yearMonth: String): Int =
        logs.count { it.date.startsWith(yearMonth) }

    // ── 5단계: 안 입는 옷 ─────────────────────────────────────────────────

    const val UNWORN_DAYS = 90

    /** 월로 정하는 지금 계절: 3~5월 봄, 6~8월 여름, 9~11월 가을, 12~2월 겨울 */
    fun seasonOf(date: String): String = when (WearDates.toCalendar(date).get(Calendar.MONTH) + 1) {
        in 3..5 -> "봄"
        in 6..8 -> "여름"
        in 9..11 -> "가을"
        else -> "겨울"
    }

    /** 옷의 계절(여름/겨울/봄·가을/사계절)이 지금 계절에 맞는지. 계절이 없는 옷은 사계절로 본다. */
    fun inSeason(itemSeason: String?, nowSeason: String): Boolean = when (itemSeason) {
        "여름" -> nowSeason == "여름"
        "겨울" -> nowSeason == "겨울"
        "봄·가을" -> nowSeason == "봄" || nowSeason == "가을"
        else -> true
    }

    data class Unworn(
        val item: WardrobeItem,
        val lastWorn: String?,      // null이면 한 번도 안 입음
        val addedDate: String,
        val days: Int               // 기준일로부터 지난 날 수
    )

    data class UnwornResult(
        val unworn: List<Unworn>,                  // 오래 안 입은 순
        val excludedBySeason: List<WardrobeItem>,  // 오래 안 입었지만 지금 계절에 안 맞아 뺀 옷
        val season: String
    )

    /**
     * 3개월(90일) 넘게 안 입은 옷.
     * 기준일 = 마지막 착용일 · 옷장 등록일 · [계속 보관]을 누른 날 중 가장 최근 날짜.
     * (한 번도 안 입었으면 등록일, [계속 보관]을 누르면 그날부터 다시 90일을 센다)
     */
    fun unworn(items: List<WardrobeItem>, logs: List<WearLog>, today: String = WearDates.today()): UnwornResult {
        val stats = stats(logs)
        val season = seasonOf(today)
        val unworn = mutableListOf<Unworn>()
        val excluded = mutableListOf<WardrobeItem>()
        items.forEach { item ->
            val lastWorn = stats[item.id]?.lastWorn
            val addedDate = WearDates.fromMillis(item.addedAt)
            val base = listOfNotNull(lastWorn, addedDate, item.keptAt?.let { WearDates.fromMillis(it) }).max()
            val days = WearDates.daysBetween(base, today)
            if (days < UNWORN_DAYS) return@forEach
            if (inSeason(item.season, season)) unworn += Unworn(item, lastWorn, addedDate, days)
            else excluded += item
        }
        return UnwornResult(unworn.sortedByDescending { it.days }, excluded, season)
    }
}
