# Value colours — design

- **Date:** 2026-11-18
- **Status:** approved by the owner (2026-11-18), after the builder check.
  Plan: `docs/superpowers/plans/2026-11-18-0010-value-colors.md`.
  Amended by the owner on 2026-11-20 after the phone check of the
  Colours section: Default and Edit replace the "Custom colours" switch
  (§3.5, §3.6), and the colour picker gets smaller (§3.4).
- **Set:** `0010`, epic `epic-widget-improvements`. Code later on branch
  `0010-value-colors`, from the epic.
- **Builds on:** spec 0003 (widget v4 colours), 0005 (layouts v5), 0006 (app
  preview), 0008 (theme). On one topic the newest spec wins.
- **Used by:** 0011 (notifications: each level gets a notify switch; owner
  decision, 2026-11-18) and 0012 (colours of the inverter values).

## 1. Intent (owner, 2026-11-18)

The colour of a widget value should depend on the value itself. In the app
settings the owner sets how many levels a value has, where each level starts
and which colour it has. The widget then draws the value in the colour of
its level. Only the text colour changes; the widget parts stay as they are.
The SOC indicator may follow the levels too. (Refined in §3: text plus soft
background, the SOC ring, a voltage arrow and temperature icons.) The same
settings must work for the inverter values of 0012 (for example, levels by
voltage).

Additions (owner, 2026-11-18, after reading this concept):

- The app has a **minimal default set** of levels and colours (the levels
  that exist today). They work by default. (The defaults were then set
  value by value, §3.3 and §3.5.)
- **A switch per value** turns customisation on or off (replaced on
  2026-11-20 by the Default and Edit buttons, §3.5). With customisation
  on, the owner can add more levels.
- When customisation is turned on, the levels are **already filled with the
  defaults**, ready to edit.
- A value's customisation can be **reset to the start settings**, as
  elsewhere in the app.
- **Notifications ride on these levels** (owner decision, 2026-11-18): 0011
  adds a notify switch to each level. This spec is built first; it only has
  to keep the level model open for that switch.

## 2. Today

- `domain/WidgetLevels.kt` has fixed limits from design v4. There are no
  settings for them:

  | Value | BAD (red) | WARN (amber) |
  |---|---|---|
  | SOC (ring) | < 15 % | < 25 % (else OK, green) |
  | Cell delta | > 40 mV | > 15 mV |
  | Battery temperature | ≤ 0 or > 55 °C | < 5 or > 45 °C |
  | MOSFET temperature | > 80 °C | > 65 °C |

  The worst battery sensor colours the average-temperature pill.
- The main value (current or power) is coloured by direction, not by value:
  charging OK, discharging WARN, idle grey.
- `Level` (PLAIN, OK, WARN, BAD, IDLE) → `WidgetPalette` (one set per theme)
  → pill background, pill text and ring colour, through `WidgetColors`.
- Total voltage, average cell voltage and the value under SOC have no
  levels.

## 3. Design

### 3.0 Approaches considered

- **Fixed levels in code, only colours editable.** Least work, but the owner
  wants to add and remove levels. Rejected.
- **A fixed number of levels per value** (for example always 4). Simple
  storage, but the values need different counts (SOC 4, temperature 5,
  MOSFET 3, voltage 1). Rejected.
- **A dynamic level list per value on a shared base of types** (picked,
  owner, 2026-11-18): one model for colours, icons and, with 0011,
  notifications. A separate rule list for notifications was also rejected:
  colour and notification come from the same level, so they always agree.

### 3.1 Level model (owner, 2026-11-18)

One level system for all values, in two layers. A level is a band of values
("from X"); this spec uses "level" and "band" for the same thing.

- **Base layer: fixed in code, the same for every value.**
  - **Four types** (severity): Critical, Warning, Normal, Info. Each type
    has a default colour (red, amber, green, blue) and, with 0011, its own
    Android notification channel.
  - **The palette:** 8 named colours plus "plain" (§3.4).
  - **The icon set** (temperatures only for now, §3.3): none, snowflake,
    leaf, flame, flame with alert.
