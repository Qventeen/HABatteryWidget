package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelBasisTest {
  private val settings = Settings(capacityAh = 100.0)

  private fun reading(total: Double? = null, average: Double? = null, capacity: Double? = null) =
    BatteryReading(
      chargePercent = 50.0,
      totalVoltageV = total,
      averageCellVoltageV = average,
      totalCapacityAh = capacity,
      updatedAtMillis = 1L,
    )

  @Test
  fun capacity_readingWins() =
    assertEquals(280.0, LevelBasis.of(reading(capacity = 280.0), settings).capacityAh)

  @Test
  fun capacity_withoutReading_isTheSetting() =
    assertEquals(100.0, LevelBasis.of(null, settings).capacityAh)

  @Test
  fun cells_fromTotalOverAverage() =
    assertEquals(16, LevelBasis.of(reading(total = 52.8, average = 3.3), settings).cells)

  @Test
  fun cells_withoutReading_isNull() = assertNull(LevelBasis.of(null, settings).cells)

  @Test
  fun cells_belowOne_isNull() =
    assertNull(LevelBasis.of(reading(total = 0.4, average = 3.3), settings).cells)

  @Test
  fun capacity_zeroOrNegative_isNull() {
    assertNull(LevelBasis.of(null, Settings(capacityAh = 0.0)).capacityAh)
    assertNull(LevelBasis.of(null, Settings(capacityAh = -5.0)).capacityAh)
  }

  @Test
  fun cells_readingWinsOverTypedCount() =
    assertEquals(16, LevelBasis.of(reading(total = 52.8, average = 3.3), Settings(cellCount = 15)).cells)

  @Test
  fun cells_typedCountFillsAGap() {
    assertEquals(16, LevelBasis.of(reading(average = 3.3), Settings(cellCount = 16)).cells)
    assertEquals(16, LevelBasis.of(null, Settings(cellCount = 16)).cells)
  }

  @Test
  fun cells_typedBelowOne_isNull() = assertNull(LevelBasis.of(null, Settings(cellCount = 0)).cells)

  @Test
  fun type_nullIsLiFePO4() {
    assertEquals(BatteryType.LIFEPO4, LevelBasis.of(null, Settings()).type)
    assertEquals(BatteryType.LTO, LevelBasis.of(null, Settings(batteryType = BatteryType.LTO)).type)
  }
}
