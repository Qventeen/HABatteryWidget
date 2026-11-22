package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import java.math.BigDecimal
import java.math.RoundingMode

object LevelPicker {
  /** The decimals of the average cell voltage, as shown and as the per-cell edges are rounded. */
  const val CELL_DECIMALS = 3

  /**
   * The level for [shown], the number as the widget shows it: the ↓ level with the smallest edge at
   * or above it, else the ↑ level with the largest edge at or below it, else the middle level. A
   * broken list without a middle level gives its first level. The list may be unsorted. [levels]
   * must not be empty.
   */
  fun pick(levels: List<ColorLevel>, shown: Double): ColorLevel {
    require(levels.isNotEmpty()) { "levels must not be empty" }
    return levels
      .filter { it.side == LevelSide.BELOW && it.edge != null && it.edge >= shown }
      .minByOrNull { it.edge!! }
      ?: levels
        .filter { it.side == LevelSide.ABOVE && it.edge != null && it.edge <= shown }
        .maxByOrNull { it.edge!! }
      ?: levels.firstOrNull { it.side == LevelSide.MIDDLE }
      ?: levels.first()
  }

  /**
   * The owner's levels, ordered, when they are switched on, not empty and valid for [value]; else
   * `null`. A list stored by an older build is not valid, so it never reaches the widget. This is
   * the one check for the widget, the tags, the summary, the Default button and the editor.
   */
  fun ownLevels(value: LevelValue, custom: CustomLevels): List<ColorLevel>? =
    if (custom.on && custom.levels.isNotEmpty() && Validation.levels(value, custom.levels).isEmpty()) {
      ordered(custom.levels)
    } else {
      null
    }

  /** The owner's levels when [ownLevels] accepts them, else the built-in ones. */
  fun effective(value: LevelValue, settings: Settings, basis: LevelBasis): List<ColorLevel> =
    effective(value, ownLevels(value, settings.customLevels(value)), basis)

  /** [effective] for a caller that already has the result of [ownLevels] as [own]. */
  fun effective(value: LevelValue, own: List<ColorLevel>?, basis: LevelBasis): List<ColorLevel> =
    own ?: DefaultLevels.of(value, basis)

  /** The levels as shown: ↓ by edge ascending, then the middle one, then ↑ by edge ascending. */
  fun ordered(levels: List<ColorLevel>): List<ColorLevel> =
    levels.sortedWith(compareBy<ColorLevel> { it.side.ordinal }.thenBy { it.edge ?: 0.0 })

  /**
   * Pack-voltage levels as per-cell levels: every edge divided by [cells] and rounded to the
   * [CELL_DECIMALS] the average cell voltage is shown with, so the colour follows the shown
   * number.
   */
  fun perCell(voltageLevels: List<ColorLevel>, cells: Int): List<ColorLevel> =
    voltageLevels.map { level ->
      level.copy(edge = level.edge?.let { (it / cells).roundTo(CELL_DECIMALS) })
    }

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

/** The number as a whole count of the last shown digit, so edges compare without float noise. */
internal fun Double.toSteps(decimals: Int): Long =
  BigDecimal(toString()).setScale(decimals, RoundingMode.HALF_EVEN).unscaledValue().toLong()

internal fun stepsToDouble(steps: Long, decimals: Int): Double =
  BigDecimal.valueOf(steps, decimals).toDouble()
