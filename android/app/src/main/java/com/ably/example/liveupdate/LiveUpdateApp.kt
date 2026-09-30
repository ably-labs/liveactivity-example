package com.ably.example.liveupdate

import android.app.Application
import com.ably.example.liveupdate.model.GameStore
import com.ably.example.liveupdate.notification.LiveUpdateNotifier

class LiveUpdateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveUpdateNotifier.createChannel(this)
        GameStore.init(this)
    }
}
