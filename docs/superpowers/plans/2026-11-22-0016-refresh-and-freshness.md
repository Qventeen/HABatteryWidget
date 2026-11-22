# Refresh on unlock, ↻ on both widgets, freshness dot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app refreshes when the phone is unlocked, the battery widget loses its auto-refresh button, the inverter strip gets ↻, and both widgets show a freshness dot on ↻.

**Architecture:** Pure rules in `domain/` (`Freshness`, `RefreshPolicy.unlockSyncWanted`, `UnlockRefresh`), a runtime `USER_PRESENT` receiver in `system/` registered by `App`, two new `SyncScheduler` methods backed by WorkManager (unlock sync with a network constraint, a no-network redraw when a dot turns amber), and Glance drawing changes in `widget/`. Every trigger still ends in `SyncBattery` (One sync path).

**Tech Stack:** Kotlin, Jetpack Glance, WorkManager 2.12 (+ `work-testing` for tests), kotlinx.serialization, JUnit 4 + Robolectric (SDK 34), Glance unit-test semantics (`hasRunCallbackClickAction`, test tags).

**Spec:** `docs/superpowers/specs/2026-11-22-0016-refresh-and-freshness-design.md` (approved 2026-11-22). Design: `docs/design/README.md` section "Refresh — ↻ and freshness dot", `docs/design/refresh-dot.png`, canvas row v14.

## Global Constraints

- Branch `0016-refresh` from `epic-widget-improvements`; merge back with a merge commit after the owner's review.
- Every Gradle run: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`. Gate: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`.
- One sync path: the unlock receiver never calls the network; it marks refreshing, redraws and enqueues work.
- Freshness: late when age > interval + 5 min; the interval is the saved one when the timed sync is on and valid, else 15 min. Each widget follows its own part.
- Dot: 8 dp, 2 dp ring in the card colour, on the ↻ circle's top-right edge; fresh `#43A047` / `#66BB6A`, late `#F59E0B` / `#FFB74D` (light / dark). Round on API 26+ (a vector drawable, not `cornerRadius`).
- Strip: ↻ face 28 dp, icon 15 dp, at 15.5–43.5 dp from the card top; ⚠ 11 dp at 2.5–13.5 dp, not clickable. Card stays 59 dp high.
- `RefreshSettings.onUnlock` defaults to `true` (spec 0016 exception to "stored fields default to false").
- Every visible text in `values/strings.xml` and `values-uk/strings.xml` in the same commit; no user text in Kotlin.
- Before writing or changing a code comment, load the `writing-code-comments` skill. Commits: header only, `commit-style` skill, one `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` trailer.
- Never run connected tests or `adb uninstall` on the owner's phone; `adb -s <serial>` only.

## Review Focus

1. **Unlock bursts** (pocket, quick unlock / lock): at most one sync while one runs or the data is under a minute old. Test: Task 6 `secondUnlock_whileRefreshing_doesNothing`.
2. **A reading time in the future** (clock set back): the dot is fresh and the age is 0, never negative. Test: Task 1 `futureReading_isFreshAndZeroOld`.
3. **Inverter-only user** (battery not set up): the unlock gate and the dot use the inverter part only. Tests: Task 1 `inverterOnly_usesInverterReading`, Task 4 `stripDot_followsInverterPart`.
4. **Home-screen scale** (`hostScale` 1.28): the new strip sizes scale with the rest. Test: Task 4 `scaled_includesRefreshSizes`.
5. **Auto on with an empty or invalid interval:** the dot counts with 15 min. Test: Task 1 `invalidInterval_uses15`.

---

### Task 1: Freshness rules and the unlock setting (domain)

**Files:**
- Create: `app/src/main/kotlin/io/lumensync/habattery/domain/Freshness.kt`
- Modify: `domain/model/Settings.kt` (`RefreshSettings`), `domain/RefreshPolicy.kt`, `domain/BatteryOverview.kt` (use `Freshness.isRefreshing`)
- Test: `app/src/test/.../domain/FreshnessTest.kt` (new), `domain/RefreshPolicyTest.kt`, the settings-store test under `app/src/test/.../data/`

