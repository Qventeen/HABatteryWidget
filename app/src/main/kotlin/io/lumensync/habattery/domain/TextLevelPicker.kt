package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.ModeText
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.customTextLevels

object TextLevelPicker {
  /**
   * The item for [text], trimmed and ignoring case; the "other" item for a text no item lists;
   * `null` when Home Assistant has no value.
   */
  fun match(levels: List<TextLevel>, text: String?): TextLevel? {
    if (text == null || ModeText.isNoValue(text)) return null
    val key = text.trim()
    return levels.firstOrNull { level ->
      !level.other && level.texts.any { it.trim().equals(key, ignoreCase = true) }
    } ?: levels.firstOrNull { it.other }
  }

  /**
   * The owner's items, "other" last, when they are switched on, not empty and valid; else `null`.
   * A list stored by a later build may not be valid, so it never reaches the widget. This is the
   * one check for the strip, the alerts, the section and the editor.
   */
  fun ownLevels(custom: CustomTextLevels): List<TextLevel>? =
    if (custom.on && custom.levels.isNotEmpty() && Validation.textLevels(custom.levels).isEmpty()) {
      ordered(custom.levels)
    } else {
      null
    }

  /** The owner's items when [ownLevels] accepts them, else the built-in ones. */
  fun effective(value: TextValue, settings: Settings): List<TextLevel> =
    ownLevels(settings.customTextLevels(value)) ?: DefaultTextLevels.of(value)

  /** The items as shown: the others in stored order, then "other". */
  fun ordered(levels: List<TextLevel>): List<TextLevel> = levels.sortedBy { it.other }
}
