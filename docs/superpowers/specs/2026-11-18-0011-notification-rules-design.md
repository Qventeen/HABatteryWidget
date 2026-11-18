# Notification rules — design

- **Date:** 2026-11-18
- **Status:** detailed (2026-11-18). All questions in §5 are decided. Waiting
  for the builder check and the owner's approval; no plan yet.
- **Set:** `0011`, epic `epic-widget-improvements`. Code later on branch
  `0011-notification-rules`, from the epic.
- **Builds on:** 0010 (value colours; required, build it first) and spec 0001
  (§6 full-charge notification, §13 refresh, §14 stored data). On one topic
  the newest spec wins.

## 1. Intent (owner, 2026-11-18)

Today the app sends a notification only when the battery is full. The owner
wants to turn notifications on and off and to have more of them. The first
kind is battery charge levels. Each notification has a type with a colour:
red (danger), yellow (warning), green, and blue (full charge).

Owner decision on names (2026-11-18): a notification has two separate
parts.

- **Type (severity):** how serious it is. A fixed set of four: **Critical**
  (red), **Warning** (amber), **Normal** (green), **Info** (blue). It sets
  the Android channel and the accent colour. "Error" stays reserved for real
  faults (HA not reachable, token rejected), and a state is not a "success".
- **Level name:** what happened, per level, for example "Low", "Getting
  low", "Normal", "Full" (0010 §3.1: a default name, translated, or the
  owner's own text). It goes into the title.

Owner decision, after reading the first concept: **merge notifications with
the colour levels of 0010.** Each level of a value gets a notify switch (a
bell). When the value enters a level whose bell is on, the app sends a
notification, at the same time as the colour changes. By default most bells
are off; some chosen ones are on.

Order: first the colours (0010, spec and build), then this spec extends
today's notification code to work on the levels.

Owner decision on direction (2026-11-18): a crossing sends the notification
of the level the value **enters**, in both directions. Falling from 21 % to
19 % enters red → a red (Critical) notification. Rising from 19 % to 20 %
enters amber → a yellow (Warning) notification that says the charge went
up. No direction setting for now; it may come later if this is too noisy.

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

## 3. Design

### 3.0 Approaches considered

- **A separate rule list** (value, edge, type, text) next to the colour
  levels. Rejected by the owner: two lists to keep in step, and the colour
  and the notification could disagree.
- **A bell on each 0010 level** (picked, owner, 2026-11-18). One model;
  colour and notification come from the same level.
- **How a level fires and re-arms.**
  - "Sticky level" (the level changes only after the value passes an edge by
    a margin): calm, but 19 % would not notify "Low" until about 17 %, which
    goes against the owner's example. Rejected.
  - **Fire on entry, re-arm after a margin** (picked, my call): entering a
    level notifies at once (19 % → "Low"); the same level can notify again
    only after the value has left it by the margin (§3.3).

### 3.1 Which levels notify

- **A bell per level:** the 0010 level gets a field `notify` (default
  `false`). Only values with levels have bells: SOC, voltage, charge
  current, discharge current, cell delta, battery temperature, MOSFET
  temperature (owner, 2026-11-18). The average cell has none (0010 §3.5).
  Inverter values come with 0012.
- **Defaults** (owner, 2026-11-18): the bell is on for
  - SOC: Low (below 20 %) and Full (100 %);
  - battery temperature: Freezing (below 0 °C) and Hot (from 55 °C);
  - MOSFET: Hot (from 80 °C);

  and off for every other level. These are rare and important; the
  temperature ones are about safety.
- **Bells are edited with "Custom colours" on** (owner, 2026-11-18). Turning
  custom on alone changes nothing visible (the copy equals the defaults);
  then the owner sets the bells. All level settings stay in one place. With
  custom off, the read-only list in the Colours section shows which default
  levels have a bell.
- **Main switch** (owner, 2026-11-18): the Notifications section keeps one
  switch, off by default, which asks for the permission on Android 13+, as
  today. Off: nothing is posted, whatever the bells say. Its label changes
  from "notify when full" to a general "Notifications" switch.

### 3.2 When a notification is posted

The check runs inside `SyncBattery` after each successful reading, where
`FullChargeRule` runs today.

