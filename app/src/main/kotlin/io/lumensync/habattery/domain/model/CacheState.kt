package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/** [reading] and [error] are the battery part's; each part keeps its last good reading. */
@Serializable
data class CacheState(
  val reading: BatteryReading? = null,
  val error: SyncError? = null,
  val refreshingSinceMillis: Long? = null,
  val levelAlerts: Map<String, LevelAlertState> = emptyMap(),
  val inverterReading: InverterReading? = null,
  val inverterError: SyncError? = null,
)
