package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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

/**
 * 하루 예보 요약 (계획 4 — 3단계).
 * OpenWeatherMap 무료 예보는 3시간 간격이라, 같은 날짜끼리 묶어서 하루 요약을 만든다.
 */
data class DayForecast(
    val date: String,        // "2026-09-30"
    val minTemp: Int,
    val maxTemp: Int,
    val dayTemp: Int,        // 낮(9~18시) 평균 기온 — 코디는 이 기온으로 짠다
    val rainChance: Int,     // 비·눈 올 확률 (0~100, 그날 가장 높은 값)
    val humidity: Int,
    val conditionId: Int,    // 대표 날씨
    val condition: String
) {
    /** 코디 엔진에 넣을 날씨. 비 올 확률이 60% 이상이면 비 오는 날로 본다. */
    fun toWeatherInfo(): WeatherInfo =
        if (rainChance >= RAINY_CHANCE && conditionId !in 200..699) WeatherInfo("비 소식", dayTemp, humidity, 500)
        else WeatherInfo(condition, dayTemp, humidity, conditionId)

    companion object {
        const val RAINY_CHANCE = 60
    }
}

object WeatherRepository {
    // 키는 local.properties의 OWM_API_KEY에서 build.gradle.kts가 꺼내 넣는다 (코드에 적지 않음)
    private val API_KEY = BuildConfig.OWM_API_KEY

    /** 5일 예보를 날짜별 요약으로 받는다. 키가 없거나 실패하면 예외. */
    suspend fun fetchForecast(lat: Double, lon: Double): List<DayForecast> = withContext(Dispatchers.IO) {
        check(API_KEY.isNotBlank()) { "local.properties에 OWM_API_KEY가 없어요" }
        val url = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$API_KEY&units=metric&lang=kr"
        parseForecast(JSONObject(URL(url).readText()))
    }

    private class Slot(val hour: Int, val temp: Double, val humidity: Int, val pop: Double, val id: Int, val desc: String)

    /** 3시간 간격 40개 → 날짜별로 묶어 최대 5일 */
    fun parseForecast(json: JSONObject): List<DayForecast> {
        // 시간은 그 지역 시간으로 계산한다 (city.timezone = UTC와의 차이, 초)
        val offsetMs = json.optJSONObject("city")?.optLong("timezone", 9 * 3600L)?.times(1000) ?: (9 * 3600_000L)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val hourFmt = SimpleDateFormat("H", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val list = json.getJSONArray("list")
        val byDate = linkedMapOf<String, MutableList<Slot>>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val local = Date(o.getLong("dt") * 1000 + offsetMs)
            val main = o.getJSONObject("main")
            val w = o.getJSONArray("weather").getJSONObject(0)
            byDate.getOrPut(dateFmt.format(local)) { mutableListOf() } += Slot(
                hour = hourFmt.format(local).toInt(),
                temp = main.getDouble("temp"),
                humidity = main.getInt("humidity"),
                pop = o.optDouble("pop", 0.0),
                id = w.getInt("id"),
                desc = w.getString("description")
            )
        }
        return byDate.map { (date, slots) ->
            val daytime = slots.filter { it.hour in 9..18 }.ifEmpty { slots }
            // 대표 날씨: 비·눈 확률이 높은 시간이 있으면 그 날씨, 아니면 낮 시간에 가장 많이 나온 날씨
            val wet = slots.filter { it.id in 200..699 && it.pop * 100 >= DayForecast.RAINY_CHANCE }.maxByOrNull { it.pop }
            val rep = wet ?: daytime.groupBy { it.id }.maxBy { it.value.size }.value.first()
            DayForecast(
                date = date,
                minTemp = slots.minOf { it.temp }.roundToInt(),
                maxTemp = slots.maxOf { it.temp }.roundToInt(),
                dayTemp = daytime.map { it.temp }.average().roundToInt(),
                rainChance = (slots.maxOf { it.pop } * 100).roundToInt(),
                humidity = slots.map { it.humidity }.average().roundToInt(),
                conditionId = rep.id,
                condition = rep.desc
            )
        }.take(5)
    }

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
