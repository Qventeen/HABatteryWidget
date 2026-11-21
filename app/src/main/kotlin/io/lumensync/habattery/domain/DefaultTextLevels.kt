package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextLevelName
import io.lumensync.habattery.domain.model.TextValue

/** The built-in text levels of each text value. */
object DefaultTextLevels {
  fun of(value: TextValue): List<TextLevel> =
    when (value) {
      TextValue.MODE -> MODES
    }

  /**
   * The ESP32 mode texts (spec 0012). The colours follow the inverter's own light, so they are
   * stored even where they equal the type's colour: changing the type keeps the mode's colour.
   */
  private val MODES =
    listOf(
      mode(1, "Power On", TextLevelName.POWER_ON, LevelColor.GREY, InverterIcon.POWER_SETTINGS_NEW, LevelType.INFO),
      mode(2, "Standby", TextLevelName.STANDBY, LevelColor.TEAL, InverterIcon.MODE_STANDBY, LevelType.WARNING),
      mode(3, "Mains", TextLevelName.MAINS, LevelColor.BLUE, InverterIcon.TOWER, LevelType.INFO),
      mode(4, "Off-Grid", TextLevelName.OFF_GRID, LevelColor.GREEN, InverterIcon.BATTERY_CHARGING_FULL, LevelType.WARNING),
      mode(5, "Bypass", TextLevelName.BYPASS, LevelColor.VIOLET, InverterIcon.ALT_ROUTE, LevelType.INFO),
      mode(6, "Charging", TextLevelName.CHARGING, LevelColor.AMBER, InverterIcon.CHARGER, LevelType.INFO),
      mode(7, "Fault", TextLevelName.FAULT, LevelColor.RED, InverterIcon.ERROR, LevelType.CRITICAL),
      TextLevel(
        id = 8,
        other = true,
        type = LevelType.INFO,
        color = LevelColor.GREY,
        icon = InverterIcon.HELP.name,
        notify = true,
      ),
    )

  private fun mode(
    id: Int,
    text: String,
    name: TextLevelName,
    color: LevelColor,
    icon: InverterIcon,
    type: LevelType,
  ) =
    TextLevel(
      id = id,
      texts = listOf(text),
      defaultName = name,
      type = type,
      color = color,
      icon = icon.name,
      notify = true,
    )
}
