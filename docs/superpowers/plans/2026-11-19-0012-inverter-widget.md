# Inverter Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second home-screen widget, the inverter strip (3×1, 4×1, 5×1: mode, grid input voltage, output voltage), fed by the same sync in two independent parts, with its own tab in the app.

**Architecture:** The settings get an `inverter` block, and the cache gets an `InverterReading` with its own error. `BatterySource.fetch` takes a two-part request and returns a two-part result, so a failed battery part never stops the inverter part. 0010 levels and 0011 bells cover the two voltages; a small `ModeAlerts` evaluator posts mode changes. A new Glance widget (`InverterWidget`) draws an `InverterWidgetModel`. The app moves its sections into a bottom navigation bar: Battery | Inverter | Common.

**Tech Stack:** Kotlin, Jetpack Glance (`SizeMode.Responsive`), Jetpack Compose Material 3 (`NavigationBar`), kotlinx.serialization, DataStore, OkHttp, JUnit, Robolectric, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-11-18-0012-inverter-widget-design.md` (design: `docs/design/README.md`, section "Inverter strip"; `inverter-*.png`).

**Amended 2026-11-21 (0011 as built):**

- `LevelAlert` has no `rising` field: the arrow is the level's side; it carries `span: LevelSpan`. Read Task 5's `rising == false` as `level.side == LevelSide.BELOW`.
- `LevelAlerts.margin(value, basis: LevelBasis, custom)` takes a `LevelBasis`, not a capacity: read `margin(GRID_VOLTAGE, null)` as `margin(GRID_VOLTAGE, LevelBasis(null, null))`.
- There is no `notification_measured_at`: the 0011 body is `notification_level_text` "%1$s · measured at %2$s" (number first). The mode notification has no number, so Task 6 adds its own string, e.g. `notification_mode_text` "Measured at %1$s" / "Виміряно о %1$s".
- The number a level is picked by comes from `ShownNumber` (domain): see Task 5 for the whole-volt rounding, so the widget colour and the alert agree.
- All four level channels are created on each post (0011 §7); `postMode` keeps that.
- The section "Colours" is now "Levels and alerts" (`ColorsSection` keeps its name); read "Colours" in Tasks 11–12 as that section.
- The margin field is "Hysteresis" with an ⓘ tip. The voltage default is 0.025 V × cells, rounded to 0.01 V, and 0.4 V without a cell count (`domain/LevelAlerts.kt:103-108`). The inverter voltages keep their own 5 V default.

The readiness check (2026-11-21) confirmed the other names. Fixes by task (paths are under `app/src/main/kotlin/io/lumensync/habattery/` unless they start with `app/` or `docs/`):

- **Global**
  - Add `LevelSide`, `LevelBasis`, `LevelSpan`, `LevelSpans` and `ShownNumber` to the "Before Task 1" name check.
  - Default voltage levels are two-sided (`side` + `edge`, no `from`; `domain/model/ColorLevels.kt:87-100`). Ids 1–4 in shown order: NO_GRID/NO_POWER `BELOW 99`, LOW `BELOW 206`, NORMAL `MIDDLE`, HIGH `ABOVE 254`. Read every "from 100 / 207 / 254" in Tasks 5 and 7 this way (Open question 1).
  - Append-only: add one line in the `LevelValue` KDoc, "append only; ordinals are notification ids" (GRID 107, OUTPUT 108, mode 150).
- **Task 1**
  - `settingsRecord_v2` is a round trip (`data/store/StoredJsonV2Test.kt:22-28`) and `StoreJson.encodeDefaults = true`: add an `"inverter"` block with non-default values to its sample and `expected`. Test "no `inverter` key → defaults" in a separate decode-only test.
  - Write the `IconSlot` defaults qualified: `MODE_STANDBY(InverterIcon.MODE_STANDBY)`, for every slot. A bare name resolves to the enum's own entry.
- **Task 3**
  - Keep `SyncResult` through Task 3 (`SyncBattery` maps the battery part back to it) and remove it in Task 4. It is still used by `system/SyncWorker.kt:23`, `SyncWorkerTest.kt` and `domain/SyncBattery.kt:28`.
  - Add `test/domain/SyncBatteryTest.kt` and `test/ui/MainViewModelTest.kt` to the files: both build `SyncResult` fakes (`MainViewModelTest.kt:80, 395-409`). Add a helper `batteryOk(r) = FetchResult(PartResult.Ok(r), null)` in `test/testing/Fakes.kt`.
  - `FakeBatterySource` also records `lastRequest: FetchRequest?` (it has `calls` and `lastToken` only, `Fakes.kt:73-95`).
  - With no battery part, write `"temps": []` and `none` for the scalar keys (`data/ha/TemplateBuilder.kt:17`).
- **Task 4**
  - `cacheRecord_v1` is a round trip (`test/data/store/StoredJsonV1Test.kt:279-331`): add `inverterReading` and `inverterError` (Task 6 adds `inverterMode`) with real values to its sample and `expected`. The "old sample → null" case is decode-only.
  - Drop `ui/MainViewModel.kt` from the files: `refreshNow` ignores the sync result (`MainViewModel.kt:81-91`).
  - `FetchResult` keeps `null` = not asked. `SyncBattery.invoke()` returns `Failed(NotConfigured)` for each part it skips as not configured (see `SyncBatteryTest.kt:53-60`).
  - Alert state: feed the inverter evaluator the battery outcome's map (`batteryOutcome?.state ?: state.levelAlerts`) and store the inverter result. `evaluate` copies the whole map (`LevelAlerts.kt:52, 74-76`), so starting both from `state.levelAlerts` loses the battery state and repeats a notification. Add a `SyncBatteryTest` where both parts post and both states are kept.
  - The token-type hint fires on `Unauthorized` in either part (`ui/MainUiState.kt:25-28` reads only the battery error today). Do it here or in Task 11.
- **Task 5**
  - Add to the files, or it does not compile: `domain/ShownNumber.kt`, `ui/sections/ColorsSection.kt`, `ui/sections/LevelEditor.kt`, `system/AndroidLevelNotifier.kt`, `format/Formatters.kt`, `ShownNumberTest.kt`. They hold exhaustive `when (value)` (`ShownNumber.kt:22-33`, `ColorsSection.kt:323-333`, `LevelEditor.kt:580-589`, `AndroidLevelNotifier.kt:104-113`).
  - `GRID_VOLTAGE(0, false, 0.0)` and `OUTPUT_VOLTAGE(0, false, 0.0)`, appended last (`LevelValue(decimals, hasIcons, floor)`, `ColorLevels.kt:72-80`).
  - `ShownNumber.of(value, reading: BatteryReading, levels)` returns `null` for the two inverter values. Add `ShownNumber.of(value, reading: InverterReading): Double?` = the voltage `roundTo(0)`. `evaluateInverter` and `InverterWidgetModel` (number and colour) both use it. Do not use `roundToInt()` (half up): 206.5 V would show "207 V" in the Low colour.
  - Add `Formatters.wholeVolts(volts: Double)` (`unit_volts`, 0 decimals; half-even like `roundTo(0)`), used by `shownText` here and the strip in Task 9. `Formatters.voltage` shows 2 decimals.
  - `LevelValue` companion gets `BATTERY` (the 7 old values) and `INVERTER` (the 2 new ones). `evaluate` loops `BATTERY` (now `LevelValue.entries`, `LevelAlerts.kt:53`), `evaluateInverter` loops `INVERTER`, both through one private per-value step.
  - `ColorsSection` gets `values: List<LevelValue>` (named, before `modifier`). It filters the own-levels map, the summary and the rows (loops at lines 90, 93, 102). `MainScreen` passes `LevelValue.BATTERY` until Task 12 adds the Inverter tab with `INVERTER`. Without it the battery "Levels and alerts" shows "Grid input" and "Output" from this task on.
  - `DefaultLevelsTest.defaultBells` (`DefaultLevelsTest.kt:212-225`) checks every value against an exact map: add `LevelValue.GRID_VOLTAGE to listOf(LevelName.LOW)`. Update the bell list in the `DefaultLevels` KDoc (`DefaultLevels.kt:12-15`).
  - `inverterDefaults`: assert sides `[BELOW, BELOW, MIDDLE, ABOVE]` and edges `[99.0, 206.0, null, 254.0]`.
  - `grid_rearm5V`: after LOW fired at 205, 210 → still disarmed, 211 → re-armed; on the low side 95 → disarmed, 94 → re-armed (re-arm is `x >= upper + margin` or `x <= lower − margin`, LOW's upper bound is its own edge, `LevelSpans.kt:48-59`, `LevelAlerts.kt:128-140`).
- **Task 6**
  - Extract the channel step (delete `full_charge`, create all four) from `post` into one private function used by `post` and `postMode` (`AndroidLevelNotifier.kt:58-70, 158-162`). Add `MODE_NOTIFICATION_ID = 150` next to `BASE_ID`.
  - Add `@StringRes fun modeWordRes(mode: InverterMode)` in `format/InverterLabels.kt`. Task 9 reuses it for the strip.
- **Task 7**
  - `LevelPicker.effective(value, settings, basis: LevelBasis)` needs a basis: pass `LevelBasis(null, null)` (`LevelPicker.kt:47-48`).
  - `InverterWidgetModel.Empty(kind, error)`, not `Empty(kind)`; `NO_DATA` carries `cache.inverterError`, like `WidgetModel.Empty` (`WidgetModel.kt:45, 77`).
  - Null volts → "—" in PLAIN, not in the first level's colour (the battery widget does this, `WidgetModel.kt:84-88`; Open question 2). A red "—" would look like "no power".
  - The number and the colour come from the same `ShownNumber` value (see Task 5).
- **Task 9**
  - `InverterWidgetContent` takes `colors: WidgetColors`, not `look: WidgetLook`. `WidgetLook` is `internal` and takes a battery `LayoutSpec` (`widget/WidgetContent.kt:107-111`): build an inverter look inside, and move `sp()` out of `WidgetLook` or give `WidgetLook` the spec as a type parameter.
  - Make `TappedWidgetKey` internal (`widget/BatteryWidget.kt:95`) and move `reapplySchedule` (`widget/BatteryWidgetReceiver.kt:24`) into a shared internal helper, for the inverter receiver's `onEnabled`/`onDisabled`.
  - `PreviewWidget.kt` becomes a content lambda plus a bucket `DpSize` (`PreviewWidget(model: WidgetModel, …)` and `views(context, layout: LayoutSpec)` are battery-only today). Both previews keep the one `PREVIEW_ID = -2` and the one `drawing` mutex (`PreviewWidget.kt:26-31, 47, 59-62`); a second id or mutex can fail the older Glance session.
  - Copy `initialLayout="@layout/glance_default_loading_layout"`, `updatePeriodMillis="0"` and `widgetCategory="home_screen"` from `app/src/main/res/xml/battery_widget_info.xml` to `inverter_widget_info.xml`.
  - The strip uses `Formatters.wholeVolts` and `modeWordRes`.
- **Task 11**
  - Home-screen scale: "Reset sizes" resets fonts and scale today (`ui/MainViewModel.kt:225-229`); `Settings.textSizeChanged` counts the scale (`Settings.kt:34-37`); slider and reset sit in `ui/sections/WidgetSection.kt:36-66`. After the move, Battery "Reset sizes" resets the battery fonts only, Common gets its own scale reset, and the scale leaves `textSizeChanged` (Open question 3).
  - `previewRequest` is set in `onCreate` from widget sizes, before settings load (`MainActivity.kt:74, 131-148`), and `PreviewTab.forSize` maps any size to a battery layout. Compute `PreviewTab` only for a battery provider (an inverter tap picks the strip's size tab). Apply `AppTabs.first` in `MainScreen` once the first state arrives, because it needs settings and token. `onNewIntent` (`MainActivity.kt:95-100`) sends a counted app-tab request, as `PreviewRequest` does.
  - `setupNeeded` is not "no URL or no token": `BatteryOverview.setupNeeded` = `!isConfigured` incl. the charge entity (`domain/BatteryOverview.kt:33`) and drives `initiallyExpanded` of Connection, Token and Entities (`ui/MainScreen.kt:140, 150, 157`). Add `MainUiState.connectionMissing` (URL blank or no token) for `AppTabs.first` and for Connection/Token `initiallyExpanded`; battery Entities keeps the battery rule.
  - "Add to home screen": `MainActions.requestPinWidget` is `() -> Unit` for the battery receiver only (`ui/MainScreen.kt:68`, `MainActivity.kt:174-179`). Make it `requestPinWidget(receiver: Class<out GlanceAppWidgetReceiver>)` (or two actions), with one hint per widget ("adds it as 3×1" for the strip). Say whether `action_add_widget` "Add widget" becomes "Add to home screen".
  - Tabs drop saved UI state (`rememberSaveable` folds in `SectionParts.kt:59`, editor state in `ColorsSection.kt:99-108`, the size tab in `WidgetPreviewCard.kt:68-69`, scroll). Use `rememberSaveableStateHolder()` with `SaveableStateProvider(tab)` so each tab keeps its own scroll (spec §3.2). Test it in `MainScreenTest` (compile only).
  - Update `app/src/androidTest/.../ui/MainScreenTest.kt`: `widgetSection_isFirstAfterPreviewAndActions` (176-185) asserts the flat order, and other tests scroll to Connection/Entities/Capacity/Reset sizes on one page. Tap the tab first; add an order test per tab. Keep `WidgetPreviewCard`'s signature, or update `WidgetPreviewTest` (`…/ui/WidgetPreviewTest.kt:88, 212`). Only compilation is gated, so a runtime break stays hidden until an instrumented run.
- **Task 12**
  - Drop `MainViewModel.fillInverterFromPrefix` and its test `fillInverterFromPrefix_noRequest`. `InverterEntitiesSection` calls `EntityFill.inverterFromPrefix(draft.prefix, draft)`, as battery Fill does (`EntitiesSection.kt:124`); `EntityFillTest` covers it.
  - Add `inverter: InverterWidgetModel?` to `MainUiState` (`ui/MainUiState.kt:11-21`), built in `MainViewModel.uiState`, for the preview card. Add the file to the task.
  - Make `ChoiceChip` internal (`LevelEditor.kt:530, 559`; `IconChips` and `ChoiceChip` are `private` and typed to `LevelIcon`) and build the 45-icon grid from it.
  - Add 10 `inv_slot_*` strings EN/UK and `iconSlotRes(slot)` in `format/` for the Icons section rows.
  - `ColorsSection(values = LevelValue.INVERTER)` uses the parameter from Task 5.
  - Clear `MainUiState.errors` on a tab switch: it is one list (`MainViewModel.kt:58`), so `LEVEL_EDGE` / `LEVEL_MARGIN` errors from one tab's editor show in an editor open in the other tab.

**Open — ask the owner before the named task:**

1. Before Task 5: default edges for grid input and output. Option A: ↓99 No grid, ↓206 Low, ↕207–253 Normal, ↑254 High (keeps the spec ranges and EN 50160's 207–253 V as normal). Option B: ↓100 / ↓207 ("N is N and below", as SOC in 0010; 207 V becomes Low and the default bell rings there). Recommendation: A.
2. Before Task 7: a missing voltage on the strip ("—"): plain, like the battery widget, or the first level's colour (blue for grid, red for output)? Recommendation: plain.
3. Before Task 11: should the Battery tab's "Reset sizes" still reset the shared home-screen scale? Recommendation: no; Common gets its own scale reset.
4. Before Task 6: the mode-change notification follows the main Notifications switch (default off), with no own switch, as the plan reads spec §3.5 "on by default; not configurable". Is that right, and should the Notifications hint also mention mode changes? Recommendation: yes to the first; add the mention.

## Global Constraints

- Start only after 0011 is merged into `epic-widget-improvements`. Branch `0012-inverter-widget` from the epic; merge back after review.
- **Before Task 1:** open the built code and check every name this plan takes from the 0009–0011 plans (`TokenType`, `StatesSource`, `TokenTypeSource`, `HaHttp`, `LevelValue`, `LevelName`, `LevelType`, `LevelColor`, `ColorLevel`, `CustomLevels`, `DefaultLevels`, `LevelPicker`, `LevelAlerts`, `LevelAlertState`, `LevelNotifier`, `AndroidLevelNotifier`, `ColorsSection`, `WidgetPalette.shades`, `CacheState.levelAlerts`, `Settings.notificationsOn`). If a build renamed one, use the built name and note it in the task's commit.
- Gradle runs on JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
- `domain/` imports nothing from `android.*`.
- Class names are fixed once shipped: `InverterWidgetReceiver` (home screens store it); `MainActivity`, `BatteryWidgetReceiver` stay.
- Stored data (0001 §14): no migration. `Settings.inverter`, the new `CacheState` fields and the new `LevelValue` keys are added with defaults; old files read fine. Defaults: entity ids and prefix `""`; mode style, icon choices and fonts default to the design values (display-choice exception).
- Entity ids from the prefix `p`: `sensor.<p>_operation_mode`, `sensor.<p>_main_voltage`, `sensor.<p>_output_voltage`.
- Mode table (spec §3.3), matched after `trim()`, ignoring case:

  | ESP32 text | Word EN / UK | Colour | Default icon | Type (channel) |
  |---|---|---|---|---|
  | `Power On` | power on / запуск | GREY | `POWER_SETTINGS_NEW` | INFO |
  | `Standby` | standby / пауза | TEAL | `MODE_STANDBY` | WARNING |
  | `Mains` | main / мережа | BLUE | `TOWER` | INFO |
  | `Off-Grid` | battery / батарея | GREEN | `BATTERY_CHARGING_FULL` | WARNING |
  | `Bypass` | bypass / байпас | VIOLET | `ALT_ROUTE` | INFO |
  | `Charging` | charging / заряд | AMBER | `CHARGER` | INFO |
  | `Fault` | fault / аварія | RED | `ERROR` | CRITICAL |
  | any other text | the HA text as it is | GREY | `HELP` | INFO |

  `unavailable`, `unknown` and blank are "no value", never a mode.
- Default voltage levels (spec §3.4), whole volts, edges "from":

  | Level | From | Grid input | Output |
  |---|---|---|---|
  | `NO_GRID` / `NO_POWER` | — (first) | INFO | CRITICAL |
  | `LOW` | 100 | WARNING, bell on (grid only) | WARNING |
  | `NORMAL` | 207 | NORMAL | NORMAL |
  | `HIGH` | 254 | WARNING | WARNING |

  Re-arm margin 5 V for both, editable (0011 §3.3 as amended).
- Strip design numbers: `docs/design/README.md`, "Strip layout" (cards 194/267/340 × 59 dp, buckets 6 dp smaller: 188/261/334 × 53).
- Icons: Material Symbols Rounded and Material Design Icons, both Apache-2.0; each drawable starts with a comment naming its source icon and the licence (as the 0010 level icons).
- Every visible text goes in `res/values/strings.xml` and `res/values-uk/strings.xml` in the same commit (`NoHardcodedTextTest`).
- Code comments: use the `writing-code-comments` skill before writing them.
- Commits: `type(scope): Capitalized imperative summary` (≤ 50 chars, no period), header only, trailer `Co-Authored-By: <model> <noreply@anthropic.com>`.
- No instrumented run unless the owner asks; `androidTest` must compile. Never run connected tests or `adb uninstall` on the owner's phone; emulator only with `ANDROID_SERIAL=emulator-5554`.
- Done gate per task with UI or resources: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`.

