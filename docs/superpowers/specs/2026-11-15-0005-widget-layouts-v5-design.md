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

## 8. Build notes (2026-11-15)

### 5.0 device test: passed

- Phone: model 2602DPT53G, Android 16 (API 36), 390 dp wide, density
  520. The launcher is `com.miui.home` (Xiaomi **HyperOS**), not ColorOS
  as written above.
- A throwaway APK with the four buckets of 5.1 in `SizeMode.Responsive`:
  each size showed its own bucket. 4×2: real size 267 × 132 dp (options
  267.3 × 131.6); 5×2: about 339 × 131 dp (from the screenshot); 4×3 and
  5×3 their own; 5×4 the stretched 5×3.
- The widget was drawn once and never redrawn while it was resized (its
  size texts stayed stale), and the shown layout always followed the real
  size: the launcher picks the bucket. Approach A1 holds on HyperOS.
- On the `Pixel_8_API_35` emulator the same APK picked the right bucket
  too, also after a rotation without a redraw.

### Builder calls (§7)

- Files in `widget/`: `LayoutSpec` (the README numbers of each layout,
  its bucket, `forSize` with the platform's best-fit rule), `TextFit`
  (pure fit rules), `PaintTextMeasurer`, `WidgetFonts` (texts and shown
  sizes of one drawing), `WidgetLayouts` (`Layout4x2` … `Layout5x3`),
  `WidgetParts` (top row, ring, pack pills, cell column and grid),
  `WidgetContent` (root, empty state). `WidgetTopRow`, `WidgetBody`,
  `ring_track.xml` are gone; `WidgetScale` moved to the card
  (`ui/sections/CardScale`).
- Measuring uses Glance's own text appearance styles
  (`TextAppearanceSpan.updateMeasureState`), so the fit uses the font and
  weight the home screen draws.
- The top row's time left and update time share the space between the
  balancing icon and the buttons and get one size, so they never
  overlap.
- A text's height check is the font's top (above the baseline) plus the
  ink below the baseline, not the line height. Android draws a one-line
  text view from the font's top; in a pill lower than the whole line the
  view is cut to the pill and keeps its top, so the glyphs move down. With
  the line height, large values and the Ukrainian decimal comma
  ("3,312 В") were cut at the bottom. In such a pill the text sits a bit
  low (100 %, 2-row layouts).
- Ring: track and arc are tinted alpha bitmaps at the layout's size and
  stroke, in a box as wide as the ring and as high as its row
  (`ContentScale.Fit`). The three lines are placed like the canvas CSS
  stack (gap, line heights), with the font padding taken out. Their fit
  checks the ink corners against the inner circle, 1 dp tolerance.
- 2-row layouts: the canvas has 6 dp between the 32 dp buttons; each
  touch area grows 3 dp toward the other, so they touch and the circles
  stay where the PNG has them.
- The empty state's reason shrinks when the column would be taller than
  the card (large system font at 100 %).
- Wording: "Sync error: %1$s" / "Помилка синхронізації: %1$s"; the font
  sliders show "%1$d %".
- Glance keeps at most 10 children in a Row or Column and drops the rest
  without an error (the first build lost ↻). The fit test checks that
  every part reaches the RemoteViews.

### Deviations (owner's call where marked)

1. **The ring text fits the ring at its design size**, not at the bucket
   (5.5 says bucket boxes). At the bucket the ring is 6 dp smaller (4×2
   70, 4×3 80, 5×2 106 dp), and fitting to it made the ring text 3–6 %
   smaller than the PNGs at the owner's sizes (4×2 SOC 26.3 instead of
   28 sp). 5.1 lets the ring shrink between bucket and design size with
   the fonts unchanged, so this follows 5.1. At the bucket − 1 dp the ink
   can overlap the stroke by up to about 3.9 dp; at the design size it
   stays inside the inner circle (1 dp tolerance; `WidgetFitTest` checks
   it).
2. **No design font went down by 0.5 sp** (5.1). At the buckets, only
   the long worst-case main values do not fit at 50 %: at the bucket
   − 1 dp boxes "↑ 3.98 kW" in 4×2 shows at 16.0 instead of 17 sp, in 5×2
   at 20.2 instead of 21, in 5×3 at 21.0 instead of 22. The fit rule shrinks just these values;
   the PNG values show at the design size. Lowering the design fonts
   would make every value smaller than the PNGs and change
   `docs/design/README.md`. So the §6 test "at 50 % with the worst-case
   values nothing shrinks" is replaced by one for the PNG values
   (`fiftyPercent_designValues_showTheDesignSizes`). Owner's call.
3. **The top-row time texts are longer than the design's**, so they
   shrink at 50 % where the row is narrow: "17 hr, 16 min" (the
   formatter of spec 0003; the design shows "17 h 16 min"), "11:15 PM"
   with a 12-hour clock, a date when the last update was not today. In
   5×2 at its bucket, "17 hr, 16 min" + "23:15" show at about 9 sp
   (design 10.5), with a date about 6 sp. A shorter time-left format
   would fix most of it. Owner's call; not changed here.
4. The app card keeps its v4 error text next to the icon (5.7): it is
   where the app shows the full reason (decision 4). The icon's content
   description is the new "Sync error: <reason>".
