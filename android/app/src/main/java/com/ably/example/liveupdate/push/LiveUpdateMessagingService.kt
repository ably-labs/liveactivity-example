package com.ably.example.liveupdate.push

import com.ably.example.liveupdate.LiveGame
import com.ably.example.liveupdate.notification.PushNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.ably.lib.push.ActivationContext
import io.ably.lib.types.RegistrationToken

// Receives FCM messages delivered by Ably. The server's game pushes are
// data-only, so onMessageReceived runs for them in the foreground and the
// background alike. Pushes with a `notification` payload only reach it in the
// foreground (FCM displays them itself otherwise), so they are shown here.
class LiveUpdateMessagingService : FirebaseMessagingService() {

    // FCM rotates registration tokens; hand the new one to Ably so the device
    // registration stays deliverable.
    override fun onNewToken(token: String) {
        ActivationContext.getActivationContext(applicationContext)
            .onNewRegistrationToken(RegistrationToken.Type.FCM, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.notification != null) {
            PushNotifier.show(applicationContext, message)
        }
        if (message.data.isNotEmpty()) {
            LiveGame.onPush(applicationContext, message.data)
        }
    }
}
