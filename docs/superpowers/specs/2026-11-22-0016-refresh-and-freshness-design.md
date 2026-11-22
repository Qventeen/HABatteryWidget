# Refresh on unlock, ↻ on both widgets, freshness dot — design

- **Date:** 2026-11-22.
- **Status:** approved by the owner (2026-11-22). Owner answers and picks
  are marked "owner"; the calls I made are marked "my call".
- **Design:** canvas row "Refresh · v14 · ↻ and freshness dot": the picked
  design only (owner, 2026-11-22), nine artboards with Tweaks (state, data
  age, theme): battery 4×2, 5×2, 4×3, 5×3, strip 3×1, 4×1, 5×1, and the
  sync error of each widget, light and dark. The repo copy is in
  `docs/design/` (§3.7).
- **Set:** `0016`, epic `epic-widget-improvements`. Code on branch
  `0016-refresh`, from the epic.
- **Builds on:** 0001 (§13 refresh and widget, §14 stored data; call 2:
  no refresh below 15 min; call 18: auto-refresh starts off), 0005 (the
  battery top row), 0012 (the inverter strip, "one sync, two parts"). This
  spec changes 0005's top row (the auto-refresh button goes) and 0012's
  strip ("no ↻ on the strip", the ⚠ at the end of the row). On one topic
  the newest spec wins.

## 1. Intent (owner, 2026-11-22)

- The timed sync (WorkManager, 15–1440 min) is robust and simple, but the
  data is often old when the owner looks at a widget: Android defers
  background work (Doze), and 15 min is the floor.
- Fresh data when you look, cheap on battery: refresh when the phone is
  unlocked (owner: "this version is good enough").
- The timed sync stays: alerts come from it. Minimum stays 15 min (owner
  picked A: no second, alarm-based path for 10–14 min).
- The widget's auto-refresh button goes. The app's switch is the only
  on/off for the timed sync.
- ↻ stays on the battery widget, and the inverter strip gets one ↻ at the
  right end.
- Both widgets get a freshness dot: it shows how old the data is. One dot
  and one ↻ per widget.
- Push from Home Assistant (own Firebase project or an ntfy server) is a
  later idea: spec 0017, not part of this one.

## 2. Approaches (research, 2026-11-22)

| Way | How fresh | Battery | Verdict |
|---|---|---|---|
| Timed WorkManager job (today) | 15–60+ min | very low | stays, for alerts |
| Refresh on unlock (runtime receiver) | seconds after unlock | low | **picked** (owner) |
| Inexact alarm every 9+ min (the fork's way) | 9–15 min | low–medium | dropped (owner picked A) |
| Exact alarm every 1 min | 1 min | high | dropped: user permission, Play policy |
| Live HA connection + permanent notification | seconds | medium–high | dropped: battery, user trust |
| Push: own Firebase or ntfy | seconds | low | later: spec 0017 idea |

Facts behind the table (helper research; sources in spec 0017):
`ACTION_USER_PRESENT` and `ACTION_SCREEN_ON` are not delivered to manifest
receivers on API 26+, so only a running process hears them; the HA
Companion app refreshes its widgets the same way (runtime `SCREEN_ON`
receiver). Android has no "widget is visible" event (Android 16's widget
events are hourly statistics).

## 3. Decisions

### 3.1 Refresh on unlock

- **Trigger:** `Intent.ACTION_USER_PRESENT` (the user unlocked; the home
  screen is about to show). A runtime receiver, registered once in
  `App.onCreate` for the process lifetime (my call: unlock, not screen on;
  screen on also fires for a notification on the lock screen, when no
  widget is visible).
- **What it does:** like the widget ↻ (One sync path): mark "refreshing",
  redraw, enqueue a one-time sync. It never calls the network itself.
- **Waits for the network** (my call): right after an unlock, Wi-Fi or
  mobile data is often not up yet, and a sync without a network would show
  the red ⚠ on many unlocks. So the unlock sync is its own unique one-time
  work with a CONNECTED constraint (REPLACE). The widget ↻ keeps
  `syncNow` without a constraint: a tap with no network should show the
  error at once.
- **When it runs**, all true (my call):
  - the switch of §3.4 is on;
  - at least one widget is placed, of either kind (the same check as the
    periodic work);
  - at least one part (battery or inverter) is configured;
  - no sync is running (`refreshingSinceMillis` within its 2-min limit);
  - some configured part has a reading at least 1 min old, or none: the
    sync is skipped only when every configured part was read in the last
    minute. Example: battery read 30 s ago, inverter failed 10 min ago →
    sync.
- The gate is a pure domain function (beside `RefreshPolicy`), so each
  condition is tested alone.
- **Limits** (§4): the receiver lives only while Android keeps the app
  process. The timed sync and any widget update start the process again,
  so it comes back within one interval at most.

### 3.2 The freshness dot

- **Shows** the age of the data the widget shows: now − `updatedAtMillis`
  of that widget's part (`BatteryReading` for the battery widget,
  `InverterReading` for the strip; both have the field, set at fetch time).
  Each widget's dot follows its own part: a failed inverter part turns only
  the strip's dot amber.
