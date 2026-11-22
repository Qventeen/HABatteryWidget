# Battery Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A battery type (LiFePO4, Li-ion, LTO) and a typed cell count in a new Pack section, so the default voltage and current levels fit the owner's chemistry.

**Architecture:** Two new stored fields (`batteryType`, `cellCount`, both `null` by default, no migration). `LevelBasis` gets the type and uses the typed count only when the reading cannot count the cells. A `BatteryPreset` per type holds volts per cell and charge / discharge C-rates; `DefaultLevels` builds the voltage and current rows from it. The Capacity section becomes the Pack section: a type row that stores at the tap, and cells + capacity with one Save.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, kotlinx.serialization, DataStore, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-20-0013-battery-profile-design.md`

Paths are under `app/src/main/kotlin/io/lumensync/habattery/` (code) and `app/src/test/kotlin/io/lumensync/habattery/` (tests) unless they start with `app/` or `docs/`.

## Global Constraints

- Branch `0013-battery-profile` from `epic-widget-improvements`.
- JDK 17 for every Gradle run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`.
- Stored fields default to `null`; no `schemaVersion` bump, no migration (0001 §14). `capacityAh` stays a top-level field.
- Every visible text in `res/values/strings.xml` with the Ukrainian text in `res/values-uk/strings.xml`, in the same commit; no user text in Kotlin (`NoHardcodedTextTest`).
- LiFePO4 numbers stay exactly as 0010: 3.00 / 3.20 / 3.45 / 3.55 / 3.65 V per cell; 0.2 / 0.5 / 0.8 C both ways.
- No default bell on any voltage or current level, for every type.
- Hysteresis defaults stay: 0.025 V per cell, 0.05 C, for every type.
- The inverter values keep `LevelBasis(null, null)`; the type never touches them.
- Load the `writing-code-comments` skill before writing or changing comments or KDoc.
- Commits: header only, by the `commit-style` skill, one `Co-Authored-By` trailer.
- Never run connected tests or `adb uninstall` on the owner's phone; connected tests only with `ANDROID_SERIAL=emulator-5554`.

## Review Focus

1. A settings file from a later build with an unknown `batteryType` (e.g. `"SODIUM"`) must load with the type `null` (so LiFePO4) and keep every other setting — Task 1 test `settingsWithUnknownBatteryType_readsNull`.
2. A typed count that differs from the reading's count must not win while both sensors work — Task 1 test `cells_readingWinsOverTypedCount`.
3. A type change with stored alert state must post nothing on the next sync — Task 2 test `typeChange_postsNothing`.
4. Cell text such as " 16 ", "15.5", "0", "33" and "x" — Task 3 tests `savePack_storesCellsAndCapacity`, `savePack_cellsErrors`.
5. A Save with a bad cell count and a good capacity stores neither — Task 3 test `savePack_anyError_storesNothing`.

---

### Task 1: Battery type and typed cells in settings and `LevelBasis`

**Files:**
- Create: `domain/model/BatteryType.kt`
- Modify: `domain/model/Settings.kt` (fields after `capacityAh`, KDoc), `domain/LevelBasis.kt`
- Test: `data/store/StoredJsonV2Test.kt`, `domain/LevelBasisTest.kt`, `domain/WidgetModelTest.kt`, `domain/LevelAlertsTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class BatteryType { LIFEPO4, LI_ION, LTO; companion object { val DEFAULT = LIFEPO4 } }`; `Settings.batteryType: BatteryType? = null`; `Settings.cellCount: Int? = null`; `data class LevelBasis(val capacityAh: Double?, val cells: Int?, val type: BatteryType = BatteryType.DEFAULT)`.

- [ ] **Step 1: Write the failing tests**

`StoredJsonV2Test.settingsRecord_v2`: add `"batteryType": "LTO", "cellCount": 22` to the sample after `"capacityAh"`, and `batteryType = BatteryType.LTO, cellCount = 22` to `expected` (the test is a round trip, so the new keys must be in the sample). New tests:

```kotlin
@Test fun settingsWithoutPack_decodeToNull() {
  val s = StoreJson.decodeFromString(SettingsRecord.serializer(), """{"schemaVersion":2,"settings":{"notificationsOn":true}}""").settings
  assertNull(s.batteryType); assertNull(s.cellCount)
}
@Test fun settingsWithUnknownBatteryType_readsNull() {
  val json = """{"schemaVersion":2,"settings":{"notificationsOn":true,"batteryType":"SODIUM","cellCount":16}}"""
  val s = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings
  assertNull(s.batteryType); assertEquals(16, s.cellCount); assertTrue(s.notificationsOn)
}
```

`LevelBasisTest`:

