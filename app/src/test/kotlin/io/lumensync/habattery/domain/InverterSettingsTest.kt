package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.InverterSettings
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.clamped
import io.lumensync.habattery.domain.model.icon
import io.lumensync.habattery.domain.model.withIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class InverterSettingsTest {
  @Test
  fun defaults() {
    val s = InverterSettings()
    assertEquals(ModeStyle.BIG_ICON, s.modeStyle)
    IconSlot.entries.forEach { assertEquals(it.default, s.icon(it)) }
    assertEquals(FontPercents.DEFAULT, s.fontPercents.word)
    assertEquals(FontPercents.DEFAULT, s.fontPercents.value)
  }

  @Test
  fun unknownIconName_fallsBack() {
    val s = InverterSettings(icons = mapOf("MODE_MAINS" to "NOT_AN_ICON", "NO_SUCH_SLOT" to "HOME"))
    assertEquals(InverterIcon.TOWER, s.icon(IconSlot.MODE_MAINS))
  }

  @Test
  fun withIcon_roundTrips() {
    val s = InverterSettings().withIcon(IconSlot.GRID_VOLTAGE, InverterIcon.LOCATION_CITY)
    assertEquals(InverterIcon.LOCATION_CITY, s.icon(IconSlot.GRID_VOLTAGE))
    assertEquals(IconSlot.OUTPUT_VOLTAGE.default, s.icon(IconSlot.OUTPUT_VOLTAGE))
  }

  @Test
  fun fontPercents_clamped() {
    val p = InverterFontPercents(word = 5, value = 500).clamped()
    assertEquals(FontPercents.MIN, p.word)
    assertEquals(FontPercents.MAX, p.value)
  }

  @Test
  fun settingsClamped_clampsInverterFonts() {
    val s = Settings(inverter = InverterSettings(fontPercents = InverterFontPercents(1, 999))).clamped()
    assertEquals(InverterFontPercents(FontPercents.MIN, FontPercents.MAX), s.inverter.fontPercents)
  }
}
