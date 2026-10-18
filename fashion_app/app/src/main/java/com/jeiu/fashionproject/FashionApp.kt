package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.notify.WeatherAlertScheduler

/** Prepare identity before any screen makes a request; local functionality never waits for the server. */
class FashionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DbSync.init(this)
        // 내일 날씨 알림이 켜져 있으면 예약 확인 (기기 번호 준비 뒤에)
        WeatherAlertScheduler.ensureScheduled(this)
    }
}
