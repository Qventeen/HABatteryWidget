# Battery profile (type and cells) — design

- **Date:** 2026-11-20. Detailed 2026-11-21.
- **Status:** approved by the owner (2026-11-21). The owner let me make the
  open calls (2026-11-21: "as you suggest"); each one is marked "my call"
  below.
- **Set:** `0013`, epic `epic-widget-improvements`. Code on branch
  `0013-battery-profile`, from the epic. Build order (owner, 2026-11-21):
  next after 0012, before 0014.
- **Builds on:** 0010 (value colours: default levels, `DefaultLevels`, the
  "Levels and alerts" section, named "Colours" before 0011), 0011 (bells,
  hysteresis, alert state), 0012 (the app tabs: the Capacity section is on
  the Battery tab) and spec 0001 (§12 strings, §14 stored data). On one
  topic the newest spec wins.

## 1. Intent (owner, 2026-11-20)

The widget should work for more than one battery kind. LiFePO4 is not the
only chemistry a JK-BMS runs, so the default voltage levels of 0010 (LiFePO4
per cell, §3.5) are wrong for other packs. The owner wants:

- **A battery type** in the app settings, used to pick the default preset of
  voltages per cell. The type is "only for understanding the default
  preset": it changes defaults, nothing else.
- **A cell count** setting, next to the capacity.
- The preset should set the default **voltage** levels and, probably, the
  default **current** levels too, because each chemistry has its own charge
  and discharge rates.

## 2. Today (after 0012)

- `LevelBasis(capacityAh, cells)` feeds every battery default. It is built
  once per reading (`LevelBasis.of(reading, settings)`) and used by the
  widget, the tags, the editor's copy and the alerts. The inverter values
  use `LevelBasis(null, null)`.
- Voltage defaults: LiFePO4 per-cell edges (3.00 / 3.20 / 3.45 / 3.55 /
  3.65 V) times the cell count of the reading (`BatteryMath.cellCount`:
  total voltage ÷ average cell voltage, rounded). No count → one plain
  level. The average cell pill uses the voltage levels ÷ the cell count.
- Current defaults: C-rate edges 0.2 / 0.5 / 0.8 C (both directions)
  times the capacity (`BatteryMath.fullCapacityAh`: the sensor when above
  0 Ah, else the Capacity setting). No capacity → one level in today's
  direction colours.
- Default hysteresis: voltage 0.025 V per cell (0.4 V with no count),
  current 0.05 C (1 A with no capacity) (`LevelAlerts.defaultMargin`).
- The Battery tab, top to bottom: preview, Add to home screen, Widget,
  Levels and alerts, Entities, Battery capacity. The Capacity section has
  one field and Save. Its title "Battery capacity" repeats the tab name.
- Own levels (Edit, 0010 §3.5) are a copy and never follow a default.
- The cell count from the reading is exact in practice: to round wrong,
  the reading would have to be off by half a cell (about 1.6 V on LiFePO4).

## 3. Design

### 3.0 Approaches considered

- **A. A type setting with a preset table per type in code** (chosen):
  small, works offline, fits `LevelBasis`; the owner keeps Edit for
  anything the preset gets wrong.
- **B. Read the BMS's own limits from Home Assistant** (OVP, UVP, charge
  voltage): the best numbers, but it needs new entities and both connection
  ways (0009) to fetch them, and not every setup exposes them. Later, not
  now (concept §4.5).
- **C. Editable presets per type:** Edit already does this per value. A
  second editing layer would make the screen heavier for no gain.

### 3.1 The Pack section (my call)

The Capacity section becomes the **Pack** section ("Pack" / "Збірка"). The
title does not repeat the tab name (concept §3). It stays at the bottom of
the Battery tab, below Entities: it is set once.

```
Pack                                   LiFePO4 · 16S · 100 Ah   ▾
Type
[ LiFePO4 ][  Li-ion  ][   LTO   ]
Sets the default voltage and current levels. Own levels stay as they
are. Li-ion means NMC.
┌ Cells in series ─────────────────┐
│ 16                               │
└──────────────────────────────────┘
Used when the total voltage or average cell sensor is empty.
┌ Capacity (Ah) ───────────────────┐
│ 100                              │
└──────────────────────────────────┘
Used when the total capacity sensor is empty or unavailable.
[ Save ]
```