- **Value layer: a level list per value, dynamic.**
  - Each level has: a lower edge "from X" (it includes X; the first level
    has none), a name (for example "Cool"; 0011 puts it into the
    notification title), a type, a colour (default: the colour of its type;
    the owner can pick another palette colour), an icon (temperature values
    only) and a notify switch (added by 0011).
  - Each level also has a stable id (my call, after the builder check), so
    0011 can attach its notify state to a level that later moves or is
    renamed.
  - 1 to 6 levels per value. The levels always stay sorted by "from"; two
    levels may not share an edge.
  - **Add level** adds a level with an empty edge field. When the owner
    types a valid edge, the level takes its place in the sorted list, so it
    splits the range it falls in (my call: no guessed edge). An edge that is
    empty, not a number or equal to another edge shows a field error, as
    other app fields do.
  - **Delete** merges the range into the level below (the first level
    merges into the one above). The last level cannot be deleted.
  - A copied level keeps a link to its default name (translated by the
    app) until the owner types an own name; a language switch translates the
    untouched names (my call, changed while planning: the code that copies
    the levels has no access to translated strings, and this way names stay
    in the app language).
  - The default list of each value is in code and is not stored. Edit and
    Save give the value its own **copy** of the defaults (§3.5), not a live
    link: a later app version may change a default, and the owner's own
    list stays as set.
- **Why dynamic, not a fixed count:** the values need different counts
  (SOC 4, battery temperature 5, MOSFET 3, voltage 1). Prior art: the
  Grafana threshold editor (a base colour plus steps you add and remove).
