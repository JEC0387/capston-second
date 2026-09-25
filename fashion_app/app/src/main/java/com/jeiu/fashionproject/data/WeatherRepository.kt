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

fun WeatherInfo.isThunderstorm() = conditionId in 200..299
fun WeatherInfo.isDrizzle() = conditionId in 300..399
fun WeatherInfo.isRain() = conditionId in 500..599
fun WeatherInfo.isSnow() = conditionId in 600..699
fun WeatherInfo.isFoggy() = conditionId in 700..799
fun WeatherInfo.isClear() = conditionId == 800
fun WeatherInfo.isRainy() = conditionId in 200..599   // 기존 호출부 호환 유지
fun WeatherInfo.isCloudy() = conditionId in 801..899

/**
 * 실시간 날씨(상태·온도·습도)에 맞춰 옷차림 한 줄 조언을 생성한다.
 * 1) 눈/천둥번개/비/안개 등 특수 기상은 우선 안내
 * 2) 그 외에는 온도 구간별 코디를 안내하고, 습도가 높으면 보정 문구를 덧붙인다
 */
fun WeatherInfo.advice(): String {
    // 특수 기상 우선 처리
    when {
        isThunderstorm() -> return "천둥번개가 쳐요. 외출은 잠시 미루는 게 좋아요"
        isSnow() && temp <= 0 -> return "눈이 와요. 두꺼운 패딩과 방한용품을 꼭 챙기세요"
        isSnow() -> return "눈이 내려요. 미끄럼 방지 신발과 따뜻한 코트를 추천해요"
        isRain() && temp >= 23 -> return "비가 와요. 우산과 통풍 잘되는 가벼운 옷을 챙기세요"
        isRain() -> return "비가 와요. 우산과 방수 아우터를 챙기는 게 좋아요"
        isDrizzle() -> return "이슬비가 내려요. 가벼운 우산이나 후드를 챙겨보세요"
        isFoggy() -> return "안개가 꼈어요. 밝은 색 옷으로 가시성을 높여보세요"
    }

    // 온도 구간별 기본 코디
    val base = when {
        temp >= 30 -> "한여름 더위예요. 민소매나 린넨 소재로 시원하게 입어보세요"
        temp >= 27 -> "더운 날씨예요. 반소매 티셔츠와 반바지가 잘 어울려요"
        temp >= 23 -> "따뜻해요. 가벼운 반소매나 얇은 셔츠가 좋아요"
        temp >= 20 -> "선선해요. 얇은 가디건이나 가벼운 아우터를 걸치기 좋아요"
        temp >= 17 -> "활동하기 좋은 날씨예요. 긴소매 셔츠나 얇은 니트를 추천해요"
        temp >= 12 -> "쌀쌀해요. 자켓이나 트렌치코트가 필요한 날씨예요"
        temp >= 8  -> "추워요. 따뜻한 니트와 코트를 챙기세요"
        temp >= 2  -> "많이 추워요. 두꺼운 코트와 목도리를 추천해요"
        else       -> "한파예요. 패딩과 방한용품으로 단단히 챙겨 입으세요"
    }

    // 습도 보정 (더울 때 습도 높으면 불쾌, 추울 때 건조하면 보습)
    val humidityNote = when {
        temp >= 25 && humidity >= 70 -> " 습도가 높아 끈적해요. 통풍 잘되는 소재가 좋아요"
        temp >= 25 && humidity <= 30 -> " 건조하고 더우니 수분을 자주 보충하세요"
        temp <= 10 && humidity <= 30 -> " 공기가 건조하니 보습에 신경 쓰세요"
        humidity >= 80               -> " 습도가 높으니 가벼운 소재를 추천해요"
        else -> ""
    }

    return base + humidityNote
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
