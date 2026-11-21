package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.InverterEntitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateBuilderTest {
  private fun battery(entities: EntitySettings) = TemplateBuilder.build(FetchRequest(entities, null))

  @Test fun quote_escapes() = assertEquals("'a\\'b\\\\c'", TemplateBuilder.quote("a'b\\c\r\n"))

  @Test
  fun onlyCharge() =
    assertEquals(
      "{{ {\"soc\": states('sensor.soc') | float(none), \"total_v\": none, \"current\": none, " +
        "\"power\": none, \"ah_left\": none, \"capacity\": none, \"avg_cell\": none, \"delta\": none, " +
        "\"mosfet\": none, \"temps\": [], \"balancing\": none, \"inv_mode\": none, " +
        "\"inv_grid_v\": none, \"inv_out_v\": none} | to_json }}",
      battery(EntitySettings(charge = "sensor.soc")),
    )

  @Test
  fun `capacity key`() =
    assertTrue(
      "\"capacity\": states('sensor.cap') | float(none)" in
        battery(EntitySettings(charge = "sensor.soc", totalCapacity = "sensor.cap"))
    )

  @Test
  fun allBasicFour() {
    val t =
      battery(
        EntitySettings(charge = "sensor.soc", power = "sensor.p", current = "sensor.i", totalVoltage = "sensor.v")
      )
    assertTrue("\"total_v\": states('sensor.v') | float(none), " in t)
    assertTrue("\"current\": states('sensor.i') | float(none), " in t)
    assertTrue("\"power\": states('sensor.p') | float(none), " in t)
  }

  @Test
  fun `new numeric keys`() {
    val t = battery(EntitySettings(charge = "sensor.soc", capacityRemaining = "sensor.a"))
    assertTrue("\"ah_left\": states('sensor.a') | float(none)" in t)
    assertTrue("\"avg_cell\": none" in t)
  }

  @Test
  fun `temps list`() =
    assertTrue(
      "\"temps\": [states('sensor.t1') | float(none), states('sensor.t2') | float(none)]" in
        battery(EntitySettings(charge = "sensor.soc", temperatures = listOf("sensor.t1", "sensor.t2")))
    )

  @Test
  fun `balancing is the raw state`() {
    val t = battery(EntitySettings(charge = "sensor.soc", balancing = "sensor.b"))
    assertTrue("\"balancing\": states('sensor.b')" in t)
    assertFalse("states('sensor.b') | float" in t)
  }

  @Test
  fun `no cells and no fallback prelude`() {
    val t = battery(EntitySettings(charge = "sensor.soc", power = "sensor.p"))
    assertFalse("cells" in t)
    assertFalse("{% set" in t)
  }

  private val inverter =
    InverterEntitySettings(
      mode = "sensor.x_operation_mode",
      gridVoltage = "sensor.x_main_voltage",
      outputVoltage = "sensor.x_output_voltage",
    )

  @Test
  fun inverterKeys() {
    val t = TemplateBuilder.build(FetchRequest(EntitySettings(charge = "sensor.soc"), inverter))
    assertTrue("\"soc\": states('sensor.soc') | float(none)" in t)
    assertTrue("\"inv_mode\": states('sensor.x_operation_mode'), " in t)
    assertFalse("states('sensor.x_operation_mode') | float" in t)
    assertTrue("\"inv_grid_v\": states('sensor.x_main_voltage') | float(none), " in t)
    assertTrue("\"inv_out_v\": states('sensor.x_output_voltage') | float(none)}" in t)
    val blankGrid = TemplateBuilder.build(FetchRequest(null, inverter.copy(gridVoltage = "")))
    assertTrue("\"inv_grid_v\": none" in blankGrid)
  }

  @Test
  fun inverterOnly_everyBatteryKeyIsNone() =
    assertEquals(
      "{{ {\"soc\": none, \"total_v\": none, \"current\": none, \"power\": none, \"ah_left\": none, " +
        "\"capacity\": none, \"avg_cell\": none, \"delta\": none, \"mosfet\": none, \"temps\": [], " +
        "\"balancing\": none, \"inv_mode\": states('sensor.x_operation_mode'), " +
        "\"inv_grid_v\": states('sensor.x_main_voltage') | float(none), " +
        "\"inv_out_v\": states('sensor.x_output_voltage') | float(none)} | to_json }}",
      TemplateBuilder.build(FetchRequest(null, inverter)),
    )

  @Test
  fun `sensor list template matches the prefix pattern`() =
    assertEquals(
      "{{ states.sensor | map(attribute='entity_id') | " +
        "select('match', 'sensor[.]bps_bms_temperature_sensor_[0-9]+$') | list | to_json }}",
      TemplateBuilder.temperatureSensors("bps_bms"),
    )

  @Test
  fun `sensor list template refuses a prefix with other characters`() {
    listOf("", "Bps", "a b", "a'b", "a.b", "a\\b").forEach {
      assertFalse(it, TemplateBuilder.isObjectIdPrefix(it))
    }
    assertTrue(TemplateBuilder.isObjectIdPrefix("bms_2"))
  }
}
