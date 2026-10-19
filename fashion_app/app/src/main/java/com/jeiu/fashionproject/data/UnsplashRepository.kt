package com.jeiu.fashionproject.data

/** Currently unused. Keep the API disabled until a secure server integration is available. */
object UnsplashRepository {
    @Suppress("UNUSED_PARAMETER")
    suspend fun getPhotoUrl(query: String): String =
        throw IllegalStateException("Unsplash 사진 서비스가 설정되지 않았어요.")
}
