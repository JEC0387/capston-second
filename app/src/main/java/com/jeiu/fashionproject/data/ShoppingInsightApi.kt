package com.jeiu.fashionproject.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * NAVER API HUB(NCP) - 데이터랩 쇼핑인사이트 '분야별 트렌드 조회'.
 * https://guide.ncloud-docs.com/docs/apihub-overview
 * https://api.ncloud-docs.com/docs/naver-api-hub-shopping-insight-categories
 *
 * 구 developers.naver.com 방식(X-Naver-Client-Id/Secret)과는 다르게,
 * NCP API Gateway 인증 헤더(X-NCP-APIGW-API-KEY-ID / X-NCP-APIGW-API-KEY)를 쓴다.
 */
interface ShoppingInsightApi {

    @Headers("Content-Type: application/json")
    @POST("shopping/v1/categories")
    suspend fun categoryTrend(
        @Header("X-NCP-APIGW-API-KEY-ID") apiKeyId: String,
        @Header("X-NCP-APIGW-API-KEY") apiKey: String,
        @Body request: ShoppingCategoryRequest
    ): ShoppingCategoryResponse
}

data class ShoppingCategoryRequest(
    val startDate: String,
    val endDate: String,
    val timeUnit: String, // date | week | month
    val category: List<CategoryParam>,
    val device: String? = null, // pc | mo
    val gender: String? = null, // m | f
    val ages: List<String>? = null // "10","20","30","40","50","60"
)

data class CategoryParam(
    val name: String,
    val param: List<String>
)

data class ShoppingCategoryResponse(
    val startDate: String,
    val endDate: String,
    val timeUnit: String,
    val results: List<CategoryTrendResult>
)

data class CategoryTrendResult(
    val title: String,
    val category: List<String>,
    val data: List<TrendDataPoint>
)

data class TrendDataPoint(
    val period: String,
    val ratio: Double
)
