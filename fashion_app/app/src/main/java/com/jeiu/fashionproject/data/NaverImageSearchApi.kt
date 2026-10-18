package com.jeiu.fashionproject.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * NAVER API HUB(NCP) - 이미지 검색 결과 조회.
 * https://api.ncloud-docs.com/docs/naver-api-hub-search-image
 *
 * 구 developers.naver.com 쇼핑 검색 API는 종료되어 더 못 쓴다. 이 API는 "쇼핑 검색"이
 * 아니라 일반 이미지 검색이라, 가격/몰이름/구매링크 같은 상품 정보는 응답에 없다
 * (title/이미지 URL/썸네일/크기뿐). 쇼핑 탭에서는 이 결과를 보여주고, 탭하면
 * ShopSearchSheet로 넘겨 실제 쇼핑몰 검색으로 연결한다.
 */
interface NaverImageSearchApi {

    @GET("search/v1/image")
    suspend fun search(
        @Header("X-NCP-APIGW-API-KEY-ID") apiKeyId: String,
        @Header("X-NCP-APIGW-API-KEY") apiKey: String,
        @Query("query") query: String,
        @Query("display") display: Int = 30,
        @Query("start") start: Int = 1,
        @Query("sort") sort: String = "sim", // sim(정확도) | date
        @Query("filter") filter: String = "all" // all | large | medium | small
    ): NaverImageSearchResponse
}

data class NaverImageSearchResponse(
    val lastBuildDate: String,
    val total: Int,
    val start: Int,
    val display: Int,
    val items: List<NaverImageItem>
)

data class NaverImageItem(
    val title: String,
    val link: String,       // 원본 이미지 URL
    val thumbnail: String,  // 썸네일 이미지 URL
    val sizeheight: String,
    val sizewidth: String
)
