# Inverter widget — concept

- **Date:** 2026-11-18. Detailing started 2026-11-19.
- **Status:** concept, detailing in progress. Not approved. Open questions
  in §5; a plan is written only after approval.
- **Set:** `0012`, epic `epic-widget-improvements`. Code later on branch
  `0012-inverter-widget`, from the epic.
- **Builds on:** 0009 (both connection ways must fetch the new values), 0010
  (colour levels for the inverter values), 0011 (bells), 0005 and 0006
  (layouts, app preview), 0007 (font sizes, home-screen scale). On one topic
  the newest spec wins.
- **Design:** new rows on the existing design canvas (owner, 2026-11-19).
  It is an extension: the existing boards and the v5 battery widget stay
  unchanged.

## 1. Intent

Owner, 2026-11-18: show the inverter on the home screen: its operating
mode, the grid input voltage, and the output voltage that goes to the
house. It is a strip placed above or below the battery widget. It is a
separate widget, but it is set up from the same app and gets its data from
the same Home Assistant sync. No new app.

Owner, 2026-11-19:

- **Why:** to see at a glance whether the house runs from the grid or from
  the battery system, and what voltage the house gets.
- **The inverter model does not matter.** The three entities come from the
  same ESP32 (ESPHome, the owner's config) as the BMS values.
- **One app configures several widgets.** The inverter widget gets the same
  tools as the battery widget: a preview, its own text sizes, its own
  colour levels by value, and bells.
- **Sizes:** 3×1, 4×1 and 5×1.
- **Mode:** today the ESP32 sends only `On-Grid` and `Off-Grid`. The strip
  shows own short lowercase words, "battery" (Off-Grid) and "main"
  (On-Grid), or a bigger, nice icon instead; the design compares both.
  Colours follow the inverter's own light: Off-Grid (on battery) green,
  On-Grid (from the grid) blue. The inverter has more modes; the strip
  should show them as icons too. (This replaces "show the HA text as it
  is", said earlier the same day.)
- **Voltages:** grid input and output voltage get colour levels (0010
  model), which also show present / absent (0 V).
- **Notifications:** the voltages get bells (0011 model); undervoltage of
  the grid input should notify. New: a notification when the mode
  changes; it tells that power at home was lost or came back.
- **Output voltage** is shown, although it is almost always 230 V.
- **Mode style is the user's choice** (owner, after design v6): "icon +
  word" or "big icon", picked in the app like the battery display choices
  and shown at once in the preview.
- **Icons are the user's choice too** (owner picked option B): the icon
  of each part is picked from a fixed list in the app; the defaults are
  the owner's picks. Layout choices (order, hiding parts, other
  arrangements) are not part of 0012: they come back as their own spec
  only if the fixed layout still bothers the owner after some weeks of
  use.
- **More values later:** more inverter parameters may join this widget in
  the future. Keep `InverterReading`, the settings block and the strip
  layout open for more values; no generic "any entity" engine now.
- **App layout:** a bottom navigation bar with three tabs, Battery |
  Inverter | Common (owner picked tabs over folding blocks and separate
  screens, and asked for the bar at the bottom).

## 2. Today

- One widget type: `BatteryWidgetReceiver` with `battery_widget_info.xml`
  (minimum = the 4×2 bucket, 261 × 126 dp, resizable). `MainActivity` is its
  configure activity.
- One sync path: `SyncBattery` → `BatterySource.fetch` → `BatteryReading` →
  `ReadingCache` → `WidgetRefresher.refreshAll()`. Today `refreshAll` redraws
  only `BatteryWidget`.
- Admin token: one template (`TemplateBuilder`) for all battery entities.
  User token (0009): one `GET /api/states/<id>` per entity.
- The app is one scrolling list of folding section cards (`SectionCard`):
  preview, actions (Refresh now, Add widget), Widget, Appearance,
  Connection, Token, Entities, Refresh, Notifications, Capacity. A tapped
  widget picks the preview tab (`EXTRA_TAPPED_WIDGET_ID`).
- Battery entity ids come from a prefix (`EntityFill`): `bps_bms` →
  `sensor.bps_bms_total_voltage`; every id can be typed by hand.

### 2.1 Inverter entities (owner's screenshots, 2026-11-19)

| Value | Default id (prefix `<p>`) | Seen state | Notes |
|---|---|---|---|
| Mode | `sensor.<p>_operation_mode` | `Off-Grid` | Text; no unit, no device class |
| Grid input voltage | `sensor.<p>_main_voltage` | `0.0 V` (grid off) | `voltage`, V, 1 decimal |
| Output voltage | `sensor.<p>_output_voltage` | `230.0 V` | `voltage`, V, 1 decimal |

The owner's prefix is `bps_inverter`. With no grid the input reads exactly
`0.0 V`.

## 3. Shape

Owner decisions are in §1. The points below are my calls; the owner read
them ("very good", 2026-11-19) and can still correct them.

### 3.1 Shared or per widget

Rule: what belongs to the data source or the phone is shared; what belongs
to how one widget looks is per widget.

| Setting | Where |
|---|---|
| HA address, token (0009) | Common |
| Refresh interval, auto-refresh | Common (one sync updates both widgets) |
| Theme, language | Common |
| Home-screen scale (0007) | Common (same launcher for both widgets) |
| Notifications main switch, Android permission | Common |
| Entities, prefix, Fill | Per widget |
| Display choices, font sizes | Per widget |
| Colour levels and bells | Per value, so per widget |
| Palette, icon set | Shared, in code (0010 base layer) |
| Capacity | Battery only |

### 3.2 App

- **Bottom navigation bar:** Battery | Inverter | Common, icon + label.
  Each widget tab: its preview with size tabs, "Add to home screen" for
  that widget type, then its sections (display and fonts, colours,
  entities; capacity on Battery only).
- **Top bar:** ↻ Refresh now as an icon (shared: one sync).
- **First tab:** a tapped widget opens its own tab; when setup is needed
  (no connection), the app opens on Common. Each tab keeps its own scroll
  position.
- **No on/off switch per widget:** blank inverter entities mean "not
  used": nothing is fetched and the strip shows "set up".
- **Build order:** 0010 and 0011 are built as planned (flat list). 0012
  then moves the sections into the tabs; the approved plans stay as they
  are.

### 3.3 Widget and data

- **A second widget type:** `InverterWidgetReceiver` with its own
  `inverter_widget_info.xml` (minimum 3×1, horizontal resize) and its own
  preview. Class names are fixed once shipped (home screens store them).
- **Strip content:** mode, grid input voltage, output voltage; small icons
  instead of labels (exact icons picked later). Voltages in whole volts
  ("230 V").
- **Mode style (display choice):** one setting for all sizes, default
  "icon + word" (the word says what the mode means). At 3×1 the word goes
  under the icon. Stored like the battery display choices (0007: defaults
  to the design value). Design: canvas v6, lines 1–4.
- **Icon choice:** five slots, each with a default (owner's picks from the
  candidate sheet, 2026-11-19):

  | Slot | Default |
  |---|---|
  | Mode "main" (On-Grid) | tower (`mdi` `transmission-tower`) |
  | Mode "battery" (Off-Grid) | `battery_charging_full` |
  | Other mode | `help` |
  | Grid input voltage | tower |
  | Output voltage | `home` |

  One shared inverter icon set (my call: one simple rule, any slot can use
  any icon), the candidates from the sheet: tower, `location_city`,
  `electrical_services`, `power_input`, `electric_meter`, `factory`,
  `apartment`, `offline_bolt`, `bolt`, `electric_bolt`,
  `battery_charging_full`, `battery_full`, `battery_5_bar`,
  `battery_android_full`, `battery_horiz_075`, `energy_savings_leaf`,
  `nest_eco_leaf`, `energy_program_saving`, `solar_power`, `help`,
  `question_mark`, `device_unknown`, `pending`, `info`, `power`, `home`,
  `house`, `cottage`, `home_work`, `home-lightning-bolt` (`mdi`), `outlet`.
  Material Symbols Rounded and Material Design Icons are both Apache-2.0
  (repo licence rule); each drawable names its source and licence, as in
  0010. Choices are stored by stable names (an enum, like 0010's
  `LevelIcon`); an unknown stored name falls back to the slot's default.
  Each icon has an EN and UK name for screen readers. The picker is a grid
  of the set, the same component as the 0010 level icon picker; the
  preview changes at once. The battery widget gets no icon choice now; it
  can reuse the picker later.
- **Mode table (in code):** HA text → word, icon, colour, matched ignoring
  case. `On-Grid` → "main", the "main" icon slot, blue; `Off-Grid` →
  "battery", the "battery" icon slot, green. More rows when the full mode
  list is known. Any other text → the "other mode" icon slot, grey, and
  the HA text as it is, so a new mode from the ESP32 still shows. `unavailable` / `unknown` →
  "no value". Words are translated (EN + UK, 0001 §12).
- **Tap:** opens the app on the Inverter tab. No ↻ and no auto-refresh
  switch on the strip; the battery widget and the app have them.
- **One sync for both:** the same run fetches battery and inverter values.
  Admin: three more lines in the same template (still one request). User
  token: three more GETs.
- **Stored reading:** a new `InverterReading` next to `BatteryReading` in
  the cache, so the battery widget code is not touched.
- **Stored settings:** the battery fields stay where they are; a new
  `inverter` block is added. Old files get its defaults, so no migration.
- **Each widget works alone:** a strip placed without inverter entities
  shows "set up", as the battery widget does.

### 3.4 Default voltage levels

Based on EN 50160 (230 V ±10 % = 207–253 V; Ukraine uses it as DSTU
EN 50160).

| Level | From | Grid input | Output |
|---|---|---|---|
| No grid / No power | 0 V | Info (blue) | Critical (red) |
| Low | 100 V | Warning (amber) | Warning (amber) |
| Normal | 207 V | Normal (green) | Normal (green) |
| High | 254 V | Warning (amber) | Warning (amber) |

Grid 0 V is blue, not red: an outage is normal for this system and the mode
already says "Off-Grid"; red for hours would be alarm fatigue. Output 0 V
is red: the house has no power.

### 3.5 Notifications

- **Mode change:** on by default. Any change of the mode text notifies;
  the first value after install is only stored (0011 rule). A change to or
  from `unavailable` / `unknown` (the ESP32 is offline) is not a mode
  change.
- **Voltage bells:** 0011 model. Default bell: grid input "Low"
  (undervoltage, 100–206 V). The grid "No grid" level has no bell by
  default, so a grid loss gives one notification (the mode change), not
  two.

## 4. Catches

- The mode is free text from the ESP32. An unknown text must still fit a
  3×1 strip (owner's grid: 3×1 ≈ 194 × 59 dp, 4×1 ≈ 267 × 59, 5×1 ≈
  340 × 59; from the v5 card sizes).
- One cell row is short. Fonts must fit (`TextFit` exists), and the launcher
  size quirks apply (some launchers draw widgets smaller; home-screen scale,
  spec 0007).
- Partial failure: the battery is fine but an inverter sensor is missing →
  only that part shows "no value".
- The user-token path makes more requests per sync.
- A grid loss changes the mode and the grid voltage at once (see §3.5).

## 5. Open questions

1. ~~One more notification case?~~ Decided (owner): undervoltage (§3.5).
2. ~~Mode on the strip: big icon or icon + word?~~ Decided (owner): the
   user picks it in the app (§3.3).
3. ~~Design?~~ Decided (owner): new rows on the existing canvas.
4. The mode-change notification: title, text and channel type.
5. The full mode list: the inverter model was not found online, so it
   comes from the owner's ESPHome YAML (the `operation_mode` mapping) or
   the nameplate model.

## 6. Non-goals

- No change to the battery widget's layouts or its design boards.
- No second app and no second sync schedule.
