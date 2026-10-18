package com.jeiu.fashionproject.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeiu.fashionproject.MainActivity
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.data.DayForecast
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.TastePreferenceStore
import com.jeiu.fashionproject.data.WardrobeStore
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLogStore
import com.jeiu.fashionproject.data.WeatherRepository
import com.jeiu.fashionproject.recommend.ForecastAlerts
import com.jeiu.fashionproject.recommend.OutfitEngine

/**
 * 저녁 8시쯤 실행 — 내일 예보를 받아 홈 화면의 "내일 코디"와 같은 규칙으로 알림을 만든다.
 *
 *  제목: "내일 맑음 9° / 18°"
 *  내용: "내일은 오늘보다 6도 낮아요, 베이지 블레이저 챙기세요
 *         추천 코디: 베이지 니트 + 블랙 슬랙스 + 로퍼"
 */
class WeatherAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val isTest = inputData.getBoolean(KEY_TEST, false)
        if (!isTest && !WeatherAlertScheduler.isEnabled(ctx)) return Result.success()

        val (lat, lon) = WeatherAlertScheduler.lastLocation(ctx)
        val forecast = runCatching { WeatherRepository.fetchForecast(lat, lon) }.getOrNull()
        if (forecast == null) {
            if (isTest) notify(ctx, "날씨 알림 테스트", "예보를 불러오지 못했어요. 인터넷과 날씨 API 키(local.properties)를 확인해 주세요.")
            // 정기 알림은 잠깐 뒤 한 번 더 시도하고, 그래도 안 되면 오늘은 건너뛴다
            return if (!isTest && runAttemptCount < 2) Result.retry() else Result.success()
        }

        val today = WearDates.today()
        val tomorrow = WearDates.daysAgo(-1)
        // 저녁에 받은 예보의 "오늘"은 밤 기온뿐이라, 미리 기억해 둔 오늘 낮 기온으로 바꾸고 없으면 비교에서 뺀다
        val days: List<DayForecast> = forecast.mapNotNull { d ->
            if (d.date != today) d
            else WeatherAlertScheduler.rememberedDayTemp(ctx, today)?.let { d.copy(dayTemp = it) }
        }
        WeatherAlertScheduler.rememberDayTemps(ctx, forecast.filter { it.date != today })

        val index = days.indexOfFirst { it.date == tomorrow }
        if (index < 0) {
            if (isTest) notify(ctx, "날씨 알림 테스트", "내일 예보가 아직 없어요.")
            return Result.success()
        }

        WardrobeStore.load(ctx)
        WearLogStore.load(ctx)
        StylePreferenceStore.load(ctx)
        TastePreferenceStore.load(ctx)
        // 홈 화면 "내일 코디"와 같은 계산 (색 · 종류 취향 포함)
        val plan = ForecastAlerts.plan(
            days, index, currentTemp = null,
            items = WardrobeStore.items.value,
            logs = WearLogStore.logs.value,
            styleScores = StylePreferenceStore.scores.value,
            count = 1,
            taste = TastePreferenceStore.scores.value
        )

        val day = plan.day
        val title = buildString {
            append("내일 ${day.condition} ${day.minTemp}° / ${day.maxTemp}°")
            if (day.rainChance >= DayForecast.RAINY_CHANCE) append(" · 비 ${day.rainChance}%")
        }
        val outfit = (plan.result as? OutfitEngine.Result.Ok)?.outfits?.firstOrNull()
            ?.items?.joinToString(" + ") { it.name }
        val lines = buildList {
            addAll(plan.alerts)
            if (outfit != null) add("추천 코디: $outfit")
        }.ifEmpty { listOf("무난한 날씨예요. 앱에서 내일 코디를 확인해 보세요.") }

        notify(ctx, if (isTest) "[테스트] $title" else title, lines.joinToString("\n"))
        return Result.success()
    }

    companion object {
        const val KEY_TEST = "test"
        private const val CHANNEL_ID = "weather_alert"
        private const val NOTIFICATION_ID = 2001

        @SuppressLint("MissingPermission") // 아래에서 알림 허용 여부를 먼저 확인한다
        fun notify(context: Context, title: String, text: String) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return   // 사용자가 알림을 허용하지 않음

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "내일 날씨 알림", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "매일 저녁 내일 날씨와 추천 코디를 알려줘요" }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            // 알림을 누르면 앱 열기
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(title)
                .setContentText(text.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}