**Interfaces:**
- Produces:
  - `RefreshSettings(auto: Boolean = false, intervalMinutes: Int? = null, onUnlock: Boolean = true)`
  - `object Freshness` with `const val GRACE_MS = 300_000L`, `const val UNLOCK_MIN_AGE_MS = 60_000L`, and:
    - `const val REFRESHING_LIMIT_MS = 120_000L`, moved here from `BatteryOverview`
    - `fun isRefreshing(sinceMillis: Long?, nowMillis: Long): Boolean` (the 2-min rule `BatteryOverview` has today)
    - `fun lateAfterMillis(refresh: RefreshSettings): Long`
    - `fun isLate(updatedAtMillis: Long, nowMillis: Long, refresh: RefreshSettings): Boolean`
    - `fun ageMinutes(updatedAtMillis: Long, nowMillis: Long): Long` (≥ 0)
    - `fun nextLateAt(cache: CacheState, settings: Settings, hasToken: Boolean, nowMillis: Long): Long?` — the earliest moment a configured part's reading turns late, later than now; null when none.
  - `RefreshPolicy.unlockSyncWanted(settings: Settings, cache: CacheState, hasToken: Boolean, hasWidgets: Boolean, nowMillis: Long): Boolean`

- [ ] **Step 1: Write the failing tests** (`FreshnessTest`, additions to `RefreshPolicyTest`; use `configuredSettings(...)` from the test helpers)

```kotlin
@Test fun lateAfter_autoOn_isIntervalPlus5() =
  assertEquals(35 * 60_000L, Freshness.lateAfterMillis(RefreshSettings(true, 30)))
@Test fun lateAfter_autoOff_is20() =
  assertEquals(20 * 60_000L, Freshness.lateAfterMillis(RefreshSettings(false, 60)))
@Test fun invalidInterval_uses15() {
  assertEquals(20 * 60_000L, Freshness.lateAfterMillis(RefreshSettings(true, null)))
  assertEquals(20 * 60_000L, Freshness.lateAfterMillis(RefreshSettings(true, 5)))
}
@Test fun isLate_atTheEdge() {
  val off = RefreshSettings()
  assertFalse(Freshness.isLate(0L, 20 * 60_000L, off))
  assertTrue(Freshness.isLate(0L, 20 * 60_000L + 1, off))
}
@Test fun futureReading_isFreshAndZeroOld() {
  assertFalse(Freshness.isLate(NOW + 60_000L, NOW, RefreshSettings()))
  assertEquals(0L, Freshness.ageMinutes(NOW + 60_000L, NOW))
}
@Test fun ageMinutes_roundsDown() = assertEquals(3L, Freshness.ageMinutes(NOW - 239_000L, NOW))
@Test fun nextLateAt_earliestGreenPart() // battery read at T, inverter at T + 2 min, both configured,
  // auto off, now = T + 1 min → T + 20 min; now = T + 21 min → T + 22 min; now = T + 23 min → null;
  // nothing configured → null
@Test fun refreshing_within2Minutes() // since now − 119_999 → true; now − 120_000 → false; null → false
```

`RefreshPolicyTest` — base case `wanted()`: `onUnlock = true`, `hasWidgets = true`, battery configured, battery reading 2 min old, not refreshing → `true`. Then one test per blocker, each `false`: `onUnlockOff`, `noWidgets`, `nothingConfigured`, `refreshingSince(now − 10_000)`, `batteryRead30sAgo`. Plus:

```kotlin
@Test fun refreshingOlderThan2Min_doesNotBlock()            // since now − 130_000 → true
@Test fun inverterOnly_usesInverterReading()                 // battery not configured: inverter 30 s → false; 10 min → true; no reading → true
@Test fun oneOldPart_isEnough()                              // battery 30 s, inverter failed with reading 10 min old → true
```

