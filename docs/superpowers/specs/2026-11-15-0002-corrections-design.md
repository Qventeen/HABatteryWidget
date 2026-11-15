# Data and settings — design

- **Date:** 2026-11-15
- **Status:** written. The owner asked to implement it right away and to
  review the result (spec, code and app) at the end.
- **Set:** `0002`. The spec lives on `develop`; the code goes on branch
  `0002-data-settings` (from `develop`).
- **Builds on:** spec 0001 (`2026-11-14-0001-clean-rewrite-design.md`,
  merged in `e788867`) and widget design v3 (`docs/design/`, merged in
  `fab0d65`).
- **Split (owner, 2026-11-15):** **0002 = data and settings** (this file).
  **0003 = widget v3** (layout, ring, pills, display choices, font sizes,
  empty and error states). 0003 depends on the data that 0002 adds.
- The first draft of this file (the owner's raw correction list and open
  questions) is in git history, commit `c56d38c`.

## 1. Intent

The owner tried the 0001 build (0.1.0), listed corrections, and redesigned
the widget around the real BMS: a JK-BMS read through the ESPHome
integration, with entity ids like `sensor.bps_bms_current`.

This spec changes the data and the settings screen so that:

- the app reads exactly the values widget v3 needs, and nothing else;
- the user fills all entity ids fast from one prefix, and can still type
  any id by hand;
- the settings screen gets the owner's corrections.

The old widget layout stays until 0003. It only loses what has no data any
more (the cell grid).

## 2. Goals, non-goals, anti-goals

**Goals**

- Read: state of charge, total voltage, current, power, Ah left, average
  cell voltage, cell delta, MOSFET temperature, battery temperatures
  (a list), balancing.
- Fill all entity ids from a prefix; every field stays editable.
- The battery state comes from the current, with a ±0.1 A idle band.
- One URL. Plain `http://` works with any host, with a warning.
- Green theme, units in the setting labels, foldable settings sections.

**Non-goals (spec 0003)**

- The widget v3 layout, the SOC ring, kWh, the display choices (kWh or Ah,
  current or power), font-size settings, temperature warning limits, the
  one-sensor temperature layout, the new look of the app's overview card.
- Cell count and cell type (Li-ion / LiFePO4): an idea only, no use yet.

**Anti-goals** (a failure even if it works)

- The app writes anything to Home Assistant or the BMS. It only reads; it
  never uses control entities (switches, numbers, buttons).
- Fill changes stored settings without the user pressing Save.
- The token goes anywhere new (0001 rules stay: `TokenStore` only, never
  in logs, errors or UI state).

## 3. Owner decisions (2026-11-15)

1. Keep the power entity.
2. Drop the cell list and min/max cell voltage everywhere, app included.
3. Full capacity is typed by hand: the existing capacity setting stays.
4. Idle when |current| ≤ 0.1 A.
5. One URL. Plain `http://` to any host is allowed, with a warning.
6. Settings sections are foldable (or dialog, or own screen; the owner is
   open). Units go in the setting labels only, e.g. "Current (A)".
7. The prefix is a fast fill only. Every entity field stays a normal text
   field the user can type by hand.
8. Entity ids use `sensor.` (singular). Suffixes come from the owner's HA
   (table in 5.1). The MOSFET sensor is `_power_tube_temperature`.
9. Balancing: HA has a read-only entity and a control entity; the app uses
   only the read-only one, `sensor.<prefix>_balancing`.
10. Temperatures: the owner's BMS has one battery sensor plus the MOSFET
    sensor. The list has an Add button for more.
11. The display choices "kWh or Ah" and "current or power" are app-wide,
    not per widget (spec 0003).

## 4. Approaches (Claude's calls; the owner handed over the how)

**A. Where the new values live**

- **A1 (chosen):** 0002 fetches and stores every value widget v3 needs;
  0003 only draws them. The entities section and Fill must list all fields
  now anyway, so the data work happens once.
- A2: each spec adds only the values it shows. Cost: the entities section,
  Fill, the template and the stored format change twice.

