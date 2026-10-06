package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.WardrobeItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 색 조합 규칙 (계획 4 — 추천).
 *
 * 옷의 대표 색을 색상(H, 0~360도) · 선명도(S, 0~1) · 밝기(V, 0~1)로 나눠서
 * "어울리는 조합인지"를 점수로 매긴다. 점수 기준값은 모두 이 파일 위쪽에 모아 두어 쉽게 조정할 수 있다.
 */
object ColorHarmony {

    // ── 기준값 (코디를 보면서 조정) ───────────────────────────────────────
    private const val NEUTRAL_MAX_SATURATION = 0.2f   // 이보다 흐린 색은 뉴트럴 (베이지·데님 등은 이름으로도 판단)
    private const val DARK_MAX_BRIGHTNESS = 0.25f     // 이보다 어두우면 블랙 계열
    private const val VIVID_MIN_SATURATION = 0.5f     // 이보다 선명하면 원색 계열
    private const val VIVID_MIN_BRIGHTNESS = 0.45f
    private const val SAME_FAMILY_HUE = 30f           // 색상 차이가 이보다 작으면 같은 계열
    private const val CLASH_HUE = 60f                 // 원색 두 개의 색상 차이가 이보다 크면 충돌

    private const val SCORE_ALL_NEUTRAL = 2.0
    private const val SCORE_POINT_COLOR = 2.0
    private const val SCORE_TONE_ON_TONE = 2.0
    private const val SCORE_NEUTRAL_TONE_ON_TONE = 0.5
    private const val SCORE_CONTRAST = 1.0
    private const val SCORE_FLAT = -0.5
    private const val SCORE_TOO_MANY = -3.0
    private const val SCORE_CLASH = -3.0

    /** 뉴트럴로 보는 색 이름 (어디든 어울리는 색) */
    private val neutralNames = setOf("블랙", "화이트", "그레이", "베이지", "네이비")

    data class Hsv(val h: Float, val s: Float, val v: Float)

