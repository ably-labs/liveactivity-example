package com.ably.example.liveupdate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ably.example.liveupdate.R
import com.ably.example.liveupdate.model.GameStatus
import com.ably.example.liveupdate.model.GameStore
import com.ably.example.liveupdate.ui.MainActivity

// Home-screen score widget. It never polls (updatePeriodMillis = 0): LiveGame
// calls refresh() whenever a push changes the game, and the system calls
// onUpdate() when a widget is added or the launcher needs it redrawn.
class GameScoreWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GameScoreWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                manager.updateAppWidget(ids, buildViews(context))
            }
        }

        /** Asks the launcher to add the widget; returns false if it can't. */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, GameScoreWidgetProvider::class.java), null, null)
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_game_score)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            val state = GameStore.load(context)?.state
            if (state == null) {
                views.setViewVisibility(R.id.game, View.GONE)
                views.setViewVisibility(R.id.empty, View.VISIBLE)
                return views
            }

            views.setViewVisibility(R.id.game, View.VISIBLE)
            views.setViewVisibility(R.id.empty, View.GONE)
            views.setTextViewText(R.id.home_team, state.homeTeam)
            views.setTextViewText(R.id.away_team, state.awayTeam)
            views.setTextViewText(R.id.home_score, state.homeScore.toString())
            views.setTextViewText(R.id.away_score, state.awayScore.toString())
            views.setTextViewText(R.id.status, state.gameStatus.label)
            views.setTextColor(R.id.status, context.getColor(state.gameStatus.colorRes))
            views.setTextViewText(R.id.clock, state.clockLine)
            views.setTextViewText(R.id.last_play, state.lastPlay)
            return views
        }

        private val GameStatus.colorRes: Int
            get() = when (this) {
                GameStatus.SCHEDULED -> R.color.status_scheduled
                GameStatus.LIVE -> R.color.status_live
                GameStatus.HALFTIME -> R.color.status_halftime
                GameStatus.FINISHED -> R.color.status_finished
            }
    }
}
