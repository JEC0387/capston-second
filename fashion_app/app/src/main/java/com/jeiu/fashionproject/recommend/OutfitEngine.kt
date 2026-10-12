package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.TasteScores
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WeatherInfo
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.data.isRainy
import com.jeiu.fashionproject.data.isSnow
import com.jeiu.fashionproject.ui.viewmodel.WearAnalyzer
import kotlin.math.abs

/**
 * 코디 조합 엔진 (계획 4 — 추천).
 *
 * AI 학습 없이 "규칙 + 점수"로 옷장 옷을 조합한다. 순서:
 *  ① 걸러내기 — 오늘 날씨·계절(+ 상황)에 안 맞는 옷 빼기
 *  ② 조합 만들기 — 상의+하의(또는 원피스) + 신발 (+ 필요하면 아우터)
 *  ③ 점수 매기기 — 색 조합 + 날씨 + 상황 + 착용 기록 + 스타일 + 내 취향(스타일 · 색 · 종류 · 조합 방식)
 *  ④ 고르기 — 점수 높은 순으로, 같은 상의·하의가 반복되지 않게 골고루
 * 옷이 모자라면 고정 코디로 대신하지 않고 무엇이 부족한지 알려준다.
 */
object OutfitEngine {

    // ── 점수 기준값 ──────────────────────────────────────────────────────
    private const val RECENTLY_WORN_DAYS = 2      // 어제·그저께 입은 옷은 감점
    private const val RECENTLY_WORN_SCORE = -1.5
    private const val LONG_UNWORN_DAYS = 30       // 한 달 넘게 안 입은 옷은 가산점
    private const val LONG_UNWORN_SCORE = 1.0
    private const val TASTE_MAX_SCORE = 1.5       // 내 취향 스타일 점수의 최댓값
    private const val STYLE_UNITY_SCORE = 0.5     // 옷 스타일이 대부분 같으면
    private const val STYLE_CLASH_SCORE = -1.0    // 클래식 + 스트릿처럼 안 어울리는 스타일이 섞이면
    // 취향 확장 (4단계) — 평가가 TASTE_FULL_COUNT번 쌓일 때까지는 약하게 반영한다
    private const val TASTE_COLOR_MAX = 1.0
    private const val TASTE_KIND_MAX = 1.0
    private const val TASTE_PATTERN_MAX = 0.5
    private const val TASTE_FULL_COUNT = 10

    /**
     * 코디 1벌.
     * @param items 보여줄 순서대로 (아우터, 상의/원피스, 하의, 신발)
     */
    data class Outfit(
        val items: List<WardrobeItem>,
        val score: Double,
        val reasons: List<String>,
        val style: String,
        val pattern: String? = null   // 색 조합 방식 (무채색 조합 / 톤온톤 / 포인트 컬러) — 취향 학습에 사용
    ) {
        val id: String get() = items.joinToString("+") { it.id }
    }

    sealed class Result {
        /** @param notice 코디 위에 따로 알려줄 말 (예: 오늘 날씨에 맞는 겉옷이 없어요) */
        data class Ok(val outfits: List<Outfit>, val notice: String? = null) : Result()
        data class NotEnough(val message: String) : Result()
    }

