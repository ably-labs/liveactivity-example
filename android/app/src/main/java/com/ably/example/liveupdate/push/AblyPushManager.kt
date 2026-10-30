package com.ably.example.liveupdate.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.FirebaseApp
import io.ably.lib.http.HttpUtils
import io.ably.lib.rest.AblyRest
import io.ably.lib.types.AblyException
import io.ably.lib.types.AsyncHttpPaginatedResponse
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Param
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

// Registers this device with Ably push (FCM) and subscribes it to FCM topics
// through Ably, so the server can reach it with one publish to the topic.
//
// Authentication uses Ably token auth via the server's `authUrl` endpoint, so
// the API key never reaches the device. Ably reports activation results as
// local broadcasts (io.ably.broadcast.PUSH_*), which are observed here.
class AblyPushManager(context: Context) {

    data class State(
        val isActivating: Boolean = false,
        val isActivated: Boolean = false,
        val deviceId: String? = null,
        val subscribedTopic: String? = null,
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
     * its `/api/config` endpoint names the Ably host, and its
     * `/api/auth` endpoint mints the TokenRequest consumed via authUrl.
     */
    fun activate(serverBaseUrl: String) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            _state.update { it.copy(errorMessage = "Firebase is not configured — add app/google-services.json and rebuild") }
            return
        }
        val base = serverBaseUrl.trim().trimEnd('/')
        _state.update {
            it.copy(isActivating = true, isActivated = false, errorMessage = null, statusMessage = "Fetching server config…")
        }
        thread {
            startActivation(base)
        }
    }

    private fun startActivation(base: String) {
        _state.update { it.copy(statusMessage = "Activating device…") }
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
     * Subscribes this device to an FCM topic via Ably's
     * `POST /push/fcmTopics/{topic}/subscriptions/{deviceId}`. Ably resolves
     * the device to its FCM registration token, so the app never handles FCM
     * topics or credentials itself. ably-java has no wrapper for this endpoint
     * yet, so it goes through `request()`, authenticated as the device by its
     * identity token (no push-admin needed). Subscribing again is a no-op.
     */
    fun subscribe(topic: String) {
        val name = topic.trim()
        val rest = rest
        if (name.isEmpty() || rest == null) return
        val device = rest.device()
        _state.update { it.copy(statusMessage = "Subscribing device to topic $name…") }
        rest.requestAsync(
            "POST",
            "/push/fcmTopics/${HttpUtils.encodeURIComponent(name)}/subscriptions/${HttpUtils.encodeURIComponent(device.id)}",
            null,
            // The endpoint takes no body, but ably-java rejects a POST without one.
            HttpUtils.JsonRequestBody("{}"),
            arrayOf(Param("X-Ably-DeviceToken", device.deviceIdentityToken)),
            object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse) {
                    if (response.success) {
                        _state.update {
                            it.copy(subscribedTopic = name, errorMessage = null, statusMessage = "Subscribed to topic $name")
                        }
                    } else {
                        onSubscribeFailed(ErrorInfo(response.errorMessage, response.statusCode, response.errorCode))
                    }
                }

                override fun onError(reason: ErrorInfo?) = onSubscribeFailed(reason)
            },
        )
    }

    private fun onSubscribeFailed(reason: ErrorInfo?) {
        if (reason?.code == ERROR_FCM_TOKEN_INVALID) {
            // FCM rejected the device's registration token and Ably marked the
            // registration failed; the device must activate again.
            prefs.edit { putBoolean(KEY_ACTIVATED, false) }
            rest = null
            _state.value = State(errorMessage = "FCM token is no longer valid — activate the device again")
            return
        }
        _state.update { it.copy(errorMessage = "Topic subscribe failed: ${reason?.message}") }
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

        // FCM reported the registration token dead (UNREGISTERED, SENDER_ID_MISMATCH).
        const val ERROR_FCM_TOKEN_INVALID = 103005

        const val ACTION_ACTIVATE = "io.ably.broadcast.PUSH_ACTIVATE"
        const val ACTION_DEACTIVATE = "io.ably.broadcast.PUSH_DEACTIVATE"
        const val ACTION_UPDATE_FAILED = "io.ably.broadcast.PUSH_UPDATE_FAILED"
    }
}
