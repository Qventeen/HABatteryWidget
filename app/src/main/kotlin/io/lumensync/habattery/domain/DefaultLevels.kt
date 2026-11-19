package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import kotlin.math.floor

/** The built-in levels of each value. Ids run 1..n in list order and names stay empty. */
object DefaultLevels {
  fun of(value: LevelValue, capacityAh: Double?): List<ColorLevel> =
    build(value, capacityAh) { round1(it) }

  /** [of] with current edges in whole amps, as a start for the owner's own list. */
  fun copyForCustom(value: LevelValue, capacityAh: Double?): List<ColorLevel> =
    build(value, capacityAh) { roundHalfUp(it, 0) }

  private fun build(
    value: LevelValue,
    capacityAh: Double?,
    roundEdge: (Double) -> Double,
  ): List<ColorLevel> {
    val capacity = capacityAh?.takeIf { it > 0.0 }
    val rows: List<Row> =
      when (value) {
        LevelValue.SOC ->
          listOf(
            Row(null, LevelType.CRITICAL, LevelName.LOW),
            Row(20.0, LevelType.WARNING, LevelName.GETTING_LOW),
            Row(40.0, LevelType.NORMAL, LevelName.NORMAL),
            Row(100.0, LevelType.INFO, LevelName.FULL),
          )
        LevelValue.VOLTAGE -> listOf(Row(null, LevelType.NORMAL, LevelName.NORMAL, LevelColor.PLAIN))
        LevelValue.CHARGE_CURRENT,
        LevelValue.DISCHARGE_CURRENT ->
          if (capacity != null) {
            listOf(
              Row(null, LevelType.INFO, LevelName.LIGHT),
              Row(roundEdge(0.2 * capacity), LevelType.NORMAL, LevelName.NORMAL),
              Row(roundEdge(0.5 * capacity), LevelType.WARNING, LevelName.HIGH),
              Row(roundEdge(0.8 * capacity), LevelType.CRITICAL, LevelName.VERY_HIGH),
            )
          } else {
            val color = if (value == LevelValue.DISCHARGE_CURRENT) LevelColor.AMBER else null
            listOf(Row(null, LevelType.NORMAL, LevelName.NORMAL, color))
          }
        LevelValue.DELTA ->
          listOf(
            Row(null, LevelType.INFO, LevelName.BALANCED),
            Row(10.0, LevelType.NORMAL, LevelName.NORMAL),
            Row(20.0, LevelType.WARNING, LevelName.HIGH),
            Row(50.0, LevelType.CRITICAL, LevelName.VERY_HIGH),
          )
        LevelValue.BATTERY_TEMPERATURE ->
          listOf(
            Row(null, LevelType.CRITICAL, LevelName.FREEZING, icon = LevelIcon.SNOWFLAKE),
            Row(0.0, LevelType.INFO, LevelName.COOL, icon = LevelIcon.SNOWFLAKE),
            Row(5.0, LevelType.NORMAL, LevelName.NORMAL, icon = LevelIcon.LEAF),
            Row(45.0, LevelType.WARNING, LevelName.WARM, icon = LevelIcon.FLAME),
            Row(55.0, LevelType.CRITICAL, LevelName.HOT, icon = LevelIcon.FLAME_ALERT),
          )
        LevelValue.MOSFET_TEMPERATURE ->
          listOf(
            Row(null, LevelType.NORMAL, LevelName.NORMAL, icon = LevelIcon.LEAF),
            Row(65.0, LevelType.WARNING, LevelName.WARM, icon = LevelIcon.FLAME),
            Row(80.0, LevelType.CRITICAL, LevelName.HOT, icon = LevelIcon.FLAME_ALERT),
          )
      }
    return rows.mapIndexed { index, row ->
      ColorLevel(
        id = index + 1,
        from = row.from,
        type = row.type,
        color = row.color,
        icon = row.icon,
        defaultName = row.name,
      )
    }
  }

  private class Row(
    val from: Double?,
    val type: LevelType,
    val name: LevelName,
    val color: LevelColor? = null,
    val icon: LevelIcon? = null,
  )

  private fun round1(x: Double): Double = roundHalfUp(x, 1)

  /** Half up, so that 0.2 × 100 = 20.000000000000004 and 0.5 × 105 = 52.5 give 20.0 and 53.0. */
  private fun roundHalfUp(x: Double, decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return floor(x * factor + 0.5) / factor
  }
}
