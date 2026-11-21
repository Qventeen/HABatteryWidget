package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.InverterWidgetModel.Data
import io.lumensync.habattery.domain.InverterWidgetModel.Empty
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.withCustomTextLevels
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InverterWidgetModelTest {
  private val settings = configuredSettings(inverter = true)
  private val builtIn = DefaultTextLevels.of(TextValue.MODE)

  private fun withModes(levels: List<TextLevel>) =
    settings.withCustomTextLevels(TextValue.MODE, CustomTextLevels(on = true, levels = levels))

  private fun List<TextLevel>.edit(id: Int, change: (TextLevel) -> TextLevel) =
    map { if (it.id == id) change(it) else it }

  private fun cache(
    mode: String? = "Off-Grid",
    grid: Double? = 0.0,
    output: Double? = 230.0,
    error: SyncError? = null,
  ) =
    CacheState(
      inverterReading = InverterReading(mode, grid, output, updatedAtMillis = 5L),
      inverterError = error,
    )

  private fun data(cache: CacheState, s: Settings = settings) =
    InverterWidgetModel.from(cache, s, hasToken = true) as Data

  @Test
  fun offGrid_default() {
    val d = data(cache())
    assertEquals(builtIn[3], d.mode.level)
    assertEquals("Off-Grid", d.mode.text)
    assertEquals(LevelColor.GREEN, d.mode.color)
    assertEquals(InverterIcon.BATTERY_CHARGING_FULL, d.mode.icon)
    assertEquals(0.0, d.grid.volts)
    assertEquals(LevelColor.BLUE, d.grid.color)
    assertEquals(230.0, d.output.volts)
    assertEquals(LevelColor.GREEN, d.output.color)
    assertEquals(ModeStyle.BIG_ICON, d.style)
  }

  @Test
  fun unknownMode_other() {
    val d = data(cache(mode = "Eco"))
    assertTrue(d.mode.level!!.other)
    assertEquals("Eco", d.mode.text)
    assertEquals(LevelColor.GREY, d.mode.color)
    assertEquals(InverterIcon.HELP, d.mode.icon)
  }

  @Test
  fun noMode_isDash() {
    val d = data(cache(mode = null))
    assertNull(d.mode.level)
    assertNull(d.mode.text)
    assertEquals(LevelColor.GREY, d.mode.color)
    assertEquals(InverterIcon.HELP, d.mode.icon)
  }

  @Test
  fun ownList_colourAndIcon() {
    val levels =
      builtIn.edit(3) {
        it.copy(texts = listOf("Mains", "Line"), color = LevelColor.VIOLET, icon = "FACTORY")
      }
    val d = data(cache(mode = "Line"), withModes(levels))
    assertEquals(3, d.mode.level!!.id)
    assertEquals(LevelColor.VIOLET, d.mode.color)
    assertEquals(InverterIcon.FACTORY, d.mode.icon)
  }

  @Test
  fun ownOtherIcon_usedForNoValue() {
    val levels = builtIn.edit(8) { it.copy(icon = "DANGEROUS") }
    assertEquals(InverterIcon.DANGEROUS, data(cache(mode = null), withModes(levels)).mode.icon)
  }

  @Test
  fun outputZero_isRed() {
    assertEquals(LevelColor.RED, data(cache(output = 0.0)).output.color)
  }

  @Test
  fun nullVolts_isPlain() {
    val d = data(cache(grid = null, output = null))
    assertNull(d.grid.volts)
    assertEquals(LevelColor.PLAIN, d.grid.color)
    assertNull(d.output.volts)
    assertEquals(LevelColor.PLAIN, d.output.color)
  }

  @Test
  fun volts_areWholeHalfEven() {
    assertEquals(206.0, data(cache(output = 206.5)).output.volts)
  }

  @Test
  fun notConfigured_isSetup() {
    assertEquals(
      Empty(EmptyKind.SETUP, null),
      InverterWidgetModel.from(cache(), configuredSettings(), hasToken = true),
    )
  }

  @Test
  fun noReading_isNoData() {
    assertEquals(
      Empty(EmptyKind.NO_DATA, SyncError.Unreachable),
      InverterWidgetModel.from(CacheState(inverterError = SyncError.Unreachable), settings, hasToken = true),
    )
  }

  @Test
  fun inverterError_shown_batteryErrorIgnored() {
    val c = cache(error = SyncError.BadResponse).copy(error = SyncError.Unauthorized)
    assertEquals(SyncError.BadResponse, data(c).error)
  }
}
