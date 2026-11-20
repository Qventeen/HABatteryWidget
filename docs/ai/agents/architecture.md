# Architecture

Design authority: `docs/superpowers/specs/2026-11-14-0001-clean-rewrite-design.md`
(§8 architecture, §9 battery math, §12 strings, §13 refresh and widget,
§14 stored data). Later specs in `docs/superpowers/specs/` change parts of it
(0002 corrections, 0003 widget v4, 0004–0008 widget v5); on one topic the
newest spec wins. Read the sections you touch before you change code.

## Packages

One `:app` module, package and app ID `io.lumensync.habattery`, manual DI.

- `App`, `AppContainer` (builds and wires every object once; the only wiring
  place), `MainActivity` (launcher and widget configure activity; `singleTop`; reads
  `EXTRA_TAPPED_WIDGET_ID`; first preview tab: configured, tapped, first placed, 4×2).
- `domain/`: pure Kotlin, no `android.*`. `model/`, `port/`, `SyncBattery`,
  `BatteryMath`, `BatteryOverview`, `EntityFill`, `FullChargeRule`, `RefreshPolicy`,
  `Validation`, `UrlPolicy`, `WidgetModel` (what the widget and its app preview show),
  `DefaultLevels`, `LevelBasis`, `LevelPicker` and `LevelSpans` (value colours), `ToggleAutoRefresh`.
- `data/`: `ha/` (`HaHttp`: shared request helper; `HomeAssistantSource`: Admin,
  template; `StatesSource`: User, states per entity and the sensor search;
  `TokenTypeSource`: picks one by `connection.tokenType`; response parsing), `settings/`, `cache/`,
  `secure/` (Keystore crypto, `EncryptedTokenStore`: one slot per token type), `store/` (JSON DataStore
  records with `schemaVersion = 1`).
- `format/`: `Formatters`, `Decimal`, `LevelTagText` (the level tag text); one formatter for
  widget, screen and notification.
- `system/`: `SyncWorker`, `WorkManagerSyncScheduler`, `AndroidFullChargeNotifier`,
  `AppLanguage` (per-app language through `LocaleManager`, Android 13+ only; the system
  stores it), `ConfigWatcher` (`App` reports language and font-scale changes, and night mode
  below API 31; a real change redraws widgets) and `LocaleChangedReceiver` (redraws on
  `ACTION_LOCALE_CHANGED`, e.g. a language change while the process is dead).
- `widget/`: Glance widget with four layouts in `SizeMode.Responsive` (spec 0005):
  `LayoutSpec` holds each layout's design numbers and bucket, `WidgetLayouts` /
  `WidgetParts` draw them, `WidgetFonts` fits every text to its box (`TextFit`,
  measured by `PaintTextMeasurer`), `RingLayout` (the ring's three text lines, placed
  by their visible ink, `ringTextGap` apart), ring bitmaps, `BatteryWidgetReceiver`, ↻ and
  auto-toggle actions, refresher, `WidgetPalette` / `WidgetTexts`, and
  `PreviewWidget` (spec 0006): the app's preview composes the same `WidgetContent`
  with `GlanceAppWidget.compose()` at the layout's bucket, without actions and at
  home-screen scale 1, and applies the `RemoteViews` fresh for each drawing (never
  `reapply`: 4×3 has its own root).
  `BatteryWidget` composes `BatteryWidgetContent`: settings → font percents,
  `WidgetColors(theme)`, `HomeScale.factor` and the open-app action with the tapped id.
  The home-screen scale (`Settings.homeScalePercent`, 80–200 %, default 100; spec 0007) goes
  into `WidgetContent(hostScale)`; the preview never takes it. `TextConfig` (the language,
  font scale, and night mode below API 31) keys the composition.
  `WidgetColors(theme)` is the only place where `ThemeChoice` becomes widget colours; layout
  code receives colours only (`WidgetLook.colors`).
- `ui/`: Compose screen, `MainViewModel`, `LevelDraft` (editor drafts and `middleSpan`),
  `PreviewTab` (size tabs, first-tab rule),
  `SensorSearch` / `SensorFill` (Fill: what HA answered and the new Entities draft), `sections/`
  (`WidgetPreviewCard` hosts the preview), `theme/`.

Dependency rule: `ui`, `widget`, `system`, `data` → `domain`. `domain` depends
on nothing Android. Names of `MainActivity` and `BatteryWidgetReceiver` are
fixed (home screens store them).

## Ports (`domain/port/Ports.kt`)

`BatterySource`, `SettingsRepository`, `TokenStore`, `ReadingCache`,
`WidgetRefresher`, `FullChargeNotifier`, `SyncScheduler`. `TokenStore` keeps one
token per type: `savedTypes`, `get(type)`, `set(type, token)`, `clear(type)`. Add an interface only
when it has a second implementation or a test fake.

## One sync path

- Every trigger (worker, widget ↻, "Refresh now", app start) runs `SyncBattery`.
- `SyncBattery`: settings and token → `BatterySource.fetch` (`TokenTypeSource` → Admin: one template request; User: one
  `GET /api/states/<id>` per entity, at most 4 at a time) →
  reading or error into `ReadingCache` (last good reading kept) →
  `FullChargeRule` → `WidgetRefresher.refreshAll()`.
- Widget ↻ only marks "refreshing" and enqueues unique one-time work.
- The widget renders from local data only (`ReadingCache`,
  `SettingsRepository`, `TokenStore.savedTypes`). No network call in
  `provideGlance`.
- Every cache or settings write is followed by `WidgetRefresher.refreshAll()`.
- `SyncError` is sealed; UI and widget map each case to a string. The worker
  retries only `Unreachable`.

## Strings and stored data

- Every visible text lives in `res/values/strings.xml` (English) with the
  Ukrainian text in `res/values-uk/strings.xml`, added in the same commit.
- Units live inside the string (`"%1$s V"`). No user text in Kotlin
  (`NoHardcodedTextTest`); no logic on translated text.
- Stored fields default only to `""`, empty list, `null` or `false`. Exception:
  display choices, font sizes, the home-screen scale and the theme default to the
  design values (specs 0003, 0007, 0008), and a level's type defaults to `NORMAL`
  and its side to `MIDDLE`, the level with no edge (`ColorLevel.type`, `ColorLevel.side`,
  spec 0010).
