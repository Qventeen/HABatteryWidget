# Widget design — 4×2

Status: design only, not built yet. It is the input for spec 0003 (widget).
The design was made on a design canvas; this folder keeps the latest
version in the repo.

Current version: **v4**. On the canvas each version is a separate row: the
live board on the left, its picture on the right (newest at the bottom);
git keeps the older versions of this folder.

![Widget 4×2: normal, refreshing, sync error, no data](widget-4x2.png)

| File | What it is |
|---|---|
| `widget-4x2.html` | Live copy of the design. Open it in a browser. 1 px = 1 dp. |
| `widget-4x2.png` | Render of the HTML file (2× scale): four frames, light and dark. |

## Layout

Main rule: the most important data sits on the left, and importance drops
to the right: SOC, then pack voltage and current, then cell details.

Card: 360 × 180 dp, 12 dp padding, 24 dp corner radius. The widget starts
at 4×2 cells. It can be made bigger: the same layout stretches. It cannot
be made smaller than 4×2, and there is no other layout.

```
 ⌛ 17 h 16 min  ⚖               11:29  (⟳auto) (↻)

 ╭─────╮  ╭───────────╮  ( 3.312 V )
 │ SOC │  │  26.50 V  │  ( Δ 12 mV )
 │ 55  │  ╰───────────╯  ( 🌡  25 °C )
 │ kWh │  ╭───────────╮  ( ▣  27 °C )
 │     │  │ ↓ 10.0 A  │
 ╰─────╯  ╰───────────╯
```

- **Top row (44 dp), full width:** time left, balancing icon, last update
  time (or the sync error, see States), auto-refresh toggle, manual refresh
  (top-right corner). Each button shows a 36 dp circle inside a 44 dp touch
  area.
- **Gap:** 4 dp under the top row, so 8 dp from the button circles to the
  pills. No divider line.
- **SOC ring (104 dp, 8 dp stroke):** small "SOC" label, the number without
  "%", and below it **kWh left or Ah left** (an app setting, for the whole
  app). The ring shows the charge level.
- **Middle column, pack (takes the free width, about 118 dp):** two big
  pills, each half the column height (51 dp, 6 dp gap): pack voltage, then
  **current or power** (an app setting, for the whole app). Neither has a
  sign: the arrow and the colour show the direction. Power is in whole W
  below 1000 W, then kW with up to 2 decimals ("↓ 265 W", "↑ 3.41 kW").
  Checked: "↓ 125.5 A", "↑ 4.38 kW" and "314.0 Ah" fit.
- **Right column, cells (90 dp):** small pills, 19 dp high, spread over the
  column: average cell voltage, cell delta, battery temperature, MOSFET
  temperature (always last).
  - **1 battery sensor:** one pill with the thermometer and no arrow →
    4 pills.
  - **2 or more sensors:** highest (↑) and lowest (↓) → 5 pills.
  - All temperature pills share one layout: 13 dp icon, 10 dp slot for ↑ / ↓
    (empty when there is no arrow), 3 dp space, value. So the numbers line up.
- All pills show numbers only (no text labels).

## States

| State | Widget |
|---|---|
| Normal | As above; the update time in the top row |
| Refreshing | The ↻ icon dims; everything else stays |
| Sync error, data exists | The last values stay. The update time is replaced by a red "⚠ <short error>"; a long text ends in "…" |
| One value missing | "—" in its place, e.g. "Δ —" |
| Full | SOC ≥ 100 % and not discharging: no time left ("—") |
| No data yet / setup needed | One centered message ("No data from Home Assistant" plus the error, or "Set up in the app") and an "Open app" button |

**Time left** shows from 0.5 A: "45 min", "17 h 16 min", and from 24 h
"2 d 7 h" (days and hours). Otherwise "—".

## Data

Entity names use the ESPHome JK-BMS naming with a user prefix, for example
`bps_bms` → `sensor.bps_bms_total_voltage`. Fill sets them from the prefix;
every id can also be typed by hand.

