package io.lumensync.habattery.data.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemperatureSensorsTest {
  private fun parse(text: String) = TemperatureSensors.parse(text, "bms")

  @Test
  fun `sorts by sensor number, not by text`() =
    assertEquals(
      listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2", "sensor.bms_temperature_sensor_10"),
      parse(
        """["sensor.bms_temperature_sensor_10","sensor.bms_temperature_sensor_2","sensor.bms_temperature_sensor_1"]"""
      ),
    )

  @Test
  fun `drops ids that do not match`() =
    assertEquals(
      listOf("sensor.bms_temperature_sensor_1"),
      parse(
        """["sensor.bms_temperature_sensor_1","sensor.other_temperature_sensor_1",""" +
          """"sensor.bms_temperature_sensor_x","sensor.bms_temperature_sensor_1_a",""" +
          """"xsensor.bms_temperature_sensor_3","sensor.bms_temperature_sensor_"]"""
      ),
    )

  @Test
  fun `keeps the first eight`() {
    val ids = (1..12).map { "sensor.bms_temperature_sensor_$it" }
    assertEquals(ids.take(8), parse(ids.joinToString(",", "[", "]") { "\"$it\"" }))
  }

  @Test
  fun `an empty list is found with no ids`() = assertEquals(emptyList<String>(), parse("[]"))

  @Test
  fun `not a list of strings is null`() {
    assertNull(parse("{nope"))
    assertNull(parse("""{"a":1}"""))
    assertNull(parse("<html>login</html>"))
  }

  @Test
  fun `select sorts, filters and cuts`() {
    fun id(n: Int) = "sensor.bms_temperature_sensor_$n"
    assertEquals(
      listOf(id(1), id(2), id(10)),
      TemperatureSensors.select(
        listOf(id(10), id(2), id(1), "sensor.other", "sensor.otherbms_temperature_sensor_3"),
        "bms",
      ),
    )
    assertEquals(
      (1..8).map(::id),
      TemperatureSensors.select((12 downTo 1).map(::id), "bms"),
    )
  }
}
