# Notification rules — design

- **Date:** 2026-11-18
- **Status:** approved by the owner (2026-11-19), after the builder check.
  Plan: `docs/superpowers/plans/2026-11-19-0011-notification-rules.md`.
  Amended 2026-11-19 (owner, while detailing 0012): editable re-arm
  margins (§3.3); the plan is amended to match. Amended 2026-11-20 (0010
  round 3, two-sided levels): the examples, the re-arm wording (§3.3) and
  the title (§3.4) follow 0010 §3.1; the rules do not change.
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
  the Android channel, the sound, the icon and the accent colour. "Error"
  stays reserved for real faults (HA not reachable, token rejected), and a
  state is not a "success".
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
20 % enters red (↓20 % Low) → a red (Critical) notification. Rising from
20 % to 21 % enters amber (↓40 % Getting low) → a yellow (Warning)
notification. (Numbers as amended 2026-11-20, 0010 §3.1.) No direction setting for now; it may come later if this is too noisy.

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
  - SOC: Low (↓20 %: 20 % and below) and Full (↑100 %);
  - battery temperature: Freezing (↓0 °C: 0 °C and below) and Hot (↑55 °C:
    55 °C and above);
  - MOSFET: Hot (↑80 °C);

  and off for every other level. These are rare and important; the
  temperature ones are about safety. "Getting low" stays silent by default
  (owner, 2026-11-18): a full cycle sends only "Low ↓20 %" and "Full
  ↑100 %"; the "rising into Getting low" example of §1 comes once its bell
  is on.
- **Bells are edited in the value's editor** (owner, 2026-11-18: "with
  Custom colours on"; since 2026-11-20 the switch is replaced by Default and
  Edit, 0010 §3.5). Edit starts from a copy of the defaults, so the bells
  start as the defaults; Save stores them as the value's own levels. All
  level settings stay in one place. Without own levels, the tags in the
  Colours section show which default levels have a bell.
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
  past one of the level's bounding edges by at least the margin of its
  value (amended 2026-11-20, below: bounds are edges, not shown numbers). The
  defaults (owner, 2026-11-18) are below; since 2026-11-19 the owner can
  change them per value (next point):

  | Value | Margin |
  |---|---|
  | SOC | 2 % |
  | Voltage | 0.1 V |
  | Charge and discharge current | 0.05 C (5 A on 100 Ah; 1 A when the capacity is unknown) |
  | Cell delta | 3 mV |
  | Battery and MOSFET temperature | 2 °C |

- **Editable margin** (owner, 2026-11-19, while detailing 0012): the
  value's editor (Edit, 0010 §3.5) has one field, "Notify again
  after it moves back by", in the value's unit (current in whole amps,
  like the custom copy). Blank means the default above, shown as a hint.
  A number ≥ 0 at the value's shown precision; anything else is a field
  error and nothing is saved. Stored as `CustomLevels.margin` (null = the
  default); Reset in the editor sets it back to blank. Without own levels
  (Default) the default applies. One field per value keeps the editor small (owner's worry
  about an overloaded screen).
- **With two-sided levels** (0010 §3.1, 2026-11-20) the bounds of a level
  are edges: a ↓ level's upper bound is its own edge and its lower bound
  the nearest lower ↓ edge; an ↑ level's lower bound is its own edge and
  its upper bound the nearest higher ↑ edge; the middle level's bounds are
  the highest ↓ edge and the lowest ↑ edge. An outer level has one bound.
  A disarmed level re-arms when `x >= upper + margin` or `x <= lower −
  margin`, on the bounds it has. So the margin counts from the number on
  the level's tag, away from the alarm side: Low ↓20 % re-arms at 22 %,
  Freezing ↓0 °C at 2 °C, Hot ↑55 °C at 53 °C, Full ↑100 % at 98 %. An
  inner level also re-arms when the value goes deeper by the margin past
  the next edge: Getting low ↓40 % at 42 % or at 18 %, Warm ↑45 °C at
  43 °C or 57 °C; the SOC middle level (between ↓40 and ↑100) at 38 % or
  102 %.
- Example, SOC with the default bells, the charge moving 22 → 20 → 21 → 20
  → 21 %: "Charge: Low ↓20 %" is posted; "Getting low" has no bell; Low
  re-arms only at 22 % or more, so the jitter at 20–21 % posts nothing
  more. Today's full notification behaves the same way (it re-arms at
  98 %).
