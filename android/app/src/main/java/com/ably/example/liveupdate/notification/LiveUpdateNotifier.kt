package com.ably.example.liveupdate.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ably.example.liveupdate.R
import com.ably.example.liveupdate.model.GameSnapshot
import com.ably.example.liveupdate.ui.MainActivity
import java.util.concurrent.TimeUnit

// Renders the game as an Android Live Update: an ongoing notification that
// requests promotion, so on Android 16+ it is pinned at the top of the shade
// and on the lock screen, with the score as a status-bar chip. On older
// versions the same notification shows as a regular ongoing notification.
//
// Promotion has strict requirements (checked by the system, otherwise the
// notification is simply not promoted): ongoing, a content title, a standard
// or BigText/Progress/Call style, no custom views, not colorized, and a
// channel whose importance is above MIN.
object LiveUpdateNotifier {
    private const val CHANNEL_ID = "live_game"
    private const val NOTIFICATION_ID = 1
    private val FINAL_SCORE_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // POST_NOTIFICATIONS is checked via areNotificationsEnabled(); without it
    // the widget still updates, only the notification is skipped.
    @SuppressLint("MissingPermission")
    fun show(context: Context, snapshot: GameSnapshot) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val state = snapshot.state
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val statusLine = "${state.gameStatus.label} · ${state.clockLine}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_basketball)
            .setContentTitle("${state.homeTeam} ${state.homeScore} – ${state.awayScore} ${state.awayTeam}")
            .setContentText(statusLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$statusLine\n${state.lastPlay}"))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (snapshot.isLive) {
            builder
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                // Shown in the status-bar chip of a promoted notification.
                .setShortCriticalText(state.scoreLine)
        } else {
            // Final score: dismissable, cleaned up automatically after a while
            // (like the iOS activity's dismissal date).
            builder
                .setAutoCancel(true)
                .setTimeoutAfter(FINAL_SCORE_TIMEOUT_MS)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
