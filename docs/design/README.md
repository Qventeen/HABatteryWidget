# Widget design — 4×2

Status: design only, not built yet. It is the input for the widget part of
spec 0002. The design was made on a design canvas; this folder keeps the
latest version in the repo.

![Widget 4×2, light and dark](widget-4x2.png)

| File | What it is |
|---|---|
| `widget-4x2.html` | Live copy of the design. Open it in a browser. 1 px = 1 dp. |
| `widget-4x2.png` | Render of the HTML file (2× scale). |

## Layout

Card: 360 × 180 dp, 12 dp padding, 24 dp corner radius.

```
 ⌛ 17 h 16 min  ⚖                 11:29  (⟳auto) (↻)
 ╭───╮   ( 26.50 V )      ( 3.312 V )
 │SOC│   ( ↓ −10.0 A )    ( Δ 40 mV )
 │55 │   ( 172.7 Ah )     ( 🌡↑ 25 °C )
 ╰───╯   ( ▣ 27 °C )      ( 🌡↓ 23 °C )
```

- **Top row (44 dp), full width:** time left, balancing icon, last update
  time, auto-refresh toggle, manual refresh (top-right corner).
- **SOC ring (112 dp):** small "SOC" label, the number without "%", kWh left
  below it. The ring shows the charge level.
- **Pills, 2 × 4, numbers only (no text labels):**
  - middle column, pack and BMS: voltage, current, Ah left, MOSFET temperature;
  - right column, cells: average cell voltage, cell delta, highest sensor
    temperature, lowest sensor temperature.

## Data

Entity names use the ESPHome JK-BMS naming with a user prefix, for example
`bps_bms` → `sensor.bps_bms_total_voltage`.

| Element | Source | Note |
|---|---|---|
| SOC | `sensor.<p>_state_of_charge` | |
| kWh left | computed | Ah left × total voltage / 1000 |
| Ah left | `sensor.<p>_capacity_remaining` | |
| Voltage | `sensor.<p>_total_voltage` | |
| Current | `sensor.<p>_current` | The sign gives the state: < 0 discharging, > 0 charging, 0 idle (likely; check on the real BMS) |
| Time left | computed | Discharging: Ah left / \|I\|. Charging: (full − Ah left) / I. "—" when idle |
| MOSFET temp. | `sensor.<p>_mosfet_temperature` | |
| Highest / lowest temp. | computed | Max / min over the available `sensor.<p>_temperature_sensor_1…N` |
| Average cell | `sensor.<p>_average_cell_voltage` | |
| Cell delta | `sensor.<p>_delta_cell_voltage` | Shown in mV |
| Balancing | `binary_sensor.<p>_balancing` | |
| Update time | app | Time of the last successful sync |
| Auto refresh | app setting | Turns periodic sync on or off |

## Colours and levels

| Element | Rule |
|---|---|
| SOC ring | green ≥ 25 %, amber < 25 %, red < 15 % |
| Current pill | amber when discharging, green when charging, grey when idle |
| Delta pill | plain ≤ 15 mV, amber > 15 mV, red > 40 mV |
| Temperature pills | plain (no limits set yet) |
| Balancing icon | green when on, grey when off |
| Auto-refresh toggle | green tonal when on; grey with a slash when off |

Theme tokens (light / dark) are the CSS variables at the top of
`widget-4x2.html`. The app theme is green; no blue.

## Font sizes

These are the groups for the widget font-size settings (spec 0002):

| Group | Default |
|---|---|
| SOC number | 44 sp |
| kWh under SOC | 13 sp |
| Pill values | 14 sp |
| Small text (SOC label, time left, update time) | 10.5 sp |

## Notes for the build

- Glance has no ring-shaped progress view (likely), so the ring will be a
  bitmap drawn at run time, with the content description "State of charge
  N percent".
- Every icon-only element needs a content description: current direction,
  balancing, MOSFET, highest and lowest temperature, both buttons.
- The buttons are 44 dp. Android advises at least 48 dp for touch targets;
  use 48 dp if the row still fits.
- Open: temperature warning limits; whether min and max cell voltage appear
  on the widget (the sensors exist, the design does not show them).
