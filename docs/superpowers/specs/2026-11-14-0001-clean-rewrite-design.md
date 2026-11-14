# Clean rewrite of HA Battery Widget — design

- **Date:** 2026-11-14
- **Status:** draft, waiting for owner approval
- **Branch:** `develop`
- **Set:** `0001` (the plan will be `plans/2026-11-14-0001-clean-rewrite.md`)

## 1. Intent

The app works, but the code is hard to change. The owner wants a clean code
base that follows SOLID, DRY and KISS, is translatable with English as the
default language, and keeps the Home Assistant (HA) token in secure storage.
The owner allowed removing all current code and starting again.

The app is a personal tool: an Android home-screen widget and a settings
screen that show a JK-BMS battery (8 cells, EVE 314 Ah) read from HA.

## 2. Goals

1. **Clean structure.** Clear layers, one way to do each thing, small files,
   no copy-paste. A new reader can find any behaviour in one place.
2. **One sync path.** Every refresh (timer, widget ↻, app button, app start)
   goes through one use case.
3. **Internationalization.** All visible text comes from Android resources.
   English is the default; Ukrainian is the first translation. No code logic
   reads translated text.
4. **Secure token.** The HA token is encrypted with a key in the Android
   Keystore, never backed up, never logged, never shown in plain text after
   saving.
5. **Same useful features** as today (section 6), with the cuts listed there.
6. **Tested.** Pure logic has JVM unit tests; storage and HTTP have tests
   with fakes or a mock server.

## 3. Non-goals

- No new features beyond those in section 6.
- No change to the visual design beyond what the rewrite needs (look stays
  close to today).
- No multi-module Gradle setup, no DI framework (Hilt/Koin).
- No in-app language picker (system per-app language is enough).
- No CI pipeline, no project `CLAUDE.md` (separate tasks).
- No git history rewrite (owner decision: untrack only, done in `7c7151b`).
  The old key stays in local use; key rotation is a later owner decision.
- `applicationId` stays `com.homeassistant.batterywidget`, so the update can
  install over the old app if the signing key is the same.

## 4. Anti-goals (a failure even if the app works)

- Any user-visible string in Kotlin code.
- Any `if` that compares translated text.
- The token in plain text on disk, in backup, in logs, or in exception text.
- Network calls inside the widget's render code (`provideGlance`).
- A second copy of the sync logic anywhere.
- Over-engineering: interfaces or layers that have only one caller and no
  test value.

## 5. Constraints

- Kotlin, Jetpack Compose, Glance 1.2, WorkManager, DataStore. Keep the
  current versions in `gradle/libs.versions.toml` unless a newer stable one
  is needed; check library docs with Context7 (fallback: developer.android.com).
- `minSdk 26`, `targetSdk 36`, JDK 17 for the build.
- HA API: `POST {baseUrl}/api/template` with a Long-Lived Access Token,
  one request returns all values as JSON (keep this — it is efficient).
- Build and tests must run on Linux: `./gradlew lint testDebugUnitTest assembleRelease`.

## 6. Scope: what stays, changes and goes

### Stays

- **Widget:** charge % with a progress bar coloured by level; power (W/kW);
  current (A); total voltage (V); time to empty / to full; cell voltages in two
  columns with min/max cells highlighted; cell delta (mV) coloured by size;
  last update time; ↻ button with a "refreshing" state; tap opens the app;
  size tiers by widget height (`WidgetLayoutSpec` logic); light/dark theme.
- **App screen:** battery preview card; "Refresh now"; "Add widget" (pin);
  auto-refresh on/off and interval; full-charge notification switch with the
  permission flow; connection settings (primary URL, fallback URL, token);
  entity editor (charge, power, fallback power, current, total voltage, cell
  list with add/remove, max 32); reset entities to defaults.
- **Notification:** one alert when the battery reaches 100%; re-arms when
  charge drops to ≤ 98% (same rule as today).
- **Widget configure activity:** returns `RESULT_OK` so the widget is placed.

### Changes

| Area | Today | New |
|---|---|---|
| Refresh interval | 1–1440 min (AlarmManager below 15) | 15–1440 min, WorkManager only *(call 2)* |
| Charge state | parses Ukrainian text of `sensor.battery_time_status` | from power sign and charge % *(call 3)* |
| Time remaining | text from `sensor.battery_time_status` | computed in app from charge %, current and capacity (default 314 Ah, editable) *(call 4)* |
| Delta, min cell, max cell | three HA entities + fallback math | computed from cell voltages *(call 5)* |
| Settings storage | SharedPreferences, token in plain text | DataStore; token encrypted (section 10) |
| Plain HTTP | allowed to any host | allowed only to local/private hosts (section 11) |
| Widget data | widget fetches in `provideGlance` | widget only reads the cache |
| Language | Ukrainian, hard-coded | English default + Ukrainian, resources only |

