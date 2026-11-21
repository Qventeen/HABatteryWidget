package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.ModeText
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SyncError
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
  @SerialName("inv_mode") val invMode: String? = null,
  @SerialName("inv_grid_v") val invGridV: Double? = null,
  @SerialName("inv_out_v") val invOutV: Double? = null,
) {
  /** An asked part without a reading fails with `BadResponse`; a part not asked stays `null`. */
  fun toFetchResult(request: FetchRequest, nowMillis: Long): FetchResult =
    FetchResult(
      battery = request.battery?.let { part(toReading(nowMillis)) },
      inverter = request.inverter?.let { part(toInverterReading(nowMillis)) },
    )

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

  /**
   * Returns null when the mode and both voltages have no value. A mode HA reports as no value
   * becomes null; a non-finite voltage becomes null.
   */
  fun toInverterReading(nowMillis: Long): InverterReading? {
    val mode = invMode?.takeUnless(ModeText::isNoValue)?.trim()
    val grid = invGridV.finiteOrNull()
    val output = invOutV.finiteOrNull()
    if (mode == null && grid == null && output == null) return null
    return InverterReading(mode, grid, output, nowMillis)
  }

  private fun <T> part(reading: T?): PartResult<T> =
    reading?.let { PartResult.Ok(it) } ?: PartResult.Failed(SyncError.BadResponse)

  private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
}

/** HA sends the raw state text; only clear on/off words count. */
internal fun parseBalancing(raw: String?): Boolean? =
  when (raw?.trim()?.lowercase()) {
    "on", "true", "1" -> true
    "off", "false", "0" -> false
    else -> null
  }
