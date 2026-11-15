package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TemplateBuilderTest {
  @Test fun quote_escapes() = assertEquals("'a\\'b\\\\c'", TemplateBuilder.quote("a'b\\c\r\n"))

  @Test
  fun onlyCharge() =
    assertEquals(
      "{{ {\"soc\": states('sensor.soc') | float(none), \"power\": none, \"current\": none, " +
        "\"total_v\": none} | to_json }}",
      TemplateBuilder.build(EntitySettings(charge = "sensor.soc")),
    )

  @Test
  fun allFour() =
    assertEquals(
      "{{ {\"soc\": states('sensor.soc') | float(none), " +
        "\"power\": states('sensor.p') | float(none), " +
        "\"current\": states('sensor.i') | float(none), " +
        "\"total_v\": states('sensor.v') | float(none)} | to_json }}",
      TemplateBuilder.build(
        EntitySettings(charge = "sensor.soc", power = "sensor.p", current = "sensor.i", totalVoltage = "sensor.v")
      ),
    )

  @Test
  fun `no cells and no fallback prelude`() {
    val t = TemplateBuilder.build(EntitySettings(charge = "sensor.soc", power = "sensor.p"))
    assertFalse("cells" in t)
    assertFalse("{% set" in t)
  }
}
