package io.lumensync.habattery.domain.port

import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.TokenType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
  val settings: Flow<Settings>

  suspend fun update(transform: (Settings) -> Settings)
}

interface ReadingCache {
  val state: Flow<CacheState>

  suspend fun update(transform: (CacheState) -> CacheState)
}

/** One token per [TokenType]; callers use the slot of the active type in the settings. */
interface TokenStore {
  val savedTypes: Flow<Set<TokenType>>

  /** Null when the [type] slot is empty or cannot be read; an unreadable slot is cleared. */
  suspend fun get(type: TokenType): String?

  /** Stores [token] trimmed; a blank one clears the slot. */
  suspend fun set(type: TokenType, token: String)

  suspend fun clear(type: TokenType)
}

/** Reads Home Assistant for both widgets: the battery part and the inverter part of a sync. */
interface BatterySource {
  /**
   * Judges each asked part on its own (spec 0012): a part without a usable reading fails with
   * `BadResponse` and the other part still reads. A failure of the whole request (`Unreachable`,
   * `Unauthorized`, `HttpError`, a body that is not JSON) fails every asked part.
   */
  suspend fun fetch(
    connection: ConnectionSettings,
    request: FetchRequest,
    token: String,
  ): FetchResult

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

interface LevelNotifier {
  /**
   * Whether the system lets the app post: permission granted and app notifications enabled. The
   * main switch is not checked here.
   */
  fun canPost(): Boolean

  fun post(alert: LevelAlert)
}

interface SyncScheduler {
  suspend fun apply(settings: Settings, settingsChanged: Boolean)

  fun syncNow()
}