- **The worst sensor** (battery temperature, today's rule kept): the pill
  shows the colour and icon of the sensor whose type ranks highest, in the
  order Critical, Warning, Info, Normal (my call: Info ranks above Normal so
  a cold sensor is not hidden).
- **One value in the app, with custom colours on (sketch):**

  ```
  Battery temperature                 Custom colours [on]
    below 0 °C   Freezing   Critical ▾   ❄    🔔
    from 0 °C    Cool       Info     ▾   ❄    🔔
    from 5 °C    Normal     Normal   ▾   🍃
    from 45 °C   Warm       Warning  ▾   🔥   🔔
    from 55 °C   Hot        Critical ▾   🔥!  🔔
    [+ Add level]                    [Reset to default]
  ```

  The 🔔 column comes with 0011. Since 2026-11-20 the switch is replaced by
  the Default and Edit buttons, and this list is the editor (§3.5, §3.6).

### 3.2 What gets coloured (owner, 2026-11-18, round 1)

- A level colours the text and a soft background of the same colour, as
  today.

  | Widget part | Levels |
  |---|---|
  | SOC ring | SOC levels; the SOC number stays plain |
  | Voltage pill | voltage levels (default: plain), **plus a direction arrow** like the current pill (new): up while charging, down while discharging, from the same current sign as the main pill |
  | Current/power pill | levels by intensity, **one list for charge and one for discharge**, because a battery can have different limits each way; idle is grey; defaults in §3.5; the arrow stays. A high level can notify (0011), for example a very high discharge current |
  | Average cell pill | the voltage levels, each edge divided by the cell count (owner, 2026-11-18); no own list |
  | Cell delta, battery temperature, MOSFET pills | levels (defaults §3.3 and §3.5; the worst battery sensor wins) |
  | Value under SOC (kWh or Ah) | plain (it repeats SOC) |
  | Inverter values | later (0012) |

### 3.3 Level icons on temperature pills (owner, 2026-11-18)

- Besides the colour, a temperature pill gets a small extra icon by level,
  next to the icon it has today (thermometer for the battery, chip for the
  MOSFET). Only temperatures for now.
- The icon shows the side (cold, normal, hot); the colour shows how bad it
  is.
- **The icon set** (owner picks from the candidate sheet, 2026-11-18), from
  Material Symbols Rounded (Apache-2.0, allowed by the repo licence rule):

  | Icon | Material Symbols name | Used for |
  |---|---|---|
  | none | – | custom levels only |
  | snowflake | `ac_unit` | cold levels |
  | leaf | `eco` | normal levels |
  | flame | `local_fire_department` | warm levels |
  | flame with alert | `emergency_heat` | hot levels |

  The custom-level icon picker offers exactly this set. More icons can join
  the set later.
- **Defaults** (edges as today's limits; names, types and icons from the
  owner's picks):

  | Value | Range | Name | Type (colour) | Icon |
  |---|---|---|---|---|
  | Battery | below 0 °C | Freezing | Critical (red) | snowflake |
  | Battery | 0–5 °C | Cool | Info (blue) | snowflake |
  | Battery | 5–45 °C | Normal | Normal (green) | leaf |
  | Battery | 45–55 °C | Warm | Warning (amber) | flame |
  | Battery | from 55 °C | Hot | Critical (red) | flame with alert |
  | MOSFET | below 65 °C | Normal | Normal (green) | leaf |
  | MOSFET | 65–80 °C | Warm | Warning (amber) | flame |
  | MOSFET | from 80 °C | Hot | Critical (red) | flame with alert |

  The MOSFET rows follow the battery picks (my call). Default names come
  from the app strings, so they are translated (proposal; 0011, question 9).
- **Look changes from today:** a normal temperature turns from plain to
  green with a leaf; 0–5 °C turns from amber to blue. Every temperature pill
  now always shows two icons (§4).

### 3.4 Palette (owner, 2026-11-18)

- **A fixed palette of 8 colours**, not a free colour picker. Each colour has
  six shades: pill text, pill background and SOC ring, for the light and the
  dark theme. Besides the 8 colours, a level can be "plain" (no colour, as
  today's PLAIN).
- Red, amber, green and grey are today's colours (`WidgetPalette`), kept
  unchanged so the approved v5 look stays. Grey is today's idle text on the
  plain pill background; its ring shade is new.
- Orange, teal and violet are free choices for custom levels (for example
  orange as a step between amber and red); no default uses them.
- **The picker in the app** (owner, 2026-11-20, after the phone check: nine
  chips were too many and wrapped to a second line): "no colour" (plain),
  drawn as an empty ring like the icon picker's "none", not as a white
  disc, then red, orange, amber, green, blue, violet and grey: eight chips,
  one row. Teal is not offered for levels; it stays in the palette because
  0012 colours the inverter's Standby mode with it.

  | Colour | Default for | Light text / bg / ring | Dark text / bg / ring |
  |---|---|---|---|
  | Red | Critical | `D32F2F` / `FDE7E7` / `D32F2F` | `EF7070` / `3D1E1E` / `EF7070` |
  | Orange | – | `C2410C` / `FFE9DD` / `E8590C` | `FF9A66` / `3D2215` / `FB8C4A` |
  | Amber | Warning | `A05A00` / `FFF1E0` / `F59E0B` | `FFB869` / `3A2A12` / `FFB74D` |
  | Green | Normal | `2E7D32` / `E5F3E6` / `43A047` | `7BC67E` / `1E3320` / `66BB6A` |
  | Teal | – | `00796B` / `E0F2F1` / `26A69A` | `5FC4B8` / `15302D` / `4DB6AC` |
  | Blue | Info | `1565C0` / `E3EEFB` / `1E88E5` | `8AB4F8` / `1B2A3E` / `64B5F6` |
  | Violet | – | `6B3FB8` / `EFE7FB` / `8E5CD9` | `B99CF2` / `2B2240` / `A788EC` |
  | Grey | – | `5C625E` / `F1F4F1` / `858C87` | `A3ABA5` / `242926` / `6B736D` |

- **Contrast** (WCAG, checked 2026-11-18; text on its background, ring on
  the card). Dark theme: all text ≥ 5.1, all rings ≥ 3.5. Light theme: text
  4.2–5.8, rings 3.0–5.0, except today's amber ring (2.1). Below the usual
  minimum on the light card are only today's red text (4.2; pill text is
  bold, so likely fine) and today's amber ring (2.1, looks pale). The owner
  kept both as they are (2026-11-18).
- Red and orange look close on the light card; they read apart best side
  by side.

### 3.5 Defaults, Default and Edit

- **Built-in default set:** each value has default bands in code; they are
  not stored in settings.
  - SOC (owner, 2026-11-18; amber widened to 40 % in round 2):

    | Range | Name | Type (colour) |
    |---|---|---|
    | below 20 % | Low | Critical (red) |
    | from 20 % | Getting low | Warning (amber) |
    | from 40 % | Normal | Normal (green) |
    | from 100 % | Full | Info (blue) |

    This changes today's look (today: red below 15 %, amber below 25 %,
    no blue).
  - Battery and MOSFET temperature: §3.3.
  - Cell delta (owner, 2026-11-18; checked against community rules of
    thumb for 16S LiFePO4, there is no standard number):

    | Range | Name | Type (colour) |
    |---|---|---|
    | below 10 mV | Balanced | Info (blue) |
    | from 10 mV | Normal | Normal (green) |
    | from 20 mV | High | Warning (amber) |
    | from 50 mV | Very high | Critical (red) |

    10 mV is the JK-BMS factory balance trigger (and the owner's setting):
    below it the balancer has nothing to do. Top-balanced packs show about
    6–25 mV near the top knee; 50 mV and more is past a healthy pack (check
    cells and connections). The delta is read under load every sync, not at
    rest, so the edges are looser than rest values. The name "Balanced" is
    my call. Look change: today the delta is plain below 16 mV, amber from
    16 mV and red from 41 mV.
  - Total voltage (owner, 2026-11-18): one level, "Normal", plain (no
    colour), as today. The pack voltage depends on the cell count; SOC
    already shows the charge.
  - Average cell (owner, 2026-11-18): uses the voltage levels (default or
    custom) with each edge divided by the cell count, so it gets the same
    names and colours; it has no own list and no buttons. Example, 16
    cells: a voltage edge of 52.0 V is 3.25 V per cell.
    - The cell count is worked out per reading: total voltage ÷ average cell
      voltage, rounded to a whole number (my call; the app has no cell-count
      setting or entity). An error of half a cell (about 1.6 V) would be
      needed to round wrong, so the count is exact in practice.
    - If either value is missing, the average cell pill is plain.
    - The average cell does not notify on its own; the voltage levels do
      (0011), so one crossing does not send two notifications (my call).
  - Current/power (owner, 2026-11-18): edges as C-rate (current ÷ full
    capacity), so the defaults fit any battery. The capacity is
    `BatteryMath.fullCapacityAh` (the BMS capacity entity, else the Capacity
    setting). Checked against EVE LF105 and LF100LA datasheets: standard
    charge and discharge 0.5 C, maximum continuous 1 C both ways. The
    charge and discharge lists have the same defaults (the datasheets are
    symmetric); the owner can make them differ.

    | Range (charge and discharge) | Name | Type (colour) |
    |---|---|---|
    | below 0.2 C | Light | Info (blue) |
    | from 0.2 C | Normal | Normal (green) |
    | from 0.5 C | High | Warning (amber) |
    | from 0.8 C | Very high | Critical (red) |

    Example, the owner's 100 Ah: blue below 20 A, green from 20 A, amber
    from 50 A (above the standard rate), red from 80 A (near the 1 C
    maximum, before a BMS cut-off). The name "Light" is my call.
    - Idle (|I| ≤ 0.1 A, `BatteryMath.IDLE_CURRENT_A`) is not a level: it
      is always grey, as today.
    - No capacity known: the pill keeps today's direction colours
      (charging green, discharging amber).
    - Edit copies the defaults as plain amps, worked out from the
      capacity at that moment and rounded to whole amps; the owner then
      edits amps (my call). With no capacity known at that moment, the
      copy is one level per direction in today's direction colours (charge
      green, discharge amber), and the owner adds levels (my call).
    - Orange is not in the defaults; the owner can add it as a custom step.
    - No current level notifies by default (0011).
    - Look change: today any charge is green and any discharge amber; now
      both are blue below 0.2 C, green from 0.2 C, and so on.
- **Default and Edit per value** (owner, 2026-11-20, after the phone
  check; this replaces the "Custom colours" switch of 2026-11-18, which
  made the screen long and gave no sign that Save worked):
  - Each value shows its levels as tags (§3.6) and two buttons, **Default**
    and **Edit**. A value without own bands uses the built-in default
    bands.
  - **Edit** opens the editor on a draft: the value's own bands, or a copy
    of the defaults when it has none (as the first "on" did). The owner
    adds, removes and edits bands. One editor is open at a time.
  - **Save** stores the draft as the value's own bands, closes the editor
    and shows the tags again, so the owner sees that it worked. With a
    field error the editor stays open and nothing is stored.
  - **Cancel** closes the editor and stores nothing.
  - **Reset** (in the editor; was "Reset to default") fills the draft with
    a fresh copy of the defaults. Nothing is stored until Save, so it does
    not ask.
  - **Default** (enabled only when the value has own bands) asks first,
    then deletes the own bands; the value uses the built-in defaults again.
    No hidden copy is kept (the switch's "off keeps the bands for the next
    on" of 2026-11-18 is dropped). This is the one action in the Colours
    section that asks.
  - Stored data does not change: `CustomLevels.on` now means "the value
    has own bands"; Default stores an empty `CustomLevels`. The field was
    never released, so no migration is needed (0001 §14).
- No switch for all values at once in the first version (owner,
  2026-11-18): the defaults work without any setup.

### 3.6 Code and storage

- Stored values (`on`, the own bands) default to `false` and an empty
  list, so the "defaults are empty" rule (0001 §14) holds.
- **Domain:** a pure function picks the level for a value; `WidgetModel`
  takes the levels from settings (or the defaults) instead of
  `WidgetLevels`, and carries each value's level colour (a palette colour
  or plain) and icon instead of today's `Level` OK, WARN and BAD. Idle
  current stays a fixed grey case (my call). `WidgetPalette` grows to the 8
  colours with 3 shades each per theme (§3.4). The colour follows the shown
  (rounded) number, as temperatures do today.
- **App (owner, 2026-11-18):** a new collapsible "Colours" section below
  Appearance, collapsed by default, the same pattern as the other sections
  (the app is one scrolling screen with no navigation library). It lists 7
  values: SOC, voltage, charge current, discharge current, cell delta,
  battery temperature, MOSFET. The voltage row also shows each edge per
  cell (for example "from 52.0 V · 3.25 V per cell") when the cell count is
  known (my call). Since 2026-11-20 (owner, after the phone check):
  - Each value shows its name, the Default and Edit buttons (§3.5) and its
    levels as **tags**: small pills in the widget's own look (the level's
    text colour on its soft background; plain levels as the plain pill),
    with the level icon for temperatures and the text "below 20 % · Low"
    or "from 20 % · Getting low". The type word is left out; the colour
    shows it.
  - Edit puts the editor in place of the tags (edge, name, type, colour
    chip, icon; sketch in §3.1). The edge and name fields look the same:
    both have their label on the border and a hint inside.
  - The app preview (0006) at the top shows the saved result; it scrolls
    out of view while editing lower down (accepted: levels are set once
    and rarely changed).
