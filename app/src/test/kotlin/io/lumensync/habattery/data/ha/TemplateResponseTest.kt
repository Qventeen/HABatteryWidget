package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateResponseTest {
  @Test
  fun `parses the new fields`() {
    val r =
      StoreJson.decodeFromString<TemplateResponse>(
          """{"soc":55,"ah_left":157.0,"avg_cell":3.312,"delta":0.04,"mosfet":27.0,"temps":[25.0,null],"balancing":"on"}"""
        )
        .toReading(1L)!!
    assertEquals(157.0, r.capacityRemainingAh)
    assertEquals(3.312, r.averageCellVoltageV)
    assertEquals(0.04, r.deltaCellVoltageV)
    assertEquals(27.0, r.mosfetTemperatureC)
    assertEquals(listOf(25.0, null), r.temperaturesC)
    assertEquals(true, r.balancing)
  }

  @Test
  fun `non-finite optional values become null`() {
    // JSON cannot carry NaN or Infinity, so build the response directly.
    val r =
      TemplateResponse(
          soc = 55.0,
          ahLeft = Double.NaN,
          avgCell = Double.POSITIVE_INFINITY,
          delta = Double.NaN,
          mosfet = Double.NEGATIVE_INFINITY,
          temps = listOf(Double.NaN, 25.0),
        )
        .toReading(1L)!!
    assertNull(r.capacityRemainingAh)
    assertNull(r.averageCellVoltageV)
    assertNull(r.deltaCellVoltageV)
    assertNull(r.mosfetTemperatureC)
    assertEquals(listOf(null, 25.0), r.temperaturesC)
  }

  @Test
  fun `balancing text`() {
    listOf("on", "ON", " on ", "true", "1").forEach { assertEquals(true, parseBalancing(it)) }
    listOf("off", "False", "0").forEach { assertEquals(false, parseBalancing(it)) }
    listOf("unknown", "unavailable", "", "2", null).forEach { assertNull(parseBalancing(it)) }
  }
}
