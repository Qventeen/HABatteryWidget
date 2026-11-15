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
  val prefix: String = "",
  val charge: String = "",
  val totalVoltage: String = "",
  val current: String = "",
  val power: String = "",
  val capacityRemaining: String = "",
  val averageCellVoltage: String = "",
  val deltaCellVoltage: String = "",
  val mosfetTemperature: String = "",
  val temperatures: List<String> = emptyList(),
  val balancing: String = "",
) {
  companion object {
    const val MAX_TEMPERATURES = 8
  }
}

@Serializable
data class RefreshSettings(val auto: Boolean = false, val intervalMinutes: Int? = null) {
  companion object {
    const val MIN_INTERVAL = 15
    const val MAX_INTERVAL = 1440
  }
}
