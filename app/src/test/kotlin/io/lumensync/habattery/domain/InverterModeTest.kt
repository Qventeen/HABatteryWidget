package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterMode
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.ModeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InverterModeTest {
  @Test
  fun of_allRows() {
    val texts =
      mapOf(
        "Power On" to InverterMode.POWER_ON,
        "Standby" to InverterMode.STANDBY,
        "Mains" to InverterMode.MAINS,
        "Off-Grid" to InverterMode.OFF_GRID,
        "Bypass" to InverterMode.BYPASS,
        "Charging" to InverterMode.CHARGING,
        "Fault" to InverterMode.FAULT,
      )
    texts.forEach { (text, mode) -> assertEquals(text, mode, InverterMode.of(text)) }
    assertEquals(InverterMode.entries.size, texts.size)
  }

  @Test
  fun of_ignoresCaseAndSpaces() {
    assertEquals(InverterMode.OFF_GRID, InverterMode.of(" off-grid "))
    assertEquals(InverterMode.MAINS, InverterMode.of("MAINS"))
    assertNull(InverterMode.of("Eco"))
    assertNull(InverterMode.of(null))
  }

  @Test
  fun isNoValue() {
    listOf(null, "", "  ", "unavailable", "Unknown").forEach {
      assertTrue(it, ModeText.isNoValue(it))
    }
    assertFalse(ModeText.isNoValue("Standby"))
  }

  @Test
  fun rows_matchSpec() {
    assertEquals(LevelType.WARNING, InverterMode.STANDBY.type)
    assertEquals(LevelType.WARNING, InverterMode.OFF_GRID.type)
    assertEquals(LevelType.CRITICAL, InverterMode.FAULT.type)
    assertEquals(LevelColor.BLUE, InverterMode.MAINS.color)
    assertEquals(LevelColor.AMBER, InverterMode.CHARGING.color)
  }
}
