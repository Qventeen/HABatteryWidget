package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.CellStats
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.TimeRemaining
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes

object BatteryMath {
  const val IDLE_POWER_W = 20.0
  const val MIN_CURRENT_A = 0.5

  fun state(chargePercent: Double, powerW: Double?): BatteryState =
    when {
      chargePercent.roundToInt() >= 100 && (powerW == null || powerW >= -IDLE_POWER_W) ->
        BatteryState.FULL
      powerW == null -> BatteryState.UNKNOWN
      powerW < -IDLE_POWER_W -> BatteryState.DISCHARGING
      powerW > IDLE_POWER_W -> BatteryState.CHARGING
      else -> BatteryState.IDLE
    }

  fun cellStats(cells: List<Double?>): CellStats? {
    val present = cells.withIndex().filter { it.value != null }
    if (present.size < 2) return null
    val min = present.minBy { it.value!! }
    val max = present.maxBy { it.value!! }
    return CellStats(
      minIndex = min.index + 1,
      maxIndex = max.index + 1,
      deltaMv = ((max.value!! - min.value!!) * 1000).roundToInt(),
    )
  }

  fun timeRemaining(reading: BatteryReading, capacityAh: Double?): TimeRemaining? {
    val direction =
      when (state(reading.chargePercent, reading.powerW)) {
        BatteryState.DISCHARGING -> Direction.TO_EMPTY
        BatteryState.CHARGING -> Direction.TO_FULL
        else -> return null
      }
    val current = reading.currentA?.let(::abs) ?: return null
    if (current < MIN_CURRENT_A || capacityAh == null) return null
    val soc = reading.chargePercent.coerceIn(0.0, 100.0)
    val fraction = if (direction == Direction.TO_EMPTY) soc / 100 else (100 - soc) / 100
    val hours = fraction * capacityAh / current
    return TimeRemaining(direction, (hours * 60).roundToLong().minutes)
  }

  fun progress(chargePercent: Double): Float = (chargePercent / 100).toFloat().coerceIn(0f, 1f)
}
