# Inverter widget — concept

- **Date:** 2026-11-18. Detailing started 2026-11-19.
- **Status:** detailed, after the builder check (2026-11-19); waiting for
  the owner's approval. A plan is written only after approval.
- **Set:** `0012`, epic `epic-widget-improvements`. Code later on branch
  `0012-inverter-widget`, from the epic.
- **Builds on:** 0009 (both connection ways must fetch the new values), 0010
  (colour levels for the inverter values), 0011 (bells), 0005 and 0006
  (layouts, app preview), 0007 (font sizes, home-screen scale). On one topic
  the newest spec wins.
- **Design:** v6 rows on the existing design canvas, **approved by the
  owner (2026-11-19)**; the default look is the "big icon" line. It is an
  extension: the existing boards and the v5 battery widget stay unchanged.

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
- **Mode:** the strip shows own short lowercase words, "battery"
  (`Off-Grid`) and "main" (grid; the ESP32 text is `Mains`, see §2.1), or a
  bigger, nice icon instead; the design compares both. Colours follow the
  inverter's own light: on battery green, from the grid blue. The inverter
  has more modes; the strip should show them as icons too. (This replaces
  "show the HA text as it is", said earlier the same day.)
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

Mode texts the ESP32 can send (owner's ESPHome YAML, work-mode code →
text): 0 `Power On`, 1 `Standby`, 2 `Mains`, 3 `Off-Grid`, 4 `Bypass`,
5 `Charging`, 6 `Fault`. The owner said "On-Grid" in talk; the real grid
text is `Mains`.

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
- **First tab:** a tapped widget opens its own tab; placing a strip opens
  the app on the Inverter tab (`MainActivity` is the configure activity of
  both widget types; my call). When the connection or token is missing,
  the app opens on Common. Each tab keeps its own scroll position.
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
  instead of labels (see icon choice below). Voltages in whole volts ("230
  V").
- **Mode style (display choice):** one setting for all sizes, default "big
  icon" (owner, 2026-11-19: looks better as the default). At 3×1 the word
  goes under the icon. Stored like the battery display choices (0007:
  defaults to the design value). Design: canvas v6, lines 1–4.
- **Mode table (in code; owner approved 2026-11-19):** the ESP32 text
  (matched ignoring case) → word, colour, default icon, notification
  channel.

  | ESP32 text | Word EN / UK | Colour | Default icon | Channel |
  |---|---|---|---|---|
  | `Power On` | power on / запуск | grey | `power_settings_new` | Info |
  | `Standby` | standby / пауза | teal | `mode_standby` | Warning |
  | `Mains` | main / мережа | blue | tower (`mdi` `transmission-tower`) | Info |
  | `Off-Grid` | battery / батарея | green | `battery_charging_full` | Warning |
  | `Bypass` | bypass / байпас | violet | `alt_route` | Info |
  | `Charging` | charging / заряд | amber | `charger` | Info |
  | `Fault` | fault / аварія | red | `error` | Critical |
  | any other text | the HA text as it is | grey | `help` | Info |

  Colours follow the inverter's own RGB light. The owner's inverter is an
  Anenji 5 kW Wi-Fi; the default RGB table of a sibling model
  (ANJ-11KW-48V-WIFI-P manual) gives battery green, AC (grid) blue, standby
  cyan, fault red, bypass purple, charging yellow, mapped here to the 0010
  palette; `Power On` has no light colour, so grey. Standby gets the
  Warning channel because the inverter most likely gives no output then
  (owner agreed). `unavailable` / `unknown` → "no value". Words are
  translated (EN + UK, 0001 §12); the Ukrainian words are short on
  purpose so they fit the "icon + word" pill at 4×1 (design v7; owner
  picked "запуск", "пауза", "заряд" over the longer words).
- **Icon choice:** ten slots: one per mode row above (the defaults there)
  plus grid input voltage (tower) and output voltage (`home`); owner's
  picks from two candidate sheets, 2026-11-19. One shared inverter icon set
  (my call: one simple rule, any slot can use any icon), the candidates
  from both sheets (45): tower, `location_city`, `electrical_services`,
  `power_input`, `electric_meter`, `factory`, `apartment`, `offline_bolt`,
  `bolt`, `electric_bolt`, `battery_charging_full`, `battery_full`,
  `battery_5_bar`, `battery_android_full`, `battery_horiz_075`,
  `energy_savings_leaf`, `nest_eco_leaf`, `energy_program_saving`,
  `solar_power`, `help`, `question_mark`, `device_unknown`, `pending`,
  `info`, `power`, `home`, `house`, `cottage`, `home_work`,
  `home-lightning-bolt` (`mdi`), `outlet`, `power_settings_new`,
  `restart_alt`, `mode_standby`, `bedtime`, `alt_route`, `call_split`,
  `swap_horiz`, `charger`, `battery_charging_90`, `ev_charger`, `error`,
  `report`, `warning`, `dangerous`. Material Symbols Rounded and Material
  Design Icons are both Apache-2.0 (repo licence rule); each drawable names
  its source and licence, as in 0010. Choices are stored by stable names
  (an enum, like 0010's `LevelIcon`); an unknown stored name falls back to
  the slot's default. Each icon has an EN and UK name for screen readers.
  The picker is a grid of the set, the same component as the 0010 level
  icon picker; the preview changes at once. The battery widget gets no
  icon choice now; it can reuse the picker later.
- **Tap:** opens the app on the Inverter tab. No ↻ and no auto-refresh
  switch on the strip; the battery widget and the app have them.
- **One sync, two independent parts** (owner picked option A after the
  builder check, 2026-11-19: today one ESP32 feeds both, but later the
  inverter may get its own device). The same run fetches battery and
  inverter values: admin, three more lines in the same template (still
  one request); user token, three more GETs. The answer is judged per
  part:
  - Each part has its own result: a reading or an error. A missing or
    `unavailable` battery charge fails only the battery part; the inverter
    part still updates, and its mode and voltage notifications still run.
    The same holds the other way.
  - A failure that touches both (HA not reachable, token rejected, no
    connection set) fails both parts.
  - "Configured" is checked per widget: the battery part needs the
    battery entities, the inverter part needs the three inverter
    entities. A part that is not configured is not fetched and its widget
    shows "set up". The sync runs when at least one part is configured.
  - 0011 level alerts run per part, only on that part's fresh reading.
  - `WidgetRefresher.refreshAll()` redraws both widget types; the widgets
    still render from local data only (0001 §13).
  - This changes the 0001 sync core (`SyncBattery`, the source port, the
    cache) and the 0009 states path; the template and states parsers stop
    failing the whole answer on a missing charge.
- **Stored reading:** a new `InverterReading` next to `BatteryReading` in
  the cache, each with its own last good reading and its own error. The
  existing cache error stays the battery's; the inverter's is a new field
  with a default, so old cache files read fine (my call; no cache
  migration, as in 0011).
- **Stored settings:** the battery fields stay where they are; a new
  `inverter` block is added. Old files get its defaults, so no migration.
  Defaults (0001 §14): entity ids and the prefix are blank; the mode style
  and the icon choices are display choices, so they default to the design
  values (as in 0003, 0007, 0008; my call).
- **Font sizes** (my call, 0007 model): two sliders on the Inverter tab,
  mode word and values, 25–100 %, step 5, 50 % = the design size, with
  "Reset sizes". The home-screen scale is shared (Common): both widgets
  sit on the same launcher.
- **Levels** (0010 model): two new level values, grid input voltage and
  output voltage, whole volts, no level icons, with the §3.4 defaults
  and their own "Custom colours" copy on the Inverter tab.
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

- **Mode change:** on by default; not configurable for now (owner). The
  notification names the new mode and takes its colour (owner): to
  "battery" green, to "main" blue, other modes their table colour. Title
  "Inverter: <word>", body "Measured at hh:mm" (0011 style; my call).
  Channel by type (0011: one channel per type; owner picked option A),
  per the mode table (§3.3): to "battery" (power lost) and to "standby"
  the Warning channel, so they pop up with sound; to "main" (power back)
  the Info channel, sound without pop-up; fault the Critical channel;
  other modes Info. The small icon follows the type (0011); the accent is
  the mode colour, not the type's default colour. Any change of the mode
  text notifies; the first value after install is only stored (0011
  rule). A change to or from `unavailable` / `unknown` (the ESP32 is
  offline) is not a mode change.
- **Voltage bells:** 0011 model. Default bell: grid input "Low"
  (undervoltage, 100–206 V). The grid "No grid" level has no bell by
  default, so a grid loss gives one notification (the mode change), not
  two. Re-arm margin (0011 §3.3, fixed per value): 5 V for both voltages
  (about 2 %; proposed after the builder check, waiting for the owner's
  OK): the grid often hovers near 207 V, so a smaller margin would ring
  the "Low" bell many times a day.
- **One notification slot per value** (0011): the two voltages and the
  mode each get their own slot; a new notification replaces the older
  one of the same value. The last mode is stored like the 0011 level
  state (first value only stored).

## 4. Catches

- The mode is free text from the ESP32. An unknown text must still fit a
  3×1 strip (owner's grid: 3×1 ≈ 194 × 59 dp, 4×1 ≈ 267 × 59, 5×1 ≈
  340 × 59; from the v5 card sizes).
- One cell row is short. Fonts must fit (`TextFit` exists), and the launcher
  size quirks apply (some launchers draw widgets smaller; home-screen scale,
  spec 0007).
- Partial failure: the battery is fine but an inverter sensor is missing →
  only that part shows "no value"; a failed battery part never freezes
  the strip (§3.3).
- The user-token path makes more requests per sync.
- A grid loss changes the mode and the grid voltage at once (see §3.5).

## 5. Open questions

1. ~~One more notification case?~~ Decided (owner): undervoltage (§3.5).
2. ~~Mode on the strip: big icon or icon + word?~~ Decided (owner): the
   user picks it in the app (§3.3).
3. ~~Design?~~ Decided (owner): new rows on the existing canvas.
4. ~~The mode-change notification?~~ Decided (owner): title, text and
   channel per mode (§3.5).
5. ~~The full mode list?~~ Decided: the owner's ESPHome YAML (§2.1) and
   the mode table (§3.3).
6. ~~Battery and inverter sync: one result or two?~~ Decided (owner,
   builder check): two independent parts (§3.3).
7. Re-arm margin for the two voltages: 5 V proposed (§3.5), waiting for
   the owner's OK.
8. Plan note: the 0012 plan names types from the 0009–0011 plans
   (`LevelValue`, `LevelAlerts`, `LevelNotifier`, the states source); it
   gets a quick re-check once 0011 is built, in case names changed.

## 6. Non-goals

- No change to the battery widget's layouts or its design boards.
- No second app and no second sync schedule.
