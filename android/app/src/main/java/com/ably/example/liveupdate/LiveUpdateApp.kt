package com.ably.example.liveupdate

import android.app.Application
import com.ably.example.liveupdate.model.GameStore
import com.ably.example.liveupdate.notification.LiveUpdateNotifier
import com.ably.example.liveupdate.notification.PushNotifier

class LiveUpdateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveUpdateNotifier.createChannel(this)
        PushNotifier.createChannel(this)
        GameStore.init(this)
    }
}
