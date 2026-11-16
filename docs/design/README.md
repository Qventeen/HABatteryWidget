# Widget design — 4×2, 5×2, 4×3, 5×3

Status: approved (v5), to be built in spec 0005 (layouts); temperatures in
spec 0004. Built before: v4 (one 4×2 layout, spec 0003).

The design was made on a design canvas; this folder keeps the latest
version in the repo. Current version: **v5**. On the canvas the v5 row has
one column per size: light, dark, sync error light, sync error dark. Git
keeps the older versions of this folder (v4 up to commit d1cf5fc:
`widget-4x2.html` and an older `widget-4x2.png`).

| File | What it is |
|---|---|
| `widget-4x2.png` | 4×2, card 267 × 132 dp: light, dark, sync error light and dark |
| `widget-5x2.png` | 5×2, card 340 × 132 dp: the same four frames |
| `widget-4x3.png` | 4×3, card 267 × 205 dp: the same four frames |
| `widget-5x3.png` | 5×3, card 340 × 205 dp: the same four frames |

The PNGs are rendered at 2× scale; 1 px of the design = 1 dp.

![Widget 4×2](widget-4x2.png)
![Widget 5×2](widget-5x2.png)
![Widget 4×3](widget-4x3.png)
![Widget 5×3](widget-5x3.png)

## Sizes

Card sizes are the owner's home screen grid (font scale 1.0, measured
2026-11-15). Each size has its own layout; it is not one layout scaled.

Main rule (as in v4): the most important data sits on the left, and
importance drops to the right, then down: SOC, then pack voltage and
current, then cell details.

| | 4×2 | 5×2 | 4×3 | 5×3 |
|---|---|---|---|---|
| Card | 267 × 132 | 340 × 132 | 267 × 205 | 340 × 205 |
| Padding, corner | 10, 24 | 10, 24 | 10, 24 | 10, 24 |
| Top row | 32 high, full width | 32 high, beside the ring | 44 high, full width | 48 high, full width |
| Buttons (touch / circle / icon) | 32 / 30 / 16 | 32 / 30 / 16 | 44 / 36 / 18 | 48 / 40 / 20 |
| Ring (size, stroke) | 76, 7 | 112, 8 — full card height, left | 86, 7 | 108, 8 |
| Pack pills (voltage, current) | 2 stacked, 6 gap, 89 wide | 2 stacked, 6 gap, 108 wide | 2 stacked, 6 gap, fill the width | 2 stacked, 7 gap, fill the width |
| Cell pills | column 66 wide, 4 gap | column 84 wide, 4 gap | 2 × 2 grid under ring and pack, 20 high, 5 / 4 gap | column 84 wide, 6 gap |

All sizes in dp. Gaps: 8 between ring, pack and cells; 4 under the top row
(4×3: 7 between the upper part and the cell grid). 4×2 is too narrow for
the 5×2 layout (the top row needs about 200 dp beside the ring), so its top
row spans the full width and the ring sits below it.

```
5×2                                 4×3                     5×3
╭─────╮ ⌛ 17 h ⚖ 11:29 (⟳)(↻)      ⌛ 17 h ⚖  11:29 (⟳)(↻)   ⌛ 17 h ⚖      11:29 (⟳)(↻)
│ SOC │ (26.50 V) (3.312 V)         ╭───╮ (  26.50 V  )     ╭─────╮ (26.50 V) (3.312 V)
│ 55  │ (       ) (Δ 12 mV)         │55 │ ( ↓ 10.0 A  )     │ SOC │ (       ) (Δ 12 mV)
│ kWh │ (↓10.0 A) (🌡 25 °C)        ╰───╯                   │ 55  │ (↓10.0 A) (🌡 25 °C)
╰─────╯ (       ) (▣ 27 °C)         (3.312 V) (Δ 12 mV)     ╰─────╯ (       ) (▣ 27 °C)
                                    (🌡 25 °C) (▣ 27 °C)
```

- **Top row:** time left, balancing icon, last update time (or the error
  icon, see States), auto-refresh toggle, manual refresh (right corner).
  The two button touch areas touch each other. 4×2 and 5×2 buttons are
  32 dp: below Android's 48 dp advice; the owner chose a 32 dp top row at
  2 rows.
- **Pack pills** fill their column. Neither value has a sign: the arrow and
  the colour show the direction. "↓ 125.5 A" and "↑ 3.98 kW" fit at the
  design card sizes; the widget fits its texts to the boxes at its bucket
  sizes (6 dp smaller, spec 0005), where these long values shrink a little
  ("↑ 3.98 kW" about 15.8 sp in 4×2).
- **Cell pills:** a small fixed gap, and the pills grow to fill the space,
  so the cell column starts and ends with the pack pills. Never spread the
  pills with big gaps.
- **4 cell pills:** average cell voltage, cell delta, battery
  temperature, MOSFET temperature (always last); 3 when no battery sensor
  is set.
- **Temperature pills:** icon, 3–4 dp space, value. No arrows, no empty
  slot. The battery pill shows the **average of all battery sensors**.
- All pills show numbers only (no text labels).

## Font sizes

Design sizes per size (sp) = 50 % of the font settings; 100 % = twice the
design size, 25 % = half (spec 0005). Above 50 % each text grows only as far
as its box allows. The design sizes fit the longest values at the design
card sizes; at the bucket boxes long worst-case values like "↑ 3.98 kW"
shrink a little (about 15.8 sp in 4×2).

