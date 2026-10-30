'use strict';

require('dotenv').config();
const express = require('express');
const path = require('node:path');
const lazyRest = require('./ably-rest');
const AblyLiveActivity = require('./ably-live-activity');
const AblyLiveUpdate = require('./ably-live-update');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const ablyEndpoint = process.env.ABLY_ENDPOINT || null;
const getRest = lazyRest(process.env.ABLY_API_KEY, ablyEndpoint);
const liveActivity = new AblyLiveActivity({ getRest });
const liveUpdate = new AblyLiveUpdate({ getRest });

// Client config for the iOS and Android apps. They fetch this before creating
// their Ably client so they talk to the same Ably environment as the server —
// a TokenRequest signed with a sandbox key is rejected by production.
// `ablyHost` is the host ably-js resolved from ABLY_ENDPOINT (e.g.
// `nonprod:sandbox` → `sandbox.realtime.ably-nonprod.net`); the apps' SDKs
// predate the `endpoint` option, so they take it as `restHost`. It's null for
// production, leaving the apps on their defaults (and fallback hosts).
app.get('/api/config', (req, res) => {
  try {
    res.json({ ablyHost: ablyEndpoint ? getRest().options.primaryDomain : null });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Ably token auth endpoint. The iOS and Android apps point their Ably `authUrl`
// here; we return a signed TokenRequest so the device can authenticate (and
// activate itself for push) without ever seeing the API key.
app.get('/api/auth', async (req, res) => {
  try {
    const tokenRequest = await liveActivity.createTokenRequest({ clientId: req.query.clientId });
    res.json(tokenRequest);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Create an APNs broadcast channel via Ably.
// Returns the Ably broadcast `id` (used for start/update/end) and the
// `apnsChannelId` the iOS app subscribes to.
app.post('/api/broadcasts', async (req, res) => {
  try {
    const { id, apnsChannelId } = await liveActivity.createBroadcast();
    res.json({ success: true, id, apnsChannelId });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Push-to-start a Live Activity on devices subscribed to the given Ably channels.
app.post('/api/live-activity/start', async (req, res) => {
  const { channels, deviceId, apnsBroadcast, apnsChannelId, homeTeam, awayTeam } = req.body;
  if (!apnsBroadcast) {
    return res.status(400).json({ error: 'apnsBroadcast is required' });
  }
  if (!apnsChannelId) {
    return res.status(400).json({ error: 'apnsChannelId is required' });
  }
  const hasChannels = Array.isArray(channels) && channels.length > 0;
  if (!hasChannels && !deviceId) {
    return res.status(400).json({ error: 'at least one channel or a deviceId is required' });
  }
  if (!homeTeam || !awayTeam) {
    return res.status(400).json({ error: 'homeTeam and awayTeam are required' });
  }
  try {
    await liveActivity.start({ channels, deviceId, apnsBroadcast, apnsChannelId, homeTeam, awayTeam });
    res.json({ success: true, apnsChannelId });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Broadcast an update to all subscribed Live Activities.
app.post('/api/live-activity/update', async (req, res) => {
  const { apnsBroadcast, homeScore, awayScore, gameStatus, period, clock, lastPlay } = req.body;
  if (!apnsBroadcast) {
    return res.status(400).json({ error: 'apnsBroadcast is required' });
  }
  try {
    await liveActivity.update({ apnsBroadcast, homeScore, awayScore, gameStatus, period, clock, lastPlay });
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Broadcast an end event to all subscribed Live Activities.
app.post('/api/live-activity/end', async (req, res) => {
  const { apnsBroadcast, homeScore, awayScore } = req.body;
  if (!apnsBroadcast) {
    return res.status(400).json({ error: 'apnsBroadcast is required' });
  }
  try {
    await liveActivity.end({ apnsBroadcast, homeScore, awayScore });
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- Android Live Updates -------------------------------------------------
// Every Android event carries the full game state and is published to the
// FCM topic the devices subscribed to in the app.

function validateAndroidTarget(body) {
  const { topic, homeTeam, awayTeam } = body;
  if (!topic) {
    return 'topic is required';
  }
  if (!homeTeam || !awayTeam) {
    return 'homeTeam and awayTeam are required';
  }
  return null;
}

for (const event of ['start', 'update', 'end']) {
  app.post(`/api/android/${event}`, async (req, res) => {
    const error = validateAndroidTarget(req.body);
    if (error) {
      return res.status(400).json({ error });
    }
    try {
      await liveUpdate[event](req.body);
      res.json({ success: true });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });
}

const PORT = process.env.PORT || 3000;

const server = app.listen(PORT, () => {
  console.log(`Dashboard running at http://localhost:${PORT}`);
});
process.on('SIGTERM', () => server.close());
process.on('SIGINT', () => server.close());
