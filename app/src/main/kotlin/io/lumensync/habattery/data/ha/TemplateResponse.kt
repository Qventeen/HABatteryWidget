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
  @SerialName("ah_left") val ahLeft: Double? = null,
  val capacity: Double? = null,
  @SerialName("avg_cell") val avgCell: Double? = null,
  val delta: Double? = null,
  val mosfet: Double? = null,
  val temps: List<Double?> = emptyList(),
  val balancing: String? = null,
) {
  /** Returns null when charge is missing or not finite. Other non-finite values become null. */
  fun toReading(nowMillis: Long): BatteryReading? {
    val charge = soc?.takeIf { it.isFinite() } ?: return null
    return BatteryReading(
      chargePercent = charge,
      powerW = power.finiteOrNull(),
      currentA = current.finiteOrNull(),
      totalVoltageV = totalV.finiteOrNull(),
      capacityRemainingAh = ahLeft.finiteOrNull(),
      totalCapacityAh = capacity.finiteOrNull(),
      averageCellVoltageV = avgCell.finiteOrNull(),
      deltaCellVoltageV = delta.finiteOrNull(),
      mosfetTemperatureC = mosfet.finiteOrNull(),
      temperaturesC = temps.map { it.finiteOrNull() },
      balancing = parseBalancing(balancing),
      updatedAtMillis = nowMillis,
    )
  }

  private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
}

/** HA sends the raw state text; only clear on/off words count. */
internal fun parseBalancing(raw: String?): Boolean? =
  when (raw?.trim()?.lowercase()) {
    "on", "true", "1" -> true
    "off", "false", "0" -> false
    else -> null
  }
