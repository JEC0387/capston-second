package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.DbSync

/** Prepare identity before any screen makes a request; local functionality never waits for the server. */
class FashionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DbSync.init(this)
    }
}