- **Colours** (my call):
  - green (the widget's "bar" green: `#43A047` light, `#66BB6A` dark):
    age ≤ the interval + 5 min;
  - amber (the SOC ring's warning colour: `#F59E0B` / `#FFB74D`): older.
  - The interval is the saved interval when the timed sync is on, else 15
    min. So at 15 min the dot turns amber at 20 min.
- **Place** (owner): on the top-right edge of the ↻ circle, on both
  widgets: an 8 dp dot with a 2 dp ring in the card colour (canvas: dot on
  the ↻ edge). It must be round on every Android version (minSdk 26): a
  `cornerRadius` is Android 12+ only, so the dot is an oval drawable (my
  call).
- **Not shown** in the empty states (setup needed, no data): they have no ↻.
- **Stays** while refreshing and on a sync error: it shows the data's age;
  the ⚠ shows the error.
- **Turns amber on time:** a widget redraws only when something asks. So
  one redraw (no network) is scheduled for the earliest moment a shown green
  dot turns amber: one unique one-time work for both widgets, replaced at
  each widget drawing (my call). The app's preview draws the same content
  but never schedules it (no side effects in the preview).
- **TalkBack:** the dot has no own focus; the ↻ description carries the
  age: "Refresh. Data is 3 min old." (my call; EN and UK strings).

### 3.3 The widgets

**Battery widget** (all four layouts):

- The auto-refresh button goes. The top row: time left, balancing, the
  update time (or the ⚠ in its place), then ↻ with the dot.
- The sync error rule stays (owner, 2026-11-15 and 2026-11-22): the red ⚠
  takes the update time's place; ↻ stays.
- The freed width goes to the update-time box (it grows; nothing else
  moves). `LayoutSpec` keeps one button: `buttonGap` goes, and the top-row
  width check counts one `buttonTouch`.

**Inverter strip** (3×1, 4×1, 5×1):

- ↻ at the right end of the row: a 28 dp circle (the battery's 4×2 circle
  is 30; 28 leaves room for the ⚠ above), 15 dp icon, the dot on its edge.
  Tap: the same `RefreshAction` as the battery widget. The rest of the card
  still opens the app. While a sync runs, ↻ dims, as on the battery widget.
- **Sync error** (owner): a red ⚠, 11 dp, above ↻ and 2 dp clear of it,
  partly in the card's top padding. It is not a button and nothing moves:
  without an error its space stays empty. The ⚠ at the end of the row
  (0012) goes.
- **Fit:** the card keeps its size (59 dp high). Glance cannot draw outside
  a parent, so the ↻ column spans the card's full height and the card's
  padding applies to the rest of the row (the plan decides the tree):
  ⚠ at 2.5–13.5 dp, ↻ at 15.5–43.5 dp from the card's top. **Fallback**
  (owner): if the ⚠ does not fit well on the phone, the ⚠ picture replaces
  ↻ during the error (no circle; the dot stays on its edge; a tap still
  refreshes).
- The voltage pills share the rest; their text fits as today
  (`InverterFonts`): at 3×1 the values go from 12.5 to about 12 sp.
- Empty states: as today, no ↻ and no dot.
- `InverterWidgetModel.Data` gets what the battery model has: refreshing
  and the data age (or the dot colour).

Glance has no free positioning: the dot and the ⚠ above ↻ are built with
nested `Box` / `Column` and padding (the plan decides the exact tree). The
Row limit of 10 children applies.

### 3.4 Settings (Common tab, Refresh section)

- "Refresh in the background" (the timed sync) and its interval stay as
  they are: 15–1440 min, starts off (0001 call 18, unchanged).
- New switch under it: **"Refresh when the phone is unlocked"** (UK:
  "Оновлювати після розблокування"), stored as
  `RefreshSettings.onUnlock: Boolean`.
- **Default on** (my call, owner please check): it is the cheap way to see
  fresh data and needs no interval. This is an exception to the rule
  "stored fields default only to false" (architecture.md), like the display
  choices; the rule's exception list gets this field. An old settings
  record without the field reads as on (kotlinx default; no schema bump —
  the plan checks this).
- A short hint under the new switch (my call, EN and UK): "Gets fresh data
  when you look. Works while Android keeps the app running; opening the app
  or the next timed sync starts it again."

### 3.5 What goes

- `widget/ToggleAutoRefreshAction`, `domain/ToggleAutoRefresh`,
  `RefreshSettings.toggled()`, `WidgetActions.toggleAuto`,
  `WidgetTexts.autoDescription`, the strings `widget_auto_on` /
  `widget_auto_off` (EN, UK), and their tests.
- `ic_auto_refresh` / `ic_auto_refresh_off` if nothing else uses them; the
  widget picker preview layout `battery_widget_preview.xml` drops its auto
  icon (my call: it shows the widget as it is now).
- `WidgetModel.autoRefresh` if nothing else reads it.

### 3.6 What stays

- One sync path, `SyncBattery`, `syncNow` (REPLACE) and the periodic work
  (`RefreshPolicy`: auto on, valid interval, widgets or notifications).
- Alerts: posted after each sync, as today.
- The ↻ action: marks refreshing, redraws, enqueues `syncNow`.

### 3.7 Design copy

- Saved with this spec (owner, 2026-11-22): `docs/design/refresh-dot.png`
  and the README section "Refresh — ↻ and freshness dot"; the battery top
  row, the strip layout, the states, data and colour rows of the README
  point to it.
- The build changes the copy only if the phone check changes the design.
- The v14 battery boards keep the v5 look; today's level colours
  (0010–0013) are not on the canvas. A design refresh of the battery boards
  is a separate task (owner asked; not in this spec).

## 4. Catches

- **Process killed → no unlock refresh** until the process starts again
  (timed sync, widget update, opening the app). With the timed sync off and
  a killed process, unlock refresh waits for the next widget update.
- **Android 14 queues the unlock broadcast for a cached app:** since
  Android 14 a broadcast to a runtime receiver waits while the app is cached
  (idle in the background). A widget-only app is cached most of the time, so
  the unlock refresh may run late: when the app next becomes active (a timed
  sync, a widget update, opening the app). A queued unlock then may sync
  right after the timed one. The owner's phone check decides whether the
  hint text changes.
- **OEM battery savers** (some brands kill background apps hard) can stop
  both paths; nothing in the app can fix that.
- **Unlock without a lock screen:** with screen lock "None" some phones may
  not send `USER_PRESENT` (a guess). The owner's phone check covers it.
- **More syncs:** at most one per unlock and per minute, so a few dozen a
  day for a typical user; each is one HA request (admin) or a few (user).
- **Dot before Android 12:** the ↻ circles are square there (as today); the
  dot stays round (oval drawable).

## 5. Tests

- Domain (JUnit): the dot rule (green at the edge, amber after; interval
  when auto on, 15 when off; per part); the unlock gate of §3.1 (each
  condition alone blocks it; a running sync blocks it; a reading under
  1 min blocks it).
- Robolectric: the unlock receiver marks refreshing and enqueues the
  unlock sync with the CONNECTED constraint, once, and does nothing when
  the switch is off; the redraw-at-amber work is enqueued with the right
  delay, replaced by a new drawing, and never by the preview.
- Widget (Glance test tags): battery top row has one ↻ and no auto button;
  the strip has ↻, the dot, and the ⚠ above ↻ only on a sync error; the
  empty states have neither.
- Settings: an old record without `onUnlock` reads as on; the switch
  writes it.
- Gate: `./gradlew lint testDebugUnitTest assembleRelease
  :app:compileDebugAndroidTestKotlin`.
- Phone check by the owner: unlock refreshes both widgets within seconds;
  the dot turns amber after 20 min without a sync; a sync error shows the ⚠
  above ↻ on the strip.

## 6. Non-goals

- No refresh below 15 min, no alarms, no foreground service.
- No push from HA (spec 0017 idea).
- No change to alerts, levels or the sync itself.
- No battery board colour refresh on the canvas (separate task).
