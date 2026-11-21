package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterMode
import io.lumensync.habattery.domain.model.InverterReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeAlertsTest {
  private fun reading(mode: String?) = InverterReading(mode = mode, updatedAtMillis = 7L)

  @Test
  fun firstValue_storesOnly() {
    val outcome = ModeAlerts.evaluate(reading("Mains"), enabled = true, last = null)

    assertNull(outcome.alert)
    assertEquals("Mains", outcome.last)
  }

  @Test
  fun change_alerts() {
    val outcome = ModeAlerts.evaluate(reading("Off-Grid"), enabled = true, last = "Mains")

    assertEquals(ModeAlert("Off-Grid", InverterMode.OFF_GRID, 7L), outcome.alert)
    assertEquals("Off-Grid", outcome.last)
  }

  @Test
  fun unknownMode_alertsWithRawText() {
    val outcome = ModeAlerts.evaluate(reading("Eco"), enabled = true, last = "Mains")

    assertEquals(ModeAlert("Eco", null, 7L), outcome.alert)
  }

  @Test
  fun sameIgnoringCase_noAlert() {
    val outcome = ModeAlerts.evaluate(reading("MAINS"), enabled = true, last = "Mains")

    assertNull(outcome.alert)
    assertEquals("Mains", outcome.last)
  }

  @Test
  fun disabled_tracksOnly() {
    val outcome = ModeAlerts.evaluate(reading("Off-Grid"), enabled = false, last = "Mains")

    assertNull(outcome.alert)
    assertEquals("Off-Grid", outcome.last)
  }

  @Test
  fun unavailable_isNotAChange() {
    val gone = ModeAlerts.evaluate(reading(null), enabled = true, last = "Mains")
    assertNull(gone.alert)
    assertEquals("Mains", gone.last)

    val back = ModeAlerts.evaluate(reading("Mains"), enabled = true, last = gone.last)
    assertNull(back.alert)
  }
}
