package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.SyncBattery
import io.lumensync.habattery.domain.ToggleAutoRefresh
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontSizes
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.FakeNotifier
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.FakeWidgetRefresher
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
  ): MainViewModel {
    settings = FakeSettingsRepository(initial)
    tokens = FakeTokenStore(token)
    source = FakeBatterySource(SyncResult.Success(reading()))
    val cache = FakeReadingCache()
    val sync = SyncBattery(settings, tokens, batterySource ?: source, cache, FakeNotifier(), widgets)
    val toggle = ToggleAutoRefresh(settings, scheduler, widgets)
    val vm = MainViewModel(
        settings, tokens, cache, sync, toggle, scheduler, widgets, batterySource ?: source, clock = { 1_000L })
    backgroundScope.launch { vm.uiState.collect {} }
    return vm
  }

  private lateinit var current: MainViewModel
  private val state get() = current.uiState.value

  private fun TestScope.start(
    initial: Settings = configuredSettings(),
    token: String? = "t",
    source: BatterySource? = null,
  ) =
    vm(initial, token, source).also {
      current = it
      advanceUntilIdle()
    }

  @Test fun uiState_carriesTheWidgetModel() = runTest(dispatcher) {
    start()
    val widget = state.widget
    assertTrue("$widget", widget is WidgetModel.Data)
    assertEquals(50, (widget as WidgetModel.Data).socPercent)
  }

  @Test fun toggleAutoRefresh_turnsOnWith15AndSyncs() = runTest(dispatcher) {
    val vm = start()
    vm.toggleAutoRefresh()
    advanceUntilIdle()
    assertEquals(RefreshSettings(true, 15), state.settings.refresh)
    assertEquals(1, scheduler.syncNowCalls)
    assertTrue((state.widget as WidgetModel.Data).autoRefresh)
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
    assertEquals("abc", tokens.get())
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
    vm.setNotifyOnFull(true)
    advanceUntilIdle()
    assertEquals("http://ha.local:8123", state.settings.connection.url)
    assertNull(state.settings.capacityAh)
    assertFalse(state.settings.notifyOnFull)
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

  @Test fun saveFontSizes_clamps() = runTest(dispatcher) {
    val vm = start()
    vm.saveFontSizes(FontSizes(soc = 50f, small = 11.5f))
    advanceUntilIdle()
    assertEquals(FontSizes(soc = 42f, small = 11.5f), state.settings.fontSizes)
  }

  @Test fun setNotifyOnFull_saves() = runTest(dispatcher) {
    val vm = start()
    vm.setNotifyOnFull(true)
    advanceUntilIdle()
    assertTrue(state.settings.notifyOnFull)
    assertEquals(1, source.calls)
  }

  @Test fun refreshNow_setsSyncingWhileRunning() = runTest(dispatcher) {
    val gate = CompletableDeferred<Unit>()
    val blocking =
      object : BatterySource {
        override suspend fun fetch(
          connection: ConnectionSettings,
          entities: EntitySettings,
          token: String,
        ): SyncResult {
          gate.await()
          return SyncResult.Success(reading())
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
}
