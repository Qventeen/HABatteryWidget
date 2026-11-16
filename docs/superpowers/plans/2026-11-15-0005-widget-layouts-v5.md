# Widget layouts v5 (0005) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Four widget layouts (4×2, 5×2, 4×3, 5×3) from design v5, picked by the home screen through `SizeMode.Responsive`, with font settings as percents (50 % = design) and every text fitted to its box.

**Architecture:** One `LayoutSpec` per layout holds the README numbers; the composables (`Layout4x2` … `Layout5x3`) are built from shared parts that take the spec. `WidgetContent` picks the spec from `LocalSize` (the bucket). Before drawing, `WidgetFonts` turns `FontPercents` × design size × system font scale into the requested size of each text and fits it to its box (boxes from the spec at the bucket size) with a `TextMeasurer`; the Android measurer uses the same text appearance Glance draws with.

**Tech Stack:** Kotlin, Jetpack Glance 1.2.0 (`SizeMode.Responsive`), Compose (app card), Robolectric + glance-appwidget-testing (unit), instrumented tests on `Pixel_8_API_35`.

**Spec:** `docs/superpowers/specs/2026-11-15-0005-widget-layouts-v5-design.md` (section numbers below refer to it). Design: `docs/design/README.md` and the four v5 PNGs.

## Global Constraints

- JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
- Every task ends with `./gradlew :app:testDebugUnitTest` green; androidTest compiles where touched.
- `domain/` has no `android.*` import. No new dependencies.
- Every visible text in `values/strings.xml` and `values-uk/strings.xml`, same commit; no text in Kotlin (`NoHardcodedTextTest`).
- Colours only from `WidgetColors`; layout code never sees `ThemeChoice` (0008). Keep `key(locale)` in `BatteryWidget` (0008).
- No network in `provideGlance` (0001).
- Branch `0005-widget-layouts` from `epic-widget-v5`. Commits header only, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Device safety: adb only with `-s emulator-5554`; connected Gradle tasks only with `ANDROID_SERIAL=emulator-5554`.

## Builder calls (spec §7)

1. **Files in `widget/`:** `LayoutSpec.kt` (the four specs and the bucket choice), `TextFit.kt` (pure fit rules), `PaintTextMeasurer.kt` (Android measurer), `WidgetFonts.kt` (boxes and shown sizes per drawing), `WidgetLayouts.kt` (the four layout composables), `WidgetParts.kt` (top row, ring, pack pills, cell pills, grid), `WidgetContent.kt` (root, `WidgetLook`, empty state). `WidgetTopRow.kt`, `WidgetBody.kt` and `WidgetScale.kt` go.
2. **`LayoutSpec` shape:** one data class with every README number of a layout (padding, corner, top-row height and gaps, button touch / face / icon, icon sizes, ring size and stroke, ring text gap and line heights, pack gap / padding / arrow, cell column width / gap / padding / temperature icon, grid sizes, empty-state sizes, design font sizes per text role). Two flags say the shape: `ringFullHeight` (5×2) and `cellGrid` (4×3).
3. **Bucket choice:** `LayoutSpec.forSize(size)` = the platform rule (`RemoteViews.findBestFitLayout`): of the buckets that fit (`ceil(size) + 1 > bucket`), the closest; none fits → 4×2.
4. **Text roles:** six roles with design sizes per layout (SOC, under SOC, ring label, main, cell, small); the ring label takes the small group's percent (§5.4).
5. **Fit boxes:** computed from the spec at the bucket size (§5.5). The top-row time left and update time share one box (the space between the balancing icon and the buttons) and get one common size, so they never overlap. The ring uses ink bounds (`Paint.getTextBounds`) of the three lines inside the inner circle with a 1 dp tolerance (§5.5: "touch by under 1 dp: accepted").
6. **Measurer:** `TextAppearanceSpan(context, Glance's TextAppearance style).updateMeasureState(paint)`, so measuring uses the exact typeface and weight Glance draws with (the device default font, weight 400 / 500 / 700).
7. **Ring:** track and arc are both alpha bitmaps (`RingBitmap.track`, `RingBitmap.arc`), drawn at the layout's ring size and stroke, tinted; the box is the ring column width × the row height with `ContentScale.Fit`, so the ring shrinks to its row below the design size. `ring_track.xml` goes. Line positions follow the canvas CSS stack (`RingLayout`), corrected for Android's font padding.
8. **Buttons in 2-row layouts:** the canvas has 6 dp between the 32 dp touch areas; each touch area grows 3 dp toward the other, so the circles stay where the PNG has them and the touch areas touch (§5.3).
9. **Error icon content description:** `widget_sync_error` becomes "Sync error: %1$s". The app card keeps its v4 icon + text (the app shows the full text); its icon uses the same description.
10. **App card scaling:** `WidgetScale` moves to `ui/sections/CardScale.kt` with its test.
11. **Fit check:** an instrumented test (`WidgetFitTest`) composes the real widget with `GlanceAppWidget.compose(size = bucket)`, applies the RemoteViews at the bucket size and at the owner's size, and asserts every text fits its view; with the instrumentation argument `fitShots=true` it also saves PNGs for the side-by-side sheet.

