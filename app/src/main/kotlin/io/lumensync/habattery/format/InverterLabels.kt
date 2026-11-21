package io.lumensync.habattery.format

import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.InverterMode

/** The word for a mode in the notification and the strip. */
@StringRes
fun modeWordRes(mode: InverterMode): Int =
  when (mode) {
    InverterMode.POWER_ON -> R.string.mode_word_power_on
    InverterMode.STANDBY -> R.string.mode_word_standby
    InverterMode.MAINS -> R.string.mode_word_mains
    InverterMode.OFF_GRID -> R.string.mode_word_off_grid
    InverterMode.BYPASS -> R.string.mode_word_bypass
    InverterMode.CHARGING -> R.string.mode_word_charging
    InverterMode.FAULT -> R.string.mode_word_fault
  }
