package io.lumensync.habattery.testing

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.domain.port.FullChargeNotifier
import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
  private val state = MutableStateFlow(initial)
  override val settings = state

  override suspend fun update(transform: (Settings) -> Settings) {
    state.value = transform(state.value)
  }
}

class FakeTokenStore(initial: String? = null) : TokenStore {
  private val token = MutableStateFlow(initial?.trim()?.takeIf { it.isNotEmpty() })
  override val hasToken = token.map { it != null }

  override suspend fun get(): String? = token.value

  override suspend fun set(token: String) {
    this.token.value = token.trim().takeIf { it.isNotEmpty() }
  }

  override suspend fun clear() {
    token.value = null
  }
}

/** Returns [results] in order; the last one repeats. Suspends once per call, so parallel runs interleave. */
class FakeBatterySource(private vararg val results: SyncResult) : BatterySource {
  var calls = 0
    private set

  override suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult {
    val result = results[minOf(calls, results.lastIndex)]
    calls++
    yield()
    return result
  }
}

class FakeReadingCache(initial: CacheState = CacheState()) : ReadingCache {
  private val flow = MutableStateFlow(initial)
  override val state = flow
  val current: CacheState
    get() = flow.value

  override suspend fun update(transform: (CacheState) -> CacheState) {
    flow.value = transform(flow.value)
  }
}

class FakeNotifier : FullChargeNotifier {
  var shown = 0
    private set

  override fun showFullCharge() {
    shown++
  }
}

class FakeWidgetRefresher : WidgetRefresher {
  var refreshes = 0
    private set

  override suspend fun refreshAll() {
    refreshes++
  }
}

class FakeSyncScheduler : SyncScheduler {
  val applied = mutableListOf<Pair<Settings, Boolean>>()
  var syncNowCalls = 0
    private set

  override suspend fun apply(settings: Settings, settingsChanged: Boolean) {
    applied += settings to settingsChanged
  }

  override fun syncNow() {
    syncNowCalls++
  }
}

fun configuredSettings(notifyOnFull: Boolean = false): Settings =
  Settings(
    connection = ConnectionSettings(primaryUrl = "http://ha.local:8123"),
    entities = EntitySettings(charge = "sensor.soc"),
    notifyOnFull = notifyOnFull,
  )

fun reading(charge: Double = 50.0, power: Double? = 0.0): BatteryReading =
  BatteryReading(chargePercent = charge, powerW = power, updatedAtMillis = 1_000L)