    /** 0xFFRRGGBB → 색상·선명도·밝기. (안드로이드 기능 없이 계산해서 PC 테스트에서도 동작) */
    fun hsv(colorValue: Long): Hsv {
        val r = ((colorValue shr 16) and 0xFF) / 255f
        val g = ((colorValue shr 8) and 0xFF) / 255f
        val b = (colorValue and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Hsv(h, if (mx == 0f) 0f else d / mx, mx)
    }

    /** 색만 보고 뉴트럴인지 (흐리거나 아주 어두운 색) */
    fun isNeutralColor(colorValue: Long): Boolean {
        val c = hsv(colorValue)
        return c.s < NEUTRAL_MAX_SATURATION || c.v < DARK_MAX_BRIGHTNESS
    }

    /** 옷이 뉴트럴인지 — 색 이름·데님 여부도 함께 본다 */
    fun isNeutral(item: WardrobeItem): Boolean =
        item.colorName in neutralNames || item.detailType == "청바지" || "데님" in item.name ||
            isNeutralColor(item.colorValue)

    private fun isVivid(item: WardrobeItem): Boolean {
        val c = hsv(item.colorValue)
        return c.s >= VIVID_MIN_SATURATION && c.v >= VIVID_MIN_BRIGHTNESS
    }

    /** 색상 차이 (원이라 350도와 10도는 20도 차이) */
    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    data class Result(val score: Double, val reasons: List<String>)

    /**
     * 코디 색 점수.
     * @param main 상의(원피스면 원피스) — 밝기 대비를 볼 때 기준
     * @param bottom 하의 (원피스 코디면 null)
     * @param all 코디에 들어간 옷 전부 (아우터·신발 포함)
     */
    fun evaluate(main: WardrobeItem, bottom: WardrobeItem?, all: List<WardrobeItem>): Result {
        var score = 0.0
        val reasons = mutableListOf<String>()

        // 1) 뉴트럴이 아닌 색이 몇 계열인지
        val colored = all.filterNot { isNeutral(it) }
        val families = mutableListOf<MutableList<WardrobeItem>>()
        colored.forEach { item ->
            val h = hsv(item.colorValue).h
            val family = families.find { f -> hueDistance(hsv(f.first().colorValue).h, h) < SAME_FAMILY_HUE }
            if (family != null) family += item else families += mutableListOf(item)
        }
        val vivid = colored.filter { isVivid(it) }
        val clash = vivid.any { a -> vivid.any { b -> hueDistance(hsv(a.colorValue).h, hsv(b.colorValue).h) > CLASH_HUE } }

        when {
            clash -> {
                score += SCORE_CLASH
                val (a, b) = vivid.take(2)
                reasons += "선명한 ${josa(a.colorName, "과", "와")} ${josa(b.colorName, "이", "가")} 부딪혀요"
            }
            families.size >= 3 -> {
                score += SCORE_TOO_MANY
                reasons += "색이 많아 조금 복잡해 보여요"
            }
            colored.isEmpty() -> {
                score += SCORE_ALL_NEUTRAL
                reasons += "${josa(colorPair(main, bottom, all), "은", "는")} 어디든 어울리는 차분한 조합이에요"
            }
            families.size == 1 && colored.size == 1 -> {
                score += SCORE_POINT_COLOR
                reasons += "뉴트럴 옷에 ${colored.first().colorName} 하나로 포인트를 줬어요"
            }
            families.size == 1 -> {
                score += SCORE_TONE_ON_TONE
                reasons += "${colored.map { it.colorName }.distinct().joinToString("·")} 같은 계열로 맞춘 톤온톤이에요"
            }
            // 2계열이면 가산·감점 없음
        }

        // 2) 뉴트럴끼리의 톤온톤 (베이지 + 브라운처럼 같은 따뜻한 계열, 밝기만 다름)
        if (colored.isEmpty()) {
            val tinted = all.filter { val c = hsv(it.colorValue); c.s >= 0.12f && c.v >= DARK_MAX_BRIGHTNESS }
            val toneOnTone = tinted.any { a ->
                tinted.any { b ->
                    a !== b && hueDistance(hsv(a.colorValue).h, hsv(b.colorValue).h) < SAME_FAMILY_HUE &&
                        abs(hsv(a.colorValue).v - hsv(b.colorValue).v) >= 0.1f
                }
            }
            if (toneOnTone) score += SCORE_NEUTRAL_TONE_ON_TONE
        }

        // 3) 상의·하의 밝기 대비
        if (bottom != null) {
            val dv = abs(hsv(main.colorValue).v - hsv(bottom.colorValue).v)
            when {
                dv in 0.2f..0.75f -> {
                    score += SCORE_CONTRAST
                    reasons += "상의와 하의의 밝기 차이가 있어 또렷해 보여요"
                }
                dv < 0.1f -> score += SCORE_FLAT
            }
        }
        return Result(score, reasons)
    }

    /** "블랙 + 베이지" — 상의·하의(없으면 다른 옷) 색 이름 */
    private fun colorPair(main: WardrobeItem, bottom: WardrobeItem?, all: List<WardrobeItem>): String {
        val names = (listOfNotNull(main, bottom) + all).map { it.colorName }.distinct().take(2)
        return if (names.size == 1) "${names.first()} 원톤" else names.joinToString(" + ")
    }
}

/**
 * 받침에 맞는 조사를 붙인다. 예) josa("블랙", "은", "는") → "블랙은", josa("베이지", "은", "는") → "베이지는"
 * 한글이 아닌 글자로 끝나면 받침 없는 쪽을 쓴다.
 */
fun josa(word: String, withBatchim: String, withoutBatchim: String): String {
    val last = word.lastOrNull() ?: return word
    val hasBatchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return word + if (hasBatchim) withBatchim else withoutBatchim
}
