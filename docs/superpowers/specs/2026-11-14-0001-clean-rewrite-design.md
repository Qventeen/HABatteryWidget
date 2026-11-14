# Clean rewrite of HA Battery Widget — design

- **Date:** 2026-11-14 (v3: owner decisions on defaults, app ID and signing;
  missing values defined; second builder check)
- **Status:** draft, waiting for owner approval
- **Branch:** `develop`
- **Set:** `0001` (the plan will be `plans/2026-11-14-0001-clean-rewrite.md`)
- **Related:** `CLAUDE.md` (project rules), `references/README.md` (expert
  examples)

## 1. Intent

The app works, but the code is hard to change. The owner wants a clean code
base that follows SOLID, DRY and KISS, is translatable with English as the
default language, and keeps the Home Assistant (HA) token in secure storage.
The owner allowed removing all current code and starting again.

The app is a personal tool: an Android home-screen widget and a settings
screen that show a JK-BMS battery (8 cells, EVE 314 Ah) read from HA.

**No upgrade path from the old app** (owner decision, 2026-11-14). The old app
is uninstalled once; the new app starts with empty settings. The rewrite may
change anything, including the build setup, package and app ID. It must be a
clean foundation for future releases.

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
- No entity picker or auto-discovery from HA. First setup means typing each
  entity ID by hand (about 13 with 8 cells). Accepted for now; entity setup
  may be redesigned later (owner).
- No CI pipeline (separate task). `CLAUDE.md` already exists with stable
  facts only (`d27ca46`); this rewrite updates it at the end (section 15).
- No generated widget previews (`providePreview`, Android 15+). The static
  picker preview stays, with its texts moved to resources. A later option.
- No move to `targetSdk 37`. If it comes later, follow the Android 17 widget
  memory rules in `references/android-skills/system/glance-appwidget-updates-and-memory/`.
- No git history rewrite (owner decision: untrack only, done in `7c7151b`).
- No migration of the old app's data (owner decision). Versioning of the new
  app's own data is in section 14.

## 4. Anti-goals (a failure even if the app works)

- Any user-visible string in Kotlin code.
- Any `if` that compares translated text.
- The token in plain text on disk, in backup, in logs, or in exception text.
- Network calls inside the widget's render code (`provideGlance`).
- A second copy of the sync logic anywhere.
- Over-engineering: interfaces or layers that have only one caller and no
  test value.
- After the first production release: renaming the app ID, the widget
  receiver or the launcher activity (see section 5).
- Copying the shape of a reference repo (Hilt, Room, many modules) instead of
  its idea.

## 5. Constraints

- Stack: Kotlin, Jetpack Compose + Material 3, Glance 1.2, WorkManager,
  typed DataStore (`androidx.datastore:datastore`) with a kotlinx.serialization
  JSON serializer, OkHttp, kotlinx.serialization. Keep the current versions in `gradle/libs.versions.toml` unless a newer stable one
  is needed. Check library docs with Context7 when it is connected (it was
  not on 2026-11-14); fallback: developer.android.com and the Glance source in
  `references/androidx-glance/`.
- `minSdk 26`, `targetSdk 36`, JDK 17 for the build.
- HA API: `POST {baseUrl}/api/template` with a Long-Lived Access Token,
  one request returns all values as JSON (keep this — it is efficient).
- Build and tests must run on Linux: `./gradlew lint testDebugUnitTest assembleRelease`
  (through `bash ./gradlew` until the exec bit is fixed, section 11).
- **Final component names from the first release.** Choose the app ID, the
  widget receiver and the launcher activity names now, and never rename them
  after the first production release. Android binds placed widgets and
  launcher icons to them; Google's Gratitude case study (2026) reports that a
  package refactor removed widgets from users' home screens. Before that
  release, renames are free. App ID and package: `io.lumensync.habattery`
  (owner decision, 2026-11-14). Component names *(call 20)*:
  `io.lumensync.habattery.MainActivity` and
  `io.lumensync.habattery.widget.BatteryWidgetReceiver`.

## 5a. References

`references/` holds 7 expert repos chosen and checked on 2026-11-14 (index and
rules: `references/README.md`). Before building a part, read its reference in
the README's "What to study, by topic" table. Key ones for this spec:

| Spec part | Reference |
|---|---|
| Widget update after a cache change | `android-skills/system/glance-appwidget-updates-and-memory/` |
| Progress bar *(call 12)* | `androidx-glance/.../demos/ProgressIndicatorAppWidget.kt` |
| Error UI *(call 16)* | `androidx-glance/.../demos/ErrorUiAppWidget.kt` |
| Widget UI tests | `androidx-glance/glance/glance-appwidget-testing/` |
| Layers and fakes at our size | `architecture-samples/` |
| Periodic sync | `nowinandroid/sync/work/` |

