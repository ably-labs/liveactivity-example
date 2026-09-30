# Live Activities & Live Updates with Ably

Drive iOS **Live Activities** (Lock Screen + Dynamic Island) and Android
**Live Updates** (promoted ongoing notifications + a home-screen widget) from a
Node.js server using [Ably](https://ably.com). The example shows a live NBA
game score: a dashboard in the browser pushes score updates, and every
subscribed iPhone and Android phone updates in real time.

Ably holds the push credentials — the Apple APNs auth key and the Firebase
service account (both uploaded once in the Ably dashboard):

- the **server** needs only an Ably API key (no `.p8` files, no JWT signing,
  no Firebase SDK), no direct APNs or FCM connections;
- the **iOS and Android apps** authenticate with Ably token auth via the
  server's `authUrl` endpoint.

The dashboard has one tab per platform: **iOS Live Activity** and
**Android Live Update**.

Both Live Activity flows are covered, end to end:

| Flow | What happens | Requires |
|---|---|----------|
| **Broadcast updates** | The activity is started on-device and subscribes to an APNs broadcast channel; one push from the server updates *every* subscribed device at once | iOS 18+  |
| **Push-to-start** | The server starts a Live Activity remotely on devices that never opened the flow — targeted by Ably channel or device ID | iOS 18+  |

Android ([Tutorial C](#tutorial-c--android-live-updates)) mirrors these with
a data-only FCM push the app renders itself:

| Flow | What happens | Requires |
|---|---|----------|
| **Remote start / update / end** | The server pushes the full game state to devices subscribed to an Ably channel (one publish, every device) or to one device ID; the app shows it as a Live Update and in the score widget | Android 8+ (Live Update promotion: Android 16+) |

## How it works

```
┌────────────────┐   Ably push admin API   ┌────────┐   APNs    ┌─────────────────┐
│ Node dashboard │ ───────────────────────▶│  Ably  │ ─────────▶│ iPhone           │
│ (ably-js)      │  broadcast / start /    │        │  channel  │  Live Activity   │
│                │  update / end           │ holds  │  fan-out  │  (Lock Screen +  │
│  /api/auth ◀───┼─────────────────────────┼─ .p8 ──┼───────────┤   Dynamic Island)│
└────────────────┘   token auth (authUrl)  └────────┘           └─────────────────┘
```

1. The server creates an **APNs broadcast channel** through Ably and gets back
   a `{ id, apnsChannelId }` pair.
2. The iOS app either starts an activity locally subscribed to that
   `apnsChannelId` (`pushType: .channel`), or registers its **push-to-start
   token** with Ably so the server can start the activity remotely.
3. The dashboard calls Ably's push admin Live Activity API
   (`start` / `update` / `end`); Ably signs the APNs request with your `.p8`
   and APNs fans the update out to every subscribed device.

On Android there is no server-rendered UI like a Live Activity, so every
event is a data-only push carrying the whole game state:

```
┌────────────────┐  channel publish with  ┌────────┐   FCM    ┌──────────────────────┐
│ Node dashboard │  extras.push, or push  │  Ably  │ ────────▶│ Android app           │
│  Android tab   │ ──────────────────────▶│        │  data    │  FirebaseMessaging-   │
│                │  admin publish to a    │ holds  │  message │  Service → Live Update│
│  /api/auth ◀───┼─ device ID ────────────┼ FCM SA ┼──────────┤  notification+widget │
└────────────────┘                        └────────┘          └──────────────────────┘
```

## What's in the repo

```
ios/                                iOS app (Xcode project)
  LiveActivityExample/              Main app target
    Models/GameAttributes.swift       GameAttributes — the ActivityAttributes wire contract
    Services/LiveActivityManager.swift  ActivityKit: start/end, token observation
    Services/AblyPushManager.swift      Ably device activation + push-to-start registration
    Views/                            SwiftUI control panel
  GameScoreWidget/                  Widget extension — Lock Screen + Dynamic Island UI

android/                            Android app (Gradle project, Kotlin + Compose)
  app/src/main/java/com/ably/example/liveupdate/
    model/GameState.kt                GameState — the FCM data wire contract
    model/GameStore.kt                Persisted latest game (read by the widget)
    LiveGame.kt                       Applies start/update/end to notification + widget
    push/AblyPushManager.kt           Ably device activation (FCM) + channel subscribe
    push/LiveUpdateMessagingService.kt  FCM receiver: token refresh + incoming pushes
    notification/LiveUpdateNotifier.kt  The Live Update (promoted ongoing notification)
    widget/GameScoreWidgetProvider.kt   Home-screen score widget (RemoteViews)
    ui/                               Compose control panel

server/                             Node.js dashboard
  server.js                           Express API + Ably token auth endpoint
  ably-live-activity.js               iOS: Ably push admin client (broadcast + live activity)
  ably-live-update.js                 Android: data-only FCM pushes via Ably
  public/                             Web dashboard (HTML/CSS/JS)
```

**SDK versions:** [ably-cocoa 1.2.62+](https://github.com/ably/ably-cocoa)
(Swift Package Manager) on iOS, [ably 2.24.0+](https://www.npmjs.com/package/ably)
(npm) on the server — the first releases with the Live Activity push admin and
push-to-start APIs — and [ably-android 1.8.2](https://github.com/ably/ably-java)
on Android.

## Prerequisites

- An **Apple Developer account** with an APNs auth key (`.p8`) — create one
  under Certificates, Identifiers & Profiles → Keys.
- An **iPhone** iOS 18+
- **Xcode 15+** and **Node.js 18+**.
- An **Ably account** ([free signup](https://ably.com/signup)).
- For Android: a **Firebase project**, **Android Studio** (or JDK 17 + the
  Android SDK 36), and an Android 8+ device or emulator with Google Play
  services. Live Updates need Android 16+; older versions show a regular
  ongoing notification.

## Step 1 — Configure Ably

1. Create an Ably app (or use an existing one).
2. In the app's **Push** settings, upload your APNs auth key: the `.p8`
   contents, Key ID, Team ID, and your app's bundle ID. Select the sandbox
   APNs endpoint for development builds.
3. For Android: in the Firebase console, add an Android app with package
   name `com.ably.example.liveupdate`, then under Project settings → Service
   accounts generate a private key (JSON) and upload it in the Ably app's
   **Push** settings (FCM section).
4. For Android channel targeting: under the Ably app's **Settings → Rules**,
   add a channel rule for the `games` namespace with **Push notifications
   enabled**. Publishes with a push payload are rejected on other channels
   ("Published push notification to not push-enabled channel").
5. Create an API key with the **Push Admin** and **Publish** capabilities.

That's the only place your Apple and Firebase credentials live — the server
never sees them.

## Step 2 — Run the server

```bash
cd server
npm install
cp .env.example .env   # set ABLY_API_KEY
npm start
```

Open [http://localhost:3000](http://localhost:3000).

The only required `.env` value is `ABLY_API_KEY` — the key with Push Admin
capability from Step 1 (Ably dashboard → your app → API Keys). Set `PORT` to
change the dashboard port (default 3000).

## Step 3 — Build the iOS app

The Xcode project is included; open
`ios/LiveActivityExample.xcodeproj`, then:

1. **Signing & Capabilities** (main app target): set your team, make sure
   **Push Notifications** is added, and `aps-environment` is `development`.
2. Set the bundle ID to match what you configured in Ably's Push settings.
3. The **ably-cocoa** package (1.2.62, via SPM) resolves automatically on
   first open.
4. Build to a physical device or emulator.

If you're recreating the project from scratch instead, the important bits are:

- Two targets: the app and a Widget Extension (`GameScoreWidget`).
- `Models/GameAttributes.swift` must belong to **both** targets — it defines
  `GameAttributes`, the shared `ActivityAttributes` type. Its name is part of
  the wire contract: the server sends `attributes-type: "GameAttributes"`.
- `Info.plist` needs:
  ```xml
  <key>NSSupportsLiveActivities</key><true/>
  <key>NSSupportsLiveActivitiesFrequentUpdates</key><true/>
  ```
- Deployment target iOS 18+ on both targets.

## Step 4 — Build the Android app

1. In the Firebase console, download `google-services.json` for the
   `com.ably.example.liveupdate` app and save it as
   `android/app/google-services.json` (it is git-ignored). Without it the app
   still builds, but push activation reports that Firebase isn't configured.
2. Open the `android/` folder in Android Studio, or build from the command
   line:
   ```bash
   cd android
   ./gradlew :app:installDebug
   ```
3. On first launch, tap **Allow Notifications**. On Android 16+ the
   *Notifications* card also shows whether **Live Updates** are allowed for
   the app, with a shortcut to the setting.

The important bits, if you're recreating the project from scratch:

- `POST_NOTIFICATIONS` and `POST_PROMOTED_NOTIFICATIONS` permissions in the
  manifest; the second lets an ongoing notification request promotion.
- A `FirebaseMessagingService` that forwards new FCM tokens to Ably
  (`ActivationContext.onNewRegistrationToken`) and handles incoming data
  messages.
- The notification must meet the promotion rules: ongoing, has a title, a
  standard or `BigTextStyle`, no custom views, not colorized, on a channel
  above `IMPORTANCE_MIN` — then `setRequestPromotedOngoing(true)` and
  `setShortCriticalText(...)` (the status-bar chip).

## Tutorial A — Broadcast updates (one push, every device)

The activity is started **on the device**, subscribed to an APNs broadcast
channel; the dashboard then updates all subscribed devices with a single call.

1. In the dashboard, click **Create Broadcast**. You get two IDs:
   - **Broadcast ID** — the Ably handle used for update/end calls;
   - **APNS Channel ID** — what devices subscribe to.
2. In the iOS app, paste the **APNS Channel ID** into the *Broadcast Channel*
   field and tap **Start Live Activity Locally**. The activity starts with
   `pushType: .channel(apnsChannelId)`.
3. Back in the dashboard, change the points, period, clock, or last play and
   click **Send Update** — every subscribed device updates simultaneously.
   No per-device tokens are ever collected.
4. **End Activity** ends it everywhere (and invalidates the broadcast).

Repeat step 2 on more devices to see the fan-out: one `update()` call, all
screens change at once.

## Tutorial B — Push-to-start (server starts the activity remotely)

Here the device runs no activity at all — the server starts one on it via
Ably. This is the two-step device registration released in ably-cocoa 1.2.62.

**On the device (one-time setup):**

1. Launch the app. iOS issues a **push-to-start token** as soon as the app
   observes `Activity.pushToStartTokenUpdates`; it appears in the app UI.
2. Check the *Server URL* field points at your machine (the app authenticates
   through the server's `/api/auth` token endpoint — the Ably API key stays
   server-side).
3. Tap **Activate Device with Ably**. Under the hood this is two steps:
   `push.activate()` registers the device with Ably using a standard APNs
   token, then `push.registerPushToStartToken(_:)` attaches the Live Activity
   push-to-start token to that registration.
4. Subscribe the device to an Ably channel, e.g. `games:lal-bos` — this is how
   the server targets it. (The app also shows the **Ably Device ID** if you'd
   rather target one device directly.)

**From the dashboard:**

5. Click **Create Broadcast** (push-to-start also enrolls the new activity
   into a broadcast channel, so you can update it the same way as Tutorial A).
6. In *Start Live Activity*, enter the channel from step 4 (or paste the
   Device ID), set the teams, and click **Start Live Activity**.
7. The Live Activity appears on the device — even if the app is in the
   background — already subscribed to the broadcast. Use **Send Update** /
   **End Activity** as before.

## Tutorial C — Android Live Updates

The server pushes the full game state to Android devices; the app renders it
as a Live Update notification and in the home-screen widget.

**On the device (one-time setup):**

1. Check the *Server URL* (the emulator reaches your machine at
   `http://10.0.2.2:3000`; a physical device needs your machine's LAN IP) and
   tap **Activate Device with Ably**. `push.activate()` fetches an FCM
   registration token and registers the device with Ably; the **Ably Device
   ID** then appears in the app.
2. Subscribe the device to an Ably channel, e.g. `games:lal-bos` (the
   namespace needs the push rule from Step 1).
3. Optionally tap **Add Widget to Home Screen** to pin the Game Score widget.

**From the dashboard (Android Live Update tab):**

4. Enter the channel from step 2 and/or paste the Device ID, set the teams,
   and click **Start Live Update**. The Live Update appears — even with the
   app in the background — and the widget shows the game.
5. Change the points, status, period, clock or last play and click **Send
   Update**: the notification's score chip, the expanded notification and the
   widget all update together. Subscribe more devices to the same channel to
   see one publish reach all of them.
6. **End Live Update** turns the notification into a dismissable final score
   (cleared after an hour) and the widget shows *FINAL*.

You can also tap **Start Live Update Locally** in the app and drive it from
the dashboard in the same way.

## API reference

Everything the server does goes through the Ably JS SDK's push admin API:

```js
const rest = new Ably.Rest({ key: ABLY_API_KEY });

// 1. Create a broadcast channel → { id, apnsChannelId }
await rest.push.admin.createApnsBroadcast({ messageStoragePolicy: 1 });

// 2. Push-to-start on devices subscribed to a channel (or by deviceId)
await rest.push.admin.liveActivity.start({
  recipient: { channels: ['games:lal-bos'] },   // or { deviceId }
  apnsBroadcast: id,          // enroll the started activity into the broadcast
  apns: {
    aps: {
      event: 'start',
      'input-push-channel': apnsChannelId,
      'attributes-type': 'GameAttributes',      // must match the Swift type name
      attributes: { homeTeam, awayTeam },
      'content-state': { homeScore: 0, awayScore: 0, /* … */ },
      alert: { title: 'Lakers vs Celtics', body: 'Game starting!' },
      timestamp: Math.floor(Date.now() / 1000),
    },
  },
  headers: { 'apns-priority': '10' },
});

// 3. Update / end all subscribed activities with one call
await rest.push.admin.liveActivity.update({ apnsBroadcast: id, apns, headers });
await rest.push.admin.liveActivity.end({ apnsBroadcast: id, apns, headers });
```

The `apns` field is a standard [APNs Live Activity payload](https://developer.apple.com/documentation/activitykit/starting-and-updating-live-activities-with-activitykit-push-notifications)
— Ably signs it and passes it through unchanged. `messageStoragePolicy: 1`
caches the last update so late-joining devices receive the current
content-state when they subscribe.

For Android, every event is a data-only push with the full game state (FCM
data values must be strings). Channel targeting is a normal channel publish
with a push payload in `extras`; device targeting uses push admin publish:

```js
const push = {
  data: {
    event: 'update',                    // 'start' | 'update' | 'end'
    homeTeam: 'Lakers', awayTeam: 'Celtics',
    homeScore: '102', awayScore: '99',
    gameStatus: 'live', period: 'Q4', clock: '1:12', lastPlay: 'James dunk',
    timestamp: String(Date.now()),      // lets the app drop out-of-order pushes
  },
  fcm: { priority: 'HIGH' },            // merged into the FCM AndroidConfig
};

// Every device subscribed to the channel (needs a push-enabled channel rule)
await rest.channels.get('games:lal-bos').publish({ name: 'live-update', extras: { push } });
// One device
await rest.push.admin.publish({ deviceId }, push);
```

There is no `notification` block, so FCM always delivers the message to the
app's `FirebaseMessagingService.onMessageReceived`, in the foreground and the
background, and the app builds the Live Update itself.

The token auth endpoint is a one-liner — the device's `authUrl` points here:

```js
app.get('/api/auth', async (req, res) => {
  res.json(await rest.auth.createTokenRequest({
    capability: JSON.stringify({ '*': ['subscribe', 'publish', 'push-subscribe'] }),
  }));
});
```

## Troubleshooting

- **No push-to-start token in the app** — push-to-start needs iOS 18+ and a
  physical device or emulator; the token only appears while
  `NSSupportsLiveActivities` is set and Live Activities are allowed for the
  app in Settings.
- **Activation fails with an auth error** — the device must reach the server's
  `/api/auth` over your local network; check the *Server URL* uses your Mac's
  hostname/IP, not `localhost`.
- **Updates don't arrive** — verify the APNs key in Ably's Push settings uses
  the **sandbox** endpoint for development builds, and that the bundle ID
  matches exactly.
- **Broadcast subscribe silently ignored** — `pushType: .channel` requires
  iOS 18+; on 17.x start the activity without a channel and use its
  per-activity update token instead.
- **Android: activation fails or never completes** — `app/google-services.json`
  must belong to the Firebase project whose service account is uploaded to
  Ably, and the device or emulator needs Google Play services.
- **Android: "Published push notification to not push-enabled channel"** —
  add the push-enabled channel rule for the namespace (Step 1), or target the
  device by ID instead.
- **Android: the notification isn't promoted to a Live Update** — needs
  Android 16+, and Live Updates must be allowed for the app (the app's
  *Notifications* card shows the state and links to the setting).