**B. How Fill works**

- **B1 (chosen):** Fill overwrites every entity field in the section's
  draft. Nothing is stored until Save, so the user can still check and
  edit each field. Predictable, and Save is the safety net.
- B2: fill only empty fields. Keeps hand edits, but a changed prefix does
  not update the old ids, which surprises.
- B3: a confirm dialog before overwriting. More UI for little gain.

**C. Settings layout**

- **C1 (chosen):** foldable sections on one screen. Least code (no
  navigation); the six sections (Connection, Token, Entities, Refresh,
  Notifications, Capacity) fit. The same section blocks can move to
  own screens later.
- C2: a dialog per section. Bad for long forms (entities with a list).
- C3: an own screen per section. Needs navigation; more code and tests.

**D. When to warn about plain HTTP**

- **D1 (chosen):** warn only for `http://` to a host that is not local
  (the 0001 local rule: `localhost`, single-label names, `*.local`,
  private IPv4, CGNAT, IPv6 ULA and link-local). Plain HTTP at home is
  normal for HA; a warning there would be noise.
- D2: warn for every `http://` URL.

## 5. Design

### 5.1 Entities and Fill

All entity fields are optional except state of charge. All ids are full
HA ids; the existing id check (`domain.object_id`) stays, so a hand-typed
id may use any domain (for example `binary_sensor.`).

| Field (`EntitySettings`) | Fill sets | Unit in label | Required |
|---|---|---|---|
| `charge` | `sensor.<p>_state_of_charge` | % | yes |
| `totalVoltage` | `sensor.<p>_total_voltage` | V | |
| `current` | `sensor.<p>_current` | A | |
| `power` | `sensor.<p>_power` | W | |
| `capacityRemaining` (new) | `sensor.<p>_capacity_remaining` | Ah | |
| `averageCellVoltage` (new) | `sensor.<p>_average_cell_voltage` | V | |
| `deltaCellVoltage` (new) | `sensor.<p>_delta_cell_voltage` | V | |
| `mosfetTemperature` (new) | `sensor.<p>_power_tube_temperature` | °C | |
| `temperatures` (new, list, max 8) | `[sensor.<p>_temperature_sensor_1]` | °C | |
| `balancing` (new) | `sensor.<p>_balancing` | on/off | |

Removed: `fallbackPower`, `cells`, `MAX_CELLS`.

- **Prefix:** a new stored field `EntitySettings.prefix`. Valid when it
  matches `^[a-z0-9_]+$` after trim (HA object ids are lower case). The
  prefix itself is optional: entity ids typed by hand work without it.