## Review Focus

- A launcher that reports a size between two buckets (e.g. 300 × 150): the platform picks the smaller layout, never a clipped bigger one. → Task 3 test `forSize_betweenBuckets_picksTheFittingOne`.
- 3 cell pills (no battery sensor) in 4×3: MOSFET takes row 2 alone, no empty slot. → Task 4 test `grid_noSensor_mosfetAloneInRowTwo`.
- Ukrainian worst case ("314,0 А·год", "8,32 кВт·год") at 100 %: shrinks, never clips. → Task 6 fit check.
- Large system font (1.3) at 100 %: requested sizes grow, fit keeps every text inside. → Task 2 unit test and Task 6 fit check.
- Stored old `fontSizes` JSON: ignored, everyone starts at 50 %. → Task 1 test `oldFontSizes_areIgnored`.

---

### Task 1: Font percents (§5.4)

**Files:** Modify `domain/model/Settings.kt`, `data/settings/DataStoreSettingsRepository.kt`, `ui/MainViewModel.kt`, `ui/MainScreen.kt`, `MainActivity.kt`, `ui/sections/WidgetSection.kt`, `res/values*/strings.xml`. Tests: `domain/FontPercentsTest.kt` (replaces `FontSizesTest`), `StoredJsonV1Test`, `DataStoreSettingsRepositoryTest`, `MainViewModelTest`.

**Interfaces — Produces:** `enum class FontGroup { SOC, UNDER_SOC, MAIN, CELL, SMALL }`; `data class FontPercents(soc, underSoc, main, cell, small: Int = 50)` with `get(group)`, `with(group, percent)`, `clamped()`, `requestedDp(group, designSp, fontScale): Float = designSp × percent / 50 × fontScale`, constants `MIN = 25`, `DEFAULT = 50`, `MAX = 100`, `STEP = 5`; `Settings.fontPercents`; `MainViewModel.saveFontPercents(FontPercents)`.

- [ ] Tests: clamping 24 → 25, 25, 50, 100, 101 → 100; requested size at 25 / 50 / 100 % × font scale 0.85 / 1.0 / 1.3 (e.g. 22 sp, 100 %, 1.3 → 57.2); v1 JSON pins `"fontPercents": {…}`; `oldFontSizes_areIgnored` (JSON with only `fontSizes` → `FontPercents()`); repository clamps `{"soc":5,"small":150}` → 25 / 100; ViewModel clamps on save.
- [ ] Implement; sliders 25–100 step 5 show "%1$d %" (both languages); Reset → `FontPercents()`.
- [ ] The widget and the card keep compiling: the card uses v4 sizes 40 / 12 / 22 / 14 / 10.5 × percent / 50 × font scale (§5.7); the widget keeps v4 until Task 4.
- Commit: `feat(domain): Store font sizes as percents`

### Task 2: Text fit rules (§5.5, C1)

**Files:** Create `widget/TextFit.kt`, `test/.../widget/TextFitTest.kt`.

**Interfaces — Produces:** `interface TextMeasurer { fun width(text: String, sizeDp: Float, weight: TextWeight): Float; fun lineHeight(sizeDp: Float, weight: TextWeight): Float; fun ink(text: String, sizeDp: Float, weight: TextWeight): Ink }`, `enum class TextWeight { NORMAL, MEDIUM, BOLD }`, `data class Ink(val width: Float, val top: Float, val bottom: Float)` (relative to the baseline), `object TextFit { fun fit(texts: List<String>, requested: Float, weight: TextWeight, box: Box, measurer: TextMeasurer): Float; fun fitRing(lines: RingLines, ring: RingBox, measurer: TextMeasurer): Float }` (the common factor ≤ 1).

- [ ] Tests with a fake measurer (width = 0.5 × size × chars, line height = 1.17 × size): `shortValue_keepsTheRequestedSize`, `longValue_shrinksToItsBox`, `height_limitsATallRequest`, `sharedBox_fitsTheSumOfWidths`, `ringLines_shrinkTogether`, `ring_toleratesOneDpTouch`, `fit_neverReturnsMoreThanRequested`.
- [ ] Implement: proportional size from the measured width / height, then step down 0.25 dp until it fits (fonts do not scale exactly linearly).
- Commit: `feat(widget): Fit each text to its box`

### Task 3: Layout specs and buckets (§5.1, §5.2)

**Files:** Create `widget/LayoutSpec.kt`, test `widget/LayoutSpecTest.kt`; modify `res/xml/battery_widget_info.xml`.

