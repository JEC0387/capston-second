package com.jeiu.fashionproject.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class WeatherInfo(
    val condition: String,
    val temp: Int,
    val humidity: Int,
    val conditionId: Int
)

fun WeatherInfo.isRainy() = conditionId in 200..599
fun WeatherInfo.isCloudy() = conditionId in 801..899

fun WeatherInfo.advice(): String = when {
    isRainy() -> "우산을 챙기는 게 좋을 것 같아요"
    temp >= 25 -> "가벼운 반소매가 어울리는 날씨예요"
    temp >= 20 -> "가벼운 아우터를 걸치기 좋은 날씨예요"
    temp >= 15 -> "얇은 재킷이 필요한 날씨예요"
    temp >= 10 -> "따뜻한 니트나 재킷을 추천해요"
    else -> "두꺼운 코트가 필요한 날씨예요"
}

object WeatherRepository {
    private const val API_KEY = "e422ab80d71dbbe764957743657f398f"

    suspend fun fetch(lat: Double, lon: Double): WeatherInfo = withContext(Dispatchers.IO) {
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$API_KEY&units=metric&lang=kr"
        val json = JSONObject(URL(url).readText())
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val main = json.getJSONObject("main")
        WeatherInfo(
            condition = weather.getString("description"),
            temp = main.getDouble("temp").toInt(),
            humidity = main.getInt("humidity"),
            conditionId = weather.getInt("id")
        )
    }
}
