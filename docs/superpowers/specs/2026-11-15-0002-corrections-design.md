# Corrections to the clean rewrite — design

- **Date:** 2026-11-15
- **Status:** draft. It keeps the owner's correction list and the widget
  redesign. It has not yet been through approaches, the builder check or
  owner approval. No code until it is approved.
- **Branch:** `develop` (this spec only). Design work happens on
  `0002-widget-design` (`docs/design/`).
- **Set:** `0002`
- **Builds on:** spec 0001 (`2026-11-14-0001-clean-rewrite-design.md`). Its
  code is on `0001-clean-rewrite`, which is waiting for owner review and is
  not merged yet. "Today" below means that branch.

## 1. Intent

The owner tried the 0001 build (0.1.0) and listed corrections. The owner
also redesigned the widget around the real BMS: a JK-BMS read through the
ESPHome integration, with entity names like `sensor.bps_bms_current`.

## 2. The owner's corrections

| # | Correction (owner's words, shortened) | Today (0001) | Notes |
|---|---|---|---|
| 1 | Green app theme again; no blue. | App theme is generated from blue `#00668B` (`ui/theme/Color.kt`). The widget uses blue `#1976D2` for the max cell and teal cell badges. | The old app used green `#4CAF50`. |
| 2 | Widget font sizes in settings: one size per data group, in a separate settings section. | Fixed sizes per widget size tier (`widget/WidgetDimens.kt`). | Groups from the design: SOC number, kWh, pill values, small text. |
| 3 | Physical units on measurements, e.g. "Current (A)". | Settings labels have no units. | Likely the settings field labels; widget values already show units. Q7. |
| 4 | Remove the fallback power logic. | `fallbackPower` entity and the template prelude that uses it. | |
| 5 | One URL only, no optional second URL. "We can use any URL the user gives." | Primary and fallback URL. Plain `http://` only to local or private hosts (0001 §11). | "Any URL" conflicts with the 0001 security rule. Q1. |
| 6 | A prefix field that fills all related entity names. Example prefix `bps_bms` → `sensor.bps_bms_state_of_charge`, `…_total_voltage`, `…_current`, `…_cell_voltage_1` … `_64`. | Each entity is typed by hand; at most 32 cells (`MAX_CELLS`). | Section 4. The new widget shows no cells. Q2. |
| 7 | Min and max cell voltage, and the delta, from sensors: `…_min_cell_voltage`, `…_max_cell_voltage`, `…_delta_cell_voltage`. | Computed from the cell list (0001 call 5). | The widget design shows only average and delta. Q3. |
| 8 | Foldable sections in the settings. | All sections are always open. | The owner's message stops at "Like…". Q6. |
| 9 | Widget redesign. | 0001 widget with per-cell display. | Section 3. |

## 3. Widget redesign

The saved design is `docs/design/` (README, HTML page, PNG). It is on branch
`0002-widget-design` until the design is finished and merged into `develop`.

In short (4×2 widget, 360 × 180 dp):

- **Top row:** time left, balancing icon, last update time, auto-refresh
  on/off toggle, manual refresh (top-right).
- **SOC ring:** "SOC" label, the number without "%", kWh left below. The
  ring shows the charge level.
- **Pills, numbers only (no labels):** voltage, current (arrow + colour show
  the direction), Ah left, MOSFET temperature; average cell voltage, cell
  delta, highest and lowest sensor temperature.
- **No per-cell display.**

New behaviour this brings:

- The auto-refresh toggle on the widget changes the app setting.
- The charge state comes from the sign of the current. The design assumes
  < 0 = discharging; check this on the real BMS.
- Time left moves into the top row. Its maths stays as in 0001.

## 4. Entity names from the prefix

With prefix `<p>`, the app fills these fields (ESPHome JK-BMS names):

| Value | Entity |
|---|---|
| SOC | `sensor.<p>_state_of_charge` |
| Total voltage | `sensor.<p>_total_voltage` |
| Current | `sensor.<p>_current` |
| Ah left | `sensor.<p>_capacity_remaining` |
| Average cell | `sensor.<p>_average_cell_voltage` |
| Min / max cell | `sensor.<p>_min_cell_voltage`, `sensor.<p>_max_cell_voltage` |
| Cell delta | `sensor.<p>_delta_cell_voltage` |
| MOSFET temperature | `sensor.<p>_mosfet_temperature` |
| Sensor temperatures | `sensor.<p>_temperature_sensor_1` … `_N` |
| Balancing | `binary_sensor.<p>_balancing` |
| Cells (if kept, Q2) | `sensor.<p>_cell_voltage_1` … `_64` |

Proposal (Claude): the prefix fills the fields, and each field stays
editable. That way one odd entity name does not block the rest.

## 5. What changes in the 0001 rules

- §6 and §9: delta, min and max come from sensors, not from the cell list.
- §8 and §9: no fallback URL and no fallback power.
- §9: the charge state comes from the current sign, not the power sign (Q4).
- §11: the URL rule is open (Q1).
- §14: 0.1.0 was never released, so "before the first release, stored data
  may change freely" applies. No migration is needed (likely). The owner's
  test install needs a reinstall or a data reset.

## 6. Open questions (owner)

1. **"Any URL".** Allow plain `http://` to public hosts? Cost: the HA token
   then travels unencrypted over the internet. Options: keep the 0001 rule;
   allow it with a warning; allow it with no warning.
2. **Cells.** The widget shows no cells. Keep the cell entities (up to 64)
   for the app screen, or drop cells completely?
3. **Min / max cell voltage.** Show them on the widget, in the app only, or
   not at all?
4. **Power entity.** Keep it (to show watts, or as the state source), or
   drop it now that the state comes from the current?
5. **Temperature limits.** Warning colours for MOSFET and sensor
   temperatures: which limits, or none?
6. **Foldable sections.** The message stopped at "Like…". Which example did
   you mean?
7. **Units.** Units only in the settings labels, or in other places too?
8. **Temperature sensors.** A fixed number N, or use whatever the prefix
   finds?

## 7. Next steps

Answer the open questions → approaches → full design → builder check →
owner approval → implementation plan.
