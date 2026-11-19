# Notification Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single full-charge notification with a bell on each 0010 colour level: entering a level whose bell is on posts a notification of the level's type, once per entry, re-armed after a margin.

**Architecture:** A pure evaluator in `domain` (`LevelAlerts`) takes the reading, the settings and the stored alert state and returns the alerts and the new state; `SyncBattery` runs it where `FullChargeRule` runs today and hands the alerts to a `LevelNotifier` port. `AndroidLevelNotifier` posts them on four channels (one per type). `Settings.notifyOnFull` becomes `notificationsOn` through a v1 → v2 settings migration.

**Tech Stack:** Kotlin, kotlinx.serialization (JSON DataStore, `DataMigration`), AndroidX Core notifications, Jetpack Compose (Material 3), Robolectric, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-18-0011-notification-rules-design.md` (read it before each task; §3.x below points into it). It builds on 0010: read `docs/superpowers/specs/2026-11-18-0010-value-colors-design.md` and its plan `docs/superpowers/plans/2026-11-18-0010-value-colors.md`; their types (`LevelValue`, `ColorLevel`, `DefaultLevels`, `LevelPicker`, `LevelDraft`, `ColorsSection`) exist when this plan starts.

**Amended 2026-11-20 (0010 changed before merge):** the "Custom colours" switch is gone; each value has Default and Edit (0010 §3.5). In Task 5 read "with custom on" as "in the editor", "the read-only default list" as "the tags", and `resetLevels` as the editor's Reset (draft only, nothing stored) plus `useDefaults(value)` (deletes own levels). Check the built names before Task 1.

## Global Constraints

- Start only after 0010 is merged into `epic-widget-improvements`. Branch `0011-notification-rules` from the epic; merge back after review.
- Gradle runs on JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
- `domain/` imports nothing from `android.*`.
- Stored data (0001 §14): the settings rename is a meaning change after a release, so `SettingsRecord.schemaVersion` goes 1 → 2 with a `DataMigration` and a test on a saved v1 sample. The cache change only adds and removes fields: `CacheRecord` stays at 1, no migration.
- Default bells (spec §3.1): on for SOC Low and Full, battery temperature Freezing and Hot, MOSFET Hot; off everywhere else ("Getting low" stays off).
- Re-arm margins (spec §3.3): SOC 2 %, voltage 0.1 V, current 0.05 C (1 A without a capacity), delta 3 mV, temperatures 2 °C. Editable per value in the value's editor (Edit, 0010 §3.5 as amended 2026-11-20): `CustomLevels.margin` (null = the default; amended 2026-11-19).
- Channels (spec §3.5): ids `level_critical`, `level_warning`, `level_normal`, `level_info`; Critical and Warning `IMPORTANCE_HIGH`, Normal and Info `IMPORTANCE_DEFAULT`; each created again before each post (0001 §12); the old `full_charge` channel is deleted.
- Every visible text goes in `res/values/strings.xml` and `res/values-uk/strings.xml` in the same commit (`NoHardcodedTextTest`).
- Code comments: use the `writing-code-comments` skill before writing them.
- Commits: `type(scope): Capitalized imperative summary` (≤50 chars, no period), header only, trailer `Co-Authored-By: <model> <noreply@anthropic.com>`.
- No instrumented run unless the owner asks; `androidTest` must compile. Never run connected tests or `adb uninstall` on the owner's phone.
- Done gate: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`.

## Review Focus

1. A settings file from v0.2.0 (`schemaVersion` 1, `"notifyOnFull": true`): it loads with notifications on and is written back as v2 → test in Task 2.
2. A charge jittering at an edge (21 → 19 → 20 → 19 → 20 %): exactly one notification ("Low ↓ 19 %") → test in Task 3.
3. The owner edits a level list between two syncs (a stored level id is gone): no false notification, the stale ids are dropped → test in Task 3.
4. The notification permission is denied on Android 13+ while the main switch is on: nothing is posted, no crash, the levels are still tracked → tests in Tasks 4 and 5.
5. A failed sync, or a reading without the value: nothing is evaluated for that value and its state stays → test in Task 4.

