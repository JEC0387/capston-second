package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 쇼핑 탭 카드 한 개. 이미지 검색 결과라 가격/몰이름/구매링크는 없다. */
data class ShopImageResult(
    val title: String,
    val imageUrl: String,
    val thumbnailUrl: String
)

object NaverImageSearchRepository {
    private val tagRegex = Regex("<[^>]*>")

    private val api: NaverImageSearchApi by lazy {
        NaverApiHubClient.retrofit.create(NaverImageSearchApi::class.java)
    }

    suspend fun search(query: String, display: Int = 30): List<ShopImageResult> = withContext(Dispatchers.IO) {
        if (BuildConfig.NAVER_API_KEY_ID.isBlank() || BuildConfig.NAVER_API_KEY.isBlank()) {
            throw IllegalStateException(
                "NAVER API HUB 키가 설정되지 않았어요.\nlocal.properties에 NAVER_API_KEY_ID / NAVER_API_KEY를 넣어주세요."
            )
        }

        val response = api.search(
            apiKeyId = BuildConfig.NAVER_API_KEY_ID,
            apiKey = BuildConfig.NAVER_API_KEY,
            query = query,
            display = display
        )

        response.items.map { item ->
            ShopImageResult(
                title = item.title.replace(tagRegex, ""), // 검색어 강조용 <b> 태그 제거
                imageUrl = item.link,
                thumbnailUrl = item.thumbnail
            )
        }
    }
}
