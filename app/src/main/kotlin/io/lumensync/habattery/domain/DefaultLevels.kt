package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import kotlin.math.floor

/**
 * The built-in levels of each value, in shown order. Ids run 1..n and names stay empty. Bells are on
 * for the levels that mean act now: SOC Low and Full, Freezing and Hot battery, Hot MOSFET, Low and
 * High grid voltage, High output voltage.
 */
object DefaultLevels {
  private val BELOW = LevelSide.BELOW
  private val MIDDLE = LevelSide.MIDDLE
  private val ABOVE = LevelSide.ABOVE

  fun of(value: LevelValue, basis: LevelBasis): List<ColorLevel> =
    build(value, basis) { round1(it) }

  /** [of] with current edges in whole amps, as a start for the owner's own list. */
  fun copyForCustom(value: LevelValue, basis: LevelBasis): List<ColorLevel> =
    build(value, basis) { roundHalfUp(it, 0) }

  private fun build(
    value: LevelValue,
    basis: LevelBasis,
    roundEdge: (Double) -> Double,
  ): List<ColorLevel> {
    val capacity = basis.capacityAh?.takeIf { it > 0.0 }
    val cells = basis.cells?.takeIf { it >= 1 }
    val rows: List<Row> =
      when (value) {
        LevelValue.SOC ->
          listOf(
            Row(BELOW, 20.0, LevelType.CRITICAL, LevelName.LOW),
            Row(BELOW, 40.0, LevelType.WARNING, LevelName.GETTING_LOW),
            Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL),
            Row(ABOVE, 100.0, LevelType.INFO, LevelName.FULL),
          )
        LevelValue.VOLTAGE ->
          if (cells != null) {
            // LiFePO4 volts per cell, scaled to the pack.
            listOf(
              Row(BELOW, packEdge(3.00, cells), LevelType.CRITICAL, LevelName.LOW),
              Row(BELOW, packEdge(3.20, cells), LevelType.WARNING, LevelName.GETTING_LOW),
              Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL),
              Row(ABOVE, packEdge(3.45, cells), LevelType.INFO, LevelName.FULL),
              Row(ABOVE, packEdge(3.55, cells), LevelType.WARNING, LevelName.HIGH),
              Row(ABOVE, packEdge(3.65, cells), LevelType.CRITICAL, LevelName.VERY_HIGH),
            )
          } else {
            listOf(Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL, LevelColor.PLAIN))
          }
        LevelValue.CHARGE_CURRENT,
        LevelValue.DISCHARGE_CURRENT ->
          if (capacity != null) {
            listOf(
              Row(BELOW, roundEdge(0.2 * capacity), LevelType.INFO, LevelName.LIGHT),
              Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL),
              Row(ABOVE, roundEdge(0.5 * capacity), LevelType.WARNING, LevelName.HIGH),
              Row(ABOVE, roundEdge(0.8 * capacity), LevelType.CRITICAL, LevelName.VERY_HIGH),
            )
          } else {
            val color = if (value == LevelValue.DISCHARGE_CURRENT) LevelColor.AMBER else null
            listOf(Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL, color))
          }
        LevelValue.DELTA ->
          listOf(
            Row(BELOW, 9.0, LevelType.INFO, LevelName.BALANCED),
            Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL),
            Row(ABOVE, 20.0, LevelType.WARNING, LevelName.HIGH),
            Row(ABOVE, 50.0, LevelType.CRITICAL, LevelName.VERY_HIGH),
          )
        LevelValue.BATTERY_TEMPERATURE ->
          listOf(
            Row(BELOW, 0.0, LevelType.CRITICAL, LevelName.FREEZING, icon = LevelIcon.SNOWFLAKE),
            Row(BELOW, 5.0, LevelType.INFO, LevelName.COOL, icon = LevelIcon.SNOWFLAKE),
            Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL, icon = LevelIcon.LEAF),
            Row(ABOVE, 45.0, LevelType.WARNING, LevelName.WARM, icon = LevelIcon.FLAME),
            Row(ABOVE, 55.0, LevelType.CRITICAL, LevelName.HOT, icon = LevelIcon.FLAME_ALERT),
          )
        LevelValue.MOSFET_TEMPERATURE ->
          listOf(
            Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL, icon = LevelIcon.LEAF),
            Row(ABOVE, 65.0, LevelType.WARNING, LevelName.WARM, icon = LevelIcon.FLAME),
            Row(ABOVE, 80.0, LevelType.CRITICAL, LevelName.HOT, icon = LevelIcon.FLAME_ALERT),
          )
        LevelValue.GRID_VOLTAGE,
        LevelValue.OUTPUT_VOLTAGE -> {
          // Normal is 230 V ±10 % (EN 50160). A grid outage is normal for this system and the mode
          // already shows it, so no grid is only Info; no output means the house has no power. Low
          // voltage is a warning; high voltage can damage devices, so it is Critical.
          val none =
            if (value == LevelValue.GRID_VOLTAGE) {
              Row(BELOW, 99.0, LevelType.INFO, LevelName.NO_GRID)
            } else {
              Row(BELOW, 99.0, LevelType.CRITICAL, LevelName.NO_POWER)
            }
          listOf(
            none,
            Row(BELOW, 206.0, LevelType.WARNING, LevelName.LOW),
            Row(MIDDLE, null, LevelType.NORMAL, LevelName.NORMAL),
            Row(ABOVE, 254.0, LevelType.CRITICAL, LevelName.HIGH),
          )
        }
      }
    return rows.mapIndexed { index, row ->
      ColorLevel(
        id = index + 1,
        side = row.side,
        edge = row.edge,
        type = row.type,
        color = row.color,
        icon = row.icon,
        defaultName = row.name,
        notify = isBell(value, row.name),
      )
    }
  }

  private fun isBell(value: LevelValue, name: LevelName): Boolean =
    when (value) {
      LevelValue.SOC -> name == LevelName.LOW || name == LevelName.FULL
      LevelValue.BATTERY_TEMPERATURE -> name == LevelName.FREEZING || name == LevelName.HOT
      LevelValue.MOSFET_TEMPERATURE -> name == LevelName.HOT
      // No grid has no bell: a grid loss also changes the inverter mode, and one notification is
      // enough.
      LevelValue.GRID_VOLTAGE -> name == LevelName.LOW || name == LevelName.HIGH
      LevelValue.OUTPUT_VOLTAGE -> name == LevelName.HIGH
      else -> false
    }

  private class Row(
    val side: LevelSide,
    val edge: Double?,
    val type: LevelType,
    val name: LevelName,
    val color: LevelColor? = null,
    val icon: LevelIcon? = null,
  )

  /** 3.45 × 15 is 51.75000000000001, so the product is rounded to the 2 decimals of a voltage edge. */
  private fun packEdge(perCell: Double, cells: Int): Double = roundHalfUp(perCell * cells, 2)

  private fun round1(x: Double): Double = roundHalfUp(x, 1)

  /** Half up, so that 0.2 × 100 = 20.000000000000004 and 0.5 × 105 = 52.5 give 20.0 and 53.0. */
  private fun roundHalfUp(x: Double, decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return floor(x * factor + 0.5) / factor
  }
}