Read only; take the idea, not the shape (anti-goals, section 4).

## 6. Scope: what stays, changes and goes

### Stays

- **Widget:** charge % with a progress bar coloured by level; power (W/kW);
  current (A); total voltage (V); time to empty / to full; cell voltages in two
  columns with min/max cells highlighted; cell delta (mV) coloured by size;
  last update time; refresh mode line ("auto, every N min" / "manual");
  ↻ button with a "refreshing" state; tap opens the app;
  size tiers by widget height (`WidgetLayoutSpec` logic); light/dark theme.
- **App screen:** battery preview card; "Refresh now"; "Add widget" (pin);
  auto-refresh on/off and interval; full-charge notification switch with the
  permission flow; connection settings (primary URL, fallback URL, token);
  entity editor (charge, power, fallback power, current, total voltage, cell
  list with add/remove, max 32). Entity setup may be redesigned later (owner).
- **Notification:** one alert when the state becomes `FULL` (section 9);
  re-arms when charge drops to ≤ 98% (same rule as today).
- **Widget configure activity:** returns `RESULT_OK` so the widget is placed.

### Changes

| Area | Today | New |
|---|---|---|
| Refresh interval | 1–1440 min (AlarmManager below 15) | 15–1440 min, WorkManager only *(call 2)* |
| Charge state | parses Ukrainian text of `sensor.battery_time_status` | from power sign and charge % *(call 3)* |
| Time remaining | text from `sensor.battery_time_status` | computed in app from charge %, current and a capacity setting (starts empty) *(call 4)* |
| Delta, min cell, max cell | three HA entities + fallback math | computed from cell voltages *(call 5)* |
| Settings storage | SharedPreferences, token in plain text | typed DataStore: one `@Serializable` data class per file, JSON serializer, no string keys *(call 17)*; token encrypted (section 10) |
| Plain HTTP | allowed to any host | allowed only to local/private hosts (section 11) |
| Widget data | widget fetches in `provideGlance` | widget reads only local data: cache and settings *(call 20)* |
| Language | Ukrainian, hard-coded | English default + Ukrainian, resources only |
| Widget crash fallback | own `onCompositionError` with hard-coded Ukrainian `RemoteViews` | Glance `errorUiLayout` with a translated layout *(call 16)* |
| Picker preview layout | hard-coded Ukrainian sample texts | same layout, texts from resources (sample numbers as `translatable="false"`) |

### Goes

- AlarmManager path and `SyncAlarmReceiver`.
- Navigation3 (one screen does not need it).
- Entities: delta voltage, min cell, max cell, time status.
- All old config defaults (owner decision): entity IDs (`espjkbms…`), the
  8-cell list, primary URL `http://homeassistant.local:8123`, auto-refresh on
  every 15 min, and "reset entities to defaults". Every config field starts
  empty; every switch starts off *(call 18)*.
- Progress-bar bitmap and its memory-cap check (use Glance
  `LinearProgressIndicator`) *(call 12)*.
- All old code, build scripts and resources (rewritten from zero), and the old
  app's stored data (no migration).
- The signing passwords in `app/build.gradle.kts` and `HA-Battery-Widget.apk`
  from the working tree. (`app/release.keystore` is already untracked in
  `7c7151b`; the file stays on disk, ignored by git.)

## 7. Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A** | **Rewrite from scratch** on `develop`, reusing only the proven pure logic (template quoting, entity-ID check, full-charge rule, `WidgetLayoutSpec`) | Cleanest result; old shapes do not leak in. Higher short-term cost. |
| B | Step-by-step refactor of the current code | Lower risk per step, but the current shape (three fetch paths, text parsing, nullable store) pulls the design back. Slower to reach "clean". |
| C | Rewrite as several Gradle modules (`:domain`, `:data`, …) | Strong boundaries, but too much setup for ~3k lines. Breaks KISS. |
| 0 | No custom app: the HA Companion app's **Template widget** (renders a Jinja template as simple HTML) plus an HA automation for the 100% alert | Zero code, no token handling here. Likely lacks the progress bar, cell grid and size tiers. |

**Gate before building:** the owner tries option 0 for about 10 minutes with
the current template. If it is enough, this rewrite stops. If not, build A.