- **The number checked** is the one the widget colours (0010 §3.6): SOC in
  whole percent, voltage at 0.01 V, delta in mV, temperatures in whole
  degrees. Battery temperature: the sensor whose level ranks worst (the one
  the pill colour comes from); its own temperature is the number.
- **Current:** charge current and discharge current are two values. While
  the battery charges, the discharge current counts as 0 A (its first
  level), and the other way round; idle is 0 A for both (my call). So a
  charge that starts at 85 A enters "Very high" and can notify.
- **A missing value** (sensor not set or no number): no check for that
  value; its stored state stays as it was.
- **Entering a level:** when the level picked for the number is not the
  level stored for that value, the value has entered a new level. If that
  level's bell is on, the main switch is on and the level is armed (§3.3),
  the app posts a notification and disarms the level. The new level is
  stored in any case.
- **The first check** for a value (after install, after an update from a
  version without this spec, or when the stored level no longer exists
  because the owner edited the list) only stores the level; nothing is
  posted (my call, owner agreed 2026-11-18). Otherwise turning a bell on
  would at once post "Charge: Normal".
- The state is tracked also while the main switch is off, so turning the
  switch on does not post the current level (my call).
- **Once per entry** (owner, 2026-11-18): no repeat while the value stays in
  a level.
- **"Full" is plain level logic** (my call, after the builder check):
  entering the 100 % level posts even if the battery discharges at that
  moment. Today's rule also asked for "not discharging"; in practice the
  charge reaches 100 % only while charging, so nothing changes for the
  owner.
- **Missing permission** (Android 13+, denied) counts like the main switch
  off: the state is tracked, nothing is posted (my call).
- **Ties:** two battery sensors with the same worst type give the number of
  the first one in the settings order, as the widget does (my call).
- A value with one level (for example the current with no capacity known,
  0010 §3.5) never changes level, so it never posts.

### 3.3 Re-arm margin

- A level that has notified is disarmed. It re-arms when the number is
  outside the level's range by at least the margin of its value (owner,
  2026-11-18: fixed in code; editable later if needed):

  | Value | Margin |
  |---|---|
  | SOC | 2 % |
  | Voltage | 0.1 V |
  | Charge and discharge current | 0.05 C (5 A on 100 Ah; 1 A when the capacity is unknown) |
  | Cell delta | 3 mV |
  | Battery and MOSFET temperature | 2 °C |

- Example, SOC with the default bells, the charge moving 21 → 19 → 20 → 19
  → 20 %: "Low ↓ 19 %" is posted; "Getting low" has no bell; Low re-arms only
  at 22 % or more, so the jitter at 19–20 % posts nothing more. Today's full
  notification behaves the same way (it re-arms at 98 %).
- With a bell on both sides of an edge, a value jumping across the edge can
  post once on each side, then both levels are disarmed until the value
  moves away by the margin.
- **Level ids** (0010 §3.1): the custom copy keeps the ids of the default
  levels, so turning "Custom colours" on or off keeps the stored state; a
  new level gets the highest id plus one. Disarmed ids of levels that no
  longer exist are dropped at each check (my call).

### 3.4 What a notification shows

- **Title** (owner, 2026-11-18): "value: level name, arrow, number", for
  example "Charge: Low ↓ 19 %", "Battery temperature: Hot ↑ 55 °C". The arrow
  is ↓ when the new level is below the previous one, ↑ when above. The
  number uses the widget's formatter. The "value: name" form works with free
  names in English and Ukrainian.
- **Value labels:** Charge, Voltage, Charge current, Discharge current, Cell
  delta, Battery temperature, MOSFET temperature (Ukrainian: Заряд, Напруга,
  Струм заряду, Струм розряду, Різниця комірок, Температура батареї,
  Температура MOSFET).
- **Body** (owner, 2026-11-18): when the value was measured, "Measured at
  14:05" (the reading's time, in the update-time format), because a
  notification can come 15–30 min late.
- **Tap:** opens the app, as today.
- **One notification per value** (my call): a new level of the same value
  replaces the older notification of that value, so they do not stack.
  Different values stack. Entering a level without a bell leaves the older
  notification in place, as it may not be read yet (my call).

### 3.5 Android channels

