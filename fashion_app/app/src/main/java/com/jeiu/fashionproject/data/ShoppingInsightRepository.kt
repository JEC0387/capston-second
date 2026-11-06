package com.jeiu.fashionproject.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 월별 클릭 추이 한 점. ratio는 해당 기간 내 최고치를 100으로 둔 상대값. */
data class MonthlyClickTrend(val month: String, val ratio: Double)

object ShoppingInsightRepository {
    /** Server-side NAVER API HUB credential proxy. */
    suspend fun fashionClothingMonthlyTrend(): List<MonthlyClickTrend> = withContext(Dispatchers.IO) {
        val body = BackendApi.getJson("/api/naver/shopping-insight")
            ?: throw IllegalStateException("쇼핑인사이트를 불러오지 못했어요. 서버 연결을 확인해주세요.")
        val results = org.json.JSONObject(body).getJSONArray("results")
        if (results.length() == 0) throw IllegalStateException("쇼핑인사이트 응답에 결과가 없어요.")
        val data = results.getJSONObject(0).getJSONArray("data")
        (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            MonthlyClickTrend(item.getString("period"), item.getDouble("ratio"))
        }
    }
}
