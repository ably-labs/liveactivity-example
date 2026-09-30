import SwiftUI

struct ContentView: View {
    @State private var manager = LiveActivityManager()
    @State private var pushManager = AblyPushManager()

    var body: some View {
        NavigationStack {
            LiveActivityControlView(manager: manager, pushManager: pushManager)
                .navigationTitle("NBA Live Activity")
                .navigationBarTitleDisplayMode(.large)
        }
        .task {
            manager.restoreExistingActivities()
            manager.observePushToStartToken()
        }
    }
}