- **Stored** in `Settings` as a new field with a default; no migration is
  needed for a new field (0001 §14).

## 4. Catches

- A colour must read well on the light and the dark card. A free picker
  would allow unreadable colours, so the proposal is a fixed palette.
- Text plus soft background plus the ring (decided) means each palette
  colour needs six shades (§3.4).
- The main pill shows current (A) or power (W). Its levels are in amps and
  also colour the pill when it shows watts (owner, 2026-11-18): BMS limits
  are in amps, and the voltage changes little, so watts follow amps
  closely. The settings show the edges in "A".
- The voltage arrow changes the approved v5 look a little: the pill gets
  narrower room for the number, so the text fit must count the arrow, as it
  does for the main pill. The pill already supports the arrow (`PackPill`
  takes a `Flow`; the voltage pill passes `Flow.NONE` today).
- A level icon is a second icon in a small temperature pill. Normal levels
  have the leaf (§3.3), so a temperature pill **always** shows two icons, not
  only at a bad level. In narrow layouts (the 4×2 cell column) the number
  gets less room all the time and may get smaller. The text fit must count
  the icon.
- Together these change the approved v5 look (blue SOC at 100 %, blue or green
  low current, coloured delta, voltage arrow, temperature icons, green normal
  temperatures, blue cool battery). No design-canvas drawing (owner,
  2026-11-18): a drawing cannot prove the real fit, because launchers draw
  widgets at other sizes. The fit is checked in the app preview and on the
  owner's phone during the build, in experiment mode; the plan has an early
  fit-check task. The palette is fixed in §3.4.
