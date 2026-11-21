# Mode editor (text levels for the inverter mode) — design

- **Date:** 2026-11-21. Detailed 2026-11-21.
- **Status:** approved by the owner (2026-11-21). Owner answers and picks
  are marked "owner"; the calls I made are marked "my call".
- **Design:** `docs/design/mode-editor.png` (README section "Mode editor";
  canvas row "Mode editor · v8"). The picture shows the layout; this spec
  decides the behaviour.
- **Set:** `0014`, epic `epic-widget-improvements`. Code on branch
  `0014-mode-editor`, from the epic. Build order: after 0013 (merged).
- **Builds on:** 0012 (the mode table, `ModeAlerts`, the icon slots, the
  Inverter tab), 0010 and 0011 (the level system: per-value lists, the
  Default / Edit pattern, the editor parts, bells, one notification channel
  per type) and spec 0001 (§12 strings, §14 stored data). On one topic the
  newest spec wins.

## 1. Intent (owner, 2026-11-21)

- Different inverters send different mode texts. Any user, not only a
  colleague, must be able to say which texts the mode entity sends and map
  them to the modes the app shows: "editable text for the incoming value
  and our internal level item" (owner).
- The defaults stay as today: the ESP32 texts of 0012 §2.1.
- The user can rename the shown mode names, change their colour and icon,
  and set each mode's bell and type, close to the level editor.
- The user can drop modes their inverter does not have, and add new ones.
- The owner asked whether the mechanism can serve other text entities
  later. Answer: yes, it is built as "text levels", the text counterpart
  of the 0010 levels (§3.1).

## 2. Today (after 0012)

- `InverterMode` is a fixed table in code: 7 rows (Power On, Standby,
  Mains, Off-Grid, Bypass, Charging, Fault), matched trimmed and ignoring
  case. Any other text is "other": the strip shows it as Home Assistant
  sends it, grey, with the `MODE_OTHER` icon.
- Each row has a translated word (EN, UK), a colour, a default icon and a
  type (notification channel). The icons are stored per slot
  (`InverterSettings.icons`, `IconSlot.MODE_*`), picked in the Icons
  section.
- `ModeAlerts` compares raw texts: any change of the text posts "Inverter:
  <word>" on the row's channel, when the main Notifications switch is on.
  The first value is only stored (`CacheState.inverterMode`); `unavailable`,
  `unknown` and blank are never a mode. One notification slot (id 150).
- The numeric levels (0010, 0011): each value has a built-in list in code
  (`DefaultLevels`) and an own list (`CustomLevels`: `on`, `levels`); the
  own list counts only when on, not empty and valid
  (`LevelPicker.ownLevels`). The section shows each value's levels as tags
  with "Default" (confirm dialog) and "Edit" (a draft with Save / Cancel).
  Up to 6 levels per value; exactly one middle level, which cannot be
  deleted.

## 3. Design

### 3.1 Text levels (owner picked option B)

A **text level** is to a text value what a level is to a number: a number
finds its level by edge, a text finds its text level by its texts. Same
shape, same rules:

- **`TextValue`** (new enum, append only): the text values that have text
  levels. Only `MODE` now. Storage, matching, validation and the editor
  take a `TextValue`, so a second text entity later adds a key and its
  default list. The alert path stays mode-only (`ModeAlerts`,
  `CacheState.inverterMode`, slot 150); a second text value would reuse its
  item-compare rule (§3.5) (my call). No other text value is built now
  (non-goal).
- **One text level** (`TextLevel`, stored):
  - `id: Int` — stable while editing, like `ColorLevel.id`.
  - `texts: List<String>` — the match texts. Empty only for "other".
  - `other: Boolean = false` — the catch-all item (§3.2).
  - `name: String = ""` — the user's own name, shown as typed.
  - `defaultName` — a key of the translated default word, or `null` (an
    item the user added has none). Stored as a name; an unknown key reads
    as `null`.
  - `type: LevelType` — the channel (Critical, Warning, Normal, Info).
    Default `NORMAL`, like `ColorLevel.type` (0001 §14 exception).
  - `color: LevelColor? = null` — `null` means the type's colour, like
    `ColorLevel.color` (owner: colour editable, defaults as today).
  - `icon: String = ""` — an `InverterIcon` name; blank or unknown reads as
    `HELP`, so a name from a later build cannot break the file (like the
    0012 icon map).
  - `notify: Boolean = false` — the bell.
