package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import java.util.Calendar

/**
 * 예보 미리 알림 문구 (계획 4 — 3단계, 앱 안에서만 표시).
 *
 *  전날보다 5도 이상 추움 → "내일은 오늘보다 6도 낮아요, 베이지 블레이저 챙기세요"
 *  전날보다 5도 이상 더움 → "내일은 더워져요, 반팔 코디를 준비했어요"
 *  일교차 10도 이상      → "아침저녁으로 쌀쌀해요, 겉옷을 챙기세요"
 *  비 올 확률 60% 이상   → "내일 비 소식이 있어요, 우산 챙기고 밝은 하의는 피하세요"
 */
object ForecastAlerts {

    private const val BIG_CHANGE = 5      // 이만큼 기온이 바뀌면 알림
    private const val BIG_RANGE = 10      // 일교차가 이 이상이면 알림

    private val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    /** "오늘" / "내일" / "모레" / "목요일" */
    fun dayLabel(date: String, today: String = WearDates.today()): String =
        when (WearDates.daysBetween(today, date)) {
            0 -> "오늘"
            1 -> "내일"
            2 -> "모레"
            else -> weekdays[WearDates.toCalendar(date).get(Calendar.DAY_OF_WEEK) - 1] + "요일"
        }

    /** 날씨 띠에 쓰는 짧은 이름: "오늘" / "내일" / "목" */
    fun shortLabel(date: String, today: String = WearDates.today()): String =
        when (WearDates.daysBetween(today, date)) {
            0 -> "오늘"
            1 -> "내일"
            else -> weekdays[WearDates.toCalendar(date).get(Calendar.DAY_OF_WEEK) - 1]
        }

    /** 예보 하루치 코디 계획 — 그날 코디 + 알림 문구 */
    data class DayPlan(val day: DayForecast, val result: OutfitEngine.Result, val alerts: List<String>)

    /**
     * [days]의 [index]번째 날 코디와 알림을 만든다.
     * 기온 비교: 내일은 오늘(지금 기온과 오늘 예보 중 높은 값), 그 뒤로는 전날 낮 기온과 비교한다.
     */
    fun plan(
        days: List<DayForecast>,
        index: Int,
        currentTemp: Int?,
        items: List<WardrobeItem>,
        logs: List<com.jeiu.fashionproject.data.WearLog>,
        styleScores: Map<String, Int>,
        situation: Situation? = null,
        count: Int = 4,
        today: String = WearDates.today()
    ): DayPlan {
        val day = days[index]
        // 일교차가 크면 "겉옷을 챙기세요" 알림과 맞도록 코디에도 겉옷을 더한다
        val result = OutfitEngine.recommend(
            items, day.toWeatherInfo(), logs, styleScores, situation,
            today = day.date, count = count, needOuter = day.maxTemp - day.minTemp >= BIG_RANGE
        )
        val prevDay = days.getOrNull(index - 1)
        val previousTemp = when {
            dayLabel(day.date, today) == "내일" ->
                listOfNotNull(currentTemp, prevDay?.takeIf { it.date == today }?.dayTemp).maxOrNull()
            else -> prevDay?.dayTemp
        }
        val outer = (result as? OutfitEngine.Result.Ok)?.outfits?.firstOrNull()?.items?.firstOrNull { it.category == "아우터" }
        return DayPlan(day, result, alerts(day, previousTemp, outer, today))
    }

    /**
     * 그날 알림 문구들 (없으면 빈 목록).
     * @param previousTemp 비교할 전날 기온 (내일이면 오늘 기온). null이면 기온 변화는 비교하지 않는다
     * @param outer 그날 추천 코디의 겉옷 — "○○ 챙기세요"에 쓴다
     */
    fun alerts(
        day: DayForecast,
        previousTemp: Int?,
        outer: WardrobeItem?,
        today: String = WearDates.today()
    ): List<String> {
        val label = dayLabel(day.date, today)
        val prevLabel = if (label == "내일") "오늘" else "전날"
        val result = mutableListOf<String>()

        if (previousTemp != null && label != "오늘") {
            val diff = day.dayTemp - previousTemp
            when {
                diff <= -BIG_CHANGE ->
                    result += "${josa(label, "은", "는")} ${prevLabel}보다 ${-diff}도 낮아요, ${outer?.name ?: "겉옷"} 챙기세요"
                diff >= BIG_CHANGE ->
                    result += if (day.dayTemp >= 23) "${josa(label, "은", "는")} 더워져요, 반팔 코디를 준비했어요"
                    else "${josa(label, "은", "는")} ${prevLabel}보다 ${diff}도 높아요, 한 겹 가볍게 입으세요"
            }
        }
        if (day.maxTemp - day.minTemp >= BIG_RANGE) {
            result += "아침저녁으로 쌀쌀해요 (최저 ${day.minTemp}도), 겉옷을 챙기세요"
        }
        if (day.rainChance >= DayForecast.RAINY_CHANCE) {
            result += "$label 비 소식이 있어요 (${day.rainChance}%), 우산 챙기고 밝은 하의는 피하세요"
        }
        return result
    }
}