- A missing value (sensor not set or unavailable) draws in the plain colour.
- More settings make the app screen longer; the Colours section stays
  collapsed and opens one editor at a time (§3.6).

## 5. Questions (all decided, 2026-11-18)

1. ~~Text colour only, or also the pill background?~~ Decided (owner,
   2026-11-18): text plus soft background, as today.
2. ~~SOC: ring, number, or both?~~ Decided: the ring; the number stays plain.
3. ~~Main value: colour by direction, or bands by value?~~ Decided (owner,
   2026-11-18): bands by intensity, one list per direction; the arrow stays.
4. ~~Which values get bands?~~ Decided: the table in §3.2.
5. ~~The palette: which colours and how many?~~ Decided (owner,
   2026-11-18): the 8 colours in §3.4; today's red and amber unchanged.
6. ~~Band edges: is "from X" inclusive; does it depend on the direction?~~
   Decided (owner, 2026-11-18): "from X" includes X, the same in both
   directions. 20 % is amber; red is below 20 %, so falling to 19 % enters
   red. The re-arm margin of 0011 stops repeat alerts near an edge. Decimal
   edges have the precision the value is shown with, because the colour
   follows the shown number (my call).
7. ~~Where in the app?~~ Decided (owner, 2026-11-18): a new "Colours"
   section below Appearance (§3.6).