### Goes

- AlarmManager path and `SyncAlarmReceiver`.
- Navigation3 (one screen does not need it).
- Entities: delta voltage, min cell, max cell, time status.
- Progress-bar bitmap and its memory-cap check (use Glance
  `LinearProgressIndicator`) *(call 12)*.
- The signing passwords in `app/build.gradle.kts` and `HA-Battery-Widget.apk`
  from the working tree. (`app/release.keystore` is already untracked in
  `7c7151b`; the file stays on disk, ignored by git.)

## 7. Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A** | **Rewrite from scratch** on `develop`, reusing only the proven pure logic (template quoting, entity-ID check, full-charge rule, `WidgetLayoutSpec`) | Cleanest result; old shapes do not leak in. Higher short-term cost. |
| B | Step-by-step refactor of the current code | Lower risk per step, but the current shape (three fetch paths, text parsing, nullable store) pulls the design back. Slower to reach "clean". |
| C | Rewrite as several Gradle modules (`:domain`, `:data`, …) | Strong boundaries, but too much setup for ~3k lines. Breaks KISS. |

**Picked: A** *(call 1)*. The owner allowed removing all code, and the goal is
"maximum clean". Layer rules are kept by package structure and review, not by
modules.

## 8. Architecture

### Packages (single `:app` module)

```
com.homeassistant.batterywidget
├── App.kt              Application; owns AppContainer
├── AppContainer.kt     manual DI: builds and wires all objects once
├── domain/             pure Kotlin, no android.* imports
│   ├── model/          BatteryReading, BatteryState, Settings types, SyncError
│   ├── BatteryMath     state, delta, min/max cell, time remaining
│   ├── FullChargeRule  notification rule with re-arm at ≤ 98%
│   ├── Validation      entity IDs, URLs, interval, capacity
│   ├── port/           interfaces the domain needs (below)
│   └── SyncBattery     the one sync use case
├── data/
│   ├── ha/             HomeAssistantSource (HTTP), TemplateBuilder, DTO, UrlPolicy
│   ├── settings/       DataStore settings repository + legacy migration
│   ├── secure/         Keystore crypto + TokenStore
│   └── cache/          DataStore reading cache
├── system/             SyncWorker, WorkManager scheduler, notifier
├── widget/             Glance widget, receiver, ↻ action, widget refresher
└── ui/                 MainActivity, screen sections, ViewModel, theme
```

**Dependency rule:** `ui`, `widget`, `system`, `data` → `domain`. `domain`
depends on nothing Android. Wiring happens only in `AppContainer`.

### Ports (interfaces in `domain/port`)

Only where a second implementation exists (a fake in tests) or where the
domain must not see Android:

- `BatterySource` — fetch a reading for given settings and token.
- `SettingsRepository` — read/write connection, entities, refresh,
  notification and battery settings; exposes a `Flow`.
- `TokenStore` — `get()`, `set(token)`, `clear()`.
- `ReadingCache` — runtime state, not settings: last good reading, last
  error, `refreshingSince` time, and the full-charge "already notified" flag;
  `Flow`. The old notified flag is migrated (section 14).
- `WidgetRefresher` — redraw all widgets.
- `FullChargeNotifier` — show the 100% notification.
- `SyncScheduler` — apply the refresh schedule.

### The one sync flow

```
trigger: SyncWorker | widget ↻ | app "Refresh now" | app start
  └─ SyncBattery()
       settings = SettingsRepository; token = TokenStore
       if not configured            → result NotConfigured
       reading = BatterySource.fetch(...)   (primary URL, then fallback URL)
       on success → ReadingCache.saveReading(reading)
                    FullChargeRule → FullChargeNotifier (if needed)
       on failure → ReadingCache.saveError(error)   (last good reading kept)
       WidgetRefresher.refreshAll()
       return SyncResult
```

- The widget ↻ action sets "refreshing" in the cache and enqueues a unique
  one-time work request (REPLACE). It does no network work itself.
- The app screen calls `SyncBattery` directly in `viewModelScope` (the user is
  waiting for the result).
- The widget renders from `ReadingCache.flow` only.

### Error model

`SyncError` is a sealed type: `NotConfigured`, `InsecureConnection`,
`Unauthorized` (401/403), `Unreachable` (I/O, timeout), `HttpError(code)`,
`BadResponse` (parse failure). The UI and the widget map each case to a
string resource. The worker retries only on `Unreachable`.

