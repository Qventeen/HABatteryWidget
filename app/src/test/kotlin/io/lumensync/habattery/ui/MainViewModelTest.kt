package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.SyncBattery
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.customLevels
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.FakeLevelNotifier
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.batteryOk
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.reading
import java.io.IOException
import java.security.ProviderException
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var settings: FakeSettingsRepository
  private lateinit var tokens: FakeTokenStore
  private lateinit var source: FakeBatterySource
  private val widgets = FakeWidgetRefresher()
  private val scheduler = FakeSyncScheduler()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun TestScope.vm(
    initial: Settings = configuredSettings(),
    token: String? = "t",
    batterySource: BatterySource? = null,
    userToken: String? = null,
  ): MainViewModel {
    settings = FakeSettingsRepository(initial)
    tokens = FakeTokenStore(token, user = userToken)
    source = FakeBatterySource(batteryOk(reading()))
    val cache = FakeReadingCache()
    val sync = SyncBattery(settings, tokens, batterySource ?: source, cache, FakeLevelNotifier(), widgets)
    val vm = MainViewModel(
        settings, tokens, cache, sync, scheduler, widgets, batterySource ?: source, clock = { 1_000L })
    backgroundScope.launch { vm.uiState.collect {} }
    return vm
  }

  private lateinit var current: MainViewModel
  private val state get() = current.uiState.value

  private fun TestScope.start(
    initial: Settings = configuredSettings(),
    token: String? = "t",
    source: BatterySource? = null,
    userToken: String? = null,
  ) =
    vm(initial, token, source, userToken).also {
      current = it
      advanceUntilIdle()
    }

  @Test fun uiState_carriesTheWidgetModel() = runTest(dispatcher) {
    start()
    val widget = state.widget
    assertTrue("$widget", widget is WidgetModel.Data)
    assertEquals(50, (widget as WidgetModel.Data).socPercent)
  }

  @Test fun uiState_carriesTheInverterError() = runTest(dispatcher) {
    val inverterOnly = configuredSettings(battery = false, inverter = true)
    val unauthorized = FetchResult(null, PartResult.Failed(SyncError.Unauthorized))
    start(inverterOnly, source = FakeBatterySource(unauthorized))
    assertEquals(SyncError.Unauthorized, state.inverterError)
    assertTrue(state.showTokenTypeHint)
  }

  @Test fun init_syncsOnce() = runTest(dispatcher) {
    start()
    assertEquals(1, source.calls)
  }

  @Test fun saveConnection_trimsAndSaves() = runTest(dispatcher) {
    val vm = start()
    vm.saveConnection("  http://192.168.1.2:8123/ ")
    advanceUntilIdle()
    assertEquals("http://192.168.1.2:8123/", state.settings.connection.url)
    assertTrue(widgets.refreshes >= 1)
    assertTrue(scheduler.applied.last().second)
    assertEquals(2, source.calls)
  }

  @Test fun saveConnection_keepsTokenType() = runTest(dispatcher) {
    val vm =
      start(
        initial =
          configuredSettings()
            .copy(connection = ConnectionSettings("http://ha.local:8123", TokenType.USER)),
      )
    vm.saveConnection("http://other.local:8123")
    advanceUntilIdle()
    assertEquals(
      ConnectionSettings("http://other.local:8123", TokenType.USER),
      state.settings.connection,
    )
  }

  @Test fun saveConnection_publicHttp_saves() = runTest(dispatcher) {
    val vm = start()
    vm.saveConnection("http://example.com")
    advanceUntilIdle()
    assertEquals(emptyList<FieldError>(), state.errors)
    assertEquals("http://example.com", state.settings.connection.url)
  }

  @Test fun saveToken_neverInState() = runTest(dispatcher) {
    val vm = start(token = null)
    vm.saveToken(" abc ")
    advanceUntilIdle()
    assertEquals("abc", tokens.get(TokenType.ADMIN))
    assertTrue(state.hasToken)
    assertFalse(state.toString().contains("abc"))
  }

  @Test fun saveToken_blank_isRequired() = runTest(dispatcher) {
    val vm = start(token = null)
    vm.saveToken("  ")
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.TOKEN, Problem.REQUIRED)), state.errors)
  }

  @Test fun saveToken_keystoreFails_isInvalidWithoutLeak() = runTest(dispatcher) {
    val vm = start(token = null)
    tokens.setFailure = ProviderException("secret-abc")
    vm.saveToken("abc")
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.TOKEN, Problem.INVALID)), state.errors)
    assertFalse(state.hasToken)
    assertFalse(state.toString().contains("abc"))
    assertEquals(0, source.calls)
  }

  @Test fun saveToken_writesActiveSlot() = runTest(dispatcher) {
    val vm = start(initial = configuredSettings(tokenType = TokenType.USER), token = "admin-t")
    vm.saveToken("u")
    advanceUntilIdle()
    assertEquals("u", tokens.get(TokenType.USER))
    assertEquals("admin-t", tokens.get(TokenType.ADMIN))
  }

  @Test fun saveTokenType_savesAndSyncs() = runTest(dispatcher) {
    val vm = start(token = "admin-t", userToken = "user-t")
    val before = source.calls
    vm.saveTokenType(TokenType.USER)
    advanceUntilIdle()
    assertEquals(TokenType.USER, state.settings.connection.tokenType)
    assertEquals(before + 1, source.calls)
    assertEquals("user-t", source.lastToken)
  }

  @Test fun removeToken_clearsOnlyActiveSlot() = runTest(dispatcher) {
    val vm =
      start(
        initial = configuredSettings(tokenType = TokenType.USER),
        token = "admin-t",
        userToken = "user-t",
      )
    val calls = source.calls
    val refreshes = widgets.refreshes
    vm.removeToken()
    advanceUntilIdle()
    assertNull(tokens.get(TokenType.USER))
    assertEquals("admin-t", tokens.get(TokenType.ADMIN))
    assertFalse(state.hasToken)
    assertTrue(widgets.refreshes > refreshes)
    assertEquals(calls, source.calls)
  }

  @Test fun hasToken_followsActiveType() = runTest(dispatcher) {
    val vm = start(token = "admin-t")
    assertTrue(state.hasToken)
    vm.saveTokenType(TokenType.USER)
    advanceUntilIdle()
    assertFalse(state.hasToken)
  }

  @Test fun refreshNow_storageThrows_endsNotSyncing() = runTest(dispatcher) {
    val vm = start()
    source.failWith = IOException("disk")
    vm.refreshNow()
    advanceUntilIdle()
    assertFalse(state.syncing)
  }

  @Test fun saveSettings_writeFails_keepsSettingsWithoutCrash() = runTest(dispatcher) {
    val vm = start()
    settings.updateFailure = IOException("disk")
    vm.saveConnection("http://192.168.1.2:8123")
    vm.saveCapacity("100", Locale.US)
    vm.setNotificationsOn(true)
    advanceUntilIdle()
    assertEquals("http://ha.local:8123", state.settings.connection.url)
    assertNull(state.settings.capacityAh)
    assertFalse(state.settings.notificationsOn)
    assertEquals(1, source.calls)
    settings.updateFailure = null
    vm.saveCapacity("100", Locale.US)
    advanceUntilIdle()
    assertEquals(100.0, state.settings.capacityAh)
  }

  @Test fun saveRefresh_requiresInterval() = runTest(dispatcher) {
    val vm = start()
    vm.saveRefresh(true, "")
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.INTERVAL, Problem.REQUIRED)), state.errors)
    vm.saveRefresh(true, "30")
    advanceUntilIdle()
    assertTrue(state.errors.isEmpty())
    assertEquals(30, state.settings.refresh.intervalMinutes)
    assertTrue(scheduler.applied.last().second)
  }

  @Test fun saveRefresh_notANumber_isInvalid() = runTest(dispatcher) {
    val vm = start()
    vm.saveRefresh(true, "abc")
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.INTERVAL, Problem.INVALID)), state.errors)
  }

  @Test fun saveCapacity_ukrainianComma() = runTest(dispatcher) {
    val vm = start()
    vm.saveCapacity("314,5", Locale.forLanguageTag("uk"))
    advanceUntilIdle()
    assertEquals(314.5, state.settings.capacityAh)
    vm.saveCapacity("", Locale.US)
    advanceUntilIdle()
    assertNull(state.settings.capacityAh)
    vm.saveCapacity("0", Locale.US)
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.CAPACITY, Problem.OUT_OF_RANGE)), state.errors)
    vm.saveCapacity("x", Locale.US)
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.CAPACITY, Problem.INVALID)), state.errors)
  }

  @Test fun saveEntities_chargeRequired() = runTest(dispatcher) {
    val vm = start()
    vm.saveEntities(EntitySettings(charge = " "))
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.CHARGE, Problem.REQUIRED)), state.errors)
    assertEquals("sensor.soc", state.settings.entities.charge)
    vm.saveEntities(EntitySettings(charge = " sensor.new "))
    advanceUntilIdle()
    assertEquals("sensor.new", state.settings.entities.charge)
    assertTrue(state.errors.isEmpty())
  }

  @Test fun saveEntities_trimsEveryFieldAndEachTemperature() = runTest(dispatcher) {
    val vm = start()
    vm.saveEntities(
      EntitySettings(
        prefix = " bps_bms ",
        charge = " sensor.soc ",
        totalVoltage = " sensor.v ",
        current = " sensor.i ",
        power = " sensor.p ",
        capacityRemaining = " sensor.ah ",
        averageCellVoltage = " sensor.avg ",
        deltaCellVoltage = " sensor.delta ",
        mosfetTemperature = " sensor.mosfet ",
        temperatures = listOf(" sensor.t1 ", "sensor.t2"),
        balancing = " binary_sensor.bal ",
      )
    )
    advanceUntilIdle()
    assertTrue(state.errors.isEmpty())
    assertEquals(
      EntitySettings(
        prefix = "bps_bms",
        charge = "sensor.soc",
        totalVoltage = "sensor.v",
        current = "sensor.i",
        power = "sensor.p",
        capacityRemaining = "sensor.ah",
        averageCellVoltage = "sensor.avg",
        deltaCellVoltage = "sensor.delta",
        mosfetTemperature = "sensor.mosfet",
        temperatures = listOf("sensor.t1", "sensor.t2"),
        balancing = "binary_sensor.bal",
      ),
      state.settings.entities,
    )
  }

  @Test fun saveDisplay_storesAndRedraws() = runTest(dispatcher) {
    val vm = start()
    val before = widgets.refreshes
    vm.saveDisplay(DisplaySettings(UnderSoc.AH, MainValue.POWER))
    advanceUntilIdle()
    assertEquals(DisplaySettings(UnderSoc.AH, MainValue.POWER), state.settings.display)
    assertTrue(widgets.refreshes > before)
    assertEquals(1, source.calls)
  }

  @Test fun saveAppearance_storesAndRedraws() = runTest(dispatcher) {
    val vm = start()
    val before = widgets.refreshes
    vm.saveAppearance(AppearanceSettings(ThemeChoice.DARK))
    advanceUntilIdle()
    assertEquals(ThemeChoice.DARK, state.settings.appearance.theme)
    assertTrue(widgets.refreshes > before)
    assertEquals(1, source.calls)
  }

  @Test fun saveFontPercents_clamps() = runTest(dispatcher) {
    val vm = start()
    vm.saveFontPercents(FontPercents(soc = 120, small = 75))
    advanceUntilIdle()
    assertEquals(FontPercents(soc = 100, small = 75), state.settings.fontPercents)
  }

  @Test fun saveHomeScale_clampsAndRedraws() = runTest(dispatcher) {
    val vm = start()
    val before = widgets.refreshes
    vm.saveHomeScale(128)
    advanceUntilIdle()
    assertEquals(128, state.settings.homeScalePercent)
    assertTrue(widgets.refreshes > before)
    vm.saveHomeScale(201)
    advanceUntilIdle()
    assertEquals(200, state.settings.homeScalePercent)
    vm.saveHomeScale(79)
    advanceUntilIdle()
    assertEquals(80, state.settings.homeScalePercent)
    assertEquals(1, source.calls)
  }

  @Test fun resetWidgetSettings_setsFontsTo50AndScaleTo100() = runTest(dispatcher) {
    val vm = start()
    vm.saveFontPercents(FontPercents(soc = 90, small = 30))
    vm.saveHomeScale(128)
    advanceUntilIdle()
    val before = widgets.refreshes
    vm.resetWidgetSettings()
    advanceUntilIdle()
    assertEquals(FontPercents(), state.settings.fontPercents)
    assertEquals(100, state.settings.homeScalePercent)
    assertTrue(widgets.refreshes > before)
  }

  @Test fun setNotificationsOn_saves() = runTest(dispatcher) {
    val vm = start()
    vm.setNotificationsOn(true)
    advanceUntilIdle()
    assertTrue(state.settings.notificationsOn)
    assertEquals(1, source.calls)
  }

  @Test fun refreshNow_setsSyncingWhileRunning() = runTest(dispatcher) {
    val gate = CompletableDeferred<Unit>()
    val blocking =
      object : BatterySource {
        override suspend fun fetch(
          connection: ConnectionSettings,
          request: FetchRequest,
          token: String,
        ): FetchResult {
          gate.await()
          return batteryOk(reading())
        }

        override suspend fun listTemperatureSensors(
          connection: ConnectionSettings,
          prefix: String,
          token: String,
        ): SensorList = SensorList.Found(emptyList())
      }
    val vm = start(source = blocking)
    assertTrue(state.syncing)
    gate.complete(Unit)
    advanceUntilIdle()
    assertFalse(state.syncing)
    assertEquals(50.0, vm.uiState.value.overview?.reading?.chargePercent)
  }

  @Test fun findTemperatureSensors_found() = runTest(dispatcher) {
    val vm = start()
    val ids = listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2")
    source.sensors = SensorList.Found(ids)
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    assertEquals(SensorSearch.Found(ids), result.await())
    assertEquals(listOf("bms"), source.sensorCalls)
  }

  @Test fun findTemperatureSensors_noneFound() = runTest(dispatcher) {
    val vm = start()
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    assertEquals(SensorSearch.NoneFound, result.await())
  }

  @Test fun findTemperatureSensors_failed() = runTest(dispatcher) {
    val vm = start()
    source.sensors = SensorList.Failed(SyncError.Unauthorized)
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    assertEquals(SensorSearch.Failed(SyncError.Unauthorized), result.await())
  }

  @Test fun sensorSearch_usesActiveTypeToken() = runTest(dispatcher) {
    val vm =
      start(
        initial = configuredSettings(tokenType = TokenType.USER),
        token = "admin-t",
        userToken = "user-t",
      )
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    result.await()
    assertEquals(listOf("bms"), source.sensorCalls)
    assertEquals("user-t", source.lastToken)
  }

  @Test fun findTemperatureSensors_skippedWithoutTokenOrUrl() = runTest(dispatcher) {
    listOf(
        start(token = null),
        start(initial = configuredSettings().copy(connection = ConnectionSettings(""))),
      )
      .forEach { vm ->
        val result = async { vm.findTemperatureSensors("bms") { true } }
        advanceUntilIdle()
        assertEquals(SensorSearch.Skipped, result.await())
        assertEquals(emptyList<String>(), source.sensorCalls)
      }
  }

  @Test fun findTemperatureSensors_droppedWhenTheDraftChangedDuringTheRequest() = runTest(dispatcher) {
    val vm = start()
    source.sensors = SensorList.Found(listOf("sensor.bms_temperature_sensor_1"))
    val gate = CompletableDeferred<Unit>()
    source.sensorGate = gate
    var current = true
    val result = async { vm.findTemperatureSensors("bms") { current } }
    advanceUntilIdle()
    assertEquals(listOf("bms"), source.sensorCalls) // the request is in flight
    current = false
    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(SensorSearch.Superseded, result.await())
  }

  @Test fun findTemperatureSensors_failingStorageIsSkippedQuietly() = runTest(dispatcher) {
    val vm = start()
    source.sensorFailure = IllegalStateException("disk")
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    assertEquals(SensorSearch.Skipped, result.await())
  }

  @Test fun findTemperatureSensors_failingTokenStoreIsSkippedQuietly() = runTest(dispatcher) {
    val vm = start()
    tokens.getFailure = java.security.GeneralSecurityException("keystore")
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    assertEquals(SensorSearch.Skipped, result.await())
  }

  @Test fun findTemperatureSensors_cancellationIsNotSwallowed() = runTest(dispatcher) {
    val vm = start()
    source.sensorGate = CompletableDeferred()
    val result = async { vm.findTemperatureSensors("bms") { true } }
    advanceUntilIdle()
    result.cancel()
    advanceUntilIdle()
    assertTrue(result.isCancelled)
  }

  @Test fun findTemperatureSensors_changesNothingUntilSave() = runTest(dispatcher) {
    val vm = start()
    val before = state.settings
    source.sensors = SensorList.Found(listOf("sensor.bms_temperature_sensor_1"))
    async { vm.findTemperatureSensors("bms") { true } }.await()
    advanceUntilIdle()
    assertEquals(before, state.settings)
  }

  @Test fun saveEntities_storesTheFoundList() = runTest(dispatcher) {
    val vm = start()
    val ids = listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2")
    vm.saveEntities(state.settings.entities.copy(prefix = "bms", temperatures = ids))
    advanceUntilIdle()
    assertEquals(ids, state.settings.entities.temperatures)
  }

  private fun draft(
    side: LevelSide,
    edge: String = "",
    id: Int = 0,
    name: String = "",
    notify: Boolean = false,
  ) = LevelDraft(id, side, edge, LevelType.NORMAL, null, null, name, null, notify)

  private val mid = draft(LevelSide.MIDDLE, id = 2)

  private fun edges(value: LevelValue) = state.settings.customLevels(value).levels.map { it.edge }

  @Test fun saveLevels_ordersSides() = runTest(dispatcher) {
    val vm = start()
    val saved =
      vm.saveLevels(
        LevelValue.CHARGE_CURRENT,
        listOf(
          mid,
          draft(LevelSide.ABOVE, "80", id = 3),
          draft(LevelSide.BELOW, "20", id = 1),
          draft(LevelSide.ABOVE, "50", id = 4, name = " Mid "),
        ),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertTrue(saved)
    val levels = state.settings.customLevels(LevelValue.CHARGE_CURRENT)
    assertTrue(levels.on)
    assertEquals(listOf<Double?>(20.0, null, 50.0, 80.0), levels.levels.map { it.edge })
    assertEquals(listOf(1, 2, 4, 3), levels.levels.map { it.id })
    assertEquals("Mid", levels.levels[2].name)
  }

  @Test fun saveLevels_middleEdgeIsIgnored() = runTest(dispatcher) {
    val vm = start()
    val saved =
      vm.saveLevels(
        LevelValue.SOC,
        listOf(draft(LevelSide.MIDDLE, "junk", id = 1), draft(LevelSide.ABOVE, "50", id = 2)),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertTrue(saved)
    assertEquals(listOf<Double?>(null, 50.0), edges(LevelValue.SOC))
  }

  @Test fun saveLevels_blankEdge_isRequired() = runTest(dispatcher) {
    val vm = start()
    val saved =
      vm.saveLevels(
        LevelValue.SOC,
        listOf(mid, draft(LevelSide.ABOVE, "  ", id = 1)),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertFalse(saved)
    assertEquals(listOf(FieldError(Field.LEVEL_EDGE, Problem.REQUIRED, 1)), state.errors)
    assertTrue(state.settings.colorLevels.isEmpty())
  }

  @Test fun saveLevels_unparsableEdge_isInvalid_andReportsAllErrorsAtDraftPositions() =
    runTest(dispatcher) {
      val vm = start()
      vm.saveLevels(
        LevelValue.SOC,
        listOf(
          mid,
          draft(LevelSide.ABOVE, "40", id = 1),
          draft(LevelSide.ABOVE, "x", id = 3),
          draft(LevelSide.ABOVE, "40", id = 4),
        ),
        "",
        Locale.US,
      )
      advanceUntilIdle()
      assertEquals(
        setOf(
          FieldError(Field.LEVEL_EDGE, Problem.INVALID, 2),
          FieldError(Field.LEVEL_EDGE, Problem.DUPLICATE, 3),
        ),
        state.errors.toSet(),
      )
      assertTrue(state.settings.colorLevels.isEmpty())
    }

  @Test fun saveLevels_duplicate_returnsFalse() = runTest(dispatcher) {
    val vm = start()
    val before = state.settings
    val saved =
      vm.saveLevels(
        LevelValue.SOC,
        listOf(mid, draft(LevelSide.ABOVE, "40", id = 1), draft(LevelSide.ABOVE, "40", id = 3)),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertFalse(saved)
    assertEquals(listOf(FieldError(Field.LEVEL_EDGE, Problem.DUPLICATE, 2)), state.errors)
    assertEquals(before, state.settings)
  }

  @Test fun saveLevels_overlap_returnsFalse() = runTest(dispatcher) {
    val vm = start()
    val before = state.settings
    val saved =
      vm.saveLevels(
        LevelValue.SOC,
        listOf(draft(LevelSide.BELOW, "40", id = 1), mid, draft(LevelSide.ABOVE, "41", id = 3)),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertFalse(saved)
    assertEquals(
      setOf(FieldError(Field.LEVEL_EDGE, Problem.OVERLAP, 0), FieldError(Field.LEVEL_EDGE, Problem.OVERLAP, 2)),
      state.errors.toSet(),
    )
    assertEquals(before, state.settings)
  }

  @Test fun saveLevels_gapOfOneNumber_saves() = runTest(dispatcher) {
    val vm = start()
    val saved =
      vm.saveLevels(
        LevelValue.SOC,
        listOf(draft(LevelSide.BELOW, "40", id = 1), mid, draft(LevelSide.ABOVE, "42", id = 3)),
        "",
        Locale.US,
      )
    advanceUntilIdle()
    assertTrue(saved)
    assertEquals(listOf<Double?>(40.0, null, 42.0), edges(LevelValue.SOC))
  }

  @Test fun saveLevels_negativeAndUkrainian() = runTest(dispatcher) {
    val uk = Locale.forLanguageTag("uk")
    val vm = start()
    vm.saveLevels(
      LevelValue.BATTERY_TEMPERATURE,
      listOf(draft(LevelSide.BELOW, "-10", id = 1), mid, draft(LevelSide.ABOVE, "45", id = 3)),
      "",
      uk,
    )
    vm.saveLevels(
      LevelValue.VOLTAGE,
      listOf(draft(LevelSide.BELOW, "51,2", id = 1), mid, draft(LevelSide.ABOVE, "55,2", id = 3)),
      "",
      uk,
    )
    advanceUntilIdle()
    assertEquals(listOf<Double?>(-10.0, null, 45.0), edges(LevelValue.BATTERY_TEMPERATURE))
    assertEquals(listOf<Double?>(51.2, null, 55.2), edges(LevelValue.VOLTAGE))
  }

  @Test fun saveLevels_roundsToShownPrecision() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(
      LevelValue.DELTA,
      listOf(mid, draft(LevelSide.ABOVE, "12.4", id = 1)),
      "",
      Locale.US,
    )
    advanceUntilIdle()
    assertEquals(listOf<Double?>(null, 12.0), edges(LevelValue.DELTA))
  }

  @Test fun useDefaults_dropsOwnLevels() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(LevelValue.SOC, listOf(mid, draft(LevelSide.ABOVE, "30", id = 1)), "", Locale.US)
    advanceUntilIdle()
    vm.useDefaults(LevelValue.SOC)
    advanceUntilIdle()
    assertEquals(CustomLevels(), state.settings.customLevels(LevelValue.SOC))
  }

  @Test fun saveLevels_keepsBells() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(
      LevelValue.SOC,
      listOf(
        draft(LevelSide.BELOW, "20", id = 1),
        draft(LevelSide.BELOW, "40", id = 3, notify = true),
        mid,
      ),
      "",
      Locale.US,
    )
    advanceUntilIdle()
    val bells = state.settings.customLevels(LevelValue.SOC).levels.associate { it.id to it.notify }
    assertEquals(mapOf(1 to false, 3 to true, 2 to false), bells)
  }

  private val socDrafts = listOf(mid, draft(LevelSide.ABOVE, "100", id = 1))

  private fun margin(value: LevelValue) = state.settings.customLevels(value).margin

  @Test fun saveLevels_margin() = runTest(dispatcher) {
    val uk = Locale.forLanguageTag("uk")
    val vm = start()
    assertTrue(vm.saveLevels(LevelValue.SOC, socDrafts, "4", Locale.US))
    advanceUntilIdle()
    assertEquals(4.0, margin(LevelValue.SOC))

    val before = state.settings
    assertFalse(vm.saveLevels(LevelValue.SOC, socDrafts, "-1", Locale.US))
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.LEVEL_MARGIN, Problem.INVALID)), state.errors)
    assertEquals(before, state.settings)

    assertTrue(vm.saveLevels(LevelValue.SOC, socDrafts, " ", Locale.US))
    advanceUntilIdle()
    assertNull(margin(LevelValue.SOC))

    val voltage = listOf(mid, draft(LevelSide.ABOVE, "55,2", id = 1))
    assertTrue(vm.saveLevels(LevelValue.VOLTAGE, voltage, "0,2", uk))
    advanceUntilIdle()
    assertEquals(0.2, margin(LevelValue.VOLTAGE))
  }

  @Test fun saveLevels_unparsableMargin_isInvalid() = runTest(dispatcher) {
    val vm = start()
    assertFalse(vm.saveLevels(LevelValue.SOC, socDrafts, "2 %", Locale.US))
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.LEVEL_MARGIN, Problem.INVALID)), state.errors)
    assertTrue(state.settings.colorLevels.isEmpty())
  }

  @Test fun saveLevels_marginAndEdgeErrors_areReportedTogether() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(LevelValue.SOC, listOf(mid, draft(LevelSide.ABOVE, "x", id = 1)), "-1", Locale.US)
    advanceUntilIdle()
    assertEquals(
      setOf(
        FieldError(Field.LEVEL_EDGE, Problem.INVALID, 1),
        FieldError(Field.LEVEL_MARGIN, Problem.INVALID),
      ),
      state.errors.toSet(),
    )
  }

  @Test fun saveLevels_marginRoundsToShownPrecision() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(LevelValue.SOC, socDrafts, "2.4", Locale.US)
    vm.saveLevels(
      LevelValue.VOLTAGE,
      listOf(mid, draft(LevelSide.ABOVE, "55.2", id = 1)),
      "0.123",
      Locale.US,
    )
    vm.saveLevels(
      LevelValue.CHARGE_CURRENT,
      listOf(mid, draft(LevelSide.ABOVE, "50", id = 1)),
      "4.6",
      Locale.US,
    )
    advanceUntilIdle()
    assertEquals(2.0, margin(LevelValue.SOC))
    assertEquals(0.12, margin(LevelValue.VOLTAGE))
    assertEquals(4.6, margin(LevelValue.CHARGE_CURRENT))
  }

  @Test fun saveLevels_currentMarginBelowOneAmp_isKept() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(
      LevelValue.CHARGE_CURRENT,
      listOf(mid, draft(LevelSide.ABOVE, "50", id = 1)),
      "0.5",
      Locale.US,
    )
    advanceUntilIdle()
    assertEquals(0.5, margin(LevelValue.CHARGE_CURRENT))
  }

  @Test fun useDefaults_clearsMargin() = runTest(dispatcher) {
    val vm = start()
    vm.saveLevels(LevelValue.SOC, socDrafts, "4", Locale.US)
    advanceUntilIdle()
    assertEquals(4.0, margin(LevelValue.SOC))
    vm.useDefaults(LevelValue.SOC)
    advanceUntilIdle()
    assertNull(margin(LevelValue.SOC))
  }
}
