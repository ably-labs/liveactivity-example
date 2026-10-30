'use strict';

// Drives Android Live Updates (promoted ongoing notifications, Android 16+)
// and the home-screen score widget through Ably push over FCM. Ably holds the
// Firebase service account (configured in the Ably app), so this server only
// needs an Ably API key.
//
// Android has no server-rendered equivalent of a Live Activity: the app builds
// the notification and widget itself. Every event is therefore a data-only
// push carrying the full game state, which the app's FirebaseMessagingService
// renders on arrival:
//
//   start()  : show the Live Update (ongoing, promoted) and refresh the widget.
//   update() : replace the Live Update content and refresh the widget.
//   end()    : show the final score as a dismissable notification.
//
// Devices are targeted by FCM topic: the app subscribes itself to a topic
// through Ably (POST /push/fcmTopics/{topic}/subscriptions/{deviceId}), and
// each event is a single POST /push/fcmTopics/{topic}/messages that FCM fans
// out to every subscribed device. Ably resolves devices to FCM tokens and calls
// FCM with the app's service account. There's no ably-js wrapper for these
// endpoints yet, so they're called through `rest.request()`.
class AblyLiveUpdate {
  // `getRest` returns the shared Ably Rest client (see ably-rest.js).
  constructor({ getRest }) {
    this._getRest = getRest;
  }

  get rest() {
    return this._getRest();
  }

  start({ topic, homeTeam, awayTeam }) {
    return this._send({
      topic,
      event: 'start',
      state: {
        homeTeam,
        awayTeam,
        homeScore: 0,
        awayScore: 0,
        gameStatus: 'scheduled',
        period: 'Q1',
        clock: '12:00',
        lastPlay: 'Tip-off soon',
      },
    });
  }

  update({ topic, homeTeam, awayTeam, homeScore, awayScore, gameStatus, period, clock, lastPlay }) {
    return this._send({
      topic,
      event: 'update',
      state: {
        homeTeam,
        awayTeam,
        homeScore: homeScore ?? 0,
        awayScore: awayScore ?? 0,
        gameStatus: gameStatus ?? 'live',
        period: period ?? 'Q1',
        clock: clock ?? '',
        lastPlay: lastPlay ?? '',
      },
    });
  }

  end({ topic, homeTeam, awayTeam, homeScore, awayScore }) {
    return this._send({
      topic,
      event: 'end',
      state: {
        homeTeam,
        awayTeam,
        homeScore: homeScore ?? 0,
        awayScore: awayScore ?? 0,
        gameStatus: 'finished',
        period: 'Final',
        clock: '',
        lastPlay: 'Final',
      },
    });
  }

  async _send({ topic, event, state }) {
    // FCM data values must be strings. `timestamp` lets the app drop pushes
    // that arrive out of order, like `aps.timestamp` does for Live Activities.
    const data = Object.fromEntries(
      Object.entries({ event, ...state, timestamp: Date.now() }).map(([k, v]) => [k, String(v)]),
    );
    // Fields are FCM v1 message fields, passed to FCM as-is. Data-only (no
    // `notification`), so the app's onMessageReceived always runs and renders
    // the Live Update itself; high priority wakes the app in Doze.
    const body = { data, android: { priority: 'high' } };

    const res = await this.rest.request(
      'POST',
      `/push/fcmTopics/${encodeURIComponent(topic)}/messages`,
      6,
      null,
      body,
    );
    // request() resolves on HTTP errors too, so surface them as rejections.
    if (!res.success) {
      throw new Error(`${res.errorMessage} (${res.errorCode})`);
    }
    return res.items[0];
  }
}

module.exports = AblyLiveUpdate;
