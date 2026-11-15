# Widget preview in the app — design

- **Date:** 2026-11-15
- **Status:** approved by the owner (2026-11-15).
- **Set:** `0006`. Code on branch `0006-app-preview`, from `develop` after
  branch `0004-widget-design` (design v5 and these specs) is merged into
  it (the owner approves that merge).
- **Builds on:** spec 0005 (widget layouts v5).

## 1. Intent

The card at the top of the app — also the screen you see when you add or
reconfigure the widget — draws the widget a second time in Compose (spec
0003, decision 7). With four layouts that would be four more drawings to
keep in sync. The owner wants to see in the app how the widget really
looks, for each size, while configuring it.

## 2. Goals, non-goals, anti-goals

**Goals**

- Size tabs `4×2 · 5×2 · 4×3 · 5×3` above a preview of the widget.
- The preview is the real widget layout at the tab's design size, with the
  current reading and settings, in the app's theme.
- It opens on the size of the widget being configured.
- One drawing per layout: the app shows the widget's own layout, no
  Compose copy (if the test in 5.1 works).

**Non-goals**

- Tapping ↻ or the auto toggle on the preview (the screen has "Refresh
  now", and the Refresh section has the auto switch).
- Scaling the preview to the screen width; per-widget settings.

**Anti-goals**

- The preview differs from the widget on the home screen.
- A network call to draw the preview.

## 3. Owner decisions (2026-11-15)

1. Size tabs (option a), opening on the configured widget's size.
2. A short test first: can Glance draw the widget for the app? If yes, the
   Compose copy goes ("all picks").

## 4. Approaches (Claude's calls)

**A. Drawing the preview**

- **A1 (chosen if 5.1 works):** Glance turns a widget's composition for a
  given size into `RemoteViews` (`GlanceAppWidget.compose(context, id,
  options, size, state)`; public, not experimental, in glance-appwidget
  1.2.0 `AppWidgetComposer.kt` — checked by the reviewer in the library
  sources), and the app shows them with
  `AndroidView { remoteViews.apply(context, parent) }`. Passing `size` makes
  Glance draw that one size, so the tab's layout is chosen. One layout code
  for the widget and the app.
  `compose()` runs the widget's own `provideGlance`, which reads the
  *saved* settings. So the app uses a small `PreviewWidget :
  GlanceAppWidget` that draws the same `WidgetContent` from a model and
  settings it is given (spec 0007 passes the unsaved drafts).
- **A2 (fallback):** a Compose copy of each layout, sharing `WidgetModel`
  (today's approach × 4). If the test fails, the builder stops and tells
  the owner before taking this path, because it doubles the layout work.
- A3: a bitmap of the `RemoteViews`: needs the same API as A1, plus bitmap
  work, for no gain.

**B. Which tab opens first**

- **B1 (chosen):** if `MainActivity` was opened with
  `EXTRA_APPWIDGET_ID` (placement on Android 8–11, reconfigure on 12+), the
  bucket of that widget; else the bucket of the first placed widget; else
  4×2. "Bucket" = the largest of the four sizes that fits the size Android
  reports in the widget's options (the same rule as spec 0005). The user can
  switch tabs; the choice is not stored.

## 5. Design

### 5.1 Test first (throwaway, about 1 hour)

On a scratch branch: call the A1 API for each bucket and show the result in
`MainActivity`. Check on `Pixel_8_API_35` and `Pixel_6` (API 31):
the right layout per size, day and night colours, the ring bitmap, fonts,
no crash. Screenshots and the result go to the owner; A1 or A2 is decided
before the real work starts.

### 5.2 Preview card

- Segmented buttons `4×2 · 5×2 · 4×3 · 5×3`, then the preview at the
  bucket's design size (dp), centered, on the app background.
- Narrow screens: when the preview plus the screen's side padding is wider
  than the screen (5×2 and 5×3 are 340 dp; e.g. a 360 dp phone), the
  preview is scaled down to fit the width, keeping its shape. It is never
  scaled up. The owner's phone (390 dp) shows every tab at full size.
- The preview is not interactive: touches do not reach the widget's
  buttons.
- Empty model: the widget's empty layout without the "Open app" button
  (we are already in the app) — or with it hidden by the same model flag;
  builder's choice.
- The preview redraws when the reading, the settings or the tab change
  (from the ViewModel state). No network call: the reading comes from the
  cache, as for the widget.
- Theme: follows the app (system today; spec 0008 adds a setting). On
  Android 12+ Glance resolves day / night colours when the views are
  applied, so the preview takes the app's mode (reviewer, Glance sources).

### 5.3 Removed (with A1)

`OverviewCard`, `OverviewParts`, `CardLook` and any UI use of widget
scaling; strings no longer used (both languages). With A2 these become the
four Compose layouts instead.

## 6. Tests

- **Unit:** the first-tab rule as a pure function (configured id with
  reported sizes → bucket; no id → first placed widget; none → 4×2;
  edges at the bucket sizes).
- **androidTest:** the preview shows for each tab; tabs switch; the empty
  model shows the message; at a narrow width the 5×3 tab fits the screen.
- **Manual:** screenshots of each tab, light and dark, next to the v5 PNGs;
  open the app through "reconfigure" from a placed 5×3 widget → the 5×3
  tab opens.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease`;
  instrumented tests on `Pixel_8_API_35`.

## 7. Left to the builder

The tab control style, spacing, how `RemoteViews` are applied and reused,
and the empty-state button choice in 5.2.
