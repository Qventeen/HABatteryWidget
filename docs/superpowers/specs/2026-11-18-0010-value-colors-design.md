# Value colours — concept

- **Date:** 2026-11-18
- **Status:** concept. Not approved. It needs a detailing session before a
  plan is written.
- **Set:** `0010`, epic `epic-widget-improvements`. Code later on branch
  `0010-value-colors`, from the epic.
- **Builds on:** spec 0003 (widget v4 colours), 0005 (layouts v5), 0006 (app
  preview), 0008 (theme). On one topic the newest spec wins.
- **Used by:** 0011 (notification rules, if they share the levels) and 0012
  (colours of the inverter values).

## 1. Intent (owner, 2026-11-18)

The colour of a widget value should depend on the value itself. In the app
settings the owner sets how many levels a value has, where each level starts
and which colour it has. The widget then draws the value in the colour of
its level. Only the text colour changes; the widget parts stay as they are.
The SOC indicator may follow the levels too. The same settings must work for
the inverter values of 0012 (for example, levels by voltage).

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

## 3. Proposed shape (a proposal, not decided)

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
- **Defaults = today's limits and colours**, so the widget looks the same
  until the owner changes something. Like the display choices, this is an
  exception to "defaults are empty" (0001 §14). A "reset to default" per
  value.
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
   0.001 V for a cell)?
7. Where in the app: a new section, inside the Widget section, or a separate
   screen?
8. Should a band also carry a notification? See 0011, question 1.

## 6. Non-goals

- No new widget parts and no layout changes (owner: text colour only).
- No free colour picker in the first version (proposal).
- One colour set for the whole app, not one per placed widget (proposal).
