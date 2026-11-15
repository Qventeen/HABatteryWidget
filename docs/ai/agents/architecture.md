# Architecture

Design authority: `docs/superpowers/specs/2026-11-14-0001-clean-rewrite-design.md`
(§8 architecture, §9 battery math, §12 strings, §13 refresh and widget,
§14 stored data). Read the sections you touch before you change code.

## Packages

One `:app` module, package and app ID `io.lumensync.habattery`, manual DI.

- `App`, `AppContainer` (builds and wires every object once; the only wiring
  place), `MainActivity` (launcher and widget configure activity).
- `domain/`: pure Kotlin, no `android.*`. `model/`, `port/`, `SyncBattery`,
  `BatteryMath`, `BatteryOverview`, `FullChargeRule`, `RefreshPolicy`,
  `Validation`, `UrlPolicy`, `WidgetModel` (what widget and app card show),
  `WidgetLevels`, `ToggleAutoRefresh`.
- `data/`: `ha/` (HTTP source, template, response), `settings/`, `cache/`,
  `secure/` (Keystore crypto, `EncryptedTokenStore`), `store/` (JSON DataStore
  records with `schemaVersion = 1`).
- `format/`: `Formatters`, `Decimal`; one formatter for widget, screen and
  notification.
- `system/`: `SyncWorker`, `WorkManagerSyncScheduler`, `AndroidFullChargeNotifier`.
- `widget/`: Glance widget (one layout scaled by `WidgetScale`, ring bitmap),
  `BatteryWidgetReceiver`, ↻ and auto-toggle actions, refresher, and
  `WidgetPalette` / `WidgetTexts`, which the app's overview card reuses.
- `ui/`: Compose screen, `MainViewModel`, `sections/`, `theme/`.

Dependency rule: `ui`, `widget`, `system`, `data` → `domain`. `domain` depends
on nothing Android. Names of `MainActivity` and `BatteryWidgetReceiver` are
fixed (home screens store them).

## Ports (`domain/port/Ports.kt`)

`BatterySource`, `SettingsRepository`, `TokenStore`, `ReadingCache`,
`WidgetRefresher`, `FullChargeNotifier`, `SyncScheduler`. Add an interface only
when it has a second implementation or a test fake.

## One sync path

- Every trigger (worker, widget ↻, "Refresh now", app start) runs `SyncBattery`.
- `SyncBattery`: settings and token → `BatterySource.fetch` (the one URL) →
  reading or error into `ReadingCache` (last good reading kept) →
  `FullChargeRule` → `WidgetRefresher.refreshAll()`.
- Widget ↻ only marks "refreshing" and enqueues unique one-time work.
- The widget renders from local data only (`ReadingCache`,
  `SettingsRepository`, `TokenStore.hasToken`). No network call in
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
  display choices and font sizes default to the design values (spec 0003).
