'use strict';

// Drives iOS Live Activities over an APNs broadcast channel via Ably's push
// admin API. Ably holds the APNs auth key (configured in the Ably app), so this
// server only needs an Ably API key — no .p8 / JWT signing of its own.
//
// Lifecycle:
//   1. createBroadcast()        -> { id, apnsChannelId }
//      `id`            : opaque Ably broadcast id, used in every later call.
//      `apnsChannelId` : Apple channel id the iOS app subscribes to
//                        (pushType: .channel(apnsChannelId)).
//   2. start()  : push-to-start to devices subscribed to the given Ably channels.
//   3. update() : single push fanned out by APNs to all subscribed devices.
//   4. end()    : ends the activity everywhere and invalidates the broadcast id.
//
// The `apns` payloads mirror the iOS `GameAttributes` contract (an NBA
// basketball game: teams, points, status, period, clock, last play). Ably
// passes them to APNs as-is.
class AblyLiveActivity {
  // `getRest` returns the shared Ably Rest client (see ably-rest.js).
  constructor({ getRest }) {
    this._getRest = getRest;
  }

  get rest() {
    return this._getRest();
  }

  // Create an APNs broadcast channel. messageStoragePolicy: 1 caches the last
  // update so late-joining devices get the current content-state on subscribe.
  createBroadcast() {
    return this.rest.push.admin.createApnsBroadcast({ messageStoragePolicy: 1 });
  }

  // Mint a signed Ably TokenRequest for a client (the iOS app), used as the
  // response to its `authUrl`. The capability includes `push-subscribe` so the
  // device can activate/register itself for push (Live Activity push-to-start)
  // and subscribe to channels; the API key stays on the server.
  createTokenRequest({ clientId } = {}) {
    return this.rest.auth.createTokenRequest({
      ...(clientId ? { clientId } : {}),
      capability: JSON.stringify({
        '*': ['subscribe', 'publish', 'presence', 'history', 'push-subscribe'],
      }),
    });
  }

  // Push-to-start a new Live Activity. Targets either the devices subscribed to
  // the given Ably channels, a specific Ably deviceId, or both.
  start({ channels, deviceId, apnsBroadcast, apnsChannelId, homeTeam, awayTeam }) {
    const hasChannels = Array.isArray(channels) && channels.length > 0;
    const recipient = {
      ...(hasChannels ? { channels } : {}),
      ...(deviceId ? { deviceId } : {}),
    };

    const apns = {
      aps: {
        timestamp: Math.floor(Date.now() / 1000),
        event: 'start',
        'input-push-channel': apnsChannelId,
        'content-state': {
          homeScore: 0,
          awayScore: 0,
          gameStatus: 'scheduled',
          period: 'Q1',
          clock: '12:00',
          lastPlay: 'Tip-off soon',
        },
        'attributes-type': 'GameAttributes',
        attributes: { homeTeam, awayTeam },
        alert: {
          title: `${homeTeam} vs ${awayTeam}`,
          body: 'Game starting!',
        },
      },
    };

    return this.rest.push.admin.liveActivity.start({
      recipient,
      apnsBroadcast,
      apns,
      headers: { 'apns-priority': '10' },
    });
  }

  // Broadcast a content-state update to all subscribed activities.
  update({ apnsBroadcast, homeScore, awayScore, gameStatus, period, clock, lastPlay }) {
    const apns = {
      aps: {
        timestamp: Math.floor(Date.now() / 1000),
        event: 'update',
        'content-state': {
          homeScore: homeScore ?? 0,
          awayScore: awayScore ?? 0,
          gameStatus: gameStatus ?? 'live',
          period: period ?? 'Q1',
          clock: clock ?? '',
          lastPlay: lastPlay ?? '',
        },
      },
    };

    return this.rest.push.admin.liveActivity.update({
      apnsBroadcast,
      apns,
      headers: {
        'apns-priority': '10',
        'apns-expiration': String(Math.floor(Date.now() / 1000) + 3600),
      }
    });
  }

  // Broadcast an end event and clean up the channel.
  end({ apnsBroadcast, homeScore, awayScore }) {
    const apns = {
      aps: {
        timestamp: Math.floor(Date.now() / 1000),
        event: 'end',
        'content-state': {
          homeScore: homeScore ?? 0,
          awayScore: awayScore ?? 0,
          gameStatus: 'finished',
          period: 'Final',
          clock: '',
          lastPlay: 'Final',
        },
        'dismissal-date': Math.floor(Date.now() / 1000) + 3600,
      },
    };

    return this.rest.push.admin.liveActivity.end({
      apnsBroadcast,
      apns,
      headers: { 'apns-priority': '10' },
    });
  }
}

module.exports = AblyLiveActivity;
