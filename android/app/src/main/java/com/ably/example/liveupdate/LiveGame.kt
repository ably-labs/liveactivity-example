package com.ably.example.liveupdate

import android.content.Context
import android.util.Log
import com.ably.example.liveupdate.model.GameSnapshot
import com.ably.example.liveupdate.model.GameState
import com.ably.example.liveupdate.model.GameStore
import com.ably.example.liveupdate.notification.LiveUpdateNotifier
import com.ably.example.liveupdate.widget.GameScoreWidgetProvider

// Single entry point for game state changes, whether they come from a push or
// from the in-app controls: persists the state, then redraws the Live Update
// notification and the home-screen widget from it.
object LiveGame {
    private const val TAG = "LiveGame"

    fun start(context: Context, state: GameState) =
        render(context, GameSnapshot(state, isLive = true, timestamp = System.currentTimeMillis()))

    fun end(context: Context) {
        val current = GameStore.load(context) ?: return
        render(context, GameSnapshot(current.state.finished(), isLive = false, System.currentTimeMillis()))
    }

    /** Handles the data of an FCM push sent by the server's /api/android endpoints. */
    fun onPush(context: Context, data: Map<String, String>) {
        val event = data["event"]
        val state = GameState.fromMap(data)
        if (state == null) {
            Log.w(TAG, "Ignoring push without game state: $data")
            return
        }
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        val current = GameStore.load(context)
        if (current != null && timestamp < current.timestamp) {
            Log.i(TAG, "Ignoring out-of-order '$event' push")
            return
        }
        when (event) {
            "start", "update" -> render(context, GameSnapshot(state, isLive = true, timestamp))
            "end" -> render(context, GameSnapshot(state, isLive = false, timestamp))
            else -> Log.w(TAG, "Ignoring push with unknown event '$event'")
        }
    }

    private fun render(context: Context, snapshot: GameSnapshot) {
        GameStore.save(context, snapshot)
        LiveUpdateNotifier.show(context, snapshot)
        GameScoreWidgetProvider.refresh(context)
    }
}
