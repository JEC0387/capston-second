package com.jeiu.fashionproject.ui.viewmodel

import com.jeiu.fashionproject.data.BodyRecord
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 측정 기록 그래프에서 고를 수 있는 항목 */
enum class BodyMetric(val label: String, val shortLabel: String, val isRatio: Boolean) {
    SHOULDER_HIP("어깨:엉덩이 비율", "어깨:엉덩이", true),
    WAIST_HIP("허리:엉덩이 비율", "허리:엉덩이", true),
    LEG("다리 길이 비율", "다리 비율", true),
    SHOULDER_CM("어깨 너비", "어깨 cm", false),
    WAIST_CM("허리 너비", "허리 cm", false),
    HIP_CM("엉덩이 너비", "엉덩이 cm", false);

    /** 이 기록에서 이 항목 값 (없거나 잘못된 값이면 null) */
    fun read(r: BodyRecord): Float? = when (this) {
        SHOULDER_HIP -> r.shoulderToHip
        WAIST_HIP -> r.waistToHip
        LEG -> r.legRatio
        SHOULDER_CM -> r.shoulderCm
        WAIST_CM -> r.waistCm
        HIP_CM -> r.hipCm
    }?.takeIf { it.isFinite() && it > 0f }

    fun format(v: Float): String =
        if (isRatio) String.format(Locale.US, "%.2f", v) else String.format(Locale.US, "%.1fcm", v)
}

/** 직전 기록과 비교한 변화 하나 */
data class MetricChange(
    val metric: BodyMetric,
    val before: Float,
    val after: Float,
    val significant: Boolean,   // 측정 오차보다 큰 변화인지
    val text: String            // 예: "허리:엉덩이 비율 −2% (0.86 → 0.84)"
)

/**
 * 측정 기록 분석 (계획 1 — 7. 측정 기록 & 변화). 화면과 분리한 순수 계산이라 단위 테스트가 가능하다.
 *
 * - 비율은 "몇 % 달라졌는지"(상대 변화), cm 는 "몇 cm 달라졌는지"로 비교한다.
 * - 촬영 자세 · 옷차림 때문에 생기는 작은 차이는 변화로 보지 않는다
 *   (비율 1% 미만, cm 0.5cm 미만 → "거의 같음").
 */
object BodyHistoryAnalyzer {
    const val RATIO_THRESHOLD_PERCENT = 1f
    const val CM_THRESHOLD = 0.5f

    /** 오래된 것 → 최근 것 순서로 정렬 (그래프용) */
    fun chronological(records: List<BodyRecord>): List<BodyRecord> =
        records.sortedWith(compareBy<BodyRecord> { it.measuredAt }.thenBy { it.id })

    /** 그래프에 찍을 점 (값이 있는 기록만, 오래된 것부터, 최근 maxPoints 개) */
    fun series(records: List<BodyRecord>, metric: BodyMetric, maxPoints: Int = 20): List<Pair<BodyRecord, Float>> =
        chronological(records).mapNotNull { r -> metric.read(r)?.let { r to it } }.takeLast(maxPoints)

    /** 가장 최근 기록과 그 직전 기록을 항목별로 비교 (값이 둘 다 있는 항목만) */
    fun compareLatest(records: List<BodyRecord>): List<MetricChange> {
        val sorted = chronological(records)
        if (sorted.size < 2) return emptyList()
        val before = sorted[sorted.size - 2]
        val after = sorted.last()
        return BodyMetric.values().mapNotNull { m ->
            val b = m.read(before) ?: return@mapNotNull null
            val a = m.read(after) ?: return@mapNotNull null
            change(m, b, a)
        }
    }

    fun change(metric: BodyMetric, before: Float, after: Float): MetricChange {
        return if (metric.isRatio) {
            val percent = (after - before) / before * 100f
            val rounded = percent.roundToInt()
            val significant = abs(percent) >= RATIO_THRESHOLD_PERCENT && rounded != 0
            val diff = if (significant) "${sign(rounded)}${abs(rounded)}%" else "변화 없음"
            MetricChange(metric, before, after, significant,
                "${metric.label} $diff (${metric.format(before)} → ${metric.format(after)})")
        } else {
            val delta = after - before
            val significant = abs(delta) >= CM_THRESHOLD
            val diff = if (significant) sign(delta) + String.format(Locale.US, "%.1fcm", abs(delta)) else "변화 없음"
            MetricChange(metric, before, after, significant,
                "${metric.label} $diff (${metric.format(before)} → ${metric.format(after)})")
        }
    }

    /** 한 줄 요약 — 예: "지난번보다 허리:엉덩이 비율 −2%, 허리 너비 −1.2cm" */
    fun summary(records: List<BodyRecord>): String {
        val sorted = chronological(records)
        if (sorted.isEmpty()) return "아직 측정 기록이 없어요. 체형 분석을 하면 기록이 쌓여요."
        if (sorted.size == 1) return "첫 기록이에요. 다음에 다시 측정하면 변화를 비교해 드려요."
        val changes = compareLatest(records).filter { it.significant }
        val typeChange = sorted[sorted.size - 2].bodyType.takeIf { it.isNotBlank() && it != sorted.last().bodyType }
            ?.let { "체형이 $it → ${sorted.last().bodyType}(으)로 바뀌었어요. " } ?: ""
        if (changes.isEmpty()) return typeChange + "지난번과 거의 같아요."
        val parts = changes.take(3).joinToString(", ") { c ->
            val m = c.metric
            if (m.isRatio) {
                val p = ((c.after - c.before) / c.before * 100f).roundToInt()
                "${m.shortLabel} ${sign(p)}${abs(p)}%"
            } else {
                val d = c.after - c.before
                "${m.label} ${sign(d)}${String.format(Locale.US, "%.1f", abs(d))}cm"
            }
        }
        return typeChange + "지난번보다 $parts"
    }

    /** 체형 판정이 몇 번씩 나왔는지 (많은 순) */
    fun bodyTypeCounts(records: List<BodyRecord>): List<Pair<String, Int>> =
        records.filter { it.bodyType.isNotBlank() }.groupingBy { it.bodyType }.eachCount()
            .toList().sortedByDescending { it.second }

    private fun sign(v: Int) = if (v > 0) "+" else if (v < 0) "−" else ""
    private fun sign(v: Float) = if (v > 0f) "+" else if (v < 0f) "−" else ""
}
