package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.TimeRemaining
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes

object BatteryMath {
  const val IDLE_CURRENT_A = 0.1
  const val MIN_CURRENT_A = 0.5

  fun state(chargePercent: Double, currentA: Double?): BatteryState =
    when {
      chargePercent.roundToInt() >= 100 && (currentA == null || currentA >= -IDLE_CURRENT_A) ->
        BatteryState.FULL
      currentA == null -> BatteryState.UNKNOWN
      currentA < -IDLE_CURRENT_A -> BatteryState.DISCHARGING
      currentA > IDLE_CURRENT_A -> BatteryState.CHARGING
      else -> BatteryState.IDLE
    }

  /** The capacity sensor when it reports more than 0 Ah, else the capacity typed in the app. */
  fun fullCapacityAh(reading: BatteryReading, settingsCapacityAh: Double?): Double? =
    reading.totalCapacityAh?.takeIf { it > 0.0 } ?: settingsCapacityAh

  fun deltaMv(reading: BatteryReading): Int? =
    reading.deltaCellVoltageV?.let { (it * 1000).roundToInt() }

  fun timeRemaining(reading: BatteryReading, capacityAh: Double?): TimeRemaining? {
    val direction =
      when (state(reading.chargePercent, reading.currentA)) {
        BatteryState.DISCHARGING -> Direction.TO_EMPTY
        BatteryState.CHARGING -> Direction.TO_FULL
        else -> return null
      }
    val current = reading.currentA?.let(::abs) ?: return null
    if (current < MIN_CURRENT_A) return null
    val soc = reading.chargePercent.coerceIn(0.0, 100.0)
    val ahLeft = reading.capacityRemainingAh
    val ah =
      if (direction == Direction.TO_EMPTY) {
        ahLeft?.coerceAtLeast(0.0) ?: capacityAh?.let { soc / 100 * it }
      } else {
        capacityAh?.let { cap -> ahLeft?.let { max(0.0, cap - it) } ?: ((100 - soc) / 100 * cap) }
      } ?: return null
    return TimeRemaining(direction, (ah / current * 60).roundToLong().minutes)
  }

  fun progress(chargePercent: Double): Float = (chargePercent / 100).toFloat().coerceIn(0f, 1f)
}
