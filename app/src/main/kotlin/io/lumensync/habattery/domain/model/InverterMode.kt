package io.lumensync.habattery.domain.model

import io.lumensync.habattery.domain.model.IconSlot.MODE_BYPASS
import io.lumensync.habattery.domain.model.IconSlot.MODE_CHARGING
import io.lumensync.habattery.domain.model.IconSlot.MODE_FAULT
import io.lumensync.habattery.domain.model.IconSlot.MODE_MAINS
import io.lumensync.habattery.domain.model.IconSlot.MODE_OFF_GRID
import io.lumensync.habattery.domain.model.IconSlot.MODE_POWER_ON
import io.lumensync.habattery.domain.model.IconSlot.MODE_STANDBY
import io.lumensync.habattery.domain.model.LevelColor.AMBER
import io.lumensync.habattery.domain.model.LevelColor.BLUE
import io.lumensync.habattery.domain.model.LevelColor.GREEN
import io.lumensync.habattery.domain.model.LevelColor.GREY
import io.lumensync.habattery.domain.model.LevelColor.RED
import io.lumensync.habattery.domain.model.LevelColor.TEAL
import io.lumensync.habattery.domain.model.LevelColor.VIOLET
import io.lumensync.habattery.domain.model.LevelType.CRITICAL
import io.lumensync.habattery.domain.model.LevelType.INFO
import io.lumensync.habattery.domain.model.LevelType.WARNING

/** The operation modes the ESP32 reports; [type] picks the notification channel. */
enum class InverterMode(
  val text: String,
  val color: LevelColor,
  val type: LevelType,
  val slot: IconSlot,
) {
  POWER_ON("Power On", GREY, INFO, MODE_POWER_ON),
  STANDBY("Standby", TEAL, WARNING, MODE_STANDBY),
  MAINS("Mains", BLUE, INFO, MODE_MAINS),
  OFF_GRID("Off-Grid", GREEN, WARNING, MODE_OFF_GRID),
  BYPASS("Bypass", VIOLET, INFO, MODE_BYPASS),
  CHARGING("Charging", AMBER, INFO, MODE_CHARGING),
  FAULT("Fault", RED, CRITICAL, MODE_FAULT);

  companion object {
    /** The row for [text] after trimming, ignoring case; `null` for any other text. */
    fun of(text: String?): InverterMode? {
      val key = text?.trim() ?: return null
      return entries.firstOrNull { it.text.equals(key, ignoreCase = true) }
    }
  }
}

/** Texts Home Assistant gives when a sensor has no reading. */
object ModeText {
  fun isNoValue(text: String?): Boolean =
    text == null ||
      text.isBlank() ||
      text.trim().equals("unavailable", ignoreCase = true) ||
      text.trim().equals("unknown", ignoreCase = true)
}
