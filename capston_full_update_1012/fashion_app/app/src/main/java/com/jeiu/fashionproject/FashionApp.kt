package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.notify.WeatherAlertScheduler

/**
 * 앱이 켜질 때 가장 먼저 한 번 실행된다 (화면이 없는 백그라운드 알림 작업 때도).
 * - 기기 번호 준비: 서버 DB가 폰마다 데이터를 따로 저장하도록 (모든 서버 요청보다 먼저)
 * - 내일 날씨 알림이 켜져 있으면 예약 확인
 */
class FashionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DbSync.init(this)
        WeatherAlertScheduler.ensureScheduled(this)
    }
}