- **One channel per type** (4 channels: Critical, Warning, Normal, Info), so
  the owner can set the sound, vibration and pop-up per type in the Android
  settings. Importance (my call): Critical and Warning high (pop-up), Normal
  and Info default (no pop-up).
- One small icon for all (today's battery icon); the accent colour is the
  level's type colour from the 0010 palette.
- The old `full_charge` channel is deleted, so it does not stay as an empty
  entry in the Android settings (my call).

### 3.6 Stored data and migration

- **Settings:** `ColorLevel.notify: Boolean = false` (0010 model). The main
  switch keeps the stored key `notifyOnFull`, so no data changes and no
  migration is needed: on stays on, off stays off; the Full level notifies by
  default, so today's behaviour is kept (my call; a rename in code only).
- **Cache:** per value, the last level and the disarmed levels (by level id,
  0010 §3.1). It replaces `CacheState.fullChargeNotified`, which is no
  longer read (unknown keys are ignored on read). No cache migration: the
  first check stores the levels and posts nothing (§3.2).
- **Code:** a pure evaluator in `domain` takes the reading, the effective
  levels, the main switch and the stored state, and returns what to post and
  the new state; the notifier port posts it. `FullChargeRule` and
  `AndroidFullChargeNotifier` are replaced.

### 3.7 App

- **Notifications section:** the main switch ("Notifications"), a hint
  "Choose the levels with the bells in Colours", and, when auto refresh is
  off, a note that no notification can come without it (§4, Delay).
- **Colours section (0010):** a bell toggle on each level row in the editor;
  the read-only default list shows the bells.
- **Sync condition:** the periodic sync runs when auto refresh is on and (a
  widget exists or the main switch is on), as today.

## 4. Catches

- **Delay.** Levels are checked only during a sync: every 15 min at best,
  and only with auto refresh on. Android's battery saving (Doze) can add
  more. A notification can come 15–30 min late. With auto refresh off, no
  notification comes at all; the screen says so (§3.7).
- **Missed moments.** A value that enters and leaves a level between two
  syncs is never seen. A value can also jump over a level: only the level it
  lands in notifies.
- **Colour.** Android does not colour the whole notification. The type shows
  as the accent colour (if the system shows it; HyperOS may not) and its
  channel's sound.
- **Channels.** After a channel is created, the app cannot change its sound
  or importance; the owner changes them in the Android settings.
- **Faster path.** For urgent alarms, a Home Assistant automation that sends
  through the HA Companion app arrives in seconds. This spec does not
  replace it.
- **Noise.** Levels close together can spam; the bells (most off) and the
  re-arm margin limit this.

## 5. Questions (all decided, 2026-11-18)

1. ~~Shared levels with 0010, or a separate rule list?~~ Decided (owner):
   shared, notifications ride on the levels.
2. ~~Direction per level?~~ Decided (owner): the level entered sets the
   notification, both directions, no setting for now.
3. ~~Which levels notify by default?~~ Decided (owner): SOC Low and Full,
   battery temperature Freezing and Hot, MOSFET Hot (§3.1).
4. ~~Keep one main switch?~~ Decided (owner): yes, today's switch (§3.1).
5. ~~Bells with "Custom colours" off?~~ Decided (owner): bells are edited
   with custom on only (§3.1).
6. ~~The type of a level?~~ Decided by the 0010 level model: the owner picks
   the type; the colour follows the type and can be changed.
7. ~~Re-arm margin: fixed or editable?~~ Decided (owner): fixed per value
   (§3.3).
8. ~~Which values can notify?~~ Decided (owner): every value with levels;
   the inverter with 0012.
9. ~~Title and body?~~ Decided (owner): "value: name, arrow, number"; the
   body says when the value was measured (§3.4).
10. ~~Repeat?~~ Decided (owner): once per entry.
11. ~~Quiet hours?~~ Decided (owner): none in the app; Android's Do Not
    Disturb and the channel settings cover it.
12. ~~Sync-error notification?~~ Decided (owner): not in this spec.

## 6. Non-goals

- No push from a server (FCM): the app posts local notifications after its
  own sync.
- No checks more often than the 15-minute sync (no foreground service, no
  exact alarms); they would cost phone battery.
- No direction setting, no repeat, no quiet hours, no sync-error
  notification in this version.
