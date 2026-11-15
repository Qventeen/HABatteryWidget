package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
  val connection: ConnectionSettings = ConnectionSettings(),
  val entities: EntitySettings = EntitySettings(),
  val refresh: RefreshSettings = RefreshSettings(),
  val notifyOnFull: Boolean = false,
  val capacityAh: Double? = null,
)

@Serializable
data class ConnectionSettings(val primaryUrl: String = "", val fallbackUrl: String = "")

@Serializable
data class EntitySettings(
  val charge: String = "",
  val power: String = "",
  val current: String = "",
  val totalVoltage: String = "",
)

@Serializable
data class RefreshSettings(val auto: Boolean = false, val intervalMinutes: Int? = null) {
  companion object {
    const val MIN_INTERVAL = 15
    const val MAX_INTERVAL = 1440
  }
}
