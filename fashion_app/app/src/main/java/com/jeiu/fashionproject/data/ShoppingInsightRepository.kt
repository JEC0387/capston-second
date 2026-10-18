package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 월별 클릭 추이 한 점. ratio는 해당 기간 내 최고치를 100으로 둔 상대값. */
data class MonthlyClickTrend(val month: String, val ratio: Double)

object ShoppingInsightRepository {

    /** 데이터랩 쇼핑인사이트 카테고리 코드 (예시 문서 기준). */
    private const val CATEGORY_FASHION_CLOTHING = "50000000"

    private val api: ShoppingInsightApi by lazy {
        NaverApiHubClient.retrofit.create(ShoppingInsightApi::class.java)
    }

    /** 패션의류 카테고리의 최근 1년, 월 단위 클릭 추이. */
    suspend fun fashionClothingMonthlyTrend(): List<MonthlyClickTrend> = withContext(Dispatchers.IO) {
        if (BuildConfig.NAVER_API_KEY_ID.isBlank() || BuildConfig.NAVER_API_KEY.isBlank()) {
            throw IllegalStateException(
                "NAVER API HUB 키가 설정되지 않았어요.\nlocal.properties에 NAVER_API_KEY_ID / NAVER_API_KEY를 넣어주세요."
            )
        }

        val (startDate, endDate) = lastYearRange()
        val request = ShoppingCategoryRequest(
            startDate = startDate,
            endDate = endDate,
            timeUnit = "month",
            category = listOf(CategoryParam(name = "패션의류", param = listOf(CATEGORY_FASHION_CLOTHING)))
        )

        val response = api.categoryTrend(
            apiKeyId = BuildConfig.NAVER_API_KEY_ID,
            apiKey = BuildConfig.NAVER_API_KEY,
            request = request
        )

        val result = response.results.firstOrNull()
            ?: throw IllegalStateException("쇼핑인사이트 응답에 결과가 없어요.")

        result.data.map { point -> MonthlyClickTrend(month = point.period, ratio = point.ratio) }
    }

    /** 오늘 기준 정확히 1년 전 ~ 오늘, "yyyy-MM-dd" 문자열. minSdk 24라 java.time 대신 Calendar 사용. */
    private fun lastYearRange(): Pair<String, String> {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val end = Calendar.getInstance()
        val endDate = format.format(end.time)
        end.add(Calendar.YEAR, -1)
        val startDate = format.format(end.time)
        return startDate to endDate
    }
}