## 9. HA data and battery math

- **Entities in settings:** charge %, power, fallback power, current, total
  voltage, cells (list). Defaults are the current `espjkbms` IDs.
- **Request:** the template builder keeps today's safe quoting and the
  "fallback power if primary is unknown/unavailable" logic; it returns
  `{soc, power, current, total_v, cells[]}` as JSON. Parse with
  kotlinx.serialization.
- **State** (sign: power > 0 = charging):
  - `FULL` — charge rounds to 100 and power ≥ −20 W
  - `DISCHARGING` — power < −20 W
  - `CHARGING` — power > 20 W
  - `IDLE` — otherwise
- **Delta / min / max:** from the cell list (1-based indices); hidden when
  fewer than 2 cells.
- **Units** stay as today: power in W, current in A, voltages in V; delta is
  shown in mV.
- **Time remaining:** the direction comes from the state (not from the sign of
  current), so the current's sign convention does not matter. To empty =
  `soc/100 × capacityAh / |current|`; to full =
  `(100 − soc)/100 × capacityAh / |current|`. Hidden when |current| < 0.5 A
  (both directions) or state is FULL/IDLE. Capacity default 314 Ah, editable
  in settings. Computed at render time from the cached reading (pure function);
  the widget already shows the reading's update time, so no extra staleness rule.

## 10. Secure token storage

- `KeystoreCrypto`: AES-256-GCM key in `AndroidKeyStore` (fixed alias, no
  user authentication, so the background worker can use it). Random IV per
  encryption; store `IV + ciphertext` as Base64.
- `TokenStore` uses `KeystoreCrypto` and its own DataStore file
  (`secure_settings`), separate from normal settings.
- `secure_settings` is excluded from cloud backup and device transfer
  (`backup_rules.xml` and `data_extraction_rules.xml`, now wired in the
  manifest). Keystore keys never leave the device anyway.
- If decryption fails (restored data, lost key, corruption): clear the stored
  token and return "no token". The UI asks for the token again. Never crash.
- The token is never logged and never put in exception messages or
  `SyncError`.
- UI: the token field is masked with a show/hide toggle. After save, the
  field is empty and shows "Token saved"; typing a new value replaces it.
  The token never sits in `UiState` after save.

## 11. Other security and repo rules

- **UrlPolicy:** `https://` to any host; `http://` only to `localhost`,
  single-label names, `*.local`, private IPv4 (`10/8`, `172.16/12`,
  `192.168/16`), CGNAT/Tailscale `100.64/10`, IPv6 `fc00::/7` and `fe80::/10`.
  Otherwise the request is refused with `InsecureConnection`. No DNS lookup
  for this check (KISS). The manifest keeps cleartext allowed, because
  Android's network security config cannot list IP ranges; the code rule is
  the real guard.
- **Signing:** release signing reads `storeFile`, passwords and alias from
  `~/.gradle/gradle.properties` or environment variables. When they are
  missing, the release build is unsigned; debug always works. The existing
  key file (kept locally) is used, so the new app installs over the old one.
- `.gitignore` already ignores `*.keystore`, `*.jks`; add `*.apk`, `*.aab`.
- Fix the exec bit of `gradlew` in git; README gets the Linux build command.

## 12. Internationalization

- `res/values/strings.xml` = English (default). `res/values-uk/strings.xml` =
  Ukrainian. Every visible text, including widget, notification channel and
  error texts, lives there.
- `res/resources.properties` with `unqualifiedResLocale=en`, and
  `androidResources { generateLocaleConfig = true }` → per-app language in
  Android 13+ system settings. Older Android follows the system language.
- Placeholders (`%1$s`) for every composed text; `<plurals>` for counts
  (Ukrainian has 4 forms).
- Numbers with the user's locale (`NumberFormat` with fixed fraction digits);
  clock time with `DateFormat.getTimeFormat(context)` (12/24 h); durations
  with `android.icu.text.MeasureFormat`.
- Units are part of the string resource (`"%1$s V"`), so a translator may
  change them.
- ViewModel and widget work with typed values (`SyncError`, enums, numbers);
  only Compose/Glance code turns them into text.
- Lint `MissingTranslation`, `ExtraTranslation`, `MissingQuantity`,
  `HardcodedText` are errors. `HardcodedText` covers XML only, so the build
  also has a light guard against string literals in Compose `Text(...)` /
  Glance text calls (builder picks the form).
- Debug build enables pseudo-locales (`en-XA`, `ar-XB`) for manual layout
  checks.
- The notification channel is created again before each post, so its name
  follows the current language.

## 13. Refresh and widget