## Review Focus

1. The BMS link drops (charge `unavailable`) while the inverter is fine: the strip updates, the mode notification still posts, only the battery widget shows ⚠ (Task 4: `batteryFails_inverterStillSaved`; Task 6: `batteryFails_modeStillPosts`).
2. The ESP32 is offline (all three inverter states `unavailable`): the inverter part fails with `BadResponse`, the strip keeps its last values with ⚠, and no mode notification is posted (Task 3: `inverterAllUnavailable_isBadResponse`; Task 6: `unavailable_isNotAChange`).
3. Mode text in another case or with spaces (`" off-grid "`) matches its row; an unknown text (`"Eco"`) shows the "other" icon, grey, and the text as it is (Task 2: `of_ignoresCaseAndSpaces`; Task 7: `unknownMode_otherSlot`).
4. A stored icon name the app does not know (a later version, a typo): the slot's default icon, no crash (Task 1: `unknownIconName_fallsBack`).
5. Only the inverter is set up (battery entities blank): the sync runs, the battery widget says "set up", the strip works, and the worker does not retry (Task 4: `onlyInverterConfigured_fetchesInverterOnly`).

---

### Task 1: Inverter settings

**Files:**
- Modify: `domain/model/Settings.kt`, `domain/EntityFill.kt`, `domain/Validation.kt` (and every caller of `isConfigured`)
- Create: `domain/model/InverterSettings.kt`
- Test: `test/.../domain/EntityFillTest.kt`, `ValidationTest.kt`, `test/.../data/store/StoredJsonV2Test.kt` (or the newest stored-JSON test after 0011), create `test/.../domain/InverterSettingsTest.kt`

