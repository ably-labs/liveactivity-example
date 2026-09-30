package com.ably.example.liveupdate.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.FirebaseApp
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.rest.AblyRest
import io.ably.lib.types.AblyException
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Registers this device with Ably push (FCM) and subscribes it to channels,
// so the server can target it by channel name or device ID.
//
// Authentication uses Ably token auth via the server's `authUrl` endpoint, so
// the API key never reaches the device. Ably reports activation results as
// local broadcasts (io.ably.broadcast.PUSH_*), which are observed here.
class AblyPushManager(context: Context) {

    data class State(
        val isActivating: Boolean = false,
        val isActivated: Boolean = false,
        val deviceId: String? = null,
        val subscribedChannel: String? = null,
        val statusMessage: String? = null,
        val errorMessage: String? = null,
    )

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val broadcasts = LocalBroadcastManager.getInstance(this.context)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Retained so the activation state machine and channel calls share one client.
    private var rest: AblyRest? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val error = intent.errorMessage()
            when (intent.action) {
                ACTION_ACTIVATE -> onActivated(error)
                ACTION_DEACTIVATE -> onDeactivated(error)
                ACTION_UPDATE_FAILED -> _state.update {
                    it.copy(isActivating = false, errorMessage = "Registration update failed: $error")
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_ACTIVATE)
            addAction(ACTION_DEACTIVATE)
            addAction(ACTION_UPDATE_FAILED)
        }
        broadcasts.registerReceiver(receiver, filter)
    }

    /** Server URL from the last successful activation, to prefill the UI. */
    val savedServerUrl: String? get() = prefs.getString(KEY_SERVER_URL, null)

    /**
     * Re-attaches to an activation that survived an app restart (Ably persists
     * the device registration). Activating an already-activated device just
     * reports success again, which restores the device ID in the UI.
     */
    fun restore() {
        val serverUrl = savedServerUrl ?: return
        if (prefs.getBoolean(KEY_ACTIVATED, false)) activate(serverUrl)
    }

    /**
     * Activates the device with Ably. [serverBaseUrl] is the dashboard server;
     * its `/api/auth` endpoint mints the TokenRequest consumed via authUrl.
     */
    fun activate(serverBaseUrl: String) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            _state.update { it.copy(errorMessage = "Firebase is not configured — add app/google-services.json and rebuild") }
            return
        }
        val base = serverBaseUrl.trim().trimEnd('/')
        _state.update {
            it.copy(isActivating = true, isActivated = false, errorMessage = null, statusMessage = "Activating device…")
        }
        try {
            val options = ClientOptions().apply {
                authUrl = "$base/api/auth"
                authMethod = "GET"
            }
            val rest = AblyRest(options)
            rest.setAndroidContext(context)
            this.rest = rest
            prefs.edit { putString(KEY_SERVER_URL, base) }
            // Fetches an FCM registration token and registers the device with
            // Ably; the result arrives as an ACTION_ACTIVATE broadcast.
            rest.push.activate()
        } catch (e: AblyException) {
            _state.update { it.copy(isActivating = false, errorMessage = "Activation failed: ${e.errorInfo.message}") }
        }
    }

    fun deactivate() {
        val rest = rest ?: return
        _state.update { it.copy(statusMessage = "Deactivating device…") }
        try {
            rest.push.deactivate()
        } catch (e: AblyException) {
            _state.update { it.copy(errorMessage = "Deactivation failed: ${e.errorInfo.message}") }
        }
    }

    /**
     * Subscribes this device to push on an Ably channel, so the server can
     * target it by channel name. The channel's namespace must have push
     * enabled in the Ably dashboard.
     */
    fun subscribe(channelName: String) {
        val name = channelName.trim()
        val rest = rest
        if (name.isEmpty() || rest == null) return
        _state.update { it.copy(statusMessage = "Subscribing device to $name…") }
        rest.channels.get(name).push.subscribeDeviceAsync(object : CompletionListener {
            override fun onSuccess() {
                _state.update {
                    it.copy(subscribedChannel = name, errorMessage = null, statusMessage = "Subscribed to push on $name")
                }
            }

            override fun onError(reason: ErrorInfo?) {
                _state.update { it.copy(errorMessage = "Channel subscribe failed: ${reason?.message}") }
            }
        })
    }

    fun close() {
        broadcasts.unregisterReceiver(receiver)
    }

    private fun onActivated(error: String?) {
        if (error != null) {
            prefs.edit { putBoolean(KEY_ACTIVATED, false) }
            _state.update {
                it.copy(isActivating = false, isActivated = false, statusMessage = null, errorMessage = "Activation failed: $error")
            }
            return
        }
        prefs.edit { putBoolean(KEY_ACTIVATED, true) }
        val deviceId = runCatching { rest?.device()?.id }.getOrNull()
        _state.update {
            it.copy(
                isActivating = false,
                isActivated = true,
                deviceId = deviceId,
                errorMessage = null,
                statusMessage = "Device activated with Ably push (FCM)",
            )
        }
    }

    private fun onDeactivated(error: String?) {
        if (error != null) {
            _state.update { it.copy(errorMessage = "Deactivation failed: $error") }
            return
        }
        prefs.edit { putBoolean(KEY_ACTIVATED, false) }
        rest = null
        _state.value = State(statusMessage = "Device deactivated")
    }

    private fun Intent.errorMessage(): String? =
        if (getBooleanExtra("hasError", false)) getStringExtra("error.message") ?: "unknown error" else null

    private companion object {
        const val PREFS = "ably_push"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACTIVATED = "activated"

        const val ACTION_ACTIVATE = "io.ably.broadcast.PUSH_ACTIVATE"
        const val ACTION_DEACTIVATE = "io.ably.broadcast.PUSH_DEACTIVATE"
        const val ACTION_UPDATE_FAILED = "io.ably.broadcast.PUSH_UPDATE_FAILED"
    }
}