```kotlin
@Test fun cells_readingWinsOverTypedCount() =
  assertEquals(16, LevelBasis.of(reading(total = 52.8, average = 3.3), Settings(cellCount = 15)).cells)
@Test fun cells_typedCountFillsAGap() {
  assertEquals(16, LevelBasis.of(reading(average = 3.3), Settings(cellCount = 16)).cells)
  assertEquals(16, LevelBasis.of(null, Settings(cellCount = 16)).cells)
}
@Test fun cells_typedBelowOne_isNull() = assertNull(LevelBasis.of(null, Settings(cellCount = 0)).cells)
@Test fun type_nullIsLiFePO4() {
  assertEquals(BatteryType.LIFEPO4, LevelBasis.of(null, Settings()).type)
  assertEquals(BatteryType.LTO, LevelBasis.of(null, Settings(batteryType = BatteryType.LTO)).type)
}
```

`WidgetModelTest.averageCell_typedCountFillsAGap`: `data(sample.copy(totalVoltageV = null, averageCellVoltageV = 3.30), settings().copy(cellCount = 16)).averageCellLook` is `LevelLook(LevelColor.GREEN)` (default LiFePO4 Normal).

`LevelAlertsTest.margin_voltage_usesTheTypedCount`: `LevelAlerts.margin(LevelValue.VOLTAGE, LevelBasis.of(null, Settings(cellCount = 16)))` is `0.4`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*StoredJsonV2Test" --tests "*LevelBasisTest" --tests "*WidgetModelTest" --tests "*LevelAlertsTest"`
Expected: compile errors (`BatteryType`, `cellCount`, `type` unknown).

- [ ] **Step 3: Implement**

`BatteryType` as in Interfaces; KDoc: the pack chemistry, used only to pick default levels (spec 0013); `null` in settings means `DEFAULT`. `Settings`: the two fields after `capacityAh`. `LevelBasis.of`: `cells` = the reading's count (≥ 1), else `settings.cellCount` (≥ 1), else `null`; `type` = `settings.batteryType ?: BatteryType.DEFAULT`. Replace the KDoc line "Spec 0013 adds the battery type here." with what the type and the typed count do.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests pass.

- [ ] **Step 5: Commit** — e.g. `feat(domain): Store the battery type and cell count`

### Task 2: Presets per type in `DefaultLevels`

**Files:**
- Create: `domain/BatteryPreset.kt`
- Modify: `domain/DefaultLevels.kt` (voltage and current rows, KDoc), `docs/ai/agents/architecture.md` (domain list: add `BatteryPreset`; `LevelBasis` now also holds the type)
- Test: `domain/DefaultLevelsTest.kt`, `domain/LevelAlertsTest.kt`, `domain/LevelPickerTest.kt`

**Interfaces:**
- Consumes: `BatteryType`, `LevelBasis.type` (Task 1).
- Produces: `data class CellVolts(val low: Double, val gettingLow: Double, val full: Double, val high: Double, val veryHigh: Double)`, `data class CRates(val light: Double, val high: Double, val veryHigh: Double)`, `data class BatteryPreset(val cellVolts: CellVolts, val charge: CRates, val discharge: CRates) { companion object { fun of(type: BatteryType): BatteryPreset } }`.

Values (spec §3.2):

| Type | `CellVolts` | `charge` | `discharge` |
|---|---|---|---|
| `LIFEPO4` | 3.00, 3.20, 3.45, 3.55, 3.65 | 0.2, 0.5, 0.8 | 0.2, 0.5, 0.8 |
| `LI_ION` | 3.40, 3.60, 4.10, 4.15, 4.20 | 0.2, 0.5, 0.8 | 0.4, 1.0, 1.3 |
| `LTO` | 2.00, 2.20, 2.65, 2.70, 2.80 | 0.4, 1.0, 5.0 | 0.4, 1.0, 6.0 |

- [ ] **Step 1: Write the failing tests**