**Interfaces — Produces:** `data class LayoutSpec(...)` (call 2), `LayoutSpec.L4x2 / L5x2 / L4x3 / L5x3`, `LayoutSpec.all`, `LayoutSpec.forSize(width: Float, height: Float): LayoutSpec`, `fun boxes(width: Float, height: Float, cellPills: Int): LayoutBoxes` (shared top-row text box, pack pill, cell pill, ring diameter and stroke at that size, empty-state width).

- [ ] Tests: buckets 261×126, 334×126, 261×199, 334×199; `forSize` on each bucket, the owner's sizes, 340×278 → 5×3, 300×150 → 4×2 (`forSize_betweenBuckets_picksTheFittingOne`), 200×100 → 4×2; boxes at the design size equal the README (4×2 pack 89 × 35, cells 66 × 16; 5×2 pack 108 wide; 4×3 pack 153 × 40, grid pill 121 × 20; 5×3 pack 112 × 63, cells 84 × 28.75; ring 76 / 112 / 86 / 108); at the bucket the 4×2 ring is 70.
- [ ] Widget info: min and minResize 261 × 126 dp, max 460, no target cells.
- Commit: `feat(widget): Add the v5 layout specs and buckets`

### Task 4: Four layouts, Responsive (§5.2, §5.3, A1, B1)

**Files:** Create `widget/WidgetLayouts.kt`, `widget/WidgetParts.kt`, `widget/WidgetFonts.kt`, `widget/PaintTextMeasurer.kt`; modify `WidgetContent.kt`, `BatteryWidget.kt`, `RingBitmap.kt`, `RingLayout.kt`, `WidgetTexts.kt`, strings; delete `WidgetTopRow.kt`, `WidgetBody.kt`, `WidgetScale.kt` (moved, call 10), `ring_track.xml`, `WidgetLookTest` (replaced). Tests: `WidgetContentTest`, `RingLayoutTest`, `WidgetFontsTest`.

**Interfaces — Consumes:** Tasks 1–3. **Produces:** `WidgetContent(model, percents: FontPercents, colors: WidgetColors, actions: WidgetActions, measurer: TextMeasurer = PaintTextMeasurer(context))`; test tags `layout_4x2` … `layout_5x3` on each layout root; `WidgetFonts.compute(model, spec, percents, fontScale, measurer): ShownSizes`.

- [ ] Widget tests (Robolectric, fake measurer): `eachBucket_drawsItsLayout` (4 sizes → 4 tags); normal, refreshing, `error_showsIconOnly` (content description "Sync error: Home Assistant is unreachable", no error text, no update time), missing value, empty setup / no data, 3 and 4 cell pills, `grid_noSensor_mosfetAloneInRowTwo`, kWh / Ah, current / power, theme wiring tests kept.
- [ ] `WidgetFonts` tests: at 50 % with short values every size equals the design size; at 100 % a long value is smaller than requested.
- [ ] Implement the parts and layouts with weights (no computed widths), `SizeMode.Responsive(buckets)`, the error icon, the ring bitmaps.
- Commit: `feat(widget): Draw the four v5 layouts`

### Task 5: App card keeps v4 (§5.7, §5.8)

**Files:** Create `ui/sections/CardScale.kt`, `test/.../ui/CardScaleTest.kt` (from `WidgetScaleTest`); modify `OverviewCard.kt`, `OverviewParts.kt`, `androidTest/.../OverviewCardTest.kt`.

- [ ] The card uses `CardScale`, the percent rule of Task 1 and the shared error description; `OverviewCardTest` passes `FontPercents()`.
- Commit: `refactor(ui): Move the card scaling into the card` (folded into Task 4's commit if Task 4 cannot compile without it)

### Task 6: Fit check and preview (§5.6, §6)

**Files:** Create `androidTest/.../widget/WidgetFitTest.kt`; modify `res/layout/battery_widget_preview.xml` and its drawables.

- [ ] `WidgetFitTest`: each layout at its bucket and at the owner's size (267 × 132, 340 × 132, 267 × 205, 340 × 205, 340 × 278), worst-case and typical values, every group at 25 / 50 / 100 %, `en` and `uk`: every `TextView`'s text width ≤ its width + 0.5 px and one line; at 50 % `en` typical values, the shown size equals the design size.
- [ ] Preview layout = v5 4×2 normal state, static sample values.
- [ ] Run on `Pixel_8_API_35`, save shots (`fitShots=true`), build the side-by-side sheet against the PNGs (scratchpad only).
- Commits: `test(widget): Check every text fits at every size`, `feat(widget): Show the v5 4x2 in the widget picker`

### Task 7: Build notes and done gate

- [ ] Spec `## 8. Build notes (2026-11-15)`: §5.0 result (HyperOS `com.miui.home`), builder calls, deviations.
- [ ] `./gradlew lint testDebugUnitTest assembleRelease`; `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.
- Commit: `docs(specs): Add the 0005 build notes`
