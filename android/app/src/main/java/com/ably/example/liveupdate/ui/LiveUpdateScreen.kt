package com.ably.example.liveupdate.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ably.example.liveupdate.LiveGame
import com.ably.example.liveupdate.model.GameStore
import com.ably.example.liveupdate.push.AblyPushManager
import com.ably.example.liveupdate.widget.GameScoreWidgetProvider

// The emulator reaches the host machine's localhost at 10.0.2.2.
private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveUpdateScreen(pushManager: AblyPushManager, resumeCount: Int) {
    Scaffold(topBar = { TopAppBar(title = { Text("NBA Live Update") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LiveUpdateControlSection()
            PermissionsSection(resumeCount)
            AblyPushSection(pushManager)
            WidgetSection()
        }
    }
}

@Composable
private fun LiveUpdateControlSection() {
    val context = LocalContext.current
    val snapshot by GameStore.snapshot.collectAsState()
    Section("Live Update Control") {
        val current = snapshot
        if (current?.isLive == true) {
            Text("● Live Update running", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                "${current.state.homeTeam} ${current.state.scoreLine} ${current.state.awayTeam} · ${current.state.clockLine}",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { LiveGame.end(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("End Live Update") }
        } else {
            Hint("No Live Update running. Start one by Ably push from the dashboard's Android tab.")
        }
    }
}

@Composable
private fun PermissionsSection(resumeCount: Int) {
    val context = LocalContext.current
    // Re-read on every resume, after the user may have changed settings.
    var notificationsEnabled by remember { mutableStateOf(true) }
    var promotion by remember { mutableStateOf(Promotion.UNSUPPORTED_VERSION) }
    androidx.compose.runtime.LaunchedEffect(resumeCount) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        promotion = promotionState(context)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = it
    }

    Section("Notifications") {
        StatusRow("Notifications", if (notificationsEnabled) "Allowed" else "Not allowed", notificationsEnabled)
        if (!notificationsEnabled) {
            OutlinedButton(onClick = {
                val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsRuntimePermission) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
            }) { Text("Allow Notifications") }
        }
        when (promotion) {
            Promotion.UNSUPPORTED_VERSION -> Hint("Live Updates (promoted notifications) need Android 16+. On this version the game shows as a regular ongoing notification.")
            Promotion.UNSUPPORTED_BUILD -> {
                StatusRow("Live Updates", "Not available on this build", false)
                Hint("This Android 16 build doesn't include Live Updates yet (they shipped in Android 16 QPR1). The game shows as a regular ongoing notification. Use an Android 16 QPR1+ device or emulator image to see it promoted.")
            }
            Promotion.ALLOWED -> StatusRow("Live Updates", "Allowed", true)
            Promotion.TURNED_OFF -> {
                StatusRow("Live Updates", "Turned off for this app", false)
                OutlinedButton(onClick = {
                    openAppSettings(context, Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                }) { Text("Open Live Update Settings") }
            }
        }
    }
}

@Composable
private fun AblyPushSection(pushManager: AblyPushManager) {
    val state by pushManager.state.collectAsState()
    var serverUrl by rememberSaveable { mutableStateOf(pushManager.savedServerUrl ?: DEFAULT_SERVER_URL) }
    var channel by rememberSaveable { mutableStateOf("games:lal-bos") }

    Section("Ably Push Activation") {
        Hint("Registers this device with Ably push (FCM) so the server can start and update the Live Update remotely. Authenticates against the server's authUrl token endpoint — no API key on the device.")
        OutlinedTextField(
            serverUrl,
            { serverUrl = it },
            Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            singleLine = true,
            enabled = !state.isActivated && !state.isActivating,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        if (state.isActivated) {
            StatusRow("Device", "Activated", true)
            CopyableValue("Ably Device ID", state.deviceId)
            Hint("Subscribe this device to an Ably channel so the server can target it by channel name.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    channel,
                    { channel = it },
                    Modifier.weight(1f),
                    label = { Text("Ably channel name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { pushManager.subscribe(channel) }, enabled = channel.isNotBlank()) {
                    Text("Subscribe")
                }
            }
            state.subscribedChannel?.let { StatusRow("Subscribed", it, true) }
            OutlinedButton(
                onClick = { pushManager.deactivate() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Deactivate Device") }
        } else {
            Button(onClick = { pushManager.activate(serverUrl) }, enabled = !state.isActivating) {
                Text("Activate Device with Ably")
                if (state.isActivating) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        state.statusMessage?.let { Hint(it) }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WidgetSection() {
    val context = LocalContext.current
    var pinUnsupported by remember { mutableStateOf(false) }
    Section("Home-Screen Widget") {
        Hint("The Game Score widget shows the same live score and updates with every push.")
        OutlinedButton(onClick = { pinUnsupported = !GameScoreWidgetProvider.requestPin(context) }) {
            Text("Add Widget to Home Screen")
        }
        if (pinUnsupported) {
            Hint("This launcher can't add widgets from the app — long-press the home screen and add “Game Score” from the widget list.")
        }
    }
}

// --- Building blocks --------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CopyableValue(label: String, value: String?) {
    val context = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value ?: "Unavailable",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            if (value != null) {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(label, value))
                }) { Text("Copy") }
            }
        }
    }
}

private enum class Promotion { UNSUPPORTED_VERSION, UNSUPPORTED_BUILD, ALLOWED, TURNED_OFF }

// The first Android 16 builds have the promotion API but not the feature:
// canPostPromotedNotifications() is always false and there is no settings
// screen to turn it on. The missing screen tells those builds apart from a
// user who switched Live Updates off.
private fun promotionState(context: Context): Promotion {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return Promotion.UNSUPPORTED_VERSION
    if (context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()) {
        return Promotion.ALLOWED
    }
    val settingsScreen = context.packageManager.resolveActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS),
        PackageManager.MATCH_DEFAULT_ONLY,
    )
    return if (settingsScreen == null) Promotion.UNSUPPORTED_BUILD else Promotion.TURNED_OFF
}

// Not every device ships a screen for every settings action (e.g. the Android 16
// emulator has none for APP_NOTIFICATION_PROMOTION_SETTINGS), so fall back to
// the app's notification settings, then to its app info page.
private fun openAppSettings(context: Context, action: String) {
    val intents = listOf(
        Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