Give the `of` helper in `DefaultLevelsTest` a `type: BatteryType = BatteryType.LIFEPO4` parameter. Existing LiFePO4 tests stay unchanged (they prove 0010's numbers). New tests (sides, types, names and ids as `voltage16Cells`; bells all off):

```kotlin
@Test fun voltageLiIon14Cells() {
  val levels = of(LevelValue.VOLTAGE, cells = 14, type = BatteryType.LI_ION)
  assertEquals(listOf(47.6, 50.4, null, 57.4, 58.1, 58.8), levels.edges())
}
@Test fun voltageLto22Cells() =
  assertEquals(listOf(44.0, 48.4, null, 58.3, 59.4, 61.6), of(LevelValue.VOLTAGE, cells = 22, type = BatteryType.LTO).edges())
@Test fun currentLiIon100Ah() {
  assertEquals(listOf(20.0, null, 50.0, 80.0), of(LevelValue.CHARGE_CURRENT, 100.0, type = BatteryType.LI_ION).edges())
  assertEquals(listOf(40.0, null, 100.0, 130.0), of(LevelValue.DISCHARGE_CURRENT, 100.0, type = BatteryType.LI_ION).edges())
}
@Test fun currentLto40Ah() {
  assertEquals(listOf(16.0, null, 40.0, 200.0), of(LevelValue.CHARGE_CURRENT, 40.0, type = BatteryType.LTO).edges())
  assertEquals(listOf(16.0, null, 40.0, 240.0), of(LevelValue.DISCHARGE_CURRENT, 40.0, type = BatteryType.LTO).edges())
}
```

Also: for every `BatteryType`, with no cell count the voltage is the one plain middle level, and with no capacity each current is today's one level; the other values (SOC, delta, both temperatures) equal the LiFePO4 lists; `copyForCustom` of LTO 40 Ah discharge gives `[16.0, null, 40.0, 240.0]`. Extend `defaultBells` (exact bell map) to loop every type.

`LevelAlertsTest.typeChange_postsNothing`: evaluate a reading with total 52.8 V, average 3.3 V, current −10 A, `capacityAh = 100.0` under `Settings()` (LiFePO4: voltage Normal, id 3), then evaluate the same reading with the returned state under `batteryType = LTO` (voltage Very high, id 6), `enabled = true`. Expected: no alerts; the voltage state's current id is 6.

`typeChange_keepsOwnLevels`: `LevelPicker.effective(LevelValue.VOLTAGE, settings, basis)` returns the stored own levels for both `LevelBasis(null, 16, LIFEPO4)` and `LevelBasis(null, 16, LTO)`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*DefaultLevelsTest" --tests "*LevelAlertsTest" --tests "*LevelPickerTest"`
Expected: compile error on `type =` in the helper, then the Li-ion / LTO edges fail.

- [ ] **Step 3: Implement**

`BatteryPreset` in `domain/BatteryPreset.kt` with the table above; its KDoc names the rule of each number (spec §3.2: Very high = charge limit, Full = usual target, High between, Low above the cut-off, Getting low = Low + 0.20 V; C-rates: High = standard rate, Very high ≈ 0.8 × max continuous, Light = 0.4 × standard) and points to the spec for sources. `DefaultLevels.build`: take `BatteryPreset.of(basis.type)`; voltage rows from `cellVolts` via `packEdge`; `CHARGE_CURRENT` rows from `charge`, `DISCHARGE_CURRENT` from `discharge` via `roundEdge(rate * capacity)`. Rows, names, types and the no-count / no-capacity branches stay as they are. Fix the comment "LiFePO4 volts per cell". Update `architecture.md`.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests pass.

- [ ] **Step 5: Commit** — e.g. `feat(domain): Pick default levels by battery type`

### Task 3: The Pack section

**Files:**
- Create: `ui/sections/PackSection.kt`
- Delete: `ui/sections/CapacitySection.kt`
- Modify: `domain/Validation.kt` (`Field.CELLS` after `CAPACITY`, `MAX_CELLS`, `cells`), `ui/MainViewModel.kt` (`saveCapacity` → `savePack`, new `saveBatteryType`), `ui/MainScreen.kt` (`MainActions`, `BatteryTab`), `MainActivity.kt` (wiring), `ui/sections/FieldErrorText.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-uk/strings.xml`, `app/src/androidTest/kotlin/io/lumensync/habattery/ui/MainScreenTest.kt`, `README.md` (Battery tab line: "entities and capacity" → "entities and the pack: type, cells, capacity")
- Test: `domain/ValidationTest.kt`, `ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `BatteryType`, `Settings.batteryType`, `Settings.cellCount` (Task 1).
- Produces:
  - `Validation.MAX_CELLS = 32`; `fun cells(count: Int?): List<FieldError>` (`null` and 1–32 → none, else `CELLS OUT_OF_RANGE`).
  - `MainViewModel.saveBatteryType(type: BatteryType)` — stores at once, no sync.
  - `MainViewModel.savePack(cellsText: String, capacityText: String, locale: Locale)` — trims both; blank → `null`; cells by `toIntOrNull()` (not a whole number → `CELLS INVALID`), capacity parsed as today; errors in field order (cells, capacity); any error → `fail` with all of them and nothing stored; else stores both; no sync.
  - `MainActions.saveBatteryType: (BatteryType) -> Unit`, `MainActions.savePack: (cells: String, capacity: String, locale: Locale) -> Unit` (replaces `saveCapacity`).
  - `@Composable fun PackSection(batteryType: BatteryType?, cellCount: Int?, capacityAh: Double?, errors: List<FieldError>, onTypeChange: (BatteryType) -> Unit, onSave: (cells: String, capacity: String, locale: Locale) -> Unit, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false)`.

Strings (spec §3.1):

| Key | English | Ukrainian |
|---|---|---|
| `pack_title` | Pack | Збірка |
| `pack_type_label` | Type | Тип |
| `pack_type_hint` | Sets the default voltage and current levels. Own levels stay as they are. Li-ion means NMC. | Задає типові рівні напруги та струму. Власні рівні не змінюються. Li-ion — це NMC. |
| `battery_type_lifepo4` / `_li_ion` / `_lto` | LiFePO4 / Li-ion / LTO (`translatable="false"`) | — |
| `cells_label` | Cells in series | Комірок послідовно |
| `cells_hint` | Used when the total voltage or average cell sensor is empty. | Використовується, коли датчик загальної напруги або середньої комірки порожній. |
| `field_cells_range` | Enter a whole number from 1 to %1$d | Введіть ціле число від 1 до %1$d |
| `pack_summary_cells` | %1$dS (`translatable="false"`) | — |
| `pack_summary_join` | %1$s · %2$s (`translatable="false"`) | — |
| `capacity_label` (changed) | Capacity (Ah) | Ємність (А·год) |

Remove `capacity_title` from both files. `capacity_hint`, `capacity_summary` and the capacity errors stay.

- [ ] **Step 1: Write the failing tests**

`ValidationTest.cells_rules`: `cells(null)`, `cells(1)`, `cells(32)` → empty; `cells(0)`, `cells(33)` → `[FieldError(CELLS, OUT_OF_RANGE)]`.

`MainViewModelTest` (replace every `saveCapacity` call; keep `saveCapacity_ukrainianComma` as `savePack_capacityUkrainianComma` with `cellsText = ""`):

```kotlin
@Test fun saveBatteryType_storesAtOnce() // saveBatteryType(LTO) → settings.batteryType == LTO; source.calls unchanged
@Test fun savePack_storesCellsAndCapacity() // savePack(" 16 ", "314,5", uk) → 16 and 314.5; savePack("", "", US) → both null
@Test fun savePack_cellsErrors() // "15.5" → [CELLS INVALID]; "x" → [CELLS INVALID]; "0" and "33" → [CELLS OUT_OF_RANGE]
@Test fun savePack_anyError_storesNothing() // savePack("0", "100", US) → capacityAh stays null; savePack("x", "0", US) → errors == [FieldError(CELLS, INVALID), FieldError(CAPACITY, OUT_OF_RANGE)]
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ValidationTest" --tests "*MainViewModelTest"`
Expected: compile errors (`Field.CELLS`, `savePack`, `saveBatteryType` unknown).

- [ ] **Step 3: Implement validation and the ViewModel**

As in Interfaces. `FieldErrorText`: `Field.CELLS` shows `field_cells_range` with `Validation.MAX_CELLS` for both `INVALID` and `OUT_OF_RANGE` (the `%1$d` needs the argument, so not through `invalidMessage`).

- [ ] **Step 4: Implement `PackSection` and the wiring**

Layout as spec §3.1: `ChoiceRow(R.string.pack_type_label, BatteryType.entries, batteryType ?: BatteryType.DEFAULT, ::batteryTypeLabel, onSelect = onTypeChange)`, the type hint under it (`bodySmall`), the cells field (`KeyboardType.Number`, `Field.CELLS` error, `cells_hint`), the capacity field as `CapacitySection` had it, one `SaveButton`. Summary: the type name, then `pack_summary_cells` when `cellCount != null`, then `capacity_summary` when `capacityAh != null`, folded left to right with `pack_summary_join`. `BatteryTab` puts `PackSection` where `CapacitySection` was. `MainScreenTest`: `capacity_title` → `pack_title` (fold test, order test, the scroll test at the `capacity_title` lines; comments say "Pack").

- [ ] **Step 5: Run the done gate**

Run: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`, no new lint warnings.

- [ ] **Step 6: Commit** — e.g. `feat(ui): Replace the capacity section with the pack section` (split the validation + ViewModel and the UI into two commits if it reads better)

## After the tasks

1. Whole-branch review (Opus, xhigh), fixes on the branch, done gate again.
2. Owner check on the phone (debug APK): the Pack section in English and Ukrainian; a type change shows in the Levels and alerts tags and on the widget.
3. Merge into `epic-widget-improvements` with a merge commit after the owner's OK.