Settings store test: decoding a stored record whose `refresh` object is `{"auto":true,"intervalMinutes":30}` gives `RefreshSettings(true, 30, onUnlock = true)`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew testDebugUnitTest --tests '*FreshnessTest' --tests '*RefreshPolicyTest' --tests '*Settings*'`
Expected: compile FAIL (`Unresolved reference: Freshness`, `onUnlock`).

- [ ] **Step 3: Implement** `Freshness`, `RefreshSettings.onUnlock`, `RefreshPolicy.unlockSyncWanted`; `BatteryOverview.build` uses `Freshness.isRefreshing`. "Configured" is `Validation.isBatteryConfigured(settings, hasToken)` / `Validation.isInverterConfigured(settings, hasToken)`; a part's reading is `cache.reading` / `cache.inverterReading`, its time `updatedAtMillis`. The interval counts when `refresh.auto && Validation.refresh(refresh).isEmpty()`.

- [ ] **Step 4: Run the tests** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(refresh): Add the freshness and unlock rules`

---

### Task 2: Remove the widget's auto-refresh toggle

**Files:**
- Delete: `widget/ToggleAutoRefreshAction.kt`, `domain/ToggleAutoRefresh.kt`, `test/.../domain/ToggleAutoRefreshTest.kt`; `res/drawable/ic_auto_refresh.xml`, `ic_auto_refresh_off.xml` and `preview_button_on_bg.xml` if nothing else uses them (grep)
- Modify: `domain/model/Settings.kt` (drop `RefreshSettings.toggled()`), `AppContainer.kt` (drop `toggleAutoRefresh`), `widget/WidgetContent.kt` (`WidgetActions(openApp, refresh)`), `widget/BatteryWidget.kt`, `widget/WidgetParts.kt` (`TopRow`: one `CircleButton`, no `growStart`), `widget/WidgetTexts.kt` (drop `autoDescription`), `domain/WidgetModel.kt` (drop `Data.autoRefresh` if unused), `widget/LayoutSpec.kt` (drop `buttonGap` from the class, `scaled`, the three layouts; top-row width counts one `buttonTouch`), `res/layout/battery_widget_preview.xml` (drop the auto `FrameLayout`), `values*/strings.xml` (drop `widget_auto_on`, `widget_auto_off`)
- Test: `widget/WidgetContentTest.kt` (drop `autoToggle_describesItsState` and `NoOpToggle`), `widget/LayoutSpecTest.kt`

- [ ] **Step 1: Write the failing test** (`WidgetContentTest`)

```kotlin
@Test
fun topRow_hasNoAutoToggle() =
  LayoutSpec.all.forEach { spec ->
    render(model(), spec.bucketWidth, spec.bucketHeight) {
      onAllNodes(hasContentDescriptionEqualTo("Auto refresh off")).assertCountEquals(0)
      onAllNodes(hasContentDescriptionEqualTo("Auto refresh on")).assertCountEquals(0)
      onNode(hasRunCallbackClickAction<NoOpRefresh>()).assertExists()
    }
  }
```

- [ ] **Step 2: Run it to see it fail** — `./gradlew testDebugUnitTest --tests '*WidgetContentTest'`. Expected: FAIL (one "Auto refresh off" node).

- [ ] **Step 3: Remove** everything in Files; fix `LayoutSpecTest` expectations that used `buttonGap`.

- [ ] **Step 4: Verify**
  - `./gradlew testDebugUnitTest` → PASS.
  - `grep -rn "toggleAuto\|ToggleAutoRefresh\|widget_auto_\|ic_auto_refresh\|buttonGap" app/src` → no output.

- [ ] **Step 5: Commit** — `feat(widget): Drop the auto-refresh toggle`

---

### Task 3: Freshness dot on the battery widget

**Files:**
- Create: `app/src/main/res/drawable/widget_dot.xml` (24 dp vector, one white circle `M12,2a10,10 0,1 1,0 20a10,10 0,1 1,0 -20z`)
- Modify: `domain/WidgetModel.kt` (`Data.dataLate: Boolean`, `Data.dataAgeMinutes: Long`, from `Freshness` with `overview`'s now), `domain/BatteryOverview.kt` (carry `nowMillis` if `WidgetModel.build` needs it), `widget/WidgetColors.kt` (`fresh`, `late`: the ring colour of `LevelColor.GREEN` / `LevelColor.AMBER` in the theme's `WidgetPalette`), `widget/LayoutSpec.kt` (`dot = 8f`, `dotRing = 2f`, in `scaled`), `widget/WidgetParts.kt` (`internal fun FreshnessBadge(late: Boolean, dot: Float, ring: Float, colors: WidgetColors)`; `CircleButton` takes `badge: (@Composable () -> Unit)?`), `widget/WidgetTexts.kt` (`refreshDescription(context, refreshing: Boolean, ageMinutes: Long): String`), `values*/strings.xml`
- Test: `domain/WidgetModelTest.kt`, `widget/WidgetContentTest.kt`, `widget/WidgetColorsTest.kt`

