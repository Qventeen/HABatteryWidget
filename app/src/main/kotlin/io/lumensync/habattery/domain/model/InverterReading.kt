package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/**
 * One read of the inverter sensors. [mode] is the trimmed ESP32 text and never a no-value text;
 * a missing part is `null`.
 */
@Serializable
data class InverterReading(
  val mode: String? = null,
  val gridVoltageV: Double? = null,
  val outputVoltageV: Double? = null,
  val updatedAtMillis: Long,
)
