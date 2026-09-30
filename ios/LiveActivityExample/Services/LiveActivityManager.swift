import ActivityKit
import Foundation

@Observable
final class LiveActivityManager {
    var currentActivity: Activity<GameAttributes>?
    var activityUpdateToken: String?
    var pushToStartToken: String?
    var errorMessage: String?

    var isActivityRunning: Bool { currentActivity != nil }
    var activityId: String? { currentActivity?.id }

    // MARK: - Push-to-Start Token

    func observePushToStartToken() {
        Task {
            for await tokenData in Activity<GameAttributes>.pushToStartTokenUpdates {
                let token = tokenData.map { String(format: "%02x", $0) }.joined()
                await MainActor.run { self.pushToStartToken = token }
            }
        }
    }

    // MARK: - Start Activity Locally

    // Start a Live Activity directly from the app via ActivityKit.
    // When `channelId` is non-empty the activity subscribes to that APNS
    // broadcast channel (`pushType: .channel`) so it can be updated via
    // broadcast; otherwise it uses a per-activity push token (`.token`).
    func startActivity(homeTeam: String, awayTeam: String, channelId: String? = nil) async {
        let attributes = GameAttributes(homeTeam: homeTeam, awayTeam: awayTeam)
        let initialState = GameAttributes.ContentState(
            homeScore: 0,
            awayScore: 0,
            gameStatus: .scheduled,
            period: "Q1",
            clock: "12:00",
            lastPlay: "Tip-off soon"
        )
        let content = ActivityContent(state: initialState, staleDate: nil)

        let trimmedChannel = channelId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let pushType: PushType = (trimmedChannel?.isEmpty == false)
            ? .channel(trimmedChannel!)
            : .token

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: pushType
            )
            await MainActor.run { self.currentActivity = activity }
            observeActivityTokens(activity: activity)
        } catch {
            await MainActor.run { self.errorMessage = error.localizedDescription }
        }
    }

    // MARK: - Token Observation

    private func observeActivityTokens(activity: Activity<GameAttributes>) {
        Task {
            for await tokenData in activity.pushTokenUpdates {
                let token = tokenData.map { String(format: "%02x", $0) }.joined()
                await MainActor.run { self.activityUpdateToken = token }
            }
        }
    }

    // MARK: - End Activity

    func endActivity() async {
        guard let activity = currentActivity else { return }
        let finalState = GameAttributes.ContentState(
            homeScore: activity.content.state.homeScore,
            awayScore: activity.content.state.awayScore,
            gameStatus: .finished,
            period: "Final",
            clock: "",
            lastPlay: "Final"
        )
        let finalContent = ActivityContent(state: finalState, staleDate: nil)
        await activity.end(finalContent, dismissalPolicy: .after(.now + 300))
        await MainActor.run {
            self.currentActivity = nil
            self.activityUpdateToken = nil
        }
    }

    // MARK: - Restore on App Launch

    func restoreExistingActivities() {
        for activity in Activity<GameAttributes>.activities {
            currentActivity = activity
            observeActivityTokens(activity: activity)
            break
        }
    }
}
