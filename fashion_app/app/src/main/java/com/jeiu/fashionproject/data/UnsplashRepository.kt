package com.jeiu.fashionproject.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UnsplashRepository {
    private const val ACCESS_KEY = "NPZN8CBTWsBt6vAjByTisvCksU4QQLKm45mL43-3nKc"

    suspend fun getPhotoUrl(query: String): String = withContext(Dispatchers.IO) {
        val encoded = Uri.encode(query)
        val url = "https://api.unsplash.com/photos/random?query=$encoded&orientation=portrait&client_id=$ACCESS_KEY"
        val json = JSONObject(URL(url).readText())
        json.getJSONObject("urls").getString("small")
    }
}