    fun recommend(
        items: List<WardrobeItem>,
        weather: WeatherInfo?,
        logs: List<WearLog>,
        styleScores: Map<String, Int>,
        situation: Situation? = null,
        today: String = WearDates.today(),
        count: Int = 4,
        needOuter: Boolean = false,  // 일교차가 큰 날처럼 낮 기온과 상관없이 겉옷을 더해야 할 때
        taste: TasteScores = TasteScores()   // 색 · 종류 · 조합 방식 취향 (4단계)
    ): Result {
        val conditions = WeatherRules.Conditions(
            temp = weather?.temp,
            rainy = weather != null && (weather.isRainy() || weather.isSnow()),
            season = WearAnalyzer.seasonOf(today)
        )

        // 옷장에 (이 상황에 맞는) 옷이 아예 없는 종류가 있으면 안내
        val forSituation = items.filter { SituationRules.allows(it, situation) }
        missingCategories(forSituation).takeIf { it.isNotEmpty() }?.let { missing ->
            return Result.NotEnough(
                if (situation == null) "코디를 만들려면 ${amountText(missing)} 필요해요"
                else "${situation.label} 코디를 만들려면 ${hintText(missing, situation)} 필요해요"
            )
        }

        // ① 걸러내기
        val usable = forSituation.filter { WeatherRules.allows(it, conditions) }
        missingCategories(usable).takeIf { it.isNotEmpty() }?.let { missing ->
            return Result.NotEnough(
                if (situation == null) "옷장에 오늘 날씨·계절에 맞는 옷이 부족해요.\n${amountText(missing)} 더 있으면 코디를 만들 수 있어요"
                else "옷장에 오늘 날씨·계절에 맞는 ${situation.label}용 ${hintText(missing, situation)} 없어요"
            )
        }

        val tops = usable.filter { it.category == "상의" }
        val bottoms = usable.filter { it.category == "하의" }
        val dresses = usable.filter { it.category == "원피스" }
        val shoes = usable.filter { it.category == "신발" }
        val outers = usable.filter { it.category == "아우터" }

        // ② 조합 만들기
        val bases: List<Pair<WardrobeItem, WardrobeItem?>> =
            tops.flatMap { t -> bottoms.map { b -> t to b } } + dresses.map { it to null }
        val outerNeed = if (needOuter && outers.isNotEmpty()) WeatherRules.OuterNeed.REQUIRED else WeatherRules.outerNeed(conditions)
        val outerOptions: List<WardrobeItem?> = when (outerNeed) {
            WeatherRules.OuterNeed.NONE -> listOf(null)
            WeatherRules.OuterNeed.OPTIONAL -> listOf(null) + outers
            WeatherRules.OuterNeed.REQUIRED -> outers.ifEmpty { listOf(null) }
        }
        val notice = if (outerNeed == WeatherRules.OuterNeed.REQUIRED && outers.isEmpty())
            "오늘은 겉옷이 필요한 날씨인데, 옷장에 맞는 겉옷이 없어요" else null

        // ③ 점수 매기기
        val stats = WearAnalyzer.stats(logs)
        val candidates = bases.flatMap { (main, bottom) ->
            shoes.flatMap { shoe ->
                outerOptions.map { outer -> score(main, bottom, shoe, outer, conditions, situation, stats, styleScores, taste, today) }
            }
        }

        // ④ 골고루 고르기
        return Result.Ok(pickDiverse(candidates.sortedByDescending { it.score }, count), notice)
    }

    // ── "이것만 사면" 계산용 (5단계) ─────────────────────────────────────

    /**
     * 계절 대표 날씨 — 날씨에 따라 매일 결과가 바뀌지 않도록 계절 기준으로 볼 때 쓴다.
     * 봄·가을은 16도(겉옷을 입는 날씨), 여름 27도, 겨울 3도로 본다.
     */
    fun seasonConditions(today: String = WearDates.today()): WeatherRules.Conditions {
        val season = WearAnalyzer.seasonOf(today)
        val temp = when (season) {
            "여름" -> 27
            "겨울" -> 3
            else -> 16
        }
        return WeatherRules.Conditions(temp, rainy = false, season = season)
    }

    /**
     * 가능한 조합 전부와 점수 (고르기 전). 착용 기록 · 취향은 빼고 색 · 날씨 · 상황 · 스타일만 본다.
     * 옷이 모자라 코디를 만들 수 없으면 빈 목록.
     */
    fun scoreAll(
        items: List<WardrobeItem>,
        conditions: WeatherRules.Conditions,
        situation: Situation? = null,
        today: String = WearDates.today()
    ): List<Outfit> {
        val usable = items.filter { SituationRules.allows(it, situation) && WeatherRules.allows(it, conditions) }
        if (missingCategories(usable).isNotEmpty()) return emptyList()
        val outers = usable.filter { it.category == "아우터" }
        val outerOptions: List<WardrobeItem?> = when (WeatherRules.outerNeed(conditions)) {
            WeatherRules.OuterNeed.NONE -> listOf(null)
            WeatherRules.OuterNeed.OPTIONAL -> listOf(null) + outers
            WeatherRules.OuterNeed.REQUIRED -> outers.ifEmpty { listOf(null) }
        }
        val tops = usable.filter { it.category == "상의" }
        val bottoms = usable.filter { it.category == "하의" }
        val bases = tops.flatMap { t -> bottoms.map { b -> t to b } } + usable.filter { it.category == "원피스" }.map { it to null }
        return bases.flatMap { (main, bottom) ->
            usable.filter { it.category == "신발" }.flatMap { shoe ->
                outerOptions.map { outer ->
                    score(main, bottom, shoe, outer, conditions, situation, emptyMap(), emptyMap(), TasteScores(), today, withHistory = false)
                }
            }
        }
    }

