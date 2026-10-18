package com.jeiu.fashionproject.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * NAVER API HUB(NCP)는 데이터랩, 검색 등 여러 서비스를 같은 API Gateway 호스트로
 * 제공한다. 서비스별 Repository(쇼핑인사이트, 이미지 검색 ...)가 이 Retrofit 인스턴스
 * 하나를 공유하고, 각자 자기 서비스의 @GET/@POST 경로만 인터페이스로 선언한다.
 */
object NaverApiHubClient {
    private const val BASE_URL = "https://naverapihub.apigw.ntruss.com/"

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
