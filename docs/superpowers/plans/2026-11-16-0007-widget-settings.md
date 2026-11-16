# Widget settings (0007) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline). Steps use checkbox (`- [ ]`) syntax.

**Goal:** The Widget section is the first settings section after the preview. It keeps the font sliders, adds a "Home-screen scale" slider (80–150 %) and one "Reset to defaults" button. Every change saves at once.

**Architecture:** `Settings.homeScalePercent` (default 100) is stored with the settings and clamped on read like `FontPercents`. `BatteryWidget` hands `percent / 100f` to `WidgetContent(hostScale)`; it replaces `LayoutSpec.HOST_SCALE`. The app preview keeps scale 1.0.

**Spec:** `docs/superpowers/specs/2026-11-15-0007-widget-settings-design.md`, §8 (replaces the number fields of §3.1, §5.2–5.4 and the validation tests of §6).

## Global Constraints

- JDK 17; done gate `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`.
- No new dependencies. Strings in both languages, same commit as the code. No device, emulator or adb.
- `domain/` has no `android.*` import.

## Tasks

### Task 1: Stored home-screen scale replaces `HOST_SCALE`

**Files:** `domain/model/Settings.kt`, `data/settings/DataStoreSettingsRepository.kt`, `widget/BatteryWidget.kt`, `widget/LayoutSpec.kt`. Tests: `StoredJsonV1Test`, `DataStoreSettingsRepositoryTest`, new `HomeScaleTest`, `LayoutSpecTest`, `WidgetContentTest`.

- [ ] Tests: clamping (79 → 80, 80, 100, 150, 151 → 150), default 100, old JSON → 100, stored form pinned, repository round trip and clamp on read, a scale of 1.28 draws bigger sizes than 1.0 in `WidgetContent`.
- Commit: `feat(widget): Store the home-screen scale`

### Task 2: ViewModel save and reset

**Files:** `ui/MainViewModel.kt`. Test: `MainViewModelTest`.

- [ ] Tests: save clamps and redraws the widgets; reset sets fonts 50 and scale 100.
- Commit: `feat(ui): Save the scale and reset the widget settings`

### Task 3: Widget section and screen order

**Files:** `ui/sections/WidgetSection.kt`, `ui/MainScreen.kt`, `MainActivity.kt`, both `strings.xml`. Tests: androidTest `MainScreenTest` (compile only).

- [ ] Scale slider with supporting text, one "Reset to defaults", summary "· text size changed", Widget above Appearance; remove the unused "Reset sizes" string.
- [ ] androidTest: Widget is first after the preview and actions; the scale slider saves; Reset sets 50 / 100.
- Commit: `feat(ui): Add the home-screen scale slider`

### Task 4: Docs

- [ ] `architecture.md` (scale stored, no constant), spec 0007 build notes.
- Commit: `docs: Record the 0007 widget settings build`

## Review focus

- A saved scale reaches a live Glance session (settings are collected as state).
- The preview never takes the scale.
- Old stored files load with 100; out-of-range values are clamped.
