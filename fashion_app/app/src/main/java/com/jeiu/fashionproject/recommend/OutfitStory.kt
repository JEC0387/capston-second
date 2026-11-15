package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 추천 코디 여러 벌의 제목 · 이유를 다양하게 만든다.
 *
 * 예전에는 4벌 모두 "선호하는 ○○ 스타일", "○○·○○ 어디든 어울리는 조합", "○도 날씨라 ○○를 더했어요"가
 * 색 이름만 바뀐 채 반복됐다. 여기서는
 *  - 개인화 이유(상황 · 선호 스타일 · 취향 학습 · 좋아하는 색 · 오래 안 입은 옷)는 앞 코디에서 이미 쓴 문장이면 빼고,
 *  - 날씨 문장은 기온 구간 · 비 · 습도와 코디의 핵심 옷(아우터 · 상의 · 하의 · 신발)으로 여러 표현 중 고르고,
 *  - 옷 종류마다 다른 한 줄(슬랙스 → 다리 라인, 스니커즈 → 오래 걸어도 편함 …)을 붙이고,
 *  - 색 문장도 앞에서 쓴 것과 겹치지 않게 다른 표현으로 바꾼다.
 * 체형 분석 이유는 맨 뒤에 그대로 둔다. 점수 · 순서는 바꾸지 않는다.
 */
object OutfitStory {

    data class Weather(val conditions: WeatherRules.Conditions, val humidity: Int? = null) {
        val temp: Int? get() = conditions.temp
        val rainy: Boolean get() = conditions.rainy
    }

    private const val MAX_REASONS = 3
    private const val MAX_PERSONAL = 2

    fun diversify(outfits: List<OutfitEngine.Outfit>, weather: Weather): List<OutfitEngine.Outfit> {
        val usedKeys = mutableSetOf<String>()
        val usedTitles = mutableSetOf<String>()
        return outfits.mapIndexed { index, outfit ->
            val body = outfit.reasons.lastOrNull()?.takeIf { it.startsWith("체형 분석 반영") }
            val base = if (body != null) outfit.reasons.dropLast(1) else outfit.reasons
            val parts = Parts.of(outfit, weather.conditions)
            val generic = setOfNotNull(parts.colorReason, parts.weatherReason)
            val personal = base.filter { it !in generic }
                .filter { index == 0 || it !in usedKeys }
                .take(MAX_PERSONAL)
                .map { it to it }

            val generated = buildList {
                weatherLines(parts, weather, index).firstOrNull { it.first !in usedKeys }?.let { add(it) }
                itemLines(parts).filter { it.first !in usedKeys }.take(2).forEach { add(it) }
                colorLines(parts).firstOrNull { it.first !in usedKeys }?.let { add(it) }
            }
            // 개인화 → 날씨 → 옷 → 색 순으로, 같은 문장 없이 최대 3줄
            val chosen = (personal + interleave(generated)).distinctBy { it.second }.take(MAX_REASONS)
            usedKeys += chosen.map { it.first }
            val title = title(parts, outfit.style, usedTitles)
            usedTitles += title
            outfit.copy(reasons = chosen.map { it.second } + listOfNotNull(body), title = title)
        }
    }

    /** 날씨 1줄 → 옷 1줄 → 색 1줄 → 나머지 옷 줄 순서 */
    private fun interleave(lines: List<Pair<String, String>>): List<Pair<String, String>> {
        val weather = lines.filter { it.first.startsWith("weather:") }
        val items = lines.filter { it.first.startsWith("item:") }
        val colors = lines.filter { it.first.startsWith("color:") }
        return weather.take(1) + items.take(1) + colors.take(1) + items.drop(1) + weather.drop(1) + colors.drop(1)
    }

    /** 코디 구성 */
    private class Parts(val outer: WardrobeItem?, val main: WardrobeItem, val bottom: WardrobeItem?, val shoe: WardrobeItem?,
                        val all: List<WardrobeItem>, val colorReason: String?, val weatherReason: String?,
                        val pattern: String?) {
        companion object {
            fun of(o: OutfitEngine.Outfit, c: WeatherRules.Conditions): Parts {
                val outer = o.items.firstOrNull { it.category == "아우터" }
                val main = o.items.firstOrNull { it.category == "상의" || it.category == "원피스" } ?: o.items.first()
                val bottom = o.items.firstOrNull { it.category == "하의" }
                val shoe = o.items.firstOrNull { it.category == "신발" }
                val color = ColorHarmony.evaluate(main, bottom, o.items)
                return Parts(outer, main, bottom, shoe, o.items,
                    color.reasons.firstOrNull()?.takeIf { color.score > 0 },
                    WeatherRules.reason(main, bottom, outer, c), o.pattern ?: color.pattern)
            }
        }
    }

