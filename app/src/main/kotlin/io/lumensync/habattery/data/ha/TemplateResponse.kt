package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.BatteryReading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TemplateResponse(
  val soc: Double? = null,
  val power: Double? = null,
  val current: Double? = null,
  @SerialName("total_v") val totalV: Double? = null,
) {
  /** Returns null when charge is missing or not finite. Other non-finite values become null. */
  fun toReading(nowMillis: Long): BatteryReading? {
    val charge = soc?.takeIf { it.isFinite() } ?: return null
    return BatteryReading(
      chargePercent = charge,
      powerW = power.finiteOrNull(),
      currentA = current.finiteOrNull(),
      totalVoltageV = totalV.finiteOrNull(),
      updatedAtMillis = nowMillis,
    )
  }

  private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
}
