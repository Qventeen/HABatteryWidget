package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelBasis
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLevelsTest {
  private fun pick(value: LevelValue, shown: Double, capacityAh: Double? = null): ColorLevel =
    LevelPicker.pick(DefaultLevels.of(value, LevelBasis(capacityAh, null)), shown)

  @Test
  fun `soc levels`() {
    assertEquals(LevelName.LOW, pick(LevelValue.SOC, 19.0).defaultName)
    assertEquals(LevelColor.RED, pick(LevelValue.SOC, 19.0).shownColor)
    assertEquals(LevelName.GETTING_LOW, pick(LevelValue.SOC, 20.0).defaultName)
    assertEquals(LevelName.GETTING_LOW, pick(LevelValue.SOC, 39.0).defaultName)
    assertEquals(LevelName.NORMAL, pick(LevelValue.SOC, 40.0).defaultName)
    assertEquals(LevelColor.GREEN, pick(LevelValue.SOC, 40.0).shownColor)
    assertEquals(LevelName.NORMAL, pick(LevelValue.SOC, 99.0).defaultName)
    assertEquals(LevelName.FULL, pick(LevelValue.SOC, 100.0).defaultName)
    assertEquals(LevelColor.BLUE, pick(LevelValue.SOC, 100.0).shownColor)
  }

  @Test
  fun `delta levels`() {
    assertEquals(LevelName.BALANCED, pick(LevelValue.DELTA, 9.0).defaultName)
    assertEquals(LevelColor.BLUE, pick(LevelValue.DELTA, 9.0).shownColor)
    assertEquals(LevelName.NORMAL, pick(LevelValue.DELTA, 10.0).defaultName)
    assertEquals(LevelName.NORMAL, pick(LevelValue.DELTA, 19.0).defaultName)
    assertEquals(LevelName.HIGH, pick(LevelValue.DELTA, 20.0).defaultName)
    assertEquals(LevelColor.AMBER, pick(LevelValue.DELTA, 20.0).shownColor)
    assertEquals(LevelName.VERY_HIGH, pick(LevelValue.DELTA, 50.0).defaultName)
    assertEquals(LevelColor.RED, pick(LevelValue.DELTA, 50.0).shownColor)
  }

  @Test
  fun `battery temperature levels`() {
    val v = LevelValue.BATTERY_TEMPERATURE
    pick(v, -1.0).let {
      assertEquals(LevelName.FREEZING, it.defaultName)
      assertEquals(LevelColor.RED, it.shownColor)
      assertEquals(LevelIcon.SNOWFLAKE, it.icon)
    }
    pick(v, 0.0).let {
      assertEquals(LevelName.COOL, it.defaultName)
      assertEquals(LevelColor.BLUE, it.shownColor)
      assertEquals(LevelIcon.SNOWFLAKE, it.icon)
    }
    pick(v, 5.0).let {
      assertEquals(LevelName.NORMAL, it.defaultName)
      assertEquals(LevelColor.GREEN, it.shownColor)
      assertEquals(LevelIcon.LEAF, it.icon)
    }
    pick(v, 45.0).let {
      assertEquals(LevelName.WARM, it.defaultName)
      assertEquals(LevelColor.AMBER, it.shownColor)
      assertEquals(LevelIcon.FLAME, it.icon)
    }
    pick(v, 55.0).let {
      assertEquals(LevelName.HOT, it.defaultName)
      assertEquals(LevelColor.RED, it.shownColor)
      assertEquals(LevelIcon.FLAME_ALERT, it.icon)
    }
  }

  @Test
  fun `mosfet levels`() {
    val v = LevelValue.MOSFET_TEMPERATURE
    assertEquals(LevelName.NORMAL, pick(v, 64.0).defaultName)
    assertEquals(LevelIcon.LEAF, pick(v, 64.0).icon)
    assertEquals(LevelName.WARM, pick(v, 65.0).defaultName)
    assertEquals(LevelName.HOT, pick(v, 80.0).defaultName)
  }

  @Test
  fun `current 100Ah edges`() {
    val expected = listOf(null, 20.0, 50.0, 80.0)
    assertEquals(expected, DefaultLevels.of(LevelValue.CHARGE_CURRENT, LevelBasis(100.0, null)).map { it.from })
    assertEquals(expected, DefaultLevels.of(LevelValue.DISCHARGE_CURRENT, LevelBasis(100.0, null)).map { it.from })
    assertEquals(
      listOf(null, 56.0, 140.0, 224.0),
      DefaultLevels.of(LevelValue.CHARGE_CURRENT, LevelBasis(280.0, null)).map { it.from },
    )
  }

  @Test
  fun `current on edge is green`() {
    assertEquals(LevelType.NORMAL, pick(LevelValue.CHARGE_CURRENT, 20.0, 100.0).type)
    assertEquals(LevelType.INFO, pick(LevelValue.CHARGE_CURRENT, 19.9, 100.0).type)
  }

  @Test
  fun `current without capacity`() {
    for (cap in listOf(null, 0.0)) {
      val charge = DefaultLevels.of(LevelValue.CHARGE_CURRENT, LevelBasis(cap, null))
      val discharge = DefaultLevels.of(LevelValue.DISCHARGE_CURRENT, LevelBasis(cap, null))
      assertEquals(1, charge.size)
      assertEquals(1, discharge.size)
      assertEquals(LevelColor.GREEN, charge.single().shownColor)
      assertEquals(LevelColor.AMBER, discharge.single().shownColor)
    }
  }

  @Test
  fun `voltage noCells is one plain level`() {
    for (levels in
      listOf(
        DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, null)),
        DefaultLevels.copyForCustom(LevelValue.VOLTAGE, LevelBasis(null, null)),
      )) {
      assertEquals(1, levels.size)
      assertEquals(LevelColor.PLAIN, levels.single().shownColor)
    }
  }

  @Test
  fun `voltage 16 cells`() {
    val levels = DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 16))
    assertEquals(listOf(null, 48.0, 51.2, 55.2, 56.8, 58.4), levels.map { it.from })
    assertEquals(
      listOf(
        LevelType.CRITICAL,
        LevelType.WARNING,
        LevelType.NORMAL,
        LevelType.INFO,
        LevelType.WARNING,
        LevelType.CRITICAL,
      ),
      levels.map { it.type },
    )
    assertEquals(
      listOf(
        LevelName.LOW,
        LevelName.GETTING_LOW,
        LevelName.NORMAL,
        LevelName.FULL,
        LevelName.HIGH,
        LevelName.VERY_HIGH,
      ),
      levels.map { it.defaultName },
    )
    assertEquals(listOf(1, 2, 3, 4, 5, 6), levels.map { it.id })
    assertTrue(levels.all { it.color == null })
    assertEquals(levels, DefaultLevels.copyForCustom(LevelValue.VOLTAGE, LevelBasis(null, 16)))
  }

  @Test
  fun `voltage 15 cells edges are exact`() {
    // 3.45 × 15 = 51.75000000000001 in doubles.
    val levels = DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 15))
    assertEquals(listOf(null, 45.0, 48.0, 51.75, 53.25, 54.75), levels.map { it.from })
  }

  @Test
  fun `copyForCustom rounds to whole amps`() {
    val levels = DefaultLevels.copyForCustom(LevelValue.CHARGE_CURRENT, LevelBasis(105.0, null))
    assertEquals(listOf(null, 21.0, 53.0, 84.0), levels.map { it.from })
    assertEquals(listOf(1, 2, 3, 4), levels.map { it.id })
    assertTrue(levels.all { it.name == "" && it.defaultName != null })
  }

  @Test
  fun `every default list is well formed`() {
    for (value in LevelValue.entries) {
      for (basis in
        listOf(
          LevelBasis(null, null),
          LevelBasis(100.0, 16),
          LevelBasis(280.0, 15),
          LevelBasis(null, 16),
        )) {
        val levels = DefaultLevels.of(value, basis)
        assertEquals(levels.size, levels.map { it.id }.toSet().size)
        assertTrue(levels.size <= MAX_LEVELS)
        val edges = levels.mapNotNull { it.from }
        assertEquals(edges.sorted().distinct(), edges)
      }
    }
  }
}
