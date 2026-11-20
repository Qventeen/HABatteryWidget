package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.LevelSpan.AtOrAbove
import io.lumensync.habattery.domain.LevelSpan.AtOrBelow
import io.lumensync.habattery.domain.LevelSpan.Between
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelSpansTest {
  private fun below(id: Int, edge: Double) = ColorLevel(id = id, side = LevelSide.BELOW, edge = edge)

  private fun above(id: Int, edge: Double) = ColorLevel(id = id, side = LevelSide.ABOVE, edge = edge)

  private fun middle(id: Int) = ColorLevel(id = id)

  private fun spans(levels: List<ColorLevel>, value: LevelValue) =
    levels.map { LevelSpans.of(levels, it, value.decimals, value.floor) }

  private fun middleSpan(levels: List<ColorLevel>, value: LevelValue) =
    LevelSpans.of(levels, levels.single { it.side == LevelSide.MIDDLE }, value.decimals, value.floor)

  @Test
  fun soc_defaults() =
    assertEquals(
      listOf(AtOrBelow(20.0), AtOrBelow(40.0), Between(41.0, 99.0), AtOrAbove(100.0)),
      spans(DefaultLevels.of(LevelValue.SOC, LevelBasis(null, null)), LevelValue.SOC),
    )

  @Test
  fun voltage_16cells_middle() =
    assertEquals(
      Between(51.21, 55.19),
      middleSpan(DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 16)), LevelValue.VOLTAGE),
    )

  @Test
  fun current_100Ah_middle() =
    assertEquals(
      Between(20.1, 49.9),
      middleSpan(DefaultLevels.of(LevelValue.CHARGE_CURRENT, LevelBasis(100.0, null)), LevelValue.CHARGE_CURRENT),
    )

  @Test
  fun mosfet_middle_isAtOrBelow() =
    assertEquals(
      AtOrBelow(64.0),
      middleSpan(DefaultLevels.of(LevelValue.MOSFET_TEMPERATURE, LevelBasis(null, null)), LevelValue.MOSFET_TEMPERATURE),
    )

  @Test
  fun onlyBelow_middle_isAtOrAbove() =
    assertEquals(AtOrAbove(21.0), middleSpan(listOf(below(1, 20.0), middle(2)), LevelValue.SOC))

  @Test
  fun oneStepGap_middle_isOnePoint() =
    assertEquals(
      Between(41.0, 41.0),
      middleSpan(listOf(below(1, 40.0), middle(2), above(3, 42.0)), LevelValue.SOC),
    )

  @Test
  fun singleMiddle_usesTheFloor() {
    assertEquals(AtOrAbove(0.0), middleSpan(listOf(middle(1)), LevelValue.SOC))
    assertEquals(Anything, middleSpan(listOf(middle(1)), LevelValue.BATTERY_TEMPERATURE))
  }

  @Test
  fun perCell_voltage_middle() {
    val perCell = LevelPicker.perCell(DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 16)), 16)
    assertEquals(
      Between(3.201, 3.449),
      LevelSpans.of(perCell, perCell.single { it.side == LevelSide.MIDDLE }, 3, 0.0),
    )
  }

  private fun bounds(levels: List<ColorLevel>) = levels.map { LevelSpans.bounds(levels, it) }

  @Test
  fun bounds_soc_defaults() =
    assertEquals(
      listOf(null to 20.0, 20.0 to 40.0, 40.0 to 100.0, 100.0 to null),
      bounds(DefaultLevels.of(LevelValue.SOC, LevelBasis(null, null))),
    )

  @Test
  fun bounds_batteryTemperature_defaults() =
    assertEquals(
      listOf(null to 0.0, 0.0 to 5.0, 5.0 to 45.0, 45.0 to 55.0, 55.0 to null),
      bounds(DefaultLevels.of(LevelValue.BATTERY_TEMPERATURE, LevelBasis(null, null))),
    )

  @Test
  fun bounds_mosfet_middleHasOnlyAnUpperBound() =
    assertEquals(
      listOf(null to 65.0, 65.0 to 80.0, 80.0 to null),
      bounds(DefaultLevels.of(LevelValue.MOSFET_TEMPERATURE, LevelBasis(null, null))),
    )

  @Test
  fun bounds_unsortedList_usesTheNearestEdges() =
    assertEquals(
      listOf(20.0 to 40.0, null to 20.0, 80.0 to null, 40.0 to 50.0, 50.0 to 80.0),
      bounds(listOf(below(1, 40.0), below(2, 20.0), above(3, 80.0), middle(4), above(5, 50.0))),
    )

  @Test
  fun bounds_singleMiddle_hasNone() = assertEquals(listOf(null to null), bounds(listOf(middle(1))))

  private val Anything = LevelSpan.Anything
}
