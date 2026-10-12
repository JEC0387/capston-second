package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog

/**
 * 스타일 분석 (마이페이지 > 스타일 분석 결과).
 *
 * 새 AI 없이, 이미 있는 데이터로 계산한다.
 * - 보유: 옷장 옷마다 붙은 스타일(StyleTagger — 사용자가 고른 스타일이 있으면 그 값)
 * - 착용: 최근 90일 착용 기록에서 입은 옷의 스타일 (입은 횟수만큼)
 * 착용 기록이 있으면 보유 50% + 착용 50%로 섞고, 없으면 보유만 쓴다.
 */
object StyleProfile {
    val STYLES = listOf("미니멀", "캐주얼", "클래식", "스트릿", "빈티지")
    const val WINDOW_DAYS = 90

    data class StyleShare(
        val style: String,
        val percent: Int,      // 합이 100이 되도록 반올림
        val owned: Int,        // 옷장에 있는 이 스타일 옷 수
        val worn: Int          // 최근 기간 동안 이 스타일 옷을 입은 횟수
    )

    data class Result(
        val shares: List<StyleShare>,   // percent 큰 순서
        val totalItems: Int,
        val totalWears: Int,
        val windowDays: Int,
        val keywords: List<String>,
        val comment: String
    ) {
        val top: StyleShare get() = shares.first()
    }

    private val KEYWORDS = mapOf(
        "미니멀" to listOf("깔끔한", "심플한", "모던한"),
        "캐주얼" to listOf("편안한", "자연스러운", "활동적인"),
        "클래식" to listOf("단정한", "세련된", "격식 있는"),
        "스트릿" to listOf("개성 있는", "힙한", "자유로운"),
        "빈티지" to listOf("레트로한", "따뜻한", "감성적인"),
    )

    private val DESCRIPTION = mapOf(
        "미니멀" to "군더더기 없는 깔끔한 미니멀 스타일이 중심이에요. 무채색 · 뉴트럴 기본템이 많아 어떤 옷과도 조합하기 쉬워요.",
        "캐주얼" to "편안하고 자연스러운 캐주얼 스타일이 중심이에요. 데일리로 활용하기 좋은 옷이 많아요.",
        "클래식" to "단정하고 세련된 클래식 스타일이 중심이에요. 셔츠 · 슬랙스 · 구두처럼 격식 있는 자리에도 어울리는 옷이 많아요.",
        "스트릿" to "개성 있는 스트릿 스타일이 중심이에요. 카고 · 후드처럼 편하면서 힙한 옷을 즐겨요.",
        "빈티지" to "따뜻한 감성의 빈티지 스타일이 중심이에요. 체크 · 가디건 같은 레트로한 옷이 포인트예요.",
    )

    /** 옷장이 비어 있으면 null */
    fun analyze(
        items: List<WardrobeItem>,
        logs: List<WearLog>,
        today: String = WearDates.today(),
        windowDays: Int = WINDOW_DAYS
    ): Result? {
        if (items.isEmpty()) return null
        val styleById = items.associate { it.id to StyleTagger.styleOf(it) }

        val owned = STYLES.associateWith { s -> styleById.values.count { it == s } }
        val ownedTotal = owned.values.sum()
        if (ownedTotal == 0) return null

        val recentLogs = logs.filter {
            runCatching { WearDates.daysBetween(it.date, today) in 0..windowDays }.getOrDefault(false)
        }
        val wornStyles = recentLogs.flatMap { log -> log.itemIds.mapNotNull { styleById[it] } }
        val worn = STYLES.associateWith { s -> wornStyles.count { it == s } }
        val wornTotal = worn.values.sum()

        val scores = STYLES.associateWith { s ->
            val o = owned.getValue(s).toDouble() / ownedTotal
            if (wornTotal == 0) o else 0.5 * o + 0.5 * worn.getValue(s).toDouble() / wornTotal
        }
        val percents = toPercents(scores)
        val shares = STYLES
            .map { StyleShare(it, percents.getValue(it), owned.getValue(it), worn.getValue(it)) }
            .sortedWith(compareByDescending<StyleShare> { it.percent }.thenBy { STYLES.indexOf(it.style) })

        return Result(
            shares = shares,
            totalItems = ownedTotal,
            totalWears = wornTotal,
            windowDays = windowDays,
            keywords = keywords(shares, items),
            comment = comment(shares, owned, worn, wornTotal, windowDays)
        )
    }

    /** 합이 정확히 100이 되도록 반올림 (최대 나머지 방식) */
    fun toPercents(scores: Map<String, Double>): Map<String, Int> {
        val total = scores.values.sum()
        if (total <= 0.0) return scores.mapValues { 0 }
        val raw = scores.mapValues { it.value / total * 100.0 }
        val floors = raw.mapValues { kotlin.math.floor(it.value).toInt() }.toMutableMap()
        var left = 100 - floors.values.sum()
        raw.entries.sortedByDescending { it.value - kotlin.math.floor(it.value) }.forEach { e ->
            if (left > 0) { floors[e.key] = floors.getValue(e.key) + 1; left-- }
        }
        return floors
    }

    private fun keywords(shares: List<StyleShare>, items: List<WardrobeItem>): List<String> {
        val result = mutableListOf<String>()
        shares.getOrNull(0)?.takeIf { it.percent > 0 }?.let { result += KEYWORDS.getValue(it.style) }
        shares.getOrNull(1)?.takeIf { it.percent > 0 }?.let { result += KEYWORDS.getValue(it.style).take(2) }
        // 가장 많은 색
        items.map { it.colorName }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?.let { result += "$it 컬러" }
        return result.distinct()
    }

    private fun comment(
        shares: List<StyleShare>,
        owned: Map<String, Int>,
        worn: Map<String, Int>,
        wornTotal: Int,
        windowDays: Int
    ): String {
        val top = shares.first()
        val parts = mutableListOf(DESCRIPTION.getValue(top.style))

        shares.getOrNull(1)?.takeIf { it.percent >= 15 }?.let {
            parts += "${it.style} 스타일도 ${it.percent}%로 함께 즐기고 있어요."
        }

        if (wornTotal == 0) {
            parts += "착용 기록을 남기면 실제로 자주 입는 스타일까지 반영해서 분석해 드려요."
        } else {
            // 같은 개수면 STYLES 순서에서 앞에 있는 스타일 (maxBy 는 처음 찾은 최댓값을 돌려줌)
            val ownedTop = STYLES.maxBy { owned.getValue(it) }
            val wornTop = STYLES.maxBy { worn.getValue(it) }
            parts += if (ownedTop != wornTop)
                "옷장에는 $ownedTop 옷이 가장 많지만, 최근 ${windowDays}일 동안은 $wornTop 옷을 가장 자주 입었어요."
            else
                "옷장 구성과 실제로 입는 옷이 모두 $ownedTop 쪽이라 취향이 뚜렷해요."
            // 갖고는 있는데 안 입는 스타일 (기록이 충분할 때만)
            if (wornTotal >= 5) {
                STYLES.firstOrNull { owned.getValue(it) >= 2 && worn.getValue(it) == 0 }?.let {
                    parts += "$it 옷 ${owned.getValue(it)}벌은 최근 ${windowDays}일 동안 한 번도 입지 않았어요. 코디 추천에서 활용해 보세요."
                }
            }
        }
        return parts.joinToString(" ")
    }
}
