package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CacheState(
  val reading: BatteryReading? = null,
  val error: SyncError? = null,
  val refreshingSinceMillis: Long? = null,
  val fullChargeNotified: Boolean = false,
)
