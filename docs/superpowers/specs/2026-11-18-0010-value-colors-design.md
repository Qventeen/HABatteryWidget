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
- **What gets coloured (owner, 2026-11-18, round 1).** A level colours the
  text and a soft background of the same colour, as today.

  | Widget part | Levels |
  |---|---|
  | SOC ring | SOC levels; the SOC number stays plain |
  | Voltage pill | voltage levels (default: plain), **plus a direction arrow** like the current pill (new): up while charging, down while discharging, from the same current sign as the main pill |
  | Current/power pill | levels by intensity, **one list for charge and one for discharge**, because a battery has different limits each way: idle grey, low green, higher amber, highest red; the arrow stays. A high level can notify (0011), for example a very high discharge current |
  | Average cell pill | levels (default: plain) |
  | Cell delta, battery temperature, MOSFET pills | levels, as today (the worst battery sensor wins) |
  | Value under SOC (kWh or Ah) | plain (it repeats SOC) |
  | Inverter values | later (0012) |

- **Level marks on temperature pills (owner, 2026-11-18).** Besides the
  text colour, a temperature pill gets a small extra icon by level, next to
  the icon it has today (thermometer for the battery, chip for the MOSFET).
  Only temperatures for now. Proposal: the icon shows the side, the colour
  shows how bad it is.
  - High levels: a flame. Low levels: a snowflake. Middle (normal) levels:
    no extra icon (proposal; §5, question 15).
  - Each temperature band has a mark from a fixed set (none, flame,
    snowflake); custom levels can change it.
  - Defaults. Battery: below 0 °C red ❄, 0–5 °C amber ❄, 5–45 °C none,
    45–55 °C amber 🔥, above 55 °C red 🔥. MOSFET: 65–80 °C amber 🔥, above
    80 °C red 🔥.
  - Icons from Material Symbols (`local_fire_department`, `ac_unit`;
    Apache-2.0, allowed by the repo licence rule).
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
  - Total voltage and average cell have no levels today; they default to
    one plain band (no colour).
  - Current/power: defaults are open (§5, question 13). Note: with levels
    by intensity, a low discharge turns green; today any discharge is amber.
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
- Text plus soft background (decided) means each palette colour needs four
  shades: text and background, for the light and the dark theme.
- The main pill shows current (A) or power (W). Proposal: its levels are in
  amps (BMS limits are in amps) and also colour the pill when it shows watts
  (§5, question 12).
- The voltage arrow changes the approved v5 look a little: the pill gets
  narrower room for the number, so the text fit must count the arrow, as it
  does for the main pill. The pill already supports the arrow (`PackPill`
  takes a `Flow`; the voltage pill passes `Flow.NONE` today).
- A level mark is a second icon in a small temperature pill: in narrow
  layouts (the 4×2 cell column) the number may get smaller. The text fit
  must count the mark.
- Together these change the approved v5 look (blue SOC at 100 %, green low
  discharge, voltage arrow, temperature marks). Proposal: draw them as new
  v6 rows on the design canvas before this spec is final.
- A missing value (sensor not set or unavailable) draws in the plain colour.
- More settings make the app screen longer; a sub-screen may be needed.

## 5. Open questions (for the detailing session)

1. ~~Text colour only, or also the pill background?~~ Decided (owner,
   2026-11-18): text plus soft background, as today.
2. ~~SOC: ring, number, or both?~~ Decided: the ring; the number stays plain.
3. ~~Main value: colour by direction, or bands by value?~~ Decided (owner,
   2026-11-18): bands by intensity, one list per direction; the arrow stays.
4. ~~Which values get bands?~~ Decided: the table in §3.
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
12. Current/power levels in amps for both displays (proposal), or separate
    lists in watts when the pill shows power?
13. Default current levels: from the capacity as C-rate (proposal, a guess
    for LiFePO4: charge green to 0.2 C, amber to 0.5 C, red above;
    discharge green to 0.5 C, amber to 0.8 C, red above), or fixed amps
    from the owner's BMS limits?
14. Idle colour: grey (proposal; blue already means Info / full) or blue?
15. Temperature marks for middle (normal) levels: none (proposal), or an
    icon? Which other marks, if any, besides flame and snowflake?

## 6. Non-goals

- No new widget parts and no layout changes (owner: text colour only),
  except the direction arrow on the voltage pill and the level marks on the
  temperature pills (owner, 2026-11-18).
- No free colour picker in the first version (proposal).
- One colour set for the whole app, not one per placed widget (proposal).
