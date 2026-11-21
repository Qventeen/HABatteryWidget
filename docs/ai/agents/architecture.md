# Architecture

Design authority: `docs/superpowers/specs/2026-11-14-0001-clean-rewrite-design.md`
(§8 architecture, §9 battery math, §12 strings, §13 refresh and widget,
§14 stored data). Later specs in `docs/superpowers/specs/` change parts of it
(0002 corrections, 0003 widget v4, 0004–0008 widget v5, 0012 inverter widget); on one topic the
newest spec wins. Read the sections you touch before you change code.

## Packages

One `:app` module, package and app ID `io.lumensync.habattery`, manual DI.

- `App`, `AppContainer` (builds and wires every object once; the only wiring
  place), `MainActivity` (launcher and configure activity of both widgets; `singleTop`; reads
  `EXTRA_TAPPED_WIDGET_ID`). The first tab is `AppTabs.first`: a widget opens its own tab,
  else the tab an alert asked for (`AppTab.EXTRA_OPEN_TAB`), else a missing connection opens
  Common, else Battery. The first battery preview tab (`PreviewTab.first`) is configured, tapped,
  first placed, 4×2; `StripRequest.first` does the same for the strip, ending on 3×1.
- `domain/`: pure Kotlin, no `android.*`. `model/`, `port/`, `SyncBattery`,
  `BatteryMath`, `BatteryOverview`, `EntityFill`, `LevelAlerts` (which level notifications to post, and
  the per-value alert state), `ModeAlerts` (the inverter mode change to post: compares the modes of the new and the stored text), `ShownNumber` (the number a level is picked by; widget and alerts), `RefreshPolicy`,
  `Validation`, `UrlPolicy`, `WidgetModel` and `InverterWidgetModel` (what each widget and its app preview show),
  `DefaultLevels`, `LevelBasis` (capacity, cells and battery type), `BatteryPreset` (volts per
  cell and charge / discharge C-rates per `BatteryType`), `LevelPicker` and `LevelSpans` (value
  levels and alerts), `DefaultTextLevels` and `TextLevelPicker` (text levels, spec 0014: the
  built-in mode list, the match of a text to its mode, and the one own-list check),
  `ToggleAutoRefresh`. `model/` holds `BatteryType`, `InverterSettings`, `InverterReading`,
  `TextLevels` (`TextValue`, `TextLevel`, `CustomTextLevels`, `ModeText`), and `FetchRequest` /
  `PartResult` / `FetchResult` (what a sync asks and gets back, one result per part).
- `data/`: `ha/` (`HaHttp`: shared request helper; `HomeAssistantSource`: Admin,
  template; `StatesSource`: User, states per entity and the sensor search; both read the two
  parts and build the answer with `toFetchResult`;
  `TokenTypeSource`: picks one by `connection.tokenType`; response parsing), `settings/`, `cache/`,
  `secure/` (Keystore crypto, `EncryptedTokenStore`: one slot per token type), `store/` (JSON DataStore
  records: settings `schemaVersion = 2`, migrated from v1 by `SettingsMigrationV2` in
  `settingsDataStore`; cache and token `schemaVersion = 1`).
- `format/`: `Formatters`, `Decimal`, `LevelTagText` (the level tag text),
  `LevelLabels` (level name, type word, unit and notification value labels), `InverterLabels`
  (`modeWord`: the one mode word for strip, notification and app; icon slot names); one formatter for
  widget, screen and notification.
- `system/`: `SyncWorker`, `WorkManagerSyncScheduler`, `AndroidLevelNotifier` (one channel per level
  type; also posts mode changes; notification ids: 100 + `LevelValue` ordinal, so 107 and 108 for
  the grid and output voltage, and 150 for the mode; each is also its content intent's request
  code, and the inverter ones carry `AppTab.EXTRA_OPEN_TAB` to open the Inverter tab),
  `AppLanguage` (per-app language through `LocaleManager`, Android 13+ only; the system
  stores it), `ConfigWatcher` (`App` reports language and font-scale changes, and night mode
  below API 31; a real change redraws widgets) and `LocaleChangedReceiver` (redraws on
  `ACTION_LOCALE_CHANGED`, e.g. a language change while the process is dead).
- `widget/`: two Glance widgets. The battery widget has four layouts in `SizeMode.Responsive` (spec 0005):
  `LayoutSpec` holds each layout's design numbers and bucket, `WidgetLayouts` /
  `WidgetParts` draw them, `WidgetFonts` fits every text to its box (`TextFit`,
  measured by `PaintTextMeasurer`), `RingLayout` (the ring's three text lines, placed
  by their visible ink, `ringTextGap` apart), ring bitmaps, `BatteryWidgetReceiver`, ↻ and
  auto-toggle actions, refresher, `WidgetPalette` / `WidgetTexts`, and
  `PreviewWidget` (spec 0006): the app's preview composes the same `WidgetContent` (or
  `InverterWidgetContent`) with `GlanceAppWidget.compose()` at the layout's bucket, without
  actions and at home-screen scale 1, and applies the `RemoteViews` fresh for each drawing (never
  `reapply`: 4×3 has its own root). Its content lambda shares one preview id and one mutex, so
  drawings of both widgets run one at a time.
  The inverter strip (spec 0012): `InverterWidget` / `InverterWidgetReceiver`,
  `InverterLayoutSpec` (three sizes, 3×1, 4×1, 5×1, with `StripSizes`), `InverterWidgetContent`
  (mode pill, two voltage pills), `InverterFonts` (fits its texts like `WidgetFonts`) and
  `InverterIcons` (icon → drawable and name). `StoredContent` (`provideStoredContent`) gives both
  widgets their local data and the recompose-on-config rule, `WidgetSchedule` re-applies the
  sync schedule when a receiver gets its first or last widget, and `WidgetLook<S>` holds the
  sizes `S` and the colours of one drawing. `GlanceWidgetRefresher` redraws both widgets.
  `BatteryWidget` composes `BatteryWidgetContent`: settings → font percents,
  `WidgetColors(theme)`, `HomeScale.factor` and the open-app action with the tapped id.
  The home-screen scale (`Settings.homeScalePercent`, 80–200 %, default 100; spec 0007) goes
  into `WidgetContent(hostScale)`; the preview never takes it. `TextConfig` (the language,
  font scale, and night mode below API 31) keys the composition.
  `WidgetColors(theme)` is the only place where `ThemeChoice` becomes widget colours; layout
  code receives colours only (`WidgetLook.colors`).
