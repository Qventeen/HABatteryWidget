# Widget preview in the app (0006) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline). Steps use checkbox (`- [ ]`) syntax.

**Goal:** The app's top card shows the real widget layout for a chosen size tab (`4×2 · 5×2 · 4×3 · 5×3`), drawn by Glance, instead of the Compose copy.

**Architecture:** `PreviewWidget : GlanceAppWidget` draws `WidgetContent` from a model, font percents and colours it is given, without actions and at host scale 1.0. `GlanceAppWidget.compose(context, size = bucket)` turns it into `RemoteViews`; the app applies them fresh into a small host view at the layout's design card size, scaled down only on narrow screens. The first tab comes from a pure rule over the sizes Android reports.

**Tech Stack:** Kotlin, Glance 1.2.0 (`compose()`), Compose Material 3 segmented buttons, Robolectric + glance-appwidget-testing (unit), instrumented tests (compile only until the end of the epic).

**Spec:** `docs/superpowers/specs/2026-11-15-0006-app-widget-preview-design.md` (A1, B1, §5.2, §5.3, §6); 0007 §8 (preview at 100 %).

## Global Constraints

- JDK 17; done gate `./gradlew lint testDebugUnitTest assembleRelease`; androidTest compiles (`:app:compileDebugAndroidTestKotlin`).
- No new dependencies. Strings in both languages, same commit. Colours only through `WidgetColors`.
- Branch `0006-app-preview` from `epic-widget-v5`. Spike 5.1 runs in place (A1 is proven by `WidgetFitTest`); if A1 fails, stop and report.
- Device safety: adb only with `-s emulator-5554`.

## Builder calls (spec §7)

1. **No tap targets in the preview:** `WidgetContent(actions = null)` draws no `clickable` and no "Open app" button, so the RemoteViews carry no click handler at all. The host view also intercepts every touch.
2. **Theme:** the preview passes the app's `ThemeChoice` to `WidgetColors`, as the widget does; System resolves day / night from the activity's configuration when the views are applied (the app's theme for System is the system's).
3. **Sizes:** composed at the bucket (as `LocalSize` is on the home screen), laid out at the design card (bucket + 6 dp), like the owner's widgets.
4. **Tab switch:** every redraw applies new views into an emptied host; never `reapply` (4×3 has a Column root).
5. **Full sync error:** the widget shows only an icon, so a line under the preview shows "Sync error: <reason>" when a reading has an error.

## Tasks

### Task 1: Widget without actions + `PreviewWidget`

**Files:** `widget/WidgetContent.kt`, `WidgetLayouts.kt`, `WidgetParts.kt`, new `widget/PreviewWidget.kt`, `LayoutSpec.kt` (`designSize`). Test: `WidgetContentTest`.

- [ ] Test: without actions no click action and no "Open app"; the layouts still draw.
- Commit: `feat(widget): Draw the widget without tap targets`

### Task 2: First-tab rule (B1)

**Files:** new `ui/PreviewTab.kt`. Test: `ui/PreviewTabTest.kt`.

- [ ] Tests: configured size → its bucket; edges at each bucket (−0.9 dp shows it, −1 dp the smaller one); configured wins over placed; no id → first placed; none → 4×2; 0 × 0 → 4×2. Scale: 1 at or above the design width, never above 1.
- Commit: `feat(ui): Pick the preview's first size tab`

### Task 3: Preview card on the main screen

**Files:** new `ui/sections/WidgetPreviewCard.kt`; `ui/MainScreen.kt`, `MainActivity.kt` (first tab from the intent and the placed widgets), strings. Tests: androidTest `ui/WidgetPreviewTest.kt`, `MainScreenTest`, `MainActivityConfigureTest`.

- [ ] androidTest: each tab shows its design size and the reading; tabs switch; empty model shows the message without "Open app"; 300 dp wide → 5×3 fits, shape kept.
- Commit: `feat(ui): Show the real widget as the app preview`

### Task 4: Remove the Compose card (§5.3)

**Files:** delete `OverviewCard`, `OverviewParts`, `CardScale` (with `CardLook`), `CardScaleTest`, `CardLookTest`, `OverviewCardTest`; the auto toggle's ViewModel path; unused strings (both languages).

- Commit: `refactor(ui): Remove the Compose overview card`

### Task 5: Docs

- [ ] `architecture.md`, spec 0006 build notes.
- Commit: `docs: Record the 0006 app preview build`

## Review Focus

- Touches never reach the widget's buttons (no click handlers in the preview's views).
- App theme Light / Dark on a system in the other mode: the preview follows the app.
- Switching 4×3 ↔ 4×2 / 5×3: fresh views, the right layout each time.
- Text sizes equal the widget's at the same system font scale (same fit path).
- "Refresh now", the auto switch and the full error text are still on the screen.
