package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelColor.AMBER
import io.lumensync.habattery.domain.model.LevelColor.BLUE
import io.lumensync.habattery.domain.model.LevelColor.GREEN
import io.lumensync.habattery.domain.model.LevelColor.GREY
import io.lumensync.habattery.domain.model.LevelColor.RED
import io.lumensync.habattery.domain.model.LevelColor.TEAL
import io.lumensync.habattery.domain.model.LevelColor.VIOLET
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelType.CRITICAL
import io.lumensync.habattery.domain.model.LevelType.INFO
import io.lumensync.habattery.domain.model.LevelType.WARNING
import io.lumensync.habattery.domain.model.TextLevelName
import io.lumensync.habattery.domain.model.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTextLevelsTest {
  private data class Row(
    val id: Int,
    val texts: List<String>,
    val other: Boolean,
    val defaultName: TextLevelName?,
    val color: LevelColor?,
    val icon: String,
    val type: LevelType,
    val notify: Boolean,
  )

  @Test
  fun mode_isThe0012Table() {
    val rows =
      DefaultTextLevels.of(TextValue.MODE).map {
        Row(it.id, it.texts, it.other, it.defaultName, it.color, it.icon, it.type, it.notify)
      }

    assertEquals(
      listOf(
        Row(1, listOf("Power On"), false, TextLevelName.POWER_ON, GREY, "POWER_SETTINGS_NEW", INFO, true),
        Row(2, listOf("Standby"), false, TextLevelName.STANDBY, TEAL, "MODE_STANDBY", WARNING, true),
        Row(3, listOf("Mains"), false, TextLevelName.MAINS, BLUE, "TOWER", INFO, true),
        Row(4, listOf("Off-Grid"), false, TextLevelName.OFF_GRID, GREEN, "BATTERY_CHARGING_FULL", WARNING, true),
        Row(5, listOf("Bypass"), false, TextLevelName.BYPASS, VIOLET, "ALT_ROUTE", INFO, true),
        Row(6, listOf("Charging"), false, TextLevelName.CHARGING, AMBER, "CHARGER", INFO, true),
        Row(7, listOf("Fault"), false, TextLevelName.FAULT, RED, "ERROR", CRITICAL, true),
        Row(8, emptyList(), true, null, GREY, "HELP", INFO, true),
      ),
      rows,
    )
  }

  @Test
  fun mode_isValid() =
    assertTrue(Validation.textLevels(DefaultTextLevels.of(TextValue.MODE)).isEmpty())
}