**Interfaces:**
- Consumes: `Freshness.isLate`, `Freshness.ageMinutes` (Task 1).
- Produces: `FreshnessBadge(...)` (used by Task 4), `WidgetColors.fresh` / `.late`, strings `widget_refresh_age` = "Refresh. Data is %1$d min old" (UK "Оновити. Дані %1$d хв тому"), `widget_refreshing_age` = "Refreshing… Data is %1$d min old" (UK "Оновлення… Дані %1$d хв тому"); test tags `dot_fresh` / `dot_late` on the badge.

Badge tree (Glance has no per-child alignment): a `Box` of face + 1 dp holding two `fillMaxSize` boxes, the first aligns the face `BottomStart`, the second aligns the badge `TopEnd`. The badge is a `Box(dot + 2 × ring)` with two `Image(widget_dot)`: the outer tinted `colors.card`, the inner (`dot`) tinted `fresh` or `late`; the badge has no content description (the ↻ carries the age).

- [ ] **Step 1: Write the failing tests**

```kotlin
// WidgetModelTest
@Test fun data_lateAfter20Minutes_autoOff() // updated NOW − 21 min → dataLate true, dataAgeMinutes 21; NOW − 3 min → false, 3
// WidgetContentTest
@Test fun dot_freshThenLate() {
  render(model(updated = NOW - 3 * 60_000L)) {
    onNode(hasTestTag("dot_fresh")).assertExists(); onAllNodes(hasTestTag("dot_late")).assertCountEquals(0)
  }
  render(model(updated = NOW - 25 * 60_000L)) { onNode(hasTestTag("dot_late")).assertExists() }
}
@Test fun refreshDescription_saysAge() {
  render(model(updated = NOW - 3 * 60_000L)) {
    onNode(hasRunCallbackClickAction<NoOpRefresh>()).assert(hasContentDescription("Refresh. Data is 3 min old"))
  }
  render(model(updated = NOW - 3 * 60_000L, refreshingSince = NOW - 1_000L)) {
    onNode(hasRunCallbackClickAction<NoOpRefresh>()).assert(hasContentDescription("Refreshing… Data is 3 min old"))
  }
}
@Test fun emptyStates_haveNoDot() // SETUP and NO_DATA: no dot_fresh, no dot_late
// WidgetColorsTest
@Test fun dotColours() // LIGHT: fresh #43A047, late #F59E0B; DARK: #66BB6A, #FFB74D
```

Update `refreshing_keepsButtonWithRefreshingDescription` to the new texts.

- [ ] **Step 2: Run them to see them fail** — `./gradlew testDebugUnitTest --tests '*WidgetModelTest' --tests '*WidgetContentTest' --tests '*WidgetColorsTest'`. Expected: compile FAIL (`dataLate`, `fresh`).

- [ ] **Step 3: Implement** as in Files. Drop `widget_refresh_action` / `widget_refreshing` only if nothing else uses them (grep).

- [ ] **Step 4: Run the tests** — same command, then `./gradlew testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(widget): Show the freshness dot on the battery widget`

---

### Task 4: ↻, dot and ⚠ on the inverter strip

**Files:**
- Modify: `domain/InverterWidgetModel.kt` (`Data.refreshing`, `Data.dataLate`, `Data.dataAgeMinutes`; `from(cache, settings, hasToken, nowMillis: Long)`), `widget/InverterLayoutSpec.kt` (`StripSizes`: `refresh = 28f`, `refreshIcon = 15f`, `alertIcon = 11f`, `alertTop = 2.5f`, `dot = 8f`, `dotRing = 2f`, all in `scaled`), `widget/InverterFonts.kt` (`internal fun pillWidth(spec: InverterLayoutSpec, hostScale: Float, modeWidth: Float): Float`; no ⚠ term), `widget/InverterWidgetContent.kt` (new `refresh: Action?` parameter after `openApp`; the tree below), `widget/InverterWidget.kt` (pass `actionRunCallback<RefreshAction>()` and `System.currentTimeMillis()`), the preview call sites (`refresh = null`)
- Test: `domain/InverterWidgetModelTest.kt`, `widget/InverterLayoutSpecTest.kt`, `widget/InverterFontsTest.kt`, `widget/InverterWidgetContentTest.kt` (add `class NoOpRefresh : ActionCallback`)

