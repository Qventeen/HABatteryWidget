package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateBuilderTest {
  @Test fun quote_escapes() = assertEquals("'a\\'b\\\\c'", TemplateBuilder.quote("a'b\\c\r\n"))

  @Test
  fun onlyCharge() =
    assertEquals(
      "{{ {\"soc\": states('sensor.soc') | float(none), \"total_v\": none, \"current\": none, " +
        "\"power\": none, \"ah_left\": none, \"capacity\": none, \"avg_cell\": none, \"delta\": none, " +
        "\"mosfet\": none, \"temps\": [], \"balancing\": none} | to_json }}",
      TemplateBuilder.build(EntitySettings(charge = "sensor.soc")),
    )

  @Test
  fun `capacity key`() =
    assertTrue(
      "\"capacity\": states('sensor.cap') | float(none)" in
        TemplateBuilder.build(EntitySettings(charge = "sensor.soc", totalCapacity = "sensor.cap"))
    )

  @Test
  fun allBasicFour() {
    val t =
      TemplateBuilder.build(
        EntitySettings(charge = "sensor.soc", power = "sensor.p", current = "sensor.i", totalVoltage = "sensor.v")
      )
    assertTrue("\"total_v\": states('sensor.v') | float(none), " in t)
    assertTrue("\"current\": states('sensor.i') | float(none), " in t)
    assertTrue("\"power\": states('sensor.p') | float(none), " in t)
  }

  @Test
  fun `new numeric keys`() {
    val t = TemplateBuilder.build(EntitySettings(charge = "sensor.soc", capacityRemaining = "sensor.a"))
    assertTrue("\"ah_left\": states('sensor.a') | float(none)" in t)
    assertTrue("\"avg_cell\": none" in t)
  }

  @Test
  fun `temps list`() =
    assertTrue(
      "\"temps\": [states('sensor.t1') | float(none), states('sensor.t2') | float(none)]" in
        TemplateBuilder.build(EntitySettings(charge = "sensor.soc", temperatures = listOf("sensor.t1", "sensor.t2")))
    )

  @Test
  fun `balancing is the raw state`() {
    val t = TemplateBuilder.build(EntitySettings(charge = "sensor.soc", balancing = "sensor.b"))
    assertTrue("\"balancing\": states('sensor.b')" in t)
    assertFalse("states('sensor.b') | float" in t)
  }

  @Test
  fun `no cells and no fallback prelude`() {
    val t = TemplateBuilder.build(EntitySettings(charge = "sensor.soc", power = "sensor.p"))
    assertFalse("cells" in t)
    assertFalse("{% set" in t)
  }
}
