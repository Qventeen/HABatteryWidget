package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.InverterWidgetModel.Data
import io.lumensync.habattery.domain.InverterWidgetModel.Empty
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.InverterMode
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.withIcon
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InverterWidgetModelTest {
  private val settings = configuredSettings(inverter = true)

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
    assertEquals(InverterMode.OFF_GRID, d.mode.mode)
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
  fun unknownMode_otherSlot() {
    val d = data(cache(mode = "Eco"))
    assertNull(d.mode.mode)
    assertEquals("Eco", d.mode.text)
    assertEquals(LevelColor.GREY, d.mode.color)
    assertEquals(InverterIcon.HELP, d.mode.icon)
  }

  @Test
  fun noMode_isDash() {
    val d = data(cache(mode = null))
    assertNull(d.mode.text)
    assertEquals(LevelColor.GREY, d.mode.color)
    assertEquals(InverterIcon.HELP, d.mode.icon)
  }

  @Test
  fun customIcon_used() {
    val s = settings.copy(inverter = settings.inverter.withIcon(IconSlot.MODE_OFF_GRID, InverterIcon.BATTERY_FULL))
    assertEquals(InverterIcon.BATTERY_FULL, data(cache(), s).mode.icon)
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