| Element | Source | Note |
|---|---|---|
| SOC | `sensor.<p>_state_of_charge` | |
| kWh left | computed | Ah left × total voltage / 1000; shown when "kWh" is chosen |
| Ah left | `sensor.<p>_capacity_remaining` | Shown when "Ah" is chosen; also input for kWh and time left |
| Voltage | `sensor.<p>_total_voltage` | |
| Current | `sensor.<p>_current` | Sets the state: idle when \|I\| ≤ 0.1 A, discharging below −0.1 A, charging above 0.1 A. Sign confirmed on the owner's BMS (negative while discharging) |
| Power | `sensor.<p>_power` | Shown when "power" is chosen, without the sign |
| Time left | computed | Discharging: Ah left / \|I\|. Charging: (full − Ah left) / I. From 0.5 A |
| Full capacity | `sensor.<p>_total_battery_capacity` | Optional; when empty or unavailable, the capacity typed in the app is used |
| Battery temperatures | the list in the settings | Fill adds `sensor.<p>_temperature_sensor_1`; more with Add. 1 sensor → its value; 2+ → max and min |
| MOSFET temp. | `sensor.<p>_power_tube_temperature` | |
| Average cell | `sensor.<p>_average_cell_voltage` | |
| Cell delta | `sensor.<p>_delta_cell_voltage` | Reported in V, shown in mV |
| Balancing | `sensor.<p>_balancing` | Read-only; `on`/`off`, `true`/`false` or `1`/`0` |
| Update time | app | Time of the last successful sync |
| Auto refresh | app setting | The widget toggle turns periodic sync on or off; with no interval set, it uses 15 min |

## Colours and levels

| Element | Rule |
|---|---|
| SOC ring | green ≥ 25 %, amber < 25 %, red < 15 % |
| Current / power pill | amber when discharging, green when charging, grey when idle |
| Delta pill | plain ≤ 15 mV, amber > 15 mV, red > 40 mV |
| Battery temperature pills | amber below 5 °C or above 45 °C; red at 0 °C or below, or above 55 °C |
| MOSFET pill | amber above 65 °C, red above 80 °C |
| Sync error text | red, with the ⚠ icon |
| Balancing icon | green when on, grey when off |
| Auto-refresh toggle | green tonal when on; grey with a slash when off |
| Refresh button | grey; dimmed while refreshing |

Theme tokens (light / dark) are the CSS variables at the top of
`widget-4x2.html`. The app theme is green; no blue.

## Font sizes

These are the groups for the widget font-size settings (spec 0003):

| Group | Default |
|---|---|
| SOC number | 40 sp |
| kWh / Ah under SOC | 12 sp |
| Main values (voltage, current or power) | 22 sp |
| Cell pill values | 14 sp |
| Small text (SOC label, time left, update time, error) | 10.5 sp |

## Notes for the build

- Glance has no ring-shaped progress view (likely), so the ring is a bitmap
  drawn at run time, with the content description "State of charge N
  percent". This replaces spec 0001's `LinearProgressIndicator` choice.
- Every icon-only element needs a content description: current direction,
  balancing, sync error, battery temperature (or highest and lowest),
  MOSFET, both buttons.
- The buttons have a 44 dp touch area (36 dp visible circle). Android
  advises at least 48 dp for touch targets; use 48 dp if the row still fits.
- The ring content is tight: at the default sizes the line under SOC has
  about 3 dp of room inside the ring. The SOC and kWh/Ah font-size settings
  need upper limits.
- `minSdk` is 26. Glance's rounded corners work only from Android 12
  (likely); older versions need rounded background drawables for the pills
  and the card.
- The app's overview card shows the same layout, drawn in Compose. Keep
  one plain-Kotlin model for both (which pills, texts and colours), so only
  the drawing is written twice.