- `ui/`: Compose screen with a bottom bar of three tabs (`AppTab`: Battery, Inverter, Common),
  `MainViewModel`, `LevelDraft` (editor drafts and `middleSpan`), `TextLevelDraft` (the mode
  editor's drafts: add / delete a mode, add / remove a text),
  `PreviewTab` (battery size tabs, first-tab rule),
  `SensorSearch` / `SensorFill` (Fill: what HA answered and the new Entities draft), `sections/`
  (`GlancePreview` hosts both previews; `WidgetPreviewCard`, `InverterPreviewCard`; the Inverter
  sections `InverterEntitiesSection`, `InverterDisplaySection`, `InverterIconsSection` (the two
  voltage icons), `ModesSection` / `ModeEditor` (spec 0014: the modes' texts, words, colours,
  icons, bells and types);
  `HomeScaleSection` on Common; `AddWidgetRow`), `theme/`.

Dependency rule: `ui`, `widget`, `system`, `data` → `domain`. `domain` depends
on nothing Android. One accepted edge: `system/AndroidLevelNotifier` reads the accent
colour from `widget/WidgetPalette`. Names of `MainActivity`, `BatteryWidgetReceiver` and `InverterWidgetReceiver` are
fixed (home screens store them).

## Ports (`domain/port/Ports.kt`)

`BatterySource` (`fetch(connection, request: FetchRequest, token): FetchResult`),
`SettingsRepository`, `TokenStore`, `ReadingCache`, `WidgetRefresher`, `LevelNotifier` (`post`
for a level, `postMode` for a mode change), `SyncScheduler`. `TokenStore` keeps one
token per type: `savedTypes`, `get(type)`, `set(type, token)`, `clear(type)`. Add an interface only
when it has a second implementation or a test fake.

## One sync path

- Every trigger (worker, widget ↻, "Refresh now", app start) runs `SyncBattery`.
- `SyncBattery` serves two independent parts, battery and inverter:
  - Each part is checked as configured on its own. A part that is not configured is not fetched
    and gets `Failed(NotConfigured)`; its last reading stays in the cache. The sync runs when at
    least one part is configured.
  - Settings and token → `BatterySource.fetch(FetchRequest)` (`TokenTypeSource` → Admin: one
    template request; User: one `GET /api/states/<id>` per entity, at most 4 at a time).
  - A failure of the whole request (not reachable, token rejected, no JSON) fails every asked
    part. Otherwise each part has its own reading or error; a part without a usable reading
    fails with `BadResponse` and the other part still reads.
  - Each part's reading or error goes into `ReadingCache` (a failed part keeps its last good
    reading). In the same write `LevelAlerts.evaluate` (battery) and `LevelAlerts.evaluateInverter`
    run on a fresh reading, one after the other on the same alert map
    (`CacheState.levelAlerts`), and `ModeAlerts.evaluate` matches the new mode text and
    `CacheState.inverterMode` with the current mode list and compares the two modes.
  - Then `LevelNotifier.post` for each level alert, `LevelNotifier.postMode` for a mode change,
    and `WidgetRefresher.refreshAll()`.
- Widget ↻ only marks "refreshing" and enqueues unique one-time work.
- The widget renders from local data only (`ReadingCache`,
  `SettingsRepository`, `TokenStore.savedTypes`). No network call in
  `provideGlance`.
- Every cache or settings write is followed by `WidgetRefresher.refreshAll()`.
- `SyncError` is sealed; UI and widget map each case to a string. The worker
  retries only when any part is `Unreachable`.

## Strings and stored data

- Every visible text lives in `res/values/strings.xml` (English) with the
  Ukrainian text in `res/values-uk/strings.xml`, added in the same commit.
- Units live inside the string (`"%1$s V"`). No user text in Kotlin
  (`NoHardcodedTextTest`); no logic on translated text.
- Stored fields default only to `""`, empty list, `null` or `false`. Exception:
  display choices, font sizes, the home-screen scale and the theme default to the
  design values (specs 0003, 0007, 0008), and so do the inverter mode style, icons and strip
  font sizes (spec 0012), and a level's or a text level's type defaults to `NORMAL`
  and its side to `MIDDLE`, the level with no edge (`ColorLevel.type`, `ColorLevel.side`,
  spec 0010).
- The pack fields (spec 0013): `Settings.batteryType` `null` reads as LiFePO4, and a type name
  from a later build reads as `null`; `Settings.cellCount` is used only when the reading cannot
  count the cells, like `capacityAh` for the capacity.
