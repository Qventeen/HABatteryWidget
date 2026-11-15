# Widget layouts v5 — design

- **Date:** 2026-11-15
- **Status:** approved by the owner (2026-11-15).
- **Set:** `0005`. Code on branch `0005-widget-layouts`, from `develop` after
  branch `0004-widget-design` (design v5 and these specs) is merged into
  it (the owner approves that merge).
- **Builds on:** spec 0003 (widget v4), spec 0004 (temperatures) and widget
  design **v5** (`docs/design/README.md` and the four PNGs; branch
  `0004-widget-design`, commits `7b4e53f`, `01263b7`).

## 1. Intent

Today the widget has one layout, scaled by
`s = min(1, width / 360, (height − 44) / 136)` (spec 0003, approach C1).
On the owner's phone (ColorOS, 5-column grid) it looks too small at
2 rows and empty at 3 rows. Design v5 has four layouts made for the
owner's real cell sizes: 4×2, 5×2, 4×3, 5×3. Build them, let Android show
the one that fits, and make the font settings work across layouts.

## 2. Goals, non-goals, anti-goals

**Goals**

- Four layouts, exactly as `docs/design/README.md` gives them: sizes, gaps,
  font sizes, colours, states.
- Android shows the layout that fits the widget's real size; a bigger
  widget stretches the largest layout that fits; the smallest size is 4×2.
- Sync error: a red ⚠ icon only, in place of the update time.
- Font settings as a percent: 50 % = each layout's design size, 100 % =
  twice it; every text fits its box at any setting (model and rule; the
  input fields come in spec 0007).
- The widget picker preview shows v5.

**Non-goals**

- The preview in the app (0006), the settings UI rework (0007), forcing
  light or dark (0008).
- Other layouts (1 row, 3 or 6 columns), per-widget settings.
- Rounded pills on Android 8–11 (0003 approach D1 stays).
- The 0003 review minors, except where this work replaces the code
  (preview layout fixed at 1×, scaling).

**Anti-goals** (a failure even if it works)

- The launcher shows a layout made for another size than the widget has
  (what happened on ColorOS in the 0004 size trials with `SizeMode.Exact`).
- Text that does not fit at any allowed font setting with the worst-case
  values ("↓ 125.5 A", "↑ 3.98 kW", SOC 100, "314.0 Ah", "−20 °C",
  "Δ 100 mV"), in English and in Ukrainian ("314,0 А·год" is longer).
- Elements move between states that have data.
- Network calls in `provideGlance` (0001 rule).

## 3. Owner decisions (2026-11-15)

