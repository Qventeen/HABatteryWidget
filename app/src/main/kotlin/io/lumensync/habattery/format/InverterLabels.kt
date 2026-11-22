package io.lumensync.habattery.format

import android.content.res.Resources
import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextLevelName

/** The translated default word of a mode. */
@StringRes
fun textLevelNameRes(name: TextLevelName): Int =
  when (name) {
    TextLevelName.POWER_ON -> R.string.mode_word_power_on
    TextLevelName.STANDBY -> R.string.mode_word_standby
    TextLevelName.MAINS -> R.string.mode_word_mains
    TextLevelName.OFF_GRID -> R.string.mode_word_off_grid
    TextLevelName.BYPASS -> R.string.mode_word_bypass
    TextLevelName.CHARGING -> R.string.mode_word_charging
    TextLevelName.FAULT -> R.string.mode_word_fault
  }

/**
 * The word for a mode on the strip, in the notification and in the app. "Other" shows the Home
 * Assistant text, which tells the user which text to map, or its label where there is none. Any
 * other mode shows the user's own name as typed, else its translated default word, else the text
 * it was matched by. [liveText] is the Home Assistant text, `null` in the app's lists.
 */
fun modeWord(resources: Resources, level: TextLevel, liveText: String?): String =
  when {
    level.other -> liveText ?: resources.getString(R.string.mode_other_label)
    level.name.isNotBlank() -> level.name
    level.defaultName != null -> resources.getString(textLevelNameRes(level.defaultName))
    else -> liveText ?: level.texts.firstOrNull().orEmpty()
  }

/** The name of a strip place in the Icons section. */
@StringRes
fun iconSlotRes(slot: IconSlot): Int =
  when (slot) {
    IconSlot.GRID_VOLTAGE -> R.string.inv_slot_grid_voltage
    IconSlot.OUTPUT_VOLTAGE -> R.string.inv_slot_output_voltage
  }