**Picked: A** *(call 1)*. The owner allowed removing all code, and the goal is
"maximum clean". Layer rules are kept by package structure and review, not by
modules. The layers follow Google's recommended app architecture (UI → small
domain → data), not "Clean Architecture by the book": a use case exists only
where logic is shared (`SyncBattery`).

## 8. Architecture

### Packages (single `:app` module)

```
io.lumensync.habattery  (app ID = package, section 5)
├── App.kt              Application; owns AppContainer
├── AppContainer.kt     manual DI: builds and wires all objects once
├── MainActivity.kt     launcher + widget configure activity (final name, section 5)
├── domain/             pure Kotlin, no android.* imports
│   ├── model/          BatteryReading, BatteryState, Settings types, SyncError
│   ├── BatteryMath     state, delta, min/max cell, time remaining
│   ├── FullChargeRule  notification rule with re-arm at ≤ 98%
│   ├── Validation      entity IDs, URLs, interval, capacity
│   ├── port/           interfaces the domain needs (below)
│   └── SyncBattery     the one sync use case
├── data/
│   ├── ha/             HomeAssistantSource (HTTP), TemplateBuilder, DTO, UrlPolicy
│   ├── settings/       typed DataStore settings repository (versioned, section 14)
│   ├── secure/         Keystore crypto + TokenStore
│   └── cache/          typed DataStore reading cache
├── system/             SyncWorker, WorkManager scheduler, notifier
├── widget/             Glance widget, BatteryWidgetReceiver (final name, section 5), ↻ action, refresher
└── ui/                 screen sections, ViewModel, theme
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
  `Flow`.
- `WidgetRefresher` — redraw all widgets.
- `FullChargeNotifier` — show the 100% notification.
- `SyncScheduler` — apply the refresh schedule.

### The one sync flow

```
trigger: SyncWorker | widget ↻ | app "Refresh now" | app start
  └─ SyncBattery()
       settings = SettingsRepository; token = TokenStore
       if not configured → error NotConfigured (no request)
       else reading = BatterySource.fetch(...)   (primary URL, then fallback URL)
       on success → ReadingCache.saveReading(reading)
                    FullChargeRule → FullChargeNotifier (if needed)
       on failure → ReadingCache.saveError(error)   (last good reading kept)
       always     → clear "refreshing"; WidgetRefresher.refreshAll()
       return SyncResult
