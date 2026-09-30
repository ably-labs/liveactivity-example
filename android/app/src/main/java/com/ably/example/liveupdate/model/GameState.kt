package com.ably.example.liveupdate.model

import org.json.JSONObject

// Models an NBA basketball game, mirroring the iOS `GameAttributes`. The field
// names are the wire contract: the server sends them as FCM data keys.
data class GameState(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val gameStatus: GameStatus,
    val period: String, // "Q1"…"Q4", "OT", "Final"
    val clock: String, // "7:32"; empty when not applicable
    val lastPlay: String,
) {
    /** The period, with the game clock appended while the game is running. */
    val clockLine: String get() = if (clock.isEmpty()) period else "$period · $clock"

    val scoreLine: String get() = "$homeScore–$awayScore"

    fun finished(): GameState =
        copy(gameStatus = GameStatus.FINISHED, period = "Final", clock = "", lastPlay = "Final")

    fun toMap(): Map<String, String> = mapOf(
        "homeTeam" to homeTeam,
        "awayTeam" to awayTeam,
        "homeScore" to homeScore.toString(),
        "awayScore" to awayScore.toString(),
        "gameStatus" to gameStatus.wireName,
        "period" to period,
        "clock" to clock,
        "lastPlay" to lastPlay,
    )

    fun toJson(): JSONObject = JSONObject(toMap())

    companion object {
        fun initial(homeTeam: String, awayTeam: String) = GameState(
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = 0,
            awayScore = 0,
            gameStatus = GameStatus.SCHEDULED,
            period = "Q1",
            clock = "12:00",
            lastPlay = "Tip-off soon",
        )

        /** Parses FCM data (all values are strings); null if the teams are missing. */
        fun fromMap(data: Map<String, String>): GameState? {
            val homeTeam = data["homeTeam"]?.takeIf { it.isNotBlank() } ?: return null
            val awayTeam = data["awayTeam"]?.takeIf { it.isNotBlank() } ?: return null
            return GameState(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeScore = data["homeScore"]?.toIntOrNull() ?: 0,
                awayScore = data["awayScore"]?.toIntOrNull() ?: 0,
                gameStatus = GameStatus.fromWireName(data["gameStatus"]),
                period = data["period"].orEmpty(),
                clock = data["clock"].orEmpty(),
                lastPlay = data["lastPlay"].orEmpty(),
            )
        }

        fun fromJson(json: JSONObject): GameState? =
            fromMap(json.keys().asSequence().associateWith { json.getString(it) })
    }
}

enum class GameStatus(val wireName: String, val label: String) {
    SCHEDULED("scheduled", "SCHEDULED"),
    LIVE("live", "LIVE"),
    HALFTIME("halftime", "HALFTIME"),
    FINISHED("finished", "FINAL"),
    ;

    companion object {
        fun fromWireName(name: String?): GameStatus = entries.firstOrNull { it.wireName == name } ?: LIVE
    }
}
