package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.domain.model.Settings

/**
 * What the built-in levels are computed from: the full capacity (current edges), the cells in
 * series (voltage edges) and the battery [type] (the volts per cell and the C-rates). A `null`
 * capacity or cell count means the level list falls back to a plain one. Build it once per
 * reading, so the widget, the tags and the editor's copy agree.
 */
data class LevelBasis(
  val capacityAh: Double?,
  val cells: Int?,
  val type: BatteryType = BatteryType.DEFAULT,
) {
  companion object {
    /**
     * The cells come from the reading (total / average) when it can count them, else from the
     * typed count: the reading follows the real pack, so a stale typed count never wins.
     */
    fun of(reading: BatteryReading?, settings: Settings): LevelBasis =
      LevelBasis(
        capacityAh =
          (reading?.let { BatteryMath.fullCapacityAh(it, settings.capacityAh) } ?: settings.capacityAh)
            ?.takeIf { it > 0.0 },
        cells =
          reading?.let(BatteryMath::cellCount)?.takeIf { it >= 1 }
            ?: settings.cellCount?.takeIf { it >= 1 },
        type = settings.batteryType ?: BatteryType.DEFAULT,
      )
  }
}
