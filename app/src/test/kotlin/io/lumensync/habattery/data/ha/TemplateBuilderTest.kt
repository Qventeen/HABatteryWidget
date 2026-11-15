package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateBuilderTest {
  @Test fun quote_escapes() = assertEquals("'a\\'b\\\\c'", TemplateBuilder.quote("a'b\\c\r\n"))

  @Test
  fun onlyCharge() =
    assertEquals(
      "{{ {\"soc\": states('sensor.soc') | float(none), \"power\": none, \"current\": none, " +
        "\"total_v\": none, \"cells\": []} | to_json }}",
      TemplateBuilder.build(EntitySettings(charge = "sensor.soc")),
    )

  @Test
  fun powerWithFallback() =
    assertEquals(
      "{% set p = states('sensor.p') | float(none) %}" +
        "{% if p is none %}{% set p = states('sensor.fb') | float(none) %}{% endif %}" +
        "{{ {\"soc\": states('sensor.soc') | float(none), \"power\": p, \"current\": none, " +
        "\"total_v\": none, \"cells\": []} | to_json }}",
      TemplateBuilder.build(
        EntitySettings(charge = "sensor.soc", power = "sensor.p", fallbackPower = "sensor.fb")
      ),
    )

  @Test
  fun onlyFallbackPower_andCells() {
    val t =
      TemplateBuilder.build(
        EntitySettings(
          charge = "sensor.soc",
          fallbackPower = "sensor.fb",
          cells = listOf("sensor.c1", "sensor.c2"),
        )
      )
    assertTrue(t.contains("\"power\": states('sensor.fb') | float(none)"))
    assertTrue(
      t.contains("\"cells\": [states('sensor.c1') | float(none), states('sensor.c2') | float(none)]")
    )
  }
}