**Interfaces:**
- Consumes: `Freshness` (Task 1), `FreshnessBadge`, `WidgetColors.fresh/late`, `WidgetTexts.refreshDescription` (Task 3), `RefreshAction`.

Tree: the card `Box` keeps background, corner and the open-app tap but no padding. Inside, a `Row(fillMaxSize)`: (1) the pills `Box(defaultWeight, fillMaxHeight, padding(start = padding, top = padding, bottom = padding, end = gap))` holding today's mode + voltage row without the end ⚠; (2) the ↻ column `Column(width = refresh + padding, fillMaxHeight, padding(end = padding))`: `Spacer(alertTop)`, the ⚠ (`ic_alert`, `alertIcon`, `colors.bad`, content description "Sync error: <reason>") or a `Spacer(alertIcon)`, `Spacer(1)`, the ↻ `CircleButton`-like box (face `refresh`, icon `refreshIcon`, dims while refreshing, `FreshnessBadge`). Empty states: today's tree, no ↻ column.

- [ ] **Step 1: Write the failing tests**

```kotlin
// InverterWidgetModelTest
@Test fun stripDot_followsInverterPart() // battery read NOW − 1 min, inverter NOW − 25 min, auto off → Data.dataLate true
@Test fun refreshing_sharedMark()        // refreshingSince NOW − 10 s → Data.refreshing true (the strip's ↻ dims)
// InverterLayoutSpecTest
@Test fun scaled_includesRefreshSizes() {
  val s = InverterLayoutSpec.S3X1.design.scaled(1.28f)
  assertEquals(35.84f, s.refresh, 0.01f); assertEquals(14.08f, s.alertIcon, 0.01f); assertEquals(3.2f, s.alertTop, 0.01f)
}
// InverterFontsTest
@Test fun pillWidth_3x1_leavesRoomForRefresh() = assertEquals(48f, InverterFonts.pillWidth(InverterLayoutSpec.S3X1, 1f, 44f), 0.01f)
// InverterWidgetContentTest
@Test fun data_hasRefreshWithDot()        // every spec: NoOpRefresh click action exists; dot_fresh exists
@Test fun error_showsAlertAboveRefresh()  // error = Unreachable: "Sync error: Home Assistant is unreachable" node exists AND NoOpRefresh exists; ic_alert count 1
@Test fun empty_hasNoRefresh()            // SETUP, NO_DATA: no NoOpRefresh, no dot_fresh / dot_late
@Test fun preview_noRefreshAction()       // refresh = null: no run-callback click action
```

Update `offGrid_bigIcon_4x1` (it asserts no `ic_alert`; still true without an error).

- [ ] **Step 2: Run them to see them fail** — `./gradlew testDebugUnitTest --tests '*Inverter*'`. Expected: compile FAIL (`refresh`, `pillWidth`, `dataLate`).

- [ ] **Step 3: Implement** as in Files and Tree.

- [ ] **Step 4: Run the tests** — same command, then `./gradlew testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(widget): Add ↻ and the freshness dot to the strip`

---

### Task 5: Redraw when a dot turns amber

**Files:**
- Modify: `domain/port/Ports.kt` (`SyncScheduler.redrawAt(atMillis: Long)`), `system/WorkManagerSyncScheduler.kt`, `widget/StoredContent.kt`, `testing/Fakes.kt` (`FakeSyncScheduler.redraws: List<Long>`), `gradle/libs.versions.toml` + `app/build.gradle.kts` (`testImplementation` `androidx.work:work-testing` at the `work` version)
- Create: `system/RedrawWorker.kt` (`CoroutineWorker`: `applicationContext.container.widgetRefresher.refreshAll()`, `Result.success()`)
- Test: `app/src/test/.../system/WorkManagerSyncSchedulerTest.kt` (new)

