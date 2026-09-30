package com.ably.example.liveupdate.push

import com.ably.example.liveupdate.LiveGame
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.ably.lib.push.ActivationContext
import io.ably.lib.types.RegistrationToken

// Receives FCM messages delivered by Ably. The server sends data-only pushes,
// so onMessageReceived runs in the foreground and the background alike.
class LiveUpdateMessagingService : FirebaseMessagingService() {

    // FCM rotates registration tokens; hand the new one to Ably so the device
    // registration stays deliverable.
    override fun onNewToken(token: String) {
        ActivationContext.getActivationContext(applicationContext)
            .onNewRegistrationToken(RegistrationToken.Type.FCM, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        LiveGame.onPush(applicationContext, message.data)
    }
}
