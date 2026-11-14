package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.SyncBattery
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.Settings
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
    val vm = MainViewModel(settings, tokens, cache, sync, scheduler, widgets, clock = { 1_000L })
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

  @Test fun init_syncsOnce() = runTest(dispatcher) {
    start()
    assertEquals(1, source.calls)
  }

  @Test fun saveConnection_trimsAndSaves() = runTest(dispatcher) {
    val vm = start()
    vm.saveConnection("  http://192.168.1.2:8123/ ", "")
    advanceUntilIdle()
    assertEquals("http://192.168.1.2:8123/", state.settings.connection.primaryUrl)
    assertTrue(widgets.refreshes >= 1)
    assertTrue(scheduler.applied.last().second)
    assertEquals(2, source.calls)
  }

  @Test fun saveConnection_insecure_keepsSettings() = runTest(dispatcher) {
    val vm = start()
    vm.saveConnection("http://example.com", "")
    advanceUntilIdle()
    assertEquals(listOf(FieldError(Field.PRIMARY_URL, Problem.INSECURE)), state.errors)
    assertEquals("http://ha.local:8123", state.settings.connection.primaryUrl)
    assertEquals(1, source.calls)
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
      }
    val vm = start(source = blocking)
    assertTrue(state.syncing)
    gate.complete(Unit)
    advanceUntilIdle()
    assertFalse(state.syncing)
    assertEquals(50.0, vm.uiState.value.overview?.reading?.chargePercent)
  }
}
