package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelSide.ABOVE
import io.lumensync.habattery.domain.model.LevelSide.BELOW
import io.lumensync.habattery.domain.model.LevelSide.MIDDLE
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelType.CRITICAL
import io.lumensync.habattery.domain.model.LevelType.INFO
import io.lumensync.habattery.domain.model.LevelType.NORMAL
import io.lumensync.habattery.domain.model.LevelType.WARNING
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLevelsTest {
  private fun of(
    value: LevelValue,
    capacityAh: Double? = null,
    cells: Int? = null,
    type: BatteryType = BatteryType.LIFEPO4,
  ) = DefaultLevels.of(value, LevelBasis(capacityAh, cells, type))

  /** Every type, with and without a capacity and a cell count. */
  private val bases =
    BatteryType.entries.flatMap {
      listOf(LevelBasis(100.0, 16, it), LevelBasis(null, null, it), LevelBasis(105.0, 15, it))
    }

  private fun List<ColorLevel>.sides() = map { it.side }

  private fun List<ColorLevel>.edges() = map { it.edge }

  private fun List<ColorLevel>.types() = map { it.type }

  private fun List<ColorLevel>.names() = map { it.defaultName }

  private fun List<ColorLevel>.icons() = map { it.icon }

  @Test
  fun soc() {
    val levels = of(LevelValue.SOC)
    assertEquals(listOf(BELOW, BELOW, MIDDLE, ABOVE), levels.sides())
    assertEquals(listOf(20.0, 40.0, null, 100.0), levels.edges())
    assertEquals(listOf(CRITICAL, WARNING, NORMAL, INFO), levels.types())
    assertEquals(
      listOf(LevelName.LOW, LevelName.GETTING_LOW, LevelName.NORMAL, LevelName.FULL),
      levels.names(),
    )
    assertEquals(listOf(1, 2, 3, 4), levels.map { it.id })
    assertTrue(levels.all { it.name == "" && it.color == null && it.icon == null })
  }

  @Test
  fun voltage16Cells() {
    val levels = of(LevelValue.VOLTAGE, cells = 16)
    assertEquals(listOf(BELOW, BELOW, MIDDLE, ABOVE, ABOVE, ABOVE), levels.sides())
    assertEquals(listOf(48.0, 51.2, null, 55.2, 56.8, 58.4), levels.edges())
    assertEquals(listOf(CRITICAL, WARNING, NORMAL, INFO, WARNING, CRITICAL), levels.types())
    assertEquals(
      listOf(
        LevelName.LOW,
        LevelName.GETTING_LOW,
        LevelName.NORMAL,
        LevelName.FULL,
        LevelName.HIGH,
        LevelName.VERY_HIGH,
      ),
      levels.names(),
    )
    assertEquals(levels, DefaultLevels.copyForCustom(LevelValue.VOLTAGE, LevelBasis(null, 16)))
  }

  @Test
  fun voltage15CellsEdgesAreExact() =
    // 3.45 × 15 = 51.75000000000001 in doubles.
    assertEquals(
      listOf(45.0, 48.0, null, 51.75, 53.25, 54.75),
      of(LevelValue.VOLTAGE, cells = 15).edges(),
    )

  @Test
  fun voltageNoCells_isOnePlainLevel() {
    for (levels in
      listOf(
        of(LevelValue.VOLTAGE),
        DefaultLevels.copyForCustom(LevelValue.VOLTAGE, LevelBasis(null, null)),
      )) {
      assertEquals(listOf(MIDDLE), levels.sides())
      assertEquals(LevelColor.PLAIN, levels.single().shownColor)
    }
  }

  @Test
  fun voltageLiIon14Cells() {
    val levels = of(LevelValue.VOLTAGE, cells = 14, type = BatteryType.LI_ION)
    assertEquals(listOf(47.6, 50.4, null, 57.4, 58.1, 58.8), levels.edges())
    val liFePO4 = of(LevelValue.VOLTAGE, cells = 14)
    assertEquals(liFePO4.sides(), levels.sides())
    assertEquals(liFePO4.types(), levels.types())
    assertEquals(liFePO4.names(), levels.names())
    assertEquals(listOf(1, 2, 3, 4, 5, 6), levels.map { it.id })
    assertTrue(levels.none { it.notify })
  }

  @Test
  fun voltageLto22Cells() =
    assertEquals(
      listOf(44.0, 48.4, null, 58.3, 59.4, 61.6),
      of(LevelValue.VOLTAGE, cells = 22, type = BatteryType.LTO).edges(),
    )

  @Test
  fun voltageNoCells_isOnePlainLevel_forEveryType() {
    for (type in BatteryType.entries) {
      val levels = of(LevelValue.VOLTAGE, type = type)
      assertEquals(listOf(MIDDLE), levels.sides())
      assertEquals(LevelColor.PLAIN, levels.single().shownColor)
    }
  }

  @Test
  fun current100Ah() {
    for (value in listOf(LevelValue.CHARGE_CURRENT, LevelValue.DISCHARGE_CURRENT)) {
      val levels = of(value, capacityAh = 100.0)
      assertEquals(listOf(BELOW, MIDDLE, ABOVE, ABOVE), levels.sides())
      assertEquals(listOf(20.0, null, 50.0, 80.0), levels.edges())
      assertEquals(listOf(INFO, NORMAL, WARNING, CRITICAL), levels.types())
      assertEquals(
        listOf(LevelName.LIGHT, LevelName.NORMAL, LevelName.HIGH, LevelName.VERY_HIGH),
        levels.names(),
      )
    }
    assertEquals(
      listOf(56.0, null, 140.0, 224.0),
      of(LevelValue.CHARGE_CURRENT, capacityAh = 280.0).edges(),
    )
  }

  @Test
  fun currentLiIon100Ah() {
    assertEquals(
      listOf(20.0, null, 50.0, 80.0),
      of(LevelValue.CHARGE_CURRENT, 100.0, type = BatteryType.LI_ION).edges(),
    )
    assertEquals(
      listOf(40.0, null, 100.0, 130.0),
      of(LevelValue.DISCHARGE_CURRENT, 100.0, type = BatteryType.LI_ION).edges(),
    )
  }

  @Test
  fun currentLto40Ah() {
    assertEquals(
      listOf(16.0, null, 40.0, 200.0),
      of(LevelValue.CHARGE_CURRENT, 40.0, type = BatteryType.LTO).edges(),
    )
    val discharge = of(LevelValue.DISCHARGE_CURRENT, 40.0, type = BatteryType.LTO)
    assertEquals(listOf(16.0, null, 40.0, 240.0), discharge.edges())
    assertEquals(of(LevelValue.DISCHARGE_CURRENT, 40.0).sides(), discharge.sides())
    assertEquals(of(LevelValue.DISCHARGE_CURRENT, 40.0).types(), discharge.types())
    assertEquals(of(LevelValue.DISCHARGE_CURRENT, 40.0).names(), discharge.names())
  }

  @Test
  fun copyForCustom_lto40AhDischarge() =
    assertEquals(
      listOf(16.0, null, 40.0, 240.0),
      DefaultLevels.copyForCustom(
          LevelValue.DISCHARGE_CURRENT,
          LevelBasis(40.0, null, BatteryType.LTO),
        )
        .edges(),
    )

  @Test
  fun copyForCustom_roundsToWholeAmps() {
    val levels = DefaultLevels.copyForCustom(LevelValue.CHARGE_CURRENT, LevelBasis(105.0, null))
    assertEquals(listOf(21.0, null, 53.0, 84.0), levels.edges())
    assertEquals(listOf(1, 2, 3, 4), levels.map { it.id })
    assertTrue(levels.all { it.name == "" && it.defaultName != null })
  }

  @Test
  fun currentWithoutCapacity() {
    for (type in BatteryType.entries) {
      for (cap in listOf(null, 0.0)) {
        val charge = of(LevelValue.CHARGE_CURRENT, capacityAh = cap, type = type)
        val discharge = of(LevelValue.DISCHARGE_CURRENT, capacityAh = cap, type = type)
        assertEquals(listOf(MIDDLE), charge.sides())
        assertEquals(listOf(MIDDLE), discharge.sides())
        assertEquals(LevelColor.GREEN, charge.single().shownColor)
        assertEquals(LevelColor.AMBER, discharge.single().shownColor)
      }
    }
  }

  @Test
  fun otherValues_areTheSameForEveryType() {
    val typed = listOf(LevelValue.VOLTAGE, LevelValue.CHARGE_CURRENT, LevelValue.DISCHARGE_CURRENT)
    for (type in BatteryType.entries) {
      for (value in LevelValue.entries - typed) {
        assertEquals(
          "$value $type",
          DefaultLevels.of(value, LevelBasis(100.0, 16)),
          DefaultLevels.of(value, LevelBasis(100.0, 16, type)),
        )
      }
    }
  }

  @Test
  fun delta() {
    val levels = of(LevelValue.DELTA)
    assertEquals(listOf(BELOW, MIDDLE, ABOVE, ABOVE), levels.sides())
    assertEquals(listOf(9.0, null, 20.0, 50.0), levels.edges())
    assertEquals(listOf(INFO, NORMAL, WARNING, CRITICAL), levels.types())
    assertEquals(
      listOf(LevelName.BALANCED, LevelName.NORMAL, LevelName.HIGH, LevelName.VERY_HIGH),
      levels.names(),
    )
  }

  @Test
  fun batteryTemperature() {
    val levels = of(LevelValue.BATTERY_TEMPERATURE)
    assertEquals(listOf(BELOW, BELOW, MIDDLE, ABOVE, ABOVE), levels.sides())
    assertEquals(listOf(0.0, 5.0, null, 45.0, 55.0), levels.edges())
    assertEquals(listOf(CRITICAL, INFO, NORMAL, WARNING, CRITICAL), levels.types())
    assertEquals(
      listOf(LevelName.FREEZING, LevelName.COOL, LevelName.NORMAL, LevelName.WARM, LevelName.HOT),
      levels.names(),
    )
    assertEquals(
      listOf(
        LevelIcon.SNOWFLAKE,
        LevelIcon.SNOWFLAKE,
        LevelIcon.LEAF,
        LevelIcon.FLAME,
        LevelIcon.FLAME_ALERT,
      ),
      levels.icons(),
    )
  }

  @Test
  fun mosfetTemperature() {
    val levels = of(LevelValue.MOSFET_TEMPERATURE)
    assertEquals(listOf(MIDDLE, ABOVE, ABOVE), levels.sides())
    assertEquals(listOf(null, 65.0, 80.0), levels.edges())
    assertEquals(listOf(NORMAL, WARNING, CRITICAL), levels.types())
    assertEquals(
      listOf(LevelName.NORMAL, LevelName.WARM, LevelName.HOT),
      levels.names(),
    )
    assertEquals(
      listOf(LevelIcon.LEAF, LevelIcon.FLAME, LevelIcon.FLAME_ALERT),
      levels.icons(),
    )
  }

  @Test
  fun inverterDefaults() {
    val grid = of(LevelValue.GRID_VOLTAGE)
    assertEquals(listOf(BELOW, BELOW, MIDDLE, ABOVE), grid.sides())
    assertEquals(listOf(99.0, 206.0, null, 254.0), grid.edges())
    assertEquals(
      listOf(LevelName.NO_GRID, LevelName.LOW, LevelName.NORMAL, LevelName.HIGH),
      grid.names(),
    )
    assertEquals(listOf(INFO, WARNING, NORMAL, CRITICAL), grid.types())
    assertEquals(listOf(false, true, false, true), grid.map { it.notify })
    assertEquals(listOf(1, 2, 3, 4), grid.map { it.id })

    val output = of(LevelValue.OUTPUT_VOLTAGE)
    assertEquals(grid.sides(), output.sides())
    assertEquals(grid.edges(), output.edges())
    assertEquals(LevelName.NO_POWER, output.first().defaultName)
    assertEquals(listOf(CRITICAL, WARNING, NORMAL, CRITICAL), output.types())
    assertEquals(listOf(false, false, false, true), output.map { it.notify })
  }

  @Test
  fun valueGroups_holdEveryValueOnce() {
    assertEquals(LevelValue.entries, LevelValue.BATTERY + LevelValue.INVERTER)
    assertEquals(listOf(LevelValue.GRID_VOLTAGE, LevelValue.OUTPUT_VOLTAGE), LevelValue.INVERTER)
  }

  @Test
  fun defaults_areValid() {
    for (value in LevelValue.entries) {
      for (basis in bases) {
        val levels = DefaultLevels.of(value, basis)
        assertTrue(levels.size <= MAX_LEVELS)
        assertEquals(emptyList<FieldError>(), Validation.levels(value, levels))
        assertEquals(emptyList<FieldError>(), Validation.levels(value, DefaultLevels.copyForCustom(value, basis)))
      }
    }
  }

  @Test
  fun defaults_areOrdered() {
    for (value in LevelValue.entries) {
      for (basis in bases) {
        val levels = DefaultLevels.of(value, basis)
        assertEquals(LevelPicker.ordered(levels), levels)
        val copy = DefaultLevels.copyForCustom(value, basis)
        assertEquals(LevelPicker.ordered(copy), copy)
      }
    }
  }

  @Test
  fun sidesAreTyped() = assertEquals(LevelSide.MIDDLE, ColorLevel().side)

  @Test
  fun defaultBells() {
    val expected =
      mapOf(
        LevelValue.SOC to listOf(LevelName.LOW, LevelName.FULL),
        LevelValue.BATTERY_TEMPERATURE to listOf(LevelName.FREEZING, LevelName.HOT),
        LevelValue.MOSFET_TEMPERATURE to listOf(LevelName.HOT),
        LevelValue.GRID_VOLTAGE to listOf(LevelName.LOW, LevelName.HIGH),
        LevelValue.OUTPUT_VOLTAGE to listOf(LevelName.HIGH),
      )
    for (value in LevelValue.entries) {
      for (basis in bases) {
        val bells = DefaultLevels.of(value, basis).filter { it.notify }.map { it.defaultName }
        assertEquals("$value $basis", expected[value] ?: emptyList<LevelName>(), bells)
      }
    }
  }

  @Test
  fun copyForCustom_keepsBells() {
    val basis = LevelBasis(null, null)
    assertEquals(
      DefaultLevels.of(LevelValue.SOC, basis).map { it.notify },
      DefaultLevels.copyForCustom(LevelValue.SOC, basis).map { it.notify },
    )
  }
}