8. ~~Should a band also carry a notification?~~ Decided (owner,
   2026-11-18): yes, added by 0011.
9. ~~Turning the switch off: keep or drop the own bands?~~ Decided (owner,
   2026-11-18): keep them for the next "on". Replaced (owner, 2026-11-20):
   no switch; Default deletes the own bands after asking (§3.5).
10. ~~Does reset turn the switch off; does it confirm?~~ Decided (owner,
    2026-11-18): it only refills the bands, and it asks for confirmation.
    Replaced (owner, 2026-11-20): Reset refills the editor's draft and does
    not ask; Default asks (§3.5).
11. ~~Also one switch for all values?~~ Decided (owner, 2026-11-18): no,
    one switch per value only (since 2026-11-20: Default and Edit per
    value, §3.5).
12. ~~Current/power levels in amps or watts?~~ Decided (owner,
    2026-11-18): amps for both displays.
13. ~~Default current levels?~~ Decided (owner, 2026-11-18): C-rate edges
    from the known capacity (§3.5).
14. ~~Idle colour: grey or blue?~~ Decided (owner, 2026-11-18): grey, as
    today; blue means Info, 100 % and Cool.
15. ~~Temperature marks for normal levels; which other marks?~~ Decided
    (owner, 2026-11-18): normal levels get a green leaf; the icon set and
    defaults are in §3.3.

## 6. Non-goals

- No new widget parts and no layout changes (owner: text colour only),
  except the direction arrow on the voltage pill and the level icons on the
  temperature pills (owner, 2026-11-18).
- No free colour picker in the first version (owner, 2026-11-18: the fixed
  palette of §3.4).
- One colour set for the whole app, not one per placed widget (my call).
