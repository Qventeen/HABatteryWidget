package io.lumensync.habattery.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLevelsTest {
  @Test
  fun charge_levels() {
    assertEquals(Level.BAD, WidgetLevels.charge(14))
    assertEquals(Level.WARN, WidgetLevels.charge(15))
    assertEquals(Level.WARN, WidgetLevels.charge(24))
    assertEquals(Level.OK, WidgetLevels.charge(25))
  }

  @Test
  fun delta_levels() {
    assertEquals(Level.BAD, WidgetLevels.delta(41))
    assertEquals(Level.WARN, WidgetLevels.delta(40))
    assertEquals(Level.WARN, WidgetLevels.delta(16))
    assertEquals(Level.PLAIN, WidgetLevels.delta(15))
  }

  @Test
  fun battery_levels() {
    assertEquals(Level.PLAIN, WidgetLevels.battery(5.0))
    assertEquals(Level.WARN, WidgetLevels.battery(4.9))
    assertEquals(Level.WARN, WidgetLevels.battery(0.1))
    assertEquals(Level.BAD, WidgetLevels.battery(0.0))
    assertEquals(Level.BAD, WidgetLevels.battery(-3.0))
    assertEquals(Level.PLAIN, WidgetLevels.battery(45.0))
    assertEquals(Level.WARN, WidgetLevels.battery(45.1))
    assertEquals(Level.WARN, WidgetLevels.battery(55.0))
    assertEquals(Level.BAD, WidgetLevels.battery(55.1))
  }

  @Test
  fun mosfet_levels() {
    assertEquals(Level.PLAIN, WidgetLevels.mosfet(65.0))
    assertEquals(Level.WARN, WidgetLevels.mosfet(65.1))
    assertEquals(Level.WARN, WidgetLevels.mosfet(80.0))
    assertEquals(Level.BAD, WidgetLevels.mosfet(80.1))
  }
}
