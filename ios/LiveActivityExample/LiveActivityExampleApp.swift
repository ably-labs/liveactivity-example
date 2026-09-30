import SwiftUI
import UIKit

// Forwards APNs remote-notification registration callbacks into the Ably SDK,
// which the standard push activation flow (push.activate()) depends on.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        AblyPushManager.shared?.didRegisterForRemoteNotifications(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        AblyPushManager.shared?.didFailToRegisterForRemoteNotifications(error: error)
    }
}

@main
struct LiveActivityExampleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