- **Type** is a `ChoiceRow` (the segmented row of Widget, Appearance and
  Access token). Like those rows it stores at the tap; the widget redraws
  (every settings write refreshes it). With no stored type the row shows
  LiFePO4 selected (§3.2).
- **Cells in series** and **Capacity** are text fields with one Save for
  both. Save checks both fields; with any field error nothing is stored and
  each field shows its own error. A blank field stores `null`. The capacity
  field keeps today's parsing, range and hint.
- **Summary:** the type (always), then the typed cell count as "16S" and
  the typed capacity as "100 Ah" when they are set, joined by " · ". "16S"
  is the usual pack notation and needs no plural forms in Ukrainian.
- Strings (English / Ukrainian), all in `strings.xml` (0001 §12):

  | Key idea | English | Ukrainian |
  |---|---|---|
  | title | Pack | Збірка |
  | type label | Type | Тип |
  | type hint | Sets the default voltage and current levels. Own levels stay as they are. Li-ion means NMC. | Задає типові рівні напруги та струму. Власні рівні не змінюються. Li-ion — це NMC. |
  | cells label | Cells in series | Комірок послідовно |
  | cells hint (no count) | Used when the total voltage or average cell sensor is empty. | Використовується, коли датчик загальної напруги або середньої комірки порожній. |
  | cells hint (counted, §3.3) | The sensors count 16 cells now. This number is used only when the total voltage or average cell sensor is empty. | За датчиками зараз 16 комірок. Це число використовується, лише коли датчик загальної напруги або середньої комірки порожній. |
  | cells error | Enter a whole number from 1 to 32 | Введіть ціле число від 1 до 32 |
  | capacity label | Capacity (Ah) | Ємність (А·год) |

  The type names "LiFePO4", "Li-ion" and "LTO" and the "%1$dS" summary part
  are `translatable="false"`. The capacity hint and errors stay as they are.

### 3.2 Battery type and presets

- Types: **LiFePO4**, **Li-ion** (NMC, "ternary lithium" in the JK-BMS
  app) and **LTO** — the three chemistries JK-BMS knows (concept §3).
- **No type stored = LiFePO4** (concept §4.1, my call). Today's defaults
  stay as they are for every existing install, the owner's pack is
  LiFePO4, and JK-BMS boards ship set for LiFePO4. The stored field stays
  `null` until the owner taps a type (0001 §14).
- The type changes only the default **voltage** and **current** levels.
  SOC, cell delta and the temperatures keep one default list (§6). The
  inverter values do not use it.
- Each preset keeps the shape, names, types, sides and bells of 0010: six
  voltage levels, and four current levels for charge and four for
  discharge, with no default bells.
