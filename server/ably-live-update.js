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
// Devices are targeted the same way as iOS push-to-start: by the Ably channels
// they subscribed to (one publish fans out to every subscribed device) and/or
// directly by Ably device ID.
class AblyLiveUpdate {
  // `getRest` returns the shared Ably Rest client (see ably-rest.js).
  constructor({ getRest }) {
    this._getRest = getRest;
  }

  get rest() {
    return this._getRest();
  }

  start({ channels, deviceId, homeTeam, awayTeam }) {
    return this._send({
      channels,
      deviceId,
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

  update({ channels, deviceId, homeTeam, awayTeam, homeScore, awayScore, gameStatus, period, clock, lastPlay }) {
    return this._send({
      channels,
      deviceId,
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

  end({ channels, deviceId, homeTeam, awayTeam, homeScore, awayScore }) {
    return this._send({
      channels,
      deviceId,
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

  _send({ channels, deviceId, event, state }) {
    // FCM data values must be strings. `timestamp` lets the app drop pushes
    // that arrive out of order, like `aps.timestamp` does for Live Activities.
    const data = Object.fromEntries(
      Object.entries({ event, ...state, timestamp: Date.now() }).map(([k, v]) => [k, String(v)]),
    );
    // Data-only (no `notification`), so the app's onMessageReceived always
    // runs and renders the Live Update itself. The `fcm` override is merged
    // into the FCM AndroidConfig; high priority wakes the app in Doze.
    const push = { data, fcm: { priority: 'HIGH' } };

    const publishes = (channels ?? []).map((name) =>
      this.rest.channels.get(name).publish({ name: 'live-update', data: state, extras: { push } }),
    );
    if (deviceId) {
      publishes.push(this.rest.push.admin.publish({ deviceId }, push));
    }
    return Promise.all(publishes);
  }
}

module.exports = AblyLiveUpdate;
