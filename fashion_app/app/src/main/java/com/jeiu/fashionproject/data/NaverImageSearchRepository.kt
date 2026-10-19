package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

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

    /**
     * 1) 우리 서버(/api/naver/image-search)에 먼저 묻는다 — 네이버 키는 서버(Railway Variables)에만 있다.
     * 2) 서버가 안 되면 앱에 키(local.properties)가 있을 때만 네이버에 직접 묻는다 (예비용, 없어도 된다).
     */
    suspend fun search(query: String, display: Int = 30): List<ShopImageResult> = withContext(Dispatchers.IO) {
        searchOnServer(query, display)?.let { return@withContext it }

        if (BuildConfig.NAVER_API_KEY_ID.isBlank() || BuildConfig.NAVER_API_KEY.isBlank()) {
            throw IllegalStateException(
                "이미지를 불러오지 못했어요.\n서버 연결을 확인해 주세요."
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

    /** 서버가 대신 검색한 결과. 서버 연결 실패 · 서버에 키 없음(503) · 네이버 오류(502)면 null */
    private suspend fun searchOnServer(query: String, display: Int): List<ShopImageResult>? {
        val q = URLEncoder.encode(query, "UTF-8")
        val body = BackendApi.getJson("/api/naver/image-search?query=$q&display=$display") ?: return null
        return runCatching {
            val items = JSONObject(body).getJSONArray("items")
            (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                ShopImageResult(
                    title = o.getString("title"),
                    imageUrl = o.getString("imageUrl"),
                    thumbnailUrl = o.getString("thumbnailUrl")
                )
            }
        }.getOrNull()
    }
}
