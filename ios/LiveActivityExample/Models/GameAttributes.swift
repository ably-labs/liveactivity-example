import ActivityKit
import Foundation

// Models an NBA basketball game as a Live Activity. The struct name is a wire
// contract: the server's APNs payload sets `attributes-type: "GameAttributes"`.
struct GameAttributes: ActivityAttributes {
    let homeTeam: String
    let awayTeam: String

    struct ContentState: Codable, Hashable {
        var homeScore: Int
        var awayScore: Int
        var gameStatus: GameStatus
        var period: String   // "Q1"…"Q4", "OT", "Half", "Final"
        var clock: String    // "7:32"; empty when not applicable
        var lastPlay: String

        enum GameStatus: String, Codable, Hashable {
            case scheduled
            case live
            case halftime
            case finished
        }
    }
}