---

### Task 1: Bells on the levels

**Files:**
- Modify: `domain/model/ColorLevels.kt` (`ColorLevel`, `CustomLevels`), `domain/DefaultLevels.kt`
- Test: `test/.../domain/DefaultLevelsTest.kt`, `test/.../data/store/StoredJsonV1Test.kt`

**Interfaces:**
- Produces: `ColorLevel.notify: Boolean = false` (last field); `CustomLevels.margin: Double? = null` (last field; null = the value's default margin).

- [ ] **Step 1: Write the failing tests**
  - `DefaultLevelsTest.defaultBells`: for every `LevelValue` (current with capacity 100.0 and with null), the levels with `notify == true` are exactly: SOC `LOW`, `FULL`; BATTERY_TEMPERATURE `FREEZING`, `HOT`; MOSFET_TEMPERATURE `HOT` (compare `defaultName`s).
  - `DefaultLevelsTest.copyForCustom_keepsBells`: `copyForCustom(SOC, null).map { it.notify } == of(SOC, null).map { it.notify }`.
  - `StoredJsonV1Test.customLevels_v1`: the 0010 sample gets `"notify": false` on the first level and `"notify": true` on the second, and `"margin": 4.0` on the `CustomLevels`; `expected` matches. The same sample without `"margin"` decodes to `margin == null`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*DefaultLevelsTest" --tests "*StoredJsonV1Test"`
- [ ] **Step 3: Add the fields and set the default bells**
- [ ] **Step 4: Run the same tests, expect PASS**
- [ ] **Step 5: Commit** `feat(levels): Add bells and a margin to levels`

### Task 2: Main switch rename and settings migration

**Files:**
- Modify: `domain/model/Settings.kt`, `data/store/Records.kt`, `data/store/DataStores.kt`, `AppContainer.kt`, `domain/RefreshPolicy.kt`, `ui/MainViewModel.kt`, `ui/MainScreen.kt`, `MainActivity.kt`, `ui/sections/NotificationSection.kt` (parameter name only)
- Create: `app/src/main/kotlin/io/lumensync/habattery/data/store/SettingsMigrations.kt`
- Test: `StoredJsonV1Test.kt`, new `StoredJsonV2Test.kt`, `DataStoreSettingsRepositoryTest.kt`, `RefreshPolicyTest.kt`, `MainViewModelTest.kt`, `testing/Fakes.kt`

**Interfaces:**
- Produces:
  - `Settings.notificationsOn: Boolean = false`, annotated `@JsonNames("notifyOnFull")` (`@OptIn(ExperimentalSerializationApi::class)`), so a v1 file's key is read into it; the old name is gone from code.
  - `SettingsRecord(schemaVersion: Int = 2, …)`.
  - `object SettingsMigrationV2 : DataMigration<SettingsRecord>`: `shouldMigrate` when `schemaVersion < 2`; `migrate` returns `copy(schemaVersion = 2)` (the value itself was already read through `@JsonNames`).
  - `jsonDataStore(serializer, scope, migrations: List<DataMigration<T>> = emptyList(), file)`; `fun settingsDataStore(scope: CoroutineScope, file: () -> File): DataStore<SettingsRecord>` in `DataStores.kt`, with `SettingsMigrationV2`; `AppContainer` and the repository test both use it.
  - `MainViewModel.setNotificationsOn(enabled: Boolean)`; `MainActions.setNotificationsOn`; `configuredSettings(notificationsOn: Boolean = false)` in `Fakes.kt`.

- [ ] **Step 1: Write the failing tests**
  - `StoredJsonV1Test.settingsRecord_v1`: decode only (a v1 sample no longer writes back the same keys): the existing sample → `expected` with `schemaVersion = 1` and `notificationsOn = true`.
  - `StoredJsonV2Test.settingsRecord_v2`: the same sample with `"schemaVersion": 2` and `"notificationsOn": true` round-trips exactly (copy `assertV1` as `assertV2`).
  - `DataStoreSettingsRepositoryTest.v1File_migratesToV2` (Review Focus 1): write `{"schemaVersion":1,"settings":{"notifyOnFull":true}}`; `settings.first().notificationsOn == true`; after `update { it }` the file text contains `"schemaVersion":2` and `"notificationsOn":true` and not `notifyOnFull`.
  - Existing tests: rename `notifyOnFull` to `notificationsOn` (the JSON samples that test old files keep the old key).
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*StoredJson*" --tests "*DataStoreSettingsRepositoryTest"`
- [ ] **Step 3: Rename, add the migration and the store helper, wire it**
- [ ] **Step 4: Run all unit tests, expect PASS**: `./gradlew :app:testDebugUnitTest`
- [ ] **Step 5: Commit** `feat(settings): Migrate the main notification switch`

### Task 3: The level alert evaluator

**Files:**
- Create: `app/src/main/kotlin/io/lumensync/habattery/domain/LevelAlerts.kt`, `domain/model/LevelAlertState.kt`
- Test: `app/src/test/kotlin/io/lumensync/habattery/domain/LevelAlertsTest.kt`

**Interfaces:**
- Consumes: `LevelPicker.effective`, `pick`, `worst`, `roundTo`; `BatteryMath.fullCapacityAh`; `ColorLevel.notify`; `CustomLevels.margin`, `Settings.customLevels`.
- Produces:
  - `@Serializable data class LevelAlertState(val current: Int? = null, val disarmed: List<Int> = emptyList())`.
  - `data class LevelAlert(val value: LevelValue, val level: ColorLevel, val rising: Boolean, val shown: Double, val measuredAtMillis: Long)`.
  - `data class AlertOutcome(val alerts: List<LevelAlert>, val state: Map<String, LevelAlertState>)` (keyed by `LevelValue.name`).
  - `object LevelAlerts { fun evaluate(reading: BatteryReading, settings: Settings, enabled: Boolean, state: Map<String, LevelAlertState>): AlertOutcome; fun margin(value: LevelValue, capacityAh: Double?, custom: CustomLevels = CustomLevels()): Double }` (`custom.margin` when `custom.on` and it is not null, else the default; `evaluate` passes `settings.customLevels(value)`).
- Rules (spec §3.2, §3.3), per `LevelValue`:
  - The number: SOC whole %; voltage `roundTo(2)`; delta mV; temperatures whole degrees; battery temperature = the known sensor whose level ranks `worst`, first in settings order on a tie. Charge current = `abs(currentA).roundTo(1)` while charging (`currentA > BatteryMath.IDLE_CURRENT_A`), else 0.0; discharge current the other way round; `currentA == null` → no number. No number → the value's state is copied unchanged.
  - Order: drop disarmed ids that are not in the list; re-arm a disarmed level when `x <= from − margin` or `x >= upper + margin` (`upper` = the next level's `from`; open ends never re-arm on that side); pick the level; no stored `current`, or a stored `current` that is not in the list → store the pick, no alert; a pick different from `current` → if `enabled && pick.notify && pick.id !in disarmed`, add an alert (`rising` = the pick comes after `current` in the sorted list) and disarm the pick; store the pick.

- [ ] **Step 1: Write the failing tests** (default levels unless named; `reading(charge = …)` from `Fakes.kt`)
  - `firstCheck_storesOnly`: empty state, SOC 19 → no alert; `state["SOC"]?.current` is the LOW level's id.
  - `enteringLow_posts`: stored current = GETTING_LOW, SOC 19 → one alert, `level.defaultName == LOW`, `rising == false`, `shown == 19.0`; LOW disarmed.
  - `jitter_postsOnce` (Review Focus 2): SOC 21, 19, 20, 19, 20 in a row → alerts in total: one (LOW at the first 19).
  - `rearm_atMargin`: after LOW fired, SOC 21 → still disarmed; SOC 22 → re-armed; then SOC 19 → posts again.
  - `full_rising_evenWhileDischarging`: stored NORMAL, SOC 100 with `currentA = -5.0` → alert FULL, `rising == true`.
  - `disabled_tracksOnly`: `enabled = false`, stored GETTING_LOW, SOC 19 → no alert, current = LOW, nothing disarmed; then `enabled = true`, SOC 19 → no alert.
  - `bellOff_noAlert`: stored NORMAL, SOC 39 → no alert (GETTING_LOW has no bell).
  - `current_otherDirectionCountsZero`: custom DISCHARGE_CURRENT list with every bell on, `capacityAh = 100.0`; stored LIGHT; `currentA = -85.0` → alert VERY_HIGH, `shown == 85.0`; the CHARGE_CURRENT state picks its first level (0 A).
  - `current_unknown_keepsState`: `currentA = null` → both current states unchanged.
  - `margin_values`: `margin(SOC, null) == 2.0`, `margin(CHARGE_CURRENT, 100.0) == 5.0`, `margin(DISCHARGE_CURRENT, null) == 1.0`, `margin(DELTA, null) == 3.0`, `margin(MOSFET_TEMPERATURE, null) == 2.0`, `margin(VOLTAGE, null) == 0.1`.
  - `margin_custom`: `margin(SOC, null, CustomLevels(on = true, margin = 5.0)) == 5.0`; `on = false` with `margin = 5.0` → 2.0; `on = true` with `margin = null` → 2.0.
  - `rearm_customMargin`: custom SOC = `copyForCustom` with `margin = 5.0`; after LOW fired at 19, SOC 22 → still disarmed; SOC 25 → re-armed.
  - `batteryTemperature_worstSensor`: two sensors, stored WARM; temperatures `[25.0, 56.0]` → alert HOT, `shown == 56.0`; `[56.0, 60.0]` from WARM → `shown == 56.0` (first on a tie).
  - `editedList_storesOnly` (Review Focus 3): stored `current = 9` (no such id) with `disarmed = [9]`, SOC 19 → no alert, current = LOW, `disarmed` empty.
  - `missingDelta_keepsState`: `deltaCellVoltageV = null` → `state["DELTA"]` unchanged.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*LevelAlertsTest"`
- [ ] **Step 3: Implement `LevelAlertState`, `LevelAlert`, `AlertOutcome`, `LevelAlerts`**
- [ ] **Step 4: Run the same tests, expect PASS**
- [ ] **Step 5: Commit** `feat(alerts): Add the level alert evaluator`

### Task 4: Alerts in the sync

**Files:**
- Modify: `domain/SyncBattery.kt`, `domain/port/Ports.kt`, `domain/model/CacheState.kt`, `AppContainer.kt` (temporary no-op notifier until Task 5), `testing/Fakes.kt`
- Delete: `domain/FullChargeRule.kt`, `test/.../domain/FullChargeRuleTest.kt`
- Test: `SyncBatteryTest.kt`, `StoredJsonV1Test.kt` (cache sample)

**Interfaces:**
- Consumes: Task 3.
- Produces: `interface LevelNotifier { fun canPost(): Boolean; fun post(alert: LevelAlert) }` (replaces `FullChargeNotifier`); `CacheState.levelAlerts: Map<String, LevelAlertState> = emptyMap()` (replaces `fullChargeNotified`); `FakeLevelNotifier(canPost: Boolean = true)` recording `posted: List<LevelAlert>`.
- `SyncBattery`: on success, `LevelAlerts.evaluate(reading, settings, enabled = settings.notificationsOn && notifier.canPost(), state.levelAlerts)`; store the new state with the reading; after the cache write, `post` each alert in order. On failure nothing is evaluated.

- [ ] **Step 1: Write the failing tests** (`SyncBatteryTest`; replace the full-charge tests)
  - `enteringLow_postsOnce`: cache with `levelAlerts = mapOf("SOC" to LevelAlertState(current = <GETTING_LOW id>))`, `notificationsOn = true`, reading SOC 19 → `notifier.posted.size == 1`; a second sync with SOC 19 → still 1.
  - `noPermission_tracksOnly` (Review Focus 4): `FakeLevelNotifier(canPost = false)` → nothing posted; the cache's SOC state is LOW.
  - `failure_keepsAlertState` (Review Focus 5): a failing source → `levelAlerts` unchanged, nothing posted.
  - `StoredJsonV1Test.cacheRecord_v1`: the sample drops `"fullChargeNotified"` and gets `"levelAlerts": {"SOC": {"current": 2, "disarmed": [1]}}`; `removedKeys_areIgnoredOnRead` also reads a cache JSON with `"fullChargeNotified": true`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*SyncBatteryTest" --tests "*StoredJsonV1Test"`
- [ ] **Step 3: Implement; delete `FullChargeRule`**
- [ ] **Step 4: Run all unit tests, expect PASS**
- [ ] **Step 5: Commit** `feat(alerts): Check level alerts on each sync`

### Task 5: Android notifier

**Files:**
- Create: `system/AndroidLevelNotifier.kt`; `res/drawable/ic_notify_critical.xml` (`report`), `ic_notify_warning.xml` (`warning`), `ic_notify_normal.xml` (`check_circle`), `ic_notify_info.xml` (`info`): Material Symbols Rounded, made the same way as the 0010 level icons (0010 plan, Task 5), header comment naming the icon and Apache-2.0
- Modify: `AppContainer.kt`, strings EN/UK (remove `notification_channel_full`, `notification_full_title`, `notification_full_text`)
- Delete: `system/AndroidFullChargeNotifier.kt`, `res/drawable/ic_battery_full_notification.xml` (if nothing else uses it)
- Test: `app/src/test/kotlin/io/lumensync/habattery/system/AndroidLevelNotifierTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `LevelNotifier`, `LevelAlert`; `Formatters`; the 0010 name strings.
- Produces: `class AndroidLevelNotifier(context: Context) : LevelNotifier`; `@StringRes fun levelNameRes(name: LevelName): Int` in `format/LevelLabels.kt` (move it here if 0010 left it private in `ColorsSection`) and `@StringRes fun notificationValueRes(value: LevelValue): Int`.
- Behaviour (spec §3.4, §3.5):
  - `canPost()`: notifications enabled for the app and, on Android 13+, `POST_NOTIFICATIONS` granted.
  - `post`: delete channel `full_charge`; create the type's channel again; notification id `100 + value.ordinal` (one per value); small icon by type; `setColor` = `WidgetPalette.Light.shades(level.type.defaultColor).ring`; title from `notification_level_title_down` / `_up` with the value label, the name (`level.name` or the default name's string), the number formatted as the widget does (SOC `charge_percent`, `Formatters.voltage`, `current`, `deltaMv`, `temperature`); text `notification_measured_at` with `Formatters.updatedAt(measuredAtMillis)`; tap opens the app; auto-cancel; `CATEGORY_STATUS`; a `SecurityException` on post is swallowed, as today.
- Strings (EN / UK): value labels Charge, Voltage, Charge current, Discharge current, Cell delta, Battery temperature, MOSFET temperature / Заряд, Напруга, Струм заряду, Струм розряду, Різниця комірок, Температура батареї, Температура MOSFET; `notification_level_title_down` "%1$s: %2$s ↓ %3$s", `_up` "%1$s: %2$s ↑ %3$s" (same in UK); `notification_measured_at` "Measured at %1$s" / "Виміряно о %1$s"; channels Critical, Warning, Normal, Info / Критичні, Попередження, Звичайні, Інформація.

- [ ] **Step 1: Write the failing tests** (`AndroidLevelNotifierTest`, permission granted through `ShadowApplication.grantPermissions`)
  - `post_critical`: alert SOC LOW, `rising = false`, `shown = 19.0` → one notification, id `100 + LevelValue.SOC.ordinal`, channel `level_critical`, title `"Charge: Low ↓ 19 %"` (match the `charge_percent` format), channel importance HIGH.
  - `post_info_quiet`: alert SOC FULL → channel `level_info` with importance DEFAULT, title contains "↑".
  - `sameValue_replaces`: two SOC alerts → one SOC notification left; a DELTA alert → two notifications.
  - `oldChannel_deleted`: a pre-created `full_charge` channel is gone after a post.
  - `canPost_false_withoutPermission` (Review Focus 4): permission denied → `canPost() == false`.
  - `customName_wins`: a level with `name = "Drift"` → title starts with "Cell delta: Drift".
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*AndroidLevelNotifierTest"`
- [ ] **Step 3: Implement, wire it in `AppContainer`, delete the old notifier**
- [ ] **Step 4: Run all unit tests, expect PASS**
- [ ] **Step 5: Commit** `feat(alerts): Post level notifications`

### Task 6: Bells and the Notifications section in the app

**Files:**
- Modify: `ui/sections/NotificationSection.kt`, `ui/sections/ColorsSection.kt`, `ui/LevelDraft.kt`, `ui/MainViewModel.kt` (`saveLevels` keeps `notify` and saves the margin), `ui/MainScreen.kt` (`MainActions.saveLevels`), `domain/Validation.kt` (`Field.LEVEL_MARGIN`); strings EN/UK; `res/drawable/ic_bell.xml` (`notifications`), `ic_bell_off.xml` (`notifications_off`), Material Symbols Rounded as above
- Test: `MainViewModelTest.kt`; `NoHardcodedTextTest`; the done gate

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `LevelDraft.notify: Boolean`; `saveLevels(value: LevelValue, drafts: List<LevelDraft>, margin: String, locale: Locale)` (and the same in `MainActions`): blank → `margin = null`; else `parseDecimal(margin, locale)` rounded to `value.decimals` (current: whole amps); unparsable or < 0 → `FieldError(Field.LEVEL_MARGIN, Problem.INVALID)` and nothing is stored; `resetLevels` sets `margin = null`. `NotificationSection(enabled: Boolean, autoRefresh: Boolean, onEnabledChange: (Boolean) -> Unit, onOpenSettings: () -> Unit, …)`.
- Behaviour (spec §3.7): the switch label "Notifications"; under it the hint; when `autoRefresh == false`, the note; the permission flow and the "On / Off" summary stay as today. In the Colours editor each row gets a bell toggle (`ic_bell` / `ic_bell_off`, with a content description); the read-only default list shows `ic_bell` on the levels that notify. Under the rows, with custom on, one margin field with the value's unit; the default margin is its hint; its error under the field.
- Strings (EN / UK): `notifications_switch` "Notifications" / "Сповіщення"; `notifications_hint` "Choose the levels with the bells in Colours." / "Рівні для сповіщень обирайте дзвіночками в розділі «Кольори»."; `notifications_no_auto_refresh` "Auto refresh is off, so no notifications can come." / "Автооновлення вимкнено, тому сповіщення не надходитимуть."; `level_bell` "Notify about %1$s" / "Сповіщати про «%1$s»"; `level_margin` "Notify again after it moves back by" / "Сповіщати знову, коли значення відійде на"; `level_margin_default` "Default: %1$s" / "Типово: %1$s". Remove the old switch string if nothing else uses it.

- [ ] **Step 1: Write the failing tests** `MainViewModelTest.saveLevels_keepsBells`: drafts with `notify = true` on the second row → the stored level has `notify == true`. `saveLevels_margin`: SOC with margin `"4"` → `margin == 4.0`; `""` → `null`; `"-1"` → `FieldError(LEVEL_MARGIN, INVALID)` in `uiState.errors`, stored settings unchanged; Ukrainian `"0,2"` for VOLTAGE → 0.2. `resetLevels_clearsMargin`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*MainViewModelTest"`
- [ ] **Step 3: Implement the draft field, the bell toggles and the section texts**
- [ ] **Step 4: Run the done gate, expect PASS**: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`
- [ ] **Step 5: Ask the owner** to try it on the phone: main switch on, a bell on a level just next to the current value (for example "Getting low" while near 40 %), auto refresh on; check the title, the body time, the type's sound and pop-up, and that one value keeps one notification; set a bigger margin and check that the jitter posts nothing more.
- [ ] **Step 6: Commit** `feat(ui): Add bells, margins and the switch`