**Interfaces:**
- Consumes: `Freshness.nextLateAt` (Task 1).
- Produces: `SyncScheduler.redrawAt(atMillis: Long)`: unique one-time work `"widget_redraw_late"`, `ExistingWorkPolicy.REPLACE`, initial delay `max(0, atMillis − now)`, no constraints.

In `provideStoredContent`, inside `key(config)`: `val lateAt = Freshness.nextLateAt(cache, settings, hasToken, System.currentTimeMillis())` and `LaunchedEffect(lateAt) { lateAt?.let(container.syncScheduler::redrawAt) }`. Both widgets compute the same moment, so REPLACE keeps one. The app preview composes the content directly and never runs `provideStoredContent`, so it schedules nothing.

- [ ] **Step 1: Write the failing test** (`WorkManagerTestInitHelper.initializeTestWorkManager(context)`)

```kotlin
@Test fun redrawAt_keepsOneWorkWithTheNewDelay() {
  scheduler.redrawAt(now + 10 * 60_000L)
  scheduler.redrawAt(now + 5 * 60_000L)
  val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_redraw_late").get()
    .filter { it.state == WorkInfo.State.ENQUEUED }
  assertEquals(1, infos.size)
  assertTrue(infos.single().initialDelayMillis in (4 * 60_000L)..(5 * 60_000L))
}
```

- [ ] **Step 2: Run it to see it fail** — `./gradlew testDebugUnitTest --tests '*WorkManagerSyncSchedulerTest'`. Expected: compile FAIL (`redrawAt`).

- [ ] **Step 3: Implement** as in Files.

- [ ] **Step 4: Run the tests** — same command, then `./gradlew testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(widget): Redraw when the freshness dot turns amber`

---

### Task 6: Refresh on unlock

**Files:**
- Modify: `domain/port/Ports.kt` (`SyncScheduler.syncWhenOnline()`), `system/WorkManagerSyncScheduler.kt`, `testing/Fakes.kt` (`syncWhenOnlineCalls`), `AppContainer.kt` (`val unlockRefresh: UnlockRefresh`, `hasWidgets` shared with the scheduler), `App.kt` (`onCreate` registers the receiver once)
- Create: `domain/UnlockRefresh.kt`, `system/UnlockReceiver.kt`
- Test: `domain/UnlockRefreshTest.kt`, `system/UnlockReceiverTest.kt`, `system/WorkManagerSyncSchedulerTest.kt`

**Interfaces:**
- Consumes: `RefreshPolicy.unlockSyncWanted` (Task 1).
- Produces:
  - `SyncScheduler.syncWhenOnline()`: unique one-time work `"battery_sync_unlock"`, `ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`, worker `SyncWorker`.
  - `class UnlockRefresh(settings: SettingsRepository, cache: ReadingCache, tokens: TokenStore, scheduler: SyncScheduler, widgets: WidgetRefresher, hasWidgets: suspend () -> Boolean, clock: () -> Long)` with `suspend operator fun invoke(): Boolean` — when wanted: `cache.update { it.copy(refreshingSinceMillis = now) }`, `widgets.refreshAll()`, `scheduler.syncWhenOnline()`, return true; else nothing, false. `hasToken` as `provideStoredContent` computes it.
  - `class UnlockReceiver(private val onUnlock: () -> Unit) : BroadcastReceiver` — calls `onUnlock` for `Intent.ACTION_USER_PRESENT` only.
  - `App.onCreate`: `ContextCompat.registerReceiver(this, UnlockReceiver { container.appScope.launch { container.unlockRefresh() } }, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// UnlockRefreshTest (fakes from testing/Fakes.kt, battery configured, reading 2 min old, hasWidgets = true)
@Test fun unlock_marksRefreshingAndQueuesOnlineSync() {
  assertTrue(runBlocking { refresh() })
  assertEquals(NOW, cache.current.refreshingSinceMillis); assertEquals(1, widgets.refreshes)
  assertEquals(1, scheduler.syncWhenOnlineCalls); assertEquals(0, scheduler.syncNowCalls)
}
@Test fun switchOff_doesNothing()                 // onUnlock = false → false, no calls
@Test fun secondUnlock_whileRefreshing_doesNothing() // call twice at NOW and NOW + 5 s → syncWhenOnlineCalls 1
// UnlockReceiverTest (Robolectric)
@Test fun userPresent_callsOnce()   // registerReceiver + sendBroadcast(Intent(ACTION_USER_PRESENT)) + idle main looper → 1
@Test fun otherAction_ignored()     // ACTION_SCREEN_ON → 0
// WorkManagerSyncSchedulerTest
@Test fun syncWhenOnline_needsNetwork() // unique "battery_sync_unlock" ENQUEUED, constraints.requiredNetworkType == CONNECTED
```