**Interfaces:**
- Produces (in `InverterSettings.kt`):
  - `@Serializable data class InverterEntitySettings(val prefix: String = "", val mode: String = "", val gridVoltage: String = "", val outputVoltage: String = "")`.
  - `@Serializable enum class ModeStyle { BIG_ICON, ICON_WORD }`.
  - `enum class InverterIcon` with the 45 icons of spec §3.3 in that order: `TOWER, LOCATION_CITY, ELECTRICAL_SERVICES, POWER_INPUT, ELECTRIC_METER, FACTORY, APARTMENT, OFFLINE_BOLT, BOLT, ELECTRIC_BOLT, BATTERY_CHARGING_FULL, BATTERY_FULL, BATTERY_5_BAR, BATTERY_ANDROID_FULL, BATTERY_HORIZ_075, ENERGY_SAVINGS_LEAF, NEST_ECO_LEAF, ENERGY_PROGRAM_SAVING, SOLAR_POWER, HELP, QUESTION_MARK, DEVICE_UNKNOWN, PENDING, INFO, POWER, HOME, HOUSE, COTTAGE, HOME_WORK, HOME_LIGHTNING_BOLT, OUTLET, POWER_SETTINGS_NEW, RESTART_ALT, MODE_STANDBY, BEDTIME, ALT_ROUTE, CALL_SPLIT, SWAP_HORIZ, CHARGER, BATTERY_CHARGING_90, EV_CHARGER, ERROR, REPORT, WARNING, DANGEROUS`.
  - `enum class IconSlot(val default: InverterIcon) { MODE_POWER_ON(POWER_SETTINGS_NEW), MODE_STANDBY(MODE_STANDBY), MODE_MAINS(TOWER), MODE_OFF_GRID(BATTERY_CHARGING_FULL), MODE_BYPASS(ALT_ROUTE), MODE_CHARGING(CHARGER), MODE_FAULT(ERROR), MODE_OTHER(HELP), GRID_VOLTAGE(TOWER), OUTPUT_VOLTAGE(HOME) }`.
  - `@Serializable data class InverterFontPercents(val word: Int = FontPercents.DEFAULT, val value: Int = FontPercents.DEFAULT)` with `clamped()` (same range and step as `FontPercents`).
  - `@Serializable data class InverterSettings(val entities: InverterEntitySettings = InverterEntitySettings(), val modeStyle: ModeStyle = ModeStyle.BIG_ICON, val icons: Map<String, String> = emptyMap(), val fontPercents: InverterFontPercents = InverterFontPercents())`; `fun InverterSettings.icon(slot: IconSlot): InverterIcon` (stored name → enum, unknown or missing → `slot.default`); `fun InverterSettings.withIcon(slot: IconSlot, icon: InverterIcon): InverterSettings`. String keys and values, so an unknown name from a later version cannot break the file (as 0010's `colorLevels`).
  - `Settings.inverter: InverterSettings = InverterSettings()`; `Settings.clamped()` also clamps the inverter fonts.
  - `EntityFill.inverterFromPrefix(prefix: String, current: InverterEntitySettings): InverterEntitySettings` (trim, drop a trailing `_`, fill the three ids).
  - `Field.INVERTER_PREFIX, INVERTER_MODE, INVERTER_GRID, INVERTER_OUTPUT`; `Validation.inverterEntities(e: InverterEntitySettings): List<FieldError>`: a bad prefix or id → INVALID; all three ids blank → no error; some set and some blank → REQUIRED on each blank one.
  - `Validation.isConfigured` is renamed `isBatteryConfigured` (same rule); new `Validation.isInverterConfigured(s: Settings, hasToken: Boolean)` = token, URL and all three inverter ids set.

- [ ] **Step 1: Write the failing tests**
  - `InverterSettingsTest.defaults`: `InverterSettings().modeStyle == BIG_ICON`; `icon(slot) == slot.default` for every slot; fonts 50/50.
  - `InverterSettingsTest.unknownIconName_fallsBack` (Review Focus 4): `icons = mapOf("MODE_MAINS" to "NOT_AN_ICON", "NO_SUCH_SLOT" to "HOME")` → `icon(MODE_MAINS) == TOWER`; no exception.
  - `InverterSettingsTest.withIcon_roundTrips`: `withIcon(GRID_VOLTAGE, LOCATION_CITY).icon(GRID_VOLTAGE) == LOCATION_CITY`.
  - `EntityFillTest.inverterFromPrefix`: `"bps_inverter_"` → `sensor.bps_inverter_operation_mode`, `_main_voltage`, `_output_voltage`, prefix `bps_inverter`.
  - `ValidationTest.inverterEntities`: all blank → none; only mode set → REQUIRED on grid and output; `"sensor.a b"` → INVALID; prefix `"Bad-Prefix"` → INVALID on `INVERTER_PREFIX`.
  - `ValidationTest.isInverterConfigured`: the three ids + URL + token → true; one id blank → false.
  - Stored JSON: the newest settings sample without `"inverter"` decodes to `InverterSettings()`; a sample with `"inverter": {"modeStyle": "ICON_WORD", "icons": {"GRID_VOLTAGE": "HOME"}}` decodes to those values.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*InverterSettingsTest" --tests "*EntityFillTest" --tests "*ValidationTest" --tests "*StoredJson*"`
- [ ] **Step 3: Implement the types, the fill, the checks and the rename**
- [ ] **Step 4: Run all unit tests, expect PASS**: `./gradlew :app:testDebugUnitTest`
- [ ] **Step 5: Commit** `feat(inverter): Add inverter settings`

### Task 2: Mode table and inverter reading

**Files:**
- Create: `domain/model/InverterMode.kt`, `domain/model/InverterReading.kt`
- Test: create `test/.../domain/InverterModeTest.kt`

**Interfaces:**
- Consumes: `IconSlot` (Task 1); `LevelColor`, `LevelType` (0010).
- Produces:
  - `enum class InverterMode(val text: String, val color: LevelColor, val type: LevelType, val slot: IconSlot)`: the mode table rows in Global Constraints (`POWER_ON("Power On", GREY, INFO, MODE_POWER_ON)`, … `FAULT("Fault", RED, CRITICAL, MODE_FAULT)`); `companion fun of(text: String?): InverterMode?` (trim, ignore case; no match → null).
  - `object ModeText { fun isNoValue(text: String?): Boolean }`: null, blank, `unavailable`, `unknown` (ignoring case) → true.
  - `@Serializable data class InverterReading(val mode: String? = null, val gridVoltageV: Double? = null, val outputVoltageV: Double? = null, val updatedAtMillis: Long)`: `mode` is the trimmed ESP32 text, never a no-value text.
- [ ] **Step 1: Write the failing tests**
  - `of_allRows`: each of the 7 texts → its row.
  - `of_ignoresCaseAndSpaces` (Review Focus 3): `" off-grid "` → `OFF_GRID`; `"MAINS"` → `MAINS`; `"Eco"` → null.
  - `isNoValue`: `null`, `""`, `"  "`, `"unavailable"`, `"Unknown"` → true; `"Standby"` → false.
  - `rows_matchSpec`: `STANDBY.type == WARNING`, `OFF_GRID.type == WARNING`, `FAULT.type == CRITICAL`, `MAINS.color == BLUE`, `CHARGING.color == AMBER`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*InverterModeTest"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** `feat(inverter): Add the mode table`

### Task 3: Two-part fetch

**Files:**
- Modify: `domain/model/SyncError.kt` (replace `SyncResult`), `domain/port/Ports.kt` (`BatterySource.fetch`), `data/ha/TemplateBuilder.kt`, `data/ha/TemplateResponse.kt`, `data/ha/HomeAssistantSource.kt`, `data/ha/StatesSource.kt`, `data/ha/TokenTypeSource.kt`, `testing/Fakes.kt`
- Test: `TemplateBuilderTest`, `TemplateResponseTest`, `HomeAssistantSourceTest`, `StatesSourceTest`, `TokenTypeSourceTest`

**Interfaces:**
- Consumes: `InverterEntitySettings` (Task 1), `InverterReading`, `ModeText` (Task 2).
- Produces:
  - `data class FetchRequest(val battery: EntitySettings?, val inverter: InverterEntitySettings?)` (null = that part is not asked).
  - `sealed interface PartResult<out T> { data class Ok<T>(val reading: T) : PartResult<T>; data class Failed(val error: SyncError) : PartResult<Nothing> }`.
  - `data class FetchResult(val battery: PartResult<BatteryReading>?, val inverter: PartResult<InverterReading>?)`: a part is null exactly when it was not asked. `SyncResult` is removed.
  - `BatterySource.fetch(connection: ConnectionSettings, request: FetchRequest, token: String): FetchResult` (the name `BatterySource` stays; its KDoc says it reads both parts). `FakeBatterySource(vararg results: FetchResult)`.
- Rules:
  - A failure of the whole request (`Unreachable`, `Unauthorized`, `HttpError`, a body that is not JSON) fails every asked part with that error.
  - Template: `TemplateBuilder.build(request)` puts `none` for every battery key when `battery == null`, and adds `"inv_mode": states(...)` (raw, like balancing), `"inv_grid_v"` and `"inv_out_v"` (`float(none)`); `none` when the inverter part is not asked or an id is blank. `TemplateResponse` gains `invMode: String?`, `invGridV: Double?`, `invOutV: Double?` and `fun toInverterReading(nowMillis: Long): InverterReading?`: mode through `ModeText` (no value → null), voltages finite or null; null when all three are null.
  - Battery part: `toReading` null → `Failed(BadResponse)`; the inverter part is judged on its own (and the other way round).
  - States (0009): the three inverter ids join the id list (same de-duplication, `MAX_PARALLEL = 4`, 404 → no value); each part is then built from its own values with the same two rules.
  - `TokenTypeSource` passes the request through.
- [ ] **Step 1: Write the failing tests**
  - `TemplateBuilderTest.inverterKeys`: request with both parts → the template contains `"inv_mode": states('sensor.x_operation_mode')` and `"inv_grid_v": states('sensor.x_main_voltage') | float(none)`; `battery = null` → `"soc": none`.
  - `TemplateResponseTest.inverterReading`: `inv_mode "Off-Grid"`, `inv_grid_v 0.0`, `inv_out_v 230.0` → `InverterReading("Off-Grid", 0.0, 230.0, now)`; `inv_mode "unavailable"` with voltages → mode null.
  - `HomeAssistantSourceTest.batteryFails_inverterOk` (Review Focus 1): `soc: null`, inverter values set → `battery == Failed(BadResponse)`, `inverter is Ok`.
  - `HomeAssistantSourceTest.inverterAllUnavailable_isBadResponse` (Review Focus 2): the three inverter values `"unavailable"`/null → `inverter == Failed(BadResponse)`, battery `Ok`.
  - `HomeAssistantSourceTest.unauthorized_failsBoth`; `onlyInverterAsked_batteryNull`.
  - `StatesSourceTest.inverterIds_threeMoreRequests`; `StatesSourceTest.batteryChargeMissing_inverterOk`.
  - `TokenTypeSourceTest.routesByType` updated to the new signature.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*TemplateBuilderTest" --tests "*TemplateResponseTest" --tests "*HomeAssistantSourceTest" --tests "*StatesSourceTest" --tests "*TokenTypeSourceTest"`
- [ ] **Step 3: Implement** (`SyncBattery` keeps compiling by reading `battery` only; Task 4 finishes it)
- [ ] **Step 4: Run all unit tests, expect PASS**
- [ ] **Step 5: Commit** `feat(ha): Fetch battery and inverter as two parts`

### Task 4: Independent sync

**Files:**
- Modify: `domain/SyncBattery.kt`, `domain/model/CacheState.kt`, `domain/BatteryOverview.kt`, `system/SyncWorker.kt`, `ui/MainViewModel.kt` (refresh result), `testing/Fakes.kt`
- Test: `SyncBatteryTest.kt`, `SyncWorkerTest.kt`, the stored cache JSON test

**Interfaces:**
- Consumes: Tasks 1–3; `LevelAlerts.evaluate` (0011, battery part only).
- Produces:
  - `CacheState.inverterReading: InverterReading? = null`, `CacheState.inverterError: SyncError? = null`; the existing `reading` / `error` stay the battery's. `refreshingSinceMillis` is shared.
  - `SyncBattery.invoke(): FetchResult`. Per part: configured (`isBatteryConfigured` / `isInverterConfigured`) → asked; not configured → not asked and the part's error becomes `NotConfigured` (its last reading stays). No token or no URL → nothing is fetched, both parts `NotConfigured`. A part's `Ok` replaces its reading and clears its error; `Failed` sets its error and keeps its last reading. 0011 battery alerts run only on a battery `Ok`.
  - `FetchResult.toWorkResult(runAttemptCount: Int)`: retry (up to 3 times, as today) when any part is `Failed(Unreachable)`.
- [ ] **Step 1: Write the failing tests** (`SyncBatteryTest`)
  - `bothConfigured_bothSaved`.
  - `batteryFails_inverterStillSaved` (Review Focus 1): source returns battery `Failed(BadResponse)`, inverter `Ok` → `cache.error == BadResponse`, `cache.inverterReading` set, `cache.inverterError == null`.
  - `inverterFails_batteryUnchangedAndSaved`.
  - `onlyInverterConfigured_fetchesInverterOnly` (Review Focus 5): blank battery entities → the request has `battery == null`; `cache.error == NotConfigured`; `toWorkResult` is success.
  - `noToken_bothNotConfigured_noRequest`.
  - `failedPart_keepsLastReading`.
  - `SyncWorkerTest.retriesWhenAnyPartUnreachable`.
  - Cache JSON: an old cache sample without the new keys decodes with `inverterReading == null`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*SyncBatteryTest" --tests "*SyncWorkerTest" --tests "*StoredJson*"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run all unit tests, expect PASS**
- [ ] **Step 5: Commit** `feat(sync): Save battery and inverter parts apart`

### Task 5: Inverter voltage levels

**Files:**
- Modify: `domain/model/ColorLevels.kt` (`LevelValue`, `LevelName`), `domain/DefaultLevels.kt`, `domain/LevelAlerts.kt`, `format/LevelLabels.kt`; strings EN/UK
- Test: `DefaultLevelsTest.kt`, `LevelAlertsTest.kt`

**Interfaces:**
- Consumes: 0010 `LevelValue`, `DefaultLevels`, `LevelPicker`; 0011 `LevelAlerts`; `InverterReading` (Task 2).
- Produces:
  - `LevelValue.GRID_VOLTAGE` and `LevelValue.OUTPUT_VOLTAGE`, added **last** (decimals 0, no level icons), so earlier ordinals and notification ids (`100 + ordinal`) do not move.
  - `LevelName.NO_GRID`, `LevelName.NO_POWER`; strings `level_name_no_grid` "No grid" / "Немає мережі", `level_name_no_power` "No power" / "Немає живлення"; value labels "Grid input" / "Вхід мережі" and "Output" / "Вихід" for the Colours editor and `notificationValueRes` (string names as the 0010/0011 value labels).
  - `DefaultLevels.of` rows from Global Constraints (ids 1–4; grid `LOW` bell on).
  - `LevelAlerts.margin(GRID_VOLTAGE | OUTPUT_VOLTAGE, …) == 5.0` (custom margin as 0011).
  - `LevelAlerts.evaluateInverter(reading: InverterReading, settings: Settings, enabled: Boolean, state: Map<String, LevelAlertState>): AlertOutcome`: the same per-value rules as `evaluate`, for the two voltages, numbers rounded to whole volts; a null voltage keeps that value's state. Share the per-value core with `evaluate` rather than copying it.
- [ ] **Step 1: Write the failing tests**
  - `DefaultLevelsTest.inverterDefaults`: GRID froms `[null, 100.0, 207.0, 254.0]`, names `[NO_GRID, LOW, NORMAL, HIGH]`, types `[INFO, WARNING, NORMAL, WARNING]`, bells `[false, true, false, false]`; OUTPUT first level `NO_POWER`, `CRITICAL`, no bells.
  - `LevelAlertsTest.grid_undervoltage_posts`: stored NORMAL, grid 205 → alert LOW, `rising == false`, `shown == 205.0`.
  - `LevelAlertsTest.grid_roundsToWholeVolts`: 206.6 → NORMAL; 206.4 → LOW.
  - `LevelAlertsTest.grid_rearm5V`: after LOW fired at 205: 211 → still disarmed; 212 → re-armed.
  - `LevelAlertsTest.inverter_nullVoltage_keepsState`.
  - `margin_values` gains `margin(GRID_VOLTAGE, null) == 5.0`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*DefaultLevelsTest" --tests "*LevelAlertsTest"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Commit** `feat(levels): Add inverter voltage levels`

### Task 6: Mode notifications

**Files:**
- Create: `domain/ModeAlerts.kt`
- Modify: `domain/model/CacheState.kt`, `domain/SyncBattery.kt`, `domain/port/Ports.kt` (`LevelNotifier`), `system/AndroidLevelNotifier.kt`, `testing/Fakes.kt`; strings EN/UK
- Test: create `test/.../domain/ModeAlertsTest.kt`; `SyncBatteryTest.kt`, `AndroidLevelNotifierTest.kt`

**Interfaces:**
- Consumes: Tasks 2, 4, 5; 0011 `LevelNotifier`, `AndroidLevelNotifier`.
- Produces:
  - `CacheState.inverterMode: String? = null` (the last seen mode text).
  - `data class ModeAlert(val text: String, val mode: InverterMode?, val measuredAtMillis: Long)`; `data class ModeOutcome(val alert: ModeAlert?, val last: String?)`; `object ModeAlerts { fun evaluate(reading: InverterReading, enabled: Boolean, last: String?): ModeOutcome }`: no mode → `ModeOutcome(null, last)`; `last == null` → store only; same text ignoring case → nothing; another text → `last` = it, and an alert when `enabled`.
  - `LevelNotifier.postMode(alert: ModeAlert)`; `FakeLevelNotifier` records `postedModes`.
  - `SyncBattery`: on an inverter `Ok`, run `LevelAlerts.evaluateInverter` and `ModeAlerts.evaluate` with `enabled = settings.notificationsOn && notifier.canPost()`; store the states with the reading; post after the cache write (levels first, then the mode).
  - `AndroidLevelNotifier.postMode`: id `MODE_NOTIFICATION_ID = 150` (one slot); channel of `mode?.type ?: INFO`; small icon by that type (0011); `setColor` = `WidgetPalette.Light.shades(mode?.color ?: GREY).ring`; title `notification_mode_title` "Inverter: %1$s" / "Інвертор: %1$s" with the mode word (`mode_word_*` strings) or the raw text for an unknown mode; text `notification_measured_at` (0011).
  - Mode word strings (`mode_word_power_on` … `mode_word_fault`): the words of the mode table, EN / UK.
- [ ] **Step 1: Write the failing tests**
  - `ModeAlertsTest.firstValue_storesOnly`; `change_alerts`: last `"Mains"`, reading `"Off-Grid"` → alert `OFF_GRID`; `sameIgnoringCase_noAlert`: last `"Mains"`, `"MAINS"` → none; `disabled_tracksOnly`.
  - `ModeAlertsTest.unavailable_isNotAChange` (Review Focus 2): last `"Mains"`, reading mode null → no alert, `last == "Mains"`; then `"Mains"` → still no alert.
  - `SyncBatteryTest.batteryFails_modeStillPosts` (Review Focus 1): battery `Failed`, inverter `Ok` with a new mode → one `postedModes` entry.
  - `AndroidLevelNotifierTest.postMode_offGrid`: id 150, channel `level_warning`, title "Inverter: battery"; `postMode_unknown`: text `"Eco"` → channel `level_info`, title "Inverter: Eco"; `postMode_fault` → `level_critical`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*ModeAlertsTest" --tests "*SyncBatteryTest" --tests "*AndroidLevelNotifierTest"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Commit** `feat(alerts): Notify on inverter mode changes`

### Task 7: Inverter widget model

**Files:**
- Create: `domain/InverterWidgetModel.kt`
- Test: create `test/.../domain/InverterWidgetModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1, 2, 4, 5; `LevelPicker.effective`, `pick` (0010).
- Produces:
  - `sealed interface InverterWidgetModel`:
    - `data class Data(val mode: ModeLook, val grid: VoltageLook, val output: VoltageLook, val style: ModeStyle, val error: SyncError?)`;
    - `data class Empty(val kind: EmptyKind)` with `enum class EmptyKind { SETUP, NO_DATA }` (reuse `WidgetModel.EmptyKind` if its values match).
  - `data class ModeLook(val mode: InverterMode?, val text: String?, val color: LevelColor, val icon: InverterIcon)`: known mode → its color and `settings.inverter.icon(mode.slot)`; unknown text → GREY, `icon(MODE_OTHER)`, the text as it is; no mode → `text = null` (drawn "—"), GREY, `icon(MODE_OTHER)`.
  - `data class VoltageLook(val volts: Int?, val color: LevelColor, val icon: InverterIcon)`: whole volts (rounded), color from the effective levels (default or custom), `icon(GRID_VOLTAGE)` / `icon(OUTPUT_VOLTAGE)`; null volts → `"—"` and the first level's color.
  - `fun InverterWidgetModel.Companion.from(cache: CacheState, settings: Settings, hasToken: Boolean): InverterWidgetModel`: not `isInverterConfigured` → `Empty(SETUP)`; no `inverterReading` → `Empty(NO_DATA)`; else `Data` with `error = cache.inverterError`.
- [ ] **Step 1: Write the failing tests**
  - `offGrid_default`: reading `("Off-Grid", 0.0, 230.0)` → mode GREEN `BATTERY_CHARGING_FULL`; grid 0 → BLUE (NO_GRID); output 230 → GREEN; style `BIG_ICON`.
  - `unknownMode_otherSlot` (Review Focus 3): `"Eco"` → GREY, `HELP`, text `"Eco"`.
  - `customIcon_used`: `withIcon(MODE_OFF_GRID, BATTERY_FULL)` → `BATTERY_FULL`.
  - `outputZero_isRed`; `notConfigured_isSetup`; `noReading_isNoData`; `inverterError_shown_batteryErrorIgnored`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*InverterWidgetModelTest"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** `feat(inverter): Add the strip model`

### Task 8: Inverter icons

**Files:**
- Create: `res/drawable/ic_inv_<name>.xml` for each `InverterIcon` (lower snake case: `ic_inv_tower`, `ic_inv_battery_charging_full`, …); `widget/InverterIcons.kt`; strings EN/UK
- Test: create `test/.../widget/InverterIconsTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `InverterIcon` (Task 1).
- Produces: `@DrawableRes fun InverterIcon.drawable(): Int`; `@StringRes fun InverterIcon.label(): Int` (`inv_icon_<name>`, a short EN/UK name for screen readers and the picker, e.g. "Transmission tower" / "Опора ЛЕП", "House" / "Будинок").
- Sources: Material Symbols Rounded `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/<name>/default/24px.svg` (viewBox `0 -960 960 960` → `viewportWidth/Height 960` with `<group android:translateY="960">`); `TOWER` = MDI `transmission-tower`, `HOME_LIGHTNING_BOLT` = MDI `home-lightning-bolt` (`https://raw.githubusercontent.com/Templarian/MaterialDesign/master/svg/<name>.svg`, viewport 24). Fill `@android:color/white`; the widget tints them.
- [ ] **Step 1: Write the failing test** `InverterIconsTest.everyIcon_hasDrawableAndLabel`: for each `InverterIcon`, `drawable()` resolves with `ResourcesCompat.getDrawable` and `label()` gives a non-blank string in `en` and `uk`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*InverterIconsTest"`
- [ ] **Step 3: Add the drawables (each with its source and Apache-2.0 header comment), the mapping and the labels**
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Commit** `feat(widget): Add the inverter icon set`

### Task 9: The strip widget

**Files:**
- Create: `widget/InverterWidget.kt`, `widget/InverterWidgetReceiver.kt`, `widget/InverterLayoutSpec.kt`, `widget/InverterWidgetContent.kt`, `res/xml/inverter_widget_info.xml`, `res/layout/inverter_widget_preview.xml`; strings EN/UK
- Modify: `AndroidManifest.xml`, `widget/GlanceWidgetRefresher.kt`, `AppContainer.kt` (`hasWidgets`), `widget/PreviewWidget.kt` (a second content)
- Test: create `test/.../widget/InverterWidgetContentTest.kt` (as `WidgetContentTest`); `androidTest/.../widget/InverterWidgetFitTest.kt` (compile only)

**Interfaces:**
- Consumes: Tasks 7, 8; `TextFit`, `WidgetColors`, `WidgetPalette.shades`, `HomeScale.factor`.
- Produces:
  - `enum class InverterLayoutSpec(val bucketWidthDp: Float, val bucketHeightDp: Float, …)` `S3X1(188, 53)`, `S4X1(261, 53)`, `S5X1(334, 53)` with the design numbers of the README table (gaps 3/4/6; big icon 28/32/32; icon + word: icon 20 over the word at 3×1, icon 22 + 5 beside it from 4×1; word box ≤ 44/72/90; voltage icon 11/13/15; value text 12.5/15/18; word text 10.5/14/16; card padding 8, radius 20).
  - `@Composable fun InverterWidgetContent(model: InverterWidgetModel, spec: InverterLayoutSpec, look: WidgetLook, fonts: InverterFontPercents, hostScale: Float, openApp: Action?)`: pills fully round, mode pill content width, the two voltage pills share the rest (weights); ⚠ (14) at the end on an error with the content description "Sync error: <reason>"; `Empty`: the tower icon and "Set up in the app" / "No data" (`inverter_setup`, `inverter_no_data`), no button; texts fit with `TextFit`.
  - `class InverterWidget : GlanceAppWidget` (`SizeMode.Responsive` over the three buckets; reads cache, settings, token like `BatteryWidget`; tap → `MainActivity` with the tapped id).
  - `class InverterWidgetReceiver : GlanceAppWidgetReceiver()` (same `onEnabled` / `onDisabled` schedule hook as `BatteryWidgetReceiver`).
  - `inverter_widget_info.xml`: `minWidth`/`minResizeWidth` 188dp, `minHeight`/`minResizeHeight` 53dp, `maxResizeWidth` 460dp, `resizeMode="horizontal"`, `configure` `MainActivity`, `widgetFeatures="reconfigurable|configuration_optional"`, preview layout, description `inverter_widget_description`, label `inverter_widget_name` ("Inverter" / "Інвертор").
  - `GlanceWidgetRefresher.refreshAll()` updates both widget types; `hasWidgets` is true when either type is placed.
- [ ] **Step 1: Write the failing tests** (`InverterWidgetContentTest`, Robolectric + Glance test)
  - `offGrid_bigIcon_4x1`: the battery icon with its content description, "0 V", "230 V", no ⚠.
  - `iconWord_3x1_wordUnderIcon`: style `ICON_WORD` → the word "battery" is shown.
  - `error_showsWarningIcon`; `setup_showsSetupText`; `noVolts_showsDash`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*InverterWidgetContentTest"`
- [ ] **Step 3: Implement**; `InverterWidgetFitTest` mirrors `WidgetFitTest` (worst case: "Unknown" + 288 V + 300 V + ⚠ at 3×1) and must compile
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Commit** `feat(widget): Add the inverter strip widget`

### Task 10: Owner fit check on the phone

Experiment mode (owner's rule): no spec change and no commit until it fits on the phone.

- [ ] **Step 1:** Build `assembleDebug`, give the APK to the owner. Ask for: the strip at 3×1, 4×1 and 5×1 on the home screen, both styles, light and dark, a mode change (switch the grid off or wait for one), and a screenshot per size.
- [ ] **Step 2:** Tune only `InverterLayoutSpec` numbers from the owner's feedback; re-run the done gate and send a new APK per round.
- [ ] **Step 3:** When the owner says it fits, commit the tuning `fix(widget): Tune the inverter strip sizes` and record the final numbers in `docs/design/README.md` in the same commit.

### Task 11: App tabs

**Files:**
- Modify: `ui/MainScreen.kt`, `MainActivity.kt`, `ui/PreviewTab.kt`, `ui/sections/ActionsRow.kt` (removed or split), `ui/sections/WidgetSection.kt` (home-screen scale moves out); strings EN/UK
- Create: `ui/AppTab.kt`, `ui/sections/HomeScaleSection.kt`
- Test: `ui/AppTabTest.kt`, `PreviewTabTest.kt`

**Interfaces:**
- Produces:
  - `enum class AppTab { BATTERY, INVERTER, COMMON }`; `object AppTabs { fun first(tappedProvider: String?, setupNeeded: Boolean): AppTab }`: a tapped or configured inverter widget → INVERTER; a battery widget → BATTERY; else `setupNeeded` (no URL or no token) → COMMON; else BATTERY.
  - `MainScreen`: Material 3 `Scaffold` with a top bar (title, ↻ "Refresh now" icon button with a content description) and a bottom `NavigationBar` (Battery, Inverter, Common: icon + label; `tab_battery` "Battery" / "Батарея", `tab_inverter` "Inverter" / "Інвертор", `tab_common` "Common" / "Загальні"). Each tab has its own `rememberScrollState` (kept across tab switches).
  - Battery tab: preview card, "Add to home screen" (`requestPinGlanceAppWidget(BatteryWidgetReceiver)`), Widget (display + fonts), Colours (battery values), Entities, Capacity. Common tab: Appearance, Connection, Token, Refresh, Notifications, Home-screen scale. Inverter tab: Task 12 (an empty placeholder in this task).
  - `MainActivity` finds the tapped / configured widget's type with `AppWidgetManager.getAppWidgetInfo(id).provider.className`.
- [ ] **Step 1: Write the failing tests** `AppTabTest.first`: inverter provider → INVERTER; battery provider → BATTERY; none + setup needed → COMMON; none + set up → BATTERY.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*AppTabTest"`
- [ ] **Step 3: Implement**; move the sections without changing them
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Commit** `feat(ui): Move settings into three tabs`

### Task 12: The Inverter tab

**Files:**
- Create: `ui/sections/InverterPreviewCard.kt`, `ui/sections/InverterDisplaySection.kt` (mode style + fonts), `ui/sections/InverterIconsSection.kt`, `ui/sections/InverterEntitiesSection.kt`
- Modify: `ui/MainViewModel.kt`, `ui/MainScreen.kt`, `ui/sections/ColorsSection.kt` (`values: List<LevelValue>` parameter), `MainActivity.kt` (`MainActions`); strings EN/UK
- Test: `MainViewModelTest.kt`; `NoHardcodedTextTest`; the done gate

**Interfaces:**
- Consumes: Tasks 1, 5, 7–9, 11; `ColorsSection` (0010/0011).
- Produces:
  - `MainViewModel`: `saveInverterEntities(e: InverterEntitySettings)` (validates, saves, syncs); `fillInverterFromPrefix(prefix: String): InverterEntitySettings` (no request; the section puts it in its draft); `saveModeStyle(style: ModeStyle)`; `saveInverterFonts(p: InverterFontPercents)`; `resetInverterSizes()`; `saveInverterIcon(slot: IconSlot, icon: InverterIcon)`. Only `saveInverterEntities` syncs.
  - `ColorsSection(values = …)`: Battery tab passes the 7 battery values; Inverter tab passes `GRID_VOLTAGE`, `OUTPUT_VOLTAGE`.
  - Inverter tab order: preview card (size tabs 3×1 / 4×1 / 5×1, the real strip through `PreviewWidget`), "Add to home screen" (`InverterWidgetReceiver`), Display (mode style segmented button "Big icon" / "Icon + word" — "Велика іконка" / "Іконка + слово"; two font sliders "Mode word" / "Слово режиму", "Values" / "Значення"; "Reset sizes"), Icons (10 rows: slot name + current icon; a tap opens a grid of the 45 icons, the 0010 picker component), Colours, Entities (prefix + Fill + three ids, errors under the fields).
- [ ] **Step 1: Write the failing tests** (`MainViewModelTest`)
  - `saveInverterEntities_partial_isRequired`: only the mode id → REQUIRED errors, nothing stored.
  - `saveInverterEntities_savesAndSyncs`.
  - `fillInverterFromPrefix_noRequest`: `source.calls` unchanged.
  - `saveInverterIcon_storesName`: stored `icons["MODE_MAINS"] == "LOCATION_CITY"`.
  - `resetInverterSizes`: fonts back to 50/50, the mode style kept.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :app:testDebugUnitTest --tests "*MainViewModelTest"`
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run the done gate, expect PASS**
- [ ] **Step 5: Ask the owner** to try the Inverter tab on the phone: Fill with `bps_inverter`, save, place the strip from "Add to home screen", change the style, an icon and a font size and watch the preview and the strip, set a bell and a margin on the grid voltage.
- [ ] **Step 6: Commit** `feat(ui): Add the Inverter tab`
