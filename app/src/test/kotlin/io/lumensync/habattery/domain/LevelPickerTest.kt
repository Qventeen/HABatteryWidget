package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.withCustomLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LevelPickerTest {
  private val noBasis = LevelBasis(null, null)

  private fun below(id: Int, edge: Double, name: String = "") =
    ColorLevel(id = id, side = LevelSide.BELOW, edge = edge, name = name)

  private fun above(id: Int, edge: Double, name: String = "") =
    ColorLevel(id = id, side = LevelSide.ABOVE, edge = edge, name = name)

  private fun middle(id: Int, name: String = "") = ColorLevel(id = id, name = name)

  private val own = listOf(above(3, 80.0), below(1, 20.0), middle(2, "mine"))

  private fun settings(custom: CustomLevels) = Settings().withCustomLevels(LevelValue.SOC, custom)

  private fun names(value: LevelValue, basis: LevelBasis, vararg shown: Double): List<LevelName?> {
    val levels = DefaultLevels.of(value, basis)
    return shown.map { LevelPicker.pick(levels, it).defaultName }
  }

  @Test
  fun pick_soc() =
    assertEquals(
      listOf(
        LevelName.LOW,
        LevelName.LOW,
        LevelName.GETTING_LOW,
        LevelName.GETTING_LOW,
        LevelName.NORMAL,
        LevelName.NORMAL,
        LevelName.FULL,
      ),
      names(LevelValue.SOC, noBasis, 0.0, 20.0, 21.0, 40.0, 41.0, 99.0, 100.0),
    )

  @Test
  fun pick_batteryTemperature() =
    assertEquals(
      listOf(
        LevelName.FREEZING,
        LevelName.FREEZING,
        LevelName.COOL,
        LevelName.COOL,
        LevelName.NORMAL,
        LevelName.NORMAL,
        LevelName.WARM,
        LevelName.WARM,
        LevelName.HOT,
        LevelName.HOT,
      ),
      names(
        LevelValue.BATTERY_TEMPERATURE,
        noBasis,
        -10.0,
        0.0,
        1.0,
        5.0,
        6.0,
        44.0,
        45.0,
        54.0,
        55.0,
        70.0,
      ),
    )

  @Test
  fun pick_currentRoundedEdge() {
    val basis = LevelBasis(100.0, null)
    assertEquals(
      listOf(
        LevelName.LIGHT,
        LevelName.NORMAL,
        LevelName.NORMAL,
        LevelName.HIGH,
        LevelName.VERY_HIGH,
      ),
      names(
        LevelValue.CHARGE_CURRENT,
        basis,
        19.95.roundTo(1),
        20.1,
        49.9,
        50.0,
        80.0,
      ),
    )
  }

  @Test
  fun pick_unsorted() {
    val levels = DefaultLevels.of(LevelValue.BATTERY_TEMPERATURE, noBasis).reversed()
    val shown = listOf(-10.0, 0.0, 1.0, 5.0, 6.0, 44.0, 45.0, 54.0, 55.0, 70.0)
    assertEquals(
      names(LevelValue.BATTERY_TEMPERATURE, noBasis, *shown.toDoubleArray()),
      shown.map { LevelPicker.pick(levels, it).defaultName },
    )
  }

  @Test
  fun pick_onlyMiddle() {
    val only = middle(1)
    for (shown in listOf(-50.0, 0.0, 1000.0)) assertSame(only, LevelPicker.pick(listOf(only), shown))
  }

  @Test
  fun pick_brokenListWithoutMiddle_usesTheFirst() {
    val a = below(1, 20.0)
    val b = above(2, 80.0)
    assertSame(a, LevelPicker.pick(listOf(a, b), 50.0))
  }

  @Test
  fun ownLevels_roundTwoShape_null() {
    val roundTwo = CustomLevels(true, listOf(ColorLevel(1), ColorLevel(2)))
    assertNull(LevelPicker.ownLevels(LevelValue.SOC, roundTwo))
  }

  @Test
  fun ownLevels_off_null() =
    assertNull(LevelPicker.ownLevels(LevelValue.SOC, CustomLevels(false, own)))

  @Test
  fun ownLevels_empty_null() =
    assertNull(LevelPicker.ownLevels(LevelValue.SOC, CustomLevels(true)))

  @Test
  fun ownLevels_valid_isOrdered() =
    assertEquals(
      LevelPicker.ordered(own),
      LevelPicker.ownLevels(LevelValue.SOC, CustomLevels(true, own)),
    )

  @Test
  fun effective_ownValid_usesOwn() =
    assertEquals(
      LevelPicker.ordered(own),
      LevelPicker.effective(LevelValue.SOC, settings(CustomLevels(true, own)), noBasis),
    )

  @Test
  fun effective_brokenOwn_usesDefaults() {
    val broken = CustomLevels(true, listOf(ColorLevel(1), ColorLevel(2)))
    assertEquals(
      DefaultLevels.of(LevelValue.SOC, noBasis),
      LevelPicker.effective(LevelValue.SOC, settings(broken), noBasis),
    )
  }

  @Test
  fun effective_off_usesDefaults() =
    assertEquals(
      DefaultLevels.of(LevelValue.SOC, noBasis),
      LevelPicker.effective(LevelValue.SOC, settings(CustomLevels(false, own)), noBasis),
    )

  @Test
  fun ordered_sortsSides() {
    val levels = listOf(above(1, 80.0), below(2, 20.0), middle(3), above(4, 50.0), below(5, 40.0))
    assertEquals(
      listOf(20.0, 40.0, null, 50.0, 80.0),
      LevelPicker.ordered(levels).map { it.edge },
    )
  }

  @Test
  fun perCell_keepsSides() {
    val volts = DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 16))
    val perCell = LevelPicker.perCell(volts, 16)
    assertEquals(listOf(3.0, 3.2, null, 3.45, 3.55, 3.65), perCell.map { it.edge })
    assertEquals(volts.map { it.side }, perCell.map { it.side })
    assertEquals(volts.map { it.id }, perCell.map { it.id })
    assertEquals(LevelName.GETTING_LOW, LevelPicker.pick(perCell, 3.2).defaultName)
  }

  @Test
  fun perCell_roundsToThreeDecimals() {
    fun edge(volts: Double, cells: Int) = LevelPicker.perCell(listOf(above(1, volts)), cells).single().edge
    assertEquals(2.55, edge(35.7, 14)!!, 0.0)
    assertEquals(3.306, edge(52.9, 16)!!, 0.0)
  }

  @Test
  fun worst_picksTheHighestRank() {
    val normal = ColorLevel(id = 1, type = LevelType.NORMAL)
    val info = ColorLevel(id = 2, type = LevelType.INFO)
    val warning = ColorLevel(id = 3, type = LevelType.WARNING)
    assertSame(warning, LevelPicker.worst(listOf(normal, info, warning)))
    assertSame(info, LevelPicker.worst(listOf(normal, info)))
    assertNull(LevelPicker.worst(emptyList()))
  }

  @Test
  fun worst_keepsTheFirstOnATie() {
    val one = ColorLevel(id = 1, type = LevelType.INFO, defaultName = LevelName.FULL)
    val two = ColorLevel(id = 2, type = LevelType.INFO)
    assertSame(one, LevelPicker.worst(listOf(one, two)))
  }

  @Test
  fun roundTo_matchesTheShownNumber() {
    assertEquals(20.0, 19.95.roundTo(1), 0.0)
    assertEquals(19.8, 19.85.roundTo(1), 0.0)
    assertEquals(20.0, 20.000000000000004.roundTo(1), 0.0)
  }

  @Test
  fun toSteps() {
    assertEquals(5120L, 51.2.toSteps(2))
    assertEquals(-5L, (-5.0).toSteps(0))
    assertEquals(3L, (0.1 + 0.2).toSteps(1))
  }
}
