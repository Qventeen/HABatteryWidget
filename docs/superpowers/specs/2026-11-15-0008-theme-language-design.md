# Theme and language — design

- **Date:** 2026-11-15
- **Status:** approved by the owner (2026-11-15); built on branch `0008-theme-language`, merged into `epic-widget-v5`.
- **Set:** `0008`. Code on branch `0008-theme-language`, from `develop` after
  branch `0004-widget-design` (design v5 and these specs) is merged into
  it (the owner approves that merge).
- **Builds on:** spec 0003 (light and dark palettes for the app and the
  widget). Independent of 0004–0007; built after 0006 the preview follows
  the theme too.

## 1. Intent

Today the app and the widget follow the system light or dark mode and the
system language (the owner confirmed that works). The owner wants a theme
setting — System, Light, Dark — for the app and the widget, and a language
setting the same way.

## 2. Goals, non-goals, anti-goals

**Goals**

- Theme: System / Light / Dark, for the app and the widget.
- Language: System / English / Українська, for the app and the widget.

**Non-goals**

- More languages, dynamic colour (Material You), a theme per widget.
- The language setting on Android 8–12 (see approach C).

**Anti-goals**

- The widget and the app show different themes or languages.
- A widget that keeps the old theme or language until the next sync.

## 3. Owner decisions (2026-11-15)

1. Theme setting: System / Light / Dark, app and widget ("good idea").
2. Language setting "the same": System / English / Українська (Claude's
   reading of "the same").
3. These two go in their own spec, after the widget work ("all picks").

## 4. Approaches (Claude's calls)

**A. Widget theme**

- **A1 (chosen):** System = today's day / night colour providers; Light or
  Dark = fixed colours from the matching palette (single-colour
  providers), including the ring and track tints.
- A2: a night-mode override for the widget's resources: Android has no
  per-app-widget night mode.

**B. App theme:** `HaBatteryTheme(darkTheme = …)` gets the value from the
setting (System = `isSystemInDarkTheme()`); the system bars follow
(`enableEdgeToEdge` with the matching style).

**C. Language**

- **C1 (chosen):** Android 13+: `LocaleManager.setApplicationLocales`. The
  system stores the choice; it stays in sync with the per-app language
  screen in Android settings (`generateLocaleConfig = true` is already on);
  the widget's texts come from the app's context, so they follow. Android
  8–12: the language row is hidden; the app follows the system language.
- C2: AppCompat per-app locales on all versions: `MainActivity` must become
  an `AppCompatActivity` with an AppCompat theme, and on 8–12 the locale
  reaches only that activity, not the widget — the anti-goal.
- C3: own locale wrapping of every context, the widget included: much code
  for a small share of phones.

## 5. Design

### 5.1 Settings

- `Settings.appearance: AppearanceSettings(theme: ThemeChoice = SYSTEM)`;
  `ThemeChoice` = SYSTEM, LIGHT, DARK. Default per 0001 §14's exception for
  display choices (not user data).
- Language: not stored by the app; read and written with `LocaleManager`
  (empty list = System).

### 5.2 Section "Appearance"

- Right after the Widget section (spec 0007 puts Widget first).
- Theme: segmented buttons System / Light / Dark.
- Language (Android 13+ only): System / English / Українська.
- Both apply at once, without Save: they change the whole screen right
  away, and a language change recreates the activity anyway.
- Summary when folded: "System theme · System language".

### 5.3 Effects

- Theme change: the app recomposes; every widget redraws
  (`WidgetRefresher.refreshAll()`); the preview (0006) follows: Light and
  Dark use fixed colours, System resolves day / night when the views are
  applied (reviewer, Glance sources).
- Language change: the system recreates the activity; the app then calls
  `refreshAll()` so the widgets redraw in the new language.

### 5.4 Strings

New texts in `values/` and `values-uk/` in the same commit. Language names
are shown in their own language ("English", "Українська").

## 6. Tests

- **Unit:** the palette choice per theme setting (System uses the day /
  night providers; Light and Dark fixed); settings default and loading of
  old data.
- **ViewModel:** setting the theme saves and redraws widgets.
- **androidTest:** the theme switch changes the app colours; the language
  row is hidden on `Pixel_6` (API 31) and works on `Pixel_8_API_35`.
- **Manual:** widget in Light and Dark while the system is in the other
  mode; language switch → app and widget in Ukrainian.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease`;
  instrumented tests on `Pixel_8_API_35`.

## 7. Left to the builder

The control style, the section's place if the 0007 order is not built yet
(then: first section), and the Ukrainian wording.

## 8. Build notes and review fixes (2026-11-16)

- `ConfigWatcher` replaced the first `LocaleWatcher`. It watches the
  language tags and the font scale, and the night mode only below Android 12
  (API 31): from API 31 the widget carries both colours and the home screen
  picks one.
- Language names are not translated ("English", "Українська").
- Below Android 13 the language row is hidden, and the Appearance summary
  shows only the theme.
- `system/LocaleChangedReceiver` (in the manifest, not exported) redraws
  the widgets on `ACTION_LOCALE_CHANGED`. Android 13+ also sends it to the
  app when its own language changes in Settings while the process is dead.

### Known limits

- A system night-mode switch while the process is dead is seen only at the
  next redraw: there is no manifest broadcast for it.
- The launcher inflates `widget_error.xml` (fixed light colours), the
  loading layout and the picker preview with the system's language and night
  mode, not the app's choice. So the in-app preview's error view is always
  light.

### Known risk

- A short flash of the system theme can show on a cold start when the app
  theme differs. The owner may fix it later in its own task.