- [ ] **Step 2: Run them to see them fail** — `./gradlew testDebugUnitTest --tests '*UnlockRefreshTest' --tests '*UnlockReceiverTest' --tests '*WorkManagerSyncSchedulerTest'`. Expected: compile FAIL.

- [ ] **Step 3: Implement** as in Files and Interfaces.

- [ ] **Step 4: Run the tests** — same command, then `./gradlew testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(refresh): Refresh when the phone is unlocked`

---

### Task 7: The unlock switch in the app

**Files:**
- Modify: `ui/sections/RefreshSection.kt` (a `SwitchRow` under the interval field, its hint text under it; `onSave: (auto: Boolean, intervalText: String, onUnlock: Boolean) -> Unit`; saved with the same Save button), `ui/MainViewModel.kt` (`saveRefresh(auto: Boolean, intervalText: String, onUnlock: Boolean)` → `RefreshSettings(auto, minutes, onUnlock)`), `ui/MainScreen.kt` (`actions.saveRefresh` type), `values*/strings.xml`
- Test: `ui/MainViewModelTest.kt`

Strings: `refresh_on_unlock` = "Refresh when the phone is unlocked" (UK "Оновлювати після розблокування"); `refresh_on_unlock_hint` = "Gets fresh data when you look. Works while Android keeps the app running; opening the app or the next timed sync starts it again." (UK "Свіжі дані, коли ви дивитеся. Працює, поки Android тримає застосунок запущеним; його знову запускає відкриття застосунку або наступне оновлення за таймером.").

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun saveRefresh_keepsUnlockChoice() {
  vm.saveRefresh(auto = true, intervalText = "30", onUnlock = false)
  advanceUntilIdle()
  assertEquals(RefreshSettings(true, 30, onUnlock = false), settingsRepo.settings.value.refresh)
}
```

Update the other `saveRefresh` calls in the test to the new signature.

- [ ] **Step 2: Run it to see it fail** — `./gradlew testDebugUnitTest --tests '*MainViewModelTest'`. Expected: compile FAIL.

- [ ] **Step 3: Implement** as in Files.

- [ ] **Step 4: Run the tests** — `./gradlew testDebugUnitTest`. Expected: PASS, `NoHardcodedTextTest` included.

- [ ] **Step 5: Commit** — `feat(settings): Add the refresh-on-unlock switch`

---

### Task 8: Docs and gate

**Files:**
- Modify: `docs/ai/agents/architecture.md` — packages: drop `ToggleAutoRefresh` and "auto-toggle actions", add `Freshness`, `UnlockRefresh`, `UnlockReceiver`, `RedrawWorker`; One sync path: add the unlock trigger ("marks refreshing and enqueues the unlock sync, which waits for a network"); stored data: add `RefreshSettings.onUnlock` (default on, spec 0016) to the exceptions; spec list: add 0016.

- [ ] **Step 1: Edit** the file as listed.

- [ ] **Step 2: Gate**

Run: `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL; lint 0 errors, no new warnings (25 today).

- [ ] **Step 3: Commit** — `ai: Update the architecture notes for spec 0016`

- [ ] **Step 4: Phone check by the owner** (debug APK, `adb -s <phone> install -r` after the `ro.serialno` check):
  - unlocking refreshes both widgets within seconds;
  - the dot turns amber 20 min after the last sync with the timed sync off;
  - a sync error shows the ⚠ above ↻ on the strip (if it looks cramped: the spec's fallback, ⚠ instead of ↻);
  - the battery widget has one ↻; the app shows the new switch, on.
