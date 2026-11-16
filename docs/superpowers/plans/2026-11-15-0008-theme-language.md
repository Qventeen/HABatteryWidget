# Theme and language (0008) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline). Steps use checkbox (`- [ ]`) syntax.

**Goal:** A theme setting (System / Light / Dark) for the app, the overview card and the widget, and a language setting (System / English / Українська, Android 13+ only) for the app and the widget.

**Architecture:** `Settings.appearance.theme` is stored. `ThemeChoice` turns into the widget colours in one place, `WidgetColors(theme)` (System = day/night providers; Light/Dark = fixed colours). Layout code only receives a `WidgetColors`. The language is not stored: `AppLanguage` wraps `LocaleManager`, and the app asks `MainViewModel` to redraw the widgets when the language changed.

**Spec:** `docs/superpowers/specs/2026-11-15-0008-theme-language-design.md` (section numbers below refer to it).

## Global Constraints

- JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
- Every task ends with `./gradlew :app:testDebugUnitTest` green (androidTest compiles where touched).
- `domain/` has no `android.*` import. No new dependencies.
- New strings go in `values/` and `values-uk/` in the same commit. Language names are in their own language.
- Branch `0008-theme-language` from `epic-widget-v5`. Commits: one per task, header only, trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Spec 0005 rewrites the widget layouts later: the theme choice stays in `WidgetColors` only.
- Device safety: instrumented runs only with `ANDROID_SERIAL=emulator-5554`.

## Builder calls (spec 7)

1. **Control style:** segmented buttons for both rows (as the Widget section); the summary is "System theme · System language" (language part missing when the row is hidden).
2. **Place:** spec 0007 is not built, so "Appearance" is the first section: right after the overview card and the action buttons, before Connection. Spec 0007 moves it behind Widget.
3. **Where the widget choice lives:** `widget/WidgetColors.kt`, class `WidgetColors(theme: ThemeChoice)`. `WidgetContent` takes a `WidgetColors`; `WidgetLook` carries it to every draw function.
4. **Dark or not:** `ThemeChoice.isDark(systemDark)` in `domain/model` is the one rule for the app theme and the overview card.
5. **Language redraw:** `MainViewModel.languageShown(tag)` remembers the tag; when it changes (also when the change came from the Android settings screen) it calls `refreshAll()`. The activity calls it on every `onCreate` with the current locale tag.
6. **Language port:** none. `AppLanguage` (system/) is a small class in `AppContainer`; the mapping tag to `LanguageChoice` is pure and unit tested. No second implementation or fake needed.
7. **System bars:** `enableEdgeToEdge` runs in a `DisposableEffect` keyed on the dark flag, with `SystemBarStyle.auto(..., detectDarkMode = { dark })`.
8. **Ukrainian wording:** Вигляд, Тема, Мова, Системна / Світла / Темна тема.

## Tasks

### Task 1: Setting and widget colours (5.1, A1)

- [ ] Tests: `Settings().appearance.theme == SYSTEM`; old JSON without `appearance` loads with the default; `isDark` for the three choices; `WidgetColors` under day and night configs: System follows the config, Light always Light colours, Dark always Dark colours (card, text, ring, pill background).
- [ ] `ThemeChoice`, `AppearanceSettings`, `Settings.appearance`.
- [ ] `WidgetColors` becomes a class; `WidgetLook` carries it; `WidgetContent(model, fontSizes, colors, actions)`; `BatteryWidget` builds it from the settings; update tests.
- Commit: `feat(widget): Choose the widget colours by theme`

### Task 2: ViewModel and app theme (5.3, B)

- [ ] ViewModel test: `saveAppearance` stores the choice and calls `refreshAll()`, no sync.
- [ ] `MainViewModel.saveAppearance`, `MainActions.saveAppearance`.
- [ ] `HaBatteryTheme(darkTheme)` fed by the setting, `OverviewCard(theme)`, edge-to-edge bars follow.
- Commit: `feat(ui): Apply the chosen theme to the app`

### Task 3: Language (5.1, C1)

- [ ] Tests: `LanguageChoice.fromTag` (empty, `en`, `en-US`, `uk`, `uk-UA`, `de`); ViewModel `languageShown`: first call no redraw, same tag no redraw, new tag redraws.
- [ ] `LanguageChoice`, `AppLanguage` (`LocaleManager`, Android 13+), container wiring, `MainViewModel.languageShown`, activity calls it.
- Commit: `feat(system): Read and set the app language`

### Task 4: Appearance section (5.2, 5.4)

- [ ] `AppearanceSection` (theme row, language row when supported, summary), placed first; `MainScreen` takes `language`; actions `setLanguage`.
- [ ] Strings in `values/` and `values-uk/`.
- [ ] androidTest in `MainScreenTest`: Dark changes the app colour (card background of the overview), then back to System; the language row exists on API 33+ and not below.
- Commit: `feat(ui): Add the Appearance section`

### Task 5: Verify

- [ ] Done gate: `./gradlew lint testDebugUnitTest assembleRelease`.
- [ ] `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` on `Pixel_8_API_35`, then on `Pixel_6` (one emulator at a time).
- [ ] Manual (spec 6): widget Light and Dark while the system is in the other mode; language switch puts app and widget in Ukrainian; widget redraws right after a theme change.