- Auto mode: `PeriodicWorkRequest` with the chosen interval (15–1440 min),
  network required, unique name; policy UPDATE when settings change, KEEP on
  app start / widget enable. Manual mode: cancel the periodic work.
- Periodic sync runs when auto mode is on **and** (at least one widget exists
  **or** the full-charge notification is on).
- `updatePeriodMillis = 0` in the widget info, so the system does not add a
  second refresh timer. In manual mode the widget changes only after ↻ or an
  app action (same as today; intended).
- "Refreshing" is stored as a timestamp. `SyncBattery` always clears it when it
  ends (success or failure). The widget ignores a value older than 2 minutes,
  so a lost or replaced work request cannot leave it stuck.
- The widget shows the last good reading. On error it also shows a short
  error line from `SyncError`. Without any reading it shows an empty state
  with ↻.
- Keep `SizeMode.Exact` and the `WidgetLayoutSpec` tiers; keep their tests.

## 14. Settings migration (one time)

On first start of the new version: copy URLs, entities, refresh and
notification settings from the old `ha_battery_widget_prefs` file into
DataStore (`SharedPreferencesMigration`); encrypt the old token into
`TokenStore`; then delete the old file, so no plain-text token stays on disk
*(call 9)*.

## 15. Testing and done

- **JVM unit tests:** `BatteryMath`, `FullChargeRule`, `Validation`,
  `UrlPolicy`, `TemplateBuilder` (quoting), response parsing, `SyncBattery`
  with fakes, `WidgetLayoutSpec`, ViewModel with fakes, DataStore repositories
  with a temp file, legacy migration, `TokenStore` with a fake crypto.
- **HTTP tests:** OkHttp `MockWebServer` — success, 401, 500, timeout, bad
  JSON, fallback URL used after primary fails.
- **Device: an emulator on this machine** (owner decision). The builder sets
  uses the AVD `Pixel_8_API_37` (Android 17, Google APIs, x86_64; KVM works)
  and runs instrumented tests and manual checks there. `Pixel_6` (API 31,
  Android 12) is a second, older target. The SDK is shared with Android Studio
  (`~/Android/Sdk`), so the owner can open the same AVD from Device Manager.
  `targetSdk` stays 36; testing on a newer Android is fine.
  The owner reviews screenshots (en, uk, pseudo-locale, light/dark).
- **Instrumented tests** (on the emulator): `KeystoreCrypto` round trip;
  main screen smoke test.
- **Upgrade test:** install the old `HA-Battery-Widget.apk`, enter settings,
  then install the new release build signed with the same key.
- **Migration test:** instrumented test with seeded old prefs (URLs, entities,
  refresh, notification flag, token) → values in DataStore, token decrypts,
  old file gone.
- **Mock HA for manual checks:** a small local mock of `/api/template` (builder
  picks the form) that can return any reading, e.g. 100% for the notification
  check. No real HA token is needed for development.
- **Manual check:** widget and screen in English, Ukrainian and a
  pseudo-locale; light/dark; ↻; notification at 100%; update over the old
  version keeps settings and removes the old prefs file.
- **Done when:** `./gradlew lint testDebugUnitTest assembleRelease` is green,
  instrumented tests pass, the manual check passes, and no anti-goal from
  section 4 is present.

## 16. Calls made by Claude (owner, please check)

1. Approach A: rewrite from scratch.
2. Drop refresh faster than 15 min.
3. Charge state from power sign and charge %, not from sensor text.
4. Time remaining computed in the app from a capacity setting (default 314 Ah);
   `sensor.battery_time_status` is no longer used.
5. Delta, min and max cell computed from cell voltages; their three entities
   are dropped.
6. Languages: English default + Ukrainian; per-app language only via Android
   13+ system settings.
7. Token: Keystore AES-GCM + DataStore; masked field, not pre-filled.
8. Plain HTTP only to local/private hosts.
9. One-time migration of old settings, then delete the old prefs file.
10. Single module, manual DI.
11. OkHttp + kotlinx.serialization for HTTP and JSON.
12. Glance `LinearProgressIndicator` instead of a drawn bitmap.
13. Keep using the old signing key (owner kept the file and chose no history
    rewrite); rotation stays a later owner decision.
14. Settled from the builder check: time direction from state, not current
    sign; units as today; "notified" flag in `ReadingCache`; "refreshing" as
    a timestamp with a 2-minute limit; time computed at render time; a mock HA
    for manual checks.

## 17. Left to the builder

Exact class and file names; how to split the screen into section
composables; library versions (check with Context7 / official docs);
worker backoff values; `MeasureFormat` width; the form of the hard-coded-text
guard; colours and spacing within the current look.