- With a bell on both sides of an edge, a value jumping across the edge can
  post once on each side, then both levels are disarmed until the value
  moves away by the margin.
- **Level ids** (0010 §3.1): the custom copy keeps the ids of the default
  levels, so Edit + Save and Default keep the stored state; a
  new level gets the highest id plus one. Disarmed ids of levels that no
  longer exist are dropped at each check (my call).

### 3.4 What a notification shows

- **Title** (owner, 2026-11-18): "value: level name, arrow, number", for
  example "Charge: Low ↓20 %", "Battery temperature: Hot ↑55 °C". Since
  2026-11-20 (0010 round 3) the arrow and number are the level's own, as
  its Colours tag shows them: the arrow is the level's side, the number its
  edge; a middle level with a bell shows ↕ and its range ("Charge:
  Normal ↕41–99 %"). The "value: name" form works with free names in English and
  Ukrainian.
- **Value labels:** Charge, Voltage, Charge current, Discharge current, Cell
  delta, Battery temperature, MOSFET temperature (Ukrainian: Заряд, Напруга,
  Струм заряду, Струм розряду, Різниця комірок, Температура батареї,
  Температура MOSFET).
- **Body** (owner, 2026-11-18): when the value was measured, "Measured at
  14:05" (the reading's time, in the update-time format), because a
  notification can come 15–30 min late. Since 2026-11-20 the measured
  number moves here from the title (my call, so it is not lost): "19 % ·
  measured at 14:05", formatted as the widget shows it.
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
- **An icon per type** (owner decision on types, §1), from Material Symbols
  Rounded (Apache-2.0), as monochrome small icons: Critical `report`,
  Warning `warning`, Normal `check_circle`, Info `info` (icon picks are my
  call). The accent colour is the type's colour from the 0010 palette.
- **Channel names** follow the app language: each channel is created again
  before each post (0001 §12). Names: Critical, Warning, Normal, Info
  (Ukrainian: Критичні, Попередження, Звичайні, Інформація).
- The old `full_charge` channel is deleted, so it does not stay as an empty
  entry in the Android settings (my call).

### 3.6 Stored data and migration

- **Settings:** `ColorLevel.notify: Boolean = false` (0010 model; a new
  field, no migration).
- **The main switch is a meaning change after a release** (v0.2.0 shipped
  `notifyOnFull`): it now covers every bell, not only "full". So, as 0001
  §14 requires, the field is renamed to `notificationsOn`, the settings
  record's `schemaVersion` goes from 1 to 2, and a `DataMigration` copies
  `notifyOnFull` into `notificationsOn` (on stays on, off stays off), with a
  test that loads a saved v1 JSON sample. The store helper (`jsonDataStore`)
  gets a `migrations` parameter for it. The Full level notifies by default,
  so today's behaviour is kept.
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
  off, a note that no notification can come without it (§4, Delay). Its
  summary stays "On" or "Off" (0002).
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
   with custom on only (§3.1); since 2026-11-20: in the value's editor
   (Edit), 0010 §3.5.
6. ~~The type of a level?~~ Decided by the 0010 level model: the owner picks
   the type; the colour follows the type and can be changed.
7. ~~Re-arm margin: fixed or editable?~~ Decided (owner, 2026-11-18):
   fixed per value; changed (owner, 2026-11-19): editable per value in
   the value's editor, defaults unchanged (§3.3).
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

## 7. Build notes (2026-11-20)

- The number a level is picked by comes from one domain function
  (`ShownNumber`), used by the widget colours and the alerts — so both
  always agree.
- Title is "Value: Name ↓20 %" (the tag's place) and body is
  "20% · measured at 14:05", as amended; the alert has no arrow field — the
  arrow is the level's side.
- Margin precision: the margin field takes the value's shown precision
  (current 0.1 A), not whole amps — one rule for edges, hint and margin; a
  typed 0.5 A was saved as 0. Owner to confirm.
- Every level channel is created on each post — the owner can set all four
  sounds after the first notification, and the names follow the language.
- The no-auto-refresh note says a manual refresh still notifies — it does
  still sync and can post.
- `system/` reads the type colour from `widget/WidgetPalette` — an accepted
  package edge.
- Default current and voltage lists reuse level ids when the capacity or
  cell count becomes known — harmless while those lists have no default
  bells; spec 0013 must re-check it when it changes `LevelBasis`.