- **Voltage per cell** (times the cell count, rounded half up to 0.01 V,
  as today):

  | Level | LiFePO4 (0010) | Li-ion (NMC) | LTO | Name | Type |
  |---|---|---|---|---|---|
  | ↓ | 3.00 V | 3.40 V | 2.00 V | Low | Critical |
  | ↓ | 3.20 V | 3.60 V | 2.20 V | Getting low | Warning |
  | middle | — | — | — | Normal | Normal |
  | ↑ | 3.45 V | 4.10 V | 2.65 V | Full | Info |
  | ↑ | 3.55 V | 4.15 V | 2.70 V | High | Warning |
  | ↑ | 3.65 V | 4.20 V | 2.80 V | Very high | Critical |

  Examples: 14S Li-ion (a usual 48 V pack): ↓47.6 / ↓50.4 / ↑57.4 / ↑58.1 /
  ↑58.8 V. 22S LTO: ↓44 / ↓48.4 / ↑58.3 / ↑59.4 / ↑61.6 V.

  How the numbers were picked (one rule for all three, as in 0010):
  - **Very high** = the cell's charge limit. LiFePO4 3.65 V (EVE LF105,
    JK OVP). Li-ion 4.20 V, the standard limit of NMC cells (SVOLT 21700,
    common 18650 sheets; some prismatic cells allow 4.30 V). LTO 2.80 V,
    the lowest charge limit in the datasheets found (Leclanché, OSN 2.80 V;
    others 2.85–2.90 V).
  - **Full** = a usual charge target. LiFePO4 3.45 V (JK charge target).
    Li-ion 4.10 V (a common long-life target). LTO 2.65 V (the JK LTO
    preset's "SOC 100 %" voltage is 2.68 V).
  - **High** = between Full and Very high. LiFePO4 3.55 V, Li-ion 4.15 V,
    LTO 2.70 V (also the JK LTO preset's OVP).
  - **Low** = well above the discharge cut-off, so red comes before the BMS
    stops the pack. LiFePO4 3.00 V (cut-off 2.5 V, JK UVP 2.6 V). Li-ion
    3.40 V (cut-off 2.75–3.0 V). LTO 2.00 V (cut-off 1.5 V, JK LTO preset
    UVP 1.8 V).
  - **Getting low** = Low + 0.20 V for all three.
  - How sure: LiFePO4 is the owner-confirmed 0010 table. Very high is
    certain for all three; Full and High are likely; Low and Getting low
    for Li-ion and LTO are a guess within the safe range. They are a start;
    the owner can Edit them (owner, 2026-11-18: "stop polishing defaults").
  - Known effect, as in 0010: the edges are rest voltages, so under load a
    pack shows a colour lower than at rest.
- **Current** (C-rate × full capacity). **Charge and discharge have their
  own preset** (owner, 2026-11-21: "this often different"):

  | Level | LiFePO4 (0010) charge = discharge | Li-ion charge | Li-ion discharge | LTO charge | LTO discharge | Name | Type |
  |---|---|---|---|---|---|---|---|
  | ↓ | 0.2 C | 0.2 C | 0.4 C | 0.4 C | 0.4 C | Light | Info |
  | middle | — | — | — | — | — | Normal | Normal |
  | ↑ | 0.5 C | 0.5 C | 1 C | 1 C | 1 C | High | Warning |
  | ↑ | 0.8 C | 0.8 C | 1.3 C | 5 C | 6 C | Very high | Critical |

  One rule for every column: **High** = the standard rate, **Very high** ≈
  0.8 × the maximum continuous rate, **Light** = 0.4 × the standard rate.
  - LiFePO4: standard 0.5 C, maximum continuous 1 C, both ways (EVE LF105,
    LF280). Only the 30 s pulse differs (1 C charge, 3 C discharge), and a
    pulse is not a level. So both directions keep the owner's 0010 numbers.
  - Li-ion charge: standard 0.5 C, maximum about 1 C (SVOLT 21700, a 75 Ah
    prismatic NCM cell; Samsung SDI 94 Ah recommends 72 A ≈ 0.8 C).
  - Li-ion discharge: standard 1 C (SVOLT 21700), maximum continuous about
    1.6 C (Samsung SDI 94 Ah: 150 A, the usual prismatic cell of home
    packs; cylindrical cells often allow more). 0.8 × 1.6 = 1.28, rounded
    to 1.3 C. Example 100 Ah: ↓40 A / ↑100 A / ↑130 A.
  - LTO charge: standard 1 C (Yinlong 66160 40 Ah, a 45 Ah 66160 cell),
    maximum continuous 6–8 C (Leclanché 6 C, the 45 Ah cell 8 C); 0.8 × 6 C
    = 4.8, rounded to 5 C. Example 40 Ah: ↓16 A / ↑40 A / ↑200 A.
  - LTO discharge: standard 1 C, maximum continuous 8–10 C (the 45 Ah cell
    8 C, Yinlong 40 Ah 10 C); 0.8 × 8 C = 6.4, rounded to 6 C. Example
    40 Ah: ↓16 A / ↑40 A / ↑240 A.
  - How sure: likely. They are a start, like the voltages. The real limit
    of a pack is often the BMS or the inverter, not the cell; Edit covers
    that.
- **Hysteresis** (my call): stays 0.025 V per cell and 0.05 C for all
  three types. 0.025 V is 2–4 % of each chemistry's cell working range
  (LiFePO4 2.6–3.65 V, Li-ion 3.0–4.2 V, LTO 1.8–2.8 V), so one number
  fits. (Earlier I said the preset should carry it; after the numbers above
  it does not need to.)
- Edit copies the defaults of the type at that moment (0010 §3.5); Reset
  in the editor does the same. Own levels never change when the type
  changes.

### 3.3 Cell count

- **Rule** (concept §4.3, my call, the same rule as the capacity): the
  count from the reading wins; the typed count is used only when the
  reading cannot count (total voltage or average cell voltage missing, or a
  count below 1). Blank = only the reading (today). Owner, 2026-11-21: "as
  you suggest".
- Why: the reading's count is exact in practice (§2), so a typed number
  that differs is almost always a typo. One rule for both pack fields is
  simpler than "the setting overrides the reading".
- **Mismatch:** no warning (my call). The reading wins, so a wrong typed
  number never changes what the widget shows while both sensors work.
- **Counted hint** (owner, 2026-11-21: a changed typed count did not move
  the voltage levels and looked broken): while the reading counts the
  cells, the cells hint names that count (plural forms in Ukrainian), so
  the user sees which count is used. With no count it keeps the plain hint.
- **Range:** a whole number from 1 to 32 (my call). JK-BMS boards go up to
  24 cells; 32 leaves room. Anything else (0, 33, "15.5", letters) is a
  field error.
- Where it counts: the default voltage levels, the average cell pill
  (voltage levels ÷ cells) and the default voltage hysteresis. With the
  typed count, a setup whose total voltage sensor is missing now gets
  coloured voltage defaults and per-cell edges.

### 3.4 Code and storage

- `domain/model/BatteryType`: `enum class BatteryType { LIFEPO4, LI_ION,
  LTO }`, `@Serializable`.
- `Settings`: two new top-level fields next to `capacityAh`:
  `batteryType: BatteryType? = null` and `cellCount: Int? = null`. New
  fields with empty defaults need no migration and no `schemaVersion` bump
  (0001 §14). `capacityAh` stays where it is (a move would be a rename and
  need a migration). A type written by a later build reads as `null`
  (`StoreJson.coerceInputValues`), so as LiFePO4.
- `LevelBasis(capacityAh, cells, type: BatteryType = LIFEPO4)`:
  `LevelBasis.of(reading, settings)` sets `cells = reading count ?:
  settings.cellCount` (each only when ≥ 1) and `type = settings.batteryType
  ?: LIFEPO4`. The inverter's `LevelBasis(null, null)` stays as it is.
- The per-type numbers live in one domain place (for example a
  `BatteryPreset` per type: five volts per cell, three charge C-rates and
  three discharge C-rates), and `DefaultLevels` builds the voltage, charge
  and discharge rows from it. The rest of
  `DefaultLevels` stays as it is.
- `Validation.cells(count)` with `MAX_CELLS = 32`; a new `Field.CELLS`.
- `MainViewModel`: `saveBatteryType(type)` (at the tap) and one save for
  cells + capacity (replaces `saveCapacity`); no sync after either, as
  today.
- `ui/sections/PackSection` replaces `CapacitySection`.
- Docs: `docs/ai/agents/architecture.md` (`LevelBasis` and the stored
  fields) and the README feature line ("capacity" → "pack: type, cells,
  capacity").

### 3.5 Alerts re-check (0011 §7)

0011 §7 asked to re-check the alert state when 0013 changes `LevelBasis`.
Result: no change is needed.

- The default voltage and current lists keep the ids 1..n for every type.
  When the type changes, or a cell count or capacity becomes known, a
  value's stored level id can point to another level. The next check then
  sees an "entered" level. It posts only when that level's bell is on, and
  no default voltage or current level has a bell. So nothing is posted.
- Own levels do not depend on the type, so their state stays valid.
- Rule for later specs: a preset that adds a default bell to voltage or
  current must also handle the type change (for example, start that
  value's state fresh), or the first sync after a change may post once.

## 4. Catches

- With no stored type the row shows LiFePO4 selected, while the stored
  value is `null`. The effect is the same; a tap stores the type.
- An owner with own voltage or current levels sees no change after a type
  change. The type hint says "Own levels stay as they are"; Default (0010
  §3.5) brings the new type's defaults.
- The Li-ion and LTO numbers are not checked against the owner's own pack
  (it is LiFePO4). They are a documented start, not a safety limit; the
  BMS keeps the real limits.
- `MainScreenTest` (instrumented) finds the Capacity section by its title
  and checks the section order: it needs the new title.
- `copyForCustom` rounds current edges to whole amps: on a small LTO pack
  (for example 2 Ah) 0.4 C is 0.8 A → 1 A, and on a 1 Ah Li-ion pack the
  discharge edges 1 C and 1.3 C both become 1 A. This is the known
  small-pack case of 0010 (parked minor), not new; the editor shows the
  error and the owner fixes the draft.

## 5. Questions (all decided, 2026-11-21)

1. No type set → LiFePO4 defaults (§3.2, my call).
2. Li-ion and LTO presets → the tables of §3.2 (my call, from datasheets
   and the JK LTO preset; the owner approves them with this spec).
3. Cells → 1–32; the reading wins; the typed count fills a gap (§3.3; the
   owner agreed to my suggestion).
4. Temperatures per type → not now (§6, my call).
5. BMS limits from Home Assistant → later (§3.0 B).
6. Charge and discharge presets → separate per type (owner, 2026-11-21;
   the tables of §3.2). LiFePO4 stays the same both ways (its datasheets
   are symmetric for continuous current).

## 6. Non-goals

- **Temperature defaults per type.** LiFePO4 and Li-ion share the same safe
  ranges (charge 0–45 °C), so only LTO differs, and LTO datasheets disagree
  widely (charging from −20 °C to −50 °C). An LTO owner edits the
  temperature levels. A later spec can add it.
- SOC and cell delta per type.
- Other chemistries (sodium-ion, lead-acid).
- Reading the BMS's own limits from Home Assistant (§3.0 B).
- A warning when the typed and the counted cells differ.

## 7. Testing and done

- Unit tests: `DefaultLevels` per type (voltage at 16S, 14S, 22S; charge
  and discharge current at 100 Ah and 40 Ah, each direction from its own
  preset; no count / no capacity unchanged); `LevelBasis.of`
  (reading count wins, typed count fills a gap, null type = LiFePO4);
  `Validation.cells` (blank, 1, 32, 0, 33); the ViewModel saves (type at
  the tap; cells + capacity together; any error stores nothing); settings
  JSON (an old file without the fields loads `null`; an unknown type value
  reads as `null`); the average cell pill with a typed count; the voltage
  hysteresis with a typed count.
- Done gate: `./gradlew lint testDebugUnitTest assembleRelease
  :app:compileDebugAndroidTestKotlin`.
- Owner check on the phone (debug APK): the Pack section in both languages,
  a type change seen in the Levels and alerts tags and on the widget.

Sources (checked 2026-11-21):

- EVE LF105 / LF100LA datasheets and JK-BMS LiFePO4 settings (0010 §3.5);
  EVE LF105 and LF280 specs (1 C / 1 C continuous, 30 s pulse 1 C / 3 C).
- Samsung SDI 94 Ah NMC cell (4.15 V, 2.7 V, recommended charge 72 A,
  continuous discharge 150 A).
- Yinlong LTO 66160 40 Ah listings (charge 1 C, continuous discharge 10 C).
- SVOLT 21700 4400 mAh datasheet (4.2 V, 2.75 V, standard charge 0.5 C,
  standard discharge 1 C).
- Evlithium 75 Ah prismatic NCM cell (4.3 V, 2.75 V, standard 0.5 C).
- Evlithium 45 Ah LTO 66160 cell (2.9 V, 1.5 V, standard 1 C, maximum
  continuous 8 C both ways, −50 to 65 °C).
- Leclanché LTO cell datasheet (2.8 V, 1.5 V, 6 C continuous charge,
  −20 to 55 °C) and OSN Power LTO 40 Ah (2.8 V).
- Power Forum thread 29509 ("wrong settings on JK BMS"): the JK LTO preset
  (OVP 2.70 V, SOC 100 % 2.68 V, OVPR 2.65 V, UVPR 1.85 V, SOC 0 % 1.82 V,
  UVP 1.80 V).
