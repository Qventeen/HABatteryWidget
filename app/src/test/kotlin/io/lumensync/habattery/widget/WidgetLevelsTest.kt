package io.lumensync.habattery.widget

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
    assertEquals(Level.OK, WidgetLevels.delta(15))
  }
}
