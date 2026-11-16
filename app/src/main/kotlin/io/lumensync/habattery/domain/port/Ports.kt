package io.lumensync.habattery.domain.port

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
  val settings: Flow<Settings>

  suspend fun update(transform: (Settings) -> Settings)
}

interface ReadingCache {
  val state: Flow<CacheState>

  suspend fun update(transform: (CacheState) -> CacheState)
}

interface TokenStore {
  val hasToken: Flow<Boolean>

  suspend fun get(): String?

  suspend fun set(token: String)

  suspend fun clear()
}

interface BatterySource {
  suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult

  /** Asks HA for every `sensor.<prefix>_temperature_sensor_N`; only the Fill action calls it. */
  suspend fun listTemperatureSensors(
    connection: ConnectionSettings,
    prefix: String,
    token: String,
  ): SensorList
}

interface WidgetRefresher {
  suspend fun refreshAll()
}

interface FullChargeNotifier {
  fun showFullCharge()
}

interface SyncScheduler {
  suspend fun apply(settings: Settings, settingsChanged: Boolean)

  fun syncNow()
}