5. The canvas draws "SOC" with 1 px letter spacing; Glance has none.
6. The picker preview card keeps the 4×2 design size, centred, because a
   launcher may give the preview a taller box (the Pixel launcher gives
   3 × 2 cells, about 266 × 224 dp). Its ring text positions are fixed
   numbers.

### Checks

- Fit check (`WidgetFitTest`, `Pixel_8_API_35`): every layout at its
  bucket and at the owner's sizes (267 × 132, 340 × 132, 267 × 205,
  340 × 205, 340 × 278), typical and worst-case values (also 3 cell
  pills, sync error, no data, setup), every group at 25, 50 and 100 %,
  English and Ukrainian, and at font scale 1.3: every text shows whole
  (one line, wide enough, ink not cut). At 50 % in English the PNG
  values show at the design sizes. Both buttons, every icon and pill
  reach the RemoteViews.
- The drawings were compared with the v5 PNGs side by side (light, dark,
  sync error). They are made by `GlanceAppWidget.compose()` and drawn in
  software, where rounded corners do not show; on the emulator's
  launcher the card and the pills are round, and light and dark follow
  the system.

### Review and owner decisions (2026-11-16)

- Deviations 1 and 2: **accepted** by the owner. `docs/design/README.md`
  now says that long worst-case values shrink a little at the bucket
  boxes ("↑ 3.98 kW" about 16 sp in 4×2).
- Deviation 3, owner: **time left in Android's narrow format**, in the
  widget and the app card. Seen on `Pixel_8_API_35` (API 35): English
  "17h 16m", "45m", "2d 7h"; Ukrainian "17г і 16х", "45х", "2д і 7г".
  Robolectric (SDK 34): English the same; Ukrainian "17 год. і 16х",
  "45х", "2д і 7 год.". With it the 5×2 top row shows at its design
  size ("17h 16m" + "07:13" at 10.5 sp).
- Owner: **an update from an earlier day shows the date only**, in the
  app's language (ICU skeleton "MMMd", "yMMMd" in another year): "Nov 13"
  / "13 лист." on the emulator, "Jan 13" / "13 січ." in Robolectric. The
  date came from `DateUtils` before, in the device's default language.
- Review fixes:
  - A sync error keeps the top-row text size: the update time is still
    fitted when the icon takes its place.
  - Texts are fitted to the boxes at the bucket − 1 dp (Android shows a
    bucket from about 1 dp below it), with 0.5 dp width slack.
  - Line metrics count fallback fonts (`Paint.getFontMetricsInt` per
    text; the call exists from Android 13, so before 13 the main font's
    metrics are used).
  - The app card caps the system font scale at the v4 maximum again.
  - A system font-scale change redraws the widget (`ConfigWatcher`, which
    replaces 0008's `LocaleWatcher` and also watches the language).
  - The picker preview card is the 4×2 bucket (261 × 126 dp) with text
    sizes in dp. Its ring is 70 dp there, so the kWh line is 8.5 dp
    (English) and 6.5 dp (Ukrainian) to stay inside the ring (the design's
    10 dp would leave it by 2.7 dp); `WidgetFitTest` checks both.
  - `WidgetFitTest` also runs just under each bucket and at font scale
    1.3 in Ukrainian, and checks the ring ink at the design sizes.

### Ring text fit (review finding 4 and re-review finding 1, the reviewer's calls)

Each ring line's ink must stay inside the inner circle: its width within
the circle's chord at that line's height (1 dp tolerance). The sizes are
found in two steps:

1. The 50 % result: every line at the smaller of its request and its
   50 % size. A stack taller than the circle shrinks all lines by one
   factor; a line still too wide for its chord then shrinks alone.
2. Raised lines (asking for more than 50 %) grow from their 50 % result
   toward their request, in steps of 1 % of their 50 % size; a line stops
   at the last step where every line still fits. The other lines keep
   their 50 % result.

So no line is ever smaller than at 50 % (checked in every layout, English
and Ukrainian, on the emulator), and a long kWh / Ah line does not make
the SOC number smaller. A raised SOC number grows only while every line
still fits: the stack is centred, so a larger number moves the kWh line
down to a narrower chord. Where the kWh line is already at its chord at
50 % (Ukrainian "4,58 кВт·год"), the number grows only a little.

The first version (review round 1) shrank raised lines to their 50 % size
while any line was too wide, so a raised SOC number in Ukrainian stayed
at its 50 % size; the re-review replaced it with the rule above. The fit
of a single text now searches the largest size that fits, so a larger
request never shows smaller.

Measured on the emulator (4×2 / 4×3 / 5×2 / 5×3), SOC at 100 %, the
others at 50 %: English 29.7 / 37.8 / 62.0 / 52.9 sp, Ukrainian
28.8 / 32.6 / 44.8 / 42.8 sp. 4×2 Ukrainian at 50 %: SOC 28.0 sp (23.2
before finding 4); "4,58 кВт·год" 7.6 sp at 50 % and at all 100 %.

### Known risks (docs only)

- A system font-scale change while the app's process is not running is
  not seen by `ConfigWatcher`; the widget is redrawn only at its next
  update. Glance stores text sizes in sp, so until then the home screen
  applies the new scale to the old sizes and a text can be cut. It is the
  same gap as 0008's language change.
- "Today" is decided when the widget is drawn. After midnight, with auto
  refresh off, the widget keeps showing the last update as a time only
  until it is drawn again.
