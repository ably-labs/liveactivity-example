package com.ably.example.liveupdate.model

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The game currently shown in the Live Update and the widget.
 *
 * @property isLive true while the Live Update is ongoing; false once it ended.
 * @property timestamp when the server sent this state (ms), used to drop
 *   pushes that arrive out of order.
 */
data class GameSnapshot(val state: GameState, val isLive: Boolean, val timestamp: Long)

// Persists the latest snapshot so the widget can render it after process death
// (it is redrawn by the system without the app running), and exposes it as a
// flow for the in-app UI.
object GameStore {
    private const val PREFS = "game_store"
    private const val KEY_SNAPSHOT = "snapshot"

    private val _snapshot = MutableStateFlow<GameSnapshot?>(null)
    val snapshot: StateFlow<GameSnapshot?> = _snapshot.asStateFlow()

    fun init(context: Context) {
        _snapshot.value = load(context)
    }

    fun load(context: Context): GameSnapshot? {
        val raw = prefs(context).getString(KEY_SNAPSHOT, null) ?: return null
        val json = JSONObject(raw)
        val state = GameState.fromJson(json.getJSONObject("state")) ?: return null
        return GameSnapshot(state, json.getBoolean("isLive"), json.getLong("timestamp"))
    }

    fun save(context: Context, snapshot: GameSnapshot) {
        val json = JSONObject()
            .put("state", snapshot.state.toJson())
            .put("isLive", snapshot.isLive)
            .put("timestamp", snapshot.timestamp)
        prefs(context).edit { putString(KEY_SNAPSHOT, json.toString()) }
        _snapshot.value = snapshot
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
