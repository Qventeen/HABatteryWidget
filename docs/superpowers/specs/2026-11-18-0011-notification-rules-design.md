# Notification rules — concept

- **Date:** 2026-11-18
- **Status:** concept. Owner decision (2026-11-18): notifications ride on the
  colour levels of 0010. Not approved as a spec yet: the open questions in
  §5 need a detailing session before a plan is written.
- **Set:** `0011`, epic `epic-widget-improvements`. Code later on branch
  `0011-notification-rules`, from the epic.
- **Builds on:** 0010 (value colours; required, build it first) and spec 0001
  (§6 full-charge notification, §13 refresh, §14 stored data). On one topic
  the newest spec wins.

## 1. Intent (owner, 2026-11-18)

Today the app sends a notification only when the battery is full. The owner
wants to turn notifications on and off and to have more of them. The first
kind is battery charge levels. Each notification has a type: **Error**
(danger, red), **Warning** (yellow), **Success** (green) or **Full** (full
charge, blue).

Owner decision, after reading the first concept: **merge notifications with
the colour levels of 0010.** Each level of a value gets a notify switch.
When the value reaches a level whose switch is on, the app sends a
notification, at the same time as the colour changes. By default most
switches are off; some chosen ones are on. Examples:

- full charge (100 %) → notification;
- charge reaches 15 % → notification, and the colour changes;
- battery temperature falls below a level → notification.

Order: first the colours (0010, spec and build), then this spec extends
today's notification code to work on the levels.

## 2. Today

- `FullChargeRule` fires when the battery state is FULL and re-arms (gets
  ready to fire again) when the rounded charge drops to ≤ 98 %. The "already
  notified" flag is `CacheState.fullChargeNotified`.
- `AndroidFullChargeNotifier` posts to one channel (`full_charge`, high
  importance) with one notification id; a tap opens the app. It never asks
  for the permission; `NotificationSection` asks on Android 13+.
- One setting: `Settings.notifyOnFull`.
- Rules are checked only inside `SyncBattery`, that is, only when a sync
  runs. The periodic sync runs when auto refresh is on and (a widget exists
  or the full-charge notification is on) (0001 §13).

## 3. Proposed shape (a proposal, not decided)

- **A notify switch and a type per level.** Each level of a value (0010)
  gets a notify switch and a type (Error, Warning, Success, Full). When the
  value enters a level whose switch is on, the app posts a notification of
  that type. Colour and notification come from the same level, so they
  always agree.
- **Direction.** A middle level can be entered from above (the value falls)
  or from below (it rises). The switch has a direction: "when falling into
  it", "when rising into it", or both. The first level (no lower limit) can
  only be entered falling, the last one only rising. Example: without
  direction, charging from 10 % to 16 % would send "Warning" on entering
  15–25 %.
- **Defaults.** The built-in default set (0010) also says which levels
  notify. Proposal: charge < 15 % (falling, Error) and 100 % (rising, Full)
  on; all others off.
- **Main switch.** The Notifications section keeps one main switch (today's
  switch, which asks for the permission on Android 13+), off by default as
  today. Off: nothing is posted, whatever the levels say.
- **Fire once per entry.** A level re-arms only after the value leaves it by
  a margin (hysteresis: a small gap so a value near a line does not fire
  again and again; today it is 2 % for full). The margin depends on the
  value (for example 2 % for charge, 1 °C for temperatures). The cache keeps
  the last level per value and replaces `fullChargeNotified`.
- **One Android channel per type** (4 channels), so the owner can set sound,
  vibration and pop-up per type in the Android settings. The type also sets
  the icon, the accent colour and the default title.
- **Migration.** `notifyOnFull` becomes the main switch; the 100 % default
  level notifies, so today's behaviour stays. A meaning change after a
  release bumps `schemaVersion` and adds a `DataMigration` with a test (0001
  §14).
- **Sync condition:** the periodic sync runs when auto refresh is on and (a
  widget exists or the main switch is on), as today.
- **Domain:** a pure evaluator takes the reading, the effective levels and
  the stored state, and returns what to post and the new state; a notifier
  port posts it.

## 4. Catches

- **Delay.** Levels are checked only during a sync: every 15 min at best,
  and only with auto refresh on. Android's battery saving (Doze) can add
  more. A notification can come 15–30 min late. With auto refresh off, no
  notification comes at all; the screen should say so.
- **Missed moments.** A value that enters and leaves a level between two
  syncs is never seen.
- **Colour.** Android does not colour the whole notification. The type shows
  as icon, accent colour (if the system shows it; HyperOS may not) and its
  channel's sound.
- **Channels.** After a channel is created, the app cannot change its sound
  or importance; the owner changes them in the Android settings.
- **Faster path.** For urgent alarms, a Home Assistant automation that sends
  through the HA Companion app arrives in seconds. This spec does not
  replace it.
- **Noise.** Levels close together can spam; the direction and the re-arm
  margin limit this.

## 5. Open questions (for the detailing session)

1. ~~Shared levels with 0010, or a separate rule list?~~ Decided (owner,
   2026-11-18): shared, notifications ride on the levels.
2. Direction per level (falling, rising, both): is the proposal right?
3. Which levels notify by default (proposal: charge < 15 % and 100 %)?
4. Keep one main switch (today's, it asks for the permission)?
5. With "Custom colours" off for a value: can the owner still change its
   notify switches, or only after turning custom colours on?
6. The type of a level: picked freely, or proposed from its colour (red →
   Error, amber → Warning, green → Success, blue → Full)?
7. Re-arm margin per value: fixed, or editable?
8. Which values can notify: all values that have levels, also the inverter
   values of 0012 (for example a grid loss)?
9. The text: own text per level, or a fixed text per type plus the value
   ("Battery temperature 3 °C")?
10. Once per entry, or repeat every N minutes while the value stays there?
11. Quiet hours?
12. Should a long sync error (HA not reachable for N minutes) notify too?

## 6. Non-goals

- No push from a server (FCM): the app posts local notifications after its
  own sync.
- No checks more often than the 15-minute sync (no foreground service, no
  exact alarms); they would cost phone battery (proposal).