1. Design v5, four layouts (approved: "very good", PNGs committed).
2. 5×2 uses layout B: the ring over the full card height, the top row
   beside it. 4×2 has the top row over the full width (5×2's layout does
   not fit 4×2's width).
3. Other sizes: the largest designed layout that fits, stretched; the
   minimum size is 4×2 ("all picks").
4. Sync error: a red icon, no text; the reason is in the icon's content
   description and in the app.
5. Cell pills: a small fixed gap; pills grow to fill (never spread with big
   gaps).
6. Font sizes: one percent value per group for all layouts; number fields
   (spec 0007). **50 % = the design size, 100 % = twice the design size**;
   text can be made bigger and smaller; Reset returns to 50 % (owner,
   2026-11-15: "our design defaults must be 50 % from maximum for all types
   of texts").
7. Temperatures: spec 0004.

## 4. Approaches (Claude's calls)

**A. Choosing the layout**

- **A1 (chosen):** `SizeMode.Responsive` with four sizes (buckets, 5.1).
  Glance draws each layout once; on Android 12+ the home screen picks the
  largest bucket that fits the widget's real size; on Android 8–11 Glance
  picks for portrait and landscape from the size options. This is the
  platform's way to have different layouts per size. (Likely it also fixes
  the ColorOS problem, because the home screen picks by the real size;
  checked first on the owner's phone, 5.0. Glance 1.2.0 sources, checked
  by the reviewer: on Android 12+ every bucket is drawn with `LocalSize` =
  the bucket; on 8–11 Glance picks the closest bucket that fits each
  orientation, else the smallest.)
- A2: `SizeMode.Exact` and own size thresholds: one drawing per reported
  size; ColorOS showed the drawing for a wrong size (0004 trials).
- A3: keep one scaled layout: the look the owner rejected.

**B. Filling a widget bigger than its bucket**

- **B1 (chosen):** fixed parts use the design sizes (padding, top row,
  buttons, ring, gaps, fonts); columns and pills that "fill" use weights,
  so the layout stretches. With `Responsive`, `LocalSize` is the bucket
  size, not the real size, so nothing is computed from it except the
  layout choice.
- B2: scale to the real size: `Responsive` does not give the real size.

**C. Font sizes**

The owner's scale (decision 6): 50 % = design size, 100 % = 2×. But the
design sizes are already the largest that fit the worst-case values:
measured on the v5 canvas boards (Chromium render, each group raised in
5 % steps), the main values overflow at +5 % in 5×2 and 5×3, and the ring
text touches the ring at the design size in 4×2 and 4×3. So above 50 %
something must stop a long value from overflowing.

- **C1 (chosen): fit each text to its box.** Requested size = the
  layout's design size × percent / 50 × system font scale. Shown size =
  the requested size, or smaller if the text **as it is now** (e.g.
  "↓ 10.0 A") would not fit its box at the bucket size. Short values grow
  with the setting; a long value ("↓ 125.5 A") shrinks back until it fits.
  At 50 % every worst-case value fits (measured), so nothing shrinks.
  Measured in the app process with `Paint` (the system default typeface,
  the weight the widget draws), before `provideGlance` draws.
- C2: fit to fixed widest samples ("↓ 125.5 A" for every current): the
  size never changes with the value, but the main values could not grow at
  all (their widest sample already fills the pill) — the "slider does
  nothing" problem of 0003 again.
- C3: no fit, allow overflow or "…": long values get cut (anti-goal).
- C4: cap at the design size (only smaller): the owner wants bigger text.

**D. Ring:** 0003 approach B1 (tinted arc bitmap on a tinted track) stays;
the size comes from the layout.

## 5. Design

### 5.0 Device test first (throwaway)

Before any layout work: an APK with four dummy layouts in
`SizeMode.Responsive`, each showing only its name ("4×2" … "5×3") on a
different colour, with the buckets of 5.1. The owner places 4×2, 5×2,
4×3 and 5×3 on the phone and resizes between them; each must show its
own name. If ColorOS shows a wrong name, the builder stops and brings the
owner the evidence and options before going on; nothing else is built on
a guess.

### 5.1 Widget info (`res/xml/battery_widget_info.xml`) and buckets

| Bucket | Design card (owner's grid) | Bucket size (dp) |
|---|---|---|
| 4×2 | 267 × 132 | 261 × 126 |
| 5×2 | 340 × 132 | 334 × 126 |
| 4×3 | 267 × 205 | 261 × 199 |
| 5×3 | 340 × 205 | 334 × 199 |

- Bucket = design size − 6 dp each way, so the owner's widgets always get
  their layout and phones with slightly smaller cells too.
- Each layout must fit from its bucket size up. Between the bucket and the
  design size, the ring may shrink to its row's height (the bitmap scales to
  fit its box) and the filling columns shrink by weight; fonts do not
  change. The builder runs the fit check (6) at the bucket sizes; where
  worst-case text does not fit, that layout's design font for that group
  goes down by 0.5 sp, and `docs/design/README.md` is updated in the same
  commit.
- `minWidth` / `minResizeWidth` 261dp, `minHeight` / `minResizeHeight`
  126dp (the 4×2 bucket). Real launchers give about 130–142 dp for 2 rows
  (Pixel 8 API 35; the owner's phone 132).
- No `targetCellWidth` / `targetCellHeight` (as in 0003).
- `maxResizeWidth` / `maxResizeHeight` 460dp (as today).

### 5.2 Code shape (`widget/`)

- One composable per layout (`Layout4x2`, `Layout5x2`, `Layout4x3`,
  `Layout5x3`), built from shared parts: top row, ring, pack pills, cell
  pills (column or 2 × 2 grid), empty state.
- One `LayoutSpec` value per layout holds the README numbers (card
  padding, top-row height, button touch / circle / icon, ring size and
  stroke, gaps, cell column width, design font sizes per group). Parts take
  the spec; no size is written twice.
- `WidgetContent` picks the layout from `LocalSize.current` (the bucket).
- `WidgetScale` and the `s` factor are removed.

### 5.3 Parts

- **Top row:** as the README per layout. Time left (hourglass + text),
  balancing icon, then the update time — or, with a sync error, a red ⚠
  icon the size of the balancing icon, content description
  "Sync error: <`Formatters.error(error)`>" — then the auto-refresh toggle
  and ↻. The two button touch areas touch each other. 5×2: the top row
  sits beside the ring.
- **Ring:** README size and stroke; "SOC" label (small group), the number
  (SOC group), the kWh / Ah line (under-SOC group).
- **Pack pills:** voltage and current or power, stacked, fill their column;
  the arrow shows the direction (as today).
- **Cell pills:** average cell, delta, battery temperature (spec 0004),
  MOSFET. Column layouts: fixed gap, pills share the height by weight.
  4×3: a 2 × 2 grid (row 1 average cell + delta, row 2 battery + MOSFET;
  with no battery sensor MOSFET takes row 2 alone).
- **Empty state** (setup needed / no data): centered icon, title, reason
  (no data only), "Open app" button; at 2-row sizes the smaller sizes of
  the 5×2 board (icon 22, title 14 sp, button 34 dp).
- Rounded corners and pills from Android 12; square before (0003 D1).
- Content descriptions on every icon-only element, as in 0003, plus the
  error reason.

### 5.4 Font settings (`domain/model/Settings.kt`)

- `FontSizes` (sp) is replaced by `FontPercents(soc, underSoc, main, cell,
  small: Int = 50)`, each 25–100 (25 % = half the design size, 50 % = the
  design, 100 % = 2×); stored values outside the range are clamped when
  read.
- Stored data: a new field name (`fontPercents`); the old `fontSizes` is
  ignored when loading (`ignoreUnknownKeys` is on), so everyone starts at
  50 %. `schemaVersion` stays 1; `StoredJsonV1Test` pins the new format.
- Until spec 0007 replaces them, the existing five sliders show percent
  (25–100, step 5).
- The groups map to the README "Font sizes" rows; the "SOC" label in the
  ring belongs to the small group (as in 0003).

### 5.5 Fitting text (approach C1)

- A pure function per text: `fit(text, requestedPx, typeface, box) →
  shownPx`, the largest size ≤ requested at which the text's width fits the
  box's inner width and its line height fits the box's inner height. Boxes
  come from the layout's `LayoutSpec` at the bucket size (a real widget is
  never smaller than its bucket, so the result is safe when it stretches).
- Pills, the top-row texts and the empty-state texts each fit their own
  box. Pills with an icon fit the text in the box minus the icon and its
  space.
- **Ring:** the three lines ("SOC", number, kWh / Ah) are fitted together:
  every line's box must stay inside the inner circle (radius − stroke / 2);
  if they do not, the three sizes shrink by one common factor until they
  do. At 50 % the measured design already fits (4×2 and 4×3 touch the ring
  by under 1 dp: accepted, as on the canvas).
- Ukrainian texts are longer ("8,32 кВт·год", "314,0 А·год"). The same rule
  shrinks them where they do not fit, also at 50 %; the English worst case
  is the one guaranteed to show at the design size.
- Sizes reach the widget so that the home screen does not apply the
  system font scale a second time (the builder checks how 0003's
  `WidgetLook` does this and keeps that).
- Accepted: at a large setting a value can change size when its text
  changes length (e.g. 9.9 A → 10.0 A). Nothing moves; only that text's
  size changes.

### 5.6 Widget picker preview

`previewLayout` is redrawn as the v5 4×2 normal state with static sample
values (as in the PNG). Generated previews (Android 15) are a non-goal.

### 5.7 App overview card until spec 0006

The Compose card keeps its v4 look (with spec 0004's pills and the error
icon from the shared texts) and its own width scaling: the scaling code
moves from `widget/WidgetScale` into the card's code. Its five font sizes
are the v4 sizes (40 / 12 / 22 / 14 / 10.5 sp) × percent / 50, without
fitting. No other work goes into it: spec 0006 replaces it with the real
widget.

### 5.8 Removed

From the widget: `WidgetScale` and the `s` factor (the app card keeps a
copy until 0006, 5.7), `SizeMode.Exact`, the error text in the top row,
`FontSizes` and its sp ranges, every string no longer used (both
languages).

## 6. Tests

- **Domain:** `FontPercents` range and clamping (24, 25, 50, 100, 101);
  the requested-size rule (25 / 50 / 100 %; system font scale 0.85 / 1.0 /
  1.3).
- **Fit (unit tests with a fake text measurer):** a short value grows to
  the requested size; a long value shrinks to its box; height limits a
  tall request; the ring lines shrink together; at 50 % with the worst-case
  values nothing shrinks.
- **Widget (Glance unit tests):** each bucket draws its layout; states:
  normal, refreshing, error (icon and content description, no text),
  missing value, empty (setup, no data); 3 and 4 cell pills; kWh / Ah and
  current / power texts.
- **Fit check (emulator, before any APK goes to the owner):** each layout
  at its bucket size and at the owner's size, worst-case and typical values,
  every group at 25 %, 50 % and 100 %, English and Ukrainian, light and
  dark; screenshots next to the v5 PNGs. At 50 % in English the widget must
  match the PNGs.
- **Device test (5.0):** first, with the dummy layouts.
- **Manual, owner's phone (done gate, before the merge):** place 4×2,
  5×2, 4×3, 5×3; each shows its own layout; resize between them; a 5×4
  shows the stretched 5×3 layout; light and dark.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease`;
  instrumented tests on `Pixel_8_API_35`.

## 7. Left to the builder

File layout inside `widget/`, the `LayoutSpec` shape, the exact Glance
structure, the 0.5 sp adjustments from the fit check, and the wording.