- **Fill** is a pure domain function, `EntityFill.fromPrefix(prefix,
  current: EntitySettings): EntitySettings`. It returns `current` with the
  prefix and every id field above replaced (approach B1). The Fill button
  is enabled only when the prefix draft is valid. Save is blocked by a
  non-empty invalid prefix (`Field.PREFIX`, error under the prefix field);
  an empty prefix is fine. *(Builder check; Claude's call.)*
- **Temperatures:** a list editor like today's cell list: one field per
  sensor, a remove icon on each, an "Add sensor" button, at most 8
  (`MAX_TEMPERATURES`). Validation: each listed id must be valid (an empty
  row is an error, as with cells today); more than 8 is an error.

### 5.2 Reading model

`BatteryReading` (stored in the cache):

| Field | Type | From |
|---|---|---|
| `chargePercent` | `Double` | required, as today |
| `totalVoltageV`, `currentA`, `powerW` | `Double?` | as today |
| `capacityRemainingAh` | `Double?` | new |
| `averageCellVoltageV` | `Double?` | new |
| `deltaCellVoltageV` | `Double?` | new, volts as HA reports |
| `mosfetTemperatureC` | `Double?` | new |
| `temperaturesC` | `List<Double?>` | new, same order as the settings list |
| `balancing` | `Boolean?` | new |
| `updatedAtMillis` | `Long` | as today |

Removed: `cellVoltagesV`, `CellStats`, `BatteryMath.cellStats`,
`BatteryOverview.cellStats`. New: `BatteryMath.deltaMv(reading): Int?` =
`round(deltaCellVoltageV × 1000)`, null when missing.

### 5.3 Home Assistant template

One `POST /api/template` as today. The template returns one JSON object:

| Key | Value |
|---|---|
| `soc`, `total_v`, `current`, `power`, `ah_left`, `avg_cell`, `delta`, `mosfet` | `states(id) \| float(none)`; `none` for an empty id |
| `temps` | a list, one `states(id) \| float(none)` per listed sensor |
| `balancing` | the raw state string `states(id)`; `none` for an empty id |

- The fallback-power prelude is removed.
- **Balancing** is parsed in Kotlin (testable), case-insensitive: `on`,
  `true`, `1` → `true`; `off`, `false`, `0` → `false`; anything else
  (`unknown`, `unavailable`, other text) → `null`.
- Non-finite numbers become null, as today. A missing or non-finite `soc`
  is still a `BadResponse`.

### 5.4 State, time left, full charge

`BatteryMath.state(chargePercent, currentA)`, with `IDLE_CURRENT_A = 0.1`
(replaces `IDLE_POWER_W`):

1. FULL — rounded SOC ≥ 100 and (current is null or current ≥ −0.1 A);
2. UNKNOWN — current is null;
3. DISCHARGING — current < −0.1 A;
4. CHARGING — current > 0.1 A;
5. IDLE — otherwise.

The sign rule (negative = discharging) is the ESPHome JK-BMS convention
(likely); the owner checks it on the real BMS during review.

`BatteryMath.timeRemaining(reading, capacityAh)`:

- Direction from the state (CHARGING → to full, DISCHARGING → to empty);
  null otherwise.
- Null when |current| < `MIN_CURRENT_A` (0.5 A, unchanged). The idle band
  (0.1 A) and this limit are separate on purpose: at small currents the
  time would be days; spec 0003 decides how to show long times.
- **To empty:** `Ah left / |I|` when Ah left is known; else
  `SOC / 100 × capacity / |I|` (needs the capacity setting); else null.
- **To full:** `max(0, capacity − Ah left) / |I|` when both are known;
  else `(100 − SOC) / 100 × capacity / |I|`; null without capacity.

Notify-on-full uses the new state; `FullChargeRule` itself does not change.
The status text keeps showing the power value from the power entity.

### 5.5 Connection

- `ConnectionSettings(url)`. The fallback URL is removed; the source tries
  one URL. The stored key changes from `primaryUrl` to `url`.
- `UrlPolicy.check` returns `OK`, `PUBLIC_HTTP` or `INVALID`. `PUBLIC_HTTP`
  = `http://` to a host that is not local (rule D1). The source sends the
  request for `OK` and `PUBLIC_HTTP`.
- The connection section shows a warning under the URL field while the
  draft is `PUBLIC_HTTP`: "Plain http:// to an internet address sends your
  token unencrypted. Use https:// if you can." It is not an error; Save
  works.
- Removed: `Problem.INSECURE`, `SyncError.InsecureConnection`, the strings
  `error_insecure` and `field_url_insecure`.
- Redirects stay off (0001): a redirect could still move the token.

### 5.6 Settings screen

- **Foldable sections (C1):** `SectionCard` gets a header row: the title,
  a one-line summary, and a chevron icon. Tapping the header folds or
  unfolds the content. The fold state lives in `rememberSaveable`, not in
  the settings.
  - Start state: all folded, except Connection, Token and Entities when
    setup is needed (`BatteryOverview.setupNeeded`).
  - A save that returns an error unfolds the section that holds it (it is
    already open, because the user pressed its Save button).
  - Summaries: Connection — the URL host, or "Not set". Token — "Saved" or
    "Not set". Entities — the prefix, or "Set by hand", or "Not set" when
    the charge id is empty. Refresh — "Off" or "Every N min".
    Notifications — "On" or "Off". Capacity — "N Ah" or "Not set".
- **Entities section** (top to bottom): prefix field + Fill button; the
  nine single fields with units in their labels; the temperature list with
  Add; Save.
- **Units in labels**, e.g. "State of charge (%)", "Current (A)", "Power
  (W)", "Ah left (Ah)", "Cell delta (V)", "MOSFET temperature (°C)",
  "Temperature sensor 1 (°C)", "Battery capacity (Ah)", "Refresh interval
  (min)". The required mark stays on the charge field.
- **Capacity hint:** "Used for the time to full, and for the time to empty
  when there is no Ah left sensor."
- **Overview card:** the cell grid and the min/max colours go. Until 0003
  redraws the card, it lists every value read, one line each with its
  unit (Ah left, average cell, delta in mV, MOSFET, each temperature,
  balancing on/off). This lets the owner check the new data on the real
  BMS during review.

### 5.7 Old widget, kept working

- Remove the cell grid: `WidgetCells.kt`, the cell tiers in
  `WidgetLayoutSpec` (the no-cells tiers stay), the min/max/badge colours.
- The info row shows "Δ N mV" from `deltaMv(reading)`; the delta levels do
  not change.
- The status line is unchanged; the state now comes from the current.
- The static preview layout loses its cell rows; `widget_description`
  loses "cells".

### 5.8 Green theme

Replace the blue scheme in `ui/theme/Color.kt` (same four roles):

| Role | Light | Dark |
|---|---|---|
| primary | `#2E7D32` | `#7BC67E` |
| primaryContainer | `#1B5E20` | `#1E3320` |
| secondary | `#52634F` | `#B9CCB4` |
| tertiary | `#38656A` | `#A0CFD4` |

The primaries match design v3. The window themes hold no colours (checked).

### 5.9 Stored data

- 0.1.0 was never released, so 0001 §14 applies: the stored format may
  change; `schemaVersion` stays 1; no migration code.
- Old settings still load (`ignoreUnknownKeys`): charge, power, current
  and total voltage ids keep their keys; `primaryUrl`, `fallbackUrl`,
  `fallbackPower` and `cells` are dropped. The owner types the URL again.
- An old cache with the removed `insecure` error fails to decode and is
  reset by the existing corruption handler. That only loses the last
  reading.
- `StoredJsonV1Test` is updated to pin the new format.

### 5.10 Strings and tools

- Every new text in `values/strings.xml` and `values-uk/strings.xml`;
  removed strings go from both files. `NoHardcodedTextTest` stays green.
- `tools/mock_ha.py` returns the new JSON keys, with options for each
  value and `--balancing on|off`.

## 6. Tests

- **Domain:** `EntityFill` (every id, prefix kept, overwrite); `Validation`
  (prefix, temperatures list and max 8, URL without INSECURE); `UrlPolicy`
  (`OK` / `PUBLIC_HTTP` / `INVALID`, the 0001 local cases);
  `BatteryMath.state` (−0.1 / +0.1 edges, FULL, null current);
  `timeRemaining` (Ah-left path, capacity path, clamping, nulls);
  `deltaMv`.
- **Data:** `TemplateBuilder` (keys, no prelude, temps list, raw
  balancing, quoting); `TemplateResponse` (new fields, balancing strings,
  non-finite values); `HomeAssistantSource` (one URL, public HTTP is sent);
  `StoredJsonV1Test` (new format; old JSON with dropped keys still loads).
- **UI and widget:** `MainViewModel` (save connection and entities),
  `MainScreenTest` (fold and unfold, Fill fills the draft, Save stores),
  `WidgetContentTest` (no cells; delta from the sensor),
  `WidgetLayoutSpecTest`.
- **Done gate:** `bash ./gradlew lint testDebugUnitTest assembleRelease`.
- **Manual:** emulator with `tools/mock_ha.py`; screenshots of the
  settings screen (folded, unfolded, Fill) and the widget.

## 7. Left to the builder

Code structure inside the rules of 0001 (one module, manual DI, pure
`domain/`), the chevron icon and fold animation, the order of commits, and
the Ukrainian wording.