```

- The widget ↻ action sets "refreshing" in the cache and enqueues a unique
  one-time work request (REPLACE). It does no network work itself.
- The app screen calls `SyncBattery` directly in `viewModelScope` (the user is
  waiting for the result).
- The widget renders from local data only: `ReadingCache` (reading, error,
  "refreshing") and `SettingsRepository` (capacity, refresh mode). No network
  calls *(call 20)*.
- **Fallback URL** *(call 20)*: tried after any primary failure, when set. If
  both fail, the saved error is the more specific one: any error beats
  `Unreachable`; between two other errors, the primary's wins.
- Writing the cache or the settings does not redraw a widget by itself. Every
  cache change (reading, error, "refreshing" flag) and every settings save is
  followed by `WidgetRefresher.refreshAll()`,
  which calls Glance `updateAll()`. Source: Google's Glance updates skill
  (`references/android-skills/...`).

### Error model

`SyncError` is a sealed type: `NotConfigured`, `InsecureConnection`,
`Unauthorized` (401/403), `Unreachable` (I/O, timeout), `HttpError(code)`,
`BadResponse` (parse failure). The UI and the widget map each case to a
string resource. The worker retries only on `Unreachable`.

## 9. HA data and battery math

- **Entities in settings:** charge %, power, fallback power, current, total
  voltage, cells (list).
- **No config defaults:** URLs, token, entities, cell list and capacity all
  start empty. Until the URL, token and charge entity are set, sync returns
  `NotConfigured` and the widget and screen show a "set up in the app"
  state. An empty optional field hides its part of the widget (no cells →
  no cell block; no capacity → no time remaining).
- **Missing values** *(call 19)*: a value is missing when its entity is empty
  in settings, or HA reports it as `unknown`, `unavailable` or not a number.
  The template returns JSON `null` for it, never `0` (today's `float(0)`
  turns a missing power into "idle"). Charge is required: a missing charge
  gives `BadResponse`, and the last good reading stays. Any other missing
  value hides its line. A missing cell shows a dash and is left out of
  delta/min/max.
- **Request:** the template builder keeps today's safe quoting and the
  "fallback power if primary is unknown/unavailable" logic; it returns
  `{soc, power, current, total_v, cells[]}` as JSON. Parse with
  kotlinx.serialization.
- **Power sign** *(call 21)*: power > 0 means charging, for both power
  entities. If an entity uses the other sign, the owner fixes it in HA (a
  template sensor). The app has no "invert" setting (KISS).
- **State:**
  - `FULL` — charge rounds to 100 and (power ≥ −20 W or power missing)
  - `DISCHARGING` — power < −20 W
  - `CHARGING` — power > 20 W
  - `IDLE` — power within ±20 W
  - `UNKNOWN` — power missing and charge below 100
- **Delta / min / max:** from the cell list (1-based indices); hidden when
  fewer than 2 cells.
- **Units** stay as today: power in W, current in A, voltages in V; delta is
  shown in mV.
- **Time remaining:** the direction comes from the state (not from the sign of
  current), so the current's sign convention does not matter. To empty =
  `soc/100 × capacityAh / |current|`; to full =
  `(100 − soc)/100 × capacityAh / |current|`. Hidden when |current| < 0.5 A
  (both directions), when current is missing, when state is
  FULL/IDLE/UNKNOWN, or while the capacity setting is empty. Computed at render time from the cached reading (pure function);
  the widget already shows the reading's update time, so no extra staleness rule.

## 10. Secure token storage

- `KeystoreCrypto`: AES-256-GCM key in `AndroidKeyStore` (fixed alias, no
  user authentication, so the background worker can use it). Random IV per
  encryption; store `IV + ciphertext` as Base64.
- `TokenStore` uses `KeystoreCrypto` and its own typed DataStore file
  (`secure_settings`, holds only `schemaVersion` and `iv + ciphertext`),
  separate from normal settings.
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
  missing, the release build is unsigned; debug always works.
- **New signing key** (owner decision, 2026-11-14): the owner creates it with
  `keytool` outside the repo (the README gives the command) and puts its
  path, alias and passwords in `~/.gradle/gradle.properties`. Claude never
  sees or stores the passwords. The owner keeps a backup of the key: without
  it, an update needs an uninstall. The old `app/release.keystore` is not
  used; it stays on disk until the owner deletes it.
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

- Auto-refresh starts off and the interval field starts empty; auto-refresh
  can be turned on only with a valid interval *(call 18)*.
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
- Composition errors show our own small translated layout ("Widget error,
  tap to open the app"), passed as `GlanceAppWidget(errorUiLayout = …)`.
  Glance's default `onCompositionError` then shows it; we do not override
  that method *(call 16)*.
- Progress bar: Glance `LinearProgressIndicator`, colour by charge band
  *(call 12)*.

## 14. Stored data versioning (for releases after the first)

No migration from the old app (owner decision). The new app's own stored data
must be able to change safely after the first production release *(call 9)*:

- JSON settings: `ignoreUnknownKeys = true`, `encodeDefaults = true`, and a
  default value for every field. Adding or removing a field then needs no
  migration. These defaults are empty values only (`""`, empty list, `null`,
  `false`), never a real host, entity, interval or capacity.
- Every stored data class has `schemaVersion: Int`, starting at `1`. Every
  DataStore gets a `migrations` list (DataStore `DataMigration`), empty in v1.
- After the first release, a breaking change (rename, type or meaning change)
  bumps `schemaVersion` and adds a `DataMigration` with a test that loads a
  saved JSON sample of the old version.
- Before the first release, stored data may change freely (reinstall).

## 15. Testing and done

- **JVM unit tests:** `BatteryMath` (including missing values), `FullChargeRule`, `Validation`,
  `UrlPolicy`, `TemplateBuilder` (quoting), response parsing, `SyncBattery`
  with fakes, `WidgetLayoutSpec`, ViewModel with fakes, DataStore repositories
  with a temp file, `TokenStore` with a fake crypto, versioning (stored JSON
  has `schemaVersion: 1`; unknown fields are ignored).
- **HTTP tests:** OkHttp `MockWebServer` — success, 401, 500, timeout, bad
  JSON, fallback URL used after primary fails.
- **Widget UI tests:** `glance-appwidget-testing`
  (`runGlanceAppWidgetUnitTest`) for each size tier and for the empty and
  error states.
- **Device: an emulator on this machine** (owner decision). The builder
  uses the AVD `Pixel_8_API_37` (Android 17, Google APIs, x86_64; KVM works)
  and runs instrumented tests and manual checks there. `Pixel_6` (API 31,
  Android 12) is a second, older target. The SDK is shared with Android Studio
  (`~/Android/Sdk`), so the owner can open the same AVD from Device Manager.
  `targetSdk` stays 36; testing on a newer Android is fine.
  The owner reviews screenshots (en, uk, pseudo-locale, light/dark).
- **Instrumented tests** (on the emulator): `KeystoreCrypto` round trip;
  main screen smoke test.
- **Fresh install test** *(call 20)*: on the emulator with the mock HA. It
  needs the owner's new key (section 11); until the key exists, all other
  checks use the debug build. Uninstall the old app, install the new release
  build (signed with the new key), enter settings, place the widget, check ↻ and auto-refresh.
- **Mock HA for manual checks:** a small local mock of `/api/template` (builder
  picks the form) that can return any reading, e.g. 100% for the notification
  check. No real HA token is needed for development.
- **Manual check:** widget and screen in English, Ukrainian and a
  pseudo-locale; light/dark; ↻; notification at 100%.
- **Done when:** `./gradlew lint testDebugUnitTest assembleRelease` is green,
  instrumented tests pass, the manual check passes, no anti-goal from
  section 4 is present, and `CLAUDE.md` is updated: new architecture added
  (with the `agents-docs` skill), the "legacy code" and `bash ./gradlew`
  notes removed.

## 16. Calls made by Claude (owner, please check)

1. Approach A: rewrite from scratch.
2. Drop refresh faster than 15 min.
3. Charge state from power sign and charge %, not from sensor text.
4. Time remaining computed in the app from a capacity setting (starts empty);
   `sensor.battery_time_status` is no longer used.
5. Delta, min and max cell computed from cell voltages; their three entities
   are dropped.
6. Languages: English default + Ukrainian; per-app language only via Android
   13+ system settings.
7. Token: Keystore AES-GCM + DataStore; masked field, not pre-filled.
8. Plain HTTP only to local/private hosts.
9. Stored data versioned from v1 (`schemaVersion`, empty migration lists,
   tolerant JSON). No migration from the old app (owner decision).
10. Single module, manual DI.
11. OkHttp + kotlinx.serialization for HTTP and JSON.
12. Glance `LinearProgressIndicator` instead of a drawn bitmap.
13. Signing key: a new key outside the repo (owner, section 11).
14. Settled from the builder check: time direction from state, not current
    sign; units as today; "notified" flag in `ReadingCache`; "refreshing" as
    a timestamp with a 2-minute limit; time computed at render time; a mock HA
    for manual checks.
15. Final names for the app ID, receiver and launcher activity are chosen
    now and frozen from the first production release (references research).
    App ID and package: `io.lumensync.habattery` (owner).
16. Glance `errorUiLayout` instead of a custom `onCompositionError`.
17. Typed DataStore with a JSON serializer instead of key-value Preferences
    DataStore. Not chosen: Room (no relational data), Proto DataStore (needs
    a protobuf build plugin), Hilt/Koin, Retrofit, Ktor, WebSocket (needs an
    always-on connection), multi-module.
18. With no config defaults (owner): both switches start off, the interval
    starts empty, and auto-refresh needs a valid interval before it turns on.
19. Missing values come back as `null`, not `0`; missing charge is an error;
    missing power gives state `UNKNOWN` (or `FULL` at 100%), so the 100%
    alert still works without a power entity.
20. Settled from the second builder check: component names (section 5);
    the widget reads cache and settings, both local; `NotConfigured` is saved
    as an error and the sync ends normally; the fallback URL rule;
    `schemaVersion` in `secure_settings`; the fresh install test on the
    emulator with the mock HA.
21. Power sign: both power entities report power > 0 as charging; no
    invert setting (owner, please confirm, section 18).

## 17. Left to the builder

Exact class and file names (except the two in section 5); how to split the screen into section
composables; library versions (check with Context7 / official docs);
worker backoff values; `MeasureFormat` width; the form of the hard-coded-text
guard; colours and spacing within the current look.

## 18. Open questions (owner)

1. **App ID and package.** Decided (owner, 2026-11-14):
   `io.lumensync.habattery` for both the app ID and the Kotlin package. It
   replaces `com.homeassistant.batterywidget`, which used the Home Assistant
   project's domain. Frozen after the first release (section 5).
2. **Signing key.** Decided (owner, 2026-11-14): a new key outside the repo
   (section 11). The old key's file is in git history on GitHub, so it is
   not trusted.
3. **Power sign.** Please confirm that both power entities report power > 0
   as charging (call 21). If the fallback inverter sensor uses the other
   sign, fix it in HA with a template sensor.