    /** 이유 문장에 쓰는 짧은 이름 — 긴 상품명은 "색 + 종류"로 */
    fun shortName(item: WardrobeItem): String {
        val kind = SituationRules.kindOf(item)
        return if (item.name.length <= 10) item.name
        else listOf(item.colorName, kind).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun kindLabel(item: WardrobeItem): String = when (val k = SituationRules.kindOf(item)) {
        "긴팔 원피스", "반팔 원피스", "민소매 원피스" -> "원피스"
        "긴팔 상의" -> "긴팔 상의"
        else -> k
    }

    // ── 날씨 ──────────────────────────────────────────────────────────
    private fun weatherLines(p: Parts, w: Weather, index: Int): List<Pair<String, String>> {
        val t = w.temp
        val main = shortName(p.main)
        val outer = p.outer?.let { shortName(it) }
        val bottom = p.bottom?.let { shortName(it) }
        val shoe = p.shoe?.let { shortName(it) }
        val lines = mutableListOf<Pair<String, String>>()
        if (w.rainy) {
            shoe?.let { lines += "weather:rain-shoe" to "비 소식이 있어 젖어도 부담 없는 ${eul(it)} 신었어요" }
            if (p.bottom != null && !WeatherRules.isBright(p.bottom)) lines += "weather:rain-dark" to "비 오는 날이라 얼룩이 덜 보이는 어두운 ${eul(bottom!!)} 골랐어요"
            lines += "weather:rain-light" to "비가 와도 가볍게 움직일 수 있는 조합이에요"
        }
        if (t != null) {
            when {
                t <= 4 -> {
                    outer?.let {
                        lines += "weather:cold-outer" to "${t}도 한파라 ${ro(it)} 든든하게 감쌌어요"
                        lines += "weather:cold-layer" to "찬 바람 부는 ${t}도, ${eul(it)} 걸치고 ${eul(main)} 받쳐 입었어요"
                    }
                    lines += "weather:cold-main" to "추운 날이라 보온이 되는 ${eul(main)} 안에 입었어요"
                }
                t <= 11 -> {
                    outer?.let {
                        lines += "weather:chilly-outer" to "${t}도 쌀쌀한 날씨에 ${eul(it)} 걸치면 딱 좋아요"
                        lines += "weather:chilly-layer" to "${ro(it)} 체온은 지키고 실내에선 벗기 쉬워요"
                    }
                    lines += "weather:chilly-main" to "${t}도라 포근한 ${ro(main)} 체온을 지켜요"
                }
                t <= 19 -> {
                    outer?.let {
                        lines += "weather:mild-outer" to "아침저녁 선선한 ${t}도, ${ro(it)} 가볍게 레이어드했어요"
                        lines += "weather:mild-gap" to "일교차가 있는 날이라 ${eul(it)} 챙겼어요"
                    }
                    lines += "weather:mild-main" to "${t}도 선선한 날씨엔 ${main} 한 장이면 충분해요"
                }
                t <= 26 -> {
                    lines += "weather:warm-main" to "${t}도 활동하기 좋은 날씨라 가벼운 ${ro(main)} 맞췄어요"
                    bottom?.let { lines += "weather:warm-bottom" to "${t}도 포근한 날, ${ro(it)} 편하게 입기 좋아요" }
                }
                else -> {
                    lines += "weather:hot-main" to "${t}도 더운 날이라 통풍이 잘 되는 ${ro(main)} 골랐어요"
                    bottom?.let { lines += "weather:hot-bottom" to "더운 날씨에 ${ro(it)} 시원하게 입어요" }
                }
            }
            if ((w.humidity ?: 0) >= 75 && t >= 20) lines += "weather:humid" to "습도가 높아 몸에 달라붙지 않는 ${ro(main)} 골랐어요"
        }
        // 코디마다 다른 표현이 먼저 오도록 순서를 돌린다
        if (lines.isEmpty()) return lines
        val shift = index % lines.size
        return lines.drop(shift) + lines.take(shift)
    }

    // ── 옷 ────────────────────────────────────────────────────────────
    private fun itemLines(p: Parts): List<Pair<String, String>> =
        listOfNotNull(p.bottom, p.shoe, p.outer, p.main).mapNotNull { item ->
            val n = shortName(item)
            val line = when (SituationRules.kindOf(item)) {
                "셔츠" -> "${ro(n)} 단정한 인상을 더했어요"
                "블라우스" -> "${iga(n)} 부드럽고 단정한 분위기를 줘요"
                "니트" -> "${n}의 도톰한 질감이 계절감을 살려요"
                "맨투맨" -> "${ro(n)} 편안하면서도 깔끔하게 입었어요"
                "후드티" -> "${ro(n)} 캐주얼한 무드를 냈어요"
                "반팔티", "긴팔티" -> "기본 ${ira(n)} 어떤 하의와도 잘 어울려요"
                "폴로" -> "${ro(n)} 캐주얼과 단정함 사이를 맞췄어요"
                "슬랙스" -> "${iga(n)} 다리 라인을 깔끔하게 잡아 줘요"
                "청바지" -> "${ro(n)} 부담 없는 데일리 룩을 완성했어요"
                "면바지" -> "${iga(n)} 차분하면서도 편안해요"
                "트레이닝팬츠" -> "${ira(n)} 움직이기 편해요"
                "롱스커트" -> "${iga(n)} 여성스러운 실루엣을 만들어요"
                "미니스커트" -> "${ro(n)} 경쾌한 포인트를 줬어요"
                "반바지" -> "${ro(n)} 시원하고 가볍게 입어요"
                "스니커즈", "운동화" -> "${ira(n)} 오래 걸어도 편해요"
                "로퍼", "구두" -> "${ro(n)} 마무리해 단정함을 더했어요"
                "힐" -> "${ro(n)} 실루엣을 길어 보이게 했어요"
                "부츠" -> "${iga(n)} 발목까지 따뜻하게 지켜 줘요"
                "샌들" -> "${ro(n)} 발까지 시원하게"
                "코트" -> "${iga(n)} 차분하고 고급스러운 분위기를 내요"
                "패딩" -> "$n 하나면 한겨울도 든든해요"
                "자켓" -> "${ro(n)} 격식 있는 느낌을 더했어요"
                "가디건" -> "${eun(n)} 입고 벗기 쉬워 일교차에 좋아요"
                "점퍼" -> "${ro(n)} 활동적인 느낌을 더했어요"
                else -> if (item.category == "원피스") "$n 한 벌로 손쉽게 완성돼요" else null
            } ?: return@mapNotNull null
            "item:${SituationRules.kindOf(item)}" to line
        }

    // ── 색 ────────────────────────────────────────────────────────────
    private fun colorLines(p: Parts): List<Pair<String, String>> {
        val colors = p.all.map { it.colorName }.filter { it.isNotBlank() }.distinct()
        val lines = mutableListOf<Pair<String, String>>()
        p.colorReason?.let { lines += "color:${colors.sorted()}" to it }
        val top = p.main.colorName; val bottom = p.bottom?.colorName
        if (bottom != null && top.isNotBlank() && bottom.isNotBlank() && top != bottom) {
            lines += "color:pair:$top/$bottom" to "$top ${kindLabel(p.main)}에 $bottom ${kindLabel(p.bottom!!)}, 위아래 색 대비가 또렷해요"
        }
        when (p.pattern) {
            "무채색 조합" -> lines += "color:mono:${colors.sorted()}" to "${colors.joinToString("·")} 무채색이라 깔끔하고 세련돼요"
            "톤온톤" -> lines += "color:tone:${colors.sorted()}" to "${colors.joinToString("·")} 비슷한 톤으로 차분하게 맞췄어요"
            "포인트 컬러" -> colors.firstOrNull { it !in NEUTRAL }?.let {
                lines += "color:point:$it" to "${it} 컬러 하나로 코디에 생기를 줬어요"
            }
        }
        return lines
    }
    private val NEUTRAL = setOf("블랙", "화이트", "그레이", "베이지", "네이비", "아이보리", "브라운", "차콜")

    // ── 제목 ──────────────────────────────────────────────────────────
    /** "클래식 코디" 대신 옷에 맞는 제목 — 예: 단정한 블레이저 룩, 깔끔한 니트 룩 */
    private fun title(p: Parts, style: String, used: Set<String>): String {
        val anchor = p.outer ?: p.main
        val kind = kindLabel(anchor)
        val mood = MOODS[style] ?: "데일리"
        val candidates = listOfNotNull(
            "$mood $kind 룩",
            "${anchor.colorName} $kind 룩".takeIf { anchor.colorName.isNotBlank() },
            p.bottom?.let { "${kindLabel(p.main)} + ${kindLabel(it)} 룩" },
            "${anchor.colorName} ${kindLabel(p.main)} 레이어드 룩".takeIf { p.outer != null && anchor.colorName.isNotBlank() }
        )
        return candidates.firstOrNull { it !in used } ?: candidates.first()
    }
    private val MOODS = mapOf(
        "클래식" to "단정한", "포멀" to "단정한", "캐주얼" to "편안한", "미니멀" to "깔끔한", "스트릿" to "힙한",
        "스포티" to "활동적인", "빈티지" to "빈티지", "아메카지" to "빈티지", "댄디" to "댄디한", "유니크" to "포인트"
    )

    // ── 조사 ──────────────────────────────────────────────────────────
    private fun batchim(word: String): Int? {
        val last = word.lastOrNull() ?: return null
        if (last !in '가'..'힣') return null
        return (last - '가') % 28
    }
    private fun eul(w: String) = josa(w, "을", "를")
    private fun iga(w: String) = josa(w, "이", "가")
    private fun eun(w: String) = josa(w, "은", "는")
    private fun ira(w: String) = josa(w, "이라", "라")
    /** 로/으로 — 받침이 없거나 ㄹ 받침이면 "로" */
    private fun ro(w: String): String { val b = batchim(w); return w + if (b == null || b == 0 || b == 8) "로" else "으로" }
}
