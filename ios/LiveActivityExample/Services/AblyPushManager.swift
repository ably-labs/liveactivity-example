import Ably
import Foundation
import UIKit

// Registers this device with Ably and adds its Live Activity push-to-start token.
//
// Flow: call `push.activate()` (standard activation — requests a normal APNs
// device token), then once the device is activated, add the Live Activity
// push-to-start token to the existing registration via
// `push.registerPushToStartToken(_:)`. Authentication uses Ably token auth via
// the server's `authUrl` endpoint, so the API key never reaches the device.
@Observable
final class AblyPushManager: NSObject, ARTPushRegistererDelegate {
    // The app delegate forwards APNs device-token callbacks here; expose the
    // active manager so it can reach the ARTRest instance.
    static weak var shared: AblyPushManager?

    var isActivating = false
    var isActivated = false
    var deviceId: String?
    var subscribedChannel: String?
    var statusMessage: String?
    var errorMessage: String?

    // Retained for the lifetime of the activation so its push registerer
    // delegate (self) stays wired and the device state persists.
    private var rest: ARTRest?
    // Held until the device is activated, then registered with Ably. The
    // ably-cocoa API takes the raw token bytes (Data), not the hex string.
    private var pendingPushToStartToken: Data?

    // Activate the device with Ably, then register the push-to-start token.
    // `serverBaseURL` is the base of the dashboard server (its `/api/auth`
    // endpoint mints the Ably TokenRequest consumed via authUrl).
    // `pushToStartToken` is the hex string surfaced in the UI; it is converted
    // back to the raw Data the SDK expects.
    func activate(serverBaseURL: String, pushToStartToken: String) {
        let base = serverBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let authUrl = URL(string: "\(base)/api/auth") else {
            errorMessage = "Invalid server URL"
            return
        }
        guard let tokenData = Data(hexString: pushToStartToken) else {
            errorMessage = "Invalid push-to-start token"
            return
        }

        let options = ARTClientOptions()
        options.authUrl = authUrl
        options.authMethod = "GET"
        options.pushRegistererDelegate = self
        let rest = ARTRest(options: options)
        self.rest = rest
        self.pendingPushToStartToken = tokenData
        Self.shared = self

        isActivating = true
        isActivated = false
        errorMessage = nil
        statusMessage = "Activating device…"

        // Standard activation: requests a normal APNs device token, which the
        // app delegate forwards back into the SDK.
        rest.push.activate()
    }

    func deactivate() {
        statusMessage = "Deactivating device…"
        rest?.push.deactivate()
    }

    // Subscribe this device to push notifications on an Ably channel, so the
    // server can target it by channel name when it push-to-starts.
    func subscribe(toChannel name: String) {
        let channelName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !channelName.isEmpty else {
            errorMessage = "Enter a channel name to subscribe to"
            return
        }
        guard let rest else {
            errorMessage = "Activate the device before subscribing"
            return
        }
        statusMessage = "Subscribing device to \(channelName)…"
        rest.channels.get(channelName).push.subscribeDevice { [weak self] error in
            DispatchQueue.main.async {
                if let error {
                    self?.errorMessage = "Channel subscribe failed: \(error.message)"
                } else {
                    self?.errorMessage = nil
                    self?.subscribedChannel = channelName
                    self?.statusMessage = "Subscribed to push on \(channelName)"
                }
            }
        }
    }

    // MARK: - APNs token forwarding (called from the app delegate)

    func didRegisterForRemoteNotifications(deviceToken: Data) {
        guard let rest else { return }
        ARTPush.didRegisterForRemoteNotifications(withDeviceToken: deviceToken, rest: rest)
    }

    func didFailToRegisterForRemoteNotifications(error: Error) {
        guard let rest else { return }
        ARTPush.didFailToRegisterForRemoteNotificationsWithError(error, rest: rest)
    }

    // MARK: - ARTPushRegistererDelegate

    func didActivateAblyPush(_ error: ARTErrorInfo?) {
        DispatchQueue.main.async {
            if let error {
                self.isActivating = false
                self.isActivated = false
                self.errorMessage = "Activation failed: \(error.message)"
                self.statusMessage = nil
                return
            }
            self.isActivated = true
            self.errorMessage = nil
            self.deviceId = self.rest?.device.id
            self.statusMessage = "Device activated — registering push-to-start token…"

            // Device is registered; now add the Live Activity push-to-start
            // token. Completion is reported via didUpdateAblyPush.
            if let token = self.pendingPushToStartToken {
                self.rest?.push.registerPushToStartToken(token)
            } else {
                self.isActivating = false
            }
        }
    }

    func didUpdateAblyPush(_ error: ARTErrorInfo?) {
        DispatchQueue.main.async {
            self.isActivating = false
            if let error {
                self.errorMessage = "Push-to-start registration failed: \(error.message)"
            } else {
                self.errorMessage = nil
                self.statusMessage = "Device activated with push-to-start token"
            }
        }
    }

    func didDeactivateAblyPush(_ error: ARTErrorInfo?) {
        DispatchQueue.main.async {
            self.isActivated = false
            self.deviceId = nil
            self.subscribedChannel = nil
            self.pendingPushToStartToken = nil
            if let error {
                self.errorMessage = "Deactivation failed: \(error.message)"
            } else {
                self.errorMessage = nil
                self.statusMessage = "Device deactivated"
            }
        }
    }

    func didAblyPushRegistrationFail(_ error: ARTErrorInfo?) {
        DispatchQueue.main.async {
            self.isActivating = false
            self.errorMessage = "Registration failed: \(error?.message ?? "unknown error")"
        }
    }
}

private extension Data {
    /// Build `Data` from a hex string (the inverse of the `%02x` encoding used
    /// to display the push-to-start token). Returns nil if the string isn't
    /// valid hex.
    init?(hexString: String) {
        let hex = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !hex.isEmpty, hex.count % 2 == 0 else { return nil }
        var bytes = Data(capacity: hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self = bytes
    }
}