- **The own list** (`CustomTextLevels`, stored): `on: Boolean = false`,
  `levels: List<TextLevel> = emptyList()`. No margin: texts have no
  hysteresis.
- **Storage:** `Settings.textLevels: Map<String, CustomTextLevels>`, keyed by
  `TextValue` name, next to `colorLevels`. A new field with an empty
  default: no migration, no `schemaVersion` bump (0001 §14).
- **Effective list:** the own list when on, not empty and valid (§3.3),
  else the built-in list for that value. One check serves the strip, the
  alerts, the section and the editor, like `LevelPicker.ownLevels`. A
  broken stored list never reaches the widget.

### 3.2 Matching and the shown word

- **Match:** the text is trimmed and compared ignoring case with every
  item's texts. A text in a list → that item. Any other text → the "other"
  item. `unavailable`, `unknown`, blank (`ModeText.isNoValue`) → no item.
  Texts are unique across one list (§3.3), so order does not matter.
- **Shown word**, one rule for the strip, the notification and the editor:
  1. the item's own `name` when not blank, as typed;
  2. else its translated default word (`defaultName`);
  3. else the Home Assistant text as it is (the "other" item always; an
     added item with a blank name too — my call, so a name is never
     required).
- **No value:** the word is "—", the colour grey, the icon the "other"
  item's icon (as today).
- **No live text** in the section and the editor rows: the "other" row
  shows its label "other (HA text)", and an added item with a blank name
  shows its first text (my call). In the "Now" line an unmapped text shows
  the "other" label after the arrow, not the same text again (my call).
- **"Other"** (owner, Q7): bell, type, colour and icon are editable. Its
  name is not: it always shows the Home Assistant text, which tells the
  user which text to map. It has no texts and cannot be deleted. It is
  always the last row.

### 3.3 Validation (the own list)

| Rule | Error (field `TEXT_LEVEL_TEXTS` unless noted) |
|---|---|
| At most 12 items, "other" included (my call; defaults are 8) | `TOO_MANY`, no index |
| Exactly one `other` item, with no texts | broken list → not valid (cannot happen from the editor) |
| Every other item has at least one text | `REQUIRED`, at the row |
| A text is not blank, `unavailable` or `unknown` | `INVALID`, at the row |
| A text is in one item only, ignoring case (also twice in one item) | `DUPLICATE`, at the row that repeats it |

Validation runs on trimmed texts. An error keeps the editor open, like the
level editor (`onSave` returns `false`).

### 3.4 Built-in list for `MODE` (today's 0012 table)

| id | Texts | Default word EN / UK | Colour | Icon | Type | Bell |
|---|---|---|---|---|---|---|
| 1 | `Power On` | power on / запуск | grey | `POWER_SETTINGS_NEW` | Info | on |
| 2 | `Standby` | standby / пауза | teal | `MODE_STANDBY` | Warning | on |
| 3 | `Mains` | main / мережа | blue | `TOWER` | Info | on |
| 4 | `Off-Grid` | battery / батарея | green | `BATTERY_CHARGING_FULL` | Warning | on |
| 5 | `Bypass` | bypass / байпас | violet | `ALT_ROUTE` | Info | on |
| 6 | `Charging` | charging / заряд | amber | `CHARGER` | Info | on |
| 7 | `Fault` | fault / аварія | red | `ERROR` | Critical | on |
| 8 | (other) | the HA text | grey | `HELP` | Info | on |

- The words are today's strings (`mode_word_*`). Every colour is explicit
  in the list (Mains and Fault too), because most mode colours are not
  their type's colour. Changing a built-in item's type does not change its
  colour (my call).
- **Bells all on** (owner, Q2): with no stored list the app behaves exactly
  as today.
- `InverterMode` is replaced by this list. `ModeText` stays.

### 3.5 Mode alert (owner, Q1: on entering only)

`ModeAlerts.evaluate` keeps its inputs and the stored raw text
(`CacheState.inverterMode`, unchanged), and compares **items**, not texts:

- No value → keep the last text, no alert (as today).
- No last text → store the new one, no alert (as today).
- Match both the last and the new text with the **current** effective list:
  - different items → alert for the new item when its bell is on;
  - both "other" and the texts differ (ignoring case) → alert when the
    "other" bell is on;
  - else no alert (for example `Line` → `Utility`, both in "main").
- The new text is always stored. A change is tracked when the main switch
  is off, so switching it on does not replay it (as today).
- Re-matching the stored text with the current list means a list edit
  between two syncs is not itself a change: once `Line` is mapped to
  "main", the next `Line` is no alert.
