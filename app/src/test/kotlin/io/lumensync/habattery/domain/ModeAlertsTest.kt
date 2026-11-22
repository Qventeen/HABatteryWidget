package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeAlertsTest {
  private val builtIn = DefaultTextLevels.of(TextValue.MODE)

  private fun reading(mode: String?) = InverterReading(mode = mode, updatedAtMillis = 7L)

  private fun evaluate(
    mode: String?,
    last: String?,
    levels: List<TextLevel> = builtIn,
    enabled: Boolean = true,
  ) = ModeAlerts.evaluate(reading(mode), levels, enabled, last)

  private fun List<TextLevel>.edit(id: Int, change: (TextLevel) -> TextLevel) =
    map { if (it.id == id) change(it) else it }

  @Test
  fun firstValue_storesOnly() {
    val outcome = evaluate("Mains", last = null)

    assertNull(outcome.alert)
    assertEquals("Mains", outcome.last)
  }

  @Test
  fun change_alertsWithTheItem() {
    val outcome = evaluate("Off-Grid", last = "Mains")

    assertEquals(ModeAlert("Off-Grid", builtIn[3], 7L), outcome.alert)
    assertEquals("Off-Grid", outcome.last)
  }

  @Test
  fun unknownText_alertsWithOther() =
    assertTrue(evaluate("Eco", last = "Mains").alert!!.level.other)

  @Test
  fun otherToOtherText_alerts() {
    val alert = evaluate("Boost", last = "Eco").alert!!

    assertTrue(alert.level.other)
    assertEquals("Boost", alert.text)
  }

  @Test
  fun sameItem_noAlert_storesNewText() {
    val levels = builtIn.edit(3) { it.copy(texts = listOf("Mains", "Line")) }
    val outcome = evaluate("Line", last = "Mains", levels = levels)

    assertNull(outcome.alert)
    assertEquals("Line", outcome.last)
  }

  @Test
  fun sameIgnoringCase_noAlert() = assertNull(evaluate("MAINS", last = "Mains").alert)

  @Test
  fun bellOff_noAlert_tracks() {
    val levels = builtIn.edit(3) { it.copy(notify = false) }
    val outcome = evaluate("Mains", last = "Off-Grid", levels = levels)

    assertNull(outcome.alert)
    assertEquals("Mains", outcome.last)
  }

  @Test
  fun disabled_tracksOnly() {
    val outcome = evaluate("Off-Grid", last = "Mains", enabled = false)

    assertNull(outcome.alert)
    assertEquals("Off-Grid", outcome.last)
  }

  @Test
  fun unavailable_isNotAChange() {
    val gone = evaluate(null, last = "Mains")
    assertNull(gone.alert)
    assertEquals("Mains", gone.last)

    assertNull(evaluate("Mains", last = gone.last).alert)
  }

  @Test
  fun listEditBetweenSyncs_noAlert() {
    // "Line" was stored while the built-in list had it as "other"; now it is in "main".
    val levels = builtIn.edit(3) { it.copy(texts = listOf("Mains", "Line")) }

    assertNull(evaluate("Line", last = "Line", levels = levels).alert)
  }

  @Test
  fun deletedModeSameText_noAlert() {
    val withoutBypass = builtIn.filterNot { it.id == 5 }

    assertNull(evaluate("Bypass", last = "Bypass", levels = withoutBypass).alert)
  }
}
