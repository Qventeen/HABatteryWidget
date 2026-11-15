package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SyncError
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorFillTest {
  private val one = listOf("sensor.bms_temperature_sensor_1")
  private val draft = EntitySettings(prefix = "bms", temperatures = one)
  private val found = listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2")

  private fun apply(d: EntitySettings = draft, result: SensorSearch, asked: String = "bms") =
    SensorFill.apply(d, asked, one, result)

  @Test
  fun found_replacesTheList() =
    assertEquals(
      FillOutcome(draft.copy(temperatures = found), null),
      apply(result = SensorSearch.Found(found)),
    )

  @Test
  fun noneFound_keepsTheListAndNotes() =
    assertEquals(FillOutcome(draft, SensorSearch.NoneFound), apply(result = SensorSearch.NoneFound))

  @Test
  fun failed_keepsTheListAndNotes() {
    val failed = SensorSearch.Failed(SyncError.Unauthorized)
    assertEquals(FillOutcome(draft, failed), apply(result = failed))
  }

  @Test
  fun skippedAndSuperseded_changeNothing() {
    assertEquals(FillOutcome(draft, null), apply(result = SensorSearch.Skipped))
    assertEquals(FillOutcome(draft, null), apply(result = SensorSearch.Superseded))
  }

  @Test
  fun prefixChanged_dropsEverything() {
    val edited = draft.copy(prefix = "other")
    assertEquals(FillOutcome(edited, null), apply(edited, SensorSearch.Found(found)))
    assertEquals(FillOutcome(edited, null), apply(edited, SensorSearch.NoneFound))
  }

  @Test
  fun handEditedList_winsOverTheAnswer() {
    val edited = draft.copy(temperatures = one + "sensor.mine")
    assertEquals(FillOutcome(edited, null), apply(edited, SensorSearch.Found(found)))
    assertEquals(FillOutcome(edited, null), apply(edited, SensorSearch.NoneFound))
    val removed = draft.copy(temperatures = emptyList())
    assertEquals(FillOutcome(removed, null), apply(removed, SensorSearch.Found(found)))
  }

  @Test
  fun isCurrent_needsSamePrefixAndList() {
    assertEquals(true, SensorFill.isCurrent(draft, "bms", one))
    assertEquals(false, SensorFill.isCurrent(draft.copy(prefix = "x"), "bms", one))
    assertEquals(false, SensorFill.isCurrent(draft.copy(temperatures = found), "bms", one))
  }
}