- **Notification:** title "Inverter: <shown word>", body "Measured at
  hh:mm", channel and small icon by the item's type, accent = the item's
  shown colour. Same slot (150), opens the Inverter tab, follows the main
  Notifications switch (0012 §7). No notification on leaving a mode: the
  next mode's bell covers it.

### 3.6 Icons (owner picked option 1)

- Each item carries its icon; it is picked inside the open item in the
  editor, with today's icon grid (all 45 `InverterIcon`s).
- `IconSlot` keeps only `GRID_VOLTAGE` and `OUTPUT_VOLTAGE`; the `MODE_*`
  slots are removed. The Icons section shows those two rows.
- Stored `MODE_*` keys in `InverterSettings.icons` are ignored. 0012 is not
  released, so no migration (0001 §14); the owner's own mode icon picks on
  the phone reset to the defaults once and are picked again.

### 3.7 Modes section (Inverter tab)

Placed between Icons and "Levels and alerts" (my call). Same pattern as one
value of "Levels and alerts". Layout: `docs/design/mode-editor.png`
(owner picked, 2026-11-21).

- **Card:** title "Modes" / "Режими"; summary "Default" / "Custom" (my
  call, like the levels summary). Folded by default.
- **View:** one row per item, in list order, "other" last: icon and shown
  word in the item's colour, its texts, a bell mark when on, the type
  word. Buttons "Default" (confirm dialog; drops the own list, like the
  levels' `useDefaults`) and "Edit".
- **Editor** (a draft with Save / Cancel, like the level editor; my call,
  shown in the sketch the owner saw):
  - Closed item row: icon, shown word, bell toggle, type menu (reuse
    `BellToggle` and `TypeMenu`, made `internal`).
  - Open item (one open at a time, tap the row): icon button → icon grid;
    "Name" field (placeholder: the default word; hidden for "other");
    "Home Assistant texts" as chips (owner, Q11): each text is one chip
    with ✕, then an "Add text" field with a **+** button (Enter does the
    same). + adds the trimmed text; a blank field does nothing; `unavailable`,
    `unknown` or a text already in the list shows its error under the field
    and is not added (my call). A text may contain commas. Save first adds
    a text still typed in the field (my call, so it is not lost). Hidden for
    "other"; colour chips (reuse
    `ColorChips` with its colour list as a parameter; the type's colour
    carries the dot as in 0010). Modes get their own eight chips, one row:
    red, orange, amber, green, teal, blue, violet, grey, so teal replaces
    "plain" (owner, Q10). The levels keep their eight (0010 §3.4);
    "✕ Delete" (not for "other").
  - "+ Add mode" (hidden at 12 items): a new item above "other" with no
    texts, no name, type Info, no colour, icon `HELP`, bell off (owner,
    2026-11-21). Its id is above every id in the draft and
    the stored list (`withNewLevel` rule).
  - The first Edit with no own list opens a copy of the built-in list.
  - Errors show under the field of their row. Only one item is open, so
    a failed Save opens the first row with an error, and a closed row
    with an error shows an error mark (my call).
- **"Now" line (owner, Q8):** `Now: "Off-Grid" → battery` under the rows,
  from the cached mode text (`inverterReading.mode`). In the view it
  matches the saved list; in the editor it matches the draft, so the
  effect shows before Save (my call). Hidden when there is no mode text.

```
Modes                                       Custom ⌄
 🗼  main      Mains, Line           🔔 Info
 🔋  battery   Off-Grid               🔔 Warning
 …
 ?   other (HA text)                  🔔 Info
 Now: "Line" → main
                              [Default]  [Edit]

Editor:
 🗼  main                     🔔  [Info ▾]
 ┌──────────────────────────────────────────┐
 │ [🗼 ▾]  Name [          ]  blank = main   │
 │ Home Assistant texts  [Mains ✕] [Line ✕] │
 │ [ Add text             ]  (+)            │
 │ Colour  ● ● ● ● ● ● ● ●                  │
 │                               ✕ Delete   │
 └──────────────────────────────────────────┘
 ?   other (HA text)          🔔  [Info ▾]
 + Add mode
 Now: "Line" → main
                              [Cancel] [Save]
```

### 3.8 Strings

New EN + UK strings in the same commit (0001 §12): the section title and
summaries, "Name", "Home Assistant texts", the "Add text" field with a
hint "Exactly as Home Assistant shows it", TalkBack labels of the + button
and of a chip's ✕ ("Remove Line"), "Add mode", "Delete", the "Now" line (`Now: “%1$s” → %2$s`), the
"other" row label, the confirm dialog, the four errors, TalkBack labels of
the icon button and the item row. The default words stay the `mode_word_*`
strings. The builder writes the Ukrainian texts; the owner reviews them.

## 4. Catches

- **A text in two lists** cannot be saved (§3.3), so a text always has one
  item.
- **The owner's phone:** mode icon picks reset once (§3.6). Nothing else
  changes until the owner edits the list.
- **Flapping:** an inverter that switches between two modes at every sync
  notifies at every sync, as today. No re-arm for texts (non-goal).
- **Long names:** the strip fits the word to its box as today
  (`InverterFonts`); a very long name is shrunk, then cut with "…".
- **An item with all its texts removed** is an error, not a hidden item:
  to drop a mode, delete it. "Default" brings every built-in mode back.

## 5. Questions (all decided, 2026-11-21)

1. Notify on entering only, or also on leaving? **Entering only** (owner).
2. Default bells? **All on, as today** (owner).
3. Mode colour editable? **Yes**; defaults as today, from the 0010
   palette (owner).
4. Hide unused modes? **Yes** (owner) → with option B, delete the mode;
   "Default" brings it back.
5. Colleague texts or presets? **No evidence, no presets.** Every user maps
   their own texts (owner).
6. A ninth mode? **Yes, option B**: add and delete items like levels
   (owner picked B over A after Q6 and the reuse question).
7. "Other": **bell, type, colour and icon editable; the name is the HA
   text** (owner).
8. The "Now" line: **yes** (owner).
9. Mode icons: **in the open item; Icons keeps the two voltage slots**
   (owner, option 1 over a dynamic Icons section).
10. Teal in the mode colour chips (found by the builder check: the 0010
    picker leaves teal out, so Standby's colour had no chip)? **Yes: modes
    get eight chips with teal instead of "plain"** (owner, option b over
    the levels' eight or nine chips that wrap).
11. How to enter the texts? **As chips with ✕ and an "Add text" field with
    +** (owner, 2b over one comma-separated field). A new mode starts with
    the bell off (owner).
12. Mockup: design canvas, row "Mode editor · v8" (board
    `v8-mode-editor`). Frames 1, 2b and 3 are the picked design; 2a was
    not picked.

## 6. Approaches

| Option | Trade-off | Decision |
|---|---|---|
| A. Fixed 7 + other, every field editable | Cheaper (~2–3 h, a guess); mode-only code; moving to B after a release needs a migration | not picked |
| **B. Text levels: a list per text value, add / delete** | ~3–4.5 h (a guess); same rules as levels; a second text entity is a key + defaults | **picked (owner)** |
| C. A Home Assistant template sensor maps the texts | 0 app work; every user writes a template | not picked |
| D. Presets per inverter model | Needs real texts from real inverters | not picked (no evidence) |

Icons: option 1 (in the item) over option 2 (a dynamic Icons section, where
two sections edit one item and an unsaved draft can overwrite a new icon).

## 7. Non-goals

- No other text entity now; only `TextValue.MODE`.
- No notification on leaving a mode; no re-arm margin for texts.
- No presets per inverter model.
- No change to the strip layout or the mode style.
- No migration (0012 is not released).

## 8. Left to the builder

- Class and file names beyond those named here, and where the default-name
  keys live (a new enum, or appended to `LevelName`).
- The layout details inside the sketch (§3.7), within the level editor's
  look.
- Splitting the editor into files (ColorsSection and LevelEditor are long).

## 9. Testing and done

- Unit tests: matching (trim, case, other, no value); the shown word rule;
  validation (each row of §3.3); the built-in list equals the 0012 table;
  the effective list (off, empty, invalid → built-in); `ModeAlerts` by
  item (two texts of one item → no alert; other → other alert; bell off →
  no alert but stored; switch off → tracked; list edit between syncs → no
  alert); the strip model with an own list (word, colour, icon);
  settings JSON with old `MODE_*` icon keys still loads.
- Editor: draft helpers (add and delete a mode, add and remove a text) as
  unit tests; the
  mode chips offer teal and no "plain", the level chips are unchanged; one
  Compose test for edit → save → the strip word changes, if the existing
  UI tests make it cheap.
- Done gate (`commands.md`) green. Owner check on the phone: rename a mode,
  map a second text, delete and restore a mode, a bell off.
- `docs/ai/agents/architecture.md` updated in the same branch.
