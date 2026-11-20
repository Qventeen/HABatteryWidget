package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelValue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The number a value's level is picked by, rounded as the widget shows that value. The widget
 * colour and the level alerts both pick by it, so they agree at an edge. It is not always the
 * number on the widget: the battery pill shows the mean of its sensors, and the current pill may
 * show watts.
 */
object ShownNumber {
  /**
   * The number [value]'s level is picked by in [reading], or `null` when the reading lacks it.
   * [levels] are the value's levels; only the battery temperature needs them, as its number is
   * that of the sensor whose level ranks worst (the first one on a tie). Each current counts as
   * 0 A while the battery is idle or the current flows the other way.
   */
  fun of(value: LevelValue, reading: BatteryReading, levels: List<ColorLevel>): Double? =
    when (value) {
      LevelValue.SOC -> soc(reading).toDouble()
      LevelValue.VOLTAGE -> reading.totalVoltageV?.roundTo(value.decimals)
      LevelValue.CHARGE_CURRENT ->
        reading.currentA?.let { if (it > BatteryMath.IDLE_CURRENT_A) amps(it, value) else 0.0 }
      LevelValue.DISCHARGE_CURRENT ->
        reading.currentA?.let { if (it < -BatteryMath.IDLE_CURRENT_A) amps(it, value) else 0.0 }
      LevelValue.DELTA -> BatteryMath.deltaMv(reading)?.toDouble()
      LevelValue.BATTERY_TEMPERATURE -> worstSensor(reading.temperaturesC, levels)
      LevelValue.MOSFET_TEMPERATURE -> reading.mosfetTemperatureC?.let(::wholeDegrees)
    }

  /** The charge in whole percent, as the widget shows it. */
  fun soc(reading: BatteryReading): Int = reading.chargePercent.roundToInt()

  private fun amps(currentA: Double, value: LevelValue): Double =
    abs(currentA).roundTo(value.decimals)

  private fun wholeDegrees(celsius: Double): Double = celsius.roundToInt().toDouble()

  private fun worstSensor(celsius: List<Double?>, levels: List<ColorLevel>): Double? {
    val shown = celsius.filterNotNull().map(::wholeDegrees)
    val picked = shown.map { LevelPicker.pick(levels, it) }
    val worst = LevelPicker.worst(picked) ?: return null
    return shown[picked.indexOf(worst)]
  }
}