    /** 최소 조건: (상의 1벌 + 하의 1벌) 또는 원피스 1벌, 그리고 신발 1켤레. 모자란 대분류를 돌려준다. */
    private fun missingCategories(items: List<WardrobeItem>): List<String> {
        val hasDress = items.any { it.category == "원피스" }
        return listOf("상의", "하의", "신발").filter { cat ->
            items.none { it.category == cat } && (cat == "신발" || !hasDress)
        }
    }

    /** ["하의", "신발"] → "하의가 1벌 이상, 신발이 1켤레 이상" */
    private fun amountText(missing: List<String>): String {
        val clothes = missing.filter { it != "신발" }
        return listOfNotNull(
            clothes.takeIf { it.isNotEmpty() }?.let { "${josa(it.joinToString("·"), "이", "가")} 1벌 이상" },
            "신발이 1켤레 이상".takeIf { "신발" in missing }
        ).joinToString(", ")
    }

    /** ["하의", "신발"] + 운동 → "트레이닝팬츠나 반바지, 운동화가" */
    private fun hintText(missing: List<String>, situation: Situation): String =
        josa(missing.joinToString(", ") { SituationRules.hintFor(it, situation) }, "이", "가")

    private fun score(
        main: WardrobeItem,
        bottom: WardrobeItem?,
        shoe: WardrobeItem,
        outer: WardrobeItem?,
        c: WeatherRules.Conditions,
        situation: Situation?,
        stats: Map<String, WearAnalyzer.WearStat>,
        styleScores: Map<String, Int>,
        taste: TasteScores,
        today: String,
        withHistory: Boolean = true   // false면 착용 기록 점수를 빼고 계산 ("이것만 사면"용)
    ): Outfit {
        val all = listOfNotNull(outer, main, bottom, shoe)
        var score = 0.0

        // 색 조합
        val color = ColorHarmony.evaluate(main, bottom, all)
        score += color.score

        // 날씨
        score += all.sumOf { WeatherRules.itemScore(it, c) }
        val weatherReason = WeatherRules.reason(main, bottom, outer, c)

        // 상황 (선호 옷·포인트 색·레이어드)
        score += SituationRules.score(all, outer, situation)

        // 착용 기록 — 최근에 입은 옷은 감점, 오래 안 입은 옷은 가산점
        var wearScore = 0.0
        var unwornPick: Pair<WardrobeItem, Int>? = null
        if (withHistory) all.forEach { item ->
            val lastWorn = stats[item.id]?.lastWorn
            if (lastWorn != null && WearDates.daysBetween(lastWorn, today) in 0..RECENTLY_WORN_DAYS) {
                wearScore += RECENTLY_WORN_SCORE
            }
            val since = listOfNotNull(lastWorn, WearDates.fromMillis(item.addedAt)).max()
            val days = WearDates.daysBetween(since, today)
            if (days >= LONG_UNWORN_DAYS) {
                wearScore += LONG_UNWORN_SCORE
                if (days > (unwornPick?.second ?: -1)) unwornPick = item to days
            }
        }
        score += wearScore.coerceAtMost(LONG_UNWORN_SCORE * 1.5)

        // 스타일 — 코디의 스타일은 옷 스타일의 다수결 (표가 같으면 상의·원피스의 스타일)
        val styles = all.map { StyleTagger.styleOf(it) }
        val counts = styles.groupingBy { it }.eachCount()
        val mainStyle = StyleTagger.styleOf(main)
        val style = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key == mainStyle })
            .first().key
        if (styles.count { it == style } >= all.size - 1 && all.size >= 3) score += STYLE_UNITY_SCORE
        if ("클래식" in styles && "스트릿" in styles) score += STYLE_CLASH_SCORE

        // 내 취향 (취향 카드·코디 👍👎로 쌓인 스타일 점수) — 옷마다 스타일 점수를 매겨 평균
        val maxTaste = styleScores.values.maxOfOrNull { abs(it) } ?: 0
        val tasteWeight = situation?.let { SituationRules.rule(it).tasteMultiplier } ?: 1.0
        val styleTaste = if (maxTaste > 0) tasteWeight * TASTE_MAX_SCORE * styles.map { styleScores[it] ?: 0 }.average() / maxTaste else 0.0
        val favorite = styleScores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        score += styleTaste

        // 내 취향 확장 — 색 · 종류 · 조합 방식. 평가 · 착용 기록이 적을 땐 약하게 반영한다
        val confidence = ((taste.ratedCount + taste.wornCount).toDouble() / TASTE_FULL_COUNT).coerceAtMost(1.0)
        val colorNames = all.map { it.colorName }.distinct()
        val kinds = all.map { SituationRules.kindOf(it) }.distinct()
        val colorTaste = TASTE_COLOR_MAX * relative(taste.colors, colorNames)
        val kindTaste = TASTE_KIND_MAX * relative(taste.kinds, kinds)
        val patternTaste = TASTE_PATTERN_MAX * relative(taste.patterns, listOfNotNull(color.pattern))
        score += tasteWeight * confidence * (colorTaste + kindTaste + patternTaste)
        // 이유에 쓸 "좋아하는 색" · "자주 고르는 종류" (취향 상위 2개 안에 드는 것, 상의·하의를 먼저 봄)
        val byImportance = listOfNotNull(main, bottom, shoe, outer)
        val likedColor = byImportance.map { it.colorName }.firstOrNull { it in taste.top(taste.colors, 2) }
        val likedKind = byImportance.map { SituationRules.kindOf(it) }.firstOrNull { it in taste.top(taste.kinds, 2) }

        // 이유 — 좋은 점만 최대 3줄
        val reasons = buildList {
            situation?.let { add(SituationRules.rule(it).reason) }
            if (color.score > 0) addAll(color.reasons.take(1))
            weatherReason?.let { add(it) }
            if (confidence >= 0.3) {
                likedColor?.let { add("좋아하는 $it 색이 들어 있어요") }
                likedKind?.let { add("자주 고르는 ${josa(it, "이에요", "예요")}") }
            }
            if (style == favorite && styleTaste >= TASTE_MAX_SCORE * 0.3) add("평소 좋아하는 $style 스타일이에요")
            unwornPick?.let { (item, days) ->
                val period = if (days >= 60) "${days / 30}개월째" else "${days}일째"
                add("$period 안 입은 ${josa(item.name, "을", "를")} 꺼내 봤어요")
            }
        }.take(3)

        return Outfit(all, score, reasons, style, color.pattern)
    }

    /**
     * 고른 항목들의 취향 점수 합을 가장 큰 점수로 나눈 값 (−1 ~ 1로 자름). 점수가 없으면 0.
     * 평균을 쓰면 좋아하는 옷 하나가 다른 옷들에 묻혀 효과가 거의 없어서 합을 쓴다.
     */
    private fun relative(scores: Map<String, Int>, keys: List<String>): Double {
        val max = scores.values.maxOfOrNull { abs(it) } ?: 0
        if (max == 0 || keys.isEmpty()) return 0.0
        return (keys.sumOf { scores[it] ?: 0 }.toDouble() / max).coerceIn(-1.0, 1.0)
    }

    /**
     * 점수 순으로 보면서 같은 상의·하의가 겹치지 않게 고른다.
     * 옷이 적어 다 못 채우면 조건을 조금씩 풀어서 (상의나 하의 하나만 겹쳐도 OK) 채운다.
     */
    private fun pickDiverse(sorted: List<Outfit>, count: Int): List<Outfit> {
        val picked = mutableListOf<Outfit>()
        fun mainOf(o: Outfit) = o.items.first { it.category == "상의" || it.category == "원피스" }
        fun bottomOf(o: Outfit) = o.items.firstOrNull { it.category == "하의" }
        fun basesPicked() = picked.map { mainOf(it) to bottomOf(it) }

        // 1차: 상의(원피스)도 하의도 새로운 코디만
        for (o in sorted) {
            if (picked.size >= count) break
            val usedMains = picked.map { mainOf(it).id }
            val usedBottoms = picked.mapNotNull { bottomOf(it)?.id }
            if (mainOf(o).id !in usedMains && (bottomOf(o)?.id ?: "") !in usedBottoms) picked += o
        }
        // 2차: 상의·하의 조합이 새로우면 OK
        for (o in sorted) {
            if (picked.size >= count) break
            if ((mainOf(o) to bottomOf(o)) !in basesPicked()) picked += o
        }
        return picked.sortedByDescending { it.score }
    }
}
