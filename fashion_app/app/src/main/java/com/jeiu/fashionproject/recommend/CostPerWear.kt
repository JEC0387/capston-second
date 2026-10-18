package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer
import java.util.Locale

/**
 * 착용당 비용 (Cost per wear) — 옷 가격 ÷ 입은 횟수.
 * 옷 수정 화면에서 가격을 넣은 옷만 계산한다 (가격을 지어내지 않음).
 *  - 가성비 좋은 옷: 한 번 입을 때 드는 돈이 가장 적은 옷 (한 번 이상 입은 옷)
 *  - 본전 못 뽑은 옷: 한 번 이하로 입은 옷 중 비싼 순
 */
object CostPerWear {

    data class Entry(val item: WardrobeItem, val price: Int, val wears: Int) {
        /** 한 번 입을 때 드는 돈 (안 입었으면 가격 그대로) */
        val perWear: Int get() = if (wears <= 0) price else price / wears
    }

    data class Report(
        val pricedCount: Int,          // 가격을 넣은 옷 수
        val unpricedCount: Int,        // 가격을 안 넣은 옷 수
        val totalValue: Long,          // 가격 합계
        val averagePerWear: Int?,      // (입은 옷 가격 합) ÷ (입은 횟수 합)
        val bestValue: List<Entry>,
        val leastWorn: List<Entry>
    ) {
        val isEmpty: Boolean get() = pricedCount == 0
    }

    fun report(items: List<WardrobeItem>, logs: List<WearLog>, n: Int = 3): Report {
        val stats = WearAnalyzer.stats(logs)
        val owned = items.filter { it.isOwned }
        val priced = owned.mapNotNull { item ->
            item.price?.takeIf { it > 0 }?.let { Entry(item, it, stats[item.id]?.count ?: 0) }
        }
        val worn = priced.filter { it.wears > 0 }
        val wearSum = worn.sumOf { it.wears }
        return Report(
            pricedCount = priced.size,
            unpricedCount = owned.size - priced.size,
            totalValue = priced.sumOf { it.price.toLong() },
            averagePerWear = if (wearSum == 0) null else (worn.sumOf { it.price.toLong() } / wearSum).toInt(),
            bestValue = worn.sortedWith(compareBy<Entry> { it.perWear }.thenByDescending { it.wears }).take(n),
            leastWorn = priced.filter { it.wears <= 1 }.sortedWith(compareByDescending<Entry> { it.price }.thenBy { it.wears }).take(n)
        )
    }

    /** 12,900원 */
    fun won(v: Long): String = String.format(Locale.KOREA, "%,d원", v)
    fun won(v: Int): String = won(v.toLong())

    /** 사용자가 입력한 가격 글자 → 원 (숫자 · 쉼표만, 0 이하 · 1억 원 넘으면 null) */
    fun parsePrice(input: String): Int? =
        input.filter { it.isDigit() }.takeIf { it.isNotEmpty() && it.length <= 9 }?.toIntOrNull()?.takeIf { it in 1..100_000_000 }
}
