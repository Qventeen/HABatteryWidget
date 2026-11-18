# Value colours — concept

- **Date:** 2026-11-18
- **Status:** concept. The owner accepted the shape in §3 with the additions
  of 2026-11-18 (default set, a switch per value, reset). Not approved as a
  spec yet: the open questions in §5 need a detailing session before a plan
  is written.
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
The SOC indicator may follow the levels too. The same settings must work for
the inverter values of 0012 (for example, levels by voltage).

Additions (owner, 2026-11-18, after reading this concept):

- The app has a **minimal default set** of levels and colours (the levels
  that exist today). They work by default.
- **A switch per value** turns customisation on or off. With customisation
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

## 3. Proposed shape (the owner accepted it as the base, 2026-11-18)

- **A level list per value:** ordered bands "from X → colour". The first band
  has no lower limit. Any number of bands, with a cap (for example 6) to keep
  the screen usable.
- **Values:** SOC, total voltage, main value (current or power), average
  cell, cell delta, battery temperature, MOSFET temperature; later the
  inverter values (0012).
- **Colours from a fixed palette**, not a free colour picker: about 8 named
  colours (for example red, orange, amber, green, teal, blue, violet, grey).
  Each has a light-theme and a dark-theme shade, checked for contrast on the
  card in both themes.
- **Built-in default set:** each value has default bands in code; they are
  not stored in settings.
  - SOC (owner, 2026-11-18): red below 20 %, amber 20–30 %, green 30–99 %,
    blue at 100 %. This changes today's look a little (today: red below
    15 %, amber below 25 %, no blue).
  - The other values: today's limits and colours (§2) are the starting
    proposal; the owner sets their default levels in the detailing session
    (owner, 2026-11-18).
  - A value with no levels today (total voltage, average cell, the value
    under SOC) defaults to one plain band (no colour), and the main value
    keeps its colour by direction (proposal).
- **"Custom colours" switch per value**, off by default:
  - Off: the value uses the built-in default bands.
  - First time on: the value's own bands are created as a copy of the
    defaults, ready to edit; the owner adds, removes and edits bands.
  - Off again: the defaults apply; the own bands stay stored, so the next
    "on" brings them back (proposal; see §5, question 9).
- **"Reset to default" per value:** replaces the own bands with a fresh copy
  of the defaults, like "Reset sizes" in the Widget section.
- Stored values (the switch, the own bands) default to `false` and an empty
  list, so the "defaults are empty" rule (0001 §14) holds.
- **Domain:** a pure function picks the band for a value; `WidgetModel` takes
  the bands from settings instead of `WidgetLevels`. The colour follows the
  shown (rounded) number, as temperatures do today.
- **App:** a "Colours" place (a section or a sub-screen) lists the values;
  a tap opens that value's bands; add, remove and edit a band (number and
  colour chip). The app preview (0006) shows the result live.
- **Stored** in `Settings` as a new field with a default; no migration is
  needed for a new field (0001 §14).

## 4. Catches

- A colour must read well on the light and the dark card. A free picker
  would allow unreadable colours, so the proposal is a fixed palette.
- Today a level also colours the pill background (okBg, warnBg, badBg), not
  only the text. The owner asked for text colour; decide what happens to the
  backgrounds.
- A value with a unit choice (current A or power W; kWh or Ah) needs one band
  list per unit.
- A missing value (sensor not set or unavailable) draws in the plain colour.
- More settings make the app screen longer; a sub-screen may be needed.

## 5. Open questions (for the detailing session)

1. Text colour only, or also the pill background as today?
2. SOC: colour the ring, the number, or both?
3. Main value: keep the colour by direction (charge or discharge), or bands
   by value (signed current)?
4. Which values get bands in the first version?
5. The palette: which colours and how many?
6. Band edges: is "from X" inclusive? Steps for decimals (0.1 V for the pack,
   0.001 V for a cell)? Owner example (2026-11-18): falling from 21 % to
   20 % should already be red, rising from 19 % to 20 % amber. So is 20 %
   red or amber, or does it depend on the direction?
7. Where in the app: a new section, inside the Widget section, or a separate
   screen?
8. ~~Should a band also carry a notification?~~ Decided (owner,
   2026-11-18): yes, added by 0011.
9. Turning the switch off: keep the own bands for the next "on" (proposal),
   or drop them?
10. Does "Reset to default" also turn the switch off, or only refill the
    bands? Does it ask for confirmation?
11. One switch per value only, or also one switch for all values at once?

## 6. Non-goals

- No new widget parts and no layout changes (owner: text colour only).
- No free colour picker in the first version (proposal).
- One colour set for the whole app, not one per placed widget (proposal).
