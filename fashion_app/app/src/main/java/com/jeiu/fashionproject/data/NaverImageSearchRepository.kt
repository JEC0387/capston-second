package com.jeiu.fashionproject.data

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

    /** All requests use the backend proxy; no provider credential is present in the APK. */
    suspend fun search(query: String, display: Int = 30): List<ShopImageResult> = withContext(Dispatchers.IO) {
        searchOnServer(query, display)
            ?: throw IllegalStateException("이미지를 불러오지 못했어요. 서버 연결을 확인해 주세요.")
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
