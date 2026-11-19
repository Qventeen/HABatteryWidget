package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import java.math.BigDecimal
import java.math.RoundingMode

object LevelPicker {
  /**
   * The level for [shown], the number as the widget shows it: the one with the greatest `from`
   * at or below it. The first level in list order is open below, so its `from` is ignored. Later
   * levels without a `from` are never reached. The list may be unsorted. [levels] must not be empty.
   */
  fun pick(levels: List<ColorLevel>, shown: Double): ColorLevel {
    require(levels.isNotEmpty()) { "levels must not be empty" }
    return levels
      .drop(1)
      .filter { it.from != null && it.from <= shown }
      .maxByOrNull { it.from!! } ?: levels.first()
  }

  /** The owner's levels when switched on and not empty, else the built-in ones. */
  fun effective(value: LevelValue, settings: Settings, capacityAh: Double?): List<ColorLevel> {
    val custom = settings.customLevels(value)
    return if (custom.on && custom.levels.isNotEmpty()) {
      custom.levels
    } else {
      DefaultLevels.of(value, capacityAh)
    }
  }

  /** Pack-voltage levels as per-cell levels: every edge divided by [cells]. */
  fun perCell(voltageLevels: List<ColorLevel>, cells: Int): List<ColorLevel> =
    voltageLevels.map { level -> level.copy(from = level.from?.let { it / cells }) }

  /** The most serious level by `type.rank`; the first one wins a tie. */
  fun worst(levels: List<ColorLevel>): ColorLevel? = levels.maxByOrNull { it.type.rank }
}

/**
 * Rounds a measured value to the number the app shows. The display (ICU `NumberFormat`) takes the
 * shortest decimal text of the double and rounds it half-even, so 19.95 A is shown, and picked, as
 * 20.0 A.
 */
internal fun Double.roundTo(decimals: Int): Double =
  BigDecimal(toString()).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