| Group | 4×2 | 5×2 | 4×3 | 5×3 |
|---|---|---|---|---|
| SOC number | 28 | 40 | 32 | 42 |
| kWh / Ah under SOC | 10 | 12 | 11 | 12.5 |
| "SOC" label in the ring | 8.5 | 10.5 | 9.5 | 11 |
| Main values (voltage, current or power) | 17 | 21 | 22 | 22 |
| Cell pill values | 11 | 12 | 13.5 | 15.5 |
| Small text (time left, update time) | 10.5 | 10.5 | 10.5 | 11 |

## States

| State | Widget |
|---|---|
| Normal | As above; the update time in the top row |
| Refreshing | The ↻ icon dims; everything else stays |
| Sync error, data exists | The last values stay. A red ⚠ icon replaces the update time. No error text on the widget; the icon's content description is "Sync error: <reason>", and the app shows the full text |
| One value missing | "—" in its place, e.g. "Δ —" |
| Full | SOC ≥ 100 % and not discharging: no time left ("—") |
| No data yet / setup needed | One centered message ("No data from Home Assistant" plus the reason, or "Set up in the app") and an "Open app" button |

**Time left** shows from 0.5 A in Android's narrow format: "45m",
"17h 16m", and from 24 h "2d 7h" (days and hours); Ukrainian as Android
writes it. Otherwise "—".

**Update time:** the time when the last sync was today, else only the
date in the app's language ("Nov 14", "14 лист.").

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
| Current | `sensor.<p>_current` | Sets the state: idle when \|I\| ≤ 0.1 A, discharging below −0.1 A, charging above 0.1 A. Negative while discharging (confirmed on the owner's BMS) |
| Power | `sensor.<p>_power` | Shown when "power" is chosen, without the sign |
| Time left | computed | Discharging: Ah left / \|I\|. Charging: (full − Ah left) / I. From 0.5 A |
| Full capacity | `sensor.<p>_total_battery_capacity` | Optional; when empty or unavailable, the capacity typed in the app is used |
| Battery temperature | the list in the settings | The average of all listed sensors. Fill should add every battery sensor the BMS has, not only `sensor.<p>_temperature_sensor_1` (spec 0004) |
| MOSFET temp. | `sensor.<p>_power_tube_temperature` | |
| Average cell | `sensor.<p>_average_cell_voltage` | |
| Cell delta | `sensor.<p>_delta_cell_voltage` | Reported in V, shown in mV |
| Balancing | `sensor.<p>_balancing` | Read-only; `on`/`off`, `true`/`false` or `1`/`0` |
| Update time | app | Time of the last successful sync; only the date when it was on an earlier day |
| Auto refresh | app setting | The widget toggle turns periodic sync on or off; with no interval set, it uses 15 min |

## Colours and levels

| Element | Rule |
|---|---|
| SOC ring | green ≥ 25 %, amber < 25 %, red < 15 % |
| Current / power pill | amber when discharging, green when charging, grey when idle |
| Delta pill | plain ≤ 15 mV, amber > 15 mV, red > 40 mV |
| Battery temperature pill | shows the average; the colour comes from the worst sensor (spec 0004): amber below 5 °C or above 45 °C; red at 0 °C or below, or above 55 °C |
| MOSFET pill | amber above 65 °C, red above 80 °C |
| Sync error icon | red ⚠ |
| Balancing icon | green when on, grey when off |
| Auto-refresh toggle | green tonal when on; grey with a slash when off |
| Refresh button | grey; dimmed while refreshing |

Theme tokens (light / dark) are the same as in v4:

| Token | Light | Dark |
|---|---|---|
| Card | `#FFFFFF` | `#1A1D1B` |
| Text | `#1B1D1C` | `#F1F4F1` |
| Secondary text | `#5C625E` | `#A3ABA5` |
| Dim (off icons) | `#B9C0BA` | `#4E5651` |
| Pill / tile | `#F1F4F1` | `#242926` |
| Ring track | `#E1E6E1` | `#2E3530` |
| Ring bar | `#43A047` | `#66BB6A` |
| Accent (green) | `#2E7D32` | `#7BC67E` |
| Green tint | `#E5F3E6` | `#1E3320` |
| Amber text / tint / ring | `#A05A00` / `#FFF1E0` / `#F59E0B` | `#FFB869` / `#3A2A12` / `#FFB74D` |
| Red text / tint | `#D32F2F` / `#FDE7E7` | `#EF7070` / `#3D1E1E` |

The app theme is green; no blue.

## Notes for the build

- Each size is its own layout. Fill the real space with weights, not with
  computed widths (lesson from the 0004 size trials).
- The ring is a bitmap drawn at run time, with the content description
  "State of charge N percent" (as in v4).
- Every icon-only element needs a content description: current direction,
  balancing, sync error (with the reason), battery temperature, MOSFET,
  both buttons.
- `minSdk` is 26. Glance's rounded corners work only from Android 12
  (likely); older versions need rounded background drawables for the pills
  and the card.
- The app's overview card shows the same layout, drawn in Compose. Keep
  one plain-Kotlin model for both (which pills, texts and colours), so only
  the drawing is written twice.
