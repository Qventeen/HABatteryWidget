package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.EntitySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityFillTest {
  @Test
  fun `fill sets every id from the prefix`() {
    val e = EntityFill.fromPrefix("bps_bms", EntitySettings())
    assertEquals("bps_bms", e.prefix)
    assertEquals("sensor.bps_bms_state_of_charge", e.charge)
    assertEquals("sensor.bps_bms_total_voltage", e.totalVoltage)
    assertEquals("sensor.bps_bms_current", e.current)
    assertEquals("sensor.bps_bms_power", e.power)
    assertEquals("sensor.bps_bms_capacity_remaining", e.capacityRemaining)
    assertEquals("sensor.bps_bms_average_cell_voltage", e.averageCellVoltage)
    assertEquals("sensor.bps_bms_delta_cell_voltage", e.deltaCellVoltage)
    assertEquals("sensor.bps_bms_power_tube_temperature", e.mosfetTemperature)
    assertEquals(listOf("sensor.bps_bms_temperature_sensor_1"), e.temperatures)
    assertEquals("sensor.bps_bms_balancing", e.balancing)
  }

  @Test
  fun `fill sets the total capacity id`() =
    assertEquals(
      "sensor.bps_bms_total_battery_capacity",
      EntityFill.fromPrefix("bps_bms", EntitySettings()).totalCapacity,
    )

  @Test
  fun `fill overwrites hand edits`() =
    assertEquals(
      "sensor.bps_bms_current",
      EntityFill.fromPrefix("bps_bms", EntitySettings(current = "sensor.mine")).current,
    )

  @Test
  fun `fill trims spaces and trailing underscores`() {
    val e = EntityFill.fromPrefix(" bps_bms_ ", EntitySettings())
    assertEquals("bps_bms", e.prefix)
    assertEquals("sensor.bps_bms_state_of_charge", e.charge)
  }
}
