import ActivityKit
import SwiftUI
import WidgetKit

struct GameScoreLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: GameAttributes.self) { context in
            LockScreenView(context: context)
                .activityBackgroundTint(Color.black.opacity(0.85))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TeamScoreView(
                        team: context.attributes.homeTeam,
                        score: context.state.homeScore,
                        alignment: .leading
                    )
                }
                DynamicIslandExpandedRegion(.trailing) {
                    TeamScoreView(
                        team: context.attributes.awayTeam,
                        score: context.state.awayScore,
                        alignment: .trailing
                    )
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        StatusBadge(status: context.state.gameStatus)
                        Text(context.state.clockLine)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.lastPlay)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
            } compactLeading: {
                Text("\(context.state.homeScore)")
                    .font(.headline).bold()
                    .monospacedDigit()
            } compactTrailing: {
                Text("\(context.state.awayScore)")
                    .font(.headline).bold()
                    .monospacedDigit()
            } minimal: {
                Text("\(context.state.homeScore)-\(context.state.awayScore)")
                    .font(.caption2).bold()
                    .monospacedDigit()
            }
        }
    }
}

// MARK: - Lock Screen View

private struct LockScreenView: View {
    let context: ActivityViewContext<GameAttributes>

    var body: some View {
        HStack(spacing: 0) {
            TeamScoreView(
                team: context.attributes.homeTeam,
                score: context.state.homeScore,
                alignment: .center
            )
            .frame(maxWidth: .infinity)

            VStack(spacing: 6) {
                StatusBadge(status: context.state.gameStatus)
                Text(context.state.clockLine)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
                Text(context.state.lastPlay)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity)

            TeamScoreView(
                team: context.attributes.awayTeam,
                score: context.state.awayScore,
                alignment: .center
            )
            .frame(maxWidth: .infinity)
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 16)
    }
}

// MARK: - Subviews

private struct TeamScoreView: View {
    let team: String
    let score: Int
    let alignment: HorizontalAlignment

    var body: some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(team)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Text("\(score)")
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
        }
    }
}

private struct StatusBadge: View {
    let status: GameAttributes.ContentState.GameStatus

    var body: some View {
        Text(status.label)
            .font(.caption2)
            .fontWeight(.bold)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(badgeColor.opacity(0.25))
            .foregroundStyle(badgeColor)
            .clipShape(Capsule())
    }

    private var badgeColor: Color {
        switch status {
        case .scheduled: return .blue
        case .live: return .red
        case .halftime: return .orange
        case .finished: return .gray
        }
    }
}

// MARK: - Presentation helpers

extension GameAttributes.ContentState {
    /// The period, with the game clock appended while the game is live.
    var clockLine: String {
        clock.isEmpty ? period : "\(period) · \(clock)"
    }
}

extension GameAttributes.ContentState.GameStatus {
    var label: String {
        switch self {
        case .scheduled: return "SCHEDULED"
        case .live: return "LIVE"
        case .halftime: return "HALFTIME"
        case .finished: return "FINAL"
        }
    }
}
