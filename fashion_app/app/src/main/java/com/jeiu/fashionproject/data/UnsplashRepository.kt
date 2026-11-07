package com.jeiu.fashionproject.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The access credential is held only by the backend. */
object UnsplashRepository {
    suspend fun getPhotoUrl(query: String): String = withContext(Dispatchers.IO) {
        val body = BackendApi.getJson("/api/unsplash/photo?query=${Uri.encode(query)}")
            ?: throw IllegalStateException("사진 서비스를 사용할 수 없어요. 서버 연결을 확인해주세요.")
        JSONObject(body).getString("url")
    }
}
