package io.lumensync.habattery.domain.model

import kotlin.time.Duration
import kotlinx.serialization.Serializable

@Serializable
data class BatteryReading(
  val chargePercent: Double,
  val powerW: Double? = null,
  val currentA: Double? = null,
  val totalVoltageV: Double? = null,
  val capacityRemainingAh: Double? = null,
  val totalCapacityAh: Double? = null,
  val averageCellVoltageV: Double? = null,
  val deltaCellVoltageV: Double? = null,
  val mosfetTemperatureC: Double? = null,
  val temperaturesC: List<Double?> = emptyList(),
  val balancing: Boolean? = null,
  val updatedAtMillis: Long,
)

enum class BatteryState {
  FULL,
  CHARGING,
  DISCHARGING,
  IDLE,
  UNKNOWN,
}

enum class Direction {
  TO_EMPTY,
  TO_FULL,
}

data class TimeRemaining(val direction: Direction, val duration: Duration)
