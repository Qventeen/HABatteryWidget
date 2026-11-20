package io.lumensync.habattery.domain.model

import io.lumensync.habattery.domain.BatteryMath

/**
 * What the built-in levels are computed from: the full capacity (current edges) and the cells in
 * series (voltage edges). A `null` field means the level list falls back to a plain one. Build it
 * once per reading, so the widget, the tags and the editor's copy agree. Spec 0013 adds the battery
 * type here.
 */
data class LevelBasis(val capacityAh: Double?, val cells: Int?) {
  companion object {
    fun of(reading: BatteryReading?, settings: Settings): LevelBasis =
      LevelBasis(
        capacityAh =
          reading?.let { BatteryMath.fullCapacityAh(it, settings.capacityAh) } ?: settings.capacityAh,
        cells = reading?.let(BatteryMath::cellCount),
      )
  }
}
