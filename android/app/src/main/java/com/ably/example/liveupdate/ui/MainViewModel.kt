package com.ably.example.liveupdate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ably.example.liveupdate.push.AblyPushManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val pushManager = AblyPushManager(application).also { it.restore() }

    override fun onCleared() {
        pushManager.close()
    }
}
