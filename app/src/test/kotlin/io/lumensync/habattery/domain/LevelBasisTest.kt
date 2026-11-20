package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.LevelBasis
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
}
