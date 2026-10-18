package com.jeiu.fashionproject.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.WearDates
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 내일 날씨 알림 예약 (매일 저녁 8시쯤).
 *
 * - 마이페이지 > 알림 설정에서 켜고 끈다. 기본은 꺼짐 (켤 때 알림 권한을 받는다)
 * - 안드로이드가 배터리를 아끼려고 시간을 조금 미룰 수 있어 "8시쯤"이다
 * - 인터넷이 연결돼 있을 때만 실행된다 (예보를 받아야 하므로)
 */
object WeatherAlertScheduler {
    private const val PREFS = "weather_alert"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val WORK_DAILY = "weather_alert_daily"
    private const val WORK_TEST = "weather_alert_test"
    const val ALERT_HOUR = 20

    // 위치를 한 번도 못 받았으면 서울시청 기준
    private const val DEFAULT_LAT = 37.5665
    private const val DEFAULT_LON = 126.9780

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)  // 다시 켜면 저녁 8시 기준으로 새로 예약
        else WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY)
    }

    /** 앱을 켤 때 호출 — 켜져 있는데 예약이 없으면 다시 예약 (이미 있으면 그대로 둠) */
    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) schedule(context, ExistingPeriodicWorkPolicy.KEEP)
    }

    private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(ALERT_HOUR), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_DAILY, policy, request)
    }

    /** [지금 테스트 알림 보내기] — 시연용. 켜짐 여부와 상관없이 지금 한 번 보낸다 */
    fun sendTestNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherAlertWorker>()
            .setInputData(workDataOf(WeatherAlertWorker.KEY_TEST to true))
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TEST, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** 지금부터 다음 hour 시 정각까지 남은 시간 */
    fun millisUntilNext(hour: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    // ── 위치 · 낮 기온 기억 (백그라운드에서는 위치를 새로 받기 어려워서 앱이 받은 값을 쓴다) ──

    fun saveLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .apply()
    }

    fun lastLocation(context: Context): Pair<Double, Double> {
        val p = prefs(context)
        val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: DEFAULT_LAT
        val lon = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: DEFAULT_LON
        return lat to lon
    }

    /**
     * 날짜별 낮(9~18시) 기온을 기억해 둔다.
     * 저녁 8시에 받은 예보에는 오늘 낮 시간이 이미 지나 "오늘 기온"을 알 수 없으므로,
     * 전날 밤이나 오늘 오전에 받아 둔 값으로 "내일은 오늘보다 6도 낮아요"를 계산한다.
     * 오늘 날짜는 정오 전에 받은 예보일 때만 기억한다 (오후엔 낮 시간이 대부분 지나서 부정확).
     */
    fun rememberDayTemps(context: Context, days: List<DayForecast>, now: Calendar = Calendar.getInstance()) {
        val today = WearDates.of(now)
        val editor = prefs(context).edit()
        days.forEach { d ->
            if (d.date != today || now.get(Calendar.HOUR_OF_DAY) < 12) editor.putInt("daytemp_${d.date}", d.dayTemp)
        }
        // 어제보다 오래된 기록은 지운다
        val yesterday = WearDates.daysAgo(1)
        prefs(context).all.keys
            .filter { it.startsWith("daytemp_") && it.removePrefix("daytemp_") < yesterday }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun rememberedDayTemp(context: Context, date: String): Int? {
        val p = prefs(context)
        return if (p.contains("daytemp_$date")) p.getInt("daytemp_$date", 0) else null
    }
}
