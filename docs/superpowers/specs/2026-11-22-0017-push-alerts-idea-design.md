# Push alerts from Home Assistant — idea

- **Date:** 2026-11-22.
- **Status:** idea only (owner, 2026-11-22: "the next spec after 0016, as an
  idea for the project's growth"). Not planned, not approved. A real design
  starts with brainstorming when the owner asks.
- **Set:** `0017`. Follows 0016 (refresh on unlock, freshness dot).

## 1. Why

Today every alert (battery levels, inverter mode) is computed in the app
after a sync. The sync runs at most every 15 min and Android defers it while
the phone sleeps (Doze), so an alert can come 15–60 min late. Home Assistant
sees each change at once; a push from HA would bring the alert in seconds.

## 2. Options found (research, 2026-11-22)

Facts marked V were read in the docs or source; I = inferred.

| Option | How fresh | Battery | Needs | Main risk |
|---|---|---|---|---|
| HA automation → official Companion app (`notify.mobile_app_<phone>`) | seconds | none for this app | HA side only; Companion app on the phone | alerts live outside this app |
| Own Firebase project (FCM high priority) | seconds | lowest | a Firebase project, `google-services.json`, a push relay server | Google dependency, a server to run |
| ntfy server + UnifiedPush | seconds | low–medium (the ntfy app holds the connection) | a self-hosted ntfy server, the ntfy app | an extra app; alert only, not data |
| `mobile_app` registration + websocket push channel | seconds | medium–high | a live HA connection (foreground service, permanent notification) | battery, user trust |

- **`mobile_app` for own apps (V):** an app may register with its own
  client id (`POST /api/mobile_app/registrations`) and get `notify` calls
  through `push_url` (own push server) or a websocket channel.
  https://contributors.home-assistant.io/companion/mobile-app-api/setup/ and
  https://contributors.home-assistant.io/companion/mobile-app-api/notifications/
- **The official push relay** serves the official apps; no statement allows
  third-party use (I). Its limit is 500 pushes per device per day (V).
  https://companion.home-assistant.io/docs/notifications/notification-details
- **Doze (V):** high-priority FCM messages wake the app; standard alarms and
  jobs wait for maintenance windows; `setAndAllowWhileIdle` fires at most once
  per 9 min. https://developer.android.com/training/monitoring-device-state/doze-standby
- **Implicit broadcasts (V):** `SCREEN_ON` / `USER_PRESENT` are not on the
  manifest exception list.
  https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- **Foreground service limits (V):** Android 15 limits `dataSync` to 6 h per
  24 h. https://developer.android.com/about/versions/15/behavior-changes-15
- **HA Companion app (V, source):** refreshes its widgets on a runtime
  `SCREEN_ON` receiver (`BaseWidgetProvider`, `BaseGlanceEntityWidgetReceiver`);
  its websocket worker runs as a foreground service (`remoteMessaging`).
  https://github.com/home-assistant/android
- **ntfy in HA (V):** a core integration since 2025.5 (HA publishes to a
  topic). https://www.home-assistant.io/integrations/ntfy/

## 3. Smallest test first

Before any code: one HA automation that sends a test alert to the official
Companion app (`data: {ttl: 0, priority: high}`). If it arrives fast enough,
HA-side alerts may be enough and this app's own alerts stay as a backup.

## 4. Open questions for a real design

- Alerts only, or also live data for the widgets?
- Is a Google dependency (Firebase) acceptable, or self-hosted only?
- Who runs the push server, and where (the owner self-hosts)?
- Do the app's own alerts stay when push works (duplicates)?
