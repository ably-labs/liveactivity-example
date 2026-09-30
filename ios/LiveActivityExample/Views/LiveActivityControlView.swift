import SwiftUI

struct LiveActivityControlView: View {
    let manager: LiveActivityManager
    let pushManager: AblyPushManager

    @State private var homeTeam = "Lakers"
    @State private var awayTeam = "Celtics"
    @State private var channelId = ""
    @State private var serverURL = "http://localhost:3000"
    @State private var pushChannel = ""

    var body: some View {
        Form {
            Section("Game Setup") {
                TextField("Home Team", text: $homeTeam)
                TextField("Away Team", text: $awayTeam)
            }

            Section("Activity Control") {
                if manager.isActivityRunning {
                    Label("Live Activity Running", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    if let id = manager.activityId {
                        Text("ID: \(id)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Button("End Activity", role: .destructive) {
                        Task { await manager.endActivity() }
                    }
                } else {
                    Button("Start Live Activity Locally") {
                        Task {
                            await manager.startActivity(
                                homeTeam: homeTeam,
                                awayTeam: awayTeam,
                                channelId: channelId
                            )
                        }
                    }
                }
            }

            Section("Broadcast Channel (optional)") {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Paste an APNS broadcast channel ID to subscribe this locally-started activity to it, so it can be updated via broadcast. Leave empty to use a per-activity push token.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TextField("APNS Channel ID", text: $channelId)
                        .font(.system(.caption, design: .monospaced))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .disabled(manager.isActivityRunning)
                }
            }

            Section("Push-to-Start Token") {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Paste this into the server dashboard to start a Live Activity remotely.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TokenDisplayView(
                        label: "Device Token",
                        token: manager.pushToStartToken,
                        placeholder: "Waiting for token..."
                    )
                }
            }

            Section("Ably Push Activation") {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Register this device's push-to-start token with Ably so it can be push-to-started via broadcast. Authenticates against the server's authUrl token endpoint — no API key on the device.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TextField("Server URL", text: $serverURL)
                        .font(.system(.caption, design: .monospaced))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .disabled(pushManager.isActivated || pushManager.isActivating)
                }

                if pushManager.isActivated {
                    Label("Device Activated", systemImage: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                    TokenDisplayView(
                        label: "Device ID",
                        token: pushManager.deviceId,
                        placeholder: "Unavailable"
                    )

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Subscribe this device to an Ably channel so the server can push-to-start it by channel name.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack {
                            TextField("Ably channel name", text: $pushChannel)
                                .font(.system(.caption, design: .monospaced))
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                            Button("Subscribe") {
                                pushManager.subscribe(toChannel: pushChannel)
                            }
                            .buttonStyle(.borderless)
                            .disabled(pushChannel.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        if let channel = pushManager.subscribedChannel {
                            Label("Subscribed to \(channel)", systemImage: "dot.radiowaves.left.and.right")
                                .font(.caption)
                                .foregroundStyle(.green)
                        }
                    }

                    Button("Deactivate Device", role: .destructive) {
                        pushManager.deactivate()
                    }
                } else {
                    Button {
                        if let token = manager.pushToStartToken {
                            pushManager.activate(serverBaseURL: serverURL, pushToStartToken: token)
                        }
                    } label: {
                        HStack {
                            Text("Activate Device with Ably")
                            if pushManager.isActivating {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(manager.pushToStartToken == nil || pushManager.isActivating)

                    if manager.pushToStartToken == nil {
                        Text("Waiting for a push-to-start token from iOS…")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                if let status = pushManager.statusMessage {
                    Text(status)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let error = pushManager.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            if manager.isActivityRunning {
                Section("Activity Update Token") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Paste into the server dashboard to update this specific activity.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        TokenDisplayView(
                            label: "Update Token",
                            token: manager.activityUpdateToken,
                            placeholder: "Waiting for token..."
                        )
                    }
                }
            }

            if let error = manager.errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                }
            }
        }
    }
}
