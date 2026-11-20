package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShownNumberTest {
  private val noBasis = LevelBasis(null, null)

  private fun shown(value: LevelValue, reading: BatteryReading) =
    ShownNumber.of(value, reading, DefaultLevels.of(value, noBasis))

  @Test
  fun soc_wholePercent_halfUp() {
    assertEquals(55.0, shown(LevelValue.SOC, reading(charge = 54.5))!!, 0.0)
    assertEquals(54.0, shown(LevelValue.SOC, reading(charge = 54.4))!!, 0.0)
  }

  @Test
  fun voltage_twoDecimals() {
    assertEquals(26.46, shown(LevelValue.VOLTAGE, reading().copy(totalVoltageV = 26.456))!!, 0.0)
    assertNull(shown(LevelValue.VOLTAGE, reading()))
  }

  @Test
  fun delta_inMillivolts() {
    assertEquals(12.0, shown(LevelValue.DELTA, reading().copy(deltaCellVoltageV = 0.0124))!!, 0.0)
    assertNull(shown(LevelValue.DELTA, reading()))
  }

  @Test
  fun mosfet_wholeDegrees() {
    val mosfet = reading().copy(mosfetTemperatureC = 64.5)
    assertEquals(65.0, shown(LevelValue.MOSFET_TEMPERATURE, mosfet)!!, 0.0)
    assertNull(shown(LevelValue.MOSFET_TEMPERATURE, reading()))
  }

  @Test
  fun current_otherDirectionAndIdleAreZero() {
    fun both(current: Double?) =
      shown(LevelValue.CHARGE_CURRENT, reading(current = current)) to
        shown(LevelValue.DISCHARGE_CURRENT, reading(current = current))
    assertEquals(20.0 to 0.0, both(19.95))
    assertEquals(0.0 to 85.0, both(-85.0))
    assertEquals(0.0 to 0.0, both(0.1))
    assertEquals(0.0 to 0.0, both(-0.1))
    assertEquals(null to null, both(null))
  }

  @Test
  fun batteryTemperature_worstSensorInWholeDegrees() {
    fun temperature(vararg celsius: Double?) =
      shown(LevelValue.BATTERY_TEMPERATURE, reading().copy(temperaturesC = celsius.toList()))
    assertEquals(55.0, temperature(null, 25.0, 54.6)!!, 0.0) // Hot beats Normal
    assertEquals(56.0, temperature(56.0, 60.0)!!, 0.0) // both Hot: the first
    assertEquals(-1.0, temperature(-1.0, 60.0)!!, 0.0) // Freezing and Hot rank the same
    assertNull(temperature(null))
    assertNull(temperature())
  }
}
